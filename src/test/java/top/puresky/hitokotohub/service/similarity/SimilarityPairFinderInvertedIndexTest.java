package top.puresky.hitokotohub.service.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 倒排索引路径的等价性测试。
 *
 * <p>验证 {@link SimilarityPairFinder#findWithInvertedIndex} 与
 * {@link SimilarityPairFinder#findBruteForce} 在多种规模和算法下结果集一致,
 * 以及 {@link SimilarityPairFinder#find} 公共入口在阈值边界的正确分派。
 *
 * <p>设计要点:
 * <ul>
 *   <li>使用 10 个互不相同的古诗词模板派生句子,确保跨模板无 bigram 共享,
 *       模板内共享基础 bigram,从而有效触发倒排索引候选收集逻辑</li>
 *   <li>阈值统一用 0.3(必须 &gt; 0):倒排索引依赖"相似度 &gt; 0 ⟺ 共享 bigram"性质,
 *       阈值=0 时暴力法会返回所有 pair(含 sim=0)而倒排索引会跳过无共享 bigram 的 pair,
 *       导致语义不一致</li>
 *   <li>等价性比较用 key 集合(sentence1Name|sentence2Name)而非 List 顺序相等,
 *       避免相同 similarity 的 tie 顺序差异导致 flaky</li>
 * </ul>
 */
@DisplayName("SimilarityPairFinder 倒排索引等价性测试")
class SimilarityPairFinderInvertedIndexTest {

    /** 测试用相似度阈值,必须 > 0 以保证两算法语义一致。 */
    private static final double THRESHOLD = 0.3;

    /** 性能基准时间上限(毫秒),宽松阈值避免 CI 环境噪声导致 flaky。 */
    private static final long TIME_LIMIT_MS = 10_000L;

    /**
     * 生成 n 个句子 profile,基于固定种子保证可重现。
     *
     * <p>使用 10 个古诗词模板轮转 + 随机字母后缀(0-4 字符),制造相似但不完全相同的句子。
     * n=1000 时每模板派生 100 句,模板内句子共享基础 6 个 bigram,跨模板无 bigram 共享。
     */
    private static List<SentenceProfile> generateProfiles(int n, long seed) {
        Random rnd = new Random(seed);
        // 10 个互不相同的古诗词模板,确保跨模板无 bigram 共享
        String[] templates = {
            "春风又绿江南岸", "明月何时照我还", "千山鸟飞绝", "万径人踪灭",
            "孤舟蓑笠翁", "独钓寒江雪", "白日依山尽", "黄河入海流",
            "欲穷千里目", "更上一层楼"
        };
        List<SentenceProfile> profiles = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String base = templates[i % templates.length];
            int suffixLen = rnd.nextInt(5); // 0-4 个随机后缀字符
            StringBuilder content = new StringBuilder(base);
            for (int k = 0; k < suffixLen; k++) {
                content.append((char) ('a' + rnd.nextInt(26)));
            }
            profiles.add(new SentenceProfile(
                "sentence-" + i, content.toString(),
                "test-cat", "匿名", "未知", false, 0, 0
            ));
        }
        return profiles;
    }

    /**
     * 断言两种算法产生的 pair 集合等价。
     *
     * <p>比较规则:
     * <ol>
     *   <li>pair 数量相同</li>
     *   <li>pair key 集合相同(key = sentence1Name + "|" + sentence2Name)</li>
     *   <li>每个 pair 的 similarity 精确相等(同一 computeSimilarity 函数 + 同一 Math.round 截断)</li>
     * </ol>
     */
    private static void assertPairSetsEquivalent(List<SentencePair> expected,
                                                  List<SentencePair> actual) {
        assertThat(actual).hasSameSizeAs(expected);

        // 两种算法都用 i<j 顺序构建 pair,sentence1Name/sentence2Name 顺序一致
        Map<String, Double> expectedMap = new HashMap<>(expected.size());
        for (SentencePair p : expected) {
            expectedMap.put(p.sentence1Name() + "|" + p.sentence2Name(), p.similarity());
        }
        Map<String, Double> actualMap = new HashMap<>(actual.size());
        for (SentencePair p : actual) {
            actualMap.put(p.sentence1Name() + "|" + p.sentence2Name(), p.similarity());
        }

        assertThat(actualMap.keySet())
            .as("pair key 集合应一致")
            .isEqualTo(expectedMap.keySet());

        // 相似度值应精确相等
        for (String key : expectedMap.keySet()) {
            assertThat(actualMap.get(key))
                .as("pair [%s] 的 similarity", key)
                .isEqualTo(expectedMap.get(key));
        }
    }

    // ===================== 等价性测试 =====================

    @Test
    @DisplayName("COSINE: n=500(临界点)倒排索引与暴力法结果集等价")
    void shouldProduceSameResultAsBruteForceWhenCosineN500() {
        List<SentenceProfile> profiles = generateProfiles(500, 42L);
        SimilarityPairFinder.PreprocessedData data =
            SimilarityPairFinder.preprocess(profiles, "COSINE");

        List<SentencePair> brute = SimilarityPairFinder.findBruteForce(
            profiles, data, "COSINE", THRESHOLD);
        List<SentencePair> inverted = SimilarityPairFinder.findWithInvertedIndex(
            profiles, data, "COSINE", THRESHOLD);

        assertPairSetsEquivalent(brute, inverted);
    }

    @Test
    @DisplayName("JACCARD: n=500(临界点)倒排索引与暴力法结果集等价")
    void shouldProduceSameResultAsBruteForceWhenJaccardN500() {
        List<SentenceProfile> profiles = generateProfiles(500, 42L);
        SimilarityPairFinder.PreprocessedData data =
            SimilarityPairFinder.preprocess(profiles, "JACCARD");

        List<SentencePair> brute = SimilarityPairFinder.findBruteForce(
            profiles, data, "JACCARD", THRESHOLD);
        List<SentencePair> inverted = SimilarityPairFinder.findWithInvertedIndex(
            profiles, data, "JACCARD", THRESHOLD);

        assertPairSetsEquivalent(brute, inverted);
    }

    @Test
    @DisplayName("COSINE: n=1000(大数据集)倒排索引与暴力法结果集等价")
    void shouldProduceSameResultAsBruteForceWhenCosineN1000() {
        List<SentenceProfile> profiles = generateProfiles(1000, 7L);
        SimilarityPairFinder.PreprocessedData data =
            SimilarityPairFinder.preprocess(profiles, "COSINE");

        List<SentencePair> brute = SimilarityPairFinder.findBruteForce(
            profiles, data, "COSINE", THRESHOLD);
        List<SentencePair> inverted = SimilarityPairFinder.findWithInvertedIndex(
            profiles, data, "COSINE", THRESHOLD);

        assertPairSetsEquivalent(brute, inverted);
    }

    @Test
    @DisplayName("JACCARD: n=1000(大数据集)倒排索引与暴力法结果集等价")
    void shouldProduceSameResultAsBruteForceWhenJaccardN1000() {
        List<SentenceProfile> profiles = generateProfiles(1000, 7L);
        SimilarityPairFinder.PreprocessedData data =
            SimilarityPairFinder.preprocess(profiles, "JACCARD");

        List<SentencePair> brute = SimilarityPairFinder.findBruteForce(
            profiles, data, "JACCARD", THRESHOLD);
        List<SentencePair> inverted = SimilarityPairFinder.findWithInvertedIndex(
            profiles, data, "JACCARD", THRESHOLD);

        assertPairSetsEquivalent(brute, inverted);
    }

    // ===================== 分发测试 =====================

    @Test
    @DisplayName("分发: n=阈值-1 时 find() 走暴力法路径")
    void shouldDispatchToBruteForceWhenBelowThreshold() {
        int n = SimilarityPairFinder.INVERTED_INDEX_THRESHOLD - 1;
        List<SentenceProfile> profiles = generateProfiles(n, 1L);
        SimilarityPairFinder.PreprocessedData data =
            SimilarityPairFinder.preprocess(profiles, "COSINE");

        List<SentencePair> viaFind = SimilarityPairFinder.find(profiles, "COSINE", THRESHOLD);
        List<SentencePair> viaBruteForce = SimilarityPairFinder.findBruteForce(
            profiles, data, "COSINE", THRESHOLD);

        // find() 内部直接委托给 findBruteForce,List 应完全相等(含顺序)
        assertThat(viaFind).isEqualTo(viaBruteForce);
    }

    @Test
    @DisplayName("分发: n=阈值 时 find() 走倒排索引路径")
    void shouldDispatchToInvertedIndexWhenAtThreshold() {
        int n = SimilarityPairFinder.INVERTED_INDEX_THRESHOLD;
        List<SentenceProfile> profiles = generateProfiles(n, 1L);
        SimilarityPairFinder.PreprocessedData data =
            SimilarityPairFinder.preprocess(profiles, "COSINE");

        List<SentencePair> viaFind = SimilarityPairFinder.find(profiles, "COSINE", THRESHOLD);
        List<SentencePair> viaInverted = SimilarityPairFinder.findWithInvertedIndex(
            profiles, data, "COSINE", THRESHOLD);

        // find() 内部直接委托给 findWithInvertedIndex,List 应完全相等(含顺序)
        assertThat(viaFind).isEqualTo(viaInverted);
    }

    // ===================== 性能基准 =====================

    @Test
    @DisplayName("性能: n=1000 时两种算法均能在 10s 内完成,且结果等价")
    void shouldCompleteLargeDatasetWithinTimeLimit() {
        List<SentenceProfile> profiles = generateProfiles(1000, 7L);
        SimilarityPairFinder.PreprocessedData data =
            SimilarityPairFinder.preprocess(profiles, "COSINE");

        long t1 = System.nanoTime();
        List<SentencePair> brute = SimilarityPairFinder.findBruteForce(
            profiles, data, "COSINE", THRESHOLD);
        long t2 = System.nanoTime();
        List<SentencePair> inverted = SimilarityPairFinder.findWithInvertedIndex(
            profiles, data, "COSINE", THRESHOLD);
        long t3 = System.nanoTime();

        long bruteMs = (t2 - t1) / 1_000_000;
        long invertedMs = (t3 - t2) / 1_000_000;

        // 宽松时间上限,避免 CI 环境噪声导致 flaky
        assertThat(bruteMs)
            .as("暴力法耗时")
            .isLessThan(TIME_LIMIT_MS);
        assertThat(invertedMs)
            .as("倒排索引耗时")
            .isLessThan(TIME_LIMIT_MS);

        // 结果集必须等价
        assertPairSetsEquivalent(brute, inverted);

        // 至少应产生一些相似对,否则测试无法有效验证候选收集逻辑
        assertThat(brute).isNotEmpty();
    }
}
