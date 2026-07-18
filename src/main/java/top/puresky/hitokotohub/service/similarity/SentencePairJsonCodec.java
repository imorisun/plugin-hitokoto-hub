package top.puresky.hitokotohub.service.similarity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.puresky.hitokotohub.extension.SimilarityCheckLog.SimilarityPair;

/**
 * 句子相似对 JSON 编解码器（边界层）。
 *
 * <p>封装 {@link ObjectMapper} 的序列化/反序列化异常，保证调用方不会因 JSON 处理失败而中断。
 *
 * <p>内部通过 {@link SimilarityMappers} 在算法层 record 与 Extension DTO 之间转换，
 * 保证持久化 JSON 格式与原实现完全一致。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SentencePairJsonCodec {

    private static final TypeReference<List<SimilarityPair>> PAIR_LIST_TYPE =
        new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /**
     * 序列化相似对列表为 JSON 字符串。
     *
     * @param pairs 算法层 record 列表
     * @return JSON 字符串，序列化失败返回 "[]"
     */
    public String serialize(List<SentencePair> pairs) {
        try {
            return objectMapper.writeValueAsString(SimilarityMappers.toList(pairs));
        } catch (JsonProcessingException e) {
            log.error("序列化相似对失败", e);
            return "[]";
        }
    }

    /**
     * 反序列化 JSON 字符串为相似对列表。
     *
     * @param json JSON 字符串
     * @return 算法层 record 列表，解析失败返回空列表
     */
    public List<SentencePair> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<SimilarityPair> extPairs = objectMapper.readValue(json, PAIR_LIST_TYPE);
            return SimilarityMappers.fromList(extPairs);
        } catch (JsonProcessingException e) {
            log.warn("解析相似对 JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
