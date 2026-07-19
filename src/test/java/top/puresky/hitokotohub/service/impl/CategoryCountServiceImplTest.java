package top.puresky.hitokotohub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.support.MockExtensionClient;
import top.puresky.hitokotohub.support.TestFixtures;

/**
 * {@link CategoryCountServiceImpl} 单元测试。
 *
 * <p>使用 {@link MockExtensionClient}（内存 fake）验证实时计数逻辑：
 * <ul>
 *   <li>正常计数（单分类、多分类、空分类）</li>
 *   <li>已删除句子不计入（验证 {@code buildCountsMap} 中的 double-check 防御）</li>
 *   <li>异常数据（categoryName 为 null/blank）不计入任何分类</li>
 *   <li>{@code getCount} 边界处理（入参 null/blank）</li>
 * </ul>
 *
 * <p>说明：{@link MockExtensionClient#listAll} 不实现 fieldSelector 过滤，会返回该类型全部记录。
 * 因此 {@code getAllCounts} 内部对 {@code metadata.deletionTimestamp} 的过滤逻辑会被覆盖测试。
 */
@DisplayName("CategoryCountServiceImpl 单元测试")
class CategoryCountServiceImplTest {

    private CategoryCountServiceImpl serviceWith(ReactiveExtensionClient client) {
        return new CategoryCountServiceImpl(client);
    }

    // ==================== getAllCounts ====================

    @Test
    @DisplayName("getAllCounts：单分类 3 句子 → 计数为 3")
    void getAllCounts_singleCategoryMultipleSentences_countCorrect() {
        Category catA = TestFixtures.category("cat-a", "分类A");
        Sentence s1 = TestFixtures.sentence("s1", "内容1", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s2 = TestFixtures.sentence("s2", "内容2", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s3 = TestFixtures.sentence("s3", "内容3", "cat-a", "匿名", "未知", true, 0, 0);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(catA).with(s1).with(s2).with(s3).build();

        StepVerifier.create(serviceWith(client).getAllCounts())
            .assertNext(counts -> {
                assertThat(counts).containsEntry("cat-a", 3L);
                assertThat(counts).hasSize(1);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getAllCounts：多分类多句子 → 每个分类计数正确")
    void getAllCounts_multipleCategoriesEachCountCorrect() {
        Category catA = TestFixtures.category("cat-a", "分类A");
        Category catB = TestFixtures.category("cat-b", "分类B");
        Sentence s1 = TestFixtures.sentence("s1", "内容1", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s2 = TestFixtures.sentence("s2", "内容2", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s3 = TestFixtures.sentence("s3", "内容3", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s4 = TestFixtures.sentence("s4", "内容4", "cat-b", "匿名", "未知", true, 0, 0);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(catA).with(catB).with(s1).with(s2).with(s3).with(s4).build();

        StepVerifier.create(serviceWith(client).getAllCounts())
            .assertNext(counts -> {
                assertThat(counts).containsEntry("cat-a", 3L);
                assertThat(counts).containsEntry("cat-b", 1L);
                assertThat(counts).hasSize(2);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getAllCounts：空分类 → 计数为 0 但仍出现在 Map 中")
    void getAllCounts_emptyCategory_stillInMapWithZero() {
        Category catA = TestFixtures.category("cat-a", "分类A");

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(catA).build();

        StepVerifier.create(serviceWith(client).getAllCounts())
            .assertNext(counts -> {
                assertThat(counts).containsEntry("cat-a", 0L);
                assertThat(counts).hasSize(1);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getAllCounts：包含已删除句子 → 不计入（验证 buildCountsMap 防御逻辑）")
    void getAllCounts_includesDeletedSentences_notCounted() {
        Category catA = TestFixtures.category("cat-a", "分类A");
        Sentence s1 = TestFixtures.sentence("s1", "内容1", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s2 = TestFixtures.sentence("s2", "内容2", "cat-a", "匿名", "未知", true, 0, 0);
        // 已删除的 sentence（metadata.deletionTimestamp 非空）
        Sentence deleted = TestFixtures.sentence("deleted-s3", "内容3", "cat-a", "匿名", "未知", true, 0, 0);
        deleted.getMetadata().setDeletionTimestamp(Instant.now());

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(catA).with(s1).with(s2).with(deleted).build();

        StepVerifier.create(serviceWith(client).getAllCounts())
            .assertNext(counts -> {
                // 仅 s1 和 s2 计入，deleted 不计入
                assertThat(counts).containsEntry("cat-a", 2L);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getAllCounts：异常数据（categoryName 为 null/blank）→ 不计入任何分类")
    void getAllCounts_invalidCategoryName_notCounted() {
        Category catA = TestFixtures.category("cat-a", "分类A");
        // categoryName=null
        Sentence nullCat = TestFixtures.sentence("invalid-1", "内容", true);
        nullCat.getSpec().setCategoryName(null);
        // categoryName=""
        Sentence blankCat = TestFixtures.sentence("invalid-2", "内容", true);
        blankCat.getSpec().setCategoryName("");
        // 有效 sentence
        Sentence valid = TestFixtures.sentence("valid-3", "内容", "cat-a", "匿名", "未知", true, 0, 0);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(catA).with(nullCat).with(blankCat).with(valid).build();

        StepVerifier.create(serviceWith(client).getAllCounts())
            .assertNext(counts -> {
                // 仅 valid 计入 cat-a
                assertThat(counts).containsEntry("cat-a", 1L);
                assertThat(counts).hasSize(1);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getAllCounts：无任何数据 → 返回空 Map")
    void getAllCounts_noData_returnsEmptyMap() {
        ReactiveExtensionClient client = MockExtensionClient.builder().build();

        StepVerifier.create(serviceWith(client).getAllCounts())
            .assertNext(counts -> assertThat(counts).isEmpty())
            .verifyComplete();
    }

    // ==================== getCount 边界 ====================

    @Test
    @DisplayName("getCount：入参为 null → 返回 0")
    void getCount_null_returnsZero() {
        ReactiveExtensionClient client = MockExtensionClient.builder().build();

        StepVerifier.create(serviceWith(client).getCount(null))
            .assertNext(count -> assertThat(count).isEqualTo(0L))
            .verifyComplete();
    }

    @Test
    @DisplayName("getCount：入参为空字符串 → 返回 0")
    void getCount_blank_returnsZero() {
        ReactiveExtensionClient client = MockExtensionClient.builder().build();

        StepVerifier.create(serviceWith(client).getCount(""))
            .assertNext(count -> assertThat(count).isEqualTo(0L))
            .verifyComplete();
    }

    @Test
    @DisplayName("getCount：指定分类存在 → 返回正确数量")
    void getCount_validCategory_returnsCorrectCount() {
        Category catA = TestFixtures.category("cat-a", "分类A");
        Sentence s1 = TestFixtures.sentence("s1", "内容1", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s2 = TestFixtures.sentence("s2", "内容2", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s3 = TestFixtures.sentence("s3", "内容3", "cat-a", "匿名", "未知", true, 0, 0);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(catA).with(s1).with(s2).with(s3).build();

        // 注意：MockExtensionClient.countBy 不实现 fieldSelector 过滤，返回该类型全部记录数。
        // 此处验证方法链路正常返回（具体计数逻辑由 getAllCounts 测试覆盖）。
        StepVerifier.create(serviceWith(client).getCount("cat-a"))
            .assertNext(count -> assertThat(count).isEqualTo(3L))
            .verifyComplete();
    }
}
