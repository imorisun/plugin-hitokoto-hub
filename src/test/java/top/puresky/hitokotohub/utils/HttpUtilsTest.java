package top.puresky.hitokotohub.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

/**
 * {@link HttpUtils} 单元测试。
 *
 * <p>重点覆盖 X-Forwarded-For 的信任开关与严格格式校验：
 * 合法 IP 才被采纳，畸形值回退直连地址，防止脏数据进入限流缓存 key。
 */
class HttpUtilsTest {

    private MockServerHttpRequest.BaseBuilder<?> request() {
        return MockServerHttpRequest.get("/x")
            .remoteAddress(new InetSocketAddress("192.0.2.10", 12345));
    }

    @Test
    void trustEnabledShouldUseFirstForwardedHop() {
        var request = request()
            .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
            .build();
        assertEquals("203.0.113.9", HttpUtils.getClientIp(request, true));
    }

    @Test
    void trustDisabledShouldIgnoreForwardedHeader() {
        var request = request()
            .header("X-Forwarded-For", "203.0.113.9")
            .build();
        assertEquals("192.0.2.10", HttpUtils.getClientIp(request, false));
    }

    @Test
    void defaultShouldTrustForwardedHeaderForBackwardCompatibility() {
        var request = request()
            .header("X-Forwarded-For", "203.0.113.9")
            .build();
        assertEquals("203.0.113.9", HttpUtils.getClientIp(request));
    }

    @Test
    void malformedForwardedValueShouldFallbackToRemoteAddress() {
        var request = request()
            .header("X-Forwarded-For", "<script>alert(1)</script>")
            .build();
        assertEquals("192.0.2.10", HttpUtils.getClientIp(request, true));
    }

    @Test
    void invalidIpv4ShouldFallbackToRemoteAddress() {
        var request = request()
            .header("X-Forwarded-For", "999.1.1.1")
            .build();
        assertEquals("192.0.2.10", HttpUtils.getClientIp(request, true));
    }

    @Test
    void hostnameInForwardedHeaderShouldNotBeTrusted() {
        var request = request()
            .header("X-Forwarded-For", "evil.example.com")
            .build();
        assertEquals("192.0.2.10", HttpUtils.getClientIp(request, true));
    }

    @Test
    void validIpv6ShouldBeAccepted() {
        var request = request()
            .header("X-Forwarded-For", "2001:db8::1")
            .build();
        assertEquals("2001:db8::1", HttpUtils.getClientIp(request, true));
    }

    @Test
    void blankForwardedHeaderShouldFallbackToRemoteAddress() {
        var request = request()
            .header("X-Forwarded-For", "   ")
            .build();
        assertEquals("192.0.2.10", HttpUtils.getClientIp(request, true));
    }

    @Test
    void missingRemoteAndHeaderShouldReturnUnknown() {
        var request = MockServerHttpRequest.get("/x").build();
        assertEquals("unknown", HttpUtils.getClientIp(request, true));
    }

    @Test
    void oversizedForwardedValueShouldBeRejected() {
        String huge = "9".repeat(100);
        var request = request().header("X-Forwarded-For", huge).build();
        String result = HttpUtils.getClientIp(request, true);
        assertFalse(huge.equals(result), "超长值不应被当作合法 IP 采纳");
    }

    @Test
    void multiHopChainShouldTakeFirstHopOnly() {
        var request = request()
            .header("X-Forwarded-For", "203.0.113.9,192.0.2.10,10.0.0.2")
            .build();
        String result = HttpUtils.getClientIp(request, true);
        assertEquals("203.0.113.9", result);
        assertTrue(!result.contains(","));
    }
}
