package top.puresky.hitokotohub.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * 基于 {@link ConcurrentHashMap} 的冷却缓存，泛型状态值。
 *
 * <p>用于统一 {@code SentencePublicEndpoint.likeCache} 与
 * {@code SentenceSubmissionPublicEndpoint.submitCache} 两套相似但状态类型不同的 IP 冷却缓存。
 *
 * <p>线程安全；不主动清理过期项（由调用方按需调用 {@link #cleanIf(Predicate)}）。
 *
 * @param <S> 每个缓存项的状态值类型
 */
public final class IpCooldownCache<S> {

    private final ConcurrentHashMap<String, S> store = new ConcurrentHashMap<>();

    /** 获取当前状态，不存在返回 null。 */
    public @Nullable S get(String key) {
        return store.get(key);
    }

    /** 设置/替换状态。 */
    public void put(String key, S state) {
        store.put(key, state);
    }

    /** 显式移除。 */
    public void remove(String key) {
        store.remove(key);
    }

    /** 当前缓存项数量。 */
    public int size() {
        return store.size();
    }

    /**
     * 清理满足 predicate 的项。
     *
     * @param shouldRemove 返回 true 则移除该 entry
     * @return 实际移除的数量
     */
    public int cleanIf(Predicate<S> shouldRemove) {
        int before = store.size();
        store.entrySet().removeIf(entry -> shouldRemove.test(entry.getValue()));
        return before - store.size();
    }
}
