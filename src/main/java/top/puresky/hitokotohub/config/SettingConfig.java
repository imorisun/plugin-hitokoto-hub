package top.puresky.hitokotohub.config;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import reactor.core.publisher.Mono;

public interface SettingConfig {
    Mono<BasicConfig> getBasicConfig();
    Mono<AiConfig> getAiConfig();
    Mono<SubmissionConfig> getSubmissionConfig();
    Mono<SimilarityConfig> getSimilarityConfig();
    Mono<TemplateConfig> getTemplateConfig();
    Mono<ShareConfig> getShareConfig();

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
        @Schema(description = "是否信任反向代理头（X-Forwarded-For）")
        private Boolean trustProxyHeaders;
        @Schema(description = "启用浏览量统计")
        private Boolean enableViewCount;
        @Schema(description = "统计数据最大保留条数")
        private Integer statsMaxKeep;
        @Schema(description = "统计数据保留天数")
        private Integer statsRetentionDays;
        @Schema(description = "统计数据清理任务的 Cron 表达式")
        private String statsCleanupCron;
    }
    @Data
    class AiConfig {
        public static final String GROUP = "ai";
        @Schema(description = "启用 AI 自动生成（定时任务）")
        private Boolean enableAiGenerate;
        @Schema(description = "AI 生成句子的 Cron 表达式")
        private String aiCron;
        @Schema(description = "AI 生成模型名称")
        private String languageModelName;
        @Schema(description = "生成句子的主题")
        private String aiTopic;
        @Schema(description = "AI 生成句子的数量")
        private Integer aiSentenceCount;
        @Schema(description = "AI 生成的句子保存到的分类")
        private String aiSentenceCategory;
        @Schema(description = "AI 生成的句子是否自动发布")
        private Boolean aiSentenceAutoPublish;
        @Schema(description = "AI 生成句子的系统提示词")
        private String aiSystemPrompt;
        @Schema(description = "AI日志最大保留条数")
        private Integer aiLogMaxKeep;
        @Schema(description = "AI日志保留天数")
        private Integer aiLogRetentionDays;
        @Schema(description = "AI日志清理任务的 Cron 表达式")
        private String aiLogCleanupCron;
    }
    @Data
    class SubmissionConfig {
        public static final String GROUP = "submission";
        @Schema(description = "启用访客提交")
        private Boolean enableSubmission;
        @Schema(description = "默认提交分类")
        private String submissionDefaultCategory;
        @Schema(description = "审核通过后自动发布")
        private Boolean submissionAutoPublish;
        @Schema(description = "提交冷却时间（分钟）")
        private Integer submissionCooldown;
        @Schema(description = "连续提交上限")
        private Integer submissionBatchLimit;
        @Schema(description = "待审核句子数量上限")
        private Integer submissionMaxPending;
        @Schema(description = "提交记录最大保留条数")
        private Integer submissionMaxKeep;
        @Schema(description = "提交记录清理任务的 Cron 表达式")
        private String submissionCleanupCron;
    }

    @Data
    class SimilarityConfig {
        public static final String GROUP = "similarity";
        @Schema(description = "启用定时相似度检查")
        private Boolean enableScheduledCheck;
        @Schema(description = "相似度检查 Cron 表达式")
        private String similarityCron;
        @Schema(description = "相似度算法")
        private String similarityAlgorithm;
        @Schema(description = "相似度阈值")
        private Double similarityThreshold;
    }

    @Data
    class TemplateConfig {
        public static final String GROUP = "template";
        @Schema(description = "模板左上角展示的文字，留空则使用默认文字 LiteWords")
        private String templateLogoText;
        @Schema(description = "是否开启点击左上角文字回到站点首页")
        private Boolean templateLogoLinkEnabled;
        @Schema(description = "模板默认主题：auto 跟随系统, dark 暗色, light 亮色")
        private String templateTheme;
        @Schema(description = "是否显示花瓣飘落动画")
        private Boolean templateShowSakura;
        @Schema(description = "是否显示首次操作提示")
        private Boolean templateShowHint;
        @Schema(description = "是否启用定时自动切换句子")
        private Boolean enableAutoRefresh;
        @Schema(description = "自动切换间隔（秒）")
        private Integer autoRefreshInterval;
    }

    @Data
    class ShareConfig {
        public static final String GROUP = "share";
        @Schema(description = "分享卡片上的站点名称，留空则使用 Halo 站点标题")
        private String siteName;
    }
}
