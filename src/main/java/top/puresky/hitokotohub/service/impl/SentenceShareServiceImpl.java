package top.puresky.hitokotohub.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.SystemInfo;
import run.halo.app.infra.SystemInfoGetter;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.config.SettingConfig.ShareConfig;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.service.SentenceShareService;
import top.puresky.hitokotohub.service.dto.SharePayload;
import top.puresky.hitokotohub.service.share.ShareCardSvgBuilder;

@Component
@RequiredArgsConstructor
public class SentenceShareServiceImpl implements SentenceShareService {

    /** 未配置站点名称时的默认站点名称 */
    private static final String DEFAULT_SITE_NAME = "轻言";
    /** 未配置标语时的默认分享标语 */
    private static final String DEFAULT_TAGLINE = "轻拾人间辞藻，言说万千心绪";
    /** 未配置英文标识时的默认英文标识 */
    private static final String DEFAULT_WORDMARK = "LITEWORDS";

    private final ReactiveExtensionClient client;
    private final SettingConfig settingConfig;
    private final SystemInfoGetter systemInfoGetter;

    @Override
    public Mono<SharePayload> buildSharePayload(String sentenceName, boolean requirePublished) {
        return loadSentence(sentenceName, requirePublished)
            .flatMap(sentence -> loadCategoryDisplayName(sentence.getSpec().getCategoryName())
                .flatMap(displayName -> loadShareConfig()
                    .flatMap(config -> toPayload(sentence, displayName, config))));
    }

    @Override
    public Mono<String> buildShareCardSvg(String sentenceName, boolean requirePublished) {
        return loadSentence(sentenceName, requirePublished)
            .flatMap(sentence -> loadCategoryDisplayName(sentence.getSpec().getCategoryName())
                .flatMap(displayName -> loadShareConfig()
                    .flatMap(config -> loadSiteName(config)
                        .map(siteName -> ShareCardSvgBuilder.build(sentence, displayName,
                            siteName, loadTagline(config), loadWordmark(config))))));
    }

    private Mono<ShareConfig> loadShareConfig() {
        return settingConfig.getShareConfig().defaultIfEmpty(new ShareConfig());
    }

    /** 站点名称：优先插件配置，其次 Halo 站点标题，最后使用默认值 */
    private Mono<String> loadSiteName(ShareConfig config) {
        if (StringUtils.isNotBlank(config.getSiteName())) {
            return Mono.just(config.getSiteName());
        }
        return systemInfoGetter.get()
            .map(SystemInfo::getTitle)
            .filter(StringUtils::isNotBlank)
            .defaultIfEmpty(DEFAULT_SITE_NAME);
    }

    /** 分享标语：优先插件配置，未配置时使用默认值 */
    private String loadTagline(ShareConfig config) {
        return StringUtils.defaultIfBlank(config.getTagline(), DEFAULT_TAGLINE);
    }

    /** 英文标识：优先插件配置，未配置时使用默认值 */
    private String loadWordmark(ShareConfig config) {
        return StringUtils.defaultIfBlank(config.getWordmark(), DEFAULT_WORDMARK);
    }

    private Mono<Sentence> loadSentence(String sentenceName, boolean requirePublished) {
        if (StringUtils.isBlank(sentenceName)) {
            return Mono.empty();
        }
        Mono<Sentence> mono = client.fetch(Sentence.class, sentenceName);
        if (requirePublished) {
            mono = mono.filter(s -> s.getStatus() != null && s.getStatus().isPublished());
        }
        return mono;
    }

    private Mono<String> loadCategoryDisplayName(String categoryName) {
        if (StringUtils.isBlank(categoryName)) {
            return Mono.just("");
        }
        return client.fetch(Category.class, categoryName)
            .map(c -> StringUtils.defaultString(c.getSpec().getName()))
            .defaultIfEmpty(categoryName);
    }

    private Mono<SharePayload> toPayload(Sentence sentence, String categoryDisplayName,
        ShareConfig config) {
        return loadSiteName(config).map(siteName -> {
            var spec = sentence.getSpec();
            var status = sentence.getStatus();
            SharePayload payload = new SharePayload();
            payload.setName(sentence.getMetadata().getName());
            payload.setContent(spec.getContent());
            payload.setAuthor(StringUtils.defaultString(spec.getAuthor()));
            payload.setSource(StringUtils.defaultString(spec.getSource()));
            payload.setCategoryName(spec.getCategoryName());
            payload.setCategoryDisplayName(categoryDisplayName);
            payload.setLikeCount(status != null ? status.getLikeCount() : 0);
            payload.setViewCount(status != null ? status.getViewCount() : 0);
            payload.setSharePath("/hitokoto?sentence=" + sentence.getMetadata().getName());
            payload.setSiteName(siteName);
            payload.setCreatedAt(System.currentTimeMillis());
            return payload;
        });
    }
}
