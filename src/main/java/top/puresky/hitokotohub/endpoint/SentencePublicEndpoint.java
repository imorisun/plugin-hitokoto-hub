package top.puresky.hitokotohub.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.utils.CategoryViewRecordFactory;
import top.puresky.hitokotohub.utils.HttpUtils;
import top.puresky.hitokotohub.utils.IpCooldownCache;
import top.puresky.hitokotohub.utils.SimpleCooldownState;
import top.puresky.hitokotohub.utils.TimeFormatUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class SentencePublicEndpoint implements CustomEndpoint {

    private static final String TAG = "SentencePublicV1alpha1";
    private static final String GROUP_VERSION = "public.api.hitokotohub.puresky.top/v1alpha1";

    private final SettingConfig settingConfig;
    private final ReactiveExtensionClient client;
    private final IpCooldownCache<SimpleCooldownState> likeCache = new IpCooldownCache<>();

    @Override
    public @NonNull RouterFunction<ServerResponse> endpoint() {
        return route().GET("sentence/random", this::getRandomSentences,
            builder -> builder.operationId("getRandomSentences").summary("随机获取句子").tag(TAG)
                .parameter(parameterBuilder().in(ParameterIn.QUERY).name("categoryName")
                    .description("分类名称，不传则使用设置中的默认分类").implementation(String.class)
                    .required(false)).parameter(
                    parameterBuilder().in(ParameterIn.QUERY).name("limit")
                        .description("返回数量，默认使用设置值").implementation(Integer.class)
                        .required(false)).parameter(
                    parameterBuilder().in(ParameterIn.QUERY).name("encode").description(
                            "返回格式：json 返回 RandomSentenceResponse，text 返回纯文本（每行一句）")
                        .implementation(String.class).required(false)).response(
                    responseBuilder().description(
                        "encode=json（默认）时返回 RandomSentenceResponse，encode=text 时返回 "
                            + "text/plain"))).GET("sentence/like", this::toggleLike,
            builder -> builder.operationId("toggleLike").summary("点赞/取消点赞句子").tag(TAG)
                .parameter(
                    parameterBuilder().in(ParameterIn.QUERY).name("name").description("句子名称")
                        .implementation(String.class).required(true)).parameter(
                    parameterBuilder().in(ParameterIn.QUERY).name("action")
                        .description("操作类型，like 或 unlike").implementation(String.class)
                        .required(false))
                .response(responseBuilder().implementation(LikeResponse.class))).build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    Mono<ServerResponse> getRandomSentences(ServerRequest request) {
        return settingConfig.getBasicConfig().flatMap(config -> {
            String categoryNameParam = request.queryParam("categoryName")
                .filter(StringUtils::isNotBlank).orElse(null);

            int limit = request.queryParam("limit").filter(StringUtils::isNotBlank)
                .map(Integer::parseInt).orElse(config.getRandomLimit());
            int actualLimit = Math.min(limit, config.getMaxRandomLimit());
            String encode = request.queryParam("encode").filter(StringUtils::isNotBlank)
                .orElse(config.getEncode());

            List<String> defaultCategories = config.getDefaultCategory();

            // 请求参数优先，没有则用设置里的
            List<String> finalCategories = null;
            if (StringUtils.isNotBlank(categoryNameParam)) {
                finalCategories = List.of(categoryNameParam);
            } else if (!defaultCategories.isEmpty()) {
                finalCategories = defaultCategories;
            }

            ListOptions options = buildListOptions(finalCategories);
            Mono<String> displayNameMono = getDisplayName(finalCategories);

            Mono<List<Sentence>> sentencesMono =
                client.countBy(Sentence.class, options).filter(total -> total > 0)
                    .flatMap(total -> {
                        int totalInt = total.intValue();
                        int effectiveSize = Math.min(actualLimit, totalInt);
                        int totalPages = (int) Math.ceil((double) totalInt / effectiveSize);
                        int page = RandomUtils.insecure().randomInt(1, totalPages + 1);

                        var pageRequest = PageRequestImpl.of(page, effectiveSize, Sort.unsorted());

                        return client.listBy(Sentence.class, options, pageRequest)
                            .map(ListResult::getItems).flatMap(items -> {
                                if (items.size() >= effectiveSize || total <= effectiveSize) {
                                    return Mono.just(items);
                                }

                                int remaining = effectiveSize - items.size();
                                var wrapRequest = PageRequestImpl.of(1, remaining, Sort.unsorted());

                                return client.listBy(Sentence.class, options, wrapRequest)
                                    .map(ListResult::getItems).map(wrapItems -> {
                                        List<Sentence> combined = new ArrayList<>(items);
                                        combined.addAll(wrapItems);
                                        return combined;
                                    });
                            }).map(items -> {
                                List<Sentence> randomItems = new ArrayList<>(items);
                                Collections.shuffle(randomItems,
                                    java.util.concurrent.ThreadLocalRandom.current());
                                return randomItems;
                            });
                    }).flatMap(sentences -> Boolean.TRUE.equals(config.getEnableViewCount())
                        ? incrementAndRecordViews(sentences) : Mono.just(sentences))
                    .switchIfEmpty(Mono.just(Collections.emptyList()));

            if ("text".equalsIgnoreCase(encode)) {
                return sentencesMono.map(
                        sentences -> sentences.stream().map(s -> s.getSpec().getContent())
                            .collect(Collectors.joining("\n")))
                    .flatMap(text -> ServerResponse.ok().bodyValue(text));
            }

            return sentencesMono.zipWith(displayNameMono).map(tuple -> {
                List<Sentence> sentences = tuple.getT1();
                String displayName = tuple.getT2();

                List<SentenceItem> items = sentences.stream().map(this::toSentenceItem).toList();

                RandomSentenceResponse response = new RandomSentenceResponse();
                response.setCategoryName(displayName);
                response.setMaxRandomLimit(config.getMaxRandomLimit());
                response.setReturned(items.size());
                response.setSentences(items);
                return response;
            }).flatMap(response -> ServerResponse.ok().bodyValue(response));
        });
    }


    // 多分类查询
    private ListOptions buildListOptions(List<String> categoryNames) {
        if (categoryNames != null && !categoryNames.isEmpty()) {
            return ListOptions.builder().fieldQuery(
                Queries.and(Queries.in("spec.categoryName", categoryNames),
                    Queries.equal("status.isPublished", true),
                    Queries.isNull("metadata.deletionTimestamp"))).build();
        }
        return ListOptions.builder().fieldQuery(
            Queries.and(Queries.equal("status.isPublished", true),
                Queries.isNull("metadata.deletionTimestamp"))).build();
    }

    // 多分类显示名
    private Mono<String> getDisplayName(List<String> categoryNames) {
        if (categoryNames != null && !categoryNames.isEmpty()) {
            return Flux.fromIterable(categoryNames)
                .flatMap(name -> client.fetch(Category.class, name)
                    .map(c -> c.getSpec().getName()).defaultIfEmpty(name))
                .collectList().map(names -> String.join("、", names));
        }
        return Mono.just("全部");
    }


    private @NonNull Mono<List<Sentence>> incrementAndRecordViews(List<Sentence> sentences) {
        return Flux.fromIterable(sentences)
            .concatMap(sentence -> {
                if (sentence.getStatus() == null) {
                    sentence.setStatus(new Sentence.Status());
                }
                sentence.getStatus().setViewCount(sentence.getStatus().getViewCount() + 1);

                // 记录分类浏览（同时记录句子名称以支持按句子维度统计）
                CategoryViewRecord record = CategoryViewRecordFactory.create(sentence,
                    CategoryViewRecord.EventType.VIEW, null);

                return client.update(sentence)
                    .then(client.create(record))
                    .thenReturn(sentence);
            }, 1)
            .then(Mono.just(sentences));
    }

    @NonNull Mono<ServerResponse> toggleLike(@NonNull ServerRequest request) {
        String name = request.queryParam("name").orElse("");
        String action = request.queryParam("action").orElse("like");
        String ip = HttpUtils.getClientIp(request.exchange().getRequest());
        String likeKey = ip + ":like:" + name;
        String unlikeKey = ip + ":unlike:" + name;
        return settingConfig.getBasicConfig().flatMap(config -> {
            boolean isUnlike = "unlike".equals(action);
            String checkKey = isUnlike ? unlikeKey : likeKey;
            SimpleCooldownState state = likeCache.get(checkKey);
            long now = System.currentTimeMillis();
            long likeCooldown = Duration.ofHours(config.getLikeCooldown()).toMillis();
            if (state != null && state.isCoolingDown(likeCooldown, now)) {
                long remainingSeconds = state.remainingMillis(likeCooldown, now) / 1000;
                return client.get(Sentence.class, name).map(
                        sentence -> buildLikeResponse(sentence, false,
                            "请在 " + TimeFormatUtils.formatRemainingTime(remainingSeconds) + " 后再" + (isUnlike
                                ? "取消点赞" : "点赞"), "rate_limited"))
                    .defaultIfEmpty(buildErrorResponse())
                    .flatMap(response -> ServerResponse.ok().bodyValue(response));
            }
            String oppositeKey = isUnlike ? likeKey : unlikeKey;
            return client.get(Sentence.class, name).flatMap(sentence -> {
                    if (sentence.getStatus() == null) {
                        sentence.setStatus(new Sentence.Status());
                    }
                    long currentLikes = sentence.getStatus().getLikeCount();
                    if (isUnlike) {
                        sentence.getStatus().setLikeCount(Math.max(0, currentLikes - 1));
                    } else {
                        sentence.getStatus().setLikeCount(currentLikes + 1);
                    }

                    return client.update(sentence)
                        .flatMap(updated -> {
                            likeCache.put(checkKey, new SimpleCooldownState(System.currentTimeMillis()));
                            likeCache.remove(oppositeKey);

                            if (isUnlike) {
                                // 取消点赞：删除该 IP 对该句子对应的点赞记录
                                return client.listAll(CategoryViewRecord.class,
                                        ListOptions.builder().fieldQuery(Queries.and(
                                            Queries.equal("spec.sentenceName", name),
                                            Queries.equal("spec.ip", ip),
                                            Queries.equal("spec.eventType",
                                                CategoryViewRecord.EventType.LIKE.name())
                                        )).build(),
                                        Sort.by("metadata.creationTimestamp").descending())
                                    .next()
                                    .flatMap(client::delete)
                                    .doOnNext(deleted -> log.info("删除点赞记录: {}",
                                        deleted.getMetadata().getName()))
                                    .onErrorResume(e -> {
                                        log.warn("删除点赞记录失败", e);
                                        return Mono.empty();
                                    })
                                    .thenReturn(buildLikeResponse(updated, true,
                                        "取消点赞成功", "ok"));
                            }

                            // 点赞：创建点赞记录
                            CategoryViewRecord record = CategoryViewRecordFactory.forLike(
                                sentence.getSpec().getCategoryName(), name, ip);
                            return client.create(record)
                                .thenReturn(buildLikeResponse(updated, true,
                                    "点赞成功", "ok"));
                        });
                })
                .defaultIfEmpty(buildErrorResponse())
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
        });
    }

    private @NonNull LikeResponse buildLikeResponse(Sentence sentence, boolean success, String message,
        String code) {
        LikeResponse response = new LikeResponse();
        response.setSuccess(success);
        response.setMessage(message);
        response.setCode(code);
        response.setSentence(toSentenceItem(sentence));
        return response;
    }

    private @NonNull LikeResponse buildErrorResponse() {
        LikeResponse response = new LikeResponse();
        response.setSuccess(false);
        response.setMessage("句子不存在");
        response.setCode("not_found");
        response.setSentence(null);
        return response;
    }

    private @NonNull SentenceItem toSentenceItem(@NonNull Sentence s) {
        SentenceItem item = new SentenceItem();
        item.setMetaName(s.getMetadata().getName());
        item.setAuthor(s.getSpec().getAuthor());
        item.setContent(s.getSpec().getContent());
        item.setSource(s.getSpec().getSource());
        item.setCreatedBy(s.getSpec().getCreatedBy());
        item.setLikeCount(s.getStatus() != null ? s.getStatus().getLikeCount() : 0);
        item.setViewCount(s.getStatus() != null ? s.getStatus().getViewCount() : 0);
        return item;
    }

    // 清理过期的点赞缓存方法
    public void cleanExpiredLikeCache() {
        settingConfig.getBasicConfig().doOnNext(config -> {
            long now = System.currentTimeMillis();
            long cooldown = Duration.ofHours(config.getLikeCooldown()).toMillis();
            int removed = likeCache.cleanIf(state -> state.isExpired(cooldown, now));
            if (removed > 0) {
                log.info("清理过期点赞缓存: 移除 {} 项", removed);
            }
        }).subscribe();
    }

    @Data
    @Schema(name = "RandomSentenceResponse")
    public static class RandomSentenceResponse {
        @Schema(description = "请求的分类名称")
        private String categoryName;
        @Schema(description = "允许的最大请求数量")
        private long maxRandomLimit;
        @Schema(description = "实际返回数量")
        private long returned;
        @Schema(description = "句子列表")
        private List<SentenceItem> sentences;
    }

    @Data
    @Schema(name = "SentenceItem")
    public static class SentenceItem {
        @Schema(description = "MetaName")
        private String metaName;
        @Schema(description = "作者")
        private String author;
        @Schema(description = "内容")
        private String content;
        @Schema(description = "来源")
        private String source;
        @Schema(description = "创建者")
        private String createdBy;
        @Schema(description = "点赞数")
        private long likeCount;
        @Schema(description = "浏览数")
        private long viewCount;
    }

    @Data
    @Schema(name = "LikeResponse")
    public static class LikeResponse {
        @Schema(description = "是否成功")
        private boolean success;
        @Schema(description = "状态码：ok / rate_limited / not_found")
        private String code;
        @Schema(description = "提示信息")
        private String message;
        @Schema(description = "句子完整信息")
        private SentenceItem sentence;
    }
}