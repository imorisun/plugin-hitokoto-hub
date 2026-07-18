package top.puresky.hitokotohub.utils;

/**
 * 单时间戳冷却状态，配合 {@link IpCooldownCache} 用于点赞冷却场景。
 *
 * <p>替代 {@code SentencePublicEndpoint.likeCache} 中的 {@code Map<String, Long>} 裸时间戳。
 *
 * @param timestampMillis 上一次操作的时间戳（毫秒）
 */
public record SimpleCooldownState(long timestampMillis) {

    /** 是否仍在冷却窗口内。 */
    public boolean isCoolingDown(long cooldownMillis, long now) {
        return (now - timestampMillis) < cooldownMillis;
    }

    /** 是否已过期（超出冷却窗口）。 */
    public boolean isExpired(long cooldownMillis, long now) {
        return (now - timestampMillis) >= cooldownMillis;
    }

    /** 剩余毫秒（已过期返回 0）。 */
    public long remainingMillis(long cooldownMillis, long now) {
        long remaining = cooldownMillis - (now - timestampMillis);
        return Math.max(0, remaining);
    }
}
