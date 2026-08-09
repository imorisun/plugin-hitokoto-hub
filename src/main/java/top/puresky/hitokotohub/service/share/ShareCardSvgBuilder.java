package top.puresky.hitokotohub.service.share;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import top.puresky.hitokotohub.extension.Sentence;

/**
 * 分享卡片 SVG 生成器。
 *
 * <p>在服务端渲染一张 600×800 的竖版分享卡片，设计遵循项目 UI 规范：
 * 玫瑰色调、衬线字体、大量留白，整体简约优雅。支持「dark / light」两套配色，
 * 分别对应卡片夜间与日间样式。卡片被 <img> 预览或由前端绘制到 canvas 导出 PNG 后分享。
 */
public final class ShareCardSvgBuilder {

    /** 卡片尺寸 */
    private static final int CARD_WIDTH = 600;
    private static final int CARD_HEIGHT = 800;
    /** 正文区域可用宽度（左右留白 80） */
    private static final int CONTENT_WIDTH = CARD_WIDTH - 160;
    /** 正文区域顶部 y 坐标 */
    private static final double CONTENT_TOP = 190;
    /** 正文区域最大高度 */
    private static final double MAX_CONTENT_HEIGHT = 340;
    /** 正文行高系数（相对字号），行距舒展 */
    private static final double LINE_HEIGHT_RATIO = 1.6;

    /** 正文衬线字体栈 */
    private static final String SERIF_FONT =
        "'Noto Serif SC','Source Han Serif SC','Songti SC','SimSun',serif";
    /** UI 无衬线字体栈 */
    private static final String UI_FONT =
        "'PingFang SC','Hiragino Sans GB','Microsoft YaHei',-apple-system,sans-serif";

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy.M.d");

    /** 卡片配色。所有颜色均为十六进制字符串，透明度用独立字段（字符串形式便于拼接） */
    private static final class CardPalette {
        final String bgTop;
        final String bgMid;
        final String bgBottom;
        final String glowRose;
        final String glowRoseOpacity;
        final String glowPink;
        final String glowPinkOpacity;
        final String borderOpacity;
        final String accent;
        final String text;
        final String textDim;
        final String muted;
        final String line;

        CardPalette(String bgTop, String bgMid, String bgBottom, String glowRose,
            String glowRoseOpacity, String glowPink, String glowPinkOpacity, String borderOpacity,
            String accent, String text, String textDim, String muted, String line) {
            this.bgTop = bgTop;
            this.bgMid = bgMid;
            this.bgBottom = bgBottom;
            this.glowRose = glowRose;
            this.glowRoseOpacity = glowRoseOpacity;
            this.glowPink = glowPink;
            this.glowPinkOpacity = glowPinkOpacity;
            this.borderOpacity = borderOpacity;
            this.accent = accent;
            this.text = text;
            this.textDim = textDim;
            this.muted = muted;
            this.line = line;
        }

        /** 夜间（默认）配色：暗色玫瑰，与模板页暗色主题一致 */
        static CardPalette dark() {
            return new CardPalette(
                "#0a0606", "#120a0a", "#1a0e0e",
                "#fb7185", "0.14", "#f472b6", "0.08", "0.12",
                "#fb7185", "#e7e5e4", "#a1a1aa", "#52525b", "#2b2b2b");
        }

        /** 日间配色：暖白玫瑰调，文字深色，保证浅背景下的可读性 */
        static CardPalette light() {
            return new CardPalette(
                "#fdf8f7", "#fbedec", "#f8e4e3",
                "#fda4af", "0.25", "#f9a8d4", "0.14", "0.16",
                "#e11d48", "#292524", "#78716c", "#a8a29e", "#efe1e0");
        }

        static CardPalette forTheme(String theme) {
            return "light".equalsIgnoreCase(theme) ? light() : dark();
        }
    }

    private ShareCardSvgBuilder() {
    }

    /**
     * 生成分享卡片 SVG。
     *
     * @param sentence 句子（内容、作者、来源）
     * @param siteName 站点名称（来自插件分享设置，缺省为空则隐藏品牌区）
     * @param theme    卡片主题：dark（夜间）或 light（日间），非法值回退 dark
     * @return SVG 字符串
     */
    public static String build(Sentence sentence, String siteName, String theme) {
        var spec = sentence.getSpec();
        String content = StringUtils.defaultString(spec.getContent());
        String author = StringUtils.defaultString(spec.getAuthor());
        String source = StringUtils.defaultString(spec.getSource());
        String brandName = StringUtils.defaultString(siteName);
        CardPalette palette = CardPalette.forTheme(theme);

        var sb = new StringBuilder(2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(CARD_WIDTH)
            .append("\" height=\"").append(CARD_HEIGHT)
            .append("\" viewBox=\"0 0 ").append(CARD_WIDTH).append(' ').append(CARD_HEIGHT)
            .append("\" fill=\"none\">\n");

        appendDefs(sb, palette);
        sb.append("  <g clip-path=\"url(#cardClip)\">\n");
        appendBackground(sb, palette);
        appendBrand(sb, brandName, palette);
        ContentLayout layout = computeContentLayout(content);
        appendContent(sb, layout, palette);
        appendByline(sb, author, source, layout, palette);
        appendFooter(sb, palette);
        sb.append("  </g>\n");
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static void appendDefs(StringBuilder sb, CardPalette p) {
        sb.append("  <defs>\n")
            .append("    <linearGradient id=\"bgGrad\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n")
            .append("      <stop offset=\"0\" stop-color=\"").append(p.bgTop).append("\"/>\n")
            .append("      <stop offset=\"0.55\" stop-color=\"").append(p.bgMid).append("\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"").append(p.bgBottom).append("\"/>\n")
            .append("    </linearGradient>\n")
            .append("    <radialGradient id=\"glowRose\" cx=\"0.5\" cy=\"0.5\" r=\"0.5\">\n")
            .append("      <stop offset=\"0\" stop-color=\"").append(p.glowRose)
            .append("\" stop-opacity=\"").append(p.glowRoseOpacity).append("\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"").append(p.glowRose)
            .append("\" stop-opacity=\"0\"/>\n")
            .append("    </radialGradient>\n")
            .append("    <radialGradient id=\"glowPink\" cx=\"0.5\" cy=\"0.5\" r=\"0.5\">\n")
            .append("      <stop offset=\"0\" stop-color=\"").append(p.glowPink)
            .append("\" stop-opacity=\"").append(p.glowPinkOpacity).append("\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"").append(p.glowPink)
            .append("\" stop-opacity=\"0\"/>\n")
            .append("    </radialGradient>\n")
            .append("    <linearGradient id=\"lineGrad\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\">\n")
            .append("      <stop offset=\"0\" stop-color=\"").append(p.accent)
            .append("\" stop-opacity=\"0\"/>\n")
            .append("      <stop offset=\"0.5\" stop-color=\"").append(p.accent)
            .append("\" stop-opacity=\"0.6\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"").append(p.accent)
            .append("\" stop-opacity=\"0\"/>\n")
            .append("    </linearGradient>\n")
            .append("    <clipPath id=\"cardClip\">\n")
            .append("      <rect width=\"").append(CARD_WIDTH).append("\" height=\"")
            .append(CARD_HEIGHT).append("\" rx=\"28\"/>\n")
            .append("    </clipPath>\n")
            .append("  </defs>\n");
    }

    private static void appendBackground(StringBuilder sb, CardPalette p) {
        sb.append("    <rect width=\"").append(CARD_WIDTH).append("\" height=\"")
            .append(CARD_HEIGHT).append("\" fill=\"url(#bgGrad)\"/>\n")
            .append("    <ellipse cx=\"470\" cy=\"90\" rx=\"280\" ry=\"280\" fill=\"url(#glowRose)\"/>\n")
            .append("    <ellipse cx=\"110\" cy=\"730\" rx=\"300\" ry=\"280\" fill=\"url(#glowPink)\"/>\n")
            .append("    <rect x=\"0.5\" y=\"0.5\" width=\"").append(CARD_WIDTH - 1)
            .append("\" height=\"").append(CARD_HEIGHT - 1)
            .append("\" rx=\"27.5\" stroke=\"").append(p.accent)
            .append("\" stroke-opacity=\"").append(p.borderOpacity)
            .append("\" stroke-width=\"1\"/>\n");
    }

    /** 顶部居中的极简品牌区：花瓣标识 + 站点名 + 细线 */
    private static void appendBrand(StringBuilder sb, String siteName, CardPalette p) {
        if (StringUtils.isBlank(siteName)) {
            return;
        }
        // 花瓣标识（与页面 Favicon 同源），中心位于 (300, 48)
        sb.append("    <g transform=\"translate(292,40) scale(0.16) rotate(45 50 50)\">\n")
            .append("      <path d=\"M50 15 C70 25 85 45 75 65 C65 85 35 85 25 65 C15 45 30 25 50 15 Z\" fill=\"")
            .append(p.accent).append("\"/>\n")
            .append("    </g>\n");
        sb.append("    <text x=\"300\" y=\"94\" text-anchor=\"middle\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"14\" font-weight=\"500\" fill=\"").append(p.textDim)
            .append("\" letter-spacing=\"8\">").append(escapeXml(siteName)).append("</text>\n");
        sb.append("    <line x1=\"276\" y1=\"114\" x2=\"324\" y2=\"114\" stroke=\"")
            .append(p.accent).append("\" stroke-opacity=\"0.35\" stroke-width=\"1\"/>\n");
    }

    /** 正文排版结果：正文与作者行共用同一套参数，保证垂直位置计算一致、互不重叠 */
    private static class ContentLayout {
        final int fontSize;
        final List<String> lines;
        final double lineHeight;
        final double startY;
        final double bottomY;

        ContentLayout(int fontSize, List<String> lines, double lineHeight, double startY,
            double bottomY) {
            this.fontSize = fontSize;
            this.lines = lines;
            this.lineHeight = lineHeight;
            this.startY = startY;
            this.bottomY = bottomY;
        }
    }

    private static ContentLayout computeContentLayout(String content) {
        if (StringUtils.isBlank(content)) {
            content = "一言难尽";
        }
        int fontSize = 34;
        int charsPerLine = Math.max(2, CONTENT_WIDTH / fontSize);
        for (int fs : new int[] {40, 34, 28, 24, 20}) {
            int cpl = Math.max(2, CONTENT_WIDTH / fs);
            int totalCp = content.codePointCount(0, content.length());
            int lines = (int) Math.ceil((double) totalCp / cpl);
            if (lines * fs * LINE_HEIGHT_RATIO <= MAX_CONTENT_HEIGHT) {
                fontSize = fs;
                charsPerLine = cpl;
                break;
            }
        }
        // 极端长文截断，保证卡片排版完整
        int maxLines = (int) (MAX_CONTENT_HEIGHT / (fontSize * LINE_HEIGHT_RATIO));
        if (content.codePointCount(0, content.length()) > charsPerLine * maxLines) {
            content = truncateWithEllipsis(content, charsPerLine * maxLines);
        }
        List<String> wrapped = wrapText(content, charsPerLine);

        double lineHeight = fontSize * LINE_HEIGHT_RATIO;
        // 整体垂直居中：起始 y = 顶 + (可用高 - 总行高) / 2
        double startY = CONTENT_TOP + Math.max(0,
            (MAX_CONTENT_HEIGHT - wrapped.size() * lineHeight) / 2);
        double bottomY = startY + wrapped.size() * lineHeight;
        return new ContentLayout(fontSize, wrapped, lineHeight, startY, bottomY);
    }

    private static void appendContent(StringBuilder sb, ContentLayout layout, CardPalette p) {
        sb.append("    <text x=\"300\" y=\"").append(layout.startY)
            .append("\" text-anchor=\"middle\" font-family=\"").append(SERIF_FONT)
            .append("\" font-size=\"").append(layout.fontSize)
            .append("\" font-weight=\"500\" fill=\"").append(p.text)
            .append("\" letter-spacing=\"2\">\n");
        for (int i = 0; i < layout.lines.size(); i++) {
            sb.append("      <tspan x=\"300\" dy=\"").append(i == 0 ? 0 : layout.lineHeight)
                .append("\" xml:space=\"preserve\">").append(escapeXml(layout.lines.get(i)))
                .append("</tspan>\n");
        }
        sb.append("    </text>\n");
    }

    /** 作者行：以「——」引出，简约克制，固定在正文底部下方 */
    private static void appendByline(StringBuilder sb, String author, String source,
        ContentLayout layout, CardPalette p) {
        String authorText = StringUtils.isNotBlank(author) ? author : "佚名";
        String byline = StringUtils.isBlank(source)
            ? "—— " + authorText : "—— " + authorText + " · 《" + source + "》";

        double bylineY = layout.bottomY + 44;
        sb.append("    <text x=\"300\" y=\"").append(bylineY)
            .append("\" text-anchor=\"middle\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"15\" fill=\"").append(p.textDim)
            .append("\" letter-spacing=\"2\">").append(escapeXml(byline)).append("</text>\n");
    }

    /** 页脚：底部居中细线与日期 */
    private static void appendFooter(StringBuilder sb, CardPalette p) {
        sb.append("    <line x1=\"270\" y1=\"716\" x2=\"330\" y2=\"716\" stroke=\"")
            .append(p.line).append("\" stroke-width=\"1\"/>\n");
        sb.append("    <text x=\"300\" y=\"746\" text-anchor=\"middle\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"10\" fill=\"").append(p.muted)
            .append("\" letter-spacing=\"4\">")
            .append(LocalDate.now().format(DATE_FORMATTER)).append("</text>\n");
    }

    /** 按 Unicode 码点硬换行（中文每字 ≈ 1em，拉丁字符略窄，此处取近似值即可） */
    private static List<String> wrapText(String text, int charsPerLine) {
        List<String> lines = new ArrayList<>();
        int totalCodePoints = text.codePointCount(0, text.length());
        int start = 0;
        for (int handled = 0; handled < totalCodePoints; handled += charsPerLine) {
            int take = Math.min(charsPerLine, totalCodePoints - handled);
            int end = text.offsetByCodePoints(start, take);
            lines.add(text.substring(start, end));
            start = end;
        }
        return lines;
    }

    private static String truncateWithEllipsis(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int keep = Math.max(1, maxChars - 1);
        // 按 Unicode 码点定位边界，避免切碎代理对字符（如 emoji）
        int codePointCount = text.codePointCount(0, Math.min(keep, text.length()));
        int end = text.offsetByCodePoints(0, codePointCount);
        return text.substring(0, end) + "…";
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
