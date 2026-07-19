package top.puresky.hitokotohub.reconciler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ExtensionOperator;
import run.halo.app.extension.controller.Controller;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;
import top.puresky.hitokotohub.UncategorizedConstants;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;

/**
 * Sentence 资源的 Reconciler。
 *
 * <h2>设计原则（重构后）</h2>
 * <ul>
 *   <li><b>不再维护 Category.Status.sentenceCount 缓存</b>：分类计数改为通过
 *       {@link top.puresky.hitokotohub.service.CategoryCountService} 实时查询，
 *       从源头消除"缓存 ≠ 真实值"的一致性问题（跨分类迁移漏更新、并发乐观锁冲突、
 *       reconciler 失败导致缓存永久过期等）。</li>
 *   <li><b>仅做 categoryName 归一化</b>：sentence 创建/更新时，若分类名为空或不存在，
 *       归入 {@link UncategorizedConstants#METADATA_NAME}（"未分类"）。</li>
 *   <li><b>删除时不修改 SimilarityCheckLog</b>：日志作为检查时刻的快照，
 *       similarPairCount / similarPairs 保持原值直到下次相似度检查重算。
 *       前端分组视图通过 SimilarityGroupBuilder 的 profileMap::get +
 *       filter(p -> p != null) 自动过滤已删除句子。</li>
 * </ul>
 *
 * <h2>历史问题对照</h2>
 * <ul>
 *   <li>移除 {@code updateCategoryCount} 方法（不再写 Category.Status）</li>
 *   <li>移除 {@code sentence.getMetadata().setFinalizers(Collections.emptySet())}
 *       （当前 Sentence 无 finalizer，此调用为 no-op）</li>
 *   <li>移除冗余的 {@code if (category.getStatus() == null)} 检查</li>
 *   <li>新增关键决策点日志</li>
 *   <li>移除 handleDeletion 与 SimilarityCheckService 依赖（删除时不再清理 SimilarityCheckLog）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentenceReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    @Override
    public Result reconcile(Request request) {
        client.fetch(Sentence.class, request.name()).ifPresent(sentence -> {
            if (ExtensionOperator.isDeleted(sentence)) {
                // 删除时不做额外清理：SimilarityCheckLog 作为检查时刻的快照，
                // similarPairCount / similarPairs 保持不变，直到下次相似度检查重算。
                // 前端分组视图已通过 SimilarityGroupBuilder 的 profileMap::get +
                // filter(p -> p != null) 自动过滤已删除句子。
                log.info("句子 [{}] 已删除", sentence.getMetadata().getName());
                return;
            }
            normalizeCategory(sentence);
        });
        return Result.doNotRetry();
    }

    /**
     * 校验并归一化 sentence 的 categoryName。
     *
     * <p>归一化规则：
     * <ul>
     *   <li>categoryName 为 null/blank → 归入"未分类"</li>
     *   <li>categoryName 指向不存在的 Category → 归入"未分类"</li>
     *   <li>categoryName 已是"未分类"（即使 uncategorized 实体不存在）→ 不修改，避免无限循环</li>
     *   <li>其他有效分类 → 不修改</li>
     * </ul>
     *
     * <p>修改后会触发下一次 reconcile（由 Halo 自动调度）。
     */
    private void normalizeCategory(Sentence sentence) {
        String categoryName = sentence.getSpec().getCategoryName();
        String sentenceName = sentence.getMetadata().getName();

        if (categoryName == null || categoryName.isBlank()) {
            log.debug("句子 [{}] 的 categoryName 为空，归入未分类", sentenceName);
            reassignToUncategorized(sentence);
            return;
        }

        // 检查分类是否存在
        if (client.fetch(Category.class, categoryName).isEmpty()) {
            // 防止无限循环：categoryName 已是 uncategorized 时不再修改
            if (!UncategorizedConstants.METADATA_NAME.equals(categoryName)) {
                log.debug("句子 [{}] 的分类 [{}] 不存在，归入未分类",
                    sentenceName, categoryName);
                reassignToUncategorized(sentence);
            }
            // 若 categoryName == "uncategorized" 但分类实体不存在，
            // 由 UncategorizedCategoryInitializer 兜底创建
        }
    }

    /**
     * 将 sentence 的 categoryName 设为"未分类"并写回。
     */
    private void reassignToUncategorized(Sentence sentence) {
        sentence.getSpec().setCategoryName(UncategorizedConstants.METADATA_NAME);
        client.update(sentence);
        // 更新后由下一次 reconcile 处理（不会再进入此分支）
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Sentence()).build();
    }
}
