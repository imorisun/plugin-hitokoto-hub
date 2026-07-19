package top.puresky.hitokotohub.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 批量删除非最优句子的结果。
 *
 * <p>字段命名与 {@code SentenceConsoleEndpoint.BatchCreateSentenceResult} 的
 * total/success/failed 三元结构对齐，并保留前端依赖的 {@code deleted} / {@code message}。
 *
 * <p>使用场景：
 * <ul>
 *   <li>业务正常路径（无日志 / 无句子 / 无非最优候选）→ {@link #empty(String)}</li>
 *   <li>有候选删除 → {@link #of(int, int, int)}，自动生成汇总消息</li>
 *   <li>端点层异常兜底 → 返回字段结构一致的 Map（不使用本 record）</li>
 * </ul>
 *
 * @param total    候选删除总数
 * @param deleted  成功删除数
 * @param failed   失败数（含未找到、删除异常）
 * @param message  汇总消息（前端 toast 显示）
 */
@Schema(name = "BatchDeleteResult")
public record BatchDeleteResult(
    @Schema(description = "候选删除总数") int total,
    @Schema(description = "成功删除数") int deleted,
    @Schema(description = "失败数（含未找到）") int failed,
    @Schema(description = "汇总消息") String message
) {

    /** 创建空结果（无 SUCCESS 日志 / 无句子 / 无非最优候选时使用）。 */
    public static BatchDeleteResult empty(String message) {
        return new BatchDeleteResult(0, 0, 0, message);
    }

    /** 创建有数据的结果，自动生成汇总消息。 */
    public static BatchDeleteResult of(int total, int deleted, int failed) {
        return new BatchDeleteResult(total, deleted, failed,
            String.format("批量删除完成：候选 %d 个，成功 %d 个，失败 %d 个",
                total, deleted, failed));
    }
}
