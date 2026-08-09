package top.puresky.hitokotohub.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 句子分享数据载荷，公开接口与控制台接口共用。
 *
 * <p>分享链接由前端基于当前页面地址拼装（{@code location.origin + sharePath}），
 * 后端不感知站点域名，避免反向代理 / 主题路径导致的链接失真。
 */
@Data
@Schema(name = "SharePayload")
public class SharePayload {

    @Schema(description = "句子 metadata.name")
    private String name;

    @Schema(description = "句子内容")
    private String content;

    @Schema(description = "作者")
    private String author;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "分类名称（metadata.name）")
    private String categoryName;

    @Schema(description = "分类显示名")
    private String categoryDisplayName;

    @Schema(description = "点赞数")
    private long likeCount;

    @Schema(description = "浏览数")
    private long viewCount;

    @Schema(description = "相对分享路径，如 /hitokoto?sentence=xxx")
    private String sharePath;

    @Schema(description = "站点名称")
    private String siteName;

    @Schema(description = "分享创建时间戳（毫秒）")
    private long createdAt;
}
