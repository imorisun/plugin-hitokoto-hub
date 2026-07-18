package top.puresky.hitokotohub.support;

import java.time.Instant;
import java.util.UUID;
import run.halo.app.extension.Metadata;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SentenceSubmission;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.extension.SimilarityCheckLog.SimilarityPair;
import top.puresky.hitokotohub.extension.SimilarityCheckLog.Status;
import top.puresky.hitokotohub.extension.SimilarityCheckLog.TriggerType;

/**
 * 测试夹具工厂，集中构造 extension 对象，避免每个测试类重复 boilerplate。
 *
 * <p>所有工厂方法都填充合理的默认值，调用方可通过参数覆盖关键字段。
 */
public final class TestFixtures {

    private TestFixtures() {}

    /** 构造一个 Sentence，默认已发布、content="测试句子"、匿名作者、未知来源。 */
    public static Sentence sentence(String name, String content, boolean published) {
        Sentence s = new Sentence();
        s.setMetadata(metadata(name));
        Sentence.Spec spec = new Sentence.Spec();
        spec.setCategoryName("default-cat");
        spec.setContent(content);
        spec.setAuthor("匿名");
        spec.setSource("未知");
        s.setSpec(spec);
        Sentence.Status status = new Sentence.Status();
        status.setPublished(published);
        s.setStatus(status);
        return s;
    }

    /** 构造带点赞/浏览量的 Sentence。 */
    public static Sentence sentence(String name, String content, boolean published,
                                     long likeCount, long viewCount) {
        Sentence s = sentence(name, content, published);
        s.getStatus().setLikeCount(likeCount);
        s.getStatus().setViewCount(viewCount);
        return s;
    }

    /** 构造带完整元信息的 Sentence。 */
    public static Sentence sentence(String name, String content, String categoryName,
                                     String author, String source, boolean published,
                                     long likeCount, long viewCount) {
        Sentence s = sentence(name, content, published, likeCount, viewCount);
        s.getSpec().setCategoryName(categoryName);
        s.getSpec().setAuthor(author);
        s.getSpec().setSource(source);
        return s;
    }

    /** 构造一个 Category。 */
    public static Category category(String name, String displayName) {
        Category c = new Category();
        c.setMetadata(metadata(name));
        Category.Spec spec = new Category.Spec();
        spec.setName(displayName);
        spec.setDescription("测试分类");
        c.setSpec(spec);
        c.setStatus(new Category.Status());
        return c;
    }

    /** 构造带句子数的 Category。 */
    public static Category category(String name, String displayName, long sentenceCount) {
        Category c = category(name, displayName);
        c.getStatus().setSentenceCount(sentenceCount);
        return c;
    }

    /** 构造一个 SUCCESS 状态的 SimilarityCheckLog。 */
    public static SimilarityCheckLog successLog(String name, String algorithm,
                                                double threshold, String pairsJson) {
        SimilarityCheckLog log = new SimilarityCheckLog();
        log.setMetadata(metadata(name));
        SimilarityCheckLog.Spec spec = new SimilarityCheckLog.Spec();
        spec.setTriggerType(TriggerType.MANUAL);
        spec.setTriggeredBy("tester");
        spec.setAlgorithm(algorithm);
        spec.setThreshold(threshold);
        spec.setStatus(Status.SUCCESS);
        spec.setSimilarPairs(pairsJson != null ? pairsJson : "[]");
        spec.setDurationMs(10L);
        log.setSpec(spec);
        return log;
    }

    /** 构造一个 SimilarityPair。 */
    public static SimilarityPair pair(String name1, String name2, double similarity) {
        SimilarityPair p = new SimilarityPair();
        p.setSentence1Name(name1);
        p.setSentence1Content("内容1");
        p.setSentence1Category("cat");
        p.setSentence1Author("匿名");
        p.setSentence1Source("未知");
        p.setSentence2Name(name2);
        p.setSentence2Content("内容2");
        p.setSentence2Category("cat");
        p.setSentence2Author("匿名");
        p.setSentence2Source("未知");
        p.setSimilarity(similarity);
        return p;
    }

    /** 构造一个 PENDING 状态的 SentenceSubmission。 */
    public static SentenceSubmission submission(String name, String content, String categoryName) {
        SentenceSubmission sub = new SentenceSubmission();
        sub.setMetadata(metadata(name));
        SentenceSubmission.Spec spec = new SentenceSubmission.Spec();
        spec.setContent(content);
        spec.setAuthor("匿名");
        spec.setSource("未知");
        spec.setCategoryName(categoryName);
        spec.setStatus(SentenceSubmission.Status.PENDING);
        sub.setSpec(spec);
        return sub;
    }

    /** 构造一个 CategoryViewRecord。 */
    public static CategoryViewRecord viewRecord(String name, String categoryName,
                                                 CategoryViewRecord.EventType type) {
        CategoryViewRecord r = new CategoryViewRecord();
        r.setMetadata(metadata(name));
        CategoryViewRecord.Spec spec = new CategoryViewRecord.Spec();
        spec.setCategoryName(categoryName);
        spec.setEventType(type);
        r.setSpec(spec);
        return r;
    }

    /** 构造一个带 name 的 Metadata，creationTimestamp 设为当前时间。 */
    public static Metadata metadata(String name) {
        Metadata m = new Metadata();
        m.setName(name != null ? name : "test-" + UUID.randomUUID());
        m.setCreationTimestamp(Instant.now());
        return m;
    }
}
