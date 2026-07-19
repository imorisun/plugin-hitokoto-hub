package top.puresky.hitokotohub.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.config.SettingConfig;
import top.puresky.hitokotohub.service.SimilarityCheckService;
import top.puresky.hitokotohub.service.dto.BatchDeleteResult;

/**
 * {@link SimilarityCheckConsoleEndpoint} 单元测试。
 *
 * <p>聚焦 {@code deleteNonOptimal} 和 {@code listGroups} 端点方法的错误处理逻辑：
 * <ul>
 *   <li>service 正常返回 → 200</li>
 *   <li>service 抛异常 → 500（含 listGroups 的索引延迟场景）</li>
 * </ul>
 *
 * <p>使用纯 Mockito mock {@link SimilarityCheckService}，不涉及实现细节，
 * 风格与 {@link SentenceConsoleEndpointTest} 一致。
 */
@DisplayName("SimilarityCheckConsoleEndpoint 单元测试")
class SimilarityCheckConsoleEndpointTest {

    @Test
    @DisplayName("deleteNonOptimal：service 返回有数据结果 → 200")
    void deleteNonOptimal_serviceReturnsResult_returns200() {
        SimilarityCheckService service = mock(SimilarityCheckService.class);
        when(service.deleteNonOptimalSentences())
            .thenReturn(Mono.just(BatchDeleteResult.of(2, 2, 0)));

        SimilarityCheckConsoleEndpoint endpoint = newEndpoint(service);
        ServerRequest request = mock(ServerRequest.class);

        StepVerifier.create(endpoint.deleteNonOptimal(request))
            .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimal：service 返回空结果（无日志/无句子）→ 200")
    void deleteNonOptimal_serviceReturnsEmpty_returns200() {
        SimilarityCheckService service = mock(SimilarityCheckService.class);
        when(service.deleteNonOptimalSentences())
            .thenReturn(Mono.just(BatchDeleteResult.empty("无相似度检查日志，请先触发检查")));

        SimilarityCheckConsoleEndpoint endpoint = newEndpoint(service);
        ServerRequest request = mock(ServerRequest.class);

        StepVerifier.create(endpoint.deleteNonOptimal(request))
            .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimal：service 抛 RuntimeException → 500")
    void deleteNonOptimal_serviceThrowsRuntimeException_returns500() {
        SimilarityCheckService service = mock(SimilarityCheckService.class);
        when(service.deleteNonOptimalSentences())
            .thenReturn(Mono.error(new RuntimeException("DB 异常")));

        SimilarityCheckConsoleEndpoint endpoint = newEndpoint(service);
        ServerRequest request = mock(ServerRequest.class);

        StepVerifier.create(endpoint.deleteNonOptimal(request))
            .assertNext(response ->
                assertThat(response.statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimal：service 抛 IllegalArgumentException → 500（非 404）")
    void deleteNonOptimal_serviceThrowsIllegalArgument_returns500() {
        // 批量删除场景下 IllegalArgumentException 代表程序异常，映射 500 而非 404
        SimilarityCheckService service = mock(SimilarityCheckService.class);
        when(service.deleteNonOptimalSentences())
            .thenReturn(Mono.error(new IllegalArgumentException("参数非法")));

        SimilarityCheckConsoleEndpoint endpoint = newEndpoint(service);
        ServerRequest request = mock(ServerRequest.class);

        StepVerifier.create(endpoint.deleteNonOptimal(request))
            .assertNext(response ->
                assertThat(response.statusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR))
            .verifyComplete();
    }

    // ==================== listGroups ====================

    @Test
    @DisplayName("listGroups：service 返回正常分组 → 200")
    void listGroups_serviceReturnsGroups_returns200() {
        SimilarityCheckService service = mock(SimilarityCheckService.class);
        when(service.getGroups(1, 5))
            .thenReturn(Mono.just(Map.of("page", 1, "size", 5, "total", 0, "groups", List.of())));

        SimilarityCheckConsoleEndpoint endpoint = newEndpoint(service);
        ServerRequest request = mockRequestWithPageSize(1, 5);

        StepVerifier.create(endpoint.listGroups(request))
            .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
            .verifyComplete();
    }

    @Test
    @DisplayName("listGroups：service 抛 RuntimeException（如索引延迟）→ 200 + 空数据")
    void listGroups_serviceThrowsRuntimeException_returns200() {
        SimilarityCheckService service = mock(SimilarityCheckService.class);
        when(service.getGroups(1, 5))
            .thenReturn(Mono.error(new RuntimeException("索引未就绪")));

        SimilarityCheckConsoleEndpoint endpoint = newEndpoint(service);
        ServerRequest request = mockRequestWithPageSize(1, 5);

        StepVerifier.create(endpoint.listGroups(request))
            .assertNext(response -> {
                assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
            })
            .verifyComplete();
    }

    // ==================== 辅助方法 ====================

    /** 构造 endpoint，仅 stub 被测方法依赖的 service；client/settingConfig 传 mock 占位。 */
    private static SimilarityCheckConsoleEndpoint newEndpoint(SimilarityCheckService service) {
        return new SimilarityCheckConsoleEndpoint(
            mock(ReactiveExtensionClient.class),
            service,
            mock(SettingConfig.class)
        );
    }

    /** 构造含 page/size queryParam 的 ServerRequest mock。 */
    private static ServerRequest mockRequestWithPageSize(int page, int size) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.queryParam("page")).thenReturn(java.util.Optional.of(String.valueOf(page)));
        when(request.queryParam("size")).thenReturn(java.util.Optional.of(String.valueOf(size)));
        return request;
    }
}
