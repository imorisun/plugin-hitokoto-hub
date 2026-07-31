package top.puresky.hitokotohub.finder;

import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Flux;

public interface HitokotoFinder {

    Flux<SentenceVo> randomSentences(int size ,String categoryName);

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