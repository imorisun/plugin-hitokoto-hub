package top.puresky.hitokotohub.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.exception.AiFoundationException;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import top.puresky.hitokotohub.extension.AiGenerateLog;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.service.AiGenerateService;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "run.halo.aifoundation.AiModelService")
public class AiGenerateServiceImpl implements AiGenerateService {

    /** AI 返回 JSON 大小上限（512KB），防止解析 OOM */
    private static final int MAX_AI_RESPONSE_SIZE = 512 * 1024;
    /** 单条 AI 生成句子的字段长度上限，与 Sentence.Spec 校验注解保持一致 */
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MAX_AUTHOR_LENGTH = 50;
    private static final int MAX_SOURCE_LENGTH = 100;
    /** 采样温度：偏高以鼓励创意与多样性，不支持该参数的模型会自动忽略 */
    private static final double GENERATION_TEMPERATURE = 0.9;

    /**
     * 默认角色设定。与 settings.yaml 中 {@code aiSystemPrompt} 的默认值保持一致，
     * 仅在用户未配置自定义提示词时使用。
     */
    static final String DEFAULT_SYSTEM_PROMPT = """
        # 角色
        你是一位资深中文文学创作匠人，融合古典诗词的凝练、现代散文的流畅与哲学思辨的深度，擅长围绕任意主题锻造高质量的"一句话"作品。

        # 任务
        根据给定的主题与数量，创作优美且彼此独立的句子。每条句子需同时包含正文、作者、来源三个字段。

        # 创作准则
        1. 语言凝练：字字表意，剔除冗余修饰与口语化表达，单条正文控制在 8~40 字之间。
        2. 意象鲜活：调动通感、隐喻、拟人等修辞，营造可感知的画面感。
        3. 情感层次：不止于直白抒情，留有余韵与回味的空间。
        4. 节奏韵律：长短句交错，句内呼吸感自然，可朗读。
        5. 独立成句：每条句子独立完整，不依赖上下文，且彼此互不重复。
        6. 拒绝陈词滥调：避免网络流行语、常见鸡汤金句与对名言的直接改写。

        # 作者与来源
        - 原创：作者填"佚名"，来源填"原创"。
        - 化用：若化用经典，作者填原出处作者，来源填具体作品名（如《人间词话》）。
        - 不得凭空杜撰真实人物未说过的言论。

        # 输出
        严格输出 JSON 数组，每个元素包含 content（正文）、author（作者）、source（来源）三个字符串字段，数量与用户要求一致，不要附加任何解释性文字。
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        主题：%s
        请生成 %d 条优美句子。""";

    private final ExtensionGetter extensionGetter;
    private final ReactiveExtensionClient client;
    private final ObjectMapper objectMapper;

    /** AI 单条句子的结构化输出模型，用于派生 JSON Schema 与反序列化。 */
    public record AiSentenceOutput(String content, String author, String source) {
    }

    @Override
    public Mono<Void> sentencesGenerateAndSave(
        String modelName,
        String aiSystemPrompt,
        String topic,
        int count,
        String categoryName,
        boolean aiSentenceAutoPublish
    ) {
        String systemPrompt =
            StringUtils.hasText(aiSystemPrompt) ? aiSystemPrompt : DEFAULT_SYSTEM_PROMPT;
        String prompt = String.format(USER_PROMPT_TEMPLATE, topic, count);

        GenerateTextRequest request = GenerateTextRequest.builder()
            .system(systemPrompt)
            .prompt(prompt)
            .temperature(GENERATION_TEMPERATURE)
            .output(OutputSpec.array(AiSentenceOutput.class))
            .build();

        long startTime = System.currentTimeMillis();

        AiGenerateLog logEntry = new AiGenerateLog();
        logEntry.setMetadata(new Metadata());
        logEntry.getMetadata().setGenerateName("aiglog-");
        logEntry.setSpec(new AiGenerateLog.Spec());
        logEntry.getSpec().setModelName(modelName);
        logEntry.getSpec().setTopic(topic);
        logEntry.getSpec().setRequestCount(count);
        logEntry.getSpec().setCategoryName(categoryName);
        logEntry.getSpec().setAutoPublish(aiSentenceAutoPublish);
        logEntry.getSpec().setStatus(AiGenerateLog.Status.RUNNING);

        return client.create(logEntry)
            .flatMap(createdLog -> generateAndPersist(createdLog, request, startTime, count,
                categoryName, aiSentenceAutoPublish));
    }

    private Mono<Void> generateAndPersist(
        AiGenerateLog createdLog,
        GenerateTextRequest request,
        long startTime,
        int count,
        String categoryName,
        boolean autoPublish
    ) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .flatMap(server -> server.languageModel(createdLog.getSpec().getModelName()))
            .flatMap(model -> model.generateText(request))
            .flatMap(result -> parseSentences(result, count))
            .flatMap(sentences -> {
                try {
                    createdLog.getSpec().setGeneratedData(objectMapper.writeValueAsString(sentences));
                } catch (Exception e) {
                    log.warn("序列化 AI 生成结果失败", e);
                }
                return Mono.just(sentences);
            })
            .flatMapMany(Flux::fromIterable)
            .flatMap(output -> createSentence(output, categoryName, autoPublish))
            .collectList()
            .flatMap(results -> finalizeLog(createdLog, results, count, startTime))
            .onErrorResume(err -> failLog(createdLog, err, count, startTime));
    }

    private Mono<List<AiSentenceOutput>> parseSentences(GenerateTextResult result, int count) {
        // 优先使用结构化输出的原始文本，回退到最终助手文本
        String json = StringUtils.hasText(result.getOutputText())
            ? result.getOutputText() : result.getText();
        if (!StringUtils.hasText(json)) {
            return Mono.error(new RuntimeException("AI 未返回任何内容"));
        }
        if (json.length() > MAX_AI_RESPONSE_SIZE) {
            return Mono.error(new RuntimeException(
                "AI 输出超过大小限制: " + json.length() + " 字符"));
        }
        if (log.isDebugEnabled()) {
            log.debug("AI 返回 JSON 预览: {}", truncate(json, 500));
        }
        try {
            List<AiSentenceOutput> sentences =
                objectMapper.readValue(json, new TypeReference<>() {});
            // AI 可能返回超过设置数量的句子，只取设置的数量
            if (sentences.size() > count) {
                sentences = new ArrayList<>(sentences.subList(0, count));
            }
            return Mono.just(sentences);
        } catch (Exception e) {
            log.error("解析 AI 返回的 JSON 失败: {}", truncate(json, 500), e);
            return Mono.error(new RuntimeException("AI 输出格式不正确", e));
        }
    }

    private Mono<Sentence> createSentence(
        AiSentenceOutput output, String categoryName, boolean autoPublish
    ) {
        String content = output.content();
        if (!StringUtils.hasText(content)) {
            log.warn("AI 生成项缺少 content 字段，跳过");
            return Mono.empty();
        }
        Sentence sentence = new Sentence();
        sentence.setMetadata(new Metadata());
        sentence.getMetadata().setGenerateName("sentence-");
        sentence.getStatus().setPublished(autoPublish);
        sentence.setSpec(new Sentence.Spec());
        sentence.getSpec().setContent(truncate(content.strip(), MAX_CONTENT_LENGTH));
        sentence.getSpec().setAuthor(StringUtils.hasText(output.author())
            ? truncate(output.author().strip(), MAX_AUTHOR_LENGTH) : "匿名");
        sentence.getSpec().setSource(StringUtils.hasText(output.source())
            ? truncate(output.source().strip(), MAX_SOURCE_LENGTH) : "未知");
        sentence.getSpec().setCategoryName(categoryName);
        sentence.getSpec().setCreatedBy("AI");
        return client.create(sentence);
    }

    private Mono<Void> finalizeLog(
        AiGenerateLog logEntry, List<Sentence> results, int count, long startTime
    ) {
        long duration = System.currentTimeMillis() - startTime;
        logEntry.getSpec().setDurationMs(duration);

        int successCount = results.size();
        int failedCount = count - successCount;
        logEntry.getSpec().setSuccessCount(successCount);
        logEntry.getSpec().setFailedCount(Math.max(0, failedCount));

        if (failedCount == 0) {
            logEntry.getSpec().setStatus(AiGenerateLog.Status.SUCCESS);
        } else if (successCount == 0) {
            logEntry.getSpec().setStatus(AiGenerateLog.Status.FAILED);
        } else {
            logEntry.getSpec().setStatus(AiGenerateLog.Status.PARTIAL_SUCCESS);
        }
        return client.update(logEntry).then(Mono.empty());
    }

    private Mono<Void> failLog(
        AiGenerateLog logEntry, Throwable err, int count, long startTime
    ) {
        long duration = System.currentTimeMillis() - startTime;
        logEntry.getSpec().setDurationMs(duration);
        logEntry.getSpec().setSuccessCount(0);
        logEntry.getSpec().setFailedCount(count);
        logEntry.getSpec().setStatus(AiGenerateLog.Status.FAILED);
        logEntry.getSpec().setErrorMessage(describeError(err));
        return client.update(logEntry).then(Mono.error(err));
    }

    /** 将 SDK 异常转换为用户可读的错误信息，剥离底层细节。 */
    private static String describeError(Throwable err) {
        if (err instanceof StructuredOutputValidationException sve) {
            String path = sve.getValidationPath();
            return path != null ? "AI 输出结构校验失败（路径: " + path + "）"
                : "AI 输出结构校验失败";
        }
        if (err instanceof AiFoundationException) {
            return "AI 服务异常: " + err.getMessage();
        }
        return err.getMessage();
    }

    private static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
