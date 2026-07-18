package top.puresky.hitokotohub.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.CategoryViewRecord.EventType;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.support.TestFixtures;

/**
 * {@link CategoryViewRecordFactory} 单元测试。
 */
@DisplayName("CategoryViewRecordFactory")
class CategoryViewRecordFactoryTest {

    @Test
    @DisplayName("forView 创建 VIEW 记录，含 categoryName 和 sentenceName")
    void shouldCreateViewRecord() {
        CategoryViewRecord record = CategoryViewRecordFactory.forView("cat-1", "sentence-1");

        assertThat(record.getSpec().getCategoryName()).isEqualTo("cat-1");
        assertThat(record.getSpec().getEventType()).isEqualTo(EventType.VIEW);
        assertThat(record.getSpec().getSentenceName()).isEqualTo("sentence-1");
        assertThat(record.getSpec().getIp()).isNull();
        assertThat(record.getMetadata().getGenerateName()).isEqualTo("cvr-");
    }

    @Test
    @DisplayName("forView 的 sentenceName 可为 null")
    void shouldAllowNullSentenceNameForView() {
        CategoryViewRecord record = CategoryViewRecordFactory.forView("cat-1", null);

        assertThat(record.getSpec().getSentenceName()).isNull();
    }

    @Test
    @DisplayName("forLike 创建 LIKE 记录，含 ip")
    void shouldCreateLikeRecord() {
        CategoryViewRecord record =
            CategoryViewRecordFactory.forLike("cat-1", "sentence-1", "192.168.1.1");

        assertThat(record.getSpec().getCategoryName()).isEqualTo("cat-1");
        assertThat(record.getSpec().getEventType()).isEqualTo(EventType.LIKE);
        assertThat(record.getSpec().getSentenceName()).isEqualTo("sentence-1");
        assertThat(record.getSpec().getIp()).isEqualTo("192.168.1.1");
        assertThat(record.getMetadata().getGenerateName()).isEqualTo("cvr-");
    }

    @Test
    @DisplayName("create(Sentence, VIEW, null) 从 Sentence 提取 categoryName 和 sentenceName")
    void shouldCreateViewRecordFromSentence() {
        Sentence sentence = TestFixtures.sentence("s-1", "内容", true);
        sentence.getSpec().setCategoryName("my-cat");

        CategoryViewRecord record =
            CategoryViewRecordFactory.create(sentence, EventType.VIEW, null);

        assertThat(record.getSpec().getCategoryName()).isEqualTo("my-cat");
        assertThat(record.getSpec().getEventType()).isEqualTo(EventType.VIEW);
        assertThat(record.getSpec().getSentenceName()).isEqualTo("s-1");
        assertThat(record.getSpec().getIp()).isNull();
    }

    @Test
    @DisplayName("create(Sentence, LIKE, ip) 从 Sentence 提取并设置 ip")
    void shouldCreateLikeRecordFromSentence() {
        Sentence sentence = TestFixtures.sentence("s-2", "内容", true);
        sentence.getSpec().setCategoryName("like-cat");

        CategoryViewRecord record =
            CategoryViewRecordFactory.create(sentence, EventType.LIKE, "10.0.0.5");

        assertThat(record.getSpec().getCategoryName()).isEqualTo("like-cat");
        assertThat(record.getSpec().getEventType()).isEqualTo(EventType.LIKE);
        assertThat(record.getSpec().getSentenceName()).isEqualTo("s-2");
        assertThat(record.getSpec().getIp()).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("create(Sentence, LIKE, null) ip 为 null 时回退为 unknown")
    void shouldFallbackToUnknownIpWhenNull() {
        Sentence sentence = TestFixtures.sentence("s-3", "内容", true);

        CategoryViewRecord record =
            CategoryViewRecordFactory.create(sentence, EventType.LIKE, null);

        assertThat(record.getSpec().getIp()).isEqualTo("unknown");
    }
}
