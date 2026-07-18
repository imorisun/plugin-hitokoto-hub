package top.puresky.hitokotohub.utils;

/**
 * 批量计数冷却状态，配合 {@link IpCooldownCache} 用于访客提交冷却场景。
 *
 * <p>替代 {@code SentenceSubmissionPublicEndpoint} 中的内部类 {@code SubmissionCooldownState}。
 * 支持冷却窗口内累计计数，达到批量上限后进入冷却。
 *
 * <p>count 字段可变（{@link #increment()}），调用方需自行保证对同一 key 的更新串行化
 * （原实现亦如此）。
 */
public final class BatchCooldownState {

    private final long firstSubmitTime;
    private int count;

    public BatchCooldownState(long firstSubmitTime) {
        this.firstSubmitTime = firstSubmitTime;
        this.count = 1;
    }

    public long getFirstSubmitTime() {
        return firstSubmitTime;
    }

    public int getCount() {
        return count;
    }

    /** 累计计数 +1。 */
    public void increment() {
        this.count++;
    }

    /** 冷却窗口是否已过。 */
    public boolean isExpired(long cooldownMillis, long now) {
        return (now - firstSubmitTime) >= cooldownMillis;
    }

    /** 是否已达批量提交上限。 */
    public boolean reachedBatchLimit(int batchLimit) {
        return count >= batchLimit;
    }
}
