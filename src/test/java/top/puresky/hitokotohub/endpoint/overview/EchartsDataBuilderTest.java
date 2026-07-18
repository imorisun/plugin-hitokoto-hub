package top.puresky.hitokotohub.endpoint.overview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.puresky.hitokotohub.endpoint.OverviewConsoleEndpoint.ViewStatisticsResponse;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.CategoryViewRecord.EventType;
import run.halo.app.extension.Metadata;

/**
 * {@link EchartsDataBuilder} 单元测试。
 */
@DisplayName("EchartsDataBuilder")
class EchartsDataBuilderTest {

    private final EchartsDataBuilder builder = new EchartsDataBuilder();

    /** 构造一条 CategoryViewRecord，指定时间戳和分类名。 */
    private static CategoryViewRecord record(String categoryName, Instant timestamp) {
        CategoryViewRecord r = new CategoryViewRecord();
        Metadata meta = new Metadata();
        meta.setCreationTimestamp(timestamp);
        r.setMetadata(meta);
        CategoryViewRecord.Spec spec = new CategoryViewRecord.Spec();
        spec.setCategoryName(categoryName);
        spec.setEventType(EventType.VIEW);
        r.setSpec(spec);
        return r;
    }

    private static Instant at(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 10, 0)
            .atZone(ZoneId.systemDefault()).toInstant();
    }

    @Test
    @DisplayName("day 粒度：同日记录聚合到同一时间标签")
    void shouldAggregateByDay() {
        List<CategoryViewRecord> records = List.of(
            record("cat-a", at(2026, 7, 1)),
            record("cat-a", at(2026, 7, 1)),
            record("cat-b", at(2026, 7, 1)),
            record("cat-a", at(2026, 7, 2))
        );

        Map<String, Map<String, Long>> result =
            builder.aggregateByGranularity(records, "day");

        assertThat(result).hasSize(2);
        assertThat(result.get("2026-07-01")).containsEntry("cat-a", 2L).containsEntry("cat-b", 1L);
        assertThat(result.get("2026-07-02")).containsEntry("cat-a", 1L);
    }

    @Test
    @DisplayName("month 粒度：同月记录聚合到同一时间标签")
    void shouldAggregateByMonth() {
        List<CategoryViewRecord> records = List.of(
            record("cat-a", at(2026, 7, 1)),
            record("cat-b", at(2026, 7, 15)),
            record("cat-a", at(2026, 8, 1))
        );

        Map<String, Map<String, Long>> result =
            builder.aggregateByGranularity(records, "month");

        assertThat(result).hasSize(2);
        assertThat(result.get("2026-07")).containsEntry("cat-a", 1L).containsEntry("cat-b", 1L);
        assertThat(result.get("2026-08")).containsEntry("cat-a", 1L);
    }

    @Test
    @DisplayName("categoryName 为 null 时归入「未知分类」")
    void shouldHandleNullCategoryName() {
        CategoryViewRecord r = new CategoryViewRecord();
        Metadata meta = new Metadata();
        meta.setCreationTimestamp(at(2026, 7, 1));
        r.setMetadata(meta);
        CategoryViewRecord.Spec spec = new CategoryViewRecord.Spec();
        spec.setEventType(EventType.VIEW);
        r.setSpec(spec);

        Map<String, Map<String, Long>> result =
            builder.aggregateByGranularity(List.of(r), "day");

        assertThat(result.get("2026-07-01")).containsKey("未知分类");
    }

    @Test
    @DisplayName("buildTimePoints：正确构建时间点和分类详情")
    void shouldBuildTimePoints() {
        Map<String, Map<String, Long>> aggregated = new java.util.LinkedHashMap<>();
        aggregated.put("2026-07-01", Map.of("cat-a", 3L, "cat-b", 2L));
        aggregated.put("2026-07-02", Map.of("cat-a", 1L));

        Map<String, String> nameMap = Map.of("cat-a", "分类A", "cat-b", "分类B");

        List<ViewStatisticsResponse.TimePoint> points =
            builder.buildTimePoints(aggregated, nameMap);

        assertThat(points).hasSize(2);
        ViewStatisticsResponse.TimePoint first = points.get(0);
        assertThat(first.getTime()).isEqualTo("2026-07-01");
        assertThat(first.getTotalCount()).isEqualTo(5L);
        assertThat(first.getDetails()).hasSize(2);

        ViewStatisticsResponse.TimePoint second = points.get(1);
        assertThat(second.getTotalCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("buildTimePoints：nameMap 中无映射时用 categoryName 作 displayName")
    void shouldUseCategoryNameAsDisplayNameWhenMapMissing() {
        Map<String, Map<String, Long>> aggregated = new java.util.LinkedHashMap<>();
        aggregated.put("2026-07-01", Map.of("cat-x", 1L));

        List<ViewStatisticsResponse.TimePoint> points =
            builder.buildTimePoints(aggregated, Map.of());

        assertThat(points.get(0).getDetails().get(0).getDisplayName()).isEqualTo("cat-x");
    }

    @Test
    @DisplayName("buildEchartsData：空列表返回空 xAxis 和 series")
    void shouldReturnEmptyEchartsDataForEmptyInput() {
        ViewStatisticsResponse.EChartsData data = builder.buildEchartsData(List.of());

        assertThat(data.getXAxis()).isEmpty();
        assertThat(data.getSeries()).isEmpty();
    }

    @Test
    @DisplayName("buildEchartsData：正确构建 xAxis 和 series")
    void shouldBuildEchartsData() {
        ViewStatisticsResponse.TimePoint point1 = new ViewStatisticsResponse.TimePoint();
        point1.setTime("2026-07-01");
        point1.setTotalCount(3L);
        point1.setDetails(List.of(
            detail("cat-a", "分类A", 2L),
            detail("cat-b", "分类B", 1L)
        ));

        ViewStatisticsResponse.TimePoint point2 = new ViewStatisticsResponse.TimePoint();
        point2.setTime("2026-07-02");
        point2.setTotalCount(1L);
        point2.setDetails(List.of(
            detail("cat-a", "分类A", 1L)
        ));

        ViewStatisticsResponse.EChartsData data =
            builder.buildEchartsData(List.of(point1, point2));

        assertThat(data.getXAxis()).containsExactly("2026-07-01", "2026-07-02");
        assertThat(data.getSeries()).hasSize(2);

        ViewStatisticsResponse.EChartsSeries catASeries = data.getSeries().stream()
            .filter(s -> s.getName().equals("cat-a")).findFirst().orElseThrow();
        assertThat(catASeries.getDisplayName()).isEqualTo("分类A");
        assertThat(catASeries.getType()).isEqualTo("line");
        assertThat(catASeries.isSmooth()).isTrue();
        assertThat(catASeries.getData()).containsExactly(2L, 1L);

        ViewStatisticsResponse.EChartsSeries catBSeries = data.getSeries().stream()
            .filter(s -> s.getName().equals("cat-b")).findFirst().orElseThrow();
        assertThat(catBSeries.getData()).containsExactly(1L, 0L);
    }

    private static ViewStatisticsResponse.CategoryDetail detail(String name, String display, Long count) {
        ViewStatisticsResponse.CategoryDetail d = new ViewStatisticsResponse.CategoryDetail();
        d.setCategoryName(name);
        d.setDisplayName(display);
        d.setCount(count);
        return d;
    }
}
