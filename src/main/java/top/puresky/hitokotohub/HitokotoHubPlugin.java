package top.puresky.hitokotohub;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import top.puresky.hitokotohub.extension.AiGenerateLog;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SentenceSubmission;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;

/**
 * 一言库插件入口
 *
 * @author Cyon
 * @since 1.0.0
 */
@Component
public class HitokotoHubPlugin extends BasePlugin {

    private final SchemeManager schemeManager;
    private final ExtensionClient client;

    public HitokotoHubPlugin(PluginContext pluginContext, SchemeManager schemeManager,
                             ExtensionClient client) {
        super(pluginContext);
        this.schemeManager = schemeManager;
        this.client = client;
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

        schemeManager.register(SentenceSubmission.class,
            sentenceSubmissionIndexSpecs -> {
                sentenceSubmissionIndexSpecs.add(
                    IndexSpecs.<SentenceSubmission, String>single("spec.status", String.class)
                        .indexFunc(
                            submission -> submission.getSpec().getStatus().name())
                        .nullable(false)
                        .build()
                );
                sentenceSubmissionIndexSpecs.add(
                    IndexSpecs.<SentenceSubmission, String>single("spec.categoryName",
                            String.class)
                        .indexFunc(
                            submission -> submission.getSpec().getCategoryName())
                        .nullable(false)
                        .build()
                );
                sentenceSubmissionIndexSpecs.add(
                    IndexSpecs.<SentenceSubmission, String>single("spec.submitterIp",
                            String.class)
                        .indexFunc(
                            submission -> submission.getSpec().getSubmitterIp())
                        .nullable(true)
                        .build()
                );
            });

        schemeManager.register(SimilarityCheckLog.class,
            similarityCheckLogIndexSpecs -> similarityCheckLogIndexSpecs.add(
                IndexSpecs.<SimilarityCheckLog, String>single("spec.status", String.class)
                    .indexFunc(
                        similarityCheckLog -> similarityCheckLog.getSpec().getStatus().name())
                    .nullable(false)
                    .build()
            ));

        // 确保"未分类"分类存在
        ensureUncategorizedCategory();

        System.out.println("✅ 一言数据中心插件启动成功！");
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
            category.setStatus(new Category.Status());
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

        // 更新"未分类"的句子计数
        client.fetch(Category.class, name).ifPresent(category -> {
            var countOptions = ListOptions.builder()
                .fieldQuery(Queries.and(
                    Queries.equal("spec.categoryName", name),
                    Queries.isNull("metadata.deletionTimestamp")
                ))
                .build();
            long count = client.countBy(Sentence.class, countOptions);
            category.getStatus().setSentenceCount(count);
            client.update(category);
        });

        if (fixed > 0) {
            System.out.println("✅ 已迁移 " + fixed + " 条句子到「未分类」");
        }
    }

    @Override
    public void stop() {
        // 插件停用时取消注册自定义模型
        Scheme sentenceScheme = schemeManager.get(Sentence.class);
        Scheme categoryScheme = schemeManager.get(Category.class);
        Scheme categoryViewRecordScheme = schemeManager.get(CategoryViewRecord.class);
        Scheme aiGenerateLogScheme = schemeManager.get(AiGenerateLog.class);
        Scheme sentenceSubmissionScheme = schemeManager.get(SentenceSubmission.class);
        Scheme similarityCheckLogScheme = schemeManager.get(SimilarityCheckLog.class);

        schemeManager.unregister(sentenceScheme);
        schemeManager.unregister(categoryScheme);
        schemeManager.unregister(categoryViewRecordScheme);
        schemeManager.unregister(aiGenerateLogScheme);
        schemeManager.unregister(sentenceSubmissionScheme);
        schemeManager.unregister(similarityCheckLogScheme);
        System.out.println("✅ 一言数据中心插件已停止！");
    }
}