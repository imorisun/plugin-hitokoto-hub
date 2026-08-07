package top.puresky.hitokotohub.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.service.SimilarityCheckService;
import top.puresky.hitokotohub.service.dto.BatchDeleteResult;
import top.puresky.hitokotohub.service.similarity.SentencePair;
import top.puresky.hitokotohub.service.similarity.SentencePairJsonCodec;
import top.puresky.hitokotohub.service.similarity.SentenceProfile;
import top.puresky.hitokotohub.service.similarity.SimilarityGroupBuilder;
import top.puresky.hitokotohub.service.similarity.SimilarityPairFinder;

/**
 * 句子相似度检查服务实现。
 *
 * <p>核心功能包括：
 * <ul>
 *   <li>基于 TF-IDF 余弦相似度或 Jaccard 系数的全量句子比对（委托 {@link SimilarityPairFinder}）</li>
 *   <li>使用并查集（Union-Find）对相似句子进行传递性分组（委托 {@link SimilarityGroupBuilder}）</li>
 *   <li>按句子质量评分选出每组最优句子，支持批量删除非最优句子</li>
 * </ul>
 *
 * <p>本类仅保留编排逻辑（日志管理、数据访问、批量并发删除），算法实现已迁出至
 * {@code service.similarity} 包，保持算法层零 Spring/Extension 依赖。
 *
 * <p>性能特征：
 * <ul>
 *   <li>相似度计算：O(n²)，n 为句子总数</li>
 *   <li>分组构建：O(m·α(n))，m 为相似对数，α 为反阿克曼函数（近似常数）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityCheckServiceImpl implements SimilarityCheckService {

    /** 日志中存储的相似对最大数量，超出部分不持久化（但可通过重新计算恢复） */
    private static final int MAX_STORED_PAIRS = 500;

    /**
     * 参与相似度检查的句子上限。超过此值时拒绝执行，避免 O(n²) 算法导致 OOM。
     * 相似度计算需要全量句子驻留内存，5 万条约需 ~200MB profile + 中间数据。
     */
    private static final int MAX_SENTENCES_LIMIT = 50_000;

    private final ReactiveExtensionClient client;
    private final SimilarityGroupBuilder groupBuilder;
    private final SentencePairJsonCodec codec;

    // ===================== 公共 API =====================

    /**
     * {@inheritDoc}
     *
     * <p>执行流程：
     * <ol>
     *   <li>创建 RUNNING 状态的检查日志</li>
     *   <li>在 boundedElastic 线程池中执行全量相似度计算</li>
     *   <li>更新日志为 SUCCESS（或 FAILED）状态</li>
     *   <li>清理旧日志，仅保留最新一条</li>
     * </ol>
     */
    @Override
    public Mono<SimilarityCheckLog> performCheck(
        SimilarityCheckLog.TriggerType triggerType,
        String triggeredBy,
        String algorithm,
        double threshold
    ) {
        SimilarityCheckLog logEntry = createInitialLog(triggerType, triggeredBy, algorithm,
            threshold);
        return client.create(logEntry)
            .flatMap(created -> Mono.fromCallable(
                    () -> executeCheck(created, algorithm, threshold))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(client::update)
                .flatMap(updated -> deleteOldLogs(updated).thenReturn(updated))
                .onErrorResume(e -> {
                    log.error("相似度检查失败", e);
                    created.getSpec().setStatus(SimilarityCheckLog.Status.FAILED);
                    created.getSpec().setErrorMessage(e.getMessage());
                    return client.update(created);
                }));
    }

    /**
     * {@inheritDoc}
     *
     * <p>从最新成功日志中解析相似对，使用并查集分组，过滤已删除句子后分页返回。
     *
     * <p>容错设计：批量删除后 Halo 索引可能短暂未就绪，
     * 使用指数退避重试（3 次，200ms 起，1s 上限）自动等待索引恢复后返回正确数据。
     * 重试耗尽后降级为空结果，避免阻塞前端渲染。
     */
    @Override
    public Mono<Map<String, Object>> getGroups(int page, int size) {
        return getLatestSuccessLog()
            .flatMap(latestLog -> Mono.fromCallable(() ->
                buildGroupsResult(latestLog, page, size))
                .subscribeOn(Schedulers.boundedElastic()))
            .switchIfEmpty(Mono.just(groupBuilder.emptyResult(page, size)))
            .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                .maxBackoff(Duration.ofSeconds(1))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
            .onErrorResume(e -> {
                log.error("获取分组结果失败（已重试3次），返回空分组, page={}, size={}",
                    page, size, e);
                return Mono.just(groupBuilder.emptyResult(page, size));
            });
    }

    /**
     * {@inheritDoc}
     *
     * <p>执行流程：
     * <ol>
     *   <li>从最新成功日志获取算法和阈值</li>
     *   <li>重新计算完整相似对列表（不受 {@link #MAX_STORED_PAIRS} 限制）</li>
     *   <li>并查集分组，每组保留评分最高的句子</li>
     *   <li>批量并发删除非最优句子（concurrency=16）</li>
     * </ol>
     *
     * <p>注意：删除句子不会修改 SimilarityCheckLog。日志作为检查时刻的快照，
     * similarPairCount / similarPairs 保持原值直到下次相似度检查重算。
     * 前端分组视图通过 SimilarityGroupBuilder 的 profileMap 自动过滤已删除句子。
     */
    @Override
    public Mono<BatchDeleteResult> deleteNonOptimalSentences() {
        return getLatestSuccessLog()
            .flatMap(latestLog -> {
                String algorithm = latestLog.getSpec().getAlgorithm();
                double threshold = latestLog.getSpec().getThreshold();
                String logName = latestLog.getMetadata().getName();
                log.info("批量删除：开始处理，log={}, algorithm={}, threshold={}",
                    logName, algorithm, threshold);
                return fetchProfiles()
                    .doOnError(e -> log.error("获取句子列表失败, log={}", logName, e))
                    .flatMap(profiles -> {
                        if (profiles.isEmpty()) {
                            log.info("批量删除：无句子可处理, log={}", logName);
                            return Mono.just(BatchDeleteResult.empty("无句子可处理"));
                        }
                        if (profiles.size() > MAX_SENTENCES_LIMIT) {
                            String msg = "句子数量 " + profiles.size() + " 超过上限 "
                                + MAX_SENTENCES_LIMIT + "，请先清理后再执行批量删除";
                            log.warn("批量删除中止: {}", msg);
                            return Mono.just(BatchDeleteResult.empty(msg));
                        }
                        return Mono.fromCallable(() ->
                            collectNonOptimalNames(profiles, algorithm, threshold))
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnError(e ->
                                log.error("收集非最优句子名称失败, log={}", logName, e))
                            .flatMap(names -> deleteSentencesInBatch(names, logName));
                    });
            })
            .switchIfEmpty(Mono.fromSupplier(() -> {
                log.info("批量删除：无 SUCCESS 状态的检查日志，跳过");
                return BatchDeleteResult.empty("无相似度检查日志，请先触发检查");
            }));
    }

    // ===================== 检查执行 =====================

    /**
     * 创建初始检查日志（RUNNING 状态）。
     */
    private SimilarityCheckLog createInitialLog(
        SimilarityCheckLog.TriggerType triggerType,
        String triggeredBy,
        String algorithm,
        double threshold
    ) {
        SimilarityCheckLog logEntry = new SimilarityCheckLog();
        logEntry.setMetadata(new Metadata());
        logEntry.getMetadata().setGenerateName("similarity-check-");
        SimilarityCheckLog.Spec spec = new SimilarityCheckLog.Spec();
        spec.setTriggerType(triggerType);
        spec.setTriggeredBy(triggeredBy);
        spec.setAlgorithm(algorithm);
        spec.setThreshold(threshold);
        spec.setStatus(SimilarityCheckLog.Status.RUNNING);
        logEntry.setSpec(spec);
        return logEntry;
    }

    /**
     * 执行全量相似度计算并填充日志结果。
     *
     * <p>此方法在 boundedElastic 线程池中执行，可安全调用阻塞操作。
     *
     * <p>内存优化：直接流式映射为轻量 {@link SentenceProfile}，避免同时持有
     * 全量 Sentence Extension 对象与 profile 列表两份数据。
     */
    private SimilarityCheckLog executeCheck(
        SimilarityCheckLog logEntry, String algorithm, double threshold
    ) {
        long startTime = System.currentTimeMillis();
        List<SentenceProfile> profiles = fetchProfiles().block();

        if (profiles == null || profiles.isEmpty()) {
            populateEmptyResult(logEntry, startTime);
            return logEntry;
        }

        // 句子数安全阀：防止 O(n²) 算法在海量数据下 OOM
        if (profiles.size() > MAX_SENTENCES_LIMIT) {
            String msg = "句子数量 " + profiles.size() + " 超过上限 " + MAX_SENTENCES_LIMIT
                + "，请先清理或分批处理后再执行相似度检查";
            log.warn(msg);
            logEntry.getSpec().setTotalSentences(profiles.size());
            logEntry.getSpec().setStatus(SimilarityCheckLog.Status.FAILED);
            logEntry.getSpec().setErrorMessage(msg);
            logEntry.getSpec().setDurationMs(System.currentTimeMillis() - startTime);
            return logEntry;
        }

        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
        List<SentencePair> storedPairs = pairs.size() > MAX_STORED_PAIRS
            ? pairs.subList(0, MAX_STORED_PAIRS) : pairs;
        String pairsJson = codec.serialize(storedPairs);

        int totalSentences = profiles.size();
        long totalPairs = SimilarityPairFinder.totalPairs(totalSentences);

        logEntry.getSpec().setTotalSentences(totalSentences);
        logEntry.getSpec().setTotalPairs(totalPairs);
        logEntry.getSpec().setSimilarPairCount(pairs.size());
        logEntry.getSpec().setSimilarPairs(pairsJson);
        logEntry.getSpec().setStatus(SimilarityCheckLog.Status.SUCCESS);
        logEntry.getSpec().setDurationMs(System.currentTimeMillis() - startTime);

        log.info("相似度检查完成: {} 个句子, {} 个配对, {} 个相似对, 耗时 {}ms",
            totalSentences, totalPairs, pairs.size(), logEntry.getSpec().getDurationMs());
        return logEntry;
    }

    /**
     * 检查完成后删除所有旧日志，仅保留当前日志。
     */
    private Mono<Void> deleteOldLogs(SimilarityCheckLog currentLog) {
        String currentName = currentLog.getMetadata().getName();
        return client.listAll(SimilarityCheckLog.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .filter(l -> !currentName.equals(l.getMetadata().getName()))
            .flatMap(client::delete)
            .then();
    }

    // ===================== 分组构建 =====================

    /**
     * 从检查日志构建分组结果（分页）。
     *
     * <p>流程：解析相似对 → 获取现存句子 → 委托 {@link SimilarityGroupBuilder} 分组 → 分页
     */
    private Map<String, Object> buildGroupsResult(SimilarityCheckLog latestLog, int page, int size) {
        if (latestLog == null) {
            return groupBuilder.emptyResult(page, size);
        }

        List<SentencePair> pairs = codec.deserialize(latestLog.getSpec().getSimilarPairs());
        if (pairs.isEmpty()) {
            return groupBuilder.emptyResult(page, size);
        }

        // 获取现存句子构建 profileMap（已删除的句子不会出现，实现自动过滤）
        // 流式映射为轻量 profile，避免持有全量 Sentence 对象
        Map<String, SentenceProfile> profileMap = fetchProfileMap().block();
        if (profileMap == null || profileMap.isEmpty()) {
            return groupBuilder.emptyResult(page, size);
        }

        return groupBuilder.paginate(groupBuilder.buildGroups(pairs, profileMap), page, size);
    }

    // ===================== 批量删除 =====================

    /**
     * 收集所有非最优句子名称。
     *
     * <p>对每组相似句子，保留评分最高的，其余标记为待删除。
     */
    private Set<String> collectNonOptimalNames(List<SentenceProfile> profiles,
                                                String algorithm, double threshold) {
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
        Map<String, SentenceProfile> profileMap = toProfileMap(profiles);
        return groupBuilder.collectNonOptimalNames(pairs, profileMap);
    }

    /**
     * 批量并发删除句子（concurrency=16，与 StatsCleanupScheduler 一致）。
     *
     * <p>不再串行：SentenceReconciler 已不再维护 Category.Status 缓存，
     * 并发删除不会触发 Category 乐观锁冲突。
     *
     * <p>单条删除失败时跳过，不中断整体流程（项目硬约束）：
     * <ul>
     *   <li>{@code client.fetch} 返回 empty（句子已被并发删除）→ {@code defaultIfEmpty(false)} 计入 {@code failed}</li>
     *   <li>{@code client.delete} 单条抛异常 → {@code onErrorResume} 跳过，计入 {@code failed}</li>
     * </ul>
     *
     * <p>计数策略：用 {@code reduce(new int[]{0, 0}, ...)} 累加 {@code [deleted, failed]}，
     * 通过 {@code thenReturn(true)} 不依赖 {@code client.delete} 的返回值类型
     * （Halo 的 ReactiveExtensionClient.delete 可能返回 {@code Mono<Void>} 或被删对象）。
     *
     * @param names   待删除句子名称集合
     * @param logName 触发本次删除的检查日志名称（仅用于日志关联，可为空）
     * @return {@link BatchDeleteResult}，含 total/deleted/failed/message
     */
    private Mono<BatchDeleteResult> deleteSentencesInBatch(Set<String> names, String logName) {
        int total = names.size();
        if (total == 0) {
            log.info("批量删除：无非最优句子待删除, log={}", logName);
            return Mono.just(BatchDeleteResult.empty("无非最优句子需要删除"));
        }
        log.info("批量删除：共 {} 个待删除, log={}", total, logName);

        return Flux.fromIterable(names)
            .flatMap(name -> client.fetch(Sentence.class, name)
                .flatMap(s -> client.delete(s)
                    .thenReturn(Map.entry(name, true))
                    .onErrorResume(e -> {
                        log.warn("删除句子 [{}] 失败: {}", name, e.getMessage(), e);
                        return Mono.just(Map.entry(name, false));
                    }))
                .defaultIfEmpty(Map.entry(name, false)), 16)
            .collectMap(e -> e.getKey(), e -> e.getValue())
            .flatMap(nameToSuccess -> {
                long deletedCount = nameToSuccess.values().stream()
                    .filter(Boolean.TRUE::equals).count();
                long failedCount = total - deletedCount;
                log.info("批量删除完成：log={}, total={}, deleted={}, failed={}",
                    logName, total, deletedCount, failedCount);
                BatchDeleteResult result =
                    BatchDeleteResult.of(total, (int) deletedCount, (int) failedCount);

                // 只验证真正删除成功的句子，跳过删除失败的
                Set<String> deletedNames = nameToSuccess.entrySet().stream()
                    .filter(e -> e.getValue())
                    .map(e -> e.getKey())
                    .collect(Collectors.toSet());
                if (deletedNames.isEmpty()) {
                    return Mono.just(result);
                }
                return ensureSentencesGone(deletedNames, logName).thenReturn(result);
            });
    }

    /**
     * 验证被删句子是否已从存储中消失。
     *
     * <p>Halo 扩展存储在批量并发删除后索引更新存在短暂延迟，
     * 若 {@link #fetchProfiles()} 仍返回已删句子，说明索引尚未同步完成。
     * 通过指数退避重试（最多 10 次，100ms 起，500ms 上限）等待索引就绪，
     * 确保 {@code deleteNonOptimalSentences} 返回时被删数据已不可见。
     *
     * @param names   被删句子名称集合
     * @param logName 关联的检查日志名称
     * @return 验证完成（无数据）Mono
     */
    private Mono<Void> ensureSentencesGone(Set<String> names, String logName) {
        return Mono.defer(() -> fetchProfiles()
                .flatMap(profiles -> {
                    long remaining = profiles.stream()
                        .filter(p -> names.contains(p.name()))
                        .count();
                    if (remaining == 0) {
                        return Mono.empty();
                    }
                    log.debug("等待 Halo 索引同步，仍有 {} 个句子未消失, log={}",
                        remaining, logName);
                    return Mono.error(new IndexNotReadyException(
                        remaining + " 个句子索引尚未更新"));
                }))
            .retryWhen(Retry.backoff(10, Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(500))
                .onRetryExhaustedThrow((spec, signal) -> {
                    log.warn("索引同步超时（已重试10次），仍有句子未消失, log={}", logName);
                    return signal.failure();
                }))
            .then();
    }

    /**
     * 内部异常：表示 Halo 扩展存储索引尚未完成更新。
     * 用于 {@link #ensureSentencesGone} 的重试控制，不对外暴露。
     */
    private static final class IndexNotReadyException extends RuntimeException {
        IndexNotReadyException(String message) {
            super(message);
        }
    }

    // ===================== 数据访问 =====================

    /**
     * 获取最新一条 SUCCESS 状态的检查日志。
     */
    private Mono<SimilarityCheckLog> getLatestSuccessLog() {
        return client.listAll(SimilarityCheckLog.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").descending())
            .filter(l -> l.getSpec().getStatus() == SimilarityCheckLog.Status.SUCCESS)
            .next();
    }

    /**
     * 流式获取所有句子的轻量 profile（按创建时间升序）。
     *
     * <p>内存优化：在 Flux 管道中即时映射为 {@link SentenceProfile}，
     * 避免在内存中同时持有全量 Sentence Extension 对象。峰值内存仅为
     * profile 列表 + 单个 Sentence（被 GC 回收）。
     */
    private Mono<List<SentenceProfile>> fetchProfiles() {
        return client.listAll(Sentence.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .map(SentenceProfile::from)
            .collectList();
    }

    /**
     * 流式获取 name → SentenceProfile 映射，用于分组构建时过滤已删除句子。
     */
    private Mono<Map<String, SentenceProfile>> fetchProfileMap() {
        return client.listAll(Sentence.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .collectMap(
                s -> s.getMetadata().getName(),
                SentenceProfile::from
            );
    }

    // ===================== 边界转换 =====================

    /**
     * 将 profile 列表转换为 name → SentenceProfile 映射，用于批量删除时分组构建。
     */
    private Map<String, SentenceProfile> toProfileMap(List<SentenceProfile> profiles) {
        return profiles.stream().collect(Collectors.toMap(
            SentenceProfile::name,
            p -> p,
            (a, b) -> a
        ));
    }

    // ===================== 工具方法 =====================

    /**
     * 填充空结果的检查日志。
     */
    private void populateEmptyResult(SimilarityCheckLog logEntry, long startTime) {
        logEntry.getSpec().setTotalSentences(0);
        logEntry.getSpec().setTotalPairs(0);
        logEntry.getSpec().setSimilarPairCount(0);
        logEntry.getSpec().setSimilarPairs("[]");
        logEntry.getSpec().setStatus(SimilarityCheckLog.Status.SUCCESS);
        logEntry.getSpec().setDurationMs(System.currentTimeMillis() - startTime);
    }
}
