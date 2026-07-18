package top.puresky.hitokotohub.utils;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import top.puresky.hitokotohub.extension.AiGenerateLog;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SentenceSubmission;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;

/**
 * 集中管理插件所有 Extension 的索引注册与注销。
 *
 * <p>提取自 {@code HitokotoHubPlugin.start()/stop()} 中内联的 6 个 scheme 注册逻辑，
 * 使插件入口类仅保留生命周期编排，索引声明集中可读、可维护。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExtensionIndexRegistrar {

    private final SchemeManager schemeManager;

    /**
     * 注册全部自定义 Extension 及其索引。
     *
     * <p>包含：Sentence(5 索引)、Category、CategoryViewRecord(4 索引)、
     * AiGenerateLog(1 索引)、SentenceSubmission(3 索引)、SimilarityCheckLog(1 索引)。
     */
    public void registerAll() {
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

        log.info("一言数据中心插件索引注册完成");
    }

    /**
     * 注销全部自定义 Extension。
     */
    public void unregisterAll() {
        List.of(
                Sentence.class,
                Category.class,
                CategoryViewRecord.class,
                AiGenerateLog.class,
                SentenceSubmission.class,
                SimilarityCheckLog.class
            ).forEach(clazz -> {
                Scheme scheme = schemeManager.get(clazz);
                schemeManager.unregister(scheme);
            });

        log.info("一言数据中心插件索引注销完成");
    }
}
