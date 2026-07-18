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
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.service.SimilarityCheckService;
import top.puresky.hitokotohub.service.similarity.SentencePair;
import top.puresky.hitokotohub.service.similarity.SentencePairJsonCodec;
import top.puresky.hitokotohub.service.similarity.SentenceProfile;
import top.puresky.hitokotohub.service.similarity.SimilarityPairFinder;
import top.puresky.hitokotohub.service.similarity.SimilarityGroupBuilder;

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
 * <p>本类仅保留编排逻辑（日志管理、数据访问、串行删除），算法实现已迁出至
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
     */
    @Override
    public Mono<Map<String, Object>> getGroups(int page, int size) {
        return getLatestSuccessLog()
            .flatMap(latestLog -> Mono.fromCallable(() ->
                buildGroupsResult(latestLog, page, size))
                .subscribeOn(Schedulers.boundedElastic()))
            .switchIfEmpty(Mono.just(groupBuilder.emptyResult(page, size)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>执行流程：
     * <ol>
     *   <li>从最新成功日志获取算法和阈值</li>
     *   <li>重新计算完整相似对列表（不受 {@link #MAX_STORED_PAIRS} 限制）</li>
     *   <li>并查集分组，每组保留评分最高的句子</li>
     *   <li>串行删除非最优句子（避免 Category 乐观锁冲突）</li>
     * </ol>
     */
    @Override
    public Mono<Integer> deleteNonOptimalSentences() {
        return getLatestSuccessLog()
            .flatMap(latestLog -> {
                if (latestLog == null) {
                    return Mono.just(0);
                }
                String algorithm = latestLog.getSpec().getAlgorithm();
                double threshold = latestLog.getSpec().getThreshold();
                return fetchAllSentences().flatMap(sentences -> {
                    if (sentences.isEmpty()) {
                        return Mono.just(0);
                    }
                    return Mono.fromCallable(() ->
                        collectNonOptimalNames(sentences, algorithm, threshold))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(this::deleteSentencesSerially);
                });
            });
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
     */
    private SimilarityCheckLog executeCheck(
        SimilarityCheckLog logEntry, String algorithm, double threshold
    ) {
        long startTime = System.currentTimeMillis();
        List<Sentence> sentences = fetchAllSentences().block();

        if (sentences == null || sentences.isEmpty()) {
            populateEmptyResult(logEntry, startTime);
            return logEntry;
        }

        List<SentenceProfile> profiles = toProfiles(sentences);
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
        List<SentencePair> storedPairs = pairs.size() > MAX_STORED_PAIRS
            ? pairs.subList(0, MAX_STORED_PAIRS) : pairs;
        String pairsJson = codec.serialize(storedPairs);

        int totalSentences = sentences.size();
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
        List<Sentence> sentences = fetchAllSentences().block();
        if (sentences == null || sentences.isEmpty()) {
            return groupBuilder.emptyResult(page, size);
        }
        Map<String, SentenceProfile> profileMap = toProfileMap(sentences);

        return groupBuilder.paginate(groupBuilder.buildGroups(pairs, profileMap), page, size);
    }

    // ===================== 批量删除 =====================

    /**
     * 收集所有非最优句子名称。
     *
     * <p>对每组相似句子，保留评分最高的，其余标记为待删除。
     */
    private Set<String> collectNonOptimalNames(List<Sentence> sentences,
                                                String algorithm, double threshold) {
        List<SentenceProfile> profiles = toProfiles(sentences);
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, algorithm, threshold);
        Map<String, SentenceProfile> profileMap = toProfileMap(sentences);
        return groupBuilder.collectNonOptimalNames(pairs, profileMap);
    }

    /**
     * 串行删除句子，避免并发触发 reconciler 导致 Category 乐观锁冲突。
     *
     * <p>单条删除失败时跳过，不中断整体流程（项目硬约束）。
     */
    private Mono<Integer> deleteSentencesSerially(Set<String> names) {
        if (names.isEmpty()) {
            return Mono.just(0);
        }
        log.info("批量删除非最优句子，共 {} 个待删除", names.size());

        return Flux.fromIterable(names)
            .concatMap(name -> client.fetch(Sentence.class, name)
                .flatMap(s -> client.delete(s)
                    .onErrorResume(e -> {
                        log.warn("删除句子 {} 失败: {}", name, e.getMessage());
                        return Mono.empty();
                    }))
                .switchIfEmpty(Mono.empty()))
            .count()
            .map(Long::intValue)
            .delayElement(Duration.ofSeconds(1))
            .doOnSuccess(count ->
                log.info("批量删除非最优句子完成，共删除 {} 个句子", count));
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
     * 获取所有句子（按创建时间升序）。
     */
    private Mono<List<Sentence>> fetchAllSentences() {
        return client.listAll(Sentence.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .collectList();
    }

    // ===================== 边界转换 =====================

    /**
     * 将 Sentence 列表转换为 SentenceProfile 列表（算法层纯数据投影）。
     */
    private List<SentenceProfile> toProfiles(List<Sentence> sentences) {
        return sentences.stream().map(SentenceProfile::from).toList();
    }

    /**
     * 将 Sentence 列表转换为 name → SentenceProfile 映射，用于分组构建时过滤已删除句子。
     */
    private Map<String, SentenceProfile> toProfileMap(List<Sentence> sentences) {
        return sentences.stream().collect(Collectors.toMap(
            s -> s.getMetadata().getName(),
            SentenceProfile::from,
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
