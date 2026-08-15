package top.puresky.hitokotohub.service.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;
import top.puresky.hitokotohub.extension.Sentence;

/**
 * {@link ShareCardSvgBuilder} 单元测试。
 *
 * <p>重点覆盖 XSS 转义、主题选择与空值兜底。
 */
class ShareCardSvgBuilderTest {

    private Sentence sentence(String content, String author, String source) {
        Sentence sentence = new Sentence();
        sentence.setMetadata(new Metadata());
        sentence.getMetadata().setName("sentence-test");
        Sentence.Spec spec = new Sentence.Spec();
        spec.setContent(content);
        spec.setAuthor(author);
        spec.setSource(source);
        sentence.setSpec(spec);
        return sentence;
    }

    @Test
    void buildProducesValidSvgRoot() {
        String svg = ShareCardSvgBuilder.build(sentence("测试句子", "作者", "来源"), "测试站",
            "dark");
        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.trim().endsWith("</svg>"));
    }

    @Test
    void escapesXmlSpecialCharacters() {
        String svg = ShareCardSvgBuilder.build(
            sentence("<script>alert('xss')</script> & \"quotes\"", "作者<恶>", "来源&"),
            "站点<名>", "dark");
        assertFalse(svg.contains("<script>"), "原始脚本标签不应出现在输出中");
        assertTrue(svg.contains("&lt;script&gt;"));
        assertTrue(svg.contains("&amp;"));
        assertTrue(svg.contains("&quot;"));
    }

    @Test
    void darkThemeUsesDarkBackground() {
        String svg = ShareCardSvgBuilder.build(sentence("内容", "作者", null), "站点", "dark");
        assertTrue(svg.contains("#0a0606"), "暗色主题应使用暗色背景");
    }

    @Test
    void lightThemeUsesLightBackground() {
        String svg = ShareCardSvgBuilder.build(sentence("内容", "作者", null), "站点", "light");
        assertTrue(svg.contains("#fdf8f7"), "亮色主题应使用暖白背景");
    }

    @Test
    void invalidThemeFallsBackToDark() {
        String svg = ShareCardSvgBuilder.build(sentence("内容", "作者", null), "站点",
            "not-a-theme");
        assertTrue(svg.contains("#0a0606"));
    }

    @Test
    void blankContentUsesFallbackText() {
        String svg = ShareCardSvgBuilder.build(sentence("", "作者", null), "站点", "dark");
        assertTrue(svg.contains("一言难尽"));
    }

    @Test
    void blankSiteNameHidesBrandSection() {
        String withSite = ShareCardSvgBuilder.build(sentence("内容", "作者", null), "测试站",
            "dark");
        String withoutSite = ShareCardSvgBuilder.build(sentence("内容", "作者", null), "",
            "dark");
        assertTrue(withSite.contains("测试站"));
        assertFalse(withoutSite.contains("测试站"));
        // 站点名为空时整个品牌区（花瓣标识 + 文案 + 分隔线）都会被省略
        assertTrue(withoutSite.length() < withSite.length(), "品牌区应整体省略");
    }

    @Test
    void blankAuthorUsesAnonymousFallback() {
        String svg = ShareCardSvgBuilder.build(sentence("内容", "", null), "站点", "dark");
        assertTrue(svg.contains("佚名"));
    }

    @Test
    void sourceIsWrappedInBookTitleMarks() {
        String svg = ShareCardSvgBuilder.build(sentence("内容", "作者", "人间词话"), "站点",
            "dark");
        assertTrue(svg.contains("《人间词话》"));
    }
}
