package top.puresky.hitokotohub.service.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SentencePairJsonCodec} 单元测试（JSON 往返、容错降级）。
 */
class SentencePairJsonCodecTest {

    private final SentencePairJsonCodec codec = new SentencePairJsonCodec(new ObjectMapper());

    @Test
    void serializeThenDeserializeRoundTrip() {
        List<SentencePair> pairs = List.of(
            new SentencePair("a", "内容甲", "cat1", "作者甲", "来源甲",
                "b", "内容乙", "cat2", "作者乙", "来源乙", 0.9123)
        );
        String json = codec.serialize(pairs);
        List<SentencePair> restored = codec.deserialize(json);
        assertEquals(pairs, restored);
    }

    @Test
    void deserializeBlankJsonReturnsEmptyList() {
        assertTrue(codec.deserialize(null).isEmpty());
        assertTrue(codec.deserialize("").isEmpty());
        assertTrue(codec.deserialize("   ").isEmpty());
    }

    @Test
    void deserializeInvalidJsonReturnsEmptyList() {
        assertTrue(codec.deserialize("{not valid json").isEmpty());
        assertTrue(codec.deserialize("[]").isEmpty());
    }

    @Test
    void emptyListSerializesToEmptyArray() {
        assertEquals("[]", codec.serialize(List.of()));
    }
}
