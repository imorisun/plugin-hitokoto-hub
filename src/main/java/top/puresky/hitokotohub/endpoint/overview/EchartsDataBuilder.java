package top.puresky.hitokotohub.endpoint.overview;

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
import org.springframework.stereotype.Component;
import top.puresky.hitokotohub.endpoint.OverviewConsoleEndpoint.ViewStatisticsResponse;
import top.puresky.hitokotohub.extension.CategoryViewRecord;

/**
 * ECharts 图表数据构建器，从 {@link OverviewConsoleEndpoint} 抽离的纯逻辑层。
 *
 * <p>负责将 {@link CategoryViewRecord} 列表按时间粒度聚合，并构建为
 * ECharts 折线图所需的 xAxis/series 数据结构。
 *
 * <p>依赖 {@link ViewStatisticsResponse} 嵌套 DTO（保留在 endpoint 中以稳定 OpenAPI 生成）。
 */
@Component
public class EchartsDataBuilder {

    /**
     * 按时间粒度聚合记录。
     *
     * @param records     浏览/点赞记录列表
     * @param granularity 粒度：day / week / month
     * @return 外层 key=时间标签，内层 key=分类名，value=记录数
     */
    public Map<String, Map<String, Long>> aggregateByGranularity(
        List<CategoryViewRecord> records, String granularity) {
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

    /**
     * 将聚合数据转换为时间点列表。
     *
     * @param aggregatedData {@link #aggregateByGranularity} 的输出
     * @param nameMap        分类 metadata.name → 显示名 映射
     * @return 时间点列表
     */
    public List<ViewStatisticsResponse.TimePoint> buildTimePoints(
        Map<String, Map<String, Long>> aggregatedData,
        Map<String, String> nameMap) {
        List<ViewStatisticsResponse.TimePoint> timePoints = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : aggregatedData.entrySet()) {
            ViewStatisticsResponse.TimePoint point = new ViewStatisticsResponse.TimePoint();
            point.setTime(entry.getKey());
            long totalCount = entry.getValue().values().stream().mapToLong(v -> v.longValue()).sum();
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

    /**
     * 构建完整的 ECharts 数据（xAxis + series）。
     *
     * @param timePoints {@link #buildTimePoints} 的输出
     * @return ECharts 格式数据，永不为 null
     */
    public ViewStatisticsResponse.EChartsData buildEchartsData(
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
            .map(p -> p.getTime())
            .collect(Collectors.toList());

        List<ViewStatisticsResponse.EChartsSeries> series = new ArrayList<>();
        for (String categoryName : allCategories) {
            ViewStatisticsResponse.EChartsSeries serie = new ViewStatisticsResponse.EChartsSeries();
            serie.setName(categoryName);
            serie.setType("line");
            String displayName = timePoints.stream()
                .flatMap(point -> point.getDetails().stream())
                .filter(d -> d.getCategoryName().equals(categoryName))
                .map(d -> d.getDisplayName())
                .findFirst()
                .orElse(categoryName);
            serie.setDisplayName(displayName);
            List<Long> data = new ArrayList<>();
            for (ViewStatisticsResponse.TimePoint point : timePoints) {
                long count = point.getDetails().stream()
                    .filter(d -> d.getCategoryName().equals(categoryName))
                    .mapToLong(d -> d.getCount())
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
}
