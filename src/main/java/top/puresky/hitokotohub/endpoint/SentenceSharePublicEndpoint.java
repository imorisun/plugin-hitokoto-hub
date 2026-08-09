package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import top.puresky.hitokotohub.service.SentenceShareService;
import top.puresky.hitokotohub.service.dto.SharePayload;

/**
 * 公开分享接口（游客）。
 *
 * <p>游客在模板页面分享当前展示的句子：获取分享数据、分享卡片。
 * 仅允许分享已发布的句子，权限由 role-template-public 授予匿名用户。
 */
@Component
@RequiredArgsConstructor
public class SentenceSharePublicEndpoint implements CustomEndpoint {

    private static final String TAG = "SentenceShareV1alpha1Public";
    private static final String GROUP_VERSION =
        "public.api.hitokotohub.puresky.top/v1alpha1";

    private final SentenceShareService shareService;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route().GET("sentence/{name}/share", this::getShare,
                builder -> builder.operationId("getSentenceShare").summary("获取句子分享数据")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("句子 metadata.name").implementation(String.class)
                        .required(true))
                    .response(responseBuilder().implementation(SharePayload.class)))
            .GET("sentence/{name}/share/card", this::getShareCard,
                builder -> builder.operationId("getSentenceShareCard").summary("获取句子分享卡片")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("句子 metadata.name").implementation(String.class)
                        .required(true)))
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    @NonNull Mono<ServerResponse> getShare(@NonNull ServerRequest request) {
        String name = request.pathVariable("name");
        return shareService.buildSharePayload(name, true)
            .flatMap(payload -> ServerResponse.ok().bodyValue(payload))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    @NonNull Mono<ServerResponse> getShareCard(@NonNull ServerRequest request) {
        String name = request.pathVariable("name");
        return shareService.buildShareCardSvg(name, true)
            .flatMap(svg -> ServerResponse.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml;charset=UTF-8"))
                .bodyValue(svg))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
}
