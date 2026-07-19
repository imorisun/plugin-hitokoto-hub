$content = @'
package top.puresky.hitokotohub.reconciler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.controller.Reconciler;
import top.puresky.hitokotohub.UncategorizedConstants;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.support.TestFixtures;

/**
 * {@link SentenceReconciler} 单元测试。
 *
 * <p>使用纯 Mockito mock 验证 reconciler 的核心行为：
 * <ul>
 *   <li>创建/更新分支：categoryName 归一化（null/不存在 → uncategorized）</li>
 *   <li>删除分支：仅记日志，不修改 SimilarityCheckLog 快照</li>
 *   <li>边界处理：uncategorized 不存在时不触发无限循环</li>
 * </ul>
 *
 * <p>说明：{@code SentenceReconciler} 依赖同步 {@link ExtensionClient}，
 * 不能用 {@link top.puresky.hitokotohub.support.MockExtensionClient}（其为 Reactive 版本）。
 */
@DisplayName("SentenceReconciler 单元测试")
class SentenceReconcilerTest {

    private static Reconciler.Request request(String name) {
        return new Reconciler.Request(name);
    }

    private SentenceReconciler reconciler(ExtensionClient client) {
        return new SentenceReconciler(client);
    }

    // ==================== 创建/更新分支：categoryName 归一化 ====================

    @Test
    @DisplayName("reconcile：categoryName 有效 → 不修改 sentence")
    void reconcile_validCategoryName_noUpdate() {
        Sentence sentence = TestFixtures.sentence("s1", "内容", "cat-a", "匿名", "未知", true, 0, 0);
        Category category = TestFixtures.category("cat-a", "分类A");

        ExtensionClient client = mock(ExtensionClient.class);
        when(client.fetch(eq(Sentence.class), eq("s1"))).thenReturn(Optional.of(sentence));
        when(client.fetch(eq(Category.class), eq("cat-a"))).thenReturn(Optional.of(category));

        SentenceReconciler reconciler = reconciler(client);
        reconciler.reconcile(request("s1"));

        // 不应该触发 update（categoryName 已有效）
        verify(client, never()).update(any(Sentence.class));
        // categoryName 不变
        assertEquals("cat-a", sentence.getSpec().getCategoryName());
    }

    @Test
    @DisplayName("reconcile：categoryName 为 null → 归入 uncategorized")
    void reconcile_nullCategoryName_reassignedToUncategorized() {
        Sentence sentence = TestFixtures.sentence("s1", "内容", true);
        sentence.getSpec().setCategoryName(null);

        ExtensionClient client = mock(ExtensionClient.class);
        when(client.fetch(eq(Sentence.class), eq("s1"))).thenReturn(Optional.of(sentence));

        SentenceReconciler reconciler = reconciler(client);
        reconciler.reconcile(request("s1"));

        // 应该触发 update
        verify(client, times(1)).update(any(Sentence.class));
        // categoryName 被改为 uncategorized
        assertEquals(UncategorizedConstants.METADATA_NAME, sentence.getSpec().getCategoryName());
    }

    @Test
    @DisplayName("reconcile：categoryName 指向不存在的分类 → 归入 uncategorized")
    void reconcile_nonExistentCategory_reassignedToUncategorized() {
        Sentence sentence = TestFixtures.sentence("s1", "内容", "ghost-cat", "匿名", "未知", true, 0, 0);

        ExtensionClient client = mock(ExtensionClient.class);
        when(client.fetch(eq(Sentence.class), eq("s1"))).thenReturn(Optional.of(sentence));
        when(client.fetch(eq(Category.class), eq("ghost-cat"))).thenReturn(Optional.empty());

        SentenceReconciler reconciler = reconciler(client);
        reconciler.reconcile(request("s1"));

        verify(client, times(1)).update(any(Sentence.class));
        assertEquals(UncategorizedConstants.METADATA_NAME, sentence.getSpec().getCategoryName());
    }

    @Test
    @DisplayName("reconcile：categoryName=uncategorized 但分类不存在 → 不触发无限循环（不更新）")
    void reconcile_uncategorizedNotExists_noInfiniteLoop() {
        Sentence sentence = TestFixtures.sentence("s1", "内容", "uncategorized", "匿名", "未知", true, 0, 0);

        ExtensionClient client = mock(ExtensionClient.class);
        when(client.fetch(eq(Sentence.class), eq("s1"))).thenReturn(Optional.of(sentence));
        when(client.fetch(eq(Category.class), eq("uncategorized"))).thenReturn(Optional.empty());

        SentenceReconciler reconciler = reconciler(client);
        reconciler.reconcile(request("s1"));

        // 关键：不应该再次 update，否则会触发无限 reconcile 循环
        verify(client, never()).update(any(Sentence.class));
        // categoryName 保持不变（仍是 uncategorized）
        assertEquals(UncategorizedConstants.METADATA_NAME, sentence.getSpec().getCategoryName());
    }

    // ==================== 删除分支：仅记日志，不修改 SimilarityCheckLog 快照 ====================

    @Test
    @DisplayName("reconcile：删除 sentence → 仅记日志，不调用 update")
    void reconcile_deletedSentence_logsOnly_noSideEffects() {
        Sentence sentence = TestFixtures.sentence("s1", "内容", "cat-a", "匿名", "未知", true, 0, 0);
        sentence.getMetadata().setDeletionTimestamp(Instant.now());

        ExtensionClient client = mock(ExtensionClient.class);
        when(client.fetch(eq(Sentence.class), eq("s1"))).thenReturn(Optional.of(sentence));

        SentenceReconciler reconciler = reconciler(client);
        reconciler.reconcile(request("s1"));

        // 不应触发 update（删除分支仅记日志）
        verify(client, never()).update(any(Sentence.class));
    }

    // ==================== 边界：sentence 不存在 ====================

    @Test
    @DisplayName("reconcile：sentence 不存在（fetch 返回 empty）→ 不做任何操作")
    void reconcile_sentenceNotExists_noOp() {
        ExtensionClient client = mock(ExtensionClient.class);
        when(client.fetch(eq(Sentence.class), eq("ghost-sentence"))).thenReturn(Optional.empty());

        SentenceReconciler reconciler = reconciler(client);
        reconciler.reconcile(request("ghost-sentence"));

        // 不应该触发 update
        verify(client, never()).update(any(Sentence.class));
    }
}
'@
$path = "c:\Users\19002\Documents\Trae\plugin-hitokoto-hub\src\test\java\top\puresky\hitokotohub\reconciler\SentenceReconcilerTest.java"
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
$info = Get-Item $