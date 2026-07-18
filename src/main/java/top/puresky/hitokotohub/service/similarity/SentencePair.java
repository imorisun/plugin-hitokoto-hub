package top.puresky.hitokotohub.service.similarity;

/**
 * 句子相似对纯数据 record，解耦算法层与 {@code SimilarityCheckLog.SimilarityPair} DTO。
 *
 * <p>与 {@code SimilarityCheckLog.SimilarityPair} 通过 {@link SimilarityMappers} 互转。
 *
 * @param sentence1Name     句子1名称
 * @param sentence1Content  句子1内容
 * @param sentence1Category 句子1分类
 * @param sentence1Author   句子1作者
 * @param sentence1Source   句子1来源
 * @param sentence2Name     句子2名称
 * @param sentence2Content  句子2内容
 * @param sentence2Category 句子2分类
 * @param sentence2Author   句子2作者
 * @param sentence2Source   句子2来源
 * @param similarity        相似度分数 [0, 1]
 */
public record SentencePair(
    String sentence1Name,
    String sentence1Content,
    String sentence1Category,
    String sentence1Author,
    String sentence1Source,
    String sentence2Name,
    String sentence2Content,
    String sentence2Category,
    String sentence2Author,
    String sentence2Source,
    double similarity
) {}
