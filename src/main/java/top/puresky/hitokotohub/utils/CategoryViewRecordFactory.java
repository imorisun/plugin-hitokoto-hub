package top.puresky.hitokotohub.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import run.halo.app.extension.Metadata;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.CategoryViewRecord.EventType;
import top.puresky.hitokotohub.extension.Sentence;

/**
 * {@link CategoryViewRecord} 工厂，统一 VIEW/LIKE 记录的创建逻辑。
 *
 * <p>提取自 {@code SentencePublicEndpoint} 中两处重复的 CategoryViewRecord 构建代码
 * （{@code incrementAndRecordViews} 的 VIEW 记录与 {@code toggleLike} 的 LIKE 记录）。
 *
 * <p>统一约定：{@code metadata.generateName = "cvr-"}。
 */
public final class CategoryViewRecordFactory {

    private static final String GENERATE_NAME = "cvr-";

    private CategoryViewRecordFactory() {}

    /**
     * 创建浏览事件记录（仅含 categoryName + sentenceName，sentenceName 可空）。
     *
     * @param categoryName 分类名称
     * @param sentenceName 句子 metadata.name，可空
     * @return VIEW 事件记录
     */
    public static @NonNull CategoryViewRecord forView(@NonNull String categoryName,
                                                       @Nullable String sentenceName) {
        CategoryViewRecord record = new CategoryViewRecord();
        record.setMetadata(newMetadata());
        CategoryViewRecord.Spec spec = new CategoryViewRecord.Spec();
        spec.setCategoryName(categoryName);
        spec.setEventType(EventType.VIEW);
        spec.setSentenceName(sentenceName);
        record.setSpec(spec);
        return record;
    }

    /**
     * 创建点赞事件记录（含 categoryName + sentenceName + ip）。
     *
     * @param categoryName 分类名称
     * @param sentenceName 句子 metadata.name
     * @param ip           客户端 IP
     * @return LIKE 事件记录
     */
    public static @NonNull CategoryViewRecord forLike(@NonNull String categoryName,
                                                       @NonNull String sentenceName,
                                                       @NonNull String ip) {
        CategoryViewRecord record = new CategoryViewRecord();
        record.setMetadata(newMetadata());
        CategoryViewRecord.Spec spec = new CategoryViewRecord.Spec();
        spec.setCategoryName(categoryName);
        spec.setEventType(EventType.LIKE);
        spec.setSentenceName(sentenceName);
        spec.setIp(ip);
        record.setSpec(spec);
        return record;
    }

    /**
     * 通用工厂：由 Sentence + EventType + 可选 ip 构造记录。
     *
     * <p>categoryName 取自 sentence.spec.categoryName；sentenceName 取自 sentence.metadata.name
     * （存在时）；ip 仅在 LIKE 事件时设置。
     *
     * @param sentence  句子对象
     * @param eventType 事件类型
     * @param ip        客户端 IP，仅 LIKE 事件使用，可为 null
     * @return 浏览/点赞记录
     */
    public static @NonNull CategoryViewRecord create(@NonNull Sentence sentence,
                                                     @NonNull EventType eventType,
                                                     @Nullable String ip) {
        String categoryName = sentence.getSpec().getCategoryName();
        String sentenceName = (sentence.getMetadata() != null
            && sentence.getMetadata().getName() != null)
            ? sentence.getMetadata().getName() : null;

        if (eventType == EventType.LIKE) {
            return forLike(categoryName, sentenceName, ip != null ? ip : "unknown");
        }
        return forView(categoryName, sentenceName);
    }

    private static @NonNull Metadata newMetadata() {
        Metadata metadata = new Metadata();
        metadata.setGenerateName(GENERATE_NAME);
        return metadata;
    }
}
