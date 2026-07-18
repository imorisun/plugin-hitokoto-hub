package top.puresky.hitokotohub.service.similarity;

import top.puresky.hitokotohub.extension.Sentence;

/**
 * 句子纯数据投影，解耦算法层与 Halo Extension。
 *
 * <p>算法层（{@link TextSimilarityCalculator}、{@link SimilarityPairFinder} 等）
 * 仅依赖此 record 而非 {@link Sentence}，便于纯单测且零 Spring 依赖。
 *
 * @param name         metadata.name
 * @param content      句子内容（原始值，算法层使用时按需 trim）
 * @param categoryName 分类名
 * @param author       作者
 * @param source       来源
 * @param published    是否已发布
 * @param likeCount    点赞数
 * @param viewCount    浏览数
 */
public record SentenceProfile(
    String name,
    String content,
    String categoryName,
    String author,
    String source,
    boolean published,
    long likeCount,
    long viewCount
) {

    /** 从 {@link Sentence} 提取纯数据投影。 */
    public static SentenceProfile from(Sentence sentence) {
        return new SentenceProfile(
            sentence.getMetadata().getName(),
            sentence.getSpec().getContent(),
            sentence.getSpec().getCategoryName(),
            sentence.getSpec().getAuthor(),
            sentence.getSpec().getSource(),
            sentence.getStatus() != null && sentence.getStatus().isPublished(),
            sentence.getStatus() != null ? sentence.getStatus().getLikeCount() : 0,
            sentence.getStatus() != null ? sentence.getStatus().getViewCount() : 0
        );
    }
}
