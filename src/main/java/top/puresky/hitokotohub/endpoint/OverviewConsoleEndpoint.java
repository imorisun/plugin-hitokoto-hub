package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
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
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverviewConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "OverviewV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;

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
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    private @NonNull Mono<ServerResponse> getOverview(ServerRequest request) {
        Mono<Long> sentenceCount = client.countBy(Sentence.class, ListOptions.builder().build());
        Mono<Long> categoryCount = client.countBy(Category.class, ListOptions.builder().build());
        Mono<Long> publishedSentenceCount = client.countBy(Sentence.class,
            ListOptions.builder().fieldQuery(Queries.equal("status.isPublished", true)).build());
        Mono<List<OverviewResponse.CategoryDistribution>> categoryDistribution =
            client.listAll(Category.class, ListOptions.builder().build(), Sort.unsorted())
                .flatMap(category -> {
                    OverviewResponse.CategoryDistribution dist =
                        new OverviewResponse.CategoryDistribution();
                    String categoryName = category.getMetadata().getName();
                    String displayName = category.getSpec().getName();
                    long totalCount = category.getStatus().getSentenceCount();

                    dist.setCategoryName(categoryName);
                    dist.setDisplayName(displayName);
                    dist.setCount(totalCount);

                    return client.countBy(Sentence.class, ListOptions.builder()
                            .fieldQuery(Queries.and(
                                Queries.equal("spec.categoryName", categoryName),
                                Queries.equal("status.isPublished", true)))
                            .build())
                        .flatMap(count -> {
                            dist.setPublishedCount(count);
                            dist.setNotPublishedCount(totalCount - count);

                            return client.countBy(CategoryViewRecord.class, ListOptions.builder()
                                    .fieldQuery(Queries.equal("spec.categoryName", categoryName))
                                    .build())
                                .doOnNext(dist::setViewCount)
                                .thenReturn(dist);
                        });
                })
                .collectList();

        return Mono.zip(sentenceCount, categoryCount, publishedSentenceCount, categoryDistribution)
            .map(tuple -> {
                OverviewResponse response = new OverviewResponse();
                response.setSentenceCount(tuple.getT1());
                response.setCategoryCount(tuple.getT2());
                response.setPublishedSentenceCount(tuple.getT3());
                response.setNotPublishedSentenceCount(tuple.getT1() - tuple.getT3());
                response.setCategoryDistribution(tuple.getT4());
                return response;
            }).flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private @NonNull Mono<ServerResponse> getViewStatistics(ServerRequest request) {
        int days = Integer.parseInt(request.queryParam("days").orElse("30"));
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
                        aggregateByGranularity(records, granularity);
                    List<ViewStatisticsResponse.TimePoint> timePoints =
                        buildTimePoints(aggregatedData, nameMap);
                    response.setTimeSeries(timePoints);
                    response.setEchartsData(buildEchartsData(timePoints));
                } else {
                    response.setTimeSeries(new ArrayList<>());
                    ViewStatisticsResponse.EChartsData emptyECharts =
                        new ViewStatisticsResponse.EChartsData();
                    emptyECharts.setXAxis(new ArrayList<>());
                    emptyECharts.setSeries(new ArrayList<>());
                    response.setEchartsData(emptyECharts);
                }

                return ServerResponse.ok().bodyValue(response);
            });
    }

    private Map<String, Map<String, Long>> aggregateByGranularity(List<CategoryViewRecord> records,
        String granularity) {
        DateTimeFormatter formatter = switch (granularity) {
            case "week" -> DateTimeFormatter.ofPattern("yyyy-'W'ww");
            case "month" -> DateTimeFormatter.ofPattern("yyyy-MM");
            default -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
        };

        return records.stream()
            .collect(Collectors.groupingBy(
                record -> {
                    Instant timestamp = record.getMetadata().getCreationTimestamp();
                    LocalDate date = timestamp.atZone(ZoneId.systemDefault()).toLocalDate();
                    return formatter.format(date);
                },
                LinkedHashMap::new,
                Collectors.groupingBy(
                    record -> record.getSpec().getCategoryName() != null ? record.getSpec()
                        .getCategoryName() : "未知分类",
                    Collectors.counting()
                )
            ));
    }

    private List<ViewStatisticsResponse.TimePoint> buildTimePoints(
        Map<String, Map<String, Long>> aggregatedData,
        Map<String, String> nameMap) {
        List<ViewStatisticsResponse.TimePoint> timePoints = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : aggregatedData.entrySet()) {
            ViewStatisticsResponse.TimePoint point = new ViewStatisticsResponse.TimePoint();
            point.setTime(entry.getKey());
            long totalCount = entry.getValue().values().stream().mapToLong(Long::longValue).sum();
            point.setTotalCount(totalCount);
            List<ViewStatisticsResponse.CategoryDetail> details = new ArrayList<>();
            for (Map.Entry<String, Long> categoryEntry : entry.getValue().entrySet()) {
                ViewStatisticsResponse.CategoryDetail detail =
                    new ViewStatisticsResponse.CategoryDetail();
                detail.setCategoryName(categoryEntry.getKey());
                detail.setDisplayName(
                    nameMap.getOrDefault(categoryEntry.getKey(), categoryEntry.getKey()));
                detail.setCount(categoryEntry.getValue());
                details.add(detail);
            }
            point.setDetails(details);
            timePoints.add(point);
        }
        return timePoints;
    }

    private ViewStatisticsResponse.EChartsData buildEchartsData(
        List<ViewStatisticsResponse.TimePoint> timePoints) {
        if (timePoints.isEmpty()) {
            ViewStatisticsResponse.EChartsData empty = new ViewStatisticsResponse.EChartsData();
            empty.setXAxis(new ArrayList<>());
            empty.setSeries(new ArrayList<>());
            return empty;
        }

        Set<String> allCategories = new LinkedHashSet<>();
        for (ViewStatisticsResponse.TimePoint point : timePoints) {
            for (ViewStatisticsResponse.CategoryDetail detail : point.getDetails()) {
                allCategories.add(detail.getCategoryName());
            }
        }

        List<String> xAxis = timePoints.stream()
            .map(ViewStatisticsResponse.TimePoint::getTime)
            .collect(Collectors.toList());

        List<ViewStatisticsResponse.EChartsSeries> series = new ArrayList<>();
        for (String categoryName : allCategories) {
            ViewStatisticsResponse.EChartsSeries serie = new ViewStatisticsResponse.EChartsSeries();
            serie.setName(categoryName);
            serie.setType("line");
            String displayName = timePoints.stream()
                .flatMap(point -> point.getDetails().stream())
                .filter(d -> d.getCategoryName().equals(categoryName))
                .map(ViewStatisticsResponse.CategoryDetail::getDisplayName)
                .findFirst()
                .orElse(categoryName);
            serie.setDisplayName(displayName);
            List<Long> data = new ArrayList<>();
            for (ViewStatisticsResponse.TimePoint point : timePoints) {
                long count = point.getDetails().stream()
                    .filter(d -> d.getCategoryName().equals(categoryName))
                    .mapToLong(ViewStatisticsResponse.CategoryDetail::getCount)
                    .findFirst()
                    .orElse(0L);
                data.add(count);
            }
            serie.setData(data);
            serie.setSmooth(true);
            series.add(serie);
        }

        ViewStatisticsResponse.EChartsData echartsData = new ViewStatisticsResponse.EChartsData();
        echartsData.setXAxis(xAxis);
        echartsData.setSeries(series);
        return echartsData;
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
}