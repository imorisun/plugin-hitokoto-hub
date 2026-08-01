package top.puresky.hitokotohub.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.schema.JsonSchema;
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

    /** AI 返回 JSON 大小上限(512KB),防止解析 OOM */
    private static final int MAX_AI_RESPONSE_SIZE = 512 * 1024;
    /** 单条 AI 生成句子内容长度上限,与 SentenceConsoleEndpoint.MAX_CONTENT_LENGTH 一致 */
    private static final int MAX_SENTENCE_CONTENT_LENGTH = 500;

    private final ExtensionGetter extensionGetter;
    private final ReactiveExtensionClient client;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> sentencesGenerateAndSave(
        String modelName,
        String aiSystemPrompt,
        String topic,
        int count,
        String categoryName,
        boolean aiSentenceAutoPublish
    ) {

        // AI输出的单条格式（保持不变）
        Map<String, Object> sentenceOutputSchema = JsonSchema.object()
            .property("content", JsonSchema.string())
            .property("author", JsonSchema.string())
            .property("source", JsonSchema.string())
            .required("content", "author", "source")
            .build().toMap();

        String DEFAULT_SYSTEM_PROMPT = """
            # 角色设定
            你是一位精通中文文学与美学的文字匠人，擅长以古典诗词的凝练、现代散文的流畅、以及哲学思辨的深度来锻造句子。

            ## 核心指令
            当用户给出一个主题、关键词、情境或情感时，你需要生成用户所要求的相对应数量的优美句子。每个句子必须：
            1. 语言凝练：剔除冗余，每个字都承担表意或节奏功能
            2. 意象鲜活：调动视觉、听觉、触觉、嗅觉、味觉等通感进行描写（可选）
            3. 情感有层次：不止于表面抒情，蕴含可回味的余韵
            4. 节奏有韵律：长短句交错，句内呼吸感自然


            ## 修辞要求
            - 通感、隐喻/暗喻、拟人化、矛盾修辞、时空折叠、微观放大

            现在，请根据用户输入生成优美句子。
            """;

        String systemPrompt =
            StringUtils.hasText(aiSystemPrompt) ? aiSystemPrompt : DEFAULT_SYSTEM_PROMPT;

        String USER_TEMPLATE = "请围绕以下主题生成%d条优美句子：%s";
        String prompt = String.format(USER_TEMPLATE, count, topic);

        GenerateTextRequest request = GenerateTextRequest.builder()
            .system(systemPrompt)
            .prompt(prompt)
            .output(OutputSpec.array(sentenceOutputSchema))
            .build();

        long startTime = System.currentTimeMillis();

        // 先创建一条 RUNNING 日志
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
            .flatMap(createdLog -> extensionGetter.getEnabledExtension(AiModelService.class)
                .flatMap(server -> server.languageModel(modelName))
                .flatMap(model -> model.generateText(request))
                .map(r -> r.getText())
                .doOnNext(json -> {
                    if (json.length() > MAX_AI_RESPONSE_SIZE) {
                        log.warn("AI 返回内容过大: {} 字符,超过限制 {}", json.length(), MAX_AI_RESPONSE_SIZE);
                    } else if (log.isDebugEnabled()) {
                        String preview = json.length() > 500
                            ? json.substring(0, 500) + "...(truncated)"
                            : json;
                        log.debug("AI 返回 JSON 预览: {}", preview);
                    }
                })
                .flatMap(json -> {
                    if (json.length() > MAX_AI_RESPONSE_SIZE) {
                        return Mono.<List<Map<String, Object>>>error(
                            new RuntimeException("AI 输出超过大小限制: " + json.length() + " 字符"));
                    }
                    try {
                        List<Map<String, Object>> sentenceList = objectMapper.readValue(json, new TypeReference<>() {});
                        // AI可能返回超过设置数量的句子，只取设置的数量
                        if (sentenceList.size() > count) {
                            sentenceList = sentenceList.subList(0, count);
                        }
                        // 保存AI生成的源数据，供管理员查看
                        createdLog.getSpec().setGeneratedData(objectMapper.writeValueAsString(sentenceList));
                        return Mono.just(sentenceList);
                    } catch (Exception e) {
                        log.error("解析AI返回的JSON失败", e);
                        return Mono.error(new RuntimeException("AI输出格式不正确", e));
                    }
                })
                .flatMapMany(Flux::fromIterable)
                .flatMap(map -> {
                    String content = (String) map.get("content");
                    if (content == null || content.isBlank()) {
                        log.warn("AI 生成项缺少 content 字段,跳过");
                        return Mono.<Sentence>empty();
                    }
                    if (content.length() > MAX_SENTENCE_CONTENT_LENGTH) {
                        content = content.substring(0, MAX_SENTENCE_CONTENT_LENGTH);
                    }
                    Sentence sentence = new Sentence();
                    sentence.setMetadata(new Metadata());
                    sentence.setSpec(new Sentence.Spec());
                    sentence.getMetadata().setGenerateName("sentence-");
                    sentence.getStatus().setPublished(aiSentenceAutoPublish);

                    sentence.getSpec().setContent(content);
                    Object authorObj = map.get("author");
                    sentence.getSpec().setAuthor(authorObj != null ? String.valueOf(authorObj) : "匿名");
                    Object sourceObj = map.get("source");
                    sentence.getSpec().setSource(sourceObj != null ? String.valueOf(sourceObj) : "未知");
                    sentence.getSpec().setCategoryName(categoryName);
                    sentence.getSpec().setCreatedBy("AI");

                    return client.create(sentence);
                })
                .collectList()
                .map(results -> {
                    long duration = System.currentTimeMillis() - startTime;
                    createdLog.getSpec().setDurationMs(duration);

                    int successCount = results.size();
                    int failedCount = count - successCount;
                    createdLog.getSpec().setSuccessCount(successCount);
                    createdLog.getSpec().setFailedCount(Math.max(0, failedCount));

                    if (failedCount == 0) {
                        createdLog.getSpec().setStatus(AiGenerateLog.Status.SUCCESS);
                    } else if (successCount == 0) {
                        createdLog.getSpec().setStatus(AiGenerateLog.Status.FAILED);
                    } else {
                        createdLog.getSpec().setStatus(AiGenerateLog.Status.PARTIAL_SUCCESS);
                    }

                    return createdLog;
                })
                .onErrorResume(err -> {
                    long duration = System.currentTimeMillis() - startTime;
                    createdLog.getSpec().setDurationMs(duration);
                    createdLog.getSpec().setSuccessCount(0);
                    createdLog.getSpec().setFailedCount(count);
                    createdLog.getSpec().setStatus(AiGenerateLog.Status.FAILED);
                    createdLog.getSpec().setErrorMessage(err.getMessage());
                    return client.update(createdLog).then(Mono.error(err));
                })
                .flatMap(finalLog -> client.update(finalLog).then(Mono.empty()))
            );
    }
}
