package top.puresky.hitokotohub;

import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import top.puresky.hitokotohub.extension.AiGenerateLog;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;

/**
 * 一言库插件入口
 *
 * @author Cyon
 * @since 1.0.0
 */
@Component
public class HitokotoHubPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public HitokotoHubPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        // 注册自定义模型
        schemeManager.register(Sentence.class, sentenceIndexSpecs -> {
            sentenceIndexSpecs.add(
                IndexSpecs.<Sentence, String>single("spec.categoryName", String.class)
                    .indexFunc(sentence -> sentence.getSpec().getCategoryName())
                    .nullable(false)
                    .build()
            );
            sentenceIndexSpecs.add(
                IndexSpecs.<Sentence, Boolean>single("status.isPublished", Boolean.class)
                    .indexFunc(sentence -> sentence.getStatus().isPublished())
                    .nullable(false)
                    .build()
            );
            sentenceIndexSpecs.add(
                IndexSpecs.<Sentence, String>single("spec.content", String.class)
                    .indexFunc(sentence -> sentence.getSpec().getContent())
                    .nullable(false)
                    .build()
            );
            sentenceIndexSpecs.add(
                IndexSpecs.<Sentence, Long>single("status.viewCount", Long.class)
                    .indexFunc(sentence -> sentence.getStatus().getViewCount())
                    .nullable(false)
                    .build()
            );
            sentenceIndexSpecs.add(
                IndexSpecs.<Sentence, Long>single("status.likeCount", Long.class)
                    .indexFunc(sentence -> sentence.getStatus().getLikeCount())
                    .nullable(false)
                    .build()
            );
        });

        schemeManager.register(Category.class);

        schemeManager.register(CategoryViewRecord.class,
            categoryViewRecordIndexSpecs -> {
                categoryViewRecordIndexSpecs.add(
                    IndexSpecs.<CategoryViewRecord, String>single("spec.eventType", String.class)
                        .indexFunc(
                            categoryViewRecord -> categoryViewRecord.getSpec().getEventType()
                                .name())
                        .nullable(false)
                        .build()
                );
                categoryViewRecordIndexSpecs.add(
                    IndexSpecs.<CategoryViewRecord, String>single("spec.categoryName", String.class)
                        .indexFunc(
                            categoryViewRecord -> categoryViewRecord.getSpec().getCategoryName())
                        .nullable(false)
                        .build()
                );
                categoryViewRecordIndexSpecs.add(
                    IndexSpecs.<CategoryViewRecord, String>single("spec.sentenceName", String.class)
                        .indexFunc(
                            categoryViewRecord -> categoryViewRecord.getSpec().getSentenceName())
                        .nullable(true)
                        .build()
                );
                categoryViewRecordIndexSpecs.add(
                    IndexSpecs.<CategoryViewRecord, String>single("spec.ip", String.class)
                        .indexFunc(
                            categoryViewRecord -> categoryViewRecord.getSpec().getIp())
                        .nullable(true)
                        .build()
                );
            });

        schemeManager.register(AiGenerateLog.class,
            aiGenerateLogIndexSpecs -> aiGenerateLogIndexSpecs.add(
                IndexSpecs.<AiGenerateLog, String>single("spec.status", String.class)
                    .indexFunc(
                        aiGenerateLog -> aiGenerateLog.getSpec().getStatus().name())
                    .nullable(false)
                    .build()
            ));

        System.out.println("✅ 一言数据中心插件启动成功！");
    }

    @Override
    public void stop() {
        // 插件停用时取消注册自定义模型
        Scheme sentenceScheme = schemeManager.get(Sentence.class);
        Scheme categoryScheme = schemeManager.get(Category.class);
        Scheme categoryViewRecordScheme = schemeManager.get(CategoryViewRecord.class);
        Scheme aiGenerateLogScheme = schemeManager.get(AiGenerateLog.class);

        schemeManager.unregister(sentenceScheme);
        schemeManager.unregister(categoryScheme);
        schemeManager.unregister(categoryViewRecordScheme);
        schemeManager.unregister(aiGenerateLogScheme);
        System.out.println("✅ 一言数据中心插件已停止！");
    }
}