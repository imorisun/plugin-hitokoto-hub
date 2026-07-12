package top.puresky.hitokotohub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.extension.SimilarityCheckLog.SimilarityPair;
import top.puresky.hitokotohub.extension.SimilarityGroup;
import top.puresky.hitokotohub.extension.SimilarityGroup.SentenceInfo;
import top.puresky.hitokotohub.service.SimilarityCheckService;

/**
 * 句子相似度检查服务实现。
 *
 * <p>核心功能包括：
 * <ul>
 *   <li>基于 TF-IDF 余弦相似度或 Jaccard 系数的全量句子比对</li>
 *   <li>使用并查集（Union-Find）对相似句子进行传递性分组</li>
 *   <li>按句子质量评分选出每组最优句子，支持批量删除非最优句子</li>
 * </ul>
 *
 * <p>性能特征：
 * <ul>
 *   <li>相似度计算：O(n²)，n 为句子总数</li>
 *   <li>分组构建：O(m·α(n))，m 为相似对数，α 为反阿克曼函数（近似常数）</li>
 *   <li>相似度查找：O(1)，通过预构建的 similarityMap 实现</li>
 * </ul>
 */
@Slf4j
@Service
public class SimilarityCheckServiceImpl implements SimilarityCheckService {

    /** 日志中存储的相似对最大数量，超出部分不持久化（但可通过重新计算恢复） */
    private static final int MAX_STORED_PAIRS = 500;

    private final ReactiveExtensionClient client;
    private final ObjectMapper objectMapper;

    public SimilarityCheckServiceImpl(ReactiveExtensionClient client,
                                      ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    // ===================== 公共 API =====================

    /**
     * {@inheritDoc}
     *
     * <p>执行流程：
     * <ol>
     *   <li>创建 RUNNING 状态的检查日志</li>
     *   <li>在 boundedElastic 线程池中执行全量相似度计算</li>
     *   <li>更新日志为 SUCCESS（或 FAILED）状态</li>
     *   <li>清理旧日志，仅保留最新一条</li>
     * </ol>
     *
     * @param triggerType 触发类型，MANUAL 或 SCHEDULED
     * @param triggeredBy 触发者用户名
     * @param algorithm   相似度算法，COSINE 或 JACCARD
     * @param threshold   相似度阈值 [0, 1]，低于此值的句子对不计入
     * @return 包含完整检查结果的日志对象
     */
    @Override
    public Mono<SimilarityCheckLog> performCheck(
        SimilarityCheckLog.TriggerType triggerType,
        String triggeredBy,
        String algorithm,
        double threshold
    ) {
        SimilarityCheckLog logEntry = createInitialLog(triggerType, triggeredBy, algorithm,
            threshold);
        return client.create(logEntry)
            .flatMap(created -> Mono.fromCallable(
                    () -> executeCheck(created, algorithm, threshold))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(client::update)
                .flatMap(updated -> deleteOldLogs(updated).thenReturn(updated))
                .onErrorResume(e -> {
                    log.error("相似度检查失败", e);
                    created.getSpec().setStatus(SimilarityCheckLog.Status.FAILED);
                    created.getSpec().setErrorMessage(e.getMessage());
                    return client.update(created);
                }));
    }

    /**
     * {@inheritDoc}
     *
     * <p>从最新成功日志中解析相似对，使用并查集分组，过滤已删除句子后分页返回。
     *
     * @param page 页码，从 1 开始
     * @param size 每页分组数量
     * @return 包含 page、size、total、groups 的 Map
     */
    @Override
    public Mono<Map<String, Object>> getGroups(int page, int size) {
        return getLatestSuccessLog()
            .flatMap(latestLog -> Mono.fromCallable(() ->
                buildGroupsResult(latestLog, page, size))
            .subscribeOn(Schedulers.boundedElastic()))
            .switchIfEmpty(Mono.just(emptyGroupsResult(page, size)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>执行流程：
     * <ol>
     *   <li>从最新成功日志获取算法和阈值</li>
     *   <li>重新计算完整相似对列表（不受 {@link #MAX_STORED_PAIRS} 限制）</li>
     *   <li>并查集分组，每组保留评分最高的句子</li>
     *   <li>串行删除非最优句子（避免 Category 乐观锁冲突）</li>
     * </ol>
     *
     * @return 实际删除的句子数量
     */
    @Override
    public Mono<Integer> deleteNonOptimalSentences() {
        return getLatestSuccessLog()
            .flatMap(latestLog -> {
                if (latestLog == null) {
                    return Mono.just(0);
                }
                String algorithm = latestLog.getSpec().getAlgorithm();
                double threshold = latestLog.getSpec().getThreshold();
                return fetchAllSentences().flatMap(sentences -> {
                    if (sentences.isEmpty()) {
                        return Mono.just(0);
                    }
                    return Mono.fromCallable(() ->
                        collectNonOptimalNames(sentences, algorithm, threshold))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(toDelete -> deleteSentencesSerially(toDelete));
                });
            });
    }

    // ===================== 检查执行 =====================

    /**
     * 创建初始检查日志（RUNNING 状态）。
     *
     * @param triggerType 触发类型
     * @param triggeredBy 触发者
     * @param algorithm   算法
     * @param threshold   阈值
     * @return 待创建的日志对象
     */
    private SimilarityCheckLog createInitialLog(
        SimilarityCheckLog.TriggerType triggerType,
        String triggeredBy,
        String algorithm,
        double threshold
    ) {
        SimilarityCheckLog logEntry = new SimilarityCheckLog();
        logEntry.setMetadata(new Metadata());
        logEntry.getMetadata().setGenerateName("similarity-check-");
        SimilarityCheckLog.Spec spec = new SimilarityCheckLog.Spec();
        spec.setTriggerType(triggerType);
        spec.setTriggeredBy(triggeredBy);
        spec.setAlgorithm(algorithm);
        spec.setThreshold(threshold);
        spec.setStatus(SimilarityCheckLog.Status.RUNNING);
        logEntry.setSpec(spec);
        return logEntry;
    }

    /**
     * 执行全量相似度计算并填充日志结果。
     *
     * <p>此方法在 boundedElastic 线程池中执行，可安全调用阻塞操作。
     *
     * @param logEntry  日志对象（RUNNING 状态）
     * @param algorithm 算法
     * @param threshold 阈值
     * @return 填充完结果的日志对象（SUCCESS 状态）
     */
    private SimilarityCheckLog executeCheck(
        SimilarityCheckLog logEntry, String algorithm, double threshold
    ) {
        long startTime = System.currentTimeMillis();
        List<Sentence> sentences = fetchAllSentences().block();

        if (sentences == null || sentences.isEmpty()) {
            populateEmptyResult(logEntry, startTime);
            return logEntry;
        }

        SimilarityResult result = calculateSimilarPairs(sentences, algorithm, threshold);
        List<SimilarityPair> storedPairs = result.similarPairs.size() > MAX_STORED_PAIRS
            ? result.similarPairs.subList(0, MAX_STORED_PAIRS) : result.similarPairs;

        String pairsJson = serializePairs(storedPairs);

        logEntry.getSpec().setTotalSentences(result.totalSentences);
        logEntry.getSpec().setTotalPairs(result.totalPairs);
        logEntry.getSpec().setSimilarPairCount(result.similarPairCount);
        logEntry.getSpec().setSimilarPairs(pairsJson);
        logEntry.getSpec().setStatus(SimilarityCheckLog.Status.SUCCESS);
        logEntry.getSpec().setDurationMs(System.currentTimeMillis() - startTime);

        log.info("相似度检查完成: {} 个句子, {} 个配对, {} 个相似对, 耗时 {}ms",
            result.totalSentences, result.totalPairs, result.similarPairCount,
            logEntry.getSpec().getDurationMs());
        return logEntry;
    }

    /**
     * 检查完成后删除所有旧日志，仅保留当前日志。
     *
     * @param currentLog 当前完成的日志
     * @return 表示操作完成的 Mono
     */
    private Mono<Void> deleteOldLogs(SimilarityCheckLog currentLog) {
        String currentName = currentLog.getMetadata().getName();
        return client.listAll(SimilarityCheckLog.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .filter(l -> !currentName.equals(l.getMetadata().getName()))
            .flatMap(client::delete)
            .then();
    }

    // ===================== 相似度算法 =====================

    /**
     * 相似度计算结果载体。
     *
     * @param totalSentences   参与检查的句子总数
     * @param totalPairs       比对总对数 = n(n-1)/2
     * @param similarPairCount 相似对数（达到阈值）
     * @param similarPairs     相似对列表（按相似度降序）
     */
    private record SimilarityResult(
        int totalSentences,
        long totalPairs,
        int similarPairCount,
        List<SimilarityPair> similarPairs
    ) {}

    /**
     * 计算完整的相似对列表。
     *
     * <p>算法流程：
     * <ol>
     *   <li>对每个句子生成 bigram 分词集合和 TF 向量</li>
     *   <li>计算全局 IDF 权重</li>
     *   <li>构建 TF-IDF 加权向量</li>
     *   <li>两两比对，筛选达到阈值的相似对</li>
     * </ol>
     *
     * @param sentences 句子列表
     * @param algorithm 算法（COSINE 或 JACCARD）
     * @param threshold 相似度阈值
     * @return 相似度计算结果
     */
    private SimilarityResult calculateSimilarPairs(
        List<Sentence> sentences, String algorithm, double threshold
    ) {
        int n = sentences.size();
        long totalPairs = (long) n * (n - 1) / 2;

        // 预处理：生成特征向量
        List<String> contents = new ArrayList<>(n);
        List<Map<String, Double>> tfVectors = new ArrayList<>(n);
        List<Set<String>> tokenSets = new ArrayList<>(n);

        for (Sentence s : sentences) {
            String content = s.getSpec().getContent() != null
                ? s.getSpec().getContent().trim() : "";
            contents.add(content);
            tfVectors.add(computeTfVector(content));
            tokenSets.add(tokenizeToSet(content));
        }

        // 计算 TF-IDF 向量（仅余弦相似度需要）
        List<Map<String, Double>> tfidfVectors = Collections.emptyList();
        if (!"JACCARD".equalsIgnoreCase(algorithm)) {
            Map<String, Double> idfMap = computeIdf(tokenSets);
            tfidfVectors = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                tfidfVectors.add(computeTfidfVector(tfVectors.get(i), idfMap));
            }
        }

        // 两两比对
        List<SimilarityPair> similarPairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double similarity;
                if ("JACCARD".equalsIgnoreCase(algorithm)) {
                    similarity = jaccardSimilarity(tokenSets.get(i), tokenSets.get(j));
                } else {
                    similarity = cosineSimilarity(tfidfVectors.get(i), tfidfVectors.get(j));
                }
                if (similarity >= threshold) {
                    similarPairs.add(buildPair(sentences.get(i), sentences.get(j), similarity));
                }
            }
        }

        // 按相似度降序
        similarPairs.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        return new SimilarityResult(n, totalPairs, similarPairs.size(), similarPairs);
    }

    /**
     * 构建 {@link SimilarityPair} 对象。
     *
     * @param s1        句子1
     * @param s2        句子2
     * @param similarity 相似度值
     * @return 相似对对象
     */
    private SimilarityPair buildPair(Sentence s1, Sentence s2, double similarity) {
        SimilarityPair pair = new SimilarityPair();
        pair.setSentence1Name(s1.getMetadata().getName());
        pair.setSentence1Content(s1.getSpec().getContent());
        pair.setSentence1Category(s1.getSpec().getCategoryName());
        pair.setSentence1Author(s1.getSpec().getAuthor());
        pair.setSentence1Source(s1.getSpec().getSource());
        pair.setSentence2Name(s2.getMetadata().getName());
        pair.setSentence2Content(s2.getSpec().getContent());
        pair.setSentence2Category(s2.getSpec().getCategoryName());
        pair.setSentence2Author(s2.getSpec().getAuthor());
        pair.setSentence2Source(s2.getSpec().getSource());
        pair.setSimilarity(Math.round(similarity * 10000) / 10000.0);
        return pair;
    }

    // ===================== 文本处理 =====================

    /**
     * 将文本分词为字符二元组（bigram）集合，适用于中文文本。
     *
     * <p>例如 "你好世界" → {"你好", "好世", "世界"}
     *
     * @param text 输入文本
     * @return bigram 集合，空文本返回空集
     */
    private Set<String> tokenizeToSet(String text) {
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
    private Map<String, Double> computeTfVector(String text) {
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
    private Map<String, Double> computeIdf(List<Set<String>> tokenSets) {
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
    private Map<String, Double> computeTfidfVector(Map<String, Double> tf,
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
    private double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
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
    private double vectorNorm(Map<String, Double> v) {
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
    private double jaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }

    // ===================== 分组构建 =====================

    /**
     * 从检查日志构建分组结果（分页）。
     *
     * <p>流程：解析相似对 → 并查集分组 → 过滤已删除句子 → 构建分组信息 → 分页
     *
     * @param latestLog 最新成功日志，null 则返回空结果
     * @param page      页码
     * @param size      每页数量
     * @return 分页分组结果
     */
    private Map<String, Object> buildGroupsResult(SimilarityCheckLog latestLog, int page, int size) {
        if (latestLog == null) {
            return emptyGroupsResult(page, size);
        }

        List<SimilarityPair> pairs = parseSimilarPairs(latestLog);
        if (pairs.isEmpty()) {
            return emptyGroupsResult(page, size);
        }

        // 并查集分组
        UnionFind uf = UnionFind.fromPairs(pairs);
        Map<String, Set<String>> groupMembers = uf.groupByRoot();

        // 预构建相似度查找表：key = "name1|name2"（按字典序），O(1) 查找
        Map<String, Double> similarityMap = buildSimilarityMap(pairs);

        // 获取涉及句子的完整信息（已删除的不会出现）
        Map<String, Sentence> sentenceMap = fetchSentencesByName(uf.allNames());

        // 构建每个分组
        List<SimilarityGroup> groups = new ArrayList<>();
        for (Set<String> memberNames : groupMembers.values()) {
            if (memberNames.size() < 2) continue;

            SimilarityGroup group = buildGroup(memberNames, sentenceMap, similarityMap);
            if (group != null) {
                groups.add(group);
            }
        }

        // 按相似句子数量降序
        groups.sort(Comparator.comparingInt(SimilarityGroup::getSimilarCount).reversed());

        return paginateGroups(groups, page, size);
    }

    /**
     * 解析日志中的相似对 JSON。
     *
     * @param checkLog 检查日志
     * @return 相似对列表，解析失败返回空列表
     */
    private List<SimilarityPair> parseSimilarPairs(SimilarityCheckLog checkLog) {
        try {
            return objectMapper.readValue(checkLog.getSpec().getSimilarPairs(),
                new TypeReference<List<SimilarityPair>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析相似对 JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建相似度查找表。
     *
     * <p>Key 格式：{@code min(name1, name2) + "|" + max(name1, name2)}
     * 以保证双向查找的一致性。
     *
     * @param pairs 相似对列表
     * @return 相似度查找表，O(1) 查找
     */
    private Map<String, Double> buildSimilarityMap(List<SimilarityPair> pairs) {
        Map<String, Double> map = new HashMap<>(pairs.size());
        for (SimilarityPair pair : pairs) {
            String key = similarityKey(pair.getSentence1Name(), pair.getSentence2Name());
            map.put(key, pair.getSimilarity());
        }
        return map;
    }

    /**
     * 生成相似度查找的 key。
     *
     * @param name1 句子1名称
     * @param name2 句子2名称
     * @return 规范化的 key（字典序小的在前）
     */
    private String similarityKey(String name1, String name2) {
        return name1.compareTo(name2) <= 0
            ? name1 + "|" + name2
            : name2 + "|" + name1;
    }

    /**
     * 查找两个句子之间的相似度。
     *
     * @param similarityMap 相似度查找表
     * @param name1         句子1名称
     * @param name2         句子2名称
     * @return 相似度值，未找到返回 0
     */
    private double getSimilarity(Map<String, Double> similarityMap, String name1, String name2) {
        return similarityMap.getOrDefault(similarityKey(name1, name2), 0.0);
    }

    /**
     * 构建单个相似分组。
     *
     * <p>流程：过滤已删除句子 → 按评分排序选最优 → 构建相似句子列表 → 计算统计指标
     *
     * @param memberNames  组内句子名称集合
     * @param sentenceMap  句子名称 → 句子对象的映射
     * @param similarityMap 相似度查找表
     * @return 分组对象，若有效句子不足 2 个则返回 null
     */
    private SimilarityGroup buildGroup(Set<String> memberNames,
                                       Map<String, Sentence> sentenceMap,
                                       Map<String, Double> similarityMap) {
        // 过滤已删除句子
        List<Sentence> groupSentences = memberNames.stream()
            .map(sentenceMap::get)
            .filter(s -> s != null)
            .collect(Collectors.toList());

        if (groupSentences.size() < 2) {
            return null;
        }

        // 按评分降序，第一个为最优
        groupSentences.sort(Comparator.comparingDouble(s -> -SimilarityGroup.scoreSentence(s)));

        Sentence best = groupSentences.get(0);
        double bestScore = SimilarityGroup.scoreSentence(best);

        // 构建相似句子列表
        List<SentenceInfo> similarInfos = new ArrayList<>(groupSentences.size() - 1);
        double maxSim = 0;
        double sumSim = 0;

        for (int i = 1; i < groupSentences.size(); i++) {
            Sentence other = groupSentences.get(i);
            double sim = getSimilarity(similarityMap,
                best.getMetadata().getName(), other.getMetadata().getName());
            maxSim = Math.max(maxSim, sim);
            sumSim += sim;
            similarInfos.add(buildSentenceInfo(other,
                SimilarityGroup.scoreSentence(other), sim));
        }

        // 按相似度降序
        similarInfos.sort(Comparator.comparingDouble(SentenceInfo::getSimilarity).reversed());

        int similarCount = similarInfos.size();
        double avgSim = similarCount > 0 ? sumSim / similarCount : 0;

        return SimilarityGroup.builder()
            .groupId(best.getMetadata().getName())
            .bestSentence(buildSentenceInfo(best, bestScore, 0))
            .bestSentenceScore(bestScore)
            .similarSentences(similarInfos)
            .similarCount(similarCount)
            .maxSimilarity(Math.round(maxSim * 10000.0) / 10000.0)
            .avgSimilarity(Math.round(avgSim * 10000.0) / 10000.0)
            .build();
    }

    /**
     * 对分组列表进行分页。
     *
     * @param groups 全量分组列表
     * @param page   页码
     * @param size   每页数量
     * @return 包含分页信息的 Map
     */
    private Map<String, Object> paginateGroups(List<SimilarityGroup> groups, int page, int size) {
        int total = groups.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);
        List<SimilarityGroup> pageGroups = fromIndex < total
            ? groups.subList(fromIndex, toIndex) : Collections.emptyList();

        Map<String, Object> result = new HashMap<>(4);
        result.put("page", page);
        result.put("size", size);
        result.put("total", total);
        result.put("groups", pageGroups);
        return result;
    }

    /**
     * 构建空分组结果。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 空结果的 Map
     */
    private Map<String, Object> emptyGroupsResult(int page, int size) {
        Map<String, Object> result = new HashMap<>(4);
        result.put("page", page);
        result.put("size", size);
        result.put("total", 0);
        result.put("groups", Collections.emptyList());
        return result;
    }

    // ===================== 批量删除 =====================

    /**
     * 收集所有非最优句子名称。
     *
     * <p>对每组相似句子，保留评分最高的，其余标记为待删除。
     *
     * @param sentences  全量句子列表
     * @param algorithm  算法
     * @param threshold  阈值
     * @return 待删除句子名称集合
     */
    private Set<String> collectNonOptimalNames(List<Sentence> sentences,
                                               String algorithm, double threshold) {
        SimilarityResult result = calculateSimilarPairs(sentences, algorithm, threshold);
        if (result.similarPairs().isEmpty()) {
            return Collections.emptySet();
        }

        UnionFind uf = UnionFind.fromPairs(result.similarPairs());
        Map<String, Set<String>> groupMembers = uf.groupByRoot();

        Map<String, Sentence> sentenceMap = sentences.stream()
            .collect(Collectors.toMap(
                s -> s.getMetadata().getName(), s -> s, (a, b) -> a));

        Set<String> toDelete = new HashSet<>();
        for (Set<String> memberNames : groupMembers.values()) {
            if (memberNames.size() < 2) continue;

            // 找出评分最高的句子作为最优
            Sentence best = null;
            double bestScore = -1;
            for (String name : memberNames) {
                Sentence s = sentenceMap.get(name);
                if (s == null) continue;
                double score = SimilarityGroup.scoreSentence(s);
                if (score > bestScore) {
                    bestScore = score;
                    best = s;
                }
            }

            // 其余全部标记删除
            String bestName = best != null ? best.getMetadata().getName() : null;
            for (String name : memberNames) {
                if (!name.equals(bestName)) {
                    toDelete.add(name);
                }
            }
        }
        return toDelete;
    }

    /**
     * 串行删除句子，避免并发触发 reconciler 导致 Category 乐观锁冲突。
     *
     * @param names 待删除句子名称集合
     * @return 实际删除数量
     */
    private Mono<Integer> deleteSentencesSerially(Set<String> names) {
        if (names.isEmpty()) {
            return Mono.just(0);
        }
        log.info("批量删除非最优句子，共 {} 个待删除", names.size());

        return Flux.fromIterable(names)
            .concatMap(name -> client.fetch(Sentence.class, name)
                .flatMap(s -> client.delete(s)
                    .onErrorResume(e -> {
                        log.warn("删除句子 {} 失败: {}", name, e.getMessage());
                        return Mono.empty();
                    }))
                .switchIfEmpty(Mono.empty()))
            .count()
            .map(Long::intValue)
            .delayElement(Duration.ofSeconds(1))
            .doOnSuccess(count ->
                log.info("批量删除非最优句子完成，共删除 {} 个句子", count));
    }

    // ===================== 数据访问 =====================

    /**
     * 获取最新一条 SUCCESS 状态的检查日志。
     *
     * @return 最新成功日志的 Mono，无则返回空 Mono
     */
    private Mono<SimilarityCheckLog> getLatestSuccessLog() {
        return client.listAll(SimilarityCheckLog.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").descending())
            .filter(l -> l.getSpec().getStatus() == SimilarityCheckLog.Status.SUCCESS)
            .next();
    }

    /**
     * 获取所有句子（按创建时间升序）。
     *
     * @return 句子列表的 Mono
     */
    private Mono<List<Sentence>> fetchAllSentences() {
        return client.listAll(Sentence.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .collectList();
    }

    /**
     * 按名称集合精确获取句子（避免全量 listAll + filter）。
     *
     * <p>使用 concatMap 逐个 fetch，避免阻塞。在 boundedElastic 线程中调用。
     *
     * @param names 句子名称集合
     * @return 句子名称 → 句子对象的映射
     */
    private Map<String, Sentence> fetchSentencesByName(Set<String> names) {
        Map<String, Sentence> map = new HashMap<>(names.size());
        for (String name : names) {
            client.fetch(Sentence.class, name).blockOptional().ifPresent(s -> map.put(name, s));
        }
        return map;
    }

    // ===================== 工具方法 =====================

    /**
     * 序列化相似对列表为 JSON 字符串。
     *
     * @param pairs 相似对列表
     * @return JSON 字符串，序列化失败返回 "[]"
     */
    private String serializePairs(List<SimilarityPair> pairs) {
        try {
            return objectMapper.writeValueAsString(pairs);
        } catch (JsonProcessingException e) {
            log.error("序列化相似对失败", e);
            return "[]";
        }
    }

    /**
     * 填充空结果的检查日志。
     *
     * @param logEntry  日志对象
     * @param startTime 检查开始时间戳
     */
    private void populateEmptyResult(SimilarityCheckLog logEntry, long startTime) {
        logEntry.getSpec().setTotalSentences(0);
        logEntry.getSpec().setTotalPairs(0);
        logEntry.getSpec().setSimilarPairCount(0);
        logEntry.getSpec().setSimilarPairs("[]");
        logEntry.getSpec().setStatus(SimilarityCheckLog.Status.SUCCESS);
        logEntry.getSpec().setDurationMs(System.currentTimeMillis() - startTime);
    }

    /**
     * 构建 {@link SentenceInfo} 对象。
     *
     * @param s          句子对象
     * @param score      质量评分
     * @param similarity 与最优句子的相似度
     * @return 句子信息对象
     */
    private SentenceInfo buildSentenceInfo(Sentence s, double score, double similarity) {
        return SentenceInfo.builder()
            .name(s.getMetadata().getName())
            .content(s.getSpec().getContent())
            .category(s.getSpec().getCategoryName())
            .author(s.getSpec().getAuthor())
            .source(s.getSpec().getSource())
            .published(s.getStatus() != null && s.getStatus().isPublished())
            .likeCount(s.getStatus() != null ? s.getStatus().getLikeCount() : 0)
            .viewCount(s.getStatus() != null ? s.getStatus().getViewCount() : 0)
            .score(score)
            .similarity(similarity)
            .build();
    }

    // ===================== 并查集 =====================

    /**
     * 并查集（Union-Find）数据结构，支持路径压缩。
     *
     * <p>用于将传递相似的句子归为同一组：
     * 若 A~B 且 B~C，则 A、B、C 归为一组。
     *
     * <p>时间复杂度：find/union 均为 O(α(n))，α 为反阿克曼函数（近似常数）。
     */
    private static final class UnionFind {
        private final Map<String, String> parent = new HashMap<>();

        /**
         * 从相似对列表构建并查集。
         *
         * @param pairs 相似对列表
         * @return 初始化后的 UnionFind 实例
         */
        static UnionFind fromPairs(List<SimilarityPair> pairs) {
            UnionFind uf = new UnionFind();
            for (SimilarityPair pair : pairs) {
                uf.add(pair.getSentence1Name());
                uf.add(pair.getSentence2Name());
                uf.union(pair.getSentence1Name(), pair.getSentence2Name());
            }
            return uf;
        }

        /**
         * 添加节点（若已存在则忽略）。
         *
         * @param x 节点名称
         */
        void add(String x) {
            parent.putIfAbsent(x, x);
        }

        /**
         * 查找根节点（带路径压缩）。
         *
         * @param x 起始节点
         * @return 根节点名称
         */
        String find(String x) {
            String p = parent.get(x);
            if (p == null) {
                return x;
            }
            if (!p.equals(x)) {
                p = find(p);
                parent.put(x, p);
            }
            return p;
        }

        /**
         * 合并两个节点所在的集合。
         *
         * @param a 节点1
         * @param b 节点2
         */
        void union(String a, String b) {
            String ra = find(a);
            String rb = find(b);
            if (!ra.equals(rb)) {
                parent.put(ra, rb);
            }
        }

        /**
         * 按根节点分组。
         *
         * @return 根节点 → 成员名称集合
         */
        Map<String, Set<String>> groupByRoot() {
            Map<String, Set<String>> groups = new HashMap<>();
            for (String name : parent.keySet()) {
                groups.computeIfAbsent(find(name), k -> new HashSet<>()).add(name);
            }
            return groups;
        }

        /**
         * 获取所有节点名称。
         *
         * @return 节点名称集合
         */
        Set<String> allNames() {
            return parent.keySet();
        }
    }
}
