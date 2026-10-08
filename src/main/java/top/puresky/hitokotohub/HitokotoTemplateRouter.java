package top.puresky.hitokotohub;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.finder.HitokotoFinder;

@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class HitokotoTemplateRouter {

    /**
     * 配色值的字符白名单：允许十六进制、rgb(a)/hsl(a) 等常见写法，
     * 拒绝 {@code ; { } < > 引号} 等可能截断 style 属性或注入额外声明的字符。
     */
    private static final Pattern SAFE_COLOR_VALUE =
        Pattern.compile("^[#a-zA-Z0-9(),.%\\s/+\\-]{1,64}$");

    private final TemplateNameResolver templateNameResolver;
    private final SettingConfig settingConfig;
    private final HitokotoFinder hitokotoFinder;

    /**
     * 把管理员配置的颜色追加为 CSS 自定义属性；留空或格式不合法则跳过，
     * 交由样式表内置的配色生效。
     */
    private static void appendGlassColorVar(StringBuilder sb, String name, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        String color = value.trim();
        if (!SAFE_COLOR_VALUE.matcher(color).matches()) {
            return;
        }
        sb.append(name).append(':').append(color).append(';');
    }

    /**
     * 把百分比强度（0~200）换算为倍率并追加为 CSS 自定义属性；留空则跳过（沿用样式表内置强度）。
     * 以倍率而非绝对值注入，是为了让暗色/亮色两套主题各自的基值继续生效，只调整相对强弱。
     */
    private static void appendGlassScaleVar(StringBuilder sb, String name, Integer percent) {
        if (percent == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(200, percent));
        sb.append(name).append(':').append(clamped / 100.0).append(';');
    }

    @Bean
    RouterFunction<ServerResponse> hitokotoRouterFunction() {
        return route(GET("/hitokoto"), this::renderHitokotoPage);
    }

    Mono<ServerResponse> renderHitokotoPage(ServerRequest request) {
        // 分享链接直达：?sentence={name} 展示指定句子，未命中则回退为随机句子
        String sharedName = request.queryParam("sentence").filter(StringUtils::isNotBlank)
            .orElse(null);
        Mono<List<HitokotoFinder.SentenceVo>> sharedSentences = StringUtils.isNotBlank(sharedName)
            ? hitokotoFinder.sentenceByName(sharedName).map(List::of).defaultIfEmpty(List.of())
            : Mono.just(List.of());

        return sharedSentences.flatMap(list -> renderPage(request, list,
            StringUtils.isNotBlank(sharedName)));
    }

    private Mono<ServerResponse> renderPage(ServerRequest request,
        List<HitokotoFinder.SentenceVo> sharedSentences, boolean shareView) {
        Mono<SettingConfig.TemplateConfig> templateConfigMono = settingConfig.getTemplateConfig()
            .defaultIfEmpty(new SettingConfig.TemplateConfig());
        Mono<SettingConfig.SubmissionConfig> submissionConfigMono =
            settingConfig.getSubmissionConfig()
                .defaultIfEmpty(new SettingConfig.SubmissionConfig());
        return Mono.zip(templateConfigMono, submissionConfigMono)
            .flatMap(tuple -> {
                SettingConfig.TemplateConfig templateConfig = tuple.getT1();
                SettingConfig.SubmissionConfig submissionConfig = tuple.getT2();
                var model = new HashMap<String, Object>();
                model.put("sentences", List.of());
                // 非空则模板渲染指定句子，为空则渲染随机句子（见 hitokoto.html 三元表达式）
                model.put("sharedSentence",
                    sharedSentences.isEmpty() ? null : sharedSentences);
                model.put("templateTheme", templateConfig.getTemplateTheme());
                model.put("templateShowSakura", templateConfig.getTemplateShowSakura());
                model.put("templateShowHint", templateConfig.getTemplateShowHint());
                model.put("enableAutoRefresh", templateConfig.getEnableAutoRefresh());
                model.put("autoRefreshInterval", templateConfig.getAutoRefreshInterval());
                // 左上角文字：留空回退为默认文字；链接开关未设置时默认关闭（保持原行为）
                model.put("templateLogoText", StringUtils.defaultIfBlank(
                    templateConfig.getTemplateLogoText(), "LiteWords"));
                model.put("templateLogoLinkEnabled",
                    Boolean.TRUE.equals(templateConfig.getTemplateLogoLinkEnabled()));
                // 液态玻璃模板的品牌首屏：是否展示（默认展示）+ 自定义文案（留空回退默认）
                model.put("templateShowBrand",
                    !Boolean.FALSE.equals(templateConfig.getTemplateShowBrand()));
                model.put("templateBrandTitle", StringUtils.defaultIfBlank(
                    templateConfig.getTemplateBrandTitle(),
                    SettingConfig.TemplateConfig.DEFAULT_BRAND_TITLE));
                model.put("templateBrandSubtitle", StringUtils.defaultIfBlank(
                    templateConfig.getTemplateBrandSubtitle(),
                    SettingConfig.TemplateConfig.DEFAULT_BRAND_SUBTITLE));
                // 液态玻璃模板配色：仅在管理员填写时覆盖样式变量，
                // 留空则由样式表内置的暗色/亮色两套配色各自生效
                var glassVars = new StringBuilder();
                appendGlassColorVar(glassVars, "--accent",
                    templateConfig.getTemplateGlassAccent());
                appendGlassColorVar(glassVars, "--accent-2",
                    templateConfig.getTemplateGlassAccentSecondary());
                appendGlassColorVar(glassVars, "--orb-1",
                    templateConfig.getTemplateGlassOrb1());
                appendGlassColorVar(glassVars, "--orb-2",
                    templateConfig.getTemplateGlassOrb2());
                appendGlassColorVar(glassVars, "--orb-3",
                    templateConfig.getTemplateGlassOrb3());
                // 高光与模糊强度：注入倍率，样式表用 calc() 乘以各主题的内置基值
                appendGlassScaleVar(glassVars, "--hl-scale",
                    templateConfig.getTemplateGlassEdgeHighlight());
                appendGlassScaleVar(glassVars, "--sheen-scale",
                    templateConfig.getTemplateGlassSheen());
                appendGlassScaleVar(glassVars, "--glint-scale",
                    templateConfig.getTemplateGlassGlint());
                appendGlassScaleVar(glassVars, "--blur-scale",
                    templateConfig.getTemplateGlassBlur());
                model.put("templateGlassVars", glassVars.toString());
                // 分享链接直达视图：禁用自动切换句子，避免打断被分享句子的展示
                model.put("shareView", shareView);
                // 投递入口是否渲染由服务端一次性决定，避免先渲染按钮再异步隐藏造成闪烁；
                // 判定口径与 SentenceSubmissionPublicEndpoint 的 config 接口保持一致
                model.put("submissionEnabled",
                    Boolean.TRUE.equals(submissionConfig.getEnableSubmission()));
                // 按插件设置选择模板风格：glass 渲染液态玻璃模板，其余（含未配置）渲染经典单句模板
                String templateStyle = templateConfig.getTemplateStyle();
                String defaultTemplateName = SettingConfig.TemplateConfig.TEMPLATE_STYLE_GLASS
                    .equalsIgnoreCase(templateStyle) ? "hitokoto-glass" : "hitokoto";
                return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(),
                        defaultTemplateName)
                    .flatMap(templateName -> ServerResponse.ok().render(templateName, model));
            });
    }
}
