package top.puresky.hitokotohub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.utils.ExtensionIndexRegistrar;

/**
 * 一言库插件入口
 *
 * @author Cyon
 * @since 1.0.0
 */
@Component
@Slf4j
public class HitokotoHubPlugin extends BasePlugin {

    private final ExtensionIndexRegistrar indexRegistrar;
    private final ReactiveExtensionClient client;

    public HitokotoHubPlugin(PluginContext pluginContext,
                             ExtensionIndexRegistrar indexRegistrar,
                             ReactiveExtensionClient client) {
        super(pluginContext);
        this.indexRegistrar = indexRegistrar;
        this.client = client;
    }

    @Override
    public void start() {
        // 注册自定义模型索引
        indexRegistrar.registerAll();

        // 启动时兜底迁移孤儿句子（分类为空或已失效的句子归入"未分类"）
        migrateOrphanSentences();

        log.info("一言数据中心插件启动成功！");
    }

    /**
     * 启动时兜底迁移孤儿句子：分类为空、或分类已不存在的句子统一归入「未分类」。
     *
     * <p>「未分类」Category 实体的创建由 {@link UncategorizedCategoryInitializer} 负责
     * （本类不再重复创建）；句子日常的 categoryName 归一化由 {@link SentenceReconciler} 处理，
     * 本方法仅覆盖历史遗留脏数据。
     */
    private void migrateOrphanSentences() {
        String name = UncategorizedConstants.METADATA_NAME;

        // 迁移已有句子：分类为空或不存在的归入"未分类"
        var options = ListOptions.builder()
            .fieldQuery(Queries.isNull("metadata.deletionTimestamp"))
            .build();

        client.listAll(Sentence.class, options, Sort.unsorted())
            .concatMap(sentence -> fixCategoryIfNeeded(sentence, name))
            .count()
            .subscribe(
                fixed -> {
                    if (fixed > 0) {
                        log.info("已迁移 {} 条句子到「未分类」", fixed);
                    }
                },
                e -> log.error("迁移孤儿句子到「未分类」失败", e));

        // 注：不再更新 Category.Status.sentenceCount 缓存。
        // 分类计数已改为通过 CategoryCountService 实时查询（listAll + 内存分组），
        // 从源头消除缓存一致性问题。详见 SentenceReconciler 重构方案。
    }

    /**
     * 判断单条句子是否需要归入「未分类」：分类为空、或分类已不存在的句子返回更新后的
     * {@link Sentence}，否则返回 empty，以便上层 {@code concatMap(...).count()} 统计迁移数量。
     */
    private Mono<Sentence> fixCategoryIfNeeded(Sentence sentence, String name) {
        String categoryName = sentence.getSpec().getCategoryName();
        if (categoryName == null || categoryName.isBlank()) {
            return updateCategory(sentence, name);
        }
        if (name.equals(categoryName)) {
            return Mono.empty();
        }
        return client.fetch(Category.class, categoryName)
            .hasElement()
            .flatMap(exists -> exists ? Mono.empty() : updateCategory(sentence, name));
    }

    private Mono<Sentence> updateCategory(Sentence sentence, String name) {
        sentence.getSpec().setCategoryName(name);
        return client.update(sentence);
    }

    @Override
    public void stop() {
        // 插件停用时取消注册自定义模型
        indexRegistrar.unregisterAll();
        log.info("一言数据中心插件已停止！");
    }
}
