package top.puresky.hitokotohub.service;

import reactor.core.publisher.Mono;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;

/**
 * 句子相似度检查服务
 */
public interface SimilarityCheckService {

    /**
     * 执行相似度检查并保存检查日志
     *
     * @param triggerType 触发类型（MANUAL / SCHEDULED）
     * @param triggeredBy 触发者
     * @param algorithm   算法（COSINE / JACCARD）
     * @param threshold   相似度阈值
     * @return 检查日志
     */
    Mono<SimilarityCheckLog> performCheck(
        SimilarityCheckLog.TriggerType triggerType,
        String triggeredBy,
        String algorithm,
        double threshold
    );
}
