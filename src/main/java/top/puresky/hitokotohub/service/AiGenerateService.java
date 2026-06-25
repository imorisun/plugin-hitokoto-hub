package top.puresky.hitokotohub.service;

import reactor.core.publisher.Mono;

public interface AiGenerateService {
    /**
     * 生成并保存 AI 句子
     * @param modelName     AI 模型内部名称（如 "gpt-4"）
     * @param aiSystemPrompt  角色设定
     * @param topic          生成风格（如 "幽默"、"温暖"）
     * @param count         生成数量
     * @param categoryName  目标分类 metadata.name（必须存在）
     * @param aiSentenceAutoPublish      是否自动发布
     * @return Mono<Void>
     */
    Mono<Void> sentencesGenerateAndSave(String modelName, String aiSystemPrompt, String topic , int count,
        String categoryName, boolean aiSentenceAutoPublish);
}