package top.puresky.hitokotohub.service.similarity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SentenceScorer} 单元测试。
 *
 * <p>覆盖评分规则各项加权：发布状态、点赞、浏览、内容长度、作者、来源。
 */
@DisplayName("SentenceScorer 句子质量评分")
class SentenceScorerTest {

    private static SentenceProfile profile(String content, boolean published,
                                            long likeCount, long viewCount,
                                            String author, String source) {
        return new SentenceProfile("sentence-1", content, "cat", author, source,
            published, likeCount, viewCount);
    }

    @Test
    @DisplayName("全满分：发布+点赞5+浏览100+长度30+作者+来源 = 90")
    void shouldScoreFullMark() {
        String content = "一".repeat(30);
        SentenceProfile p = profile(content, true, 5, 100, "鲁迅", "朝花夕拾");
        assertThat(SentenceScorer.score(p)).isCloseTo(90.0, within(1e-9));
    }

    @Test
    @DisplayName("全空：未发布+无点赞+无浏览+空内容+匿名+未知 = 0")
    void shouldScoreZeroForEmptyProfile() {
        SentenceProfile p = profile("", false, 0, 0, "匿名", "未知");
        assertThat(SentenceScorer.score(p)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("仅发布：+40")
    void shouldAddPublishedBonus() {
        SentenceProfile p = profile("", true, 0, 0, "匿名", "未知");
        assertThat(SentenceScorer.score(p)).isCloseTo(40.0, within(1e-9));
    }

    @Test
    @DisplayName("点赞数加权：5 赞 → +10")
    void shouldAddLikeBonus() {
        SentenceProfile p = profile("", false, 5, 0, "匿名", "未知");
        assertThat(SentenceScorer.score(p)).isCloseTo(10.0, within(1e-9));
    }

    @Test
    @DisplayName("浏览量加权：100 浏览 → +10")
    void shouldAddViewBonus() {
        SentenceProfile p = profile("", false, 0, 100, "匿名", "未知");
        assertThat(SentenceScorer.score(p)).isCloseTo(10.0, within(1e-9));
    }
    @Test
    @DisplayName("内容长度 15~80 字：+15（边界值）")
    void shouldAddLengthBonusForIdealRange() {
        SentenceProfile p15 = profile("一".repeat(15), false, 0, 0, "匿名", "未知");
        assertThat(SentenceScorer.score(p15)).isCloseTo(15.0, within(1e-9));
        SentenceProfile p80 = profile("一".repeat(80), false, 0, 0, "匿名", "未知");
        assertThat(SentenceScorer.score(p80)).isCloseTo(15.0, within(1e-9));
    }

    @Test
    @DisplayName("内容长度 >80 字：+8（替代 +15）")
    void shouldAddReducedLengthBonusForLongContent() {
        SentenceProfile p = profile("一".repeat(81), false, 0, 0, "匿名", "未知");
        assertThat(SentenceScorer.score(p)).isCloseTo(8.0, within(1e-9));
    }

    @Test
    @DisplayName("内容长度 <15 字：不加分")
    void shouldNotAddLengthBonusForShortContent() {
        SentenceProfile p = profile("短句", false, 0, 0, "匿名", "未知");
        assertThat(SentenceScorer.score(p)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("有作者（非匿名非空）：+10")
    void shouldAddAuthorBonus() {
        SentenceProfile p = profile("", false, 0, 0, "太宰治", "未知");
        assertThat(SentenceScorer.score(p)).isCloseTo(10.0, within(1e-9));
    }

    @Test
    @DisplayName("作者为匿名或空白：不加分")
    void shouldNotAddAuthorBonusForAnonymous() {
        SentenceProfile pAnonymous = profile("", false, 0, 0, "匿名", "未知");
        SentenceProfile pBlank = profile("", false, 0, 0, "  ", "未知");
        assertThat(SentenceScorer.score(pAnonymous)).isEqualTo(0.0);
        assertThat(SentenceScorer.score(pBlank)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("有来源（非未知非空）：+5")
    void shouldAddSourceBonus() {
        SentenceProfile p = profile("", false, 0, 0, "匿名", "人间失格");
        assertThat(SentenceScorer.score(p)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    @DisplayName("来源为未知或空白：不加分")
    void shouldNotAddSourceBonusForUnknown() {
        SentenceProfile pUnknown = profile("", false, 0, 0, "匿名", "未知");
        SentenceProfile pBlank = profile("", false, 0, 0, "匿名", "  ");
        assertThat(SentenceScorer.score(pUnknown)).isEqualTo(0.0);
        assertThat(SentenceScorer.score(pBlank)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("null content 不抛异常且不加分")
    void shouldHandleNullContent() {
        SentenceProfile p = new SentenceProfile("s1", null, "cat", "匿名", "未知", false, 0, 0);
        assertThat(SentenceScorer.score(p)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("评分四舍五入到两位小数")
    void shouldRoundToTwoDecimals() {
        SentenceProfile p = profile("", true, 3, 105, "匿名", "未知");
        assertThat(SentenceScorer.score(p)).isCloseTo(56.5, within(1e-9));
    }
}