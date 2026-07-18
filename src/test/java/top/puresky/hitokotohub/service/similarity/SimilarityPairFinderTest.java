package top.puresky.hitokotohub.service.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SimilarityPairFinder} 单元测试。
 *
 * <p>覆盖 COSINE/JACCARD 两种算法路径、阈值过滤、降序排序、配对总数计算。
 */
@DisplayName("SimilarityPairFinder 相似对查找")
class SimilarityPairFinderTest {

    private static SentenceProfile profile(String name, String content) {
        return new SentenceProfile(name, content, "cat", "匿名", "未知", false, 0, 0);
    }

    @Test
    @DisplayName("totalPairs：n=5 时返回 10")
    void shouldComputeTotalPairs() {
        assertThat(SimilarityPairFinder.totalPairs(5)).isEqualTo(10L);
        assertThat(SimilarityPairFinder.totalPairs(0)).isEqualTo(0L);
        assertThat(SimilarityPairFinder.totalPairs(1)).isEqualTo(0L);
        assertThat(SimilarityPairFinder.totalPairs(3)).isEqualTo(3L);
    }

    @Test
    @DisplayName("COSINE：3 个完全相同内容，低阈值返回 3 对且降序")
    void shouldFindAllPairsForIdenticalContent() {
        List<SentenceProfile> profiles = List.of(
            profile("s1", "你好世界你好世界"),
            profile("s2", "你好世界你好世界"),
            profile("s3", "你好世界你好世界")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.5);
        assertThat(pairs).hasSize(3);
        assertThat(pairs).allSatisfy(p ->
            assertThat(p.similarity()).isCloseTo(1.0, within(1e-4)));
        for (int i = 1; i < pairs.size(); i++) {
            assertThat(pairs.get(i - 1).similarity())
                .isGreaterThanOrEqualTo(pairs.get(i).similarity());
        }
    }

    @Test
    @DisplayName("COSINE：阈值 1.0 仅返回完全相同的句子对")
    void shouldFilterByThreshold() {
        List<SentenceProfile> profiles = List.of(
            profile("s1", "你好世界你好世界"),
            profile("s2", "你好世界你好世界"),
            profile("s3", "完全不同的内容在此")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 1.0);
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).sentence1Name()).isIn("s1", "s2");
        assertThat(pairs.get(0).sentence2Name()).isIn("s1", "s2");
    }

    @Test
    @DisplayName("COSINE：高阈值过滤掉所有不相似对，返回空列表")
    void shouldReturnEmptyWhenNoPairReachesThreshold() {
        List<SentenceProfile> profiles = List.of(
            profile("s1", "春天来了花朵绽放"),
            profile("s2", "秋天到了落叶纷飞"),
            profile("s3", "冬夜寒冷雪花飘落")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.99);
        assertThat(pairs).isEmpty();
    }

    @Test
    @DisplayName("JACCARD：相同内容相似度为 1.0")
    void shouldFindIdenticalContentWithJaccard() {
        List<SentenceProfile> profiles = List.of(
            profile("s1", "你好世界"),
            profile("s2", "你好世界")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "JACCARD", 0.5);
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).similarity()).isCloseTo(1.0, within(1e-4));
    }
    @Test
    @DisplayName("JACCARD：大小写不敏感的算法名也能识别")
    void shouldAcceptCaseInsensitiveAlgorithmName() {
        List<SentenceProfile> profiles = List.of(
            profile("s1", "你好世界"),
            profile("s2", "你好世界")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "jaccard", 0.5);
        assertThat(pairs).hasSize(1);
    }

    @Test
    @DisplayName("空句子列表返回空对")
    void shouldReturnEmptyForEmptyProfiles() {
        List<SentencePair> pairs = SimilarityPairFinder.find(List.of(), "COSINE", 0.5);
        assertThat(pairs).isEmpty();
    }

    @Test
    @DisplayName("单个句子返回空对")
    void shouldReturnEmptyForSingleProfile() {
        List<SentencePair> pairs = SimilarityPairFinder.find(
            List.of(profile("s1", "内容")), "COSINE", 0.5);
        assertThat(pairs).isEmpty();
    }

    @Test
    @DisplayName("SentencePair 字段完整填充：name/content/category/author/source")
    void shouldPopulateAllFieldsInPair() {
        List<SentenceProfile> profiles = List.of(
            profile("s1", "你好世界"),
            profile("s2", "你好世界")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.5);
        SentencePair pair = pairs.get(0);
        assertThat(pair.sentence1Content()).isEqualTo("你好世界");
        assertThat(pair.sentence1Category()).isEqualTo("cat");
        assertThat(pair.sentence1Author()).isEqualTo("匿名");
        assertThat(pair.sentence1Source()).isEqualTo("未知");
        assertThat(pair.sentence2Content()).isEqualTo("你好世界");
    }

    @Test
    @DisplayName("相似度截断到 4 位小数")
    void shouldTruncateSimilarityToFourDecimals() {
        List<SentenceProfile> profiles = List.of(
            profile("s1", "你好世界你好"),
            profile("s2", "你好世界你好世界")
        );
        List<SentencePair> pairs = SimilarityPairFinder.find(profiles, "COSINE", 0.0);
        assertThat(pairs).hasSize(1);
        double sim = pairs.get(0).similarity();
        double remainder = (sim * 10000) - Math.floor(sim * 10000);
        assertThat(remainder).isLessThan(1e-9);
    }
}