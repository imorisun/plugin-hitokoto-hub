package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;
import org.jspecify.annotations.NonNull;
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
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SentenceSubmission;

@Component
@RequiredArgsConstructor
public class SentenceSubmissionConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "SentenceSubmissionV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;
    private final SettingConfig settingConfig;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("sentence-submissions", this::listSubmissions,
                builder -> builder.operationId("listSentenceSubmissions")
                    .summary("查询访客提交句子列表")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("page")
                        .description("页码，从 1 开始").implementation(Integer.class).required(false))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("size")
                        .description("每页数量").implementation(Integer.class).required(false))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status")
                        .description("按状态过滤：PENDING / APPROVED / REJECTED")
                        .implementation(String.class).required(false))
                    .response(responseBuilder().implementation(
                        ListResult.generateGenericClass(SentenceSubmission.class))))
            .POST("sentence-submissions/{name}/approve", this::approveSubmission,
                builder -> builder.operationId("approveSentenceSubmission")
                    .summary("审核通过访客提交的句子")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("提交记录名称").implementation(String.class).required(true))
                    .requestBody(requestBodyBuilder().required(false).content(
                        contentBuilder().schema(schemaBuilder().implementation(
                            ApproveRequest.class))))
                    .response(responseBuilder().implementation(SentenceSubmission.class)))
            .POST("sentence-submissions/{name}/reject", this::rejectSubmission,
                builder -> builder.operationId("rejectSentenceSubmission")
                    .summary("拒绝访客提交的句子")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("提交记录名称").implementation(String.class).required(true))
                    .requestBody(requestBodyBuilder().required(false).content(
                        contentBuilder().schema(schemaBuilder().implementation(
                            RejectRequest.class))))
                    .response(responseBuilder().implementation(SentenceSubmission.class)))
            .DELETE("sentence-submissions/{name}", this::deleteSubmission,
                builder -> builder.operationId("deleteSentenceSubmission")
                    .summary("删除访客提交记录")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("提交记录名称").implementation(String.class).required(true))
                    .response(responseBuilder().implementation(Object.class)))
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    private @NonNull Mono<ServerResponse> listSubmissions(@NonNull ServerRequest request) {
        int page = NumberUtils.toInt(request.queryParam("page").orElse("1"), 1);
        int size = NumberUtils.toInt(request.queryParam("size").orElse("20"), 20);
        String status = request.queryParam("status").orElse(null);

        var optionsBuilder = ListOptions.builder();
        if (StringUtils.hasText(status)) {
            optionsBuilder.fieldQuery(Queries.equal("spec.status", status));
        }

        var pageRequest =
            PageRequestImpl.of(page, size, Sort.by("metadata.creationTimestamp").descending());

        return client.listBy(SentenceSubmission.class, optionsBuilder.build(), pageRequest)
            .flatMap(submissions -> ServerResponse.ok().bodyValue(submissions));
    }

    private @NonNull Mono<ServerResponse> approveSubmission(@NonNull ServerRequest request) {
        String name = request.pathVariable("name");
        Mono<ApproveRequest> bodyMono = request.bodyToMono(ApproveRequest.class)
            .defaultIfEmpty(new ApproveRequest());

        return request.principal().map(p -> p.getName())
            .flatMap(username -> bodyMono.flatMap(approveRequest ->
                client.fetch(SentenceSubmission.class, name)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("提交记录不存在")))
                    .flatMap(submission -> {
                        if (submission.getSpec().getStatus() != SentenceSubmission.Status.PENDING) {
                            return Mono.error(
                                new IllegalStateException("该提交已被处理，无法重复审核"));
                        }
                        // 使用管理员可能编辑后的字段创建 Sentence
                        return settingConfig.getSubmissionConfig()
                            .flatMap(config -> createSentenceFromSubmission(submission,
                                approveRequest, Boolean.TRUE.equals(config.getSubmissionAutoPublish())))
                            .flatMap(sentence -> {
                                submission.getSpec().setSentenceName(
                                    sentence.getMetadata().getName());
                                submission.getSpec().setStatus(
                                    SentenceSubmission.Status.APPROVED);
                                submission.getSpec().setReviewedBy(username);
                                submission.getSpec().setReviewedAt(Instant.now().toString());
                                if (StringUtils.hasText(approveRequest.getReviewNote())) {
                                    submission.getSpec().setReviewNote(
                                        approveRequest.getReviewNote());
                                }
                                return client.update(submission)
                                    .thenReturn(submission);
                            });
                    })))
            .flatMap(submission -> ServerResponse.ok().bodyValue(submission))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(Map.of("message", e.getMessage())))
            .onErrorResume(IllegalStateException.class,
                e -> ServerResponse.status(HttpStatus.CONFLICT)
                    .bodyValue(Map.of("message", e.getMessage())));
    }

    private @NonNull Mono<ServerResponse> rejectSubmission(@NonNull ServerRequest request) {
        String name = request.pathVariable("name");
        Mono<RejectRequest> bodyMono = request.bodyToMono(RejectRequest.class)
            .defaultIfEmpty(new RejectRequest());

        return request.principal().map(p -> p.getName())
            .flatMap(username -> bodyMono.flatMap(rejectRequest ->
                client.fetch(SentenceSubmission.class, name)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("提交记录不存在")))
                    .flatMap(submission -> {
                        if (submission.getSpec().getStatus() != SentenceSubmission.Status.PENDING) {
                            return Mono.error(
                                new IllegalStateException("该提交已被处理，无法重复审核"));
                        }
                        submission.getSpec().setStatus(SentenceSubmission.Status.REJECTED);
                        submission.getSpec().setReviewedBy(username);
                        submission.getSpec().setReviewedAt(Instant.now().toString());
                        if (StringUtils.hasText(rejectRequest.getRejectionReason())) {
                            submission.getSpec().setReviewNote(rejectRequest.getRejectionReason());
                        }
                        return client.update(submission)
                            .thenReturn(submission);
                    })))
            .flatMap(submission -> ServerResponse.ok().bodyValue(submission))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(Map.of("message", e.getMessage())))
            .onErrorResume(IllegalStateException.class,
                e -> ServerResponse.status(HttpStatus.CONFLICT)
                    .bodyValue(Map.of("message", e.getMessage())));
    }

    private @NonNull Mono<ServerResponse> deleteSubmission(@NonNull ServerRequest request) {
        String name = request.pathVariable("name");
        return client.fetch(SentenceSubmission.class, name)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("提交记录不存在")))
            .flatMap(client::delete)
            .then(ServerResponse.ok().bodyValue(Map.of("message", "删除成功")))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(Map.of("message", e.getMessage())));
    }

    /**
     * 使用提交记录创建句子，若管理员在审核时修改了内容/作者/来源/分类，则使用修改后的值。
     */
    private @NonNull Mono<Sentence> createSentenceFromSubmission(
        SentenceSubmission submission, ApproveRequest approveRequest, boolean autoPublish) {
        Sentence sentence = new Sentence();
        sentence.setMetadata(new Metadata());
        sentence.getMetadata().setGenerateName("sentence-");
        Sentence.Spec spec = new Sentence.Spec();
        // 优先使用管理员编辑后的内容，未提供则回退到原始提交内容
        String content = StringUtils.hasText(approveRequest.getContent())
            ? approveRequest.getContent().trim() : submission.getSpec().getContent();
        spec.setContent(content);
        String author = StringUtils.hasText(approveRequest.getAuthor())
            ? approveRequest.getAuthor().trim()
            : (StringUtils.hasText(submission.getSpec().getAuthor())
                ? submission.getSpec().getAuthor() : "匿名");
        spec.setAuthor(author);
        String source = StringUtils.hasText(approveRequest.getSource())
            ? approveRequest.getSource().trim()
            : (StringUtils.hasText(submission.getSpec().getSource())
                ? submission.getSpec().getSource() : "未知");
        spec.setSource(source);
        String categoryName = StringUtils.hasText(approveRequest.getCategoryName())
            ? approveRequest.getCategoryName().trim()
            : submission.getSpec().getCategoryName();
        spec.setCategoryName(categoryName);
        spec.setCreatedBy("visitor");
        sentence.setSpec(spec);
        sentence.setStatus(new Sentence.Status());
        sentence.getStatus().setPublished(autoPublish);
        return client.create(sentence);
    }

    /**
     * 审核通过请求，管理员可在审核时编辑句子的内容、作者、来源、分类。
     */
    @Data
    @Schema(name = "ApproveRequest")
    public static class ApproveRequest {
        @Schema(description = "编辑后的句子内容（可选，不传则使用原始提交内容）", maxLength = 500)
        private String content;

        @Schema(description = "编辑后的作者（可选）", maxLength = 50)
        private String author;

        @Schema(description = "编辑后的来源（可选）", maxLength = 100)
        private String source;

        @Schema(description = "编辑后的分类 metadata.name（可选）")
        private String categoryName;

        @Schema(description = "审核备注（可选，会展示给访客）")
        private String reviewNote;
    }

    /**
     * 审核拒绝请求，可填写拒绝理由。
     */
    @Data
    @Schema(name = "RejectRequest")
    public static class RejectRequest {
        @Schema(description = "拒绝理由（可选，会记录为审核备注）")
        private String rejectionReason;
    }
}
