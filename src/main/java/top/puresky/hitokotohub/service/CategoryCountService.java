package top.puresky.hitokotohub.service;

import java.util.Map;
import reactor.core.publisher.Mono;
import top.puresky.hitokotohub.service.dto.CategoryStats;

/**
 * 分类下句子数量的实时查询服务。
 *
 * <p>替代历史设计中缓存于 {@code Category.Status.sentenceCount} 的方案，
 * 通过单次 {@code listAll(Sentence)} + 内存分组实现 O(N) 实时计数，
 * 彻底消除 SentenceReconciler 维护缓存带来的数据一致性问题：
 * <ul>
 *   <li>跨分类迁移漏更新</li>
 *   <li>并发乐观锁冲突</li>
 *   <li>reconciler 失败导致缓存永久过期</li>
 * </ul>
 */
public interface CategoryCountService {

    /**
     * 返回所有分类的 sentenceCount 映射（含计数为 0 的分类）。
     *
     * <p>实现策略：单次 {@code listAll(Category)} 获取所有分类名 +
     * 单次 {@code listAll(Sentence)} 内存分组计数，避免 N+1 查询。
     * 已删除（deletionTimestamp 非空）的 sentence 不计入。
     *
     * @return 分类名 → 句子数量映射
     */
    Mono<Map<String, Long>> getAllCounts();

    /**
     * 返回所有分类的句子统计映射（含总数和已发布数，含计数为 0 的分类）。
     *
     * <p>实现策略：单次 {@code listAll(Category)} + 单次 {@code listAll(Sentence)}
     * 内存按 (categoryName, isPublished) 分组，避免 Overview 接口的 N+1 查询。
     * 已删除（deletionTimestamp 非空）的 sentence 不计入。
     *
     * @return 分类名 → {@link CategoryStats} 统计映射
     */
    Mono<Map<String, CategoryStats>> getCategoryStats();

    /**
     * 返回指定分类的 sentenceCount。
     *
     * @param categoryName 分类 metadata.name
     * @return 句子数量（分类不存在或无句子均返回 0）
     */
    Mono<Long> getCount(String categoryName);
}
