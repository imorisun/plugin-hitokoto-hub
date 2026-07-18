package top.puresky.hitokotohub.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.puresky.hitokotohub.service.similarity.SentenceProfile;
import top.puresky.hitokotohub.service.similarity.SentenceScorer;

/**
 * 相似句子分组结果（非 Extension，仅用于 API 响应）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarityGroup {

    @Schema(description = "分组标识（使用最优句子名称）")
    private String groupId;

    @Schema(description = "组内最优句子")
    private SentenceInfo bestSentence;

    @Schema(description = "最优句子评分")
    private double bestSentenceScore;

    @Schema(description = "组内其他相似句子")
    private List<SentenceInfo> similarSentences;

    @Schema(description = "相似句子数量")
    private int similarCount;

    @Schema(description = "组内最高相似度")
    private double maxSimilarity;

    @Schema(description = "组内平均相似度")
    private double avgSimilarity;

    // ==================== 评分算法 ====================

    /**
     * 对句子进行综合质量评分，用于选出组内最优句子。
     *
     * <p>评分规则：
     * - 已发布：+40
     * - 点赞数 * 2（社区认可）
     * - 浏览量 / 10（受欢迎程度）
     * - 内容长度 15~80 字：+15（"一言"理想长度），>80 字：+8
     * - 有作者（非匿名）：+10
     * - 有来源（非未知）：+5
     *
     * @deprecated 评分逻辑已迁至 {@link SentenceScorer#score(SentenceProfile)}，
     *             此方法仅保留向后兼容，内部委托 SentenceScorer 实现。
     *             新代码应直接使用 {@code SentenceScorer.score(SentenceProfile.from(sentence))}。
     */
    @Deprecated
    public static double scoreSentence(Sentence sentence) {
        return SentenceScorer.score(SentenceProfile.from(sentence));
    }

    // ==================== 内嵌类 ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentenceInfo {

        @Schema(description = "句子名称")
        private String name;

        @Schema(description = "句子内容")
        private String content;

        @Schema(description = "分类名称")
        private String category;

        @Schema(description = "作者")
        private String author;

        @Schema(description = "来源")
        private String source;

        @Schema(description = "是否发布")
        private boolean published;

        @Schema(description = "点赞数")
        private long likeCount;

        @Schema(description = "浏览量")
        private long viewCount;

        @Schema(description = "质量评分")
        private double score;

        @Schema(description = "与最优句子的相似度（仅 similarSentences 中有值）")
        private double similarity;
    }
}
