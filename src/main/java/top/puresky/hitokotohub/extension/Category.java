package top.puresky.hitokotohub.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
    group = "hitokotohub.puresky.top",
    version = "v1alpha1",
    kind = "Category",
    plural = "categories",
    singular = "category"
)
public class Category extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "CategorySpec")
    public static class Spec {
        @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 50)
        private String name;

        @Schema(description = "分类描述", maxLength = 200)
        private String description;
    }
}