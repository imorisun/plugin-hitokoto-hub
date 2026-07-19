package top.puresky.hitokotohub.service.dto;

/**
 * 单个分类的句子统计信息。
 *
 * <p>由 {@link top.puresky.hitokotohub.service.CategoryCountService#getCategoryStats()}
 * 通过单次 {@code listAll(Sentence)} + 内存分组得到,避免 N+1 查询。
 *
 * @param total     该分类下句子总数(未删除)
 * @param published 该分类下已发布句子数
 */
public record CategoryStats(long total, long published) {

    /** 返回未发布句子数 = total - published。 */
    public long notPublished() {
        return total - published;
    }
}
