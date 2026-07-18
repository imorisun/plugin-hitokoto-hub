package top.puresky.hitokotohub.service.similarity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文本相似度计算工具（纯函数，零 Spring 依赖）。
 *
 * <p>支持两种算法：
 * <ul>
 *   <li>TF-IDF 加权余弦相似度（COSINE）—— 适用于语义相关性</li>
 *   <li>Jaccard 系数（JACCARD）—— 适用于字符级相似性</li>
 * </ul>
 *
 * <p>分词采用字符二元组（bigram），适用于中文文本。
 * 提取自 {@code SimilarityCheckServiceImpl} 的 private 方法。
 */
public final class TextSimilarityCalculator {

    private TextSimilarityCalculator() {}

    /**
     * 将文本分词为字符二元组（bigram）集合。
     *
     * <p>例如 "你好世界" → {"你好", "好世", "世界"}
     *
     * @param text 输入文本
     * @return bigram 集合，空文本返回空集
     */
    public static Set<String> tokenizeToSet(String text) {
        if (text == null || text.length() < 2) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            tokens.add(text.substring(i, i + 2));
        }
        return tokens;
    }

    /**
     * 计算文本的词频（TF）向量。
     *
     * @param text 输入文本
     * @return bigram → 归一化词频
     */
    public static Map<String, Double> computeTfVector(String text) {
        Map<String, Double> tf = new HashMap<>();
        if (text == null || text.length() < 2) {
            return tf;
        }
        int total = 0;
        for (int i = 0; i < text.length() - 1; i++) {
            tf.merge(text.substring(i, i + 2), 1.0, Double::sum);
            total++;
        }
        if (total > 0) {
            for (Map.Entry<String, Double> e : tf.entrySet()) {
                e.setValue(e.getValue() / total);
            }
        }
        return tf;
    }

    /**
     * 计算所有文档的逆文档频率（IDF）。
     *
     * <p>公式：IDF(t) = log(N / (df(t) + 1)) + 1
     *
     * @param tokenSets 所有文档的 token 集合列表
     * @return token → IDF 权重
     */
    public static Map<String, Double> computeIdf(List<Set<String>> tokenSets) {
        Map<String, Integer> docFreq = new HashMap<>();
        int n = tokenSets.size();
        for (Set<String> tokens : tokenSets) {
            for (String token : tokens) {
                docFreq.merge(token, 1, Integer::sum);
            }
        }
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> e : docFreq.entrySet()) {
            idf.put(e.getKey(), Math.log((double) n / (e.getValue() + 1)) + 1);
        }
        return idf;
    }

    /**
     * 构建 TF-IDF 加权向量。
     *
     * @param tf  词频向量
     * @param idf IDF 权重表
     * @return TF-IDF 加权向量
     */
    public static Map<String, Double> computeTfidfVector(Map<String, Double> tf,
                                                          Map<String, Double> idf) {
        Map<String, Double> tfidf = new HashMap<>();
        for (Map.Entry<String, Double> e : tf.entrySet()) {
            Double idfVal = idf.get(e.getKey());
            if (idfVal != null) {
                tfidf.put(e.getKey(), e.getValue() * idfVal);
            }
        }
        return tfidf;
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * <p>公式：cos(θ) = (A·B) / (|A| × |B|)
     *
     * <p>优化：遍历较小的向量以减少查找次数。
     *
     * @param v1 向量1
     * @param v2 向量2
     * @return 余弦相似度 [0, 1]，空向量返回 0
     */
    public static double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        if (v1.isEmpty() || v2.isEmpty()) {
            return 0.0;
        }
        Map<String, Double> smaller = v1.size() <= v2.size() ? v1 : v2;
        Map<String, Double> larger = v1.size() <= v2.size() ? v2 : v1;

        double dotProduct = 0.0;
        for (Map.Entry<String, Double> e : smaller.entrySet()) {
            Double val = larger.get(e.getKey());
            if (val != null) {
                dotProduct += e.getValue() * val;
            }
        }

        double norm1 = vectorNorm(v1);
        double norm2 = vectorNorm(v2);
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        return dotProduct / (norm1 * norm2);
    }

    /**
     * 计算向量的 L2 范数。
     *
     * @param v 输入向量
     * @return √(Σ vᵢ²)
     */
    public static double vectorNorm(Map<String, Double> v) {
        double sum = 0.0;
        for (double val : v.values()) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算两个集合的 Jaccard 相似度。
     *
     * <p>公式：J(A, B) = |A ∩ B| / |A ∪ B|
     *
     * @param set1 集合1
     * @param set2 集合2
     * @return Jaccard 相似度 [0, 1]，两个空集返回 0
     */
    public static double jaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }
}
