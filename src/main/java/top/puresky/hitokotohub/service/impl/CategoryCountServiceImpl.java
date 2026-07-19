package top.puresky.hitokotohub.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.service.CategoryCountService;
import top.puresky.hitokotohub.service.dto.CategoryStats;

/**
 * {@link CategoryCountService} 默认实现。
 *
 * <p>核心策略：单次 {@code listAll(Category)} + 单次 {@code listAll(Sentence)} + 内存分组，
 * 总成本 O(C + S)（C=分类数，S=句子数）。相较于历史方案中 N 次 countBy 的 O(N×S) 全表扫描，
 * 显著降低数据库压力，同时避免 SentenceReconciler 维护缓存带来的并发乐观锁冲突。
 *
 * <p>实时性保证：每次调用都重新查询，无缓存过期问题；reconciler 不再写 Category.Status，
 * 从源头消除了"缓存 ≠ 真实值"的可能。
 *
 * <p>线程模型：内存分组逻辑放在 {@link Schedulers#boundedElastic()} 上执行，
 * 避免阻塞 reactor 主线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryCountServiceImpl implements CategoryCountService {

    private final ReactiveExtensionClient client;

    @Override
    public Mono<Map<String, Long>> getAllCounts() {
        Mono<List<Category>> categoriesMono = client
            .listAll(Category.class, new ListOptions(), Sort.unsorted())
            .collectList();

        // 仅查询未删除的 sentence，避免在内存中处理大量已删除记录
        Mono<List<Sentence>> sentencesMono = client
            .listAll(Sentence.class,
                ListOptions.builder()
                    .fieldQuery(Queries.isNull("metadata.deletionTimestamp"))
                    .build(),
                Sort.unsorted())
            .collectList();

        return Mono.zip(categoriesMono, sentencesMono)
            .flatMap(tuple -> Mono.fromCallable(() ->
                buildCountsMap(tuple.getT1(), tuple.getT2()))
                .subscribeOn(Schedulers.boundedElastic()))
            .doOnError(e -> log.error("查询分类句子数量失败", e));
    }

    @Override
    public Mono<Map<String, CategoryStats>> getCategoryStats() {
        Mono<List<Category>> categoriesMono = client
            .listAll(Category.class, new ListOptions(), Sort.unsorted())
            .collectList();

        Mono<List<Sentence>> sentencesMono = client
            .listAll(Sentence.class,
                ListOptions.builder()
                    .fieldQuery(Queries.isNull("metadata.deletionTimestamp"))
                    .build(),
                Sort.unsorted())
            .collectList();

        return Mono.zip(categoriesMono, sentencesMono)
            .flatMap(tuple -> Mono.fromCallable(() ->
                buildStatsMap(tuple.getT1(), tuple.getT2()))
                .subscribeOn(Schedulers.boundedElastic()))
            .doOnError(e -> log.error("查询分类句子统计失败", e));
    }

    @Override
    public Mono<Long> getCount(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return Mono.just(0L);
        }
        return client.countBy(Sentence.class,
                ListOptions.builder()
                    .fieldQuery(Queries.and(
                        Queries.equal("spec.categoryName", categoryName),
                        Queries.isNull("metadata.deletionTimestamp")
                    ))
                    .build())
            .defaultIfEmpty(0L)
            .doOnError(e -> log.error("查询分类 [{}] 句子数量失败", categoryName, e));
    }

    /**
     * 内存分组构建分类 → 句子数映射。
     *
     * <p>确保所有已注册分类都出现在 map 中（即使计数为 0），
     * 同时处理异常数据（categoryName 为 null/blank 的 sentence 不计入任何分类）。
     */
    private Map<String, Long> buildCountsMap(List<Category> categories, List<Sentence> sentences) {
        Map<String, Long> counts = new HashMap<>(categories.size() + 8);

        // 1. 初始化所有分类计数为 0
        for (Category c : categories) {
            String name = c.getMetadata().getName();
            if (name != null && !name.isBlank()) {
                counts.put(name, 0L);
            }
        }

        // 2. 内存分组计数（防御性：跳过 categoryName 异常的 sentence）
        for (Sentence s : sentences) {
            // 防御性检查（理论上 listAll 已过滤 deletionTimestamp，但 double-check 防御）
            if (s.getMetadata() != null && s.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            if (s.getSpec() == null) {
                continue;
            }
            String categoryName = s.getSpec().getCategoryName();
            if (categoryName == null || categoryName.isBlank()) {
                // 异常数据：未指定分类的 sentence 不计入任何已知分类，
                // 也不计入 uncategorized（避免与"未分类"实体下的真实 sentence 混淆）
                continue;
            }
            counts.merge(categoryName, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 内存分组构建分类 → {@link CategoryStats} 统计映射。
     *
     * <p>同时按 (categoryName, isPublished) 分组,一次遍历得到每个分类的总数和已发布数。
     * 所有已注册分类都出现在 map 中(即使计数为 0);categoryName 异常的 sentence 不计入。
     */
    private Map<String, CategoryStats> buildStatsMap(List<Category> categories,
                                                       List<Sentence> sentences) {
        // 先用 long[2] 累加:{total, published}
        Map<String, long[]> acc = new HashMap<>(categories.size() + 8);

        // 1. 初始化所有分类计数为 0
        for (Category c : categories) {
            String name = c.getMetadata().getName();
            if (name != null && !name.isBlank()) {
                acc.put(name, new long[]{0L, 0L});
            }
        }

        // 2. 内存分组计数
        for (Sentence s : sentences) {
            if (s.getMetadata() != null && s.getMetadata().getDeletionTimestamp() != null) {
                continue;
            }
            if (s.getSpec() == null || s.getStatus() == null) {
                continue;
            }
            String categoryName = s.getSpec().getCategoryName();
            if (categoryName == null || categoryName.isBlank()) {
                continue;
            }
            long[] entry = acc.computeIfAbsent(categoryName, k -> new long[]{0L, 0L});
            entry[0]++; // total
            if (s.getStatus().isPublished()) {
                entry[1]++; // published
            }
        }

        // 3. 转换为 CategoryStats record
        Map<String, CategoryStats> result = new HashMap<>(acc.size());
        for (Map.Entry<String, long[]> e : acc.entrySet()) {
            long[] v = e.getValue();
            result.put(e.getKey(), new CategoryStats(v[0], v[1]));
        }
        return result;
    }
}
