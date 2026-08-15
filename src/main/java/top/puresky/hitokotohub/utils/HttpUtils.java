package top.puresky.hitokotohub.utils;

import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * HTTP 请求相关工具方法。
 *
 * <p>提取自 {@code SentencePublicEndpoint} 与 {@code SentenceSubmissionPublicEndpoint} 中
 * 逐字重复的 {@code getClientIp} 私有方法。
 *
 * <h2>关于 X-Forwarded-For 的信任边界</h2>
 * <p>该头由客户端原样携带，可被任意伪造；只有当 Halo 部署在反向代理（Nginx / CDN 等）
 * 之后、且代理会覆盖或追加该头时，首段才是真实客户端 IP。因此：
 * <ul>
 *   <li>仅当插件设置「基本设置 → 信任反向代理头」开启时才会读取该头（默认开启，
 *       兼容既有反代部署）；Halo 直连公网时建议关闭。</li>
 *   <li>无论是否信任，取值都会经过严格格式校验（合法 IPv4 / IPv6 字面量），
 *       拒绝畸形值，防止脏数据进入缓存 key 与持久化记录。</li>
 * </ul>
 */
public final class HttpUtils {

    private HttpUtils() {}

    /** 严格 IPv4 格式：四段 0-255 的十进制数字 */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    /** X-Forwarded-For 值长度上限（IPv6 最长 45 字符，留出余量） */
    private static final int MAX_IP_LENGTH = 64;

    /**
     * 获取客户端真实 IP 地址。
     *
     * <p>等价于 {@code getClientIp(request, true)}。
     *
     * @param request 服务端请求
     * @return 客户端 IP 字符串，不会为 null
     */
    public static @NonNull String getClientIp(@NonNull ServerHttpRequest request) {
        return getClientIp(request, true);
    }

    /**
     * 获取客户端真实 IP 地址。
     *
     * <p>优先取 {@code X-Forwarded-For} 首段（兼容反向代理链，且仅接受合法 IP 字面量）；
     * 若无或未开启信任则取 remoteAddress；若仍不可得返回 {@code "unknown"}。
     *
     * @param request           服务端请求
     * @param trustProxyHeaders 是否信任反向代理转发头（见类注释的信任边界说明）
     * @return 客户端 IP 字符串，不会为 null
     */
    public static @NonNull String getClientIp(@NonNull ServerHttpRequest request,
        boolean trustProxyHeaders) {
        if (trustProxyHeaders) {
            String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                String firstHop = forwardedFor.split(",")[0].trim();
                if (isValidIp(firstHop)) {
                    return firstHop;
                }
            }
        }
        String remoteIp = getRemoteIp(request);
        return remoteIp != null ? remoteIp : "unknown";
    }

    private static String getRemoteIp(ServerHttpRequest request) {
        return request.getRemoteAddress() != null
            ? request.getRemoteAddress().getAddress().getHostAddress()
            : null;
    }

    /**
     * 校验字符串是否为合法 IP 字面量（IPv4 / IPv6）。
     *
     * <p>注意：IP 仅作为缓存 key 与统计字段使用，不参与网络解析，因此 IPv6 仅做
     * 语法级校验（字符集合法且含冒号），不调用可能触发 DNS 的解析 API。
     */
    private static boolean isValidIp(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return false;
        }
        if (IPV4_PATTERN.matcher(value).matches()) {
            return true;
        }
        // IPv6 字面量语法级校验：允许十六进制、冒号、IPv4 映射的点、区域标识 %
        if (!value.contains(":")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
            if (!(hex || ch == ':' || ch == '.' || ch == '%')) {
                return false;
            }
        }
        return true;
    }
}
