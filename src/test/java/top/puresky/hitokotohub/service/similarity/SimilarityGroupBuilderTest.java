package top.puresky.hitokotohub.service.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.puresky.hitokotohub.extension.SimilarityGroup;

/**
 * {@link SimilarityGroupBuilder} 单元测试。
 *
 * <p>覆盖并查集分组、选最优、相似度查找、分页、批量删除名称收集。
 */
@DisplayName("SimilarityGroupBuilder 分组构建")
class SimilarityGroupBuilderTest {

    private final SimilarityGroupBuilder builder = new SimilarityGroupBuilder();

    /** 构造 SentenceProfile，控制评分关键属性。 */
    private static SentenceProfile profile(String name, String content, boolean published,
                                            long likeCount, long viewCount,
                                            String author, String source) {
        return new SentenceProfile(name, content, "cat", author, source,
            published, likeCount, viewCount);
    }

    /** 构造相似对（名称 + 相似度）。 */
    private static SentencePair pair(String n1, String n2, double similarity) {
        return new SentencePair(n1, "c1", "cat", "匿名", "未知",
            n2, "c2", "cat", "匿名", "未知", similarity);
    }

    /** 高分句子 A（发布+点赞+浏览+作者+来源）。 */
    private static SentenceProfile profileA() {
        return profile("A", "一".repeat(30), true, 10, 100, "作者", "来源");
    }

    /** 中分句子 B。 */
    private static SentenceProfile profileB() {
        return profile("B", "一".repeat(20), false, 5, 50, "匿名", "未知");
    }

    /** 低分句子 C。 */
    private static SentenceProfile profileC() {
        return profile("C", "短句", false, 0, 0, "匿名", "未知");
    }

    @Test
    @DisplayName("buildGroups：3 对传递相似 → 1 组，best 为评分最高，similarCount=2")
    void shouldBuildSingleGroupWithTransitiveSimilarity() {
        List<SentencePair> pairs = List.of(
            pair("A", "B", 0.9),
            pair("B", "C", 0.8),
            pair("A", "C", 0.7)
        );
        Map<String, SentenceProfile> profileMap = new HashMap<>();
        profileMap.put("A", profileA());
        profileMap.put("B", profileB());
        profileMap.put("C", profileC());

        List<SimilarityGroup> groups = builder.buildGroups(pairs, profileMap);

        assertThat(groups).hasSize(1);
        SimilarityGroup group = groups.get(0);
        assertThat(group.getBestSentence().getName()).isEqualTo("A");
        assertThat(group.getSimilarCount()).isEqualTo(2);
        // 相似句子按相似度降序：B(0.9) 在 C(0.7) 前
        assertThat(group.getSimilarSentences()).hasSize(2);
        assertThat(group.getSimilarSentences().get(0).getName()).isEqualTo("B");
        assertThat(group.getSimilarSentences().get(0).getSimilarity()).isCloseTo(0.9,
            org.assertj.core.api.Assertions.within(1e-4));
        assertThat(group.getSimilarSentences().get(1).getName()).isEqualTo("C");
        // 组内最高相似度 = 0.9
        assertThat(group.getMaxSimilarity()).isCloseTo(0.9,
            org.assertj.core.api.Assertions.within(1e-4));
    }

    @Test
    @DisplayName("buildGroups：空 pairs 返回空列表")
    void shouldReturnEmptyForEmptyPairs() {
        List<SimilarityGroup> groups = builder.buildGroups(List.of(), new HashMap<>());
        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("buildGroups：多组相似按 similarCount 降序")
    void shouldSortGroupsBySimilarCountDesc() {
        // 组1：A~B~C（3 个，similarCount=2）
        // 组2：D~E（2 个，similarCount=1）
        List<SentencePair> pairs = List.of(
            pair("A", "B", 0.9),
            pair("B", "C", 0.8),
            pair("A", "C", 0.7),
            pair("D", "E", 0.6)
        );
        Map<String, SentenceProfile> profileMap = new HashMap<>();
        profileMap.put("A", profileA());
        profileMap.put("B", profileB());
        profileMap.put("C", profileC());
        profileMap.put("D", profile("D", "一".repeat(20), true, 0, 0, "匿名", "未知"));
        profileMap.put("E", profile("E", "一".repeat(20), false, 0, 0, "匿名", "未知"));

        List<SimilarityGroup> groups = builder.buildGroups(pairs, profileMap);

        assertThat(groups).hasSize(2);
        // 第一组 similarCount=2，第二组 similarCount=1
        assertThat(groups.get(0).getSimilarCount()).isEqualTo(2);
        assertThat(groups.get(1).getSimilarCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("buildGroups：profileMap 中缺失的句子被过滤，组内不足 2 个则丢弃")
    void shouldFilterMissingProfiles() {
        // A~B~C，但 profileMap 中只有 A、B（C 缺失）
        List<SentencePair> pairs = List.of(
            pair("A", "B", 0.9),
            pair("B", "C", 0.8),
            pair("A", "C", 0.7)
        );
        Map<String, SentenceProfile> profileMap = new HashMap<>();
        profileMap.put("A", profileA());
        profileMap.put("B", profileB());
        // C 缺失

        List<SimilarityGroup> groups = builder.buildGroups(pairs, profileMap);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getSimilarCount()).isEqualTo(1);
        assertThat(groups.get(0).getBestSentence().getName()).isEqualTo("A");
    }

    @Test
    @DisplayName("collectNonOptimalNames：3 句子组保留评分最高，返回其余 2 个 name")
    void shouldCollectNonOptimalNames() {
        List<SentencePair> pairs = List.of(
            pair("A", "B", 0.9),
            pair("B", "C", 0.8),
            pair("A", "C", 0.7)
        );
        Map<String, SentenceProfile> profileMap = new HashMap<>();
        profileMap.put("A", profileA());
        profileMap.put("B", profileB());
        profileMap.put("C", profileC());

        Set<String> toDelete = builder.collectNonOptimalNames(pairs, profileMap);

        assertThat(toDelete).containsExactlyInAnyOrder("B", "C");
        assertThat(toDelete).doesNotContain("A");
    }

    @Test
    @DisplayName("collectNonOptimalNames：空 pairs 返回空集")
    void shouldReturnEmptySetForEmptyPairs() {
        Set<String> toDelete = builder.collectNonOptimalNames(List.of(), new HashMap<>());
        assertThat(toDelete).isEmpty();
    }

    @Test
    @DisplayName("collectNonOptimalNames：多组各自保留最优")
    void shouldKeepBestInEachGroup() {
        // 组1：A(高)~B(中)~C(低) → 保留 A
        // 组2：D(高)~E(低) → 保留 D
        List<SentencePair> pairs = List.of(
            pair("A", "B", 0.9),
            pair("B", "C", 0.8),
            pair("D", "E", 0.6)
        );
        Map<String, SentenceProfile> profileMap = new HashMap<>();
        profileMap.put("A", profileA());
        profileMap.put("B", profileB());
        profileMap.put("C", profileC());
        profileMap.put("D", profile("D", "一".repeat(20), true, 0, 0, "匿名", "未知"));
        profileMap.put("E", profile("E", "短句", false, 0, 0, "匿名", "未知"));

        Set<String> toDelete = builder.collectNonOptimalNames(pairs, profileMap);

        assertThat(toDelete).containsExactlyInAnyOrder("B", "C", "E");
        assertThat(toDelete).doesNotContain("A", "D");
    }

    @Test
    @DisplayName("paginate：5 组 page=1 size=2 → total=5，groups.size=2")
    void shouldPaginateGroups() {
        // 构造 5 个分组
        List<SentencePair> pairs = List.of(
            pair("A1", "A2", 0.9),
            pair("B1", "B2", 0.9),
            pair("C1", "C2", 0.9),
            pair("D1", "D2", 0.9),
            pair("E1", "E2", 0.9)
        );
        Map<String, SentenceProfile> profileMap = new HashMap<>();
        for (String n : List.of("A1", "A2", "B1", "B2", "C1", "C2", "D1", "D2", "E1", "E2")) {
            profileMap.put(n, profile(n, "一".repeat(20), false, 0, 0, "匿名", "未知"));
        }

        List<SimilarityGroup> allGroups = builder.buildGroups(pairs, profileMap);
        assertThat(allGroups).hasSize(5);

        Map<String, Object> page1 = builder.paginate(allGroups, 1, 2);
        assertThat(page1.get("page")).isEqualTo(1);
        assertThat(page1.get("size")).isEqualTo(2);
        assertThat(page1.get("total")).isEqualTo(5);
        assertThat((List<?>) page1.get("groups")).hasSize(2);

        Map<String, Object> page3 = builder.paginate(allGroups, 3, 2);
        assertThat(((List<?>) page3.get("groups"))).hasSize(1);
    }

    @Test
    @DisplayName("paginate：page 超出范围返回空 groups")
    void shouldReturnEmptyWhenPageOutOfRange() {
        List<SimilarityGroup> groups = builder.buildGroups(
            List.of(pair("A", "B", 0.9)),
            Map.of("A", profileA(), "B", profileB()));

        Map<String, Object> result = builder.paginate(groups, 10, 5);
        assertThat(result.get("total")).isEqualTo(1);
        assertThat(((List<?>) result.get("groups"))).isEmpty();
    }

    @Test
    @DisplayName("emptyResult：total=0，groups 为空列表")
    void shouldReturnEmptyResult() {
        Map<String, Object> result = builder.emptyResult(1, 10);

        assertThat(result.get("page")).isEqualTo(1);
        assertThat(result.get("size")).isEqualTo(10);
        assertThat(result.get("total")).isEqualTo(0);
        assertThat((List<?>) result.get("groups")).isEmpty();
    }
}
