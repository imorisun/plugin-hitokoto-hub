package top.puresky.hitokotohub.scheduled;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.plugin.PluginConfigUpdatedEvent;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.endpoint.SentencePublicEndpoint;
import top.puresky.hitokotohub.extension.AiGenerateLog;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.SentenceSubmission;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.service.AiGenerateService;
import top.puresky.hitokotohub.service.SimilarityCheckService;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class StatsCleanupScheduler implements SchedulingConfigurer {

    private static final String DEFAULT_AI_CRON = "0 0 2 * * *";
    private static final String DEFAULT_SIMILARITY_CRON = "0 0 2 * * *";

    private final ReactiveExtensionClient client;
    private final SettingConfig settingConfig;
    private final SentencePublicEndpoint sentencePublicEndpoint;
    private final ObjectProvider<AiGenerateService> aiServiceProvider;
    private final ObjectProvider<SimilarityCheckService> similarityCheckServiceProvider;

    private ScheduledTaskRegistrar taskRegistrar;
    private volatile ScheduledTask aiGenerateTask;
    private volatile ScheduledTask similarityCheckTask;

    /**
     * AI Cron 表达式缓存。由 {@link #refreshAiConfigAndSchedule()} 异步刷新,
     * {@link #scheduleAiGenerateTask()} 同步读取,避免 synchronized 方法内阻塞 I/O。
     */
    private volatile String cachedAiCron;

    @Override
    public void configureTasks(@NonNull ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        // 异步刷新 AI 配置并注册任务(完成后回调 scheduleAiGenerateTask)
        refreshAiConfigAndSchedule();
        scheduleSimilarityCheckTask();
    }

    /**
     * 监听插件配置变更，重新注册定时任务
     */
    @EventListener
    public void onPluginConfigUpdated(PluginConfigUpdatedEvent event) {
        if (event.getNewSettingValues().containsKey(SettingConfig.AiConfig.GROUP)) {
            refreshAiConfigAndSchedule();
        }
        if (event.getNewSettingValues().containsKey(SettingConfig.SimilarityConfig.GROUP)) {
            scheduleSimilarityCheckTask();
        }
    }

    /**
     * 异步读取 AI 配置并刷新缓存,完成后注册定时任务。
     *
     * <p>替代原 {@code scheduleAiGenerateTask()} 内直接 {@code .block()} 的阻塞模式,
     * 避免 synchronized 方法内持锁等待 I/O。
     */
    private void refreshAiConfigAndSchedule() {
        if (taskRegistrar == null) {
            return;
        }
        settingConfig.getAiConfig()
            .doOnNext(config -> cachedAiCron = extractValidAiCron(config))
            .doOnError(e -> log.warn("刷新 AI Cron 配置失败,使用默认值: {}", DEFAULT_AI_CRON, e))
            .doFinally(signal -> scheduleAiGenerateTask())
            .subscribe();
    }

    private synchronized void scheduleAiGenerateTask() {
        if (taskRegistrar == null) {
            return;
        }
        // 取消已存在的任务
        if (aiGenerateTask != null) {
            aiGenerateTask.cancel();
            aiGenerateTask = null;
        }
        String cron = resolveAiCron();
        aiGenerateTask = taskRegistrar.scheduleCronTask(new CronTask(this::generateAiSentences, cron));
        log.info("AI 生成句子定时任务已注册，Cron 表达式: {}", cron);
    }

    /**
     * 从缓存读取 AI Cron 表达式,无 I/O 阻塞。
     *
     * <p>缓存为空时返回默认值(启动初期或刷新失败的场景)。
     */
    private String resolveAiCron() {
        String cron = cachedAiCron;
        return StringUtils.hasText(cron) ? cron : DEFAULT_AI_CRON;
    }

    /** 从配置对象提取并校验 Cron 表达式,无效时返回默认值。 */
    private String extractValidAiCron(SettingConfig.AiConfig aiConfig) {
        if (aiConfig != null && StringUtils.hasText(aiConfig.getAiCron())) {
            try {
                new CronTrigger(aiConfig.getAiCron());
                return aiConfig.getAiCron();
            } catch (Exception e) {
                log.warn("AI Cron 表达式无效: {}，使用默认值: {}", aiConfig.getAiCron(), DEFAULT_AI_CRON);
            }
        }
        return DEFAULT_AI_CRON;
    }

    // 每 6 小时清理一次过期的点赞缓存
    @Scheduled(fixedRate = 21600000)
    public void cleanExpiredLikeCache() {
        sentencePublicEndpoint.cleanExpiredLikeCache();
    }

    // 每 5 分钟清理一次过期的浏览去重缓存（窗口短，需频繁清理）
    @Scheduled(fixedRate = 300000)
    public void cleanExpiredViewDedupCache() {
        sentencePublicEndpoint.cleanExpiredViewDedupCache();
    }

    // 每天凌晨 3 点清理一次过期的统计数据
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanOldCategoryViewRecords() {
        settingConfig.getBasicConfig()
            .flatMap(config -> {
                int maxKeep = config.getStatsMaxKeep() != null ? config.getStatsMaxKeep() : 1000;
                int retentionDays =
                    config.getStatsRetentionDays() != null ? config.getStatsRetentionDays() : 90;
                Instant cutoffTime = Instant.now().minus(Duration.ofDays(retentionDays));

                Mono<Long> byDays = client.listAll(CategoryViewRecord.class,
                        ListOptions.builder()
                            .fieldQuery(
                                Queries.lessThan("metadata.creationTimestamp",
                                    cutoffTime.toString()))
                            .build(),
                        Sort.unsorted())
                    .flatMap(client::delete)
                    .count()
                    .doOnNext(count -> {
                        if (count > 0) {
                            log.info("按天数清理了 {} 条统计数据", count);
                        }
                    });

                Mono<Long> byCount = client.listAll(CategoryViewRecord.class,
                        ListOptions.builder().build(),
                        Sort.by("metadata.creationTimestamp").ascending())
                    .collectList()
                    .flatMap(records -> {
                        if (records.size() <= maxKeep) {
                            return Mono.empty();
                        }
                        int deleteCount = records.size() - maxKeep;
                        return Flux.fromIterable(records.subList(0, deleteCount))
                            .flatMap(client::delete, 16)
                            .count()
                            .doOnNext(count -> {
                                if (count > 0) {
                                    log.info("按条数清理了 {} 条统计数据", count);
                                }
                            });
                    });

                return byDays.then(byCount);
            })
            .doOnError(e -> log.error("统计数据清理失败", e))
            .subscribe();
    }

    // 每天凌晨 3 点 30 分清理一次过期的 AI 生成日志
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanOldAiGenerateLogs() {
        settingConfig.getAiConfig()
            .flatMap(config -> {
                int maxKeep = config.getAiLogMaxKeep() != null ? config.getAiLogMaxKeep() : 500;
                int retentionDays =
                    config.getAiLogRetentionDays() != null ? config.getAiLogRetentionDays() : 30;
                Instant cutoffTime = Instant.now().minus(Duration.ofDays(retentionDays));

                Mono<Long> byDays = client.listAll(AiGenerateLog.class,
                        ListOptions.builder()
                            .fieldQuery(
                                Queries.lessThan("metadata.creationTimestamp",
                                    cutoffTime.toString()))
                            .build(),
                        Sort.unsorted())
                    .flatMap(client::delete)
                    .count()
                    .doOnNext(count -> {
                        if (count > 0) {
                            log.info("按天数清理了 {} 条AI生成日志", count);
                        }
                    });

                Mono<Long> byCount = client.listAll(AiGenerateLog.class,
                        ListOptions.builder().build(),
                        Sort.by("metadata.creationTimestamp").ascending())
                    .collectList()
                    .flatMap(logs -> {
                        if (logs.size() <= maxKeep) {
                            return Mono.empty();
                        }
                        int deleteCount = logs.size() - maxKeep;
                        return Flux.fromIterable(logs.subList(0, deleteCount))
                            .flatMap(client::delete, 16)
                            .count()
                            .doOnNext(count -> {
                                if (count > 0) {
                                    log.info("按条数清理了 {} 条AI生成日志", count);
                                }
                            });
                    });

                return byDays.then(byCount);
            })
            .doOnError(e -> log.error("AI生成日志清理失败", e))
            .subscribe();
    }

    // 每天凌晨 4 点清理一次已处理的访客提交记录（仅清理 APPROVED/REJECTED，保留 PENDING）
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanOldSentenceSubmissions() {
        settingConfig.getSubmissionConfig()
            .flatMap(config -> {
                int maxKeep =
                    config.getSubmissionMaxKeep() != null ? config.getSubmissionMaxKeep() : 1000;
                // 仅清理已处理记录（非 PENDING），保留待审核记录供管理员处理
                return client.listAll(SentenceSubmission.class,
                        ListOptions.builder().fieldQuery(Queries.and(
                            Queries.notEqual("spec.status",
                                SentenceSubmission.Status.PENDING.name()),
                            Queries.isNull("metadata.deletionTimestamp")
                        )).build(),
                        Sort.by("metadata.creationTimestamp").ascending())
                    .collectList()
                    .flatMap(submissions -> {
                        if (submissions.size() <= maxKeep) {
                            return Mono.<Long>empty();
                        }
                        int deleteCount = submissions.size() - maxKeep;
                        return Flux.fromIterable(submissions.subList(0, deleteCount))
                            .flatMap(client::delete, 16)
                            .count()
                            .doOnNext(count -> {
                                if (count > 0) {
                                    log.info("按条数清理了 {} 条已处理提交记录", count);
                                }
                            });
                    });
            })
            .doOnError(e -> log.error("访客提交记录清理失败", e))
            .subscribe();
    }

    // AI 自动生成句子（Cron 表达式从设置中读取）
    public void generateAiSentences() {
        AiGenerateService aiService = aiServiceProvider.getIfAvailable();
        if (aiService == null) {
            log.warn("AI Foundation 服务不可用（缺少依赖或未启用），跳过定时任务");
            return;
        }

        settingConfig.getAiConfig()
            .flatMap(aiConfig -> {
                if (Boolean.TRUE.equals(aiConfig.getEnableAiGenerate())) {
                    return aiService.sentencesGenerateAndSave(
                        aiConfig.getLanguageModelName(),
                        aiConfig.getAiSystemPrompt(),
                        aiConfig.getAiTopic(),
                        aiConfig.getAiSentenceCount(),
                        aiConfig.getAiSentenceCategory(),
                        aiConfig.getAiSentenceAutoPublish()
                    );
                } else {
                    log.info("AI 自动生成未开启，跳过本次任务");
                    return Mono.empty();
                }
            })
            .doOnError(e -> log.error("AI 生成句子定时任务执行失败", e))
            .subscribe();
    }

    // ===================== 相似度检查定时任务 =====================

    private synchronized void scheduleSimilarityCheckTask() {
        if (taskRegistrar == null) {
            return;
        }
        // 取消已存在的任务
        if (similarityCheckTask != null) {
            similarityCheckTask.cancel();
            similarityCheckTask = null;
        }

        settingConfig.getSimilarityConfig()
            .flatMap(config -> {
                if (Boolean.TRUE.equals(config.getEnableScheduledCheck())) {
                    String cron = resolveSimilarityCron(config.getSimilarityCron());
                    similarityCheckTask =
                        taskRegistrar.scheduleCronTask(new CronTask(this::runScheduledSimilarityCheck, cron));
                    log.info("相似度检查定时任务已注册，Cron 表达式: {}", cron);
                } else {
                    log.info("相似度检查定时任务未启用");
                }
                return Mono.empty();
            })
            .doOnError(e -> log.error("注册相似度检查定时任务失败", e))
            .subscribe();
    }

    private String resolveSimilarityCron(String cron) {
        if (StringUtils.hasText(cron)) {
            try {
                new CronTrigger(cron);
                return cron;
            } catch (Exception e) {
                log.warn("相似度检查 Cron 表达式无效: {}，使用默认值: {}", cron, DEFAULT_SIMILARITY_CRON);
            }
        }
        return DEFAULT_SIMILARITY_CRON;
    }

    public void runScheduledSimilarityCheck() {
        SimilarityCheckService service = similarityCheckServiceProvider.getIfAvailable();
        if (service == null) {
            log.warn("相似度检查服务不可用，跳过定时任务");
            return;
        }

        settingConfig.getSimilarityConfig()
            .flatMap(config -> {
                if (Boolean.TRUE.equals(config.getEnableScheduledCheck())) {
                    String algorithm = config.getSimilarityAlgorithm() != null
                        ? config.getSimilarityAlgorithm() : "COSINE";
                    double threshold = config.getSimilarityThreshold() != null
                        ? config.getSimilarityThreshold() : 0.8;
                    return service.performCheck(
                        SimilarityCheckLog.TriggerType.SCHEDULED,
                        "system",
                        algorithm,
                        threshold
                    );
                } else {
                    log.info("相似度检查未开启，跳过本次任务");
                    return Mono.empty();
                }
            })
            .doOnError(e -> log.error("相似度检查定时任务执行失败", e))
            .subscribe();
    }

}