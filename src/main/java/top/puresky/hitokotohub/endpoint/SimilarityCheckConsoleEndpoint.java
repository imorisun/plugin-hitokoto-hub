package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.service.SimilarityCheckService;
import top.puresky.hitokotohub.service.dto.BatchDeleteResult;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityCheckConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "SimilarityCheckV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;
    private final SimilarityCheckService similarityCheckService;
    private final SettingConfig settingConfig;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("similarity-check-logs", this::listLogs,
                builder -> builder.operationId("listSimilarityCheckLogs")
                    .summary("查询相似度检查日志列表")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("page")
                        .description("页码，从 1 开始").implementation(Integer.class).required(false))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("size")
                        .description("每页数量").implementation(Integer.class).required(false))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status")
                        .description("按状态过滤：RUNNING / SUCCESS / FAILED")
                        .implementation(String.class).required(false))
                    .response(responseBuilder().implementation(
                        ListResult.generateGenericClass(SimilarityCheckLog.class))))
            .GET("similarity-check-logs/{name}", this::getLog,
                builder -> builder.operationId("getSimilarityCheckLog")
                    .summary("获取相似度检查日志详情")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("日志名称").implementation(String.class).required(true))
                    .response(responseBuilder().implementation(SimilarityCheckLog.class)))
            .POST("similarity-check-logs/-/trigger", this::triggerCheck,
                builder -> builder.operationId("triggerSimilarityCheck")
                    .summary("手动触发相似度检查")
                    .tag(TAG)
                    .response(responseBuilder().implementation(Object.class)))
            .GET("similarity-check-config", this::getConfig,
                builder -> builder.operationId("getSimilarityCheckConfig")
                    .summary("获取相似度检查配置")
                    .tag(TAG)
                    .response(responseBuilder().implementation(Map.class)))
            .GET("similarity-check-groups", this::listGroups,
                builder -> builder.operationId("listSimilarityGroups")
                    .summary("查询相似句子分组结果（分页）")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("page")
                        .description("页码，从 1 开始").implementation(Integer.class).required(false))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("size")
                        .description("每页数量").implementation(Integer.class).required(false))
                    .response(responseBuilder().implementation(Map.class)))
            .POST("similarity-check-groups/-/delete-nonoptimal", this::deleteNonOptimal,
                builder -> builder.operationId("deleteNonOptimalSentences")
                    .summary("批量删除每个相似组中的非最优句子")
                    .tag(TAG)
                    .response(responseBuilder().implementation(BatchDeleteResult.class)))
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

        return client.listBy(SimilarityCheckLog.class, optionsBuilder.build(), pageRequest)
            .flatMap(logs -> ServerResponse.ok().bodyValue(logs));
    }

    private @NonNull Mono<ServerResponse> getLog(@NonNull ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(SimilarityCheckLog.class, name)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("日志不存在")))
            .flatMap(log -> ServerResponse.ok().bodyValue(log))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(Map.of("message", e.getMessage())));
    }

    private @NonNull Mono<ServerResponse> triggerCheck(@NonNull ServerRequest request) {
        return request.principal().map(Principal::getName).defaultIfEmpty("system")
            .flatMap(username -> settingConfig.getSimilarityConfig()
                .flatMap(config -> {
                    String algorithm = config.getSimilarityAlgorithm() != null
                        ? config.getSimilarityAlgorithm() : "COSINE";
                    double threshold = config.getSimilarityThreshold() != null
                        ? config.getSimilarityThreshold() : 0.8;

                    // 解析请求参数覆盖
                    String paramAlgorithm = request.queryParam("algorithm").orElse(null);
                    String paramThreshold = request.queryParam("threshold").orElse(null);
                    if (paramAlgorithm != null && !paramAlgorithm.isBlank()) {
                        algorithm = paramAlgorithm;
                    }
                    if (paramThreshold != null) {
                        threshold = Double.parseDouble(paramThreshold);
                    }

                    final String finalAlgorithm = algorithm;
                    final double finalThreshold = threshold;

                    // 异步触发检查
                    similarityCheckService.performCheck(
                            SimilarityCheckLog.TriggerType.MANUAL,
                            username,
                            finalAlgorithm,
                            finalThreshold)
                        .doOnError(e -> log.error("手动触发相似度检查失败", e))
                        .subscribe();
                    return ServerResponse.ok()
                        .bodyValue(Map.of("message", "相似度检查任务已触发，请稍后查看结果"));
                }));
    }

    private @NonNull Mono<ServerResponse> getConfig(@NonNull ServerRequest request) {
        return settingConfig.getSimilarityConfig()
            .map(config -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("enableScheduledCheck",
                    config.getEnableScheduledCheck() != null
                        ? config.getEnableScheduledCheck() : false);
                result.put("similarityCron",
                    config.getSimilarityCron() != null
                        ? config.getSimilarityCron() : "0 0 4 * * *");
                result.put("similarityAlgorithm",
                    config.getSimilarityAlgorithm() != null
                        ? config.getSimilarityAlgorithm() : "COSINE");
                result.put("similarityThreshold",
                    config.getSimilarityThreshold() != null
                        ? config.getSimilarityThreshold() : 0.8);
                return result;
            })
            .flatMap(config -> ServerResponse.ok().bodyValue(config));
    }

    @NonNull Mono<ServerResponse> listGroups(@NonNull ServerRequest request) {
        int page = Integer.parseInt(request.queryParam("page").orElse("1"));
        int size = Integer.parseInt(request.queryParam("size").orElse("10"));
        return similarityCheckService.getGroups(page, size)
            .flatMap(result -> ServerResponse.ok().bodyValue(result))
            .onErrorResume(e -> {
                log.error("获取相似度分组失败", e);
                return ServerResponse.ok()
                    .bodyValue(Map.of("page", page, "size", size, "total", 0, "groups", List.of()));
            });
    }

    @NonNull Mono<ServerResponse> deleteNonOptimal(@NonNull ServerRequest request) {
        return similarityCheckService.deleteNonOptimalSentences()
            .flatMap(result -> ServerResponse.ok().bodyValue(result))
            .onErrorResume(e -> {
                log.error("批量删除非最优句子失败", e);
                return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyValue(Map.of(
                        "message", "批量删除失败：" + e.getMessage(),
                        "total", 0, "deleted", 0, "failed", 0));
            });
    }
}
