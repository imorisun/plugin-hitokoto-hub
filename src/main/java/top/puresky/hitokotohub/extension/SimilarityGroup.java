package top.puresky.hitokotohub.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 评分规则：
     * - 已发布：+40
     * - 点赞数 * 2（社区认可）
     * - 浏览量 / 10（受欢迎程度）
     * - 内容长度 15~80 字：+15（"一言"理想长度），>80 字：+8
     * - 有作者（非匿名）：+10
     * - 有来源（非未知）：+5
     */
    public static double scoreSentence(Sentence sentence) {
        double score = 0;

        // 发布状态
        if (sentence.getStatus() != null && sentence.getStatus().isPublished()) {
            score += 40;
        }

        // 点赞数
        if (sentence.getStatus() != null && sentence.getStatus().getLikeCount() > 0) {
            score += sentence.getStatus().getLikeCount() * 2;
        }

        // 浏览量
        if (sentence.getStatus() != null && sentence.getStatus().getViewCount() > 0) {
            score += (double) sentence.getStatus().getViewCount() / 10;
        }

        // 内容长度
        if (sentence.getSpec() != null && sentence.getSpec().getContent() != null) {
            int len = sentence.getSpec().getContent().trim().length();
            if (len >= 15 && len <= 80) {
                score += 15;
            } else if (len > 80) {
                score += 8;
            }
        }

        // 有作者
        if (sentence.getSpec() != null && sentence.getSpec().getAuthor() != null
            && !sentence.getSpec().getAuthor().isBlank()
            && !"匿名".equals(sentence.getSpec().getAuthor())) {
            score += 10;
        }

        // 有来源
        if (sentence.getSpec() != null && sentence.getSpec().getSource() != null
            && !sentence.getSpec().getSource().isBlank()
            && !"未知".equals(sentence.getSpec().getSource())) {
            score += 5;
        }

        return Math.round(score * 100.0) / 100.0;
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
