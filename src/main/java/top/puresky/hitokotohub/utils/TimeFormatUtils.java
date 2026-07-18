package top.puresky.hitokotohub.utils;

import org.jspecify.annotations.NonNull;

/**
 * 时间格式化工具方法。
 *
 * <p>提取自 {@code SentencePublicEndpoint} 与 {@code SentenceSubmissionPublicEndpoint} 中
 * 逐字重复的 {@code formatRemainingTime} 私有方法。
 */
public final class TimeFormatUtils {

    private TimeFormatUtils() {}

    /**
     * 将剩余秒数格式化为中文可读的剩余时间。
     *
     * <ul>
     *   <li>{@code < 60} → "N 秒"</li>
     *   <li>{@code < 3600} → "N 分钟"</li>
     *   <li>否则 → "N 小时"</li>
     * </ul>
     *
     * @param seconds 剩余秒数（非负）
     * @return 中文剩余时间字符串，不会为 null
     */
    public static @NonNull String formatRemainingTime(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " 分钟";
        }
        return (seconds / 3600) + " 小时";
    }
}
