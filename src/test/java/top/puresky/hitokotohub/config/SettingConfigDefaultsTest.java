package top.puresky.hitokotohub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import top.puresky.hitokotohub.UncategorizedConstants;
import top.puresky.hitokotohub.config.SettingConfig.AiConfig;
import top.puresky.hitokotohub.config.SettingConfig.BasicConfig;
import top.puresky.hitokotohub.config.SettingConfig.SubmissionConfig;

/**
 * 校验设置项的默认值在「设置页定义」与「运行期兜底」两处保持一致。
 *
 * <p>插件的默认值同时存在于两个位置，一旦漂移就会出现「设置页显示 A、运行期实际是 B」的问题：
 * <ul>
 *   <li>{@code src/main/resources/extensions/settings.yaml}：设置页展示并保存的默认值</li>
 *   <li>{@link SettingConfig} 的常量与 {@code xxOrDefault()}：配置项缺失时的运行期默认值</li>
 * </ul>
 */
class SettingConfigDefaultsTest {

    /** 从 settings.yaml 提取所有 formSchema 字段的 name → value 映射（即设置页默认值）。 */
    @Test
    void settingsYamlDefaultsMatchJavaFallbacks() {
        Map<String, Object> defaults = yamlDefaults();

        assertEquals(BasicConfig.DEFAULT_MAX_RANDOM_LIMIT, defaults.get("maxRandomLimit"));
        assertEquals(BasicConfig.DEFAULT_RANDOM_LIMIT, defaults.get("randomLimit"));
        assertEquals(BasicConfig.DEFAULT_ENCODE, defaults.get("encode"));
        assertEquals(BasicConfig.DEFAULT_LIKE_COOLDOWN_HOURS, defaults.get("likeCooldown"));
        assertEquals(BasicConfig.DEFAULT_TRUST_PROXY_HEADERS, defaults.get("trustProxyHeaders"));
        assertEquals(BasicConfig.DEFAULT_ENABLE_VIEW_COUNT, defaults.get("enableViewCount"));
        assertEquals(BasicConfig.DEFAULT_STATS_MAX_KEEP, defaults.get("statsMaxKeep"));
        assertEquals(BasicConfig.DEFAULT_STATS_RETENTION_DAYS, defaults.get("statsRetentionDays"));

        assertEquals(AiConfig.DEFAULT_AUTO_PUBLISH, defaults.get("aiSentenceAutoPublish"));

        assertEquals(SubmissionConfig.DEFAULT_AUTO_PUBLISH, defaults.get("submissionAutoPublish"));
        assertEquals(SubmissionConfig.DEFAULT_COOLDOWN_MINUTES, defaults.get("submissionCooldown"));
        assertEquals(SubmissionConfig.DEFAULT_BATCH_LIMIT, defaults.get("submissionBatchLimit"));
        assertEquals(SubmissionConfig.DEFAULT_MAX_PENDING, defaults.get("submissionMaxPending"));
    }

    /** AI 生成的目标分类默认为内置的「未分类」，保证开箱即用可直接生成。 */
    @Test
    void aiDefaultCategoryIsBuiltInUncategorized() {
        assertEquals(UncategorizedConstants.METADATA_NAME,
            yamlDefaults().get("aiSentenceCategory"));
    }

    /** 配置项为 null（分组存在但字段缺失）时，必须回退到设置页默认值。 */
    @Test
    void emptyConfigFallsBackToSettingsDefaults() {
        BasicConfig basic = new BasicConfig();
        assertEquals(BasicConfig.DEFAULT_MAX_RANDOM_LIMIT, basic.maxRandomLimitOrDefault());
        assertEquals(BasicConfig.DEFAULT_RANDOM_LIMIT, basic.randomLimitOrDefault());
        assertEquals(BasicConfig.DEFAULT_ENCODE, basic.encodeOrDefault());
        assertEquals(BasicConfig.DEFAULT_LIKE_COOLDOWN_HOURS, basic.likeCooldownHoursOrDefault());
        assertEquals(BasicConfig.DEFAULT_STATS_MAX_KEEP, basic.statsMaxKeepOrDefault());
        assertEquals(BasicConfig.DEFAULT_STATS_RETENTION_DAYS, basic.statsRetentionDaysOrDefault());
        assertTrue(basic.trustProxyHeadersOrDefault());
        assertTrue(basic.viewCountEnabledOrDefault());
        assertTrue(basic.defaultCategoriesOrDefault().isEmpty());

        assertTrue(new AiConfig().autoPublishOrDefault());

        SubmissionConfig submission = new SubmissionConfig();
        assertTrue(submission.autoPublishOrDefault());
        assertEquals(SubmissionConfig.DEFAULT_COOLDOWN_MINUTES,
            submission.cooldownMinutesOrDefault());
        assertEquals(SubmissionConfig.DEFAULT_BATCH_LIMIT, submission.batchLimitOrDefault());
        assertEquals(SubmissionConfig.DEFAULT_MAX_PENDING, submission.maxPendingOrDefault());
    }

    /** 已保存的设置值必须优先于兜底默认值。 */
    @Test
    void explicitValuesOverrideDefaults() {
        BasicConfig basic = new BasicConfig();
        basic.setMaxRandomLimit(50);
        basic.setRandomLimit(5);
        basic.setEncode("text");
        basic.setLikeCooldown(1);
        basic.setTrustProxyHeaders(false);
        basic.setEnableViewCount(false);
        basic.setStatsMaxKeep(200);
        basic.setStatsRetentionDays(7);
        basic.setDefaultCategory(List.of("poem"));
        assertEquals(50, basic.maxRandomLimitOrDefault());
        assertEquals(5, basic.randomLimitOrDefault());
        assertEquals("text", basic.encodeOrDefault());
        assertEquals(1, basic.likeCooldownHoursOrDefault());
        assertFalse(basic.trustProxyHeadersOrDefault());
        assertFalse(basic.viewCountEnabledOrDefault());
        assertEquals(200, basic.statsMaxKeepOrDefault());
        assertEquals(7, basic.statsRetentionDaysOrDefault());
        assertEquals(List.of("poem"), basic.defaultCategoriesOrDefault());

        AiConfig ai = new AiConfig();
        ai.setAiSentenceAutoPublish(false);
        assertFalse(ai.autoPublishOrDefault());

        SubmissionConfig submission = new SubmissionConfig();
        submission.setSubmissionAutoPublish(false);
        submission.setSubmissionCooldown(0);
        submission.setSubmissionBatchLimit(0);
        submission.setSubmissionMaxPending(0);
        assertFalse(submission.autoPublishOrDefault());
        assertEquals(0, submission.cooldownMinutesOrDefault());
        // 连续提交上限至少为 1，避免配置为 0 时完全无法提交
        assertEquals(1, submission.batchLimitOrDefault());
        // 待审核上限 0 表示不限制
        assertEquals(0, submission.maxPendingOrDefault());
    }

    /** 读取 classpath 中的 settings.yaml，提取 name → value 映射。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> yamlDefaults() {
        try (InputStream in = SettingConfigDefaultsTest.class.getClassLoader()
            .getResourceAsStream("extensions/settings.yaml")) {
            assertNotNull(in, "未找到 extensions/settings.yaml");
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> spec = (Map<String, Object>) root.get("spec");
            List<Map<String, Object>> forms = (List<Map<String, Object>>) spec.get("forms");
            Map<String, Object> defaults = new HashMap<>();
            for (Map<String, Object> form : forms) {
                List<Map<String, Object>> formSchema =
                    (List<Map<String, Object>>) form.get("formSchema");
                if (formSchema == null) {
                    continue;
                }
                for (Map<String, Object> field : formSchema) {
                    Object name = field.get("name");
                    if (name != null && field.containsKey("value")) {
                        defaults.put(name.toString(), field.get("value"));
                    }
                }
            }
            return defaults;
        } catch (IOException e) {
            throw new IllegalStateException("读取 extensions/settings.yaml 失败", e);
        }
    }
}
