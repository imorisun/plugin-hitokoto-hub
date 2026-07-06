package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
import top.puresky.hitokotohub.extension.AiGenerateLog;
import top.puresky.hitokotohub.service.AiGenerateService;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiGenerateLogConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "AiGenerateLogV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;
    private final ObjectProvider<AiGenerateService> aiServiceProvider;
    private final SettingConfig settingConfig;

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
            .POST("ai-generate-logs/-/trigger", this::triggerGenerate,
                builder -> builder.operationId("triggerAiGenerate")
                    .summary("手动触发AI生成句子")
                    .tag(TAG)
                    .response(responseBuilder().implementation(Object.class)))
            .DELETE("ai-generate-logs/{name}", this::deleteLog,
                builder -> builder.operationId("deleteAiGenerateLog")
                    .summary("删除AI生成日志")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("日志名称").implementation(String.class).required(true))
                    .response(responseBuilder().implementation(Object.class)))
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

    private @NonNull Mono<ServerResponse> deleteLog(@NonNull ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(AiGenerateLog.class, name)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("日志不存在")))
            .flatMap(client::delete)
            .then(ServerResponse.ok().bodyValue(Map.of("message", "删除成功")))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(Map.of("message", e.getMessage())));
    }

    private @NonNull Mono<ServerResponse> triggerGenerate(@NonNull ServerRequest request) {
        AiGenerateService aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null) {
            return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyValue(Map.of("message", "AI Foundation 服务不可用，请先安装并启用"));
        }
        return settingConfig.getAiConfig()
            .flatMap(aiConfig -> {
                if (!StringUtils.hasText(aiConfig.getLanguageModelName())) {
                    return ServerResponse.badRequest()
                        .bodyValue(Map.of("message", "请先在设置中选择 AI 模型"));
                }
                if (!StringUtils.hasText(aiConfig.getAiSentenceCategory())) {
                    return ServerResponse.badRequest()
                        .bodyValue(Map.of("message", "请先在设置中选择目标分类"));
                }
                // 异步触发，立即返回，用户可在日志列表查看进度
                aiService.sentencesGenerateAndSave(
                        aiConfig.getLanguageModelName(),
                        aiConfig.getAiSystemPrompt(),
                        aiConfig.getAiTopic(),
                        aiConfig.getAiSentenceCount(),
                        aiConfig.getAiSentenceCategory(),
                        aiConfig.getAiSentenceAutoPublish())
                    .doOnError(e -> log.error("手动触发AI生成失败", e))
                    .subscribe();
                return ServerResponse.ok()
                    .bodyValue(Map.of("message", "AI生成任务已触发，请稍后查看日志"));
            });
    }
}
