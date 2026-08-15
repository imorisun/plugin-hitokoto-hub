package top.puresky.hitokotohub.service.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import top.puresky.hitokotohub.extension.SimilarityGroup;

/**
 * {@link SimilarityGroupBuilder} 单元测试。
 */
class SimilarityGroupBuilderTest {

    private final SimilarityGroupBuilder builder = new SimilarityGroupBuilder();

    private SentenceProfile profile(String name, String content, long likeCount) {
        return new SentenceProfile(name, content, "cat", "作者", "来源", true, likeCount, 100);
    }

    private Map<String, SentenceProfile> mapOf(SentenceProfile... profiles) {
        Map<String, SentenceProfile> map = new HashMap<>();
        for (SentenceProfile p : profiles) {
            map.put(p.name(), p);
        }
        return map;
    }

    @Test
    void buildGroupsMergesTransitivePairsAndPicksBest() {
        // a~b, b~c → 一组;a 点赞最高应为最优
        SentenceProfile a = profile("a", "内容甲内容甲内容甲内容甲内容甲", 10);
        SentenceProfile b = profile("b", "内容甲内容甲内容甲内容甲内容甲", 0);
        SentenceProfile c = profile("c", "内容甲内容甲内容甲内容甲内容甲", 2);
        List<SentencePair> pairs = List.of(
            new SentencePair("a", "内容甲", "cat", "作者", "来源",
                "b", "内容甲", "cat", "作者", "来源", 0.95),
            new SentencePair("b", "内容甲", "cat", "作者", "来源",
                "c", "内容甲", "cat", "作者", "来源", 0.9)
        );
        List<SimilarityGroup> groups = builder.buildGroups(pairs, mapOf(a, b, c));
        assertEquals(1, groups.size());
        SimilarityGroup group = groups.get(0);
        assertEquals("a", group.getGroupId());
        assertEquals("a", group.getBestSentence().getName());
        assertEquals(2, group.getSimilarCount());
    }

    @Test
    void buildGroupsFiltersDeletedSentences() {
        SentenceProfile a = profile("a", "内容甲内容甲内容甲内容甲内容甲", 1);
        SentenceProfile b = profile("b", "内容甲内容甲内容甲内容甲内容甲", 2);
        SentenceProfile c = profile("c", "内容甲内容甲内容甲内容甲内容甲", 3);
        // c 已删除(不在 profileMap 中),且组内只剩 a、b → a~c、b~c 两对只剩 a~b
        List<SentencePair> pairs = List.of(
            new SentencePair("a", "内容甲", "cat", "作者", "来源",
                "b", "内容甲", "cat", "作者", "来源", 0.9),
            new SentencePair("a", "内容甲", "cat", "作者", "来源",
                "c", "内容甲", "cat", "作者", "来源", 0.9)
        );
        List<SimilarityGroup> groups = builder.buildGroups(pairs, mapOf(a, b));
        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).getSimilarCount());
        assertTrue(groups.get(0).getSimilarSentences().stream()
            .noneMatch(info -> "c".equals(info.getName())));
    }

    @Test
    void buildGroupsEmptyWhenPairsReferOnlyDeletedSentences() {
        SentenceProfile a = profile("a", "内容甲内容甲内容甲内容甲内容甲", 1);
        List<SentencePair> pairs = List.of(
            new SentencePair("a", "内容甲", "cat", "作者", "来源",
                "gone", "内容甲", "cat", "作者", "来源", 0.9)
        );
        assertTrue(builder.buildGroups(pairs, mapOf(a)).isEmpty());
    }

    @Test
    void collectNonOptimalNamesKeepsHighestScoredPerGroup() {
        SentenceProfile a = profile("a", "内容甲内容甲内容甲内容甲内容甲", 0);
        SentenceProfile b = profile("b", "内容甲内容甲内容甲内容甲内容甲", 5);
        SentenceProfile c = profile("c", "内容甲内容甲内容甲内容甲内容甲", 2);
        List<SentencePair> pairs = List.of(
            new SentencePair("a", "内容甲", "cat", "作者", "来源",
                "b", "内容甲", "cat", "作者", "来源", 0.95),
            new SentencePair("b", "内容甲", "cat", "作者", "来源",
                "c", "内容甲", "cat", "作者", "来源", 0.9)
        );
        Set<String> toDelete =
            builder.collectNonOptimalNames(pairs, mapOf(a, b, c));
        assertEquals(Set.of("a", "c"), toDelete);
    }

    @Test
    void paginateReturnsCorrectWindow() {
        SentenceProfile a = profile("a", "内容甲内容甲内容甲内容甲内容甲", 1);
        SentenceProfile b = profile("b", "内容甲内容甲内容甲内容甲内容甲", 2);
        SentenceProfile c = profile("c", "内容甲内容甲内容甲内容甲内容甲", 3);
        List<SentencePair> pairs = List.of(
            new SentencePair("a", "x", "cat", null, null, "b", "x", "cat", null, null, 0.9),
            new SentencePair("a", "x", "cat", null, null, "c", "x", "cat", null, null, 0.9)
        );
        List<SimilarityGroup> groups = builder.buildGroups(pairs, mapOf(a, b, c));

        Map<String, Object> page1 = builder.paginate(groups, 1, 1);
        assertEquals(1, page1.get("page"));
        assertEquals(1, page1.get("total"));
        assertEquals(1, ((List<?>) page1.get("groups")).size());

        Map<String, Object> page2 = builder.paginate(groups, 2, 1);
        assertTrue(((List<?>) page2.get("groups")).isEmpty());
    }

    @Test
    void emptyResultShape() {
        Map<String, Object> result = builder.emptyResult(3, 10);
        assertEquals(3, result.get("page"));
        assertEquals(10, result.get("size"));
        assertEquals(0, result.get("total"));
        assertTrue(((List<?>) result.get("groups")).isEmpty());
    }
}
