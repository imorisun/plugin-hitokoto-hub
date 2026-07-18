package top.puresky.hitokotohub.service.similarity;

import java.util.List;
import top.puresky.hitokotohub.extension.SimilarityCheckLog.SimilarityPair;

/**
 * 算法层 record 与 Halo Extension DTO 之间的转换器（边界层）。
 *
 * <p>使算法层（{@link SentencePair}、{@link SentenceProfile}）与持久化层
 * （{@link SimilarityPair}）解耦，避免算法类直接依赖 Extension。
 */
public final class SimilarityMappers {

    private SimilarityMappers() {}

    /** Extension DTO → 算法层 record。 */
    public static SentencePair from(SimilarityPair ext) {
        return new SentencePair(
            ext.getSentence1Name(), ext.getSentence1Content(),
            ext.getSentence1Category(), ext.getSentence1Author(), ext.getSentence1Source(),
            ext.getSentence2Name(), ext.getSentence2Content(),
            ext.getSentence2Category(), ext.getSentence2Author(), ext.getSentence2Source(),
            ext.getSimilarity()
        );
    }

    /** 算法层 record → Extension DTO。 */
    public static SimilarityPair to(SentencePair pair) {
        SimilarityPair ext = new SimilarityPair();
        ext.setSentence1Name(pair.sentence1Name());
        ext.setSentence1Content(pair.sentence1Content());
        ext.setSentence1Category(pair.sentence1Category());
        ext.setSentence1Author(pair.sentence1Author());
        ext.setSentence1Source(pair.sentence1Source());
        ext.setSentence2Name(pair.sentence2Name());
        ext.setSentence2Content(pair.sentence2Content());
        ext.setSentence2Category(pair.sentence2Category());
        ext.setSentence2Author(pair.sentence2Author());
        ext.setSentence2Source(pair.sentence2Source());
        ext.setSimilarity(pair.similarity());
        return ext;
    }

    /** 批量：Extension DTO 列表 → 算法层 record 列表。 */
    public static List<SentencePair> fromList(List<SimilarityPair> exts) {
        return exts.stream().map(SimilarityMappers::from).toList();
    }

    /** 批量：算法层 record 列表 → Extension DTO 列表。 */
    public static List<SimilarityPair> toList(List<SentencePair> pairs) {
        return pairs.stream().map(SimilarityMappers::to).toList();
    }
}
