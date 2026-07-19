package top.puresky.hitokotohub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
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
    private final ExtensionClient client;

    public HitokotoHubPlugin(PluginContext pluginContext,
                             ExtensionIndexRegistrar indexRegistrar,
                             ExtensionClient client) {
        super(pluginContext);
        this.indexRegistrar = indexRegistrar;
        this.client = client;
    }

    @Override
    public void start() {
        // 注册自定义模型索引
        indexRegistrar.registerAll();

        // 确保"未分类"分类存在
        ensureUncategorizedCategory();

        log.info("一言数据中心插件启动成功！");
    }

    private void ensureUncategorizedCategory() {
        String name = UncategorizedConstants.METADATA_NAME;

        // 创建"未分类"（如果不存在）
        client.fetch(Category.class, name).orElseGet(() -> {
            Category category = new Category();
            category.setMetadata(new Metadata());
            category.getMetadata().setName(name);
            Category.Spec spec = new Category.Spec();
            spec.setName(UncategorizedConstants.DISPLAY_NAME);
            spec.setDescription(UncategorizedConstants.DESCRIPTION);
            category.setSpec(spec);
            client.create(category);
            return null;
        });

        // 迁移已有句子：分类为空或不存在的归入"未分类"
        var options = ListOptions.builder()
            .fieldQuery(Queries.isNull("metadata.deletionTimestamp"))
            .build();
        var sentences = client.listAll(Sentence.class, options, Sort.unsorted());

        int fixed = 0;
        for (Sentence sentence : sentences) {
            String categoryName = sentence.getSpec().getCategoryName();
            boolean needsFix = false;

            if (categoryName == null || categoryName.isBlank()) {
                needsFix = true;
            } else if (!name.equals(categoryName)
                && client.fetch(Category.class, categoryName).isEmpty()) {
                needsFix = true;
            }

            if (needsFix) {
                sentence.getSpec().setCategoryName(name);
                client.update(sentence);
                fixed++;
            }
        }

        // 注：不再更新 Category.Status.sentenceCount 缓存。
        // 分类计数已改为通过 CategoryCountService 实时查询（listAll + 内存分组），
        // 从源头消除缓存一致性问题。详见 SentenceReconciler 重构方案。

        if (fixed > 0) {
            log.info("已迁移 {} 条句子到「未分类」", fixed);
        }
    }

    @Override
    public void stop() {
        // 插件停用时取消注册自定义模型
        indexRegistrar.unregisterAll();
        log.info("一言数据中心插件已停止！");
    }
}
