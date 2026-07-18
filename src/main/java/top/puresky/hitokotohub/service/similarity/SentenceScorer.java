package top.puresky.hitokotohub.service.similarity;

/**
 * 句子质量评分器（纯函数，零 Spring/Extension 依赖）。
 *
 * <p>评分规则（提取自 {@code SimilarityGroup.scoreSentence}）：
 * <ul>
 *   <li>已发布：+40</li>
 *   <li>点赞数 × 2（社区认可）</li>
 *   <li>浏览量 / 10（受欢迎程度）</li>
 *   <li>内容长度 15~80 字：+15（"一言"理想长度），>80 字：+8</li>
 *   <li>有作者（非匿名）：+10</li>
 *   <li>有来源（非未知）：+5</li>
 * </ul>
 */
public final class SentenceScorer {

    private SentenceScorer() {}

    /**
     * 对句子进行综合质量评分。
     *
     * @param profile 句子纯数据投影
     * @return 质量评分（四舍五入到两位小数）
     */
    public static double score(SentenceProfile profile) {
        double score = 0;

        // 发布状态
        if (profile.published()) {
            score += 40;
        }

        // 点赞数
        if (profile.likeCount() > 0) {
            score += profile.likeCount() * 2;
        }

        // 浏览量
        if (profile.viewCount() > 0) {
            score += (double) profile.viewCount() / 10;
        }

        // 内容长度
        if (profile.content() != null) {
            int len = profile.content().trim().length();
            if (len >= 15 && len <= 80) {
                score += 15;
            } else if (len > 80) {
                score += 8;
            }
        }

        // 有作者
        if (profile.author() != null && !profile.author().isBlank()
            && !"匿名".equals(profile.author())) {
            score += 10;
        }

        // 有来源
        if (profile.source() != null && !profile.source().isBlank()
            && !"未知".equals(profile.source())) {
            score += 5;
        }

        return Math.round(score * 100.0) / 100.0;
    }
}
