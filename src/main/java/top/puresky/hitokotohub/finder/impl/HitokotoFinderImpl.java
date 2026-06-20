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
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
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

@Finder("hitokotoFinder")
@Component
@RequiredArgsConstructor
public class HitokotoFinderImpl implements HitokotoFinder {

    private final ReactiveExtensionClient client;
    private final SettingConfig settingConfig;

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
                            .map(ListResult::getItems)
                            .flatMapMany(items -> {
                                if (items.size() >= effectiveSize || total <= effectiveSize) {
                                    return Mono.just(items);
                                }
                                int remaining = effectiveSize - items.size();
                                var wrapRequest = PageRequestImpl.of(1, remaining, Sort.unsorted());
                                return client.listBy(Sentence.class, options, wrapRequest)
                                    .map(ListResult::getItems)
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
                                        .flatMap(sentence -> {
                                            if (sentence.getStatus() == null) {
                                                sentence.setStatus(new Sentence.Status());
                                            }
                                            sentence.getStatus().setViewCount(sentence.getStatus().getViewCount() + 1);

                                            // 记录分类浏览
                                            CategoryViewRecord record = new CategoryViewRecord();
                                            record.setMetadata(new Metadata());
                                            record.getMetadata().setGenerateName("cvr-");
                                            record.setSpec(new CategoryViewRecord.Spec());
                                            record.getSpec().setCategoryName(sentence.getSpec().getCategoryName());
                                            record.getSpec().setEventType(CategoryViewRecord.EventType.VIEW);
                                            return client.update(sentence)
                                                .then(client.create(record))
                                                .thenReturn(sentence);
                                        })
                                        .thenMany(Flux.fromIterable(randomItems));
                                }
                                return Flux.fromIterable(randomItems);
                            });
                    })
                    .map(this::toSentenceVo);
            });
    }

    @Override
    public Flux<CategoryVo> listCategories() {
        return client.listAll(Category.class, new ListOptions(), Sort.unsorted())
            .filter(c -> c.getStatus() != null && c.getStatus().getSentenceCount() > 0)
            .map(this::toCategoryVo);
    }


    private SentenceVo toSentenceVo(@NonNull Sentence s) {
        var spec = s.getSpec();
        var status = s.getStatus();
        return SentenceVo.builder().name(s.getMetadata().getName()).author(spec.getAuthor())
            .content(spec.getContent()).source(spec.getSource())
            .categoryName(spec.getCategoryName())
            .likeCount(status != null ? status.getLikeCount() : 0)
            .viewCount(status != null ? status.getViewCount() : 0).build();
    }

    private CategoryVo toCategoryVo(@NonNull Category c) {
        var spec = c.getSpec();
        var status = c.getStatus();
        return CategoryVo.builder().name(c.getMetadata().getName()).displayName(spec.getName())
            .description(spec.getDescription())
            .sentenceCount(status != null ? status.getSentenceCount() : 0).build();
    }
}