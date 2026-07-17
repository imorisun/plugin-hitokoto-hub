package top.puresky.hitokotohub.config.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import top.puresky.hitokotohub.config.SettingConfig;

@Component
@RequiredArgsConstructor
public class SettingConfigImpl implements SettingConfig {
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Mono<BasicConfig> getBasicConfig() {
        return settingFetcher.fetch(BasicConfig.GROUP,
            BasicConfig.class);
    }
    @Override
    public Mono<AiConfig> getAiConfig() {
        return settingFetcher.fetch(AiConfig.GROUP,
            AiConfig.class);
    }
    @Override
    public Mono<SubmissionConfig> getSubmissionConfig() {
        return settingFetcher.fetch(SubmissionConfig.GROUP,
            SubmissionConfig.class);
    }
    @Override
    public Mono<SimilarityConfig> getSimilarityConfig() {
        return settingFetcher.fetch(SimilarityConfig.GROUP,
            SimilarityConfig.class);
    }
    @Override
    public Mono<TemplateConfig> getTemplateConfig() {
        return settingFetcher.fetch(TemplateConfig.GROUP,
            TemplateConfig.class);
    }
}
