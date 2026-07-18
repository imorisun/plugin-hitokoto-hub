package top.puresky.hitokotohub.extension;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.puresky.hitokotohub.support.TestFixtures;

/**
 * 相似度评分冒烟测试。
 *
 * <p>同时作为测试基础设施的 smoke test：验证 {@link TestFixtures} 能正确构造 Sentence，
 * 且 {@link SimilarityGroup#scoreSentence(Sentence)} 的评分逻辑符合预期。
 */
@DisplayName("SimilarityGroup.scoreSentence 评分冒烟测试")
class SimilarityGroupScorerSmokeTest {

    @Test
    @DisplayName("已发布+点赞+浏览+理想长度+作者+来源 应累加全部加分")
    void shouldScoreFullyFeaturedSentence() {
        // 长度 15~80 → +15；已发布 +40；点赞 5*2=10；浏览 100/10=10；有作者 +10；有来源 +5 = 90
        Sentence s = TestFixtures.sentence("s1", "这是一条长度适中的测试句子内容。", "cat",
            "晨阳", "《测试集》", true, 5L, 100L);

        double score = SimilarityGroup.scoreSentence(s);

        assertThat(score).isEqualTo(90.0);
    }

    @Test
    @DisplayName("未发布+匿名+未知+短内容 仅基础分")
    void shouldScoreMinimalSentence() {
        // 未发布 0；匿名作者 0；未知来源 0；短内容（<15）0；点赞/浏览 0
        Sentence s = TestFixtures.sentence("s2", "短", false, 0L, 0L);

        double score = SimilarityGroup.scoreSentence(s);

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("超长内容应按 +8 计分")
    void shouldScoreLongContentWith8Points() {
        // 已发布 +40；长度 >80 +8；有作者 +10；有来源 +5 = 63
        // 构造 >80 字符的内容（84 个汉字）
        String longContent = "这是一段刻意超过八十个字符长度的测试句子内容，用于验证当句子长度超过上限时的评分逻辑"
            + "应当只加八分而不是十五分，所以这里需要继续填充字符直到超过阈值为止再加上结尾句号。";
        assertThat(longContent.length()).isGreaterThan(80);
        Sentence s = TestFixtures.sentence("s3", longContent, "cat", "作者", "来源", true, 0L, 0L);

        double score = SimilarityGroup.scoreSentence(s);

        assertThat(score).isEqualTo(63.0);
    }
}
