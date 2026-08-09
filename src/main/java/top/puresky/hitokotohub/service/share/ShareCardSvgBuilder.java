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
 * <p>在服务端渲染一张 600×800 的竖版分享卡片，视觉风格与模板页面设计规范保持一致：
 * 暗色玫瑰色调（#0a0606 底 + #fb7185 点缀）、花瓣标识、衬线字体排版。
 * 卡片被 <img> 预览或由前端绘制到 canvas 导出 PNG 后分享。
 */
public final class ShareCardSvgBuilder {

    /** 卡片尺寸 */
    private static final int CARD_WIDTH = 600;
    private static final int CARD_HEIGHT = 800;
    /** 正文区域可用宽度（左右留白 80） */
    private static final int CONTENT_WIDTH = CARD_WIDTH - 160;
    /** 正文区域顶部 y 坐标 */
    private static final double CONTENT_TOP = 240;
    /** 正文区域最大高度（到分隔线之前） */
    private static final double MAX_CONTENT_HEIGHT = 400;
    /** 正文行高系数（相对字号） */
    private static final double LINE_HEIGHT_RATIO = 1.7;

    /** 主题色（与模板页 :root 变量一致） */
    private static final String ACCENT = "#fb7185";
    private static final String ACCENT_SOFT = "#f472b6";
    private static final String TEXT = "#e7e5e4";
    private static final String TEXT_DIM = "#a1a1aa";
    private static final String MUTED = "#52525b";
    private static final String LINE_DARK = "#2b2b2b";

    /** 正文衬线字体栈 */
    private static final String SERIF_FONT =
        "'Noto Serif SC','Source Han Serif SC','Songti SC','SimSun',serif";
    /** UI 无衬线字体栈 */
    private static final String UI_FONT =
        "'PingFang SC','Hiragino Sans GB','Microsoft YaHei',-apple-system,sans-serif";

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy年M月d日");

    private ShareCardSvgBuilder() {
    }

    /**
     * 生成分享卡片 SVG。
     *
     * @param sentence            句子（内容、作者、来源）
     * @param categoryDisplayName 分类显示名（可为空）
     * @param siteName            站点名称（来自插件分享设置）
     * @param tagline             分享标语（来自插件分享设置）
     * @param wordmark            英文标识（来自插件分享设置）
     * @return SVG 字符串
     */
    public static String build(Sentence sentence, String categoryDisplayName, String siteName,
        String tagline, String wordmark) {
        var spec = sentence.getSpec();
        String content = StringUtils.defaultString(spec.getContent());
        String author = StringUtils.defaultString(spec.getAuthor());
        String source = StringUtils.defaultString(spec.getSource());
        String category = StringUtils.defaultString(categoryDisplayName);
        String brandName = StringUtils.defaultString(siteName);
        String slogan = StringUtils.defaultString(tagline);
        String wordMark = StringUtils.defaultString(wordmark);

        var sb = new StringBuilder(2048);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(CARD_WIDTH)
            .append("\" height=\"").append(CARD_HEIGHT)
            .append("\" viewBox=\"0 0 ").append(CARD_WIDTH).append(' ').append(CARD_HEIGHT)
            .append("\" fill=\"none\">\n");

        appendDefs(sb);
        sb.append("  <g clip-path=\"url(#cardClip)\">\n");
        appendBackground(sb);
        appendBrand(sb, brandName, wordMark);
        appendContent(sb, content);
        appendByline(sb, author, source, content);
        appendBottomBar(sb, category);
        appendFooter(sb, brandName, slogan);
        sb.append("  </g>\n");
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static void appendDefs(StringBuilder sb) {
        sb.append("  <defs>\n")
            .append("    <linearGradient id=\"bgGrad\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">\n")
            .append("      <stop offset=\"0\" stop-color=\"#0a0606\"/>\n")
            .append("      <stop offset=\"0.55\" stop-color=\"#170b0b\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"#241212\"/>\n")
            .append("    </linearGradient>\n")
            .append("    <radialGradient id=\"glowRose\" cx=\"0.5\" cy=\"0.5\" r=\"0.5\">\n")
            .append("      <stop offset=\"0\" stop-color=\"#fb7185\" stop-opacity=\"0.20\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"#fb7185\" stop-opacity=\"0\"/>\n")
            .append("    </radialGradient>\n")
            .append("    <radialGradient id=\"glowPink\" cx=\"0.5\" cy=\"0.5\" r=\"0.5\">\n")
            .append("      <stop offset=\"0\" stop-color=\"#f472b6\" stop-opacity=\"0.12\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"#f472b6\" stop-opacity=\"0\"/>\n")
            .append("    </radialGradient>\n")
            .append("    <linearGradient id=\"lineGrad\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\">\n")
            .append("      <stop offset=\"0\" stop-color=\"#fb7185\" stop-opacity=\"0\"/>\n")
            .append("      <stop offset=\"0.5\" stop-color=\"#fb7185\" stop-opacity=\"0.7\"/>\n")
            .append("      <stop offset=\"1\" stop-color=\"#fb7185\" stop-opacity=\"0\"/>\n")
            .append("    </linearGradient>\n")
            .append("    <clipPath id=\"cardClip\">\n")
            .append("      <rect width=\"").append(CARD_WIDTH).append("\" height=\"")
            .append(CARD_HEIGHT).append("\" rx=\"28\"/>\n")
            .append("    </clipPath>\n")
            .append("  </defs>\n");
    }

    private static void appendBackground(StringBuilder sb) {
        sb.append("    <rect width=\"").append(CARD_WIDTH).append("\" height=\"")
            .append(CARD_HEIGHT).append("\" fill=\"url(#bgGrad)\"/>\n")
            .append("    <ellipse cx=\"530\" cy=\"110\" rx=\"300\" ry=\"300\" fill=\"url(#glowRose)\"/>\n")
            .append("    <ellipse cx=\"60\" cy=\"720\" rx=\"320\" ry=\"300\" fill=\"url(#glowPink)\"/>\n")
            .append("    <rect x=\"0.5\" y=\"0.5\" width=\"").append(CARD_WIDTH - 1)
            .append("\" height=\"").append(CARD_HEIGHT - 1)
            .append("\" rx=\"27.5\" stroke=\"#fb7185\" stroke-opacity=\"0.12\" stroke-width=\"1\"/>\n");
    }

    private static void appendBrand(StringBuilder sb, String siteName, String wordmark) {
        // 花瓣标识（与页面 Favicon 同源）
        sb.append("    <g transform=\"translate(58,22) scale(0.22) rotate(45 50 50)\">\n")
            .append("      <path d=\"M50 15 C70 25 85 45 75 65 C65 85 35 85 25 65 C15 45 30 25 50 15 Z\" fill=\"")
            .append(ACCENT).append("\"/>\n")
            .append("    </g>\n");
        sb.append("    <text x=\"92\" y=\"56\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"24\" font-weight=\"600\" fill=\"").append(TEXT)
            .append("\" letter-spacing=\"6\">").append(escapeXml(siteName)).append("</text>\n");
        sb.append("    <line x1=\"60\" y1=\"74\" x2=\"152\" y2=\"74\" stroke=\"")
            .append(ACCENT).append("\" stroke-opacity=\"0.5\" stroke-width=\"1.5\"/>\n");
        sb.append("    <text x=\"546\" y=\"54\" text-anchor=\"end\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"12\" fill=\"").append(TEXT_DIM)
            .append("\" letter-spacing=\"3\">").append(escapeXml(wordmark)).append("</text>\n");
    }

    private static void appendContent(StringBuilder sb, String content) {
        if (StringUtils.isBlank(content)) {
            content = "一言难尽";
        }
        int fontSize = 34;
        int charsPerLine = Math.max(2, CONTENT_WIDTH / fontSize);
        int lines;
        for (int fs : new int[] {40, 34, 28, 24, 20}) {
            int cpl = Math.max(2, CONTENT_WIDTH / fs);
            lines = (int) Math.ceil((double) content.length() / cpl);
            if (lines * fs * LINE_HEIGHT_RATIO <= MAX_CONTENT_HEIGHT) {
                fontSize = fs;
                charsPerLine = cpl;
                break;
            }
        }
        // 极端长文截断，保证卡片排版完整
        int maxLines = (int) (MAX_CONTENT_HEIGHT / (fontSize * LINE_HEIGHT_RATIO));
        if (content.length() > charsPerLine * maxLines) {
            content = truncateWithEllipsis(content, charsPerLine * maxLines);
        }
        List<String> wrapped = wrapText(content, charsPerLine);

        double lineHeight = fontSize * LINE_HEIGHT_RATIO;
        // 整体垂直居中：起始 y = 顶 + (可用高 - 总行高) / 2
        double startY = CONTENT_TOP + Math.max(0, (MAX_CONTENT_HEIGHT - wrapped.size() * lineHeight) / 2);

        sb.append("    <text x=\"300\" y=\"").append(startY)
            .append("\" text-anchor=\"middle\" font-family=\"").append(SERIF_FONT)
            .append("\" font-size=\"").append(fontSize)
            .append("\" font-weight=\"500\" fill=\"").append(TEXT)
            .append("\" letter-spacing=\"2\">\n");
        for (int i = 0; i < wrapped.size(); i++) {
            sb.append("      <tspan x=\"300\" dy=\"").append(i == 0 ? 0 : lineHeight)
                .append("\" xml:space=\"preserve\">").append(escapeXml(wrapped.get(i)))
                .append("</tspan>\n");
        }
        sb.append("    </text>\n");
    }

    private static void appendByline(StringBuilder sb, String author, String source,
        String content) {
        int fontSize = 20;
        int charsPerLine = Math.max(2, CONTENT_WIDTH / fontSize);
        int lines = (int) Math.ceil((double) content.length() / charsPerLine);
        double lineHeight = fontSize * LINE_HEIGHT_RATIO;
        double contentBottom = CONTENT_TOP + Math.min(
            MAX_CONTENT_HEIGHT, Math.max(lines * lineHeight, lineHeight));

        String authorText = StringUtils.isNotBlank(author) ? author : "佚名";
        String sourceText = StringUtils.isNotBlank(source) ? "《" + source + "》" : "";
        String byline = StringUtils.isBlank(sourceText)
            ? authorText : authorText + " · " + sourceText;

        double bylineY = contentBottom + 46;
        sb.append("    <line x1=\"215\" y1=\"").append(bylineY + 6)
            .append("\" x2=\"385\" y2=\"").append(bylineY + 6)
            .append("\" stroke=\"url(#lineGrad)\" stroke-width=\"1\"/>\n");
        sb.append("    <text x=\"300\" y=\"").append(bylineY)
            .append("\" text-anchor=\"middle\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"16\" fill=\"").append(TEXT_DIM)
            .append("\" letter-spacing=\"1\">").append(escapeXml(byline)).append("</text>\n");
    }

    private static void appendBottomBar(StringBuilder sb, String category) {
        String categoryText = StringUtils.isBlank(category) ? "未分类" : category;
        int chipWidth = 36 + categoryText.length() * 15;
        int chipX = 60;
        int chipY = 660;
        sb.append("    <rect x=\"").append(chipX).append("\" y=\"").append(chipY)
            .append("\" width=\"").append(chipWidth).append("\" height=\"30\" rx=\"15\" stroke=\"")
            .append(ACCENT).append("\" stroke-opacity=\"0.55\" stroke-width=\"1\"/>\n");
        sb.append("    <text x=\"").append(chipX + chipWidth / 2).append("\" y=\"")
            .append(chipY + 20).append("\" text-anchor=\"middle\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"13\" fill=\"").append(ACCENT).append("\" letter-spacing=\"1\">")
            .append(escapeXml(categoryText)).append("</text>\n");
    }

    private static void appendFooter(StringBuilder sb, String siteName, String tagline) {
        sb.append("    <line x1=\"60\" y1=\"728\" x2=\"540\" y2=\"728\" stroke=\"")
            .append(LINE_DARK).append("\" stroke-width=\"1\"/>\n");
        sb.append("    <text x=\"60\" y=\"756\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"13\" fill=\"").append(MUTED)
            .append("\" letter-spacing=\"1\">").append(escapeXml(siteName))
            .append(" · ").append(escapeXml(tagline)).append("</text>\n");
        sb.append("    <text x=\"540\" y=\"756\" text-anchor=\"end\" font-family=\"").append(UI_FONT)
            .append("\" font-size=\"13\" fill=\"").append(MUTED).append("\" letter-spacing=\"1\">")
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
