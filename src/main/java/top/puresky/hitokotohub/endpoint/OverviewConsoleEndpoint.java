package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;
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
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.endpoint.overview.EchartsDataBuilder;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.service.CategoryCountService;
import top.puresky.hitokotohub.service.dto.CategoryStats;

@Component
@RequiredArgsConstructor
public class OverviewConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "OverviewV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;
    private final EchartsDataBuilder echartsDataBuilder;
    private final CategoryCountService categoryCountService;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("overview", this::getOverview,
                builder -> builder.operationId("getOverview")
                    .summary("获取概览信息")
                    .tag(TAG)
                    .response(responseBuilder().implementation(OverviewResponse.class)))
            .GET("overview/view-statistics", this::getViewStatistics,
                builder -> builder.operationId("getViewStatistics")
                    .summary("获取分类浏览量统计数据（用于折线图）")
                    .tag(TAG)
                    .response(responseBuilder().implementation(ViewStatisticsResponse.class)))
            .GET("overview/today-sentence-details", this::getTodaySentenceDetails,
                builder -> builder.operationId("getTodaySentenceDetails")
                    .summary("获取今日句子维度的浏览量/点赞量详情（按句子聚合）")
                    .tag(TAG)
                    .response(responseBuilder().implementation(TodaySentenceDetailsResponse.class)))
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    /**
     * 获取概览信息(优化版,消除 N+1 查询)。
     *
     * <p>原实现:3 次 countBy + 每分类 3 次 countBy = 3+3C 次查询(C=分类数)。
     * <p>现实现:3 次并行 listAll(Category/Sentence/CategoryViewRecord) + 内存聚合 = 3 次查询。
     * <p>对 C=20 分类场景,查询数从 63 降至 3(约 21x),且全部并行。
     */
    private @NonNull Mono<ServerResponse> getOverview(ServerRequest request) {
        // 3 个并行查询,消除 N+1:
        // 1. listAll(Category) → 分类列表 + 显示名
        // 2. categoryCountService.getCategoryStats() → 每分类 {total, published}(内含单次 listAll(Sentence))
        // 3. listAll(CategoryViewRecord) → 内存按 (categoryName, eventType) 分组得 view/like 计数
        Mono<List<Category>> categoriesMono = client.listAll(Category.class,
            ListOptions.builder().build(), Sort.unsorted()).collectList();
        Mono<Map<String, CategoryStats>> statsMono = categoryCountService.getCategoryStats();
        Mono<List<CategoryViewRecord>> viewRecordsMono = client.listAll(CategoryViewRecord.class,
            ListOptions.builder().build(), Sort.unsorted()).collectList();

        return Mono.zip(categoriesMono, statsMono, viewRecordsMono)
            .map(tuple -> buildOverviewResponse(tuple.getT1(), tuple.getT2(), tuple.getT3()))
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    /**
     * 纯函数:基于分类列表、句子统计、浏览记录构建 OverviewResponse。
     *
     * <p>提取为独立方法便于单元测试,无需 mock ReactiveExtensionClient。
     */
    private OverviewResponse buildOverviewResponse(List<Category> categories,
                                                     Map<String, CategoryStats> stats,
                                                     List<CategoryViewRecord> viewRecords) {
        // 内存按 (categoryName, eventType) 分组得 view/like 计数
        // long[]{viewCount, likeCount}
        Map<String, long[]> viewStats = new HashMap<>(categories.size() + 8);
        for (CategoryViewRecord r : viewRecords) {
            if (r.getSpec() == null) {
                continue;
            }
            String cat = r.getSpec().getCategoryName();
            if (cat == null || cat.isBlank()) {
                continue;
            }
            long[] arr = viewStats.computeIfAbsent(cat, k -> new long[2]);
            CategoryViewRecord.EventType type = r.getSpec().getEventType();
            if (type == CategoryViewRecord.EventType.VIEW) {
                arr[0]++;
            } else if (type == CategoryViewRecord.EventType.LIKE) {
                arr[1]++;
            }
        }

        // 构建分类分布,同时累加总数
        List<OverviewResponse.CategoryDistribution> distribution =
            new ArrayList<>(categories.size());
        for (Category category : categories) {
            String categoryName = category.getMetadata().getName();
            String displayName = category.getSpec() != null
                ? category.getSpec().getName() : categoryName;
            CategoryStats stat = stats.getOrDefault(categoryName, new CategoryStats(0, 0));
            long[] viewArr = viewStats.getOrDefault(categoryName, new long[]{0, 0});

            OverviewResponse.CategoryDistribution dist =
                new OverviewResponse.CategoryDistribution();
            dist.setCategoryName(categoryName);
            dist.setDisplayName(displayName);
            dist.setCount(stat.total());
            dist.setPublishedCount(stat.published());
            dist.setNotPublishedCount(stat.notPublished());
            dist.setViewCount(viewArr[0]);
            dist.setLikeCount(viewArr[1]);
            distribution.add(dist);
        }

        // 汇总总数(包含未注册分类的句子,与原 countBy 行为一致)
        long totalSentences = stats.values().stream().mapToLong(CategoryStats::total).sum();
        long totalPublished = stats.values().stream().mapToLong(CategoryStats::published).sum();

        OverviewResponse response = new OverviewResponse();
        response.setSentenceCount(totalSentences);
        response.setCategoryCount(categories.size());
        response.setPublishedSentenceCount(totalPublished);
        response.setNotPublishedSentenceCount(totalSentences - totalPublished);
        response.setCategoryDistribution(distribution);
        return response;
    }

    private @NonNull Mono<ServerResponse> getViewStatistics(ServerRequest request) {
        int days = NumberUtils.toInt(request.queryParam("days").orElse("30"), 30);
        String granularity = request.queryParam("granularity").orElse("day");
        String eventType = request.queryParam("eventType").orElse("VIEW");

        Instant startTime = Instant.now().minus(Duration.ofDays(days));

        Mono<List<CategoryViewRecord>> recordsMono = client.listAll(
            CategoryViewRecord.class,
            ListOptions.builder()
                .fieldQuery(Queries.and(
                    Queries.greaterThan("metadata.creationTimestamp", startTime.toString()),
                    Queries.equal("spec.eventType", eventType)
                ))
                .build(),
            Sort.by("metadata.creationTimestamp").ascending()
        ).collectList();

        Mono<Map<String, String>> categoryNameMap =
            client.listAll(Category.class, ListOptions.builder().build(), Sort.unsorted())
                .collectMap(
                    category -> category.getMetadata().getName(),
                    category -> category.getSpec().getName()
                );

        Mono<Long> totalCount = client.countBy(CategoryViewRecord.class,
            ListOptions.builder()
                .fieldQuery(Queries.equal("spec.eventType", eventType))
                .build());

        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Mono<Long> todayCount = client.countBy(CategoryViewRecord.class,
            ListOptions.builder()
                .fieldQuery(Queries.and(
                    Queries.greaterThan("metadata.creationTimestamp", todayStart.toString()),
                    Queries.equal("spec.eventType", eventType)
                ))
                .build());

        return Mono.zip(recordsMono, categoryNameMap, totalCount, todayCount)
            .flatMap(tuple -> {
                List<CategoryViewRecord> records = tuple.getT1();
                Map<String, String> nameMap = tuple.getT2();
                Long total = tuple.getT3();
                Long today = tuple.getT4();

                ViewStatisticsResponse response = new ViewStatisticsResponse();
                response.setSuccess(true);
                response.setTotalViewCount(total);
                response.setTodayViewCount(today);

                if (!records.isEmpty()) {
                    Map<String, Map<String, Long>> aggregatedData =
                        echartsDataBuilder.aggregateByGranularity(records, granularity);
                    List<ViewStatisticsResponse.TimePoint> timePoints =
                        echartsDataBuilder.buildTimePoints(aggregatedData, nameMap);
                    response.setTimeSeries(timePoints);
                    response.setEchartsData(echartsDataBuilder.buildEchartsData(timePoints));
                } else {
                    response.setTimeSeries(new ArrayList<>());
                    response.setEchartsData(echartsDataBuilder.buildEchartsData(new ArrayList<>()));
                }

                return ServerResponse.ok().bodyValue(response);
            });
    }

    /**
     * 获取今日句子维度的事件详情（浏览量/点赞量），按事件数降序排列。
     * 会按句子名称聚合记录数，并填充句子的内容、作者、来源、分类显示名等信息。
     * 为避免 OOM，仅查询今日涉及的句子和分类，而非全量加载。
     *
     * @param request 包含 query 参数 eventType（VIEW 或 LIKE，默认 LIKE）
     */
    private @NonNull Mono<ServerResponse> getTodaySentenceDetails(ServerRequest request) {
        String eventType = request.queryParam("eventType").orElse("LIKE");
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();

        return client.listAll(
                CategoryViewRecord.class,
                ListOptions.builder()
                    .fieldQuery(Queries.and(
                        Queries.greaterThan("metadata.creationTimestamp", todayStart.toString()),
                        Queries.equal("spec.eventType", eventType)
                    ))
                    .build(),
                Sort.by("metadata.creationTimestamp").descending()
            ).collectList()
            .flatMap(records -> {
                // 提取涉及的句子名称集合，仅查询需要的句子
                Set<String> sentenceNames = records.stream()
                    .map(r -> r.getSpec().getSentenceName())
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toSet());

                if (sentenceNames.isEmpty()) {
                    TodaySentenceDetailsResponse emptyResponse =
                        new TodaySentenceDetailsResponse();
                    emptyResponse.setSuccess(true);
                    emptyResponse.setEventType(eventType);
                    emptyResponse.setTotal(0);
                    emptyResponse.setSentences(List.of());
                    return ServerResponse.ok().bodyValue(emptyResponse);
                }

                // 按需查询涉及的句子
                Mono<Map<String, Sentence>> sentenceMapMono =
                    client.listAll(Sentence.class,
                            ListOptions.builder()
                                .fieldQuery(Queries.in("metadata.name", sentenceNames))
                                .build(),
                            Sort.unsorted())
                        .collectMap(s -> s.getMetadata().getName(), s -> s);

                // 按需查询涉及的分类
                Mono<Map<String, Category>> categoryMapMono = sentenceMapMono
                    .flatMap(sentenceMap -> {
                        Set<String> categoryNames = sentenceMap.values().stream()
                            .map(s -> s.getSpec().getCategoryName())
                            .filter(name -> name != null && !name.isBlank())
                            .collect(Collectors.toSet());

                        if (categoryNames.isEmpty()) {
                            return Mono.just(Map.<String, Category>of());
                        }
                        return client.listAll(Category.class,
                                ListOptions.builder()
                                    .fieldQuery(Queries.in("metadata.name", categoryNames))
                                    .build(),
                                Sort.unsorted())
                            .collectMap(c -> c.getMetadata().getName(), c -> c);
                    });

                return Mono.zip(sentenceMapMono, categoryMapMono)
                    .flatMap(tuple -> {
                        Map<String, Sentence> sentences = tuple.getT1();
                        Map<String, Category> cats = tuple.getT2();

                        // 按句子名称聚合事件数
                        Map<String, TodaySentenceDetailsResponse.TodaySentenceDetail> grouped =
                            new LinkedHashMap<>();
                        for (CategoryViewRecord record : records) {
                            String sentenceName = record.getSpec().getSentenceName();
                            if (sentenceName == null || sentenceName.isBlank()) {
                                continue;
                            }
                            grouped.compute(sentenceName, (key, existing) -> {
                                if (existing == null) {
                                    TodaySentenceDetailsResponse.TodaySentenceDetail item =
                                        new TodaySentenceDetailsResponse.TodaySentenceDetail();
                                    item.setSentenceName(sentenceName);
                                    item.setEventCount(1L);
                                    item.setLastEventTime(record.getMetadata().getCreationTimestamp());
                                    return item;
                                }
                                existing.setEventCount(existing.getEventCount() + 1);
                                return existing;
                            });
                        }

                        // 填充句子详细信息
                        List<TodaySentenceDetailsResponse.TodaySentenceDetail> result = new ArrayList<>();
                        for (TodaySentenceDetailsResponse.TodaySentenceDetail item : grouped.values()) {
                            Sentence s = sentences.get(item.getSentenceName());
                            if (s != null) {
                                item.setContent(s.getSpec().getContent());
                                item.setAuthor(s.getSpec().getAuthor());
                                item.setSource(s.getSpec().getSource());
                                String catName = s.getSpec().getCategoryName();
                                item.setCategoryName(catName);
                                Category cat = cats.get(catName);
                                item.setCategoryDisplayName(
                                    cat != null ? cat.getSpec().getName() : catName);
                            }
                            result.add(item);
                        }

                        // 按事件数降序排列
                        result.sort(Comparator.comparing(
                            TodaySentenceDetailsResponse.TodaySentenceDetail::getEventCount).reversed());

                        TodaySentenceDetailsResponse response = new TodaySentenceDetailsResponse();
                        response.setSuccess(true);
                        response.setEventType(eventType);
                        response.setTotal(result.size());
                        response.setSentences(result);
                        return ServerResponse.ok().bodyValue(response);
                    });
            })
            ;
    }

    @Data
    @Schema(name = "OverviewResponse")
    public static class OverviewResponse {
        @Schema(description = "句子总数")
        private long sentenceCount;
        @Schema(description = "分类总数")
        private long categoryCount;
        @Schema(description = "已发布句子数")
        private long publishedSentenceCount;
        @Schema(description = "未发布句子数")
        private long notPublishedSentenceCount;
        @Schema(description = "各分类句子数量分布")
        private List<CategoryDistribution> categoryDistribution;

        @Data
        @Schema(name = "CategoryDistribution")
        public static class CategoryDistribution {
            @Schema(description = "分类 metadata name")
            private String categoryName;
            @Schema(description = "分类显示名称")
            private String displayName;
            @Schema(description = "句子数量")
            private long count;
            @Schema(description = "公开的句子数量")
            private long publishedCount;
            @Schema(description = "未公开的句子数量")
            private long notPublishedCount;
            @Schema(description = "浏览量")
            private long viewCount;
            @Schema(description = "点赞量")
            private long likeCount;
        }
    }

    @Data
    @Schema(name = "ViewStatisticsResponse")
    public static class ViewStatisticsResponse {
        @Schema(description = "是否成功")
        private boolean success;
        @Schema(description = "提示信息")
        private String message;
        @Schema(description = "总计")
        private long totalViewCount;
        @Schema(description = "今日")
        private long todayViewCount;
        @Schema(description = "时间序列数据")
        private List<TimePoint> timeSeries;
        @Schema(description = "ECharts 格式数据")
        private EChartsData echartsData;

        @Data
        @Schema(name = "TimePoint")
        public static class TimePoint {
            @Schema(description = "时间点")
            private String time;
            @Schema(description = "该时间点总数")
            private Long totalCount;
            @Schema(description = "各分类详情")
            private List<CategoryDetail> details;
        }

        @Data
        @Schema(name = "CategoryDetail")
        public static class CategoryDetail {
            @Schema(description = "分类名称")
            private String categoryName;
            @Schema(description = "分类显示名称")
            private String displayName;
            @Schema(description = "数量")
            private Long count;
        }

        @Data
        @Schema(name = "EChartsData")
        public static class EChartsData {
            @Schema(description = "X轴数据")
            private List<String> xAxis;
            @Schema(description = "系列数据")
            private List<EChartsSeries> series;
        }

        @Data
        @Schema(name = "EChartsSeries")
        public static class EChartsSeries {
            @Schema(description = "分类名称")
            private String name;
            @Schema(description = "分类显示名称")
            private String displayName;
            @Schema(description = "图表类型")
            private String type = "line";
            @Schema(description = "数据点")
            private List<Long> data;
            @Schema(description = "是否平滑曲线")
            private boolean smooth = true;
        }
    }

    @Data
    @Schema(name = "TodaySentenceDetailsResponse")
    public static class TodaySentenceDetailsResponse {
        @Schema(description = "是否成功")
        private boolean success;
        @Schema(description = "事件类型：VIEW / LIKE")
        private String eventType;
        @Schema(description = "今日有事件的句子总数（去重）")
        private long total;
        @Schema(description = "句子详情列表（按事件数降序）")
        private List<TodaySentenceDetail> sentences;

        @Data
        @Schema(name = "TodaySentenceDetail")
        public static class TodaySentenceDetail {
            @Schema(description = "句子 metadata name")
            private String sentenceName;
            @Schema(description = "句子内容")
            private String content;
            @Schema(description = "作者")
            private String author;
            @Schema(description = "来源")
            private String source;
            @Schema(description = "分类名称")
            private String categoryName;
            @Schema(description = "分类显示名称")
            private String categoryDisplayName;
            @Schema(description = "今日事件数（浏览量或点赞量）")
            private Long eventCount;
            @Schema(description = "最近一次事件时间")
            private Instant lastEventTime;
        }
    }
}