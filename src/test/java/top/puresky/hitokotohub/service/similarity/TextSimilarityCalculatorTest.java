package top.puresky.hitokotohub.service.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link TextSimilarityCalculator} 单元测试。
 */
class TextSimilarityCalculatorTest {

    @Test
    void tokenizeBasicChineseText() {
        Set<String> tokens = TextSimilarityCalculator.tokenizeToSet("你好世界");
        assertEquals(Set.of("你好", "好世", "世界"), tokens);
    }

    @Test
    void tokenizeNullOrShortTextReturnsEmpty() {
        assertTrue(TextSimilarityCalculator.tokenizeToSet(null).isEmpty());
        assertTrue(TextSimilarityCalculator.tokenizeToSet("a").isEmpty());
        assertTrue(TextSimilarityCalculator.tokenizeToSet("").isEmpty());
    }

    @Test
    void tfVectorIsNormalized() {
        Map<String, Double> tf = TextSimilarityCalculator.computeTfVector("aaa");
        assertEquals(1.0, tf.get("aa"), 1e-9);
    }

    @Test
    void tfVectorSumsToOne() {
        Map<String, Double> tf = TextSimilarityCalculator.computeTfVector("你好世界");
        double sum = tf.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void cosineOfIdenticalVectorsIsOne() {
        Map<String, Double> v = Map.of("你好", 0.5, "好世", 0.5);
        assertEquals(1.0, TextSimilarityCalculator.cosineSimilarity(v, v), 1e-9);
    }

    @Test
    void cosineOfDisjointVectorsIsZero() {
        Map<String, Double> v1 = Map.of("你好", 1.0);
        Map<String, Double> v2 = Map.of("世界", 1.0);
        assertEquals(0.0, TextSimilarityCalculator.cosineSimilarity(v1, v2), 1e-9);
    }

    @Test
    void cosineWithEmptyVectorIsZero() {
        assertEquals(0.0, TextSimilarityCalculator.cosineSimilarity(Map.of(), Map.of()), 1e-9);
        assertEquals(0.0,
            TextSimilarityCalculator.cosineSimilarity(Map.of(), Map.of("a", 1.0)), 1e-9);
    }

    @Test
    void jaccardOfIdenticalSetsIsOne() {
        Set<String> set = Set.of("你好", "好世");
        assertEquals(1.0, TextSimilarityCalculator.jaccardSimilarity(set, set), 1e-9);
    }

    @Test
    void jaccardOfDisjointSetsIsZero() {
        assertEquals(0.0,
            TextSimilarityCalculator.jaccardSimilarity(Set.of("你好"), Set.of("世界")), 1e-9);
    }

    @Test
    void jaccardOfTwoEmptySetsIsZero() {
        assertEquals(0.0,
            TextSimilarityCalculator.jaccardSimilarity(Set.of(), Set.of()), 1e-9);
    }

    @Test
    void jaccardPartialOverlap() {
        // {a,b,c} ∩ {a,b,d} = {a,b}, union = {a,b,c,d} → 0.5
        double result = TextSimilarityCalculator.jaccardSimilarity(
            Set.of("a", "b", "c"), Set.of("a", "b", "d"));
        assertEquals(0.5, result, 1e-9);
    }

    @Test
    void idfRareTokenGetsHigherWeightThanCommonToken() {
        var common = Set.of("a");
        var docs = List.of(
            Set.of("a", "x"),
            Set.of("a", "y"),
            Set.of("a", "z")
        );
        Map<String, Double> idf = TextSimilarityCalculator.computeIdf(docs);
        assertTrue(idf.get("x") > idf.get("a"),
            "稀有 token 的 IDF 应高于出现在所有文档中的 token");
    }

    @Test
    void tfidfVectorOnlyKeepsKnownTokens() {
        Map<String, Double> tf = Map.of("a", 1.0, "missing", 1.0);
        Map<String, Double> idf = Map.of("a", 2.0);
        Map<String, Double> tfidf = TextSimilarityCalculator.computeTfidfVector(tf, idf);
        assertEquals(1, tfidf.size());
        assertEquals(2.0, tfidf.get("a"), 1e-9);
    }
}
