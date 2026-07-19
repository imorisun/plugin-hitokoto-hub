package top.puresky.hitokotohub.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.extension.Category;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.support.MockExtensionClient;
import top.puresky.hitokotohub.support.TestFixtures;

/**
 * {@link SentencePublicEndpoint} 单元测试。
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>{@code getRandomSentences}：默认 JSON 返回、encode=text 纯文本、categoryName 过滤</li>
 *   <li>{@code toggleLike}：首次点赞成功、重复点赞 rate_limited、句子不存在 not_found</li>
 * </ul>
 *
 * <p>Mock 策略：
 * <ul>
 *   <li>{@code ReactiveExtensionClient}：使用 {@link MockExtensionClient#builder()} 内存存储</li>
 *   <li>{@code SettingConfig}：纯 Mockito mock，{@code getBasicConfig} 返回固定 BasicConfig</li>
 *   <li>{@code ServerRequest}：手工 Mockito mock（避免依赖 spring-test 的 MockServerHttpRequest）</li>
 * </ul>
 *
 * <p>说明：{@code toggleLike} 内部的 {@code likeCache} 是实例字段，B2 测试通过同一 endpoint
 * 实例连续两次调用以触发 {@code rate_limited} 路径。B3 使用纯 mock 而非 MockExtensionClient，
 * 因为后者对不存在的 name 返回 {@code Mono.error}，不触发 endpoint 的 {@code defaultIfEmpty}。
 */
@DisplayName("SentencePublicEndpoint 单元测试")
class SentencePublicEndpointTest {

    // ==================== getRandomSentences ====================

    @Test
    @DisplayName("getRandomSentences：默认参数 → 返回 JSON 响应，不触发浏览量统计")
    void getRandomSentences_defaultParams_returnJsonResponse() {
        Sentence s1 = TestFixtures.sentence("s1", "内容1", true);
        Sentence s2 = TestFixtures.sentence("s2", "内容2", true);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1)
            .with(s2)
            .build();

        SettingConfig config = mockSettingConfig(basicConfig());
        ServerRequest request = mockRandomRequest(null, null, null);
        SentencePublicEndpoint endpoint = new SentencePublicEndpoint(config, client);

        StepVerifier.create(endpoint.getRandomSentences(request))
            .assertNext(response -> {
                assertThat(response.statusCode().is2xxSuccessful()).isTrue();
            })
            .verifyComplete();

        verify(client, times(1)).listBy(any(), any(), any());
        // enableViewCount=false，不应触发 update
        verify(client, never()).update(any());
    }

    @Test
    @DisplayName("getRandomSentences：encode=text → 返回纯文本（每行一句）")
    void getRandomSentences_encodeText_returnPlainText() {
        Sentence s1 = TestFixtures.sentence("s1", "内容1", true);
        Sentence s2 = TestFixtures.sentence("s2", "内容2", true);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1)
            .with(s2)
            .build();

        SettingConfig.BasicConfig cfg = basicConfig();
        cfg.setEncode("text");
        SettingConfig config = mockSettingConfig(cfg);

        ServerRequest request = mockRandomRequest(null, null, "text");
        SentencePublicEndpoint endpoint = new SentencePublicEndpoint(config, client);

        StepVerifier.create(endpoint.getRandomSentences(request))
            .assertNext(response -> {
                assertThat(response.statusCode().is2xxSuccessful()).isTrue();
            })
            .verifyComplete();

        verify(client, atLeast(1)).listBy(any(), any(), any());
    }

    @Test
    @DisplayName("getRandomSentences：指定 categoryName → 调用 fetch(Category.class, name) 获取显示名")
    void getRandomSentences_withCategoryName_appliesFilter() {
        Sentence s1 = TestFixtures.sentence("s1", "内容1", "cat-a", "匿名", "未知", true, 0, 0);
        Sentence s2 = TestFixtures.sentence("s2", "内容2", "cat-b", "匿名", "未知", true, 0, 0);
        Category catA = TestFixtures.category("cat-a", "分类A");

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1)
            .with(s2)
            .with(catA)
            .build();

        SettingConfig config = mockSettingConfig(basicConfig());
        ServerRequest request = mockRandomRequest("cat-a", null, null);
        SentencePublicEndpoint endpoint = new SentencePublicEndpoint(config, client);

        StepVerifier.create(endpoint.getRandomSentences(request))
            .assertNext(response -> {
                assertThat(response.statusCode().is2xxSuccessful()).isTrue();
            })
            .verifyComplete();

        // getDisplayName 路径会调用 fetch(Category.class, "cat-a")
        verify(client, atLeast(1)).fetch(eq(Category.class), eq("cat-a"));
    }

    // ==================== toggleLike ====================

    @Test
    @DisplayName("toggleLike：首次点赞 → likeCount+1，调用 client.update")
    void toggleLike_normalLike_likeCountIncremented() {
        Sentence s = TestFixtures.sentence("s1", "内容", true, 5, 10);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s)
            .build();

        SettingConfig config = mockSettingConfig(basicConfig());
        ServerRequest request = mockLikeRequest("127.0.0.1", "s1", "like");
        SentencePublicEndpoint endpoint = new SentencePublicEndpoint(config, client);

        StepVerifier.create(endpoint.toggleLike(request))
            .assertNext(response -> {
                assertThat(response.statusCode().is2xxSuccessful()).isTrue();
            })
            .verifyComplete();

        ArgumentCaptor<Sentence> captor = ArgumentCaptor.forClass(Sentence.class);
        verify(client, times(1)).update(captor.capture());
        // 原 likeCount=5，+1 后应为 6
        assertThat(captor.getValue().getStatus().getLikeCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("toggleLike：同一 IP+name 第二次点赞 → 触发 rate_limited，不再 update")
    void toggleLike_duplicateLike_returnsRateLimited() {
        Sentence s = TestFixtures.sentence("s1", "内容", true, 5, 10);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s)
            .build();

        SettingConfig config = mockSettingConfig(basicConfig());
        ServerRequest request = mockLikeRequest("127.0.0.1", "s1", "like");

        // 必须使用同一 endpoint 实例，保证 likeCache 共享
        SentencePublicEndpoint endpoint = new SentencePublicEndpoint(config, client);

        // 第一次点赞：成功，likeCache 填入
        endpoint.toggleLike(request).block();

        // 第二次点赞：触发 rate_limited 路径
        StepVerifier.create(endpoint.toggleLike(request))
            .assertNext(response -> {
                assertThat(response.statusCode().is2xxSuccessful()).isTrue();
            })
            .verifyComplete();

        // 第二次不应再调用 update（rate_limited 分支只读不写）
        verify(client, times(1)).update(any());
    }

    @Test
    @DisplayName("toggleLike：句子不存在 → 返回 code=not_found")
    void toggleLike_sentenceNotFound_returnsNotFound() {
        // 使用纯 Mockito mock：get 返回 Mono.empty() 以触发 endpoint 的 defaultIfEmpty(buildErrorResponse())
        // （MockExtensionClient.get 对不存在 name 返回 Mono.error，不会触发 defaultIfEmpty）
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.get(eq(Sentence.class), anyString())).thenReturn(Mono.empty());

        SettingConfig config = mockSettingConfig(basicConfig());
        ServerRequest request = mockLikeRequest("127.0.0.1", "nonexistent", "like");
        SentencePublicEndpoint endpoint = new SentencePublicEndpoint(config, client);

        StepVerifier.create(endpoint.toggleLike(request))
            .assertNext(response -> {
                assertThat(response.statusCode().is2xxSuccessful()).isTrue();
            })
            .verifyComplete();

        // 句子不存在，不应触发 update
        verify(client, never()).update(any());
    }

    // ==================== 辅助方法 ====================

    /** 构造默认 BasicConfig 测试夹具（randomLimit=1, maxRandomLimit=10, encode=json, enableViewCount=false）。 */
    private static SettingConfig.BasicConfig basicConfig() {
        SettingConfig.BasicConfig c = new SettingConfig.BasicConfig();
        c.setMaxRandomLimit(10);
        c.setRandomLimit(1);
        c.setEncode("json");
        c.setDefaultCategory(List.of());
        c.setLikeCooldown(1);
        c.setEnableViewCount(false);
        return c;
    }

    /** mock SettingConfig，getBasicConfig 返回固定 BasicConfig。 */
    private static SettingConfig mockSettingConfig(SettingConfig.BasicConfig basicConfig) {
        SettingConfig config = mock(SettingConfig.class);
        when(config.getBasicConfig()).thenReturn(Mono.just(basicConfig));
        return config;
    }

    /** 构造 getRandomSentences 的 ServerRequest mock（queryParam 全可控）。 */
    private static ServerRequest mockRandomRequest(String categoryName, String limit, String encode) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("categoryName")).thenReturn(Optional.ofNullable(categoryName));
        when(request.queryParam("limit")).thenReturn(Optional.ofNullable(limit));
        when(request.queryParam("encode")).thenReturn(Optional.ofNullable(encode));
        return request;
    }

    /**
     * 构造带 {@code X-Forwarded-For} 头的 ServerRequest mock（用于 toggleLike 取 IP）。
     *
     * <p>{@code HttpUtils.getClientIp} 通过 {@code request.exchange().getRequest().getHeaders().getFirst("X-Forwarded-For")}
     * 取 IP；这里手工 mock 全链路。
     */
    private static ServerRequest mockLikeRequest(String ip, String name, String action) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("name")).thenReturn(Optional.of(name));
        when(request.queryParam("action")).thenReturn(Optional.ofNullable(action));

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest httpRequest = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", ip);
        when(httpRequest.getHeaders()).thenReturn(headers);
        when(exchange.getRequest()).thenReturn(httpRequest);
        when(request.exchange()).thenReturn(exchange);
        return request;
    }
}
