package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.extension.AiGenerateLog;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiGenerateLogConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "AiGenerateLogV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("ai-generate-logs", this::listLogs,
                builder -> builder.operationId("listAiGenerateLogs")
                    .summary("查询AI生成日志列表")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("page")
                        .description("页码，从 1 开始").implementation(Integer.class).required(false))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("size")
                        .description("每页数量").implementation(Integer.class).required(false))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status")
                        .description("按状态过滤：RUNNING / SUCCESS / PARTIAL_SUCCESS / FAILED")
                        .implementation(String.class).required(false))
                    .response(responseBuilder().implementation(
                        ListResult.generateGenericClass(AiGenerateLog.class))))
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    private @NonNull Mono<ServerResponse> listLogs(@NonNull ServerRequest request) {
        int page = Integer.parseInt(request.queryParam("page").orElse("1"));
        int size = Integer.parseInt(request.queryParam("size").orElse("20"));
        String status = request.queryParam("status").orElse(null);

        var optionsBuilder = ListOptions.builder();
        if (status != null && !status.isBlank()) {
            optionsBuilder.fieldQuery(Queries.equal("spec.status", status));
        }

        var pageRequest =
            PageRequestImpl.of(page, size, Sort.by("metadata.creationTimestamp").descending());

        return client.listBy(AiGenerateLog.class, optionsBuilder.build(), pageRequest)
            .flatMap(logs -> ServerResponse.ok().bodyValue(logs));
    }
}
