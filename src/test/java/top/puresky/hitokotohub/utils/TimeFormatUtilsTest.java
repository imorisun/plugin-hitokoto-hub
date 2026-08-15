package top.puresky.hitokotohub.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link TimeFormatUtils} 单元测试。
 */
class TimeFormatUtilsTest {

    @Test
    void secondsRange() {
        assertEquals("0 秒", TimeFormatUtils.formatRemainingTime(0));
        assertEquals("59 秒", TimeFormatUtils.formatRemainingTime(59));
    }

    @Test
    void minutesRange() {
        assertEquals("1 分钟", TimeFormatUtils.formatRemainingTime(60));
        assertEquals("59 分钟", TimeFormatUtils.formatRemainingTime(3599));
    }

    @Test
    void hoursRange() {
        assertEquals("1 小时", TimeFormatUtils.formatRemainingTime(3600));
        assertEquals("2 小时", TimeFormatUtils.formatRemainingTime(7200));
        assertEquals("24 小时", TimeFormatUtils.formatRemainingTime(86400));
    }
}
