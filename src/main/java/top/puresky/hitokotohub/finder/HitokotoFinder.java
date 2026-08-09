package top.puresky.hitokotohub.finder;

import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HitokotoFinder {

    Flux<SentenceVo> randomSentences(int size ,String categoryName);

    /**
     * 按名称获取单条已发布句子（分享链接直达用）。
     *
     * @param name 句子 metadata.name
     * @return 句子 VO；不存在或未发布时为空
     */
    Mono<SentenceVo> sentenceByName(String name);

    Flux<CategoryVo> listCategories();

    @Data
    @Builder
    class SentenceVo {
        private String name;
        private String author;
        private String content;
        private String source;
        private String categoryName;
        private long likeCount;
        private long viewCount;
        private String jumpUrl;
    }

    @Data
    @Builder
    class CategoryVo {
        private String name;
        private String displayName;
        private String description;
        private long sentenceCount;
    }
}