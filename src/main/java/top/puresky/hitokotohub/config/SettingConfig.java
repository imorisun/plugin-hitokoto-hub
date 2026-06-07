package top.puresky.hitokotohub.config;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import reactor.core.publisher.Mono;
import java.util.List;

public interface SettingConfig {
    Mono<BasicConfig> getBasicConfig();

    @Data
    class BasicConfig {
        public static final String GROUP = "basic";
        @Schema(description = "最大随机条数")
        private Integer maxRandomLimit;
        @Schema(description = "默认随机条数")
        private Integer randomLimit;
        @Schema(description = "默认分类")
        private List<String> defaultCategory;
        @Schema(description = "默认返回格式")
        private String encode;
        @Schema(description = "点赞冷却时间（小时）")
        private Integer likeCooldown;
        @Schema(description = "启用浏览量统计")
        private Boolean enableViewCount;
        @Schema(description = "统计数据最大保留条数")
        private Integer statsMaxKeep;
        @Schema(description = "统计数据保留天数")
        private Integer statsRetentionDays;
    }
}
