package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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
import run.halo.app.extension.ListResult;
import run.halo.app.extension.MetadataOperator;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.extension.router.selector.FieldSelector;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.service.CategoryCountService;

/**
 * 控制台分类查询端点。
 *
 * <p>提供带实时句子数量的分类列表查询，替代直接读取已移除的
 * {@code Category.Status.sentenceCount} 缓存字段。
 *
 * <p>性能策略：单次分页查询 Category + 单次 {@link CategoryCountService#getAllCounts()}
 * （内部为 listAll + 内存分组，O(N)），合并后返回。避免对每个分类单独 countBy 的 N+1 问题。
 */
@Component
@RequiredArgsConstructor
public class CategoryConsoleEndpoint implements CustomEndpoint {

    private static final String TAG = "CategoryConsoleV1alpha1";
    private static final String GROUP_VERSION = "console.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;
    private final CategoryCountService categoryCountService;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("categories", this::listCategoriesWithCounts,
                builder -> builder.operationId("listCategoriesWithCounts")
                    .summary("获取分类列表（含实时句子数量）")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY)
                        .name("page").implementation(Integer.class).description("页码，从 1 开始"))
                    .parameter(parameterBuilder().in(ParameterIn.QUERY)
                        .name("size").implementation(Integer.class).description("每页数量"))
                    .response(responseBuilder().implementation(
                        ListResult.generateGenericClass(CategoryWithCount.class))))
            .build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    @NonNull Mono<ServerResponse> listCategoriesWithCounts(ServerRequest request) {
        // 使用 NumberUtils.toInt 兜底非数字/空值/负数，避免 NumberFormatException 导致 500
        int page = NumberUtils.toInt(request.queryParam("page").orElse("1"), 1);
        int size = NumberUtils.toInt(request.queryParam("size").orElse("20"), 20);

        // 排除已删除分类（deletionTimestamp 非空）
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(
            FieldSelector.of(Queries.isNull("metadata.deletionTimestamp")));

        var pageRequest = PageRequestImpl.of(Math.max(page, 1), Math.max(size, 1), Sort.unsorted());

        // 并行：分页分类 + 实时计数 map
        return Mono.zip(
                client.listBy(Category.class, listOptions, pageRequest),
                categoryCountService.getAllCounts()
            )
            .map(tuple -> {
                ListResult<Category> categoryList = tuple.getT1();
                var counts = tuple.getT2();
                var items = categoryList.getItems().stream()
                    .map(category -> toCategoryWithCount(category,
                        counts.getOrDefault(category.getMetadata().getName(), 0L)))
                    .toList();
                return new ListResult<>(categoryList.getPage(), categoryList.getSize(),
                    categoryList.getTotal(), items);
            })
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private CategoryWithCount toCategoryWithCount(Category category, long sentenceCount) {
        CategoryWithCount item = new CategoryWithCount();
        item.setMetadata(category.getMetadata());
        item.setSpec(category.getSpec());
        item.setSentenceCount(sentenceCount);
        return item;
    }

    /**
     * 带句子数量的分类视图。
     *
     * <p>包含完整 Category 字段 + {@code sentenceCount}（实时查询，非缓存）。
     *
     * <p>{@code metadata} 字段使用 {@link MetadataOperator} 类型（Halo 扩展 API 的
     * 实际返回类型），可接收 {@code category.getMetadata()} 直接赋值。
     * Jackson 序列化时按运行时实际类型（Metadata）输出 JSON。
     */
    @Data
    @Schema(name = "CategoryWithCount")
    public static class CategoryWithCount {
        @Schema(description = "元数据")
        private MetadataOperator metadata;
        @Schema(description = "分类规格")
        private Category.Spec spec;
        @Schema(description = "该分类下的句子数量（实时查询）")
        private long sentenceCount;
    }
}
