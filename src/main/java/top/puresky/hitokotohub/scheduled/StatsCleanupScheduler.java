package top.puresky.hitokotohub.scheduled;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.endpoint.SentencePublicEndpoint;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.service.AiGenerateService;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
public class StatsCleanupScheduler {

    private final ReactiveExtensionClient client;
    private final SettingConfig settingConfig;
    private final SentencePublicEndpoint sentencePublicEndpoint;
    private final ObjectProvider<AiGenerateService> aiServiceProvider;

    // 每 6 小时清理一次过期的点赞缓存
    @Scheduled(fixedRate = 21600000)
    public void cleanExpiredLikeCache() {
        sentencePublicEndpoint.cleanExpiredLikeCache();
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
                            .flatMap(client::delete)
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

    // 每天凌晨2点执行一次AI自动生成句子
    @Scheduled(cron = "0 0 2 * * *")
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
}