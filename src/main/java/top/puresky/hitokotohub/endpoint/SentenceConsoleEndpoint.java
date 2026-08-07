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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Sort;
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
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.extension.router.selector.FieldSelector;
import top.puresky.hitokotohub.UncategorizedConstants;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;

@Slf4j
@Component
@RequiredArgsConstructor
public class SentenceConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "SentenceV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";
    private static final int MAX_IMPORT_COLUMNS = 128;
    private static final int MAX_CONTENT_LENGTH = 500;
    /** Excel 导入文件大小上限(10MB),防止 OOM */
    private static final long MAX_EXCEL_FILE_SIZE = 10 * 1024 * 1024L;

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
                    .response(responseBuilder().implementationArray(Sentence.class)))
            .DELETE("sentence/-/clear-uncategorized", this::clearUncategorizedSentences,
                builder -> builder.operationId("clearUncategorizedSentences")
                    .summary("清空未分类的所有句子").tag(TAG)
                    .response(responseBuilder().implementation(Long.class)))
            .GET("sentence/-/export", this::exportSentences,
                builder -> builder.operationId("exportSentences").summary("导出句子").tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("format")
                        .description("导出格式：json（默认）或 excel").implementation(String.class)
                        .required(false)).parameter(
                        parameterBuilder().in(ParameterIn.QUERY).name("categoryName")
                            .description("按分类导出，留空导出全部；多个分类用逗号分隔").implementation(String.class)
                            .required(false)))
            .POST("sentence/-/import-csv", this::importCsvSentences,
                builder -> builder.operationId("importCsvSentences").summary("从 CSV 导入句子")
                    .tag(TAG).requestBody(requestBodyBuilder().content(
                            contentBuilder().mediaType(MediaType.MULTIPART_FORM_DATA_VALUE)
                                .schema(schemaBuilder().implementation(CsvImportRequest.class)))
                        .required(true))
                    .response(responseBuilder().implementation(BatchCreateSentenceResult.class)))
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    @NonNull Mono<ServerResponse> batchCreateSentence(@NonNull ServerRequest request) {
        return request.principal().map(p -> p.getName()).flatMap(username -> {
            var sentenceFlux = request.bodyToFlux(Sentence.class);
            return createSentences(sentenceFlux, username);
        }).flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private @NonNull Mono<ServerResponse> importExcelSentences(@NonNull ServerRequest request) {
        return request.principal().map(p -> p.getName())
            .flatMap(username -> request.multipartData().flatMap(parts -> {
                var file = parts.getFirst("file");
                if (!(file instanceof FilePart filePart)) {
                    return Mono.error(new IllegalArgumentException("请选择 Excel 文件"));
                }
                if (!filePart.filename().toLowerCase().endsWith(".xlsx")) {
                    return Mono.error(new IllegalArgumentException("仅支持 .xlsx 文件"));
                }
                long contentLength = filePart.headers().getContentLength();
                if (contentLength > MAX_EXCEL_FILE_SIZE) {
                    return Mono.error(new IllegalArgumentException(
                        "Excel 文件不能超过 " + MAX_EXCEL_FILE_SIZE / 1024 / 1024 + "MB"));
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
                        // 防御性检查:Content-Length 可能缺失,读入内存后再次校验
                        if (bytes.length > MAX_EXCEL_FILE_SIZE) {
                            return Mono.<List<Sentence>>error(new IllegalArgumentException(
                                "Excel 文件超过大小限制"));
                        }
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
                sanitizeSentenceInput(sentence);
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
        spec.setAuthor(author);
        spec.setSource(source);
        sentence.setSpec(spec);
        sentence.setStatus(new Sentence.Status());
        return sanitizeSentenceInput(sentence);
    }

    /**
     * 清洗 sentence 输入：trim、null 处理、默认值填充、长度截断。
     *
     * <p>注意：不在此处做 categoryName 归一化（null/不存在 → uncategorized），
     * 那是 SentenceReconciler 的职责。此处仅做"输入净化"。
     *
     * <p>应用范围：{@code createSentences}（批量创建）与 {@code buildSentence}（Excel 导入），
     * 保证两条路径行为一致。
     */
    private @NonNull Sentence sanitizeSentenceInput(Sentence sentence) {
        Sentence.Spec spec = sentence.getSpec();
        if (spec == null) {
            return sentence; // 异常输入，交由 reconciler 兜底
        }
        // content: trim + 长度截断（500）
        String content = StringUtils.trimToEmpty(spec.getContent());
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        spec.setContent(content);
        // categoryName: trim（不归一化，由 reconciler 处理）
        spec.setCategoryName(StringUtils.trimToEmpty(spec.getCategoryName()));
        // author: trim + 空则填默认值
        String author = StringUtils.trimToNull(spec.getAuthor());
        spec.setAuthor(author != null ? author : "匿名");
        // source: trim + 空则填默认值
        String source = StringUtils.trimToNull(spec.getSource());
        spec.setSource(source != null ? source : "未知");
        // linkUrl: trim + 空则置 null
        String linkUrl = StringUtils.trimToNull(spec.getLinkUrl());
        spec.setLinkUrl(linkUrl);
        // postName: trim + 空则置 null
        String postName = StringUtils.trimToNull(spec.getPostName());
        spec.setPostName(postName);
        return sentence;
    }

    private String formValue(Part part) {
        return part instanceof FormFieldPart formFieldPart ? formFieldPart.value() : null;
    }

    @NonNull Mono<ServerResponse> querySentences(ServerRequest request) {
        return listSentences(request)
            .flatMap(sentences -> ServerResponse.ok().bodyValue(sentences));
    }

    @NonNull Mono<ServerResponse> searchSentence(ServerRequest request) {
        return listSentences(request)
            .map(r -> r.getItems())
            .flatMap(sentences -> ServerResponse.ok().bodyValue(sentences));
    }

    /**
     * 共用的句子分页查询逻辑，供 {@link #querySentences} 和 {@link #searchSentence} 复用。
     */
    private Mono<ListResult<Sentence>> listSentences(ServerRequest request) {
        var query = new SentenceQuery(request);
        return client.listBy(Sentence.class, query.toListOptions(), query.toPageRequest());
    }

    @NonNull Mono<ServerResponse> clearUncategorizedSentences(ServerRequest request) {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(
            FieldSelector.of(Queries.equal("spec.categoryName",
                UncategorizedConstants.METADATA_NAME)));

        // 使用 concatMap 串行删除，避免并发触发 SentenceReconciler 导致脏数据清理压力
        // （与 SimilarityCheckServiceImpl.deleteSentencesSerially 风格一致）
        return request.principal().map(p -> p.getName()).defaultIfEmpty("system")
            .flatMap(username -> client.listAll(Sentence.class, listOptions, Sort.unsorted())
                .concatMap(sentence -> client.delete(sentence)
                    .onErrorResume(e -> {
                        log.warn("删除未分类句子 [{}] 失败: {}",
                            sentence.getMetadata().getName(), e.getMessage(), e);
                        return Mono.empty();
                    }))
                .count()
                .doOnSuccess(count -> log.info("User [{}] cleared {} uncategorized sentences", username, count))
                .flatMap(count -> ServerResponse.ok().bodyValue(count)));
    }

    // ===================== 导出句子 =====================

    @NonNull Mono<ServerResponse> exportSentences(@NonNull ServerRequest request) {
        String format = request.queryParam("format").orElse("json");
        String categoryName = request.queryParam("categoryName")
            .filter(StringUtils::isNotBlank).orElse(null);

        var optionsBuilder = ListOptions.builder();
        if (StringUtils.isNotBlank(categoryName)) {
            String[] names = categoryName.split(",");
            if (names.length == 1) {
                optionsBuilder.fieldQuery(Queries.equal("spec.categoryName", names[0].trim()));
            } else {
                List<String> categoryList = new ArrayList<>();
                for (String name : names) {
                    categoryList.add(name.trim());
                }
                optionsBuilder.fieldQuery(Queries.in("spec.categoryName", categoryList));
            }
        }

        return request.principal().map(p -> p.getName()).defaultIfEmpty("system")
            .flatMap(username -> client.listAll(Sentence.class, optionsBuilder.build(),
                    Sort.by("metadata.creationTimestamp").descending())
                .collectList()
                .flatMap(sentences -> {
                    if (sentences.isEmpty()) {
                        return ServerResponse.ok()
                            .bodyValue(format.equalsIgnoreCase("excel") ? new byte[0] : List.of());
                    }
                    log.info("User [{}] exported {} sentences in {} format, category={}",
                        username, sentences.size(), format, categoryName != null ? categoryName : "all");
                    // 构建分类名映射：metadata.name → spec.name（中文显示名）
                    return client.listAll(Category.class, ListOptions.builder()
                            .fieldQuery(Queries.isNull("metadata.deletionTimestamp")).build(),
                            Sort.unsorted())
                        .collectMap(c -> c.getMetadata().getName(), c -> c.getSpec().getName())
                        .defaultIfEmpty(Map.of())
                        .flatMap(categoryMap -> {
                            if ("excel".equalsIgnoreCase(format)) {
                                return Mono.fromCallable(
                                        () -> buildExcelExport(sentences, categoryMap))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .flatMap(bytes -> ServerResponse.ok()
                                        .contentType(MediaType.parseMediaType(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                        .header("Content-Disposition",
                                            "attachment; filename=hitokoto-export.xlsx")
                                        .bodyValue(bytes));
                            }
                            // JSON 导出
                            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(sentences);
                        });
                }));
    }

    /**
     * 构建 Excel 导出工作簿（内存中）。
     *
     * <p>列：内容 / 作者 / 来源 / 分类 / 点赞数 / 浏览数 / 是否发布
     *
     * @param sentences   导出的句子列表
     * @param categoryMap 分类 metadata.name → spec.name 映射（用于显示中文分类名）
     */
    private byte[] buildExcelExport(List<Sentence> sentences,
        Map<String, String> categoryMap) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Workbook wb = new Workbook(os, "HitokotoExport", "1.0");
        Worksheet ws = wb.newWorksheet("句子");
        ws.value(0, 0, "内容");
        ws.value(0, 1, "作者");
        ws.value(0, 2, "来源");
        ws.value(0, 3, "分类");
        ws.value(0, 4, "点赞数");
        ws.value(0, 5, "浏览数");
        ws.value(0, 6, "是否发布");
        for (int i = 0; i < sentences.size(); i++) {
            Sentence s = sentences.get(i);
            int row = i + 1;
            String categoryName = s.getSpec().getCategoryName();
            String displayName;
            if (UncategorizedConstants.METADATA_NAME.equals(categoryName)) {
                displayName = UncategorizedConstants.DISPLAY_NAME;
            } else {
                displayName = categoryMap.getOrDefault(categoryName, categoryName);
            }
            ws.value(row, 0, s.getSpec().getContent());
            ws.value(row, 1, s.getSpec().getAuthor());
            ws.value(row, 2, s.getSpec().getSource());
            ws.value(row, 3, displayName);
            ws.value(row, 4, s.getStatus() != null ? s.getStatus().getLikeCount() : 0);
            ws.value(row, 5, s.getStatus() != null ? s.getStatus().getViewCount() : 0);
            ws.value(row, 6,
                s.getStatus() != null && s.getStatus().isPublished() ? "是" : "否");
        }
        wb.finish();
        return os.toByteArray();
    }

    // ===================== CSV 导入 =====================

    private @NonNull Mono<ServerResponse> importCsvSentences(@NonNull ServerRequest request) {
        return request.principal().map(p -> p.getName())
            .flatMap(username -> request.multipartData().flatMap(parts -> {
                var file = parts.getFirst("file");
                if (!(file instanceof FilePart filePart)) {
                    return Mono.<List<Sentence>>error(new IllegalArgumentException("请选择 CSV 文件"));
                }
                if (!filePart.filename().toLowerCase().endsWith(".csv")) {
                    return Mono.<List<Sentence>>error(new IllegalArgumentException("仅支持 .csv 文件"));
                }
                long contentLength = filePart.headers().getContentLength();
                if (contentLength > MAX_EXCEL_FILE_SIZE) {
                    return Mono.<List<Sentence>>error(new IllegalArgumentException(
                        "CSV 文件不能超过 " + MAX_EXCEL_FILE_SIZE / 1024 / 1024 + "MB"));
                }
                var categoryName = formValue(parts.getFirst("categoryName"));
                if (categoryName == null || categoryName.isBlank()) {
                    return Mono.<List<Sentence>>error(new IllegalArgumentException("请选择目标分类"));
                }
                var contentField = formValue(parts.getFirst("contentField"));
                var authorField = formValue(parts.getFirst("authorField"));
                var sourceField = formValue(parts.getFirst("sourceField"));

                return DataBufferUtils.join(filePart.content()).flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        if (bytes.length > MAX_EXCEL_FILE_SIZE) {
                            return Mono.<List<Sentence>>error(new IllegalArgumentException(
                                "CSV 文件超过大小限制"));
                        }
                        return Mono.fromCallable(() -> parseCsvSentences(bytes, categoryName,
                            contentField, authorField, sourceField))
                            .subscribeOn(Schedulers.boundedElastic());
                    }).flatMapMany(Flux::fromIterable)
                    .as(sentenceFlux -> createSentences(sentenceFlux, username));
            })).flatMap(result -> ServerResponse.ok().bodyValue(result))
            .onErrorResume(IllegalArgumentException.class,
                e -> ServerResponse.badRequest().bodyValue(e.getMessage()));
    }

    /**
     * 解析 CSV 为句子列表。复用 Excel 导入的别名解析逻辑。
     *
     * <p>支持 RFC 4180 引号转义、字段内换行；自动处理 UTF-8 BOM。
     */
    private @NonNull List<Sentence> parseCsvSentences(byte[] bytes, String categoryName,
        String contentField, String authorField, String sourceField) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        // 去除 UTF-8 BOM
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        // 统一换行符
        content = content.replace("\r\n", "\n").replace('\r', '\n');
        List<String[]> rows = parseCsv(content);
        if (rows.isEmpty()) {
            return List.of();
        }

        String[] headerRow = rows.get(0);
        Map<String, Integer> headers = new HashMap<>();
        for (int i = 0; i < headerRow.length; i++) {
            String h = headerRow[i].trim();
            if (!h.isEmpty()) {
                headers.put(h, i);
            }
        }
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
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            var contentValue = cellValue(row, contentColumn);
            if (contentValue.isBlank()) {
                continue;
            }
            var author = authorColumn == null ? "" : cellValue(row, authorColumn);
            var source = sourceColumn == null ? "" : cellValue(row, sourceColumn);
            sentences.add(buildSentence(categoryName, contentValue, author, source));
        }
        return sentences;
    }

    /** 简易 RFC 4180 CSV 解析：支持引号包裹、字段内逗号与换行、双引号转义。 */
    private @NonNull List<String[]> parseCsv(String text) {
        List<String[]> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                } else if (c == '\n') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                    records.add(fields.toArray(new String[0]));
                    fields.clear();
                } else {
                    sb.append(c);
                }
            }
        }
        // 处理最后一行（无尾换行的情况）
        if (sb.length() > 0 || !fields.isEmpty()) {
            fields.add(sb.toString());
            records.add(fields.toArray(new String[0]));
        }
        return records;
    }

    private @NonNull String cellValue(String[] row, int columnIndex) {
        return columnIndex < row.length ? row[columnIndex].trim() : "";
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

    @Data
    @Schema(name = "CsvImportRequest")
    public static class CsvImportRequest {
        @Schema(description = "csv 文件", type = "string", format = "binary",
            requiredMode = Schema.RequiredMode.REQUIRED)
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
