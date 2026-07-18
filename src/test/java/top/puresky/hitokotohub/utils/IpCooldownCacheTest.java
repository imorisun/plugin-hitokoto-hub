package top.puresky.hitokotohub.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link IpCooldownCache}、{@link SimpleCooldownState}、{@link BatchCooldownState} 单元测试。
 */
@DisplayName("IpCooldownCache + 冷却状态类")
class IpCooldownCacheTest {

    @Test
    @DisplayName("get/put/remove 基本操作")
    void shouldSupportGetPutRemove() {
        IpCooldownCache<String> cache = new IpCooldownCache<>();

        cache.put("key1", "value1");
        assertThat(cache.get("key1")).isEqualTo("value1");
        assertThat(cache.size()).isEqualTo(1);

        cache.remove("key1");
        assertThat(cache.get("key1")).isNull();
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("get 不存在的 key 返回 null")
    void shouldReturnNullForMissingKey() {
        IpCooldownCache<String> cache = new IpCooldownCache<>();
        assertThat(cache.get("missing")).isNull();
    }

    @Test
    @DisplayName("cleanIf 移除满足谓词的项并返回移除数量")
    void shouldCleanIfPredicateMatches() {
        IpCooldownCache<SimpleCooldownState> cache = new IpCooldownCache<>();
        long now = System.currentTimeMillis();
        cache.put("expired", new SimpleCooldownState(now - 10_000));
        cache.put("active", new SimpleCooldownState(now));

        int removed = cache.cleanIf(state -> state.isExpired(5_000, now));

        assertThat(removed).isEqualTo(1);
        assertThat(cache.get("expired")).isNull();
        assertThat(cache.get("active")).isNotNull();
    }

    @Test
    @DisplayName("cleanIf 无匹配时返回 0")
    void shouldReturnZeroWhenNoMatch() {
        IpCooldownCache<SimpleCooldownState> cache = new IpCooldownCache<>();
        long now = System.currentTimeMillis();
        cache.put("active", new SimpleCooldownState(now));

        int removed = cache.cleanIf(state -> state.isExpired(5_000, now));

        assertThat(removed).isEqualTo(0);
        assertThat(cache.size()).isEqualTo(1);
    }

    // --- SimpleCooldownState ---

    @Test
    @DisplayName("SimpleCooldownState: 冷却窗口内 isCoolingDown=true")
    void simpleStateIsCoolingDown() {
        long now = 10_000;
        SimpleCooldownState state = new SimpleCooldownState(8_000);

        assertThat(state.isCoolingDown(5_000, now)).isTrue();
        assertThat(state.isExpired(5_000, now)).isFalse();
    }

    @Test
    @DisplayName("SimpleCooldownState: 超出冷却窗口 isExpired=true")
    void simpleStateIsExpired() {
        long now = 20_000;
        SimpleCooldownState state = new SimpleCooldownState(10_000);

        assertThat(state.isExpired(5_000, now)).isTrue();
        assertThat(state.isCoolingDown(5_000, now)).isFalse();
    }

    @Test
    @DisplayName("SimpleCooldownState: remainingMillis 返回剩余毫秒，已过期返回 0")
    void simpleStateRemainingMillis() {
        long now = 12_000;
        SimpleCooldownState state = new SimpleCooldownState(10_000);

        assertThat(state.remainingMillis(5_000, now)).isEqualTo(3_000);
        assertThat(state.remainingMillis(1_000, now)).isEqualTo(0);
    }

    // --- BatchCooldownState ---

    @Test
    @DisplayName("BatchCooldownState: 初始 count=1")
    void batchStateInitialCount() {
        BatchCooldownState state = new BatchCooldownState(10_000);

        assertThat(state.getCount()).isEqualTo(1);
        assertThat(state.getFirstSubmitTime()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("BatchCooldownState: increment 累加计数")
    void batchStateIncrement() {
        BatchCooldownState state = new BatchCooldownState(10_000);

        state.increment();
        state.increment();

        assertThat(state.getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("BatchCooldownState: reachedBatchLimit 达到上限返回 true")
    void batchStateReachedBatchLimit() {
        BatchCooldownState state = new BatchCooldownState(10_000);

        assertThat(state.reachedBatchLimit(1)).isTrue();
        assertThat(state.reachedBatchLimit(3)).isFalse();

        state.increment();
        assertThat(state.reachedBatchLimit(3)).isFalse();

        state.increment();
        assertThat(state.reachedBatchLimit(3)).isTrue();
    }

    @Test
    @DisplayName("BatchCooldownState: isExpired 超出冷却窗口返回 true")
    void batchStateIsExpired() {
        BatchCooldownState state = new BatchCooldownState(10_000);

        assertThat(state.isExpired(5_000, 12_000)).isFalse();
        assertThat(state.isExpired(5_000, 15_000)).isTrue();
    }
}
