package top.puresky.hitokotohub.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 冷却状态类的单元测试。
 */
class CooldownStateTest {

    private static final long NOW = 1_000_000L;

    // ===================== SimpleCooldownState =====================

    @Test
    void simpleStateCoolingDownWindow() {
        var state = new SimpleCooldownState(NOW);
        assertTrue(state.isCoolingDown(60_000, NOW + 59_999));
        assertFalse(state.isCoolingDown(60_000, NOW + 60_000));
    }

    @Test
    void simpleStateExpired() {
        var state = new SimpleCooldownState(NOW);
        assertFalse(state.isExpired(60_000, NOW + 30_000));
        assertTrue(state.isExpired(60_000, NOW + 60_000));
    }

    @Test
    void simpleStateRemainingMillis() {
        var state = new SimpleCooldownState(NOW);
        assertEquals(30_000, state.remainingMillis(60_000, NOW + 30_000));
        assertEquals(0, state.remainingMillis(60_000, NOW + 60_001));
    }

    // ===================== BatchCooldownState =====================

    @Test
    void batchStateStartsWithCountOne() {
        var state = new BatchCooldownState(NOW);
        assertEquals(1, state.getCount());
    }

    @Test
    void batchStateIncrement() {
        var state = new BatchCooldownState(NOW);
        state.increment();
        state.increment();
        assertEquals(3, state.getCount());
    }

    @Test
    void batchStateReachedLimit() {
        var state = new BatchCooldownState(NOW);
        assertFalse(state.reachedBatchLimit(3));
        state.increment();
        state.increment();
        assertTrue(state.reachedBatchLimit(3));
    }

    @Test
    void batchStateExpired() {
        var state = new BatchCooldownState(NOW);
        assertFalse(state.isExpired(600_000, NOW + 599_999));
        assertTrue(state.isExpired(600_000, NOW + 600_000));
    }
}
