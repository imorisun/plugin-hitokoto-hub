package top.puresky.hitokotohub;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.HashMap;
import java.util.List;
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

    private final TemplateNameResolver templateNameResolver;
    private final SettingConfig settingConfig;
    private final HitokotoFinder hitokotoFinder;

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

        return sharedSentences.flatMap(list -> renderPage(request, list));
    }

    private Mono<ServerResponse> renderPage(ServerRequest request,
        List<HitokotoFinder.SentenceVo> sharedSentences) {
        return settingConfig.getTemplateConfig()
            .defaultIfEmpty(new SettingConfig.TemplateConfig())
            .flatMap(templateConfig -> {
                var model = new HashMap<String, Object>();
                model.put("sentences", List.of());
                // 非空则模板渲染指定句子，为空则渲染随机句子（见 hitokoto.html 双分支）
                model.put("sharedSentence",
                    sharedSentences.isEmpty() ? null : sharedSentences);
                model.put("templateTheme", templateConfig.getTemplateTheme());
                model.put("templateShowSakura", templateConfig.getTemplateShowSakura());
                model.put("templateShowHint", templateConfig.getTemplateShowHint());
                model.put("enableAutoRefresh", templateConfig.getEnableAutoRefresh());
                model.put("autoRefreshInterval", templateConfig.getAutoRefreshInterval());
                return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(),
                        "hitokoto")
                    .flatMap(templateName -> ServerResponse.ok().render(templateName, model));
            });
    }
}
