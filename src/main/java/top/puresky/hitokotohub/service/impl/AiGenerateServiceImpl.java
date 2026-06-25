package top.puresky.hitokotohub.service.impl;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import run.halo.aifoundation.schema.JsonSchema;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import com.fasterxml.jackson.core.type.TypeReference;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.service.AiGenerateService;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "run.halo.aifoundation.AiModelService")
public class AiGenerateServiceImpl implements AiGenerateService {

    private final ExtensionGetter extensionGetter;
    private final ReactiveExtensionClient client;
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

        return extensionGetter.getEnabledExtension(AiModelService.class)
            .flatMap(server -> server.languageModel(modelName))
            .flatMap(model -> model.generateText(request))
            .map(GenerateTextResult::getText)
            .doOnNext(json -> {
                log.info("========== AI 返回的原始 JSON ==========");
                log.info(json);
                log.info("=========================================");
            })
            .flatMap(json -> {
                try {
                    List<Map<String, Object>> sentenceList =
                        objectMapper.readValue(json, new TypeReference<>() {
                        });
                    return Mono.just(sentenceList);
                } catch (Exception e) {
                    log.error("解析AI返回的JSON失败", e);
                    return Mono.error(new RuntimeException("AI输出格式不正确", e));
                }
            })
            .flatMapMany(Flux::fromIterable)
            .flatMap(map -> {
                Sentence sentence = new Sentence();
                sentence.setMetadata(new Metadata());
                sentence.setSpec(new Sentence.Spec());
                sentence.getMetadata().setGenerateName("sentence-");
                sentence.getStatus().setPublished(aiSentenceAutoPublish);

                sentence.getSpec().setContent((String) map.get("content"));
                sentence.getSpec().setAuthor((String) map.get("author"));
                sentence.getSpec().setSource((String) map.get("source"));
                sentence.getSpec().setCategoryName(categoryName);
                sentence.getSpec().setCreatedBy("AI");

                return client.create(sentence);
            })
            .then();
    }
}