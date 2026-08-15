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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
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
    /** 浏览量去重窗口：同一 IP 对同一句子在此时长内重复浏览只计一次 */
    private static final long VIEW_DEDUP_TTL_MILLIS = 30_000L;
    /** 公开搜索单次最大返回数量 */
    private static final int MAX_SEARCH_RESULTS = 20;
    /** 热门榜单单次最大返回数量 */
    private static final int MAX_HOT_LIMIT = 50;
    /** 设置缺失时随机接口的兜底默认值 */
    private static final int DEFAULT_MAX_RANDOM_LIMIT = 20;
    private static final int DEFAULT_RANDOM_LIMIT = 1;
    private static final int DEFAULT_LIKE_COOLDOWN_HOURS = 12;
    /**
     * 计数自增的乐观锁冲突重试策略：并发读改写导致版本冲突时，重新拉取最新数据再自增。
     * 首次尝试复用已持有的对象避免额外查询，仅冲突后才重新 fetch。
     */
    private static final Retry OPTIMISTIC_LOCKING_RETRY = Retry.backoff(3, Duration.ofMillis(50))
        .maxBackoff(Duration.ofMillis(200))
        .filter(OptimisticLockingFailureException.class::isInstance);

    private final SettingConfig settingConfig;
    private final ReactiveExtensionClient client;
    private final IpCooldownCache<SimpleCooldownState> likeCache = new IpCooldownCache<>();
    private final IpCooldownCache<SimpleCooldownState> viewDedupCache = new IpCooldownCache<>();

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
                .response(responseBuilder().implementation(LikeResponse.class)))
            .GET("sentence/{name}", this::getSentenceByName,
                builder -> builder.operationId("getSentenceByName").summary("按名称获取单条句子")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.PATH).name("name")
                        .description("句子 metadata.name").implementation(String.class)
                        .required(true))
                    .response(responseBuilder().implementation(SentenceItem.class)))
            .GET("sentence/search", this::searchSentences,
                builder -> builder.operationId("searchSentences").summary("搜索已发布句子").tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("keyword")
                        .description("关键词（匹配句子内容）").implementation(String.class)
                        .required(true)).parameter(
                        parameterBuilder().in(ParameterIn.QUERY).name("categoryName")
                            .implementation(String.class).required(false)).parameter(
                        parameterBuilder().in(ParameterIn.QUERY).name("limit")
                            .description("返回数量，默认 10，最大 " + MAX_SEARCH_RESULTS)
                            .implementation(Integer.class).required(false))
                    .response(responseBuilder().implementationArray(SentenceItem.class)))
            .GET("sentence/hot", this::getHotSentences,
                builder -> builder.operationId("getHotSentences").summary("获取热门句子榜单")
                    .tag(TAG)
                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("sort")
                        .description("排序字段：like（默认，按点赞数）/ view（按浏览数）")
                        .implementation(String.class).required(false)).parameter(
                        parameterBuilder().in(ParameterIn.QUERY).name("categoryName")
                            .implementation(String.class).required(false)).parameter(
                        parameterBuilder().in(ParameterIn.QUERY).name("limit")
                            .description("返回数量，默认 10，最大 " + MAX_HOT_LIMIT)
                            .implementation(Integer.class).required(false))
                    .response(responseBuilder().implementationArray(SentenceItem.class))).build();
    }

    @Override
    public @NonNull GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(GROUP_VERSION);
    }

    Mono<ServerResponse> getRandomSentences(ServerRequest request) {
        return settingConfig.getBasicConfig().flatMap(config -> {
            String ip = HttpUtils.getClientIp(request.exchange().getRequest(),
                isTrustProxyHeaders(config));

            String categoryNameParam = request.queryParam("categoryName")
                .filter(StringUtils::isNotBlank).orElse(null);

            // 防御性解析：非数字或非正数回退为设置默认值，避免 NumberFormatException 与非法分页
            int maxRandomLimit = config.getMaxRandomLimit() != null
                ? config.getMaxRandomLimit() : DEFAULT_MAX_RANDOM_LIMIT;
            int defaultLimit = config.getRandomLimit() != null
                ? config.getRandomLimit() : DEFAULT_RANDOM_LIMIT;
            int limit = NumberUtils.toInt(request.queryParam("limit").orElse(null), defaultLimit);
            int actualLimit = Math.max(1, Math.min(limit, maxRandomLimit));
            String encode = request.queryParam("encode").filter(StringUtils::isNotBlank)
                .orElse(config.getEncode());

            List<String> defaultCategories = config.getDefaultCategory();

            // 请求参数优先，没有则用设置里的
            List<String> finalCategories = null;
            if (StringUtils.isNotBlank(categoryNameParam)) {
                finalCategories = List.of(categoryNameParam);
            } else if (defaultCategories != null && !defaultCategories.isEmpty()) {
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
                            .map(r -> r.getItems()).flatMap(items -> {
                                if (items.size() >= effectiveSize || total <= effectiveSize) {
                                    return Mono.just(items);
                                }

                                int remaining = effectiveSize - items.size();
                                var wrapRequest = PageRequestImpl.of(1, remaining, Sort.unsorted());

                                return client.listBy(Sentence.class, options, wrapRequest)
                                    .map(r -> r.getItems()).map(wrapItems -> {
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
                        ? incrementAndRecordViews(sentences, ip) : Mono.just(sentences))
                    .switchIfEmpty(Mono.just(Collections.emptyList()));

            if ("text".equalsIgnoreCase(encode)) {
                return sentencesMono.map(
                        sentences -> sentences.stream().map(s -> s.getSpec().getContent())
                            .collect(Collectors.joining("\n")))
                    .flatMap(text -> ServerResponse.ok().bodyValue(text));
            }

            return sentencesMono.zipWith(displayNameMono).flatMap(tuple -> {
                List<Sentence> sentences = tuple.getT1();
                String displayName = tuple.getT2();
                return loadLikedNames(ip, sentences).flatMap(likedNames ->
                    Flux.fromIterable(sentences)
                        .concatMap(s -> toSentenceItem(s,
                            likedNames.contains(s.getMetadata().getName())))
                        .collectList()
                        .map(items -> {
                            RandomSentenceResponse response = new RandomSentenceResponse();
                            response.setCategoryName(displayName);
                            response.setMaxRandomLimit(maxRandomLimit);
                            response.setReturned(items.size());
                            response.setSentences(items);
                            return response;
                        }));
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


    private @NonNull Mono<List<Sentence>> incrementAndRecordViews(List<Sentence> sentences,
                                                                    String ip) {
        long now = System.currentTimeMillis();
        return Flux.fromIterable(sentences)
            .concatMap(sentence -> {
                String name = sentence.getMetadata().getName();
                // 同一 IP 对同一句子在去重窗口内只计一次浏览，防止刷新刷量
                String dedupKey = ip + ":" + name;
                SimpleCooldownState existing = viewDedupCache.get(dedupKey);
                if (existing != null && existing.isCoolingDown(VIEW_DEDUP_TTL_MILLIS, now)) {
                    return Mono.just(sentence);
                }
                viewDedupCache.put(dedupKey, new SimpleCooldownState(now));

                // 计数自增（乐观锁冲突自动重试），随后记录分类浏览事件；
                // 统计为旁路逻辑，任何失败都降级为“不计数但正常返回句子”，
                // 避免一次统计写入失败导致整个随机接口 500。
                return updateViewCountWithRetry(sentence)
                    .flatMap(updated -> {
                        CategoryViewRecord record = CategoryViewRecordFactory.create(updated,
                            CategoryViewRecord.EventType.VIEW, null);
                        return client.create(record).thenReturn(updated);
                    })
                    .onErrorResume(e -> {
                        log.warn("浏览量统计失败（句子 [{}]），已降级为不计数: {}", name,
                            e.getMessage());
                        return Mono.just(sentence);
                    });
            }, 1)
            .then(Mono.just(sentences));
    }

    /**
     * 查询当前 IP 已点赞的句子名称集合，用于在响应中标注 hasLiked。
     *
     * <p>仅查询本次返回句子范围内的 LIKE 记录（sentenceName 已建索引），
     * 结果集大小与返回条数同阶，避免全量扫描该 IP 的全部历史点赞记录。
     */
    private Mono<Set<String>> loadLikedNames(String ip, List<Sentence> sentences) {
        if (sentences.isEmpty()) {
            return Mono.just(Collections.emptySet());
        }
        Set<String> names = sentences.stream()
            .map(s -> s.getMetadata().getName())
            .collect(Collectors.toSet());
        return client.listAll(CategoryViewRecord.class,
                ListOptions.builder().fieldQuery(Queries.and(
                    Queries.equal("spec.ip", ip),
                    Queries.equal("spec.eventType", CategoryViewRecord.EventType.LIKE.name()),
                    Queries.in("spec.sentenceName", names)
                )).build(),
                Sort.unsorted())
            .map(record -> record.getSpec().getSentenceName())
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet())
            .onErrorResume(e -> {
                log.warn("查询 IP 已点赞句子失败，降级为空集合", e);
                return Mono.just(Collections.emptySet());
            });
    }

    @NonNull Mono<ServerResponse> toggleLike(@NonNull ServerRequest request) {
        String name = request.queryParam("name").orElse("");
        String action = request.queryParam("action").orElse("like");
        return settingConfig.getBasicConfig().flatMap(config -> {
            String ip = HttpUtils.getClientIp(request.exchange().getRequest(),
                isTrustProxyHeaders(config));
            String likeKey = ip + ":like:" + name;
            String unlikeKey = ip + ":unlike:" + name;
            boolean isUnlike = "unlike".equals(action);
            // 当前操作完成后该句子的点赞状态：点赞后为 true，取消后为 false（rate_limited 时保持该语义）
            boolean hasLiked = !isUnlike;
            String checkKey = isUnlike ? unlikeKey : likeKey;
            SimpleCooldownState state = likeCache.get(checkKey);
            long now = System.currentTimeMillis();
            int cooldownHours = config.getLikeCooldown() != null
                ? config.getLikeCooldown() : DEFAULT_LIKE_COOLDOWN_HOURS;
            long likeCooldown = Duration.ofHours(cooldownHours).toMillis();
            if (state != null && state.isCoolingDown(likeCooldown, now)) {
                long remainingSeconds = state.remainingMillis(likeCooldown, now) / 1000;
                return client.fetch(Sentence.class, name)
                    .filter(this::isPublishable)
                    .flatMap(sentence -> buildLikeResponse(sentence, false,
                        "请在 " + TimeFormatUtils.formatRemainingTime(remainingSeconds) + " 后再"
                            + (isUnlike ? "取消点赞" : "点赞"), "rate_limited", hasLiked))
                    .defaultIfEmpty(buildErrorResponse())
                    .flatMap(response -> ServerResponse.ok().bodyValue(response));
            }
            String oppositeKey = isUnlike ? likeKey : unlikeKey;
            return client.fetch(Sentence.class, name)
                .filter(this::isPublishable)
                .flatMap(sentence -> updateLikeCountWithRetry(sentence, isUnlike)
                    .flatMap(updated -> {
                        likeCache.put(checkKey,
                            new SimpleCooldownState(System.currentTimeMillis()));
                        likeCache.remove(oppositeKey);

                        if (isUnlike) {
                            // 取消点赞：删除该 IP 对该句子对应的点赞记录（失败不影响主流程）
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
                                .doOnNext(deleted -> log.debug("删除点赞记录: {}",
                                    deleted.getMetadata().getName()))
                                .onErrorResume(e -> {
                                    log.warn("删除点赞记录失败", e);
                                    return Mono.empty();
                                })
                                .then(buildLikeResponse(updated, true,
                                    "取消点赞成功", "ok", hasLiked));
                        }

                        // 点赞：创建点赞记录（失败不影响主流程，计数已更新）
                        CategoryViewRecord record = CategoryViewRecordFactory.forLike(
                            updated.getSpec().getCategoryName(), name, ip);
                        return client.create(record)
                            .onErrorResume(e -> {
                                log.warn("写入点赞记录失败（句子 [{}]）", name, e);
                                return Mono.empty();
                            })
                            .then(buildLikeResponse(updated, true,
                                "点赞成功", "ok", hasLiked));
                    }))
                .defaultIfEmpty(buildErrorResponse())
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
        });
    }

    /** 是否信任反向代理头：设置缺失时默认 true，兼容既有反代部署。 */
    private static boolean isTrustProxyHeaders(SettingConfig.BasicConfig config) {
        return config.getTrustProxyHeaders() == null
            || Boolean.TRUE.equals(config.getTrustProxyHeaders());
    }

    /** 仅允许对已发布且未删除的句子点赞（防止未发布内容被点赞并泄露内容）。 */
    private boolean isPublishable(Sentence sentence) {
        return sentence.getStatus() != null && sentence.getStatus().isPublished()
            && (sentence.getMetadata() == null
            || sentence.getMetadata().getDeletionTimestamp() == null);
    }

    /**
     * 浏览量 +1 并写回，带乐观锁冲突重试。
     *
     * <p>首次尝试直接使用查询结果中的对象（避免额外查询）；发生版本冲突后
     * 重新拉取最新数据再自增，最多重试 3 次，消除并发读改写的丢失更新。
     */
    private Mono<Sentence> updateViewCountWithRetry(Sentence sentence) {
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        return Mono.defer(() -> {
                if (firstAttempt.getAndSet(false)) {
                    if (sentence.getStatus() == null) {
                        sentence.setStatus(new Sentence.Status());
                    }
                    sentence.getStatus()
                        .setViewCount(sentence.getStatus().getViewCount() + 1);
                    return client.update(sentence);
                }
                return client.fetch(Sentence.class, sentence.getMetadata().getName())
                    .flatMap(fresh -> {
                        if (fresh.getStatus() == null) {
                            fresh.setStatus(new Sentence.Status());
                        }
                        fresh.getStatus().setViewCount(fresh.getStatus().getViewCount() + 1);
                        return client.update(fresh);
                    });
            })
            .retryWhen(OPTIMISTIC_LOCKING_RETRY);
    }

    /**
     * 点赞数 +1/-1 并写回，带乐观锁冲突重试。
     *
     * <p>首次尝试复用调用方已校验发布的句子对象；冲突后重新拉取（再次校验发布状态）
     * 再计算增减，避免并发点赞/取消点赞互相覆盖。
     */
    private Mono<Sentence> updateLikeCountWithRetry(Sentence sentence, boolean unlike) {
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        return Mono.defer(() -> {
                if (firstAttempt.getAndSet(false)) {
                    applyLikeDelta(sentence, unlike);
                    return client.update(sentence);
                }
                return client.fetch(Sentence.class, sentence.getMetadata().getName())
                    .filter(this::isPublishable)
                    .flatMap(fresh -> {
                        applyLikeDelta(fresh, unlike);
                        return client.update(fresh);
                    });
            })
            .retryWhen(OPTIMISTIC_LOCKING_RETRY);
    }

    /** 对句子点赞数执行 +1（点赞）或 -1（取消点赞，下限 0）。 */
    private static void applyLikeDelta(Sentence sentence, boolean unlike) {
        if (sentence.getStatus() == null) {
            sentence.setStatus(new Sentence.Status());
        }
        long currentLikes = sentence.getStatus().getLikeCount();
        sentence.getStatus()
            .setLikeCount(unlike ? Math.max(0, currentLikes - 1) : currentLikes + 1);
    }

    private @NonNull Mono<LikeResponse> buildLikeResponse(Sentence sentence, boolean success,
        String message, String code, boolean hasLiked) {
        return toSentenceItem(sentence, hasLiked).map(item -> {
            LikeResponse response = new LikeResponse();
            response.setSuccess(success);
            response.setMessage(message);
            response.setCode(code);
            response.setSentence(item);
            return response;
        });
    }

    private @NonNull LikeResponse buildErrorResponse() {
        LikeResponse response = new LikeResponse();
        response.setSuccess(false);
        response.setMessage("句子不存在");
        response.setCode("not_found");
        response.setSentence(null);
        return response;
    }

    private @NonNull Mono<SentenceItem> toSentenceItem(@NonNull Sentence s) {
        return toSentenceItem(s, false);
    }

    private @NonNull Mono<SentenceItem> toSentenceItem(@NonNull Sentence s, boolean hasLiked) {
        SentenceItem item = new SentenceItem();
        item.setMetaName(s.getMetadata().getName());
        item.setAuthor(s.getSpec().getAuthor());
        item.setContent(s.getSpec().getContent());
        item.setSource(s.getSpec().getSource());
        item.setCreatedBy(s.getSpec().getCreatedBy());
        item.setLikeCount(s.getStatus() != null ? s.getStatus().getLikeCount() : 0);
        item.setViewCount(s.getStatus() != null ? s.getStatus().getViewCount() : 0);
        item.setHasLiked(hasLiked);

        String linkUrl = s.getSpec().getLinkUrl();
        String postName = s.getSpec().getPostName();

        if (StringUtils.isNotBlank(linkUrl)) {
            item.setJumpUrl(linkUrl);
            return Mono.just(item);
        }

        if (StringUtils.isNotBlank(postName)) {
            return client.fetch(Post.class, postName)
                .map(post -> {
                    String permalink = null;
                    if (post.getStatus() != null) {
                        permalink = post.getStatus().getPermalink();
                    }
                    item.setJumpUrl(permalink);
                    return item;
                })
                .defaultIfEmpty(item);
        }

        return Mono.just(item);
    }

    // 清理过期的点赞缓存方法
    public void cleanExpiredLikeCache() {
        settingConfig.getBasicConfig().doOnNext(config -> {
            long now = System.currentTimeMillis();
            int cooldownHours = config.getLikeCooldown() != null
                ? config.getLikeCooldown() : DEFAULT_LIKE_COOLDOWN_HOURS;
            long cooldown = Duration.ofHours(cooldownHours).toMillis();
            int removed = likeCache.cleanIf(state -> state.isExpired(cooldown, now));
            if (removed > 0) {
                log.info("清理过期点赞缓存: 移除 {} 项", removed);
            }
        }).subscribe();
    }

    // 清理过期的浏览去重缓存（去重窗口短，频繁清理避免内存累积）
    public void cleanExpiredViewDedupCache() {
        long now = System.currentTimeMillis();
        int removed = viewDedupCache.cleanIf(state -> state.isExpired(VIEW_DEDUP_TTL_MILLIS, now));
        if (removed > 0) {
            log.debug("清理过期浏览去重缓存: 移除 {} 项", removed);
        }
    }

    // ===================== 按名称获取单条句子 =====================

    Mono<ServerResponse> getSentenceByName(ServerRequest request) {
        String name = request.pathVariable("name");
        return settingConfig.getBasicConfig().flatMap(config -> {
            String ip = HttpUtils.getClientIp(request.exchange().getRequest(),
                isTrustProxyHeaders(config));
            return client.fetch(Sentence.class, name)
                .filter(s -> s.getStatus() != null && s.getStatus().isPublished())
                .flatMap(s -> loadLikedNames(ip, List.of(s))
                    .map(liked -> liked.contains(name))
                    .flatMap(hasLiked -> toSentenceItem(s, hasLiked)))
                .flatMap(item -> ServerResponse.ok().bodyValue(item))
                .switchIfEmpty(ServerResponse.notFound().build());
        });
    }

    // ===================== 公开搜索 =====================

    Mono<ServerResponse> searchSentences(ServerRequest request) {
        String keyword = request.queryParam("keyword").filter(StringUtils::isNotBlank).orElse(null);
        if (keyword == null) {
            return ServerResponse.badRequest().bodyValue("keyword 参数必填");
        }
        String categoryName = request.queryParam("categoryName")
            .filter(StringUtils::isNotBlank).orElse(null);
        // 防御性解析：非数字回退默认 10，并钳制在 [1, MAX_SEARCH_RESULTS]
        int limit = NumberUtils.toInt(request.queryParam("limit").orElse(null), 10);
        int actualLimit = Math.min(Math.max(limit, 1), MAX_SEARCH_RESULTS);

        var queryBuilder = ListOptions.builder()
            .fieldQuery(Queries.and(
                Queries.equal("status.isPublished", true),
                Queries.isNull("metadata.deletionTimestamp"),
                Queries.contains("spec.content", keyword)
            ));
        if (StringUtils.isNotBlank(categoryName)) {
            queryBuilder.andQuery(Queries.equal("spec.categoryName", categoryName));
        }
        var pageRequest = PageRequestImpl.of(1, actualLimit, Sort.unsorted());

        return settingConfig.getBasicConfig().flatMap(config -> {
            String ip = HttpUtils.getClientIp(request.exchange().getRequest(),
                isTrustProxyHeaders(config));
            return client.listBy(Sentence.class, queryBuilder.build(), pageRequest)
                .map(r -> r.getItems())
                .flatMap(sentences -> loadLikedNames(ip, sentences).flatMap(likedNames ->
                    Flux.fromIterable(sentences)
                        .concatMap(s -> toSentenceItem(s,
                            likedNames.contains(s.getMetadata().getName())))
                        .collectList()))
                .flatMap(items -> ServerResponse.ok().bodyValue(items))
                .switchIfEmpty(ServerResponse.ok().bodyValue(List.of()));
        });
    }

    // ===================== 热门榜单 =====================

    Mono<ServerResponse> getHotSentences(ServerRequest request) {
        String sort = request.queryParam("sort").filter(StringUtils::isNotBlank).orElse("like");
        String categoryName = request.queryParam("categoryName")
            .filter(StringUtils::isNotBlank).orElse(null);
        // 防御性解析：非数字回退默认 10，并钳制在 [1, MAX_HOT_LIMIT]
        int limit = NumberUtils.toInt(request.queryParam("limit").orElse(null), 10);
        int actualLimit = Math.min(Math.max(limit, 1), MAX_HOT_LIMIT);

        // 按点赞数或浏览数倒序；索引已在 status.likeCount / status.viewCount 上注册
        String sortField = "view".equalsIgnoreCase(sort) ? "status.viewCount" : "status.likeCount";
        var queryBuilder = ListOptions.builder()
            .fieldQuery(Queries.and(
                Queries.equal("status.isPublished", true),
                Queries.isNull("metadata.deletionTimestamp")
            ));
        if (StringUtils.isNotBlank(categoryName)) {
            queryBuilder.andQuery(Queries.equal("spec.categoryName", categoryName));
        }
        var pageRequest = PageRequestImpl.of(1, actualLimit,
            Sort.by(Sort.Order.desc(sortField)));

        return settingConfig.getBasicConfig().flatMap(config -> {
            String ip = HttpUtils.getClientIp(request.exchange().getRequest(),
                isTrustProxyHeaders(config));
            return client.listBy(Sentence.class, queryBuilder.build(), pageRequest)
                .map(r -> r.getItems())
                .flatMap(sentences -> loadLikedNames(ip, sentences).flatMap(likedNames ->
                    Flux.fromIterable(sentences)
                        .concatMap(s -> toSentenceItem(s,
                            likedNames.contains(s.getMetadata().getName())))
                        .collectList()))
                .flatMap(items -> ServerResponse.ok().bodyValue(items))
                .switchIfEmpty(ServerResponse.ok().bodyValue(List.of()));
        });
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
        @Schema(description = "跳转链接")
        private String jumpUrl;
        @Schema(description = "当前访客是否已点赞（基于 IP 判断）")
        private boolean hasLiked;
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