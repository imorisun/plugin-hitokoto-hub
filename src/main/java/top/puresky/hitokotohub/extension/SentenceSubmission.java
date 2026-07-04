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
    kind = "SentenceSubmission",
    plural = "sentencesubmissions",
    singular = "sentencesubmission"
)
public class SentenceSubmission extends AbstractExtension {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "SentenceSubmissionSpec")
    public static class Spec {

        @Schema(description = "句子内容", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
        private String content;

        @Schema(description = "作者", maxLength = 50, defaultValue = "匿名")
        private String author = "匿名";

        @Schema(description = "来源", maxLength = 100, defaultValue = "未知")
        private String source = "未知";

        @Schema(description = "访客选择的分类", requiredMode = Schema.RequiredMode.REQUIRED)
        private String categoryName;

        @Schema(description = "提交者昵称", maxLength = 50)
        private String submitterName;

        @Schema(description = "提交者 IP")
        private String submitterIp;

        @Schema(description = "审核状态", defaultValue = "PENDING")
        private Status status = Status.PENDING;

        @Schema(description = "审核人")
        private String reviewedBy;

        @Schema(description = "审核备注")
        private String reviewNote;

        @Schema(description = "审核时间")
        private String reviewedAt;

        @Schema(description = "审核通过后生成的句子名称")
        private String sentenceName;
    }
}
