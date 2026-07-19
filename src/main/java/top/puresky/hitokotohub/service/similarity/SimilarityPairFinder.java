package top.puresky.hitokotohub.service.similarity;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
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
 * <p><b>性能优化</b>：当句子数 ≥ {@link #INVERTED_INDEX_THRESHOLD} 时，自动启用倒排索引优化。
 * 利用"相似度 &gt; 0 ⟺ 共享至少一个 bigram"的性质，仅对共享 bigram 的句子对计算相似度，
 * 将配对复杂度从 O(n²) 降至 O(n·k·c)，其中 k 为单句 bigram 数、c 为单 bigram 平均命中数。
 * 对 n=1000 的典型场景可减少约 10x 配对计算量。
 *
 * <p>提取自 {@code SimilarityCheckServiceImpl.calculateSimilarPairs}。
 */
public final class SimilarityPairFinder {

    /**
     * 倒排索引自动启用阈值。低于此值使用暴力法（更简单、无构建开销），
     * 达到此值使用倒排索引（大数据集性能更优）。两算法结果集理论等价。
     */
    static final int INVERTED_INDEX_THRESHOLD = 500;

    private SimilarityPairFinder() {}

    /**
     * 计算完整的相似对列表（按相似度降序）。
     *
     * <p>根据句子数自动选择算法：n &lt; {@value #INVERTED_INDEX_THRESHOLD} 用暴力法，
     * 否则用倒排索引优化。两者结果集等价（相似度 &gt; 0 的必要条件是共享至少一个 bigram）。
     *
     * @param profiles  句子纯数据列表
     * @param algorithm 算法（COSINE 或 JACCARD，大小写不敏感）
     * @param threshold 相似度阈值 [0, 1]
     * @return 达到阈值的相似对列表（按相似度降序）
     */
    public static List<SentencePair> find(List<SentenceProfile> profiles,
                                           String algorithm, double threshold) {
        int n = profiles.size();
        if (n < 2) {
            return Collections.emptyList();
        }

        // 预处理（两种算法路径共享）
        PreprocessedData data = preprocess(profiles, algorithm);

        // 根据规模分派
        if (n < INVERTED_INDEX_THRESHOLD) {
            return findBruteForce(profiles, data, algorithm, threshold);
        }
        return findWithInvertedIndex(profiles, data, algorithm, threshold);
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

    // ===================== 预处理 =====================

    /**
     * 预处理：生成特征向量（contents / tfVectors / tokenSets / tfidfVectors）。
     *
     * <p>提取为独立方法，供暴力法与倒排索引法共享，避免重复代码。
     *
     * <p>包级可见以便等价性测试构造 {@link PreprocessedData}。
     */
    static PreprocessedData preprocess(List<SentenceProfile> profiles,
                                         String algorithm) {
        int n = profiles.size();
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

        return new PreprocessedData(contents, tfVectors, tokenSets, tfidfVectors);
    }

    // ===================== 暴力法（n &lt; 阈值） =====================

    /**
     * 暴力两两比对，O(n²) 配对。
     *
     * <p>适用于小数据集，无倒排索引构建开销。
     *
     * <p>包级可见以便等价性测试直接调用,与 {@link #findWithInvertedIndex} 结果对比。
     */
    static List<SentencePair> findBruteForce(List<SentenceProfile> profiles,
                                              PreprocessedData data,
                                              String algorithm, double threshold) {
        int n = profiles.size();
        List<SentencePair> similarPairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double similarity = computeSimilarity(data, algorithm, i, j);
                if (similarity >= threshold) {
                    similarPairs.add(buildPair(profiles.get(i), profiles.get(j), similarity));
                }
            }
        }
        similarPairs.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        return similarPairs;
    }

    // ===================== 倒排索引法（n ≥ 阈值） =====================

    /**
     * 基于倒排索引的相似对查找。
     *
     * <p>核心思想：相似度 &gt; 0 的必要条件是两句子共享至少一个 bigram。
     * 因此先构建 bigram → 句子索引的倒排表，仅对共享 bigram 的句子对计算相似度，
     * 大幅减少无效配对计算。
     *
     * <p>步骤：
     * <ol>
     *   <li>构建倒排索引：Map&lt;bigram, List&lt;sentenceIndex&gt;&gt;</li>
     *   <li>对每个句子 i，收集所有 j &gt; i 且与 i 共享至少一个 bigram 的候选 j</li>
     *   <li>仅对候选对计算相似度，过滤阈值</li>
     * </ol>
     *
     * <p>包级可见以便等价性测试直接调用。
     */
    static List<SentencePair> findWithInvertedIndex(List<SentenceProfile> profiles,
                                                      PreprocessedData data,
                                                      String algorithm,
                                                      double threshold) {
        int n = profiles.size();
        List<Set<String>> tokenSets = data.tokenSets();

        // 1. 构建倒排索引：bigram → 句子索引列表
        Map<String, List<Integer>> invertedIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (String bigram : tokenSets.get(i)) {
                invertedIndex.computeIfAbsent(bigram, k -> new ArrayList<>()).add(i);
            }
        }

        // 2. 收集候选对并计算相似度
        List<SentencePair> similarPairs = new ArrayList<>();
        BitSet candidates = new BitSet(n);
        for (int i = 0; i < n; i++) {
            // 收集所有 j > i 且与 i 共享至少一个 bigram 的候选
            candidates.clear();
            Set<String> bigrams = tokenSets.get(i);
            if (bigrams.isEmpty()) {
                continue;
            }
            for (String bigram : bigrams) {
                List<Integer> postings = invertedIndex.get(bigram);
                if (postings == null) {
                    continue;
                }
                for (int j : postings) {
                    if (j > i) {
                        candidates.set(j);
                    }
                }
            }

            // 仅对候选 j 计算相似度
            for (int j = candidates.nextSetBit(0); j >= 0; j = candidates.nextSetBit(j + 1)) {
                double similarity = computeSimilarity(data, algorithm, i, j);
                if (similarity >= threshold) {
                    similarPairs.add(buildPair(profiles.get(i), profiles.get(j), similarity));
                }
            }
        }

        similarPairs.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        return similarPairs;
    }

    // ===================== 工具方法 =====================

    /** 根据算法类型计算两个句子的相似度。 */
    private static double computeSimilarity(PreprocessedData data, String algorithm,
                                             int i, int j) {
        if ("JACCARD".equalsIgnoreCase(algorithm)) {
            return TextSimilarityCalculator.jaccardSimilarity(
                data.tokenSets().get(i), data.tokenSets().get(j));
        }
        return TextSimilarityCalculator.cosineSimilarity(
            data.tfidfVectors().get(i), data.tfidfVectors().get(j));
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

    /**
     * 预处理结果，包含特征向量和分词集合。
     *
     * <p>提取为 record 以便暴力法与倒排索引法共享预处理结果,并支持等价性测试。
     */
    record PreprocessedData(
        List<String> contents,
        List<Map<String, Double>> tfVectors,
        List<Set<String>> tokenSets,
        List<Map<String, Double>> tfidfVectors
    ) {}
}
