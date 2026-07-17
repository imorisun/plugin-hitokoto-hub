package top.puresky.hitokotohub;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;
import top.puresky.hitokotohub.config.SettingConfig;

@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
public class HitokotoTemplateRouter {

    private final TemplateNameResolver templateNameResolver;
    private final SettingConfig settingConfig;

    @Bean
    RouterFunction<ServerResponse> hitokotoRouterFunction() {
        return route(GET("/hitokoto"), this::renderHitokotoPage);
    }

    Mono<ServerResponse> renderHitokotoPage(ServerRequest request) {
        return settingConfig.getTemplateConfig()
            .defaultIfEmpty(new SettingConfig.TemplateConfig())
            .flatMap(templateConfig -> {
                var model = new HashMap<String, Object>();
                model.put("sentences", List.of());
                model.put("templateTheme", templateConfig.getTemplateTheme());
                model.put("templateShowSakura", templateConfig.getTemplateShowSakura());
                model.put("templateShowHint", templateConfig.getTemplateShowHint());
                return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), "hitokoto")
                    .flatMap(templateName -> ServerResponse.ok().render(templateName, model));
            });
    }
}