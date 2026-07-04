package top.puresky.hitokotohub.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
    group = "hitokotohub.puresky.top",
    version = "v1alpha1",
    kind = "AiGenerateLog",
    plural = "aigeneratelogs",
    singular = "aigeneratelog"
)
public class AiGenerateLog extends AbstractExtension {

    public enum Status {
        RUNNING,
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    public static class Spec {

        @Schema(description = "使用的模型名称")
        private String modelName;

        @Schema(description = "生成主题")
        private String topic;

        @Schema(description = "请求生成数量")
        private int requestCount;

        @Schema(description = "成功数量")
        private int successCount;

        @Schema(description = "失败数量")
        private int failedCount;

        @Schema(description = "目标分类")
        private String categoryName;

        @Schema(description = "是否自动发布")
        private boolean autoPublish;

        @Schema(description = "状态")
        private Status status;

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "耗时（毫秒）")
        private long durationMs;
    }
}
