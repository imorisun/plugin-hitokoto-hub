package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import run.halo.app.extension.router.selector.FieldSelector;
import top.puresky.hitokotohub.extension.Category;

@Component
@RequiredArgsConstructor
public class CategoryPublicEndpoint implements CustomEndpoint {

    private static final String TAG = "CategoryPublicV1alpha1";
    private static final String GROUP_VERSION = "public.api.hitokotohub.puresky.top/v1alpha1";

    private final ReactiveExtensionClient client;

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route().GET("category/list", this::listCategories,
            builder -> builder.operationId("listCategories").summary("获取所有分类").tag(TAG)
                .response(responseBuilder().implementationArray(CategoryItem.class))).build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    private @NonNull Mono<ServerResponse> listCategories(ServerRequest request) {
        var listOptions = new ListOptions();
        listOptions.setFieldSelector(
            FieldSelector.of(Queries.isNull("metadata.deletionTimestamp")));

        return client.listAll(Category.class, listOptions, Sort.unsorted()).collectList()
            .map(categories -> categories.stream().map(category -> {
                CategoryItem item = new CategoryItem();
                item.setName(category.getMetadata().getName());
                item.setDisplayName(category.getSpec().getName());
                item.setDescription(category.getSpec().getDescription());
                item.setSentenceCount(
                    category.getStatus() != null ? category.getStatus().getSentenceCount() : 0);
                return item;
            }).toList()).flatMap(items -> ServerResponse.ok().bodyValue(items));
    }

    @Data
    @Schema(name = "CategoryItem")
    public static class CategoryItem {
        @Schema(description = "分类标识")
        private String name;
        @Schema(description = "分类显示名称")
        private String displayName;
        @Schema(description = "分类描述")
        private String description;
        @Schema(description = "句子数量")
        private long sentenceCount;
    }
}
