package top.puresky.hitokotohub.service.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SentencePairJsonCodec} 单元测试。
 *
 * <p>覆盖序列化/反序列化往返、损坏 JSON 容错、null/空白输入容错。
 */
@DisplayName("SentencePairJsonCodec JSON 编解码")
class SentencePairJsonCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SentencePairJsonCodec codec = new SentencePairJsonCodec(objectMapper);

    /** 构造一个 SentencePair。 */
    private static SentencePair pair(String n1, String n2, double similarity) {
        return new SentencePair(n1, "内容1", "cat", "作者1", "来源1",
            n2, "内容2", "cat", "作者2", "来源2", similarity);
    }

    @Test
    @DisplayName("往返：serialize → deserialize 后与原列表相等")
    void shouldRoundTripSerializeAndDeserialize() {
        List<SentencePair> original = List.of(
            pair("s1", "s2", 0.95),
            pair("s3", "s4", 0.80)
        );

        String json = codec.serialize(original);
        List<SentencePair> restored = codec.deserialize(json);

        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).sentence1Name()).isEqualTo("s1");
        assertThat(restored.get(0).sentence2Name()).isEqualTo("s2");
        assertThat(restored.get(0).similarity()).isCloseTo(0.95,
            org.assertj.core.api.Assertions.within(1e-4));
        assertThat(restored.get(0).sentence1Content()).isEqualTo("内容1");
        assertThat(restored.get(0).sentence1Author()).isEqualTo("作者1");
        assertThat(restored.get(1).sentence1Name()).isEqualTo("s3");
        assertThat(restored.get(1).similarity()).isCloseTo(0.80,
            org.assertj.core.api.Assertions.within(1e-4));
    }

    @Test
    @DisplayName("serialize：空列表返回 \"[]\"")
    void shouldSerializeEmptyList() {
        String json = codec.serialize(List.of());
        assertThat(json).isEqualTo("[]");
    }

    @Test
    @DisplayName("deserialize：损坏 JSON 返回空列表（不抛异常）")
    void shouldReturnEmptyListForCorruptedJson() {
        List<SentencePair> result = codec.deserialize("{invalid json");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deserialize：null 输入返回空列表")
    void shouldReturnEmptyListForNullInput() {
        assertThat(codec.deserialize(null)).isEmpty();
    }

    @Test
    @DisplayName("deserialize：空白字符串返回空列表")
    void shouldReturnEmptyListForBlankInput() {
        assertThat(codec.deserialize("")).isEmpty();
        assertThat(codec.deserialize("   ")).isEmpty();
    }

    @Test
    @DisplayName("serialize：JSON 包含全部 11 个字段（保证持久化格式不变）")
    void shouldSerializeAllFields() {
        List<SentencePair> pairs = List.of(pair("s1", "s2", 0.5));
        String json = codec.serialize(pairs);

        // 验证 JSON 字符串包含所有字段名（与 SimilarityCheckLog.SimilarityPair DTO 一致）
        assertThat(json).contains("sentence1Name");
        assertThat(json).contains("sentence1Content");
        assertThat(json).contains("sentence1Category");
        assertThat(json).contains("sentence1Author");
        assertThat(json).contains("sentence1Source");
        assertThat(json).contains("sentence2Name");
        assertThat(json).contains("sentence2Content");
        assertThat(json).contains("sentence2Category");
        assertThat(json).contains("sentence2Author");
        assertThat(json).contains("sentence2Source");
        assertThat(json).contains("similarity");
    }

    @Test
    @DisplayName("往返：单个 pair 完整保留所有字段值")
    void shouldPreserveAllFieldsInRoundTrip() {
        SentencePair original = new SentencePair(
            "name1", "内容一", "分类1", "作者一", "来源一",
            "name2", "内容二", "分类2", "作者二", "来源二",
            0.1234
        );

        String json = codec.serialize(List.of(original));
        List<SentencePair> restored = codec.deserialize(json);

        assertThat(restored).hasSize(1);
        SentencePair p = restored.get(0);
        assertThat(p.sentence1Name()).isEqualTo("name1");
        assertThat(p.sentence1Content()).isEqualTo("内容一");
        assertThat(p.sentence1Category()).isEqualTo("分类1");
        assertThat(p.sentence1Author()).isEqualTo("作者一");
        assertThat(p.sentence1Source()).isEqualTo("来源一");
        assertThat(p.sentence2Name()).isEqualTo("name2");
        assertThat(p.sentence2Content()).isEqualTo("内容二");
        assertThat(p.sentence2Category()).isEqualTo("分类2");
        assertThat(p.sentence2Author()).isEqualTo("作者二");
        assertThat(p.sentence2Source()).isEqualTo("来源二");
        assertThat(p.similarity()).isCloseTo(0.1234,
            org.assertj.core.api.Assertions.within(1e-4));
    }
}
