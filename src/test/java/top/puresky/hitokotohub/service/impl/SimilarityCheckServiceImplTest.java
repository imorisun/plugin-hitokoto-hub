package top.puresky.hitokotohub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;
import run.halo.app.extension.ReactiveExtensionClient;
import top.puresky.hitokotohub.extension.Sentence;
import top.puresky.hitokotohub.extension.SimilarityCheckLog;
import top.puresky.hitokotohub.extension.SimilarityGroup;
import top.puresky.hitokotohub.service.dto.BatchDeleteResult;
import top.puresky.hitokotohub.service.similarity.SentencePair;
import top.puresky.hitokotohub.service.similarity.SentencePairJsonCodec;
import top.puresky.hitokotohub.service.similarity.SimilarityGroupBuilder;
import top.puresky.hitokotohub.support.MockExtensionClient;
import top.puresky.hitokotohub.support.TestFixtures;

/**
 * {@link SimilarityCheckServiceImpl} 集成测试。
 *
 * <p>使用 {@link MockExtensionClient}（内存 fake）+ 真实 {@link SimilarityGroupBuilder}
 * 和 {@link SentencePairJsonCodec}，验证 3 个 public API 的端到端行为。
 */
@DisplayName("SimilarityCheckServiceImpl 集成测试")
class SimilarityCheckServiceImplTest {

    private SimilarityGroupBuilder groupBuilder;
    private SentencePairJsonCodec codec;

    @BeforeEach
    void setUp() {
        groupBuilder = new SimilarityGroupBuilder();
        codec = new SentencePairJsonCodec(new ObjectMapper());
    }

    // ==================== performCheck ====================

    @Test
    @DisplayName("performCheck COSINE：3 句子（2 相似 + 1 不相似）→ SUCCESS，similarPairCount=1")
    void performCheckWithSimilarSentences() {
        Sentence s1 = TestFixtures.sentence("s1", "人生如梦，岁月如歌", true, 10, 100);
        Sentence s2 = TestFixtures.sentence("s2", "人生如梦，岁月如歌", true, 5, 50);
        Sentence s3 = TestFixtures.sentence("s3", "独立寒秋，湘江北去", true, 3, 30);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1).with(s2).with(s3).build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.performCheck(
                SimilarityCheckLog.TriggerType.MANUAL, "tester", "COSINE", 0.3))
            .assertNext(log -> {
                assertThat(log.getSpec().getStatus()).isEqualTo(SimilarityCheckLog.Status.SUCCESS);
                assertThat(log.getSpec().getTotalSentences()).isEqualTo(3);
                assertThat(log.getSpec().getTotalPairs()).isEqualTo(3L);
                assertThat(log.getSpec().getSimilarPairCount()).isEqualTo(1);
                assertThat(log.getSpec().getSimilarPairs()).contains("s1").contains("s2");
                assertThat(log.getSpec().getDurationMs()).isGreaterThanOrEqualTo(0);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("performCheck 空句子列表 → SUCCESS，totalSentences=0")
    void performCheckWithEmptySentences() {
        ReactiveExtensionClient client = MockExtensionClient.builder().build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.performCheck(
                SimilarityCheckLog.TriggerType.MANUAL, "tester", "COSINE", 0.3))
            .assertNext(log -> {
                assertThat(log.getSpec().getStatus()).isEqualTo(SimilarityCheckLog.Status.SUCCESS);
                assertThat(log.getSpec().getTotalSentences()).isEqualTo(0);
                assertThat(log.getSpec().getTotalPairs()).isEqualTo(0L);
                assertThat(log.getSpec().getSimilarPairCount()).isEqualTo(0);
                assertThat(log.getSpec().getSimilarPairs()).isEqualTo("[]");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("performCheck 数据访问异常 → FAILED，errorMessage 非空")
    void performCheckWithError() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.create(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(client.update(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(client.listAll(any(), any(), any())).thenReturn(
            Flux.error(new RuntimeException("数据访问失败")));

        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.performCheck(
                SimilarityCheckLog.TriggerType.MANUAL, "tester", "COSINE", 0.3))
            .assertNext(log -> {
                assertThat(log.getSpec().getStatus()).isEqualTo(SimilarityCheckLog.Status.FAILED);
                assertThat(log.getSpec().getErrorMessage()).contains("数据访问失败");
            })
            .verifyComplete();
    }

    // ==================== getGroups ====================

    @Test
    @DisplayName("getGroups：有日志和相似对 → 返回分组，bestSentence 为评分最高者")
    void getGroupsWithLog() {
        // s1 评分更高（10 赞 100 浏览），s2 评分较低（5 赞 50 浏览）
        Sentence s1 = TestFixtures.sentence("s1", "人生如梦，岁月如歌", true, 10, 100);
        Sentence s2 = TestFixtures.sentence("s2", "人生如梦，岁月如歌", true, 5, 50);

        // 构造相似对 JSON（s1-s2 相似度 1.0）
        String pairsJson = codec.serialize(List.of(
            new SentencePair("s1", "人生如梦，岁月如歌", "default-cat", "匿名", "未知",
                "s2", "人生如梦，岁月如歌", "default-cat", "匿名", "未知", 1.0)
        ));

        SimilarityCheckLog log = TestFixtures.successLog("log-1", "COSINE", 0.3, pairsJson);

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1).with(s2).with(log).build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.getGroups(1, 10))
            .assertNext(result -> {
                assertThat(result.get("page")).isEqualTo(1);
                assertThat(result.get("size")).isEqualTo(10);
                assertThat(result.get("total")).isEqualTo(1);
                @SuppressWarnings("unchecked")
                List<SimilarityGroup> groups = (List<SimilarityGroup>) result.get("groups");
                assertThat(groups).hasSize(1);
                // s1 评分更高（10 赞 vs 5 赞），应是最优
                assertThat(groups.get(0).getBestSentence().getName()).isEqualTo("s1");
                assertThat(groups.get(0).getSimilarCount()).isEqualTo(1);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getGroups：无日志 → emptyResult（total=0，groups 空列表）")
    void getGroupsWithoutLog() {
        ReactiveExtensionClient client = MockExtensionClient.builder().build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.getGroups(1, 10))
            .assertNext(result -> {
                assertThat(result.get("total")).isEqualTo(0);
                assertThat(result.get("groups")).isEqualTo(List.of());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("getGroups：3 次重试均失败 → 降级返回 emptyResult")
    void getGroupsWhenListAllFails_returnsEmptyResult() {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        // 所有调用均返回错误，retryWhen 重试 3 次后 onErrorResume 兜底
        when(client.listAll(any(), any(), any()))
            .thenReturn(Flux.error(new RuntimeException("索引未就绪")));

        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.getGroups(1, 10))
            .assertNext(result -> {
                assertThat(result.get("total")).isEqualTo(0);
                assertThat(result.get("groups")).isEqualTo(List.of());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Retry.backoff 机制验证：首次 Mono.error 后自动重试并成功")
    void retryBackoffRecoversAfterError() {
        AtomicInteger callCount = new AtomicInteger(0);
        Mono<String> mono = Mono.fromCallable(() -> {
                if (callCount.getAndIncrement() < 1) {
                    throw new RuntimeException("瞬时错误");
                }
                return "正确数据";
            })
            .retryWhen(Retry.backoff(3, Duration.ofMillis(10))
                .maxBackoff(Duration.ofMillis(100)));

        StepVerifier.withVirtualTime(() -> mono)
            .thenAwait(Duration.ofMillis(500))
            .expectNext("正确数据")
            .verifyComplete();
    }

    // ==================== deleteNonOptimalSentences ====================

    @Test
    @DisplayName("deleteNonOptimalSentences：2 组相似，删除非最优后 deleted=2, failed=0")
    void deleteNonOptimalSentences() {
        // 组1：s1-s2 相同内容，s1 评分更高（应保留 s1，删除 s2）
        Sentence s1 = TestFixtures.sentence("s1", "人生如梦，岁月如歌", true, 10, 100);
        Sentence s2 = TestFixtures.sentence("s2", "人生如梦，岁月如歌", true, 5, 50);
        // 组2：s3-s4 高度相似，s3 评分更高（应保留 s3，删除 s4）
        Sentence s3 = TestFixtures.sentence("s3", "春风又绿江南岸", true, 20, 200);
        Sentence s4 = TestFixtures.sentence("s4", "春风又绿江南畔", true, 8, 80);
        // 独立句子 s5（不参与相似组，保留）
        Sentence s5 = TestFixtures.sentence("s5", "独立寒秋", true, 1, 10);

        // 构造 successLog（提供 algorithm 和 threshold，pairsJson 不影响删除逻辑）
        SimilarityCheckLog log = TestFixtures.successLog("log-1", "COSINE", 0.3, "[]");

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1).with(s2).with(s3).with(s4).with(s5).with(log).build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.deleteNonOptimalSentences())
            .assertNext(result -> {
                // s2 和 s4 应被删除（非最优），s1/s3/s5 保留
                assertThat(result.total()).isEqualTo(2);
                assertThat(result.deleted()).isEqualTo(2);
                assertThat(result.failed()).isZero();
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimalSentences：无 SUCCESS 日志 → BatchDeleteResult.empty（不返回 empty Mono 导致 500）")
    void deleteNonOptimalSentencesWithoutLog() {
        // 有句子但无 SUCCESS 日志（曾经导致端点返回 empty Mono → 500）
        Sentence s1 = TestFixtures.sentence("s1", "人生如梦", true, 10, 100);
        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1).build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.deleteNonOptimalSentences())
            .assertNext(result -> {
                assertThat(result.deleted()).isZero();
                assertThat(result.message()).contains("无相似度检查日志");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimalSentences：有 SUCCESS 日志但无句子 → deleted=0, message 含「无句子」")
    void deleteNonOptimalSentencesWithEmptySentences() {
        SimilarityCheckLog log = TestFixtures.successLog("log-1", "COSINE", 0.3, "[]");
        ReactiveExtensionClient client = MockExtensionClient.builder().with(log).build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.deleteNonOptimalSentences())
            .assertNext(result -> {
                assertThat(result.deleted()).isZero();
                assertThat(result.message()).contains("无句子");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimalSentences：有日志+句子但无相似对 → total=0, deleted=0, failed=0")
    void deleteNonOptimalSentencesWithNoSimilarPairs() {
        // threshold=0.99，两句子内容完全不同，不会形成相似对
        Sentence s1 = TestFixtures.sentence("s1", "人生如梦", true, 10, 100);
        Sentence s2 = TestFixtures.sentence("s2", "完全不同的内容", true, 5, 50);
        SimilarityCheckLog log = TestFixtures.successLog("log-1", "COSINE", 0.99, "[]");

        ReactiveExtensionClient client = MockExtensionClient.builder()
            .with(s1).with(s2).with(log).build();
        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.deleteNonOptimalSentences())
            .assertNext(result -> {
                assertThat(result.total()).isZero();
                assertThat(result.deleted()).isZero();
                assertThat(result.failed()).isZero();
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimalSentences：listAll(Sentence) 失败 → 异常传播到端点兜底")
    void deleteNonOptimalSentencesWhenListSentencesFails() {
        SimilarityCheckLog log = TestFixtures.successLog("log-1", "COSINE", 0.3, "[]");
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        // 第 1 次 listAll 返回 SimilarityCheckLog，第 2 次 listAll（Sentence）抛异常
        when(client.listAll(any(), any(), any())).thenReturn(
            Flux.just(log),
            Flux.error(new RuntimeException("数据访问失败"))
        );

        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.deleteNonOptimalSentences())
            .verifyError(RuntimeException.class);
    }

    @Test
    @DisplayName("deleteNonOptimalSentences：单条删除失败 → total=1, deleted=0, failed=1")
    void deleteNonOptimalSentencesWhenSingleDeleteFails() {
        // s1-s2 相同内容，s1 评分更高（保留 s1，删除 s2）
        Sentence s1 = TestFixtures.sentence("s1", "人生如梦，岁月如歌", true, 10, 100);
        Sentence s2 = TestFixtures.sentence("s2", "人生如梦，岁月如歌", true, 5, 50);
        SimilarityCheckLog log = TestFixtures.successLog("log-1", "COSINE", 0.3, "[]");

        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.listAll(any(), any(), any())).thenReturn(
            Flux.just(log),     // SimilarityCheckLog
            Flux.just(s1, s2)   // Sentence
        );
        when(client.fetch(eq(Sentence.class), eq("s2"))).thenReturn(Mono.just(s2));
        when(client.delete(any(Sentence.class)))
            .thenReturn(Mono.error(new RuntimeException("删除失败")));

        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.deleteNonOptimalSentences())
            .assertNext(result -> {
                assertThat(result.total()).isEqualTo(1);
                assertThat(result.deleted()).isZero();
                assertThat(result.failed()).isEqualTo(1);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("deleteNonOptimalSentences：句子已被并发删除（fetch 返回 empty）→ total=1, deleted=0, failed=1")
    void deleteNonOptimalSentencesWhenSentenceAlreadyGone() {
        // s1-s2 相同内容，s1 评分更高（保留 s1，删除 s2）
        Sentence s1 = TestFixtures.sentence("s1", "人生如梦，岁月如歌", true, 10, 100);
        Sentence s2 = TestFixtures.sentence("s2", "人生如梦，岁月如歌", true, 5, 50);
        SimilarityCheckLog log = TestFixtures.successLog("log-1", "COSINE", 0.3, "[]");

        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.listAll(any(), any(), any())).thenReturn(
            Flux.just(log),     // SimilarityCheckLog
            Flux.just(s1, s2)   // Sentence
        );
        // fetch 返回 empty（模拟 s2 在 listAll 之后、delete 之前已被并发删除）
        when(client.fetch(eq(Sentence.class), eq("s2"))).thenReturn(Mono.empty());

        SimilarityCheckServiceImpl service = new SimilarityCheckServiceImpl(client, groupBuilder, codec);

        StepVerifier.create(service.deleteNonOptimalSentences())
            .assertNext(result -> {
                assertThat(result.total()).isEqualTo(1);
                assertThat(result.deleted()).isZero();
                assertThat(result.failed()).isEqualTo(1);
            })
            .verifyComplete();
    }

}
