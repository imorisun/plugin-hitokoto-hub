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
@GVK(group = "hitokotohub.puresky.top",
    version = "v1alpha1",
    kind = "SimilarityCheckLog",
    plural = "similaritychecklogs",
    singular = "similaritychecklog")
public class SimilarityCheckLog extends AbstractExtension {

    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED
    }

    public enum TriggerType {
        MANUAL,
        SCHEDULED
    }

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "SimilarityCheckLogSpec")
    public static class Spec {

        @Schema(description = "触发类型：MANUAL / SCHEDULED")
        private TriggerType triggerType;

        @Schema(description = "触发者")
        private String triggeredBy;

        @Schema(description = "使用的算法：COSINE / JACCARD")
        private String algorithm;

        @Schema(description = "相似度阈值")
        private double threshold;

        @Schema(description = "检查的句子总数")
        private int totalSentences;

        @Schema(description = "比较的句子对总数")
        private long totalPairs;

        @Schema(description = "超过阈值的相似对数量")
        private int similarPairCount;

        @Schema(description = "耗时（毫秒）")
        private long durationMs;

        @Schema(description = "状态：RUNNING / SUCCESS / FAILED")
        private Status status;

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "相似句子对列表（JSON 字符串）")
        private String similarPairs;
    }

    @Data
    @Schema(name = "SimilarityPair")
    public static class SimilarityPair {

        @Schema(description = "句子1名称")
        private String sentence1Name;

        @Schema(description = "句子1内容")
        private String sentence1Content;

        @Schema(description = "句子2名称")
        private String sentence2Name;

        @Schema(description = "句子2内容")
        private String sentence2Content;

        @Schema(description = "相似度分数（0~1）")
        private double similarity;

        @Schema(description = "句子1分类")
        private String sentence1Category;

        @Schema(description = "句子2分类")
        private String sentence2Category;

        @Schema(description = "句子1作者")
        private String sentence1Author;

        @Schema(description = "句子1来源")
        private String sentence1Source;

        @Schema(description = "句子2作者")
        private String sentence2Author;

        @Schema(description = "句子2来源")
        private String sentence2Source;
    }
}
