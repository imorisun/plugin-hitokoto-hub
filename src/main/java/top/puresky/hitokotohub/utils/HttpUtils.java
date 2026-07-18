package top.puresky.hitokotohub.utils;

import org.jspecify.annotations.NonNull;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * HTTP 请求相关工具方法。
 *
 * <p>提取自 {@code SentencePublicEndpoint} 与 {@code SentenceSubmissionPublicEndpoint} 中
 * 逐字重复的 {@code getClientIp} 私有方法。
 */
public final class HttpUtils {

    private HttpUtils() {}

    /**
     * 获取客户端真实 IP 地址。
     *
     * <p>优先取 {@code X-Forwarded-For} 首段（兼容反向代理链）；若无则取 remoteAddress；
     * 若仍不可得返回 {@code "unknown"}。
     *
     * @param request 服务端请求
     * @return 客户端 IP 字符串，不会为 null
     */
    public static @NonNull String getClientIp(@NonNull ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
            ? request.getRemoteAddress().getAddress().getHostAddress()
            : "unknown";
    }
}
