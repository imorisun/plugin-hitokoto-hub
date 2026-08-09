package top.puresky.hitokotohub.finder.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.theme.finders.Finder;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.CategoryViewRecord;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.finder.HitokotoFinder;
import top.puresky.hitokotohub.service.CategoryCountService;

@Finder("hitokotoFinder")
@Component
@RequiredArgsConstructor
public class HitokotoFinderImpl implements HitokotoFinder {

    private final ReactiveExtensionClient client;
    private final SettingConfig settingConfig;
    private final CategoryCountService categoryCountService;

    @Override
    public Flux<SentenceVo> randomSentences(int size, String categoryName) {
        return settingConfig.getBasicConfig()
            .flatMapMany(config -> {
                int actualSize = size > 0
                    ? Math.min(size, config.getMaxRandomLimit())
                    : config.getRandomLimit();

                // 解析请求参数和默认分类
                List<String> defaultCategories = config.getDefaultCategory();

                List<String> finalCategories = null;
                if (StringUtils.isNotBlank(categoryName)) {
                    finalCategories = List.of(categoryName);
                } else if (!defaultCategories.isEmpty()) {
                    finalCategories = defaultCategories;
                }

                var query = Queries.equal("status.isPublished", true);
                if (finalCategories != null) {
                    query = Queries.and(query, Queries.in("spec.categoryName", finalCategories));
                }
                var options = ListOptions.builder().fieldQuery(query).build();

                return client.countBy(Sentence.class, options)
                    .filter(total -> total > 0)
                    .flatMapMany(total -> {
                        int totalInt = total.intValue();
                        int effectiveSize = Math.min(actualSize, totalInt);
                        int totalPages = (int) Math.ceil((double) totalInt / effectiveSize);
                        int page = RandomUtils.insecure().randomInt(1, totalPages + 1);

                        var pageRequest = PageRequestImpl.of(page, effectiveSize, Sort.unsorted());

                        return client.listBy(Sentence.class, options, pageRequest)
                            .map(r -> r.getItems())
                            .flatMapMany(items -> {
                                if (items.size() >= effectiveSize || total <= effectiveSize) {
                                    return Mono.just(items);
                                }
                                int remaining = effectiveSize - items.size();
                                var wrapRequest = PageRequestImpl.of(1, remaining, Sort.unsorted());
                                return client.listBy(Sentence.class, options, wrapRequest)
                                    .map(r -> r.getItems())
                                    .map(wrapItems -> {
                                        List<Sentence> combined = new ArrayList<>(items);
                                        combined.addAll(wrapItems);
                                        return combined;
                                    });
                            })
                            .flatMap(items -> {
                                List<Sentence> randomItems = new ArrayList<>(items);
                                Collections.shuffle(randomItems,
                                    java.util.concurrent.ThreadLocalRandom.current());

                                if (config.getEnableViewCount()) {
                                    return Flux.fromIterable(randomItems)
                                        .concatMap(sentence -> {
                                            if (sentence.getStatus() == null) {
                                                sentence.setStatus(new Sentence.Status());
                                            }
                                            sentence.getStatus().setViewCount(sentence.getStatus().getViewCount() + 1);

                                            // 记录分类浏览（同时记录句子名称以支持按句子维度统计）
                                            CategoryViewRecord record = new CategoryViewRecord();
                                            record.setMetadata(new Metadata());
                                            record.getMetadata().setGenerateName("cvr-");
                                            record.setSpec(new CategoryViewRecord.Spec());
                                            record.getSpec().setCategoryName(sentence.getSpec().getCategoryName());
                                            if (sentence.getMetadata() != null
                                                && sentence.getMetadata().getName() != null) {
                                                record.getSpec().setSentenceName(
                                                    sentence.getMetadata().getName());
                                            }
                                            record.getSpec().setEventType(CategoryViewRecord.EventType.VIEW);
                                            return client.update(sentence)
                                                .then(client.create(record))
                                                .thenReturn(sentence);
                                        }, 1)
                                        .thenMany(Flux.fromIterable(randomItems));
                                }
                                return Flux.fromIterable(randomItems);
                            });
                    })
                    .flatMap(this::toSentenceVo);
            });
    }

    @Override
    public Mono<SentenceVo> sentenceByName(String name) {
        if (StringUtils.isBlank(name)) {
            return Mono.empty();
        }
        return client.fetch(Sentence.class, name)
            .filter(s -> s.getStatus() != null && s.getStatus().isPublished())
            .flatMap(this::toSentenceVo);
    }

    @Override
    public Flux<CategoryVo> listCategories() {
        // 并行：分类列表 + 实时计数 map（单次 listAll + 内存分组，O(N)）
        var categoriesMono = client.listAll(Category.class, new ListOptions(), Sort.unsorted()).collectList();
        var countsMono = categoryCountService.getAllCounts();

        return Mono.zip(categoriesMono, countsMono)
            .flatMapMany(tuple -> {
                var categories = tuple.getT1();
                var counts = tuple.getT2();
                // 仅展示 sentenceCount > 0 的分类（保留原行为）
                return Flux.fromIterable(categories.stream()
                    .filter(c -> counts.getOrDefault(c.getMetadata().getName(), 0L) > 0)
                    .map(c -> toCategoryVo(c, counts.getOrDefault(c.getMetadata().getName(), 0L)))
                    .toList());
            });
    }


    private Mono<SentenceVo> toSentenceVo(@NonNull Sentence s) {
        var spec = s.getSpec();
        var status = s.getStatus();
        var builder = SentenceVo.builder()
            .name(s.getMetadata().getName())
            .author(spec.getAuthor())
            .content(spec.getContent())
            .source(spec.getSource())
            .categoryName(spec.getCategoryName())
            .likeCount(status != null ? status.getLikeCount() : 0)
            .viewCount(status != null ? status.getViewCount() : 0);

        String linkUrl = spec.getLinkUrl();
        String postName = spec.getPostName();

        if (StringUtils.isNotBlank(linkUrl)) {
            builder.jumpUrl(linkUrl);
            return Mono.just(builder.build());
        }

        if (StringUtils.isNotBlank(postName)) {
            return client.fetch(Post.class, postName)
                .map(post -> {
                    String permalink = null;
                    if (post.getStatus() != null) {
                        permalink = post.getStatus().getPermalink();
                    }
                    return builder.jumpUrl(permalink).build();
                })
                .defaultIfEmpty(builder.build());
        }

        return Mono.just(builder.build());
    }

    private CategoryVo toCategoryVo(@NonNull Category c, long sentenceCount) {
        var spec = c.getSpec();
        return CategoryVo.builder().name(c.getMetadata().getName()).displayName(spec.getName())
            .description(spec.getDescription())
            .sentenceCount(sentenceCount).build();
    }
}