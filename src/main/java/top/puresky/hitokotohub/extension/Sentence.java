package top.puresky.hitokotohub.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = "hitokotohub.puresky.top",
    version = "v1alpha1",
    kind = "Sentence",
    plural = "sentences",
    singular = "sentence")
public class Sentence extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    private Status status = new Status();

    @Data
    @Schema(name = "SentenceSpec")
    public static class Spec {
        @NotBlank
        @Size(max = 100)
        @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String categoryName;

        @NotBlank
        @Size(max = 500)
        @Schema(description = "句子内容", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
        private String content;

        @Size(max = 50)
        @Schema(description = "作者", maxLength = 50, defaultValue = "匿名")
        private String author = "匿名";

        @Size(max = 100)
        @Schema(description = "来源", maxLength = 100, defaultValue = "未知")
        private String source = "未知";

        @Schema(description = "创建用户")
        private String createdBy;
    }

    @Data
    @Schema(name = "SentenceStatus")
    public static class Status {

        @Schema(description = "是否发布", defaultValue = "false")
        private boolean isPublished = false;

        @Schema(description = "点赞数", defaultValue = "0")
        private long likeCount = 0;

        @Schema(description = "浏览量", defaultValue = "0")
        private long viewCount = 0;

    }
}