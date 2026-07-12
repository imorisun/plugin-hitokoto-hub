package top.puresky.hitokotohub.service;

import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.extension.SimilarityGroup;

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

    /**
     * 获取分组后的相似度结果（分页）
     *
     * @param page 页码
     * @param size 每页数量
     * @return 包含分组列表和分页信息的 Map
     */
    Mono<Map<String, Object>> getGroups(int page, int size);

    /**
     * 批量删除每个相似组中的非最优句子
     *
     * @return 删除的句子数量
     */
    Mono<Integer> deleteNonOptimalSentences();
}
