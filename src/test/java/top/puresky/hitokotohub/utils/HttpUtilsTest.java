package top.puresky.hitokotohub.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * {@link HttpUtils} 单元测试。
 */
@DisplayName("HttpUtils getClientIp")
class HttpUtilsTest {

    @Test
    @DisplayName("X-Forwarded-For 存在时取首段 IP")
    void shouldReturnFirstIpFromXForwardedFor() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.5, 70.41.3.18, 150.172.238.178");
        when(request.getHeaders()).thenReturn(headers);

        String ip = HttpUtils.getClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("X-Forwarded-For 单个 IP 时直接返回")
    void shouldReturnSingleIpFromXForwardedFor() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "10.0.0.1");
        when(request.getHeaders()).thenReturn(headers);

        String ip = HttpUtils.getClientIp(request);

        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("X-Forwarded-For 为空白时回退到 remoteAddress")
    void shouldFallbackToRemoteAddressWhenXForwardedForBlank() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "  ");
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress())
            .thenReturn(new InetSocketAddress("192.168.1.100", 8080));

        String ip = HttpUtils.getClientIp(request);

        assertThat(ip).isEqualTo("192.168.1.100");
    }

    @Test
    @DisplayName("无 X-Forwarded-For 且无 remoteAddress 时返回 unknown")
    void shouldReturnUnknownWhenNoXForwardedForAndNoRemoteAddress() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getRemoteAddress()).thenReturn(null);

        String ip = HttpUtils.getClientIp(request);

        assertThat(ip).isEqualTo("unknown");
    }

    @Test
    @DisplayName("X-Forwarded-For 带前后空格时 trim")
    void shouldTrimXForwardedFor() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "  203.0.113.5  ");
        when(request.getHeaders()).thenReturn(headers);

        String ip = HttpUtils.getClientIp(request);

        assertThat(ip).isEqualTo("203.0.113.5");
    }
}
