package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.arrayschema.Builder.arraySchemaBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.Sentence;

@Component
@RequiredArgsConstructor
public class SentenceConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "SentenceV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";
    private static final int MAX_IMPORT_COLUMNS = 128;

    private final ReactiveExtensionClient client;
    private final RoleService roleService;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route().POST("sentence/-/batch", this::batchCreateSentence,
                builder -> builder.operationId("batchCreateSentence").summary("批量创建句子").tag(TAG)
                    .requestBody(requestBodyBuilder().content(contentBuilder().array(
                            arraySchemaBuilder().schema(schemaBuilder().implementation(Sentence.class))))
                        .required(true))
                    .response(responseBuilder().implementation(BatchCreateSentenceResult.class)))
            .POST("sentence/-/import-excel", this::importExcelSentences,
                builder -> builder.operationId("importExcelSentences").summary("从 Excel 导入句子")
                    .tag(TAG).requestBody(requestBodyBuilder().content(
                            contentBuilder().mediaType(MediaType.MULTIPART_FORM_DATA_VALUE)
                                .schema(schemaBuilder().implementation(ExcelImportRequest.class)))
                        .required(true))
                    .response(responseBuilder().implementation(BatchCreateSentenceResult.class)))
            .GET("sentence", this::querySentences, builder -> {
                builder.operationId("querySentences").summary("查询句子").tag(TAG).response(
                    responseBuilder().implementation(
                        ListResult.generateGenericClass(Sentence.class)));
                SentenceQuery.buildParameters(builder);
            }).GET("sentence/search", this::searchSentence,
                builder -> builder.operationId("searchSentence").summary("搜索句子").tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("keyword")
                        .implementation(String.class).required(true)).parameter(
                        parameterBuilder().in(ParameterIn.QUERY).name("categoryName")
                            .implementation(String.class).required(false))
                    .response(responseBuilder().implementationArray(Sentence.class))).build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    private @NonNull Mono<ServerResponse> batchCreateSentence(@NonNull ServerRequest request) {
        return request.principal().map(Principal::getName).flatMap(username -> {
            var sentenceFlux = request.bodyToFlux(Sentence.class);
            return createSentences(sentenceFlux, username);
        }).flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private @NonNull Mono<ServerResponse> importExcelSentences(@NonNull ServerRequest request) {
        return request.principal().map(Principal::getName)
            .flatMap(username -> request.multipartData().flatMap(parts -> {
                var file = parts.getFirst("file");
                if (!(file instanceof FilePart filePart)) {
                    return Mono.error(new IllegalArgumentException("请选择 Excel 文件"));
                }
                if (!filePart.filename().toLowerCase().endsWith(".xlsx")) {
                    return Mono.error(new IllegalArgumentException("仅支持 .xlsx 文件"));
                }
                var categoryName = formValue(parts.getFirst("categoryName"));
                if (categoryName == null || categoryName.isBlank()) {
                    return Mono.error(new IllegalArgumentException("请选择目标分类"));
                }
                var contentField = formValue(parts.getFirst("contentField"));
                var authorField = formValue(parts.getFirst("authorField"));
                var sourceField = formValue(parts.getFirst("sourceField"));

                return DataBufferUtils.join(filePart.content()).flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return Mono.fromCallable(
                            () -> parseExcelSentences(bytes, categoryName, contentField,
                                authorField,
                                sourceField)).subscribeOn(Schedulers.boundedElastic());
                    }).flatMapMany(Flux::fromIterable)
                    .as(sentenceFlux -> createSentences(sentenceFlux, username));
            })).flatMap(result -> ServerResponse.ok().bodyValue(result))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.badRequest().bodyValue(e.getMessage()));
    }

    private @NonNull Mono<BatchCreateSentenceResult> createSentences(
        reactor.core.publisher.Flux<Sentence> sentenceFlux, String username) {
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        return roleService.getRolesByUsername(username).collectList().flatMapMany(roles -> {
            boolean hasSuperRole = roles.contains("super-role");

            return sentenceFlux.flatMap(sentence -> {
                sentence.getSpec().setCreatedBy(username);
                if (sentence.getStatus() == null) {
                    sentence.setStatus(new Sentence.Status());
                }
                sentence.getStatus().setPublished(hasSuperRole);
                return client.create(sentence).doOnSuccess(s -> {
                    success.incrementAndGet();
                    total.incrementAndGet();
                }).onErrorResume(e -> {
                    failed.incrementAndGet();
                    total.incrementAndGet();
                    return Mono.empty();
                });
            });
        }).then(Mono.fromCallable(() -> {
            BatchCreateSentenceResult result = new BatchCreateSentenceResult();
            result.setTotal(total.get());
            result.setSuccess(success.get());
            result.setFailed(failed.get());
            return result;
        }));
    }

    private @NonNull List<Sentence> parseExcelSentences(byte[] bytes, String categoryName,
        String contentField, String authorField, String sourceField) throws IOException {
        try (var workbook = new ReadableWorkbook(new ByteArrayInputStream(bytes));
             var rows = workbook.getFirstSheet().openStream()) {
            var iterator = rows.iterator();
            if (!iterator.hasNext()) {
                return List.of();
            }

            var headerRow = iterator.next();
            var headers = readHeaders(headerRow);
            var contentColumn = resolveColumn(headers, contentField,
                List.of("hitokoto", "content", "sentence", "句子内容", "内容", "一言"));
            if (contentColumn == null) {
                throw new IllegalArgumentException("未找到句子内容列");
            }
            var authorColumn =
                resolveColumn(headers, authorField, List.of("from_who", "author", "作者"));
            var sourceColumn =
                resolveColumn(headers, sourceField, List.of("from", "source", "来源", "出处"));

            List<Sentence> sentences = new ArrayList<>();
            while (iterator.hasNext()) {
                var row = iterator.next();
                var content = cellValue(row, contentColumn);
                if (content.isBlank()) {
                    continue;
                }
                var author = authorColumn == null ? "" : cellValue(row, authorColumn);
                var source = sourceColumn == null ? "" : cellValue(row, sourceColumn);
                sentences.add(buildSentence(categoryName, content, author, source));
            }
            return sentences;
        }
    }

    private @NonNull Map<String, Integer> readHeaders(Row row) {
        Map<String, Integer> headers = new HashMap<>();
        for (int columnIndex = 0; columnIndex < MAX_IMPORT_COLUMNS; columnIndex++) {
            var header = cellValue(row, columnIndex);
            if (!header.isEmpty()) {
                headers.put(header, columnIndex);
            }
        }
        return headers;
    }

    private @Nullable Integer resolveColumn(Map<String, Integer> headers, String preferred,
        List<String> aliases) {
        if (preferred != null && !preferred.isBlank() && headers.containsKey(preferred)) {
            return headers.get(preferred);
        }
        for (var alias : aliases) {
            for (var entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(alias)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private @NonNull String cellValue(@NonNull Row row, int columnIndex) {
        return row.getCellAsString(columnIndex).orElse("").trim();
    }

    private @NonNull Sentence buildSentence(String categoryName, String content, String author,
        String source) {
        var sentence = new Sentence();
        sentence.setMetadata(new Metadata());
        sentence.getMetadata().setGenerateName("sentence-");
        var spec = new Sentence.Spec();
        spec.setCategoryName(categoryName);
        spec.setContent(content);
        spec.setAuthor(author == null || author.isBlank() ? "匿名" : author);
        spec.setSource(source == null || source.isBlank() ? "未知" : source);
        sentence.setSpec(spec);
        sentence.setStatus(new Sentence.Status());
        return sentence;
    }

    private String formValue(Part part) {
        return part instanceof FormFieldPart formFieldPart ? formFieldPart.value() : null;
    }

    private @NonNull Mono<ServerResponse> querySentences(ServerRequest request) {
        var query = new SentenceQuery(request);
        return client.listBy(Sentence.class, query.toListOptions(), query.toPageRequest())
            .flatMap(sentences -> ServerResponse.ok().bodyValue(sentences));
    }

    private @NonNull Mono<ServerResponse> searchSentence(ServerRequest request) {
        var query = new SentenceQuery(request);
        return client.listBy(Sentence.class, query.toListOptions(), query.toPageRequest())
            .map(ListResult::getItems)
            .flatMap(sentences -> ServerResponse.ok().bodyValue(sentences));
    }

    @Data
    @Schema(name = "BatchCreateSentenceResult")
    public static class BatchCreateSentenceResult {
        private long total;
        private long success;
        private long failed;
    }

    @Data
    @Schema(name = "ExcelImportRequest")
    public static class ExcelImportRequest {
        @Schema(description = "xlsx 文件", type = "string", format = "binary", requiredMode =
            Schema.RequiredMode.REQUIRED)
        private String file;

        @Schema(description = "目标分类 metadata.name", requiredMode = Schema.RequiredMode.REQUIRED)
        private String categoryName;

        @Schema(description = "句子内容列名")
        private String contentField;

        @Schema(description = "作者列名")
        private String authorField;

        @Schema(description = "来源列名")
        private String sourceField;
    }
}
