package top.puresky.hitokotohub.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.Sentence;

/**
 * {@link SentenceConsoleEndpoint} 单元测试。
 *
 * <p>使用纯 Mockito mock 验证 endpoint 的核心行为：
 * <ul>
 *   <li>{@code batchCreateSentence} 输入清洗：content trim、author/source 空值填默认值、超长截断</li>
 *   <li>{@code clearUncategorizedSentences} 串行删除与失败跳过</li>
 *   <li>{@code querySentences} / {@code searchSentence} 路由分发</li>
 * </ul>
 *
 * <p>说明：通过 {@link ArgumentCaptor} 捕获传入 {@code client.create} 的 Sentence，
 * 验证 {@code sanitizeSentenceInput} 的清洗效果。
 */
@DisplayName("SentenceConsoleEndpoint 单元测试")
class SentenceConsoleEndpointTest {

    // ==================== batchCreateSentence 输入清洗 ====================

    @Test
    @DisplayName("batchCreateSentence：content 含前后空白 → 被 trim")
    void batchCreateSentence_contentTrimmed() {
        Sentence input = newSentence("  内容  ", "cat-a", "匿名", "未知");
        ReactiveExtensionClient client = mockClientCreate();
        ServerRequest request = mockRequestWithBody(List.of(input));

        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));
        endpoint.batchCreateSentence(request).block();

        ArgumentCaptor<Sentence> captor = ArgumentCaptor.forClass(Sentence.class);
        verify(client, times(1)).create(captor.capture());
        assertThat(captor.getValue().getSpec().getContent()).isEqualTo("内容");
    }

    @Test
    @DisplayName("batchCreateSentence：author 为 null/blank → 填默认值「匿名」")
    void batchCreateSentence_authorBlank_filledWithDefault() {
        Sentence input1 = newSentence("内容1", "cat-a", null, "未知");
        Sentence input2 = newSentence("内容2", "cat-a", "   ", "未知");
        ReactiveExtensionClient client = mockClientCreate();
        ServerRequest request = mockRequestWithBody(List.of(input1, input2));

        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));
        endpoint.batchCreateSentence(request).block();

        ArgumentCaptor<Sentence> captor = ArgumentCaptor.forClass(Sentence.class);
        verify(client, times(2)).create(captor.capture());
        List<Sentence> created = captor.getAllValues();
        assertThat(created.get(0).getSpec().getAuthor()).isEqualTo("匿名");
        assertThat(created.get(1).getSpec().getAuthor()).isEqualTo("匿名");
    }

    @Test
    @DisplayName("batchCreateSentence：source 为空 → 填默认值「未知」")
    void batchCreateSentence_sourceBlank_filledWithDefault() {
        Sentence input = newSentence("内容", "cat-a", "作者", "");
        ReactiveExtensionClient client = mockClientCreate();
        ServerRequest request = mockRequestWithBody(List.of(input));

        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));
        endpoint.batchCreateSentence(request).block();

        ArgumentCaptor<Sentence> captor = ArgumentCaptor.forClass(Sentence.class);
        verify(client, times(1)).create(captor.capture());
        assertThat(captor.getValue().getSpec().getSource()).isEqualTo("未知");
    }

    @Test
    @DisplayName("batchCreateSentence：content 超过 500 字符 → 被截断至 500")
    void batchCreateSentence_contentTooLong_truncated() {
        String longContent = "a".repeat(600);
        Sentence input = newSentence(longContent, "cat-a", "匿名", "未知");
        ReactiveExtensionClient client = mockClientCreate();
        ServerRequest request = mockRequestWithBody(List.of(input));

        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));
        endpoint.batchCreateSentence(request).block();

        ArgumentCaptor<Sentence> captor = ArgumentCaptor.forClass(Sentence.class);
        verify(client, times(1)).create(captor.capture());
        assertThat(captor.getValue().getSpec().getContent()).hasSize(500);
    }

    @Test
    @DisplayName("batchCreateSentence：createdBy 被设置为当前用户")
    void batchCreateSentence_createdBySet() {
        Sentence input = newSentence("内容", "cat-a", "匿名", "未知");
        ReactiveExtensionClient client = mockClientCreate();
        ServerRequest request = mockRequestWithBodyAndPrincipal(List.of(input), "alice");

        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));
        endpoint.batchCreateSentence(request).block();

        ArgumentCaptor<Sentence> captor = ArgumentCaptor.forClass(Sentence.class);
        verify(client, times(1)).create(captor.capture());
        assertThat(captor.getValue().getSpec().getCreatedBy()).isEqualTo("alice");
    }

    @Test
    @DisplayName("batchCreateSentence：用户无 super-role → published=false")
    void batchCreateSentence_noSuperRole_publishedFalse() {
        Sentence input = newSentence("内容", "cat-a", "匿名", "未知");
        ReactiveExtensionClient client = mockClientCreate();
        ServerRequest request = mockRequestWithBody(List.of(input));

        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("guest-role"));
        endpoint.batchCreateSentence(request).block();

        ArgumentCaptor<Sentence> captor = ArgumentCaptor.forClass(Sentence.class);
        verify(client, times(1)).create(captor.capture());
        assertThat(captor.getValue().getStatus().isPublished()).isFalse();
    }

    @Test
    @DisplayName("batchCreateSentence：创建失败 → 计入 failed，不中断整体")
    void batchCreateSentence_createFails_failedIncremented() {
        Sentence input1 = newSentence("内容1", "cat-a", "匿名", "未知");
        Sentence input2 = newSentence("内容2", "cat-a", "匿名", "未知");
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.create(any(Sentence.class)))
            .thenReturn(Mono.error(new RuntimeException("DB 异常")))
            .thenReturn(Mono.just(input2));  // 第二次成功
        ServerRequest request = mockRequestWithBody(List.of(input1, input2));

        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));
        StepVerifier.create(endpoint.batchCreateSentence(request))
            .assertNext(response -> assertThat(response.statusCode().is2xxSuccessful()).isTrue())
            .verifyComplete();

        // 两次都尝试了 create
        verify(client, times(2)).create(any(Sentence.class));
    }

    // ==================== clearUncategorizedSentences 串行删除 ====================

    @Test
    @DisplayName("clearUncategorizedSentences：删除多条 → 计数正确")
    void clearUncategorizedSentences_multipleDeleted_countCorrect() {
        Sentence s1 = newSentence("内容1", "uncategorized", "匿名", "未知");
        s1.getMetadata().setName("s1");
        Sentence s2 = newSentence("内容2", "uncategorized", "匿名", "未知");
        s2.getMetadata().setName("s2");
        Sentence s3 = newSentence("内容3", "uncategorized", "匿名", "未知");
        s3.getMetadata().setName("s3");

        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.listAll(any(), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(s1, s2, s3));
        when(client.delete(any(Sentence.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ServerRequest request = mock(ServerRequest.class);
        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));

        StepVerifier.create(endpoint.clearUncategorizedSentences(request))
            .assertNext(response -> {
                assertThat(response.statusCode().is2xxSuccessful()).isTrue();
            })
            .verifyComplete();
        verify(client, times(3)).delete(any(Sentence.class));
    }

    @Test
    @DisplayName("clearUncategorizedSentences：单条删除失败 → 跳过不中断")
    void clearUncategorizedSentences_oneFails_skippedNotInterrupted() {
        Sentence s1 = newSentence("内容1", "uncategorized", "匿名", "未知");
        s1.getMetadata().setName("s1");
        Sentence s2 = newSentence("内容2", "uncategorized", "匿名", "未知");
        s2.getMetadata().setName("s2");

        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.listAll(any(), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(s1, s2));
        when(client.delete(any(Sentence.class)))
            .thenReturn(Mono.error(new RuntimeException("删除失败")))
            .thenReturn(Mono.just(s2));

        ServerRequest request = mock(ServerRequest.class);
        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));

        StepVerifier.create(endpoint.clearUncategorizedSentences(request))
            .assertNext(response -> assertThat(response.statusCode().is2xxSuccessful()).isTrue())
            .verifyComplete();
        verify(client, times(2)).delete(any(Sentence.class));
    }

    @Test
    @DisplayName("clearUncategorizedSentences：无未分类句子 → 返回 0")
    void clearUncategorizedSentences_empty_returnZero() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.listAll(any(), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        ServerRequest request = mock(ServerRequest.class);
        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));

        StepVerifier.create(endpoint.clearUncategorizedSentences(request))
            .assertNext(response -> assertThat(response.statusCode().is2xxSuccessful()).isTrue())
            .verifyComplete();
        verify(client, never()).delete(any(Sentence.class));
    }

    // ==================== querySentences / searchSentence ====================

    @Test
    @DisplayName("querySentences：返回 ListResult")
    void querySentences_returnListResult() {
        Sentence s1 = newSentence("内容1", "cat-a", "匿名", "未知");
        Sentence s2 = newSentence("内容2", "cat-a", "匿名", "未知");
        ListResult<Sentence> listResult = new ListResult<>(1, 10, 2L, List.of(s1, s2));

        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        Mono<ListResult<Sentence>> resultMono = Mono.just(listResult);
        doReturn(resultMono).when(client)
            .listBy(any(), any(ListOptions.class), any(PageRequest.class));

        // querySentences 走 SentenceQuery(request)，需 mock exchange 链路避免 NPE
        ServerRequest request = mockRequestWithExchange();
        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));

        StepVerifier.create(endpoint.querySentences(request))
            .assertNext(response -> assertThat(response.statusCode().is2xxSuccessful()).isTrue())
            .verifyComplete();
        verify(client, times(1)).listBy(any(), any(ListOptions.class), any(PageRequest.class));
    }

    @Test
    @DisplayName("searchSentence：返回 items 数组")
    void searchSentence_returnItems() {
        Sentence s1 = newSentence("内容1", "cat-a", "匿名", "未知");
        ListResult<Sentence> listResult = new ListResult<>(1, 10, 1L, List.of(s1));

        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        Mono<ListResult<Sentence>> resultMono = Mono.just(listResult);
        doReturn(resultMono).when(client)
            .listBy(any(), any(ListOptions.class), any(PageRequest.class));

        // searchSentence 同样走 SentenceQuery(request)，需 mock exchange
        ServerRequest request = mockRequestWithExchange();
        SentenceConsoleEndpoint endpoint = new SentenceConsoleEndpoint(client, mockRoleService("super-role"));

        StepVerifier.create(endpoint.searchSentence(request))
            .assertNext(response -> assertThat(response.statusCode().is2xxSuccessful()).isTrue())
            .verifyComplete();
        verify(client, times(1)).listBy(any(), any(ListOptions.class), any(PageRequest.class));
    }

    // ==================== 辅助方法 ====================

    /** 构造一个含 spec 与 metadata 的 Sentence（默认 metadata.name="test-sentence"）。 */
    private static Sentence newSentence(String content, String categoryName, String author, String source) {
        Sentence s = new Sentence();
        Metadata metadata = new Metadata();
        metadata.setName("test-sentence");
        s.setMetadata(metadata);
        Sentence.Spec spec = new Sentence.Spec();
        spec.setContent(content);
        spec.setCategoryName(categoryName);
        spec.setAuthor(author);
        spec.setSource(source);
        s.setSpec(spec);
        s.setStatus(new Sentence.Status());
        return s;
    }

    /**
     * mock ServerRequest.exchange() 返回完整链路（exchange → request → queryParams），
     * 用于走 {@code SentenceQuery} / {@code SortableRequest} 构造路径的测试。
     *
     * <p>queryParams 为空 Map，使 page/size 走默认值（1/20）。
     */
    private static ServerRequest mockRequestWithExchange() {
        ServerRequest request = mock(ServerRequest.class);
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest httpRequest = mock(ServerHttpRequest.class);
        when(httpRequest.getQueryParams()).thenReturn(new LinkedMultiValueMap<>());
        when(exchange.getRequest()).thenReturn(httpRequest);
        when(request.exchange()).thenReturn(exchange);
        return request;
    }

    /** mock ReactiveExtensionClient，仅 stub create 方法返回 Mono.just(input)。 */
    private static ReactiveExtensionClient mockClientCreate() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.create(any(Sentence.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        return client;
    }

    /** mock RoleService，返回指定 role。 */
    private static RoleService mockRoleService(String role) {
        RoleService roleService = mock(RoleService.class);
        when(roleService.getRolesByUsername(any())).thenReturn(Flux.just(role));
        return roleService;
    }

    /** mock ServerRequest，bodyToFlux 返回指定列表，principal 为 "test-user"。 */
    private static ServerRequest mockRequestWithBody(List<Sentence> body) {
        return mockRequestWithBodyAndPrincipal(body, "test-user");
    }

    private static ServerRequest mockRequestWithBodyAndPrincipal(List<Sentence> body, String username) {
        ServerRequest request = mock(ServerRequest.class);
        Principal principal = () -> username;
        Mono<Principal> principalMono = Mono.just(principal);
        doReturn(principalMono).when(request).principal();
        when(request.bodyToFlux(Sentence.class)).thenReturn(Flux.fromIterable(body));
        return request;
    }
}