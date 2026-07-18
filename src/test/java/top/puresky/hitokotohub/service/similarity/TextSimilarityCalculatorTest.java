package top.puresky.hitokotohub.service.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextSimilarityCalculator 文本相似度计算")
class TextSimilarityCalculatorTest {

    @Test
    @DisplayName("tokenizeToSet：中文文本正确切分为 bigram 集合")
    void shouldTokenizeToBigramSet() {
        Set<String> tokens = TextSimilarityCalculator.tokenizeToSet("你好世界");
        assertThat(tokens).containsExactlyInAnyOrder("你好", "好世", "世界");
        assertThat(tokens).hasSize(3);
    }

    @Test
    @DisplayName("tokenizeToSet：空串、null、单字返回空集")
    void shouldReturnEmptySetForShortText() {
        assertThat(TextSimilarityCalculator.tokenizeToSet("")).isEmpty();
        assertThat(TextSimilarityCalculator.tokenizeToSet(null)).isEmpty();
        assertThat(TextSimilarityCalculator.tokenizeToSet("a")).isEmpty();
    }

    @Test
    @DisplayName("tokenizeToSet：重复 bigram 在集合中去重")
    void shouldDeduplicateBigrams() {
        Set<String> tokens = TextSimilarityCalculator.tokenizeToSet("aaa");
        assertThat(tokens).containsExactly("aa");
    }

    @Test
    @DisplayName("computeTfVector：词频归一化，所有值之和为 1")
    void shouldComputeNormalizedTfVector() {
        Map<String, Double> tf = TextSimilarityCalculator.computeTfVector("你好世界");
        double sum = tf.values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(sum).isCloseTo(1.0, within(1e-9));
        assertThat(tf).hasSize(3);
        tf.values().forEach(v -> assertThat(v).isCloseTo(1.0 / 3, within(1e-9)));
    }

    @Test
    @DisplayName("computeTfVector：重复 bigram 权重更高")
    void shouldWeightRepeatedBigramsHigher() {
        Map<String, Double> tf = TextSimilarityCalculator.computeTfVector("aaa");
        assertThat(tf.get("aa")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("computeTfVector：空文本返回空向量")
    void shouldReturnEmptyTfForShortText() {
        assertThat(TextSimilarityCalculator.computeTfVector("")).isEmpty();
        assertThat(TextSimilarityCalculator.computeTfVector(null)).isEmpty();
    }

    @Test
    @DisplayName("computeIdf：N=3 且 df=1 时返回 log(3/2)+1")
    void shouldComputeIdf() {
        Set<String> doc1 = Set.of("你好", "世界");
        Set<String> doc2 = Set.of("世界", "和平");
        Set<String> doc3 = Set.of("和平", "友谊");
        Map<String, Double> idf = TextSimilarityCalculator.computeIdf(List.of(doc1, doc2, doc3));
        double expectedYou = Math.log(3.0 / 2) + 1;
        assertThat(idf.get("你好")).isCloseTo(expectedYou, within(1e-9));
        assertThat(idf.get("世界")).isCloseTo(1.0, within(1e-9));
        assertThat(idf.get("和平")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("computeTfidfVector：TF × IDF 加权")
    void shouldComputeTfidfVector() {
        Map<String, Double> tf = Map.of("你好", 0.5, "世界", 0.5);
        Map<String, Double> idf = Map.of("你好", 2.0, "世界", 1.0);
        Map<String, Double> tfidf = TextSimilarityCalculator.computeTfidfVector(tf, idf);
        assertThat(tfidf.get("你好")).isCloseTo(1.0, within(1e-9));
        assertThat(tfidf.get("世界")).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("computeTfidfVector：IDF 中缺失的 token 被跳过")
    void shouldSkipTokensMissingInIdf() {
        Map<String, Double> tf = Map.of("你好", 0.5, "缺失", 0.5);
        Map<String, Double> idf = Map.of("你好", 2.0);
        Map<String, Double> tfidf = TextSimilarityCalculator.computeTfidfVector(tf, idf);
        assertThat(tfidf).containsOnlyKeys("你好");
        assertThat(tfidf.get("你好")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("cosineSimilarity：相同向量相似度为 1.0")
    void shouldReturnOneForIdenticalVectors() {
        Map<String, Double> v = Map.of("a", 1.0, "b", 2.0, "c", 3.0);
        double sim = TextSimilarityCalculator.cosineSimilarity(v, v);
        assertThat(sim).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("cosineSimilarity：不相交向量相似度为 0.0")
    void shouldReturnZeroForDisjointVectors() {
        Map<String, Double> v1 = Map.of("a", 1.0, "b", 2.0);
        Map<String, Double> v2 = Map.of("c", 3.0, "d", 4.0);
        double sim = TextSimilarityCalculator.cosineSimilarity(v1, v2);
        assertThat(sim).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("cosineSimilarity：空向量返回 0.0")
    void shouldReturnZeroForEmptyVector() {
        Map<String, Double> empty = Map.of();
        Map<String, Double> nonEmpty = Map.of("a", 1.0);
        assertThat(TextSimilarityCalculator.cosineSimilarity(empty, nonEmpty)).isEqualTo(0.0);
        assertThat(TextSimilarityCalculator.cosineSimilarity(nonEmpty, empty)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("cosineSimilarity：部分重叠向量返回 0.5")
    void shouldReturnPartialSimilarity() {
        Map<String, Double> v1 = Map.of("a", 1.0, "b", 1.0);
        Map<String, Double> v2 = Map.of("b", 1.0, "c", 1.0);
        double sim = TextSimilarityCalculator.cosineSimilarity(v1, v2);
        assertThat(sim).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("vectorNorm：3-4-5 直角三角形范数为 5.0")
    void shouldComputeL2Norm() {
        Map<String, Double> v = Map.of("a", 3.0, "b", 4.0);
        double norm = TextSimilarityCalculator.vectorNorm(v);
        assertThat(norm).isCloseTo(5.0, within(1e-9));
    }

    @Test
    @DisplayName("vectorNorm：空向量范数为 0")
    void shouldReturnZeroNormForEmptyVector() {
        assertThat(TextSimilarityCalculator.vectorNorm(Map.of())).isEqualTo(0.0);
    }

    @Test
    @DisplayName("jaccardSimilarity：交集/并集计算正确")
    void shouldComputeJaccard() {
        Set<String> s1 = Set.of("a", "b");
        Set<String> s2 = Set.of("b", "c");
        double sim = TextSimilarityCalculator.jaccardSimilarity(s1, s2);
        assertThat(sim).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    @DisplayName("jaccardSimilarity：相同集合相似度为 1.0")
    void shouldReturnOneForIdenticalSets() {
        Set<String> s = Set.of("a", "b", "c");
        assertThat(TextSimilarityCalculator.jaccardSimilarity(s, s)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("jaccardSimilarity：不相交集合相似度为 0.0")
    void shouldReturnZeroForDisjointSets() {
        Set<String> s1 = Set.of("a", "b");
        Set<String> s2 = Set.of("c", "d");
        assertThat(TextSimilarityCalculator.jaccardSimilarity(s1, s2)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("jaccardSimilarity：两个空集返回 0.0（避免 0/0）")
    void shouldReturnZeroForTwoEmptySets() {
        assertThat(TextSimilarityCalculator.jaccardSimilarity(Set.of(), Set.of())).isEqualTo(0.0);
    }
}
