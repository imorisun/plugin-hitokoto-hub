package top.puresky.hitokotohub.service.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * {@link SimilarityPairFinder} 单元测试。
 *
 * <p>覆盖：阈值过滤、降序排序、配对上限、暴力法与倒排索引法结果等价、
 * JACCARD 算法路径。
 */
class SimilarityPairFinderTest {

    private SentenceProfile profile(String name, String content) {
        return new SentenceProfile(name, content, "cat", null, null, false, 0, 0);
    }

    @Test
    void findsIdenticalPairsAboveThreshold() {
        List<SentenceProfile> profiles = List.of(
            profile("a", "人生若只如初见何事秋风悲画扇"),
            profile("b", "人生若只如初见何事秋风悲画扇"),
            profile("c", "完全不同的另一句话内容毫不相关")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.8);
        assertEquals(1, pairs.size());
        assertEquals("a", pairs.get(0).sentence1Name());
        assertEquals("b", pairs.get(0).sentence2Name());
        assertEquals(1.0, pairs.get(0).similarity(), 1e-9);
    }

    @Test
    void thresholdFiltersOutLowSimilarityPairs() {
        List<SentenceProfile> profiles = List.of(
            profile("a", "春风又绿江南岸明月何时照我还"),
            profile("b", "春风又绿江南岸明月何时照我还"),
            profile("c", "天街小雨润如酥草色遥看近却无")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.99);
        assertEquals(1, pairs.size());
    }

    @Test
    void resultsAreSortedDescendingBySimilarity() {
        List<SentenceProfile> profiles = List.of(
            profile("a", "同一句话内容完全相同"),
            profile("b", "同一句话内容完全相同"),
            profile("c", "同一句话内容几乎相同"),
            profile("d", "同一句话内容几乎相同")
        );
        // c 与 a/b/d 部分相似,构造多对不同相似度的场景
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.0);
        for (int i = 1; i < pairs.size(); i++) {
            assertTrue(pairs.get(i - 1).similarity() >= pairs.get(i).similarity(),
                "结果应按相似度降序");
        }
    }

    @Test
    void jaccardAlgorithmPath() {
        List<SentenceProfile> profiles = List.of(
            profile("a", "人生若只如初见何事秋风悲画扇"),
            profile("b", "人生若只如初见何事秋风悲画扇")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "JACCARD", 0.8);
        assertEquals(1, pairs.size());
        assertEquals(1.0, pairs.get(0).similarity(), 1e-9);
    }

    @Test
    void emptyAndSingleProfileReturnEmpty() {
        assertTrue(SimilarityPairFinder.find(List.of(), "COSINE", 0.5).isEmpty());
        assertTrue(
            SimilarityPairFinder.find(List.of(profile("a", "x")), "COSINE", 0.5).isEmpty());
    }

    @Test
    void totalPairsFormula() {
        assertEquals(0, SimilarityPairFinder.totalPairs(0));
        assertEquals(0, SimilarityPairFinder.totalPairs(1));
        assertEquals(10, SimilarityPairFinder.totalPairs(5));
        assertEquals(45, SimilarityPairFinder.totalPairs(10));
    }

    @Test
    void maxPairsCapIsRespected() {
        // 20 条相同句子 → C(20,2)=190 对,cap 10 时只返回 10 条
        List<SentenceProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            profiles.add(profile("s" + i, "完全相同的句子内容用于触发大量配对"));
        }
        List<SentencePair> pairs =
            SimilarityPairFinder.find(profiles, "COSINE", 0.5, 10);
        assertEquals(10, pairs.size());
    }

    @Test
    void bruteForceAndInvertedIndexProduceEquivalentResults() {
        // 超过倒排索引启用阈值(500),验证两条路径结果一致
        List<SentenceProfile> profiles = new ArrayList<>();
        String[] contents = {
            "人生若只如初见何事秋风悲画扇",
            "人生若只如初见何事秋风悲画扇",
            "人生若只如初见何事秋风悲画扇",
            "春风又绿江南岸明月何时照我还",
            "春风又绿江南岸明月何时照我还",
            "天街小雨润如酥草色遥看近却无",
            "落霞与孤鹜齐飞秋水共长天一色"
        };
        for (int i = 0; i < 600; i++) {
            profiles.add(profile("s" + i, contents[i % contents.length]));
        }

        var data = SimilarityPairFinder.preprocess(profiles, "COSINE");
        List<SentencePair> brute =
            SimilarityPairFinder.findBruteForce(profiles, data, "COSINE", 0.8);
        List<SentencePair> inverted =
            SimilarityPairFinder.findWithInvertedIndex(profiles, data, "COSINE", 0.8);

        Function<List<SentencePair>, Set<String>> signature = pairs -> pairs.stream()
            .map(p -> p.sentence1Name() + "|" + p.sentence2Name())
            .collect(Collectors.toSet());
        assertEquals(signature.apply(brute), signature.apply(inverted));
        assertEquals(brute.size(), inverted.size());
    }

    @Test
    void pairSimilarityIsRoundedToFourDecimals() {
        List<SentenceProfile> profiles = List.of(
            profile("a", "部分相同的句子内容甲"),
            profile("b", "部分相同的句子内容乙")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "JACCARD", 0.0);
        assertFalse(pairs.isEmpty());
        for (SentencePair pair : pairs) {
            assertEquals(pair.similarity(), Math.round(pair.similarity() * 10000) / 10000.0,
                1e-12);
        }
    }

    @Test
    void similarPairsCarryFullProfileData() {
        List<SentenceProfile> profiles = List.of(
            new SentenceProfile("a", "内容甲", "cat1", "作者甲", "来源甲", true, 3, 100),
            new SentenceProfile("b", "内容甲", "cat2", "作者乙", "来源乙", false, 5, 200)
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.5);
        assertEquals(1, pairs.size());
        SentencePair pair = pairs.get(0);
        assertEquals("内容甲", pair.sentence1Content());
        assertEquals("cat2", pair.sentence2Category());
        assertEquals("作者乙", pair.sentence2Author());
        assertNotNull(pair.similarity());
    }
}
