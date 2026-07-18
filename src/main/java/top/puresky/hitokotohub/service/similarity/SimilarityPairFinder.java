package top.puresky.hitokotohub.service.similarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 句子相似对查找器（纯函数，零 Spring/Extension 依赖）。
 *
 * <p>核心算法：
 * <ol>
 *   <li>对每个句子生成 bigram 分词集合和 TF 向量</li>
 *   <li>计算全局 IDF 权重（仅 COSINE 算法）</li>
 *   <li>构建 TF-IDF 加权向量（仅 COSINE 算法）</li>
 *   <li>两两比对，筛选达到阈值的相似对</li>
 * </ol>
 *
 * <p>提取自 {@code SimilarityCheckServiceImpl.calculateSimilarPairs}。
 */
public final class SimilarityPairFinder {

    private SimilarityPairFinder() {}

    /**
     * 计算完整的相似对列表（按相似度降序）。
     *
     * @param profiles  句子纯数据列表
     * @param algorithm 算法（COSINE 或 JACCARD，大小写不敏感）
     * @param threshold 相似度阈值 [0, 1]
     * @return 达到阈值的相似对列表（按相似度降序）
     */
    public static List<SentencePair> find(List<SentenceProfile> profiles,
                                           String algorithm, double threshold) {
        int n = profiles.size();

        // 预处理：生成特征向量
        List<String> contents = new ArrayList<>(n);
        List<Map<String, Double>> tfVectors = new ArrayList<>(n);
        List<Set<String>> tokenSets = new ArrayList<>(n);

        for (SentenceProfile p : profiles) {
            String content = p.content() != null ? p.content().trim() : "";
            contents.add(content);
            tfVectors.add(TextSimilarityCalculator.computeTfVector(content));
            tokenSets.add(TextSimilarityCalculator.tokenizeToSet(content));
        }

        // 计算 TF-IDF 向量（仅余弦相似度需要）
        List<Map<String, Double>> tfidfVectors = Collections.emptyList();
        if (!"JACCARD".equalsIgnoreCase(algorithm)) {
            Map<String, Double> idfMap = TextSimilarityCalculator.computeIdf(tokenSets);
            tfidfVectors = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                tfidfVectors.add(
                    TextSimilarityCalculator.computeTfidfVector(tfVectors.get(i), idfMap));
            }
        }

        // 两两比对
        List<SentencePair> similarPairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double similarity;
                if ("JACCARD".equalsIgnoreCase(algorithm)) {
                    similarity = TextSimilarityCalculator.jaccardSimilarity(
                        tokenSets.get(i), tokenSets.get(j));
                } else {
                    similarity = TextSimilarityCalculator.cosineSimilarity(
                        tfidfVectors.get(i), tfidfVectors.get(j));
                }
                if (similarity >= threshold) {
                    similarPairs.add(buildPair(profiles.get(i), profiles.get(j), similarity));
                }
            }
        }

        // 按相似度降序
        similarPairs.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        return similarPairs;
    }

    /**
     * 计算 n 个句子的两两配对总数。
     *
     * @param n 句子数
     * @return n(n-1)/2
     */
    public static long totalPairs(int n) {
        return (long) n * (n - 1) / 2;
    }

    /** 从两个 SentenceProfile 和相似度值构建 SentencePair。 */
    private static SentencePair buildPair(SentenceProfile s1, SentenceProfile s2,
                                           double similarity) {
        return new SentencePair(
            s1.name(), s1.content(), s1.categoryName(), s1.author(), s1.source(),
            s2.name(), s2.content(), s2.categoryName(), s2.author(), s2.source(),
            Math.round(similarity * 10000) / 10000.0
        );
    }
}
