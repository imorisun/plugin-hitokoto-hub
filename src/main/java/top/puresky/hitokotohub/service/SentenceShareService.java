package top.puresky.hitokotohub.service;

import reactor.core.publisher.Mono;
import top.puresky.hitokotohub.service.dto.SharePayload;

/**
 * 句子分享服务：构建分享数据载荷与生成分享卡片。
 *
 * <p>区分两种调用场景：
 * <ul>
 *   <li>{@code requirePublished=true}：游客（匿名）通过公开接口分享，仅允许已发布句子；</li>
 *   <li>{@code requirePublished=false}：管理员通过控制台接口分享任意指定的句子。</li>
 * </ul>
 */
public interface SentenceShareService {

    /**
     * 构建分享数据载荷。
     *
     * @param sentenceName      句子 metadata.name
     * @param requirePublished  是否仅允许已发布句子
     * @return 分享数据载荷；句子不存在（或未发布且要求发布）时为空
     */
    Mono<SharePayload> buildSharePayload(String sentenceName, boolean requirePublished);

    /**
     * 生成分享卡片 SVG。
     *
     * @param sentenceName      句子 metadata.name
     * @param requirePublished  是否仅允许已发布句子
     * @return SVG 字符串；句子不存在（或未发布且要求发布）时为空
     */
    Mono<String> buildShareCardSvg(String sentenceName, boolean requirePublished);
}
