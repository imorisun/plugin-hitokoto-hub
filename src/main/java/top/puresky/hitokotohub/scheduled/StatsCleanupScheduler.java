package top.puresky.hitokotohub.scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
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
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
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
    /** 统计数据清理默认 Cron（每天凌晨 3 点） */
    private static final String DEFAULT_STATS_CLEANUP_CRON = "0 0 3 * * *";
    /** AI 日志清理默认 Cron（每天凌晨 3 点 30 分） */
    private static final String DEFAULT_AI_LOG_CLEANUP_CRON = "0 30 3 * * *";
    /** 提交记录清理默认 Cron（每天凌晨 4 点） */
    private static final String DEFAULT_SUBMISSION_CLEANUP_CRON = "0 0 4 * * *";
    /** 按条数清理时单次最多删除的记录数，超量部分留待下次任务继续，避免单次内存峰值过高 */
    private static final int MAX_CLEANUP_BATCH = 50_000;

    private final ReactiveExtensionClient client;
    private final SettingConfig settingConfig;
    private final SentencePublicEndpoint sentencePublicEndpoint;
    private final ObjectProvider<AiGenerateService> aiServiceProvider;
    private final ObjectProvider<SimilarityCheckService> similarityCheckServiceProvider;

    private ScheduledTaskRegistrar taskRegistrar;
    private volatile ScheduledTask aiGenerateTask;
    private volatile ScheduledTask similarityCheckTask;
    private volatile ScheduledTask statsCleanupTask;
    private volatile ScheduledTask aiLogCleanupTask;
    private volatile ScheduledTask submissionCleanupTask;

    /**
     * Cron 表达式缓存。由 refresh 方法异步刷新, schedule 方法同步读取,
     * 避免 synchronized 方法内阻塞 I/O。
     */
    private volatile String cachedAiCron;
    private volatile String cachedStatsCleanupCron;
    private volatile String cachedAiLogCleanupCron;
    private volatile String cachedSubmissionCleanupCron;

    @Override
    public void configureTasks(@NonNull ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        // 异步刷新配置并注册任务(完成后回调对应的 schedule 方法)
        refreshAiConfigAndSchedule();
        scheduleSimilarityCheckTask();
        refreshCleanupConfigAndSchedule();
    }

    /**
     * 监听插件配置变更，重新注册定时任务
     */
    @EventListener
    public void onPluginConfigUpdated(PluginConfigUpdatedEvent event) {
        if (event.getNewSettingValues().containsKey(SettingConfig.AiConfig.GROUP)) {
            refreshAiConfigAndSchedule();
            refreshCleanupConfigAndSchedule();
        }
        if (event.getNewSettingValues().containsKey(SettingConfig.SimilarityConfig.GROUP)) {
            scheduleSimilarityCheckTask();
        }
        if (event.getNewSettingValues().containsKey(SettingConfig.BasicConfig.GROUP)
            || event.getNewSettingValues().containsKey(SettingConfig.SubmissionConfig.GROUP)) {
            refreshCleanupConfigAndSchedule();
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
        return resolveCachedCron(cachedAiCron, DEFAULT_AI_CRON);
    }

    /** 从配置对象提取并校验 Cron 表达式,无效时返回默认值。 */
    private String extractValidAiCron(SettingConfig.AiConfig aiConfig) {
        return aiConfig != null
            ? resolveValidCron(aiConfig.getAiCron(), DEFAULT_AI_CRON) : DEFAULT_AI_CRON;
    }

    // ===================== 数据清理定时任务（Cron 可从设置中配置） =====================

    /**
     * 异步读取统计/AI日志/提交记录三组配置并刷新 Cron 缓存,
     * 完成后统一重新注册三个清理任务。
     */
    private void refreshCleanupConfigAndSchedule() {
        if (taskRegistrar == null) {
            return;
        }
        Mono.zip(
                settingConfig.getBasicConfig().defaultIfEmpty(new SettingConfig.BasicConfig()),
                settingConfig.getAiConfig().defaultIfEmpty(new SettingConfig.AiConfig()),
                settingConfig.getSubmissionConfig()
                    .defaultIfEmpty(new SettingConfig.SubmissionConfig()))
            .doOnNext(tuple -> {
                cachedStatsCleanupCron = resolveValidCron(tuple.getT1().getStatsCleanupCron(),
                    DEFAULT_STATS_CLEANUP_CRON);
                cachedAiLogCleanupCron = resolveValidCron(tuple.getT2().getAiLogCleanupCron(),
                    DEFAULT_AI_LOG_CLEANUP_CRON);
                cachedSubmissionCleanupCron = resolveValidCron(
                    tuple.getT3().getSubmissionCleanupCron(), DEFAULT_SUBMISSION_CLEANUP_CRON);
            })
            .doOnError(e -> log.warn("刷新清理任务 Cron 配置失败,使用默认值", e))
            .doFinally(signal -> scheduleCleanupTasks())
            .subscribe();
    }

    private synchronized void scheduleCleanupTasks() {
        if (taskRegistrar == null) {
            return;
        }
        statsCleanupTask = reschedule(statsCleanupTask,
            () -> new CronTask(this::cleanOldCategoryViewRecords, resolveStatsCleanupCron()));
        log.info("统计数据清理定时任务已注册，Cron 表达式: {}", resolveStatsCleanupCron());

        aiLogCleanupTask = reschedule(aiLogCleanupTask,
            () -> new CronTask(this::cleanOldAiGenerateLogs, resolveAiLogCleanupCron()));
        log.info("AI 日志清理定时任务已注册，Cron 表达式: {}", resolveAiLogCleanupCron());

        submissionCleanupTask = reschedule(submissionCleanupTask,
            () -> new CronTask(this::cleanOldSentenceSubmissions,
                resolveSubmissionCleanupCron()));
        log.info("提交记录清理定时任务已注册，Cron 表达式: {}", resolveSubmissionCleanupCron());
    }

    /** 取消旧任务并注册新任务。 */
    private ScheduledTask reschedule(ScheduledTask existing, Supplier<CronTask> taskSupplier) {
        if (existing != null) {
            existing.cancel();
        }
        return taskRegistrar.scheduleCronTask(taskSupplier.get());
    }

    private String resolveStatsCleanupCron() {
        return resolveCachedCron(cachedStatsCleanupCron, DEFAULT_STATS_CLEANUP_CRON);
    }

    private String resolveAiLogCleanupCron() {
        return resolveCachedCron(cachedAiLogCleanupCron, DEFAULT_AI_LOG_CLEANUP_CRON);
    }

    private String resolveSubmissionCleanupCron() {
        return resolveCachedCron(cachedSubmissionCleanupCron, DEFAULT_SUBMISSION_CLEANUP_CRON);
    }

    /** 从缓存读取 Cron 表达式,无 I/O 阻塞;缓存为空时返回默认值。 */
    private String resolveCachedCron(String cached, String defaultCron) {
        return StringUtils.hasText(cached) ? cached : defaultCron;
    }

    /** 校验 Cron 表达式,空或无效时返回默认值。 */
    private String resolveValidCron(String cron, String defaultCron) {
        if (StringUtils.hasText(cron)) {
            try {
                new CronTrigger(cron);
                return cron;
            } catch (Exception e) {
                log.warn("Cron 表达式无效: {}，使用默认值: {}", cron, defaultCron);
            }
        }
        return defaultCron;
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

    // 清理过期的统计数据（Cron 表达式从设置中读取，默认每天凌晨 3 点）
    public void cleanOldCategoryViewRecords() {
        settingConfig.getBasicConfig()
            .flatMap(config -> {
                int maxKeep = config.getStatsMaxKeep() != null ? config.getStatsMaxKeep() : 1000;
                int retentionDays =
                    config.getStatsRetentionDays() != null ? config.getStatsRetentionDays() : 90;
                Instant cutoffTime = Instant.now().minus(Duration.ofDays(retentionDays));

                Mono<Long> byDays = client.listAll(CategoryViewRecord.class,
                        ListOptions.builder()
                            .fieldQuery(Queries.and(
                                Queries.lessThan("metadata.creationTimestamp", cutoffTime),
                                Queries.isNull("metadata.deletionTimestamp")))
                            .build(),
                        Sort.unsorted())
                    .flatMap(client::delete, 16)
                    .count()
                    .doOnNext(count -> {
                        if (count > 0) {
                            log.info("按天数清理了 {} 条统计数据", count);
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("按天数清理统计数据失败", e);
                        return Mono.just(0L);
                    });

                // 条数上限按事件类型分别执行：浏览量记录量远大于点赞记录，
                // 若共用同一配额，高频浏览会挤掉点赞记录，导致 hasLiked 状态丢失、
                // 点赞统计失真（同 IP 可重复点赞造成计数虚高）。
                Mono<Long> byCount = Mono.zip(
                        cleanupOldestRecords(CategoryViewRecord.class,
                            statsOptions(CategoryViewRecord.EventType.VIEW), maxKeep)
                            .onErrorResume(e -> {
                                log.warn("按条数清理浏览记录失败", e);
                                return Mono.just(0L);
                            }),
                        cleanupOldestRecords(CategoryViewRecord.class,
                            statsOptions(CategoryViewRecord.EventType.LIKE), maxKeep)
                            .onErrorResume(e -> {
                                log.warn("按条数清理点赞记录失败", e);
                                return Mono.just(0L);
                            }))
                    .map(tuple -> tuple.getT1() + tuple.getT2())
                    .doOnNext(count -> {
                        if (count > 0) {
                            log.info("按条数清理了 {} 条统计数据", count);
                        }
                    });

                // 两类策略相互独立：按天数清理失败不应连带跳过按条数清理
                return Mono.when(byDays, byCount);
            })
            .doOnError(e -> log.error("统计数据清理失败", e))
            .subscribe();
    }

    /** 构建按事件类型过滤（且未删除）的统计数据查询条件。 */
    private ListOptions statsOptions(CategoryViewRecord.EventType eventType) {
        return ListOptions.builder().fieldQuery(Queries.and(
            Queries.equal("spec.eventType", eventType.name()),
            Queries.isNull("metadata.deletionTimestamp")
        )).build();
    }

    // 清理过期的 AI 生成日志（Cron 表达式从设置中读取，默认每天凌晨 3 点 30 分）
    public void cleanOldAiGenerateLogs() {
        settingConfig.getAiConfig()
            .flatMap(config -> {
                int maxKeep = config.getAiLogMaxKeep() != null ? config.getAiLogMaxKeep() : 500;
                int retentionDays =
                    config.getAiLogRetentionDays() != null ? config.getAiLogRetentionDays() : 30;
                Instant cutoffTime = Instant.now().minus(Duration.ofDays(retentionDays));

                Mono<Long> byDays = client.listAll(AiGenerateLog.class,
                        ListOptions.builder()
                            .fieldQuery(Queries.and(
                                Queries.lessThan("metadata.creationTimestamp", cutoffTime),
                                Queries.isNull("metadata.deletionTimestamp")))
                            .build(),
                        Sort.unsorted())
                    .flatMap(client::delete, 16)
                    .count()
                    .doOnNext(count -> {
                        if (count > 0) {
                            log.info("按天数清理了 {} 条AI生成日志", count);
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("按天数清理AI生成日志失败", e);
                        return Mono.just(0L);
                    });

                Mono<Long> byCount = cleanupOldestRecords(AiGenerateLog.class,
                        ListOptions.builder()
                            .fieldQuery(Queries.isNull("metadata.deletionTimestamp"))
                            .build(),
                        maxKeep)
                    .doOnNext(count -> {
                        if (count > 0) {
                            log.info("按条数清理了 {} 条AI生成日志", count);
                        }
                    })
                    .onErrorResume(e -> {
                        log.warn("按条数清理AI生成日志失败", e);
                        return Mono.just(0L);
                    });

                return Mono.when(byDays, byCount);
            })
            .doOnError(e -> log.error("AI生成日志清理失败", e))
            .subscribe();
    }

    // 清理已处理的访客提交记录（Cron 表达式从设置中读取，默认每天凌晨 4 点；
    // 仅清理 APPROVED/REJECTED，保留 PENDING）
    public void cleanOldSentenceSubmissions() {
        settingConfig.getSubmissionConfig()
            .flatMap(config -> {
                int maxKeep =
                    config.getSubmissionMaxKeep() != null ? config.getSubmissionMaxKeep() : 1000;
                // 仅清理已处理记录（非 PENDING），保留待审核记录供管理员处理
                var options = ListOptions.builder().fieldQuery(Queries.and(
                    Queries.notEqual("spec.status",
                        SentenceSubmission.Status.PENDING.name()),
                    Queries.isNull("metadata.deletionTimestamp")
                )).build();
                return cleanupOldestRecords(SentenceSubmission.class, options, maxKeep)
                    .doOnNext(count -> {
                        if (count > 0) {
                            log.info("按条数清理了 {} 条已处理提交记录", count);
                        }
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
                    if (aiConfig.getAiSentenceCount() == null) {
                        log.warn("AI 生成数量未设置，跳过本次任务");
                        return Mono.empty();
                    }
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
        return resolveValidCron(cron, DEFAULT_SIMILARITY_CRON);
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

    // ===================== 按条数清理 =====================

    /**
     * 按条数保留策略清理：仅分页取出最旧的 {@code total - maxKeep} 条记录删除，
     * 避免此前 {@code listAll + collectList} 全量驻留内存的峰值问题。
     *
     * <p>单次最多删除 {@link #MAX_CLEANUP_BATCH} 条，超量部分留待下次任务继续。
     *
     * @param type    扩展模型类型
     * @param options 查询条件（复用按天数清理的过滤条件或全量）
     * @param maxKeep 最大保留条数
     * @return 实际删除的条数
     */
    private <E extends AbstractExtension> Mono<Long> cleanupOldestRecords(Class<E> type,
        ListOptions options, int maxKeep) {
        return client.countBy(type, options)
            .filter(total -> total > maxKeep)
            .flatMap(total -> {
                int pageSize = (int) Math.min(total - maxKeep, MAX_CLEANUP_BATCH);
                return client.listBy(type, options,
                        PageRequestImpl.of(1, pageSize,
                            Sort.by("metadata.creationTimestamp").ascending()))
                    .flatMapMany(result -> Flux.fromIterable(result.getItems()))
                    .flatMap(client::delete, 16)
                    .count();
            });
    }

}