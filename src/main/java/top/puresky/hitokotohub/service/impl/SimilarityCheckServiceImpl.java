package top.puresky.hitokotohub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.extension.SimilarityCheckLog.SimilarityPair;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.service.SimilarityCheckService;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityCheckServiceImpl implements SimilarityCheckService {

    private static final int MAX_STORED_PAIRS = 500;

    private final ReactiveExtensionClient client;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<SimilarityCheckLog> performCheck(
        SimilarityCheckLog.TriggerType triggerType,
        String triggeredBy,
        String algorithm,
        double threshold
    ) {
        // 创建初始日志（RUNNING 状态）
        SimilarityCheckLog logEntry = createInitialLog(triggerType, triggeredBy, algorithm,
            threshold);
        return client.create(logEntry)
            .flatMap(created -> Mono.fromCallable(
                    () -> executeCheck(created, algorithm, threshold))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(updated -> client.update(updated))
                .flatMap(updated -> deleteOldLogs(updated).thenReturn(updated))
                .onErrorResume(e -> {
                    log.error("相似度检查失败", e);
                    created.getSpec().setStatus(SimilarityCheckLog.Status.FAILED);
                    created.getSpec().setErrorMessage(e.getMessage());
                    return client.update(created);
                }));
    }

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
     * 检查完成后删除所有旧记录，只保留最新一条
     */
    private Mono<Void> deleteOldLogs(SimilarityCheckLog currentLog) {
        String currentName = currentLog.getMetadata().getName();
        return client.listAll(SimilarityCheckLog.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .filter(log -> !currentName.equals(log.getMetadata().getName()))
            .flatMap(client::delete)
            .then();
    }

    private SimilarityCheckLog executeCheck(
        SimilarityCheckLog logEntry,
        String algorithm,
        double threshold
    ) {
        long startTime = System.currentTimeMillis();

        // 获取所有句子
        List<Sentence> sentences = client.listAll(Sentence.class,
                ListOptions.builder().build(),
                Sort.by("metadata.creationTimestamp").ascending())
            .collectList()
            .block();

        if (sentences == null || sentences.isEmpty()) {
            logEntry.getSpec().setTotalSentences(0);
            logEntry.getSpec().setTotalPairs(0);
            logEntry.getSpec().setSimilarPairCount(0);
            logEntry.getSpec().setSimilarPairs("[]");
            logEntry.getSpec().setStatus(SimilarityCheckLog.Status.SUCCESS);
            logEntry.getSpec().setDurationMs(System.currentTimeMillis() - startTime);
            return logEntry;
        }

        int n = sentences.size();
        // 预处理：对每个句子生成特征向量
        List<Map<String, Double>> tfVectors = new ArrayList<>(n);
        List<Set<String>> tokenSets = new ArrayList<>(n);

        for (Sentence s : sentences) {
            String content = s.getSpec().getContent() != null
                ? s.getSpec().getContent().trim() : "";
            tfVectors.add(computeTfVector(content));
            tokenSets.add(tokenizeToSet(content));
        }

        // 计算所有 IDF 值
        Map<String, Double> idfMap = computeIdf(tokenSets);

        // 构建 TF-IDF 加权向量（用于余弦相似度）
        List<Map<String, Double>> tfidfVectors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tfidfVectors.add(computeTfidfVector(tfVectors.get(i), idfMap));
        }

        long totalPairs = (long) n * (n - 1) / 2;
        List<SimilarityPair> similarPairs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double similarity;
                if ("JACCARD".equalsIgnoreCase(algorithm)) {
                    similarity = jaccardSimilarity(tokenSets.get(i), tokenSets.get(j));
                } else {
                    // 默认使用余弦相似度
                    similarity = cosineSimilarity(tfidfVectors.get(i), tfidfVectors.get(j));
                }

                if (similarity >= threshold) {
                    similarPairs.add(buildPair(sentences.get(i), sentences.get(j), similarity));
                }
            }
        }

        // 按相似度降序排序
        similarPairs.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        int similarPairCount = similarPairs.size();
        // 限制存储的相似对数量
        List<SimilarityPair> storedPairs = similarPairs.size() > MAX_STORED_PAIRS
            ? similarPairs.subList(0, MAX_STORED_PAIRS) : similarPairs;

        String pairsJson;
        try {
            pairsJson = objectMapper.writeValueAsString(storedPairs);
        } catch (JsonProcessingException e) {
            log.error("序列化相似对失败", e);
            pairsJson = "[]";
        }

        logEntry.getSpec().setTotalSentences(n);
        logEntry.getSpec().setTotalPairs(totalPairs);
        logEntry.getSpec().setSimilarPairCount(similarPairCount);
        logEntry.getSpec().setSimilarPairs(pairsJson);
        logEntry.getSpec().setStatus(SimilarityCheckLog.Status.SUCCESS);
        logEntry.getSpec().setDurationMs(System.currentTimeMillis() - startTime);

        log.info("相似度检查完成: {} 个句子, {} 个配对, {} 个相似对, 耗时 {}ms",
            n, totalPairs, similarPairCount, logEntry.getSpec().getDurationMs());
        return logEntry;
    }

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

    // ===================== 文本相似度算法 =====================

    /**
     * 将文本分词为字符二元组集合（适用于中文）
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
     * 计算词频向量
     */
    private Map<String, Double> computeTfVector(String text) {
        Map<String, Double> tf = new HashMap<>();
        if (text == null || text.length() < 2) {
            return tf;
        }
        int total = 0;
        for (int i = 0; i < text.length() - 1; i++) {
            String bigram = text.substring(i, i + 2);
            tf.merge(bigram, 1.0, Double::sum);
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
     * 计算所有文档的 IDF 值
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
     * 构建 TF-IDF 加权向量
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
     * 计算两个向量的余弦相似度
     */
    private double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        if (v1.isEmpty() || v2.isEmpty()) {
            return 0.0;
        }
        // 选取较小的向量进行遍历
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

    private double vectorNorm(Map<String, Double> v) {
        double sum = 0.0;
        for (double val : v.values()) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算两个集合的 Jaccard 相似度
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
}
