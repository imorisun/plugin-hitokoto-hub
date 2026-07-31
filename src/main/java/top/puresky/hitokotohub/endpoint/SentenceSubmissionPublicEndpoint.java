package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.SentenceSubmission;
import top.puresky.hitokotohub.utils.BatchCooldownState;
import top.puresky.hitokotohub.utils.HttpUtils;
import top.puresky.hitokotohub.utils.IpCooldownCache;
import top.puresky.hitokotohub.utils.TimeFormatUtils;

@Component
@RequiredArgsConstructor
public class SentenceSubmissionPublicEndpoint implements CustomEndpoint {

    private static final String TAG = "SentenceSubmissionPublicV1alpha1";
    private static final String GROUP_VERSION = "public.api.hitokotohub.puresky.top/v1alpha1";
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_AUTHOR_LENGTH = 50;
    private static final int MAX_SOURCE_LENGTH = 100;
    private static final int MAX_NAME_LENGTH = 50;

    private final SettingConfig settingConfig;
    private final ReactiveExtensionClient client;
    private final IpCooldownCache<BatchCooldownState> submitCache = new IpCooldownCache<>();

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("sentence-submission/config", this::getSubmissionConfig,
                builder -> builder.operationId("getSubmissionConfig")
                    .summary("获取访客提交配置")
                    .tag(TAG)
                    .response(responseBuilder().implementation(Object.class)))
            .POST("sentence-submission/-/submit", this::submitSentence,
                builder -> builder.operationId("submitSentence")
                    .summary("访客提交句子")
                    .tag(TAG)
                    .requestBody(requestBodyBuilder().required(true).content(
                        contentBuilder().schema(schemaBuilder().implementation(
                            SubmitRequest.class))))
                    .response(responseBuilder().implementation(SubmitResponse.class)))
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    private @NonNull Mono<ServerResponse> getSubmissionConfig(@NonNull ServerRequest request) {
        return settingConfig.getSubmissionConfig()
            .flatMap(config -> {
                boolean enabled = Boolean.TRUE.equals(config.getEnableSubmission());
                String defaultCategory = config.getSubmissionDefaultCategory();
                return ServerResponse.ok().bodyValue(Map.of(
                    "enableSubmission", enabled,
                    "defaultCategory", defaultCategory != null ? defaultCategory : ""
                ));
            });
    }

    private @NonNull Mono<ServerResponse> submitSentence(@NonNull ServerRequest request) {
        String ip = HttpUtils.getClientIp(request.exchange().getRequest());
        return settingConfig.getSubmissionConfig()
            .flatMap(config -> {
                if (!Boolean.TRUE.equals(config.getEnableSubmission())) {
                    return ServerResponse.status(HttpStatus.FORBIDDEN)
                        .bodyValue(buildResponse(false, "submitted_disabled",
                            "访客提交功能未开启"));
                }
                int cooldownMinutes = config.getSubmissionCooldown() == null
                    ? 0 : config.getSubmissionCooldown();
                int batchLimit = config.getSubmissionBatchLimit() == null
                    ? 1 : Math.max(1, config.getSubmissionBatchLimit());
                long now = System.currentTimeMillis();
                long cooldownMs = Duration.ofMinutes(cooldownMinutes).toMillis();

                if (cooldownMinutes > 0) {
                    BatchCooldownState state = submitCache.get(ip);
                    if (state != null) {
                        long elapsed = now - state.getFirstSubmitTime();
                        // 冷却周期已过，重置状态
                        if (elapsed >= cooldownMs) {
                            submitCache.remove(ip);
                            state = null;
                        }
                    }
                    if (state != null && state.reachedBatchLimit(batchLimit)) {
                        // 已达连续提交上限，进入冷却
                        long remainingMs = cooldownMs - (now - state.getFirstSubmitTime());
                        long remainingSeconds = remainingMs / 1000;
                        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                            .bodyValue(buildResponse(false, "rate_limited",
                                "已达到连续提交上限，请在 " + TimeFormatUtils.formatRemainingTime(remainingSeconds)
                                    + " 后再试"));
                    }
                }
                return request.bodyToMono(SubmitRequest.class)
                    .flatMap(submitRequest -> validateAndCreate(submitRequest, config, ip))
                    .doOnSuccess(v -> {
                        if (cooldownMinutes > 0) {
                            BatchCooldownState state = submitCache.get(ip);
                            long currentTime = System.currentTimeMillis();
                            if (state == null) {
                                submitCache.put(ip, new BatchCooldownState(currentTime));
                            } else {
                                long elapsed = currentTime - state.getFirstSubmitTime();
                                if (elapsed >= cooldownMs) {
                                    // 冷却周期已过，重置计数
                                    submitCache.put(ip, new BatchCooldownState(currentTime));
                                } else {
                                    state.increment();
                                }
                            }
                        }
                    });
            })
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.badRequest()
                    .bodyValue(buildResponse(false, "invalid_param", e.getMessage())));
    }

    private @NonNull Mono<ServerResponse> validateAndCreate(SubmitRequest submitRequest,
        SettingConfig.SubmissionConfig config, String ip) {
        String content = submitRequest.getContent() == null
            ? "" : submitRequest.getContent().trim();
        if (!StringUtils.hasText(content)) {
            return Mono.error(new IllegalArgumentException("句子内容不能为空"));
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Mono.error(new IllegalArgumentException(
                "句子内容不能超过 " + MAX_CONTENT_LENGTH + " 字"));
        }

        String categoryName = submitRequest.getCategoryName();
        if (!StringUtils.hasText(categoryName)) {
            categoryName = config.getSubmissionDefaultCategory();
        }
        if (!StringUtils.hasText(categoryName)) {
            return Mono.error(new IllegalArgumentException("请选择分类"));
        }

        String author = submitRequest.getAuthor();
        if (author != null && author.length() > MAX_AUTHOR_LENGTH) {
            return Mono.error(new IllegalArgumentException(
                "作者不能超过 " + MAX_AUTHOR_LENGTH + " 字"));
        }
        String source = submitRequest.getSource();
        if (source != null && source.length() > MAX_SOURCE_LENGTH) {
            return Mono.error(new IllegalArgumentException(
                "来源不能超过 " + MAX_SOURCE_LENGTH + " 字"));
        }
        String submitterName = submitRequest.getSubmitterName();
        if (submitterName != null && submitterName.length() > MAX_NAME_LENGTH) {
            return Mono.error(new IllegalArgumentException(
                "昵称不能超过 " + MAX_NAME_LENGTH + " 字"));
        }

        final String finalCategoryName = categoryName;
        // 校验分类是否存在
        return client.fetch(Category.class, finalCategoryName)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("所选分类不存在")))
            .flatMap(category -> {
                SentenceSubmission submission = new SentenceSubmission();
                submission.setMetadata(new Metadata());
                submission.getMetadata().setGenerateName("subm-");
                SentenceSubmission.Spec spec = new SentenceSubmission.Spec();
                spec.setContent(content);
                spec.setAuthor(StringUtils.hasText(author) ? author : "匿名");
                spec.setSource(StringUtils.hasText(source) ? source : "未知");
                spec.setCategoryName(finalCategoryName);
                spec.setSubmitterName(StringUtils.hasText(submitterName)
                    ? submitterName : null);
                spec.setSubmitterIp(ip);
                spec.setStatus(SentenceSubmission.Status.PENDING);
                submission.setSpec(spec);
                return client.create(submission);
            })
            .flatMap(saved -> ServerResponse.ok().bodyValue(
                buildResponse(true, "ok", "提交成功，等待管理员审核")));
    }

    private @NonNull SubmitResponse buildResponse(boolean success, String code, String message) {
        SubmitResponse response = new SubmitResponse();
        response.setSuccess(success);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    @Data
    @Schema(name = "SubmitRequest")
    public static class SubmitRequest {
        @Schema(description = "句子内容", requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 500)
        private String content;

        @Schema(description = "作者", maxLength = 50)
        private String author;

        @Schema(description = "来源", maxLength = 100)
        private String source;

        @Schema(description = "分类 metadata.name")
        private String categoryName;

        @Schema(description = "提交者昵称", maxLength = 50)
        private String submitterName;
    }

    @Data
    @Schema(name = "SubmitResponse")
    public static class SubmitResponse {
        @Schema(description = "是否成功")
        private boolean success;
        @Schema(description = "状态码：ok / invalid_param / rate_limited / submitted_disabled")
        private String code;
        @Schema(description = "提示信息")
        private String message;
    }
}
