package top.puresky.hitokotohub.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TimeFormatUtils} 单元测试。
 */
@DisplayName("TimeFormatUtils formatRemainingTime")
class TimeFormatUtilsTest {

    @Test
    @DisplayName("0 秒返回 0 秒")
    void shouldFormatZeroSeconds() {
        assertThat(TimeFormatUtils.formatRemainingTime(0)).isEqualTo("0 秒");
    }

    @Test
    @DisplayName("59 秒（< 60）返回秒")
    void shouldFormatSecondsUnderMinute() {
        assertThat(TimeFormatUtils.formatRemainingTime(59)).isEqualTo("59 秒");
    }

    @Test
    @DisplayName("60 秒（= 60）返回 1 分钟")
    void shouldFormatExactlyOneMinute() {
        assertThat(TimeFormatUtils.formatRemainingTime(60)).isEqualTo("1 分钟");
    }

    @Test
    @DisplayName("3599 秒（< 3600）返回分钟")
    void shouldFormatMinutesUnderHour() {
        assertThat(TimeFormatUtils.formatRemainingTime(3599)).isEqualTo("59 分钟");
    }

    @Test
    @DisplayName("3600 秒（= 3600）返回 1 小时")
    void shouldFormatExactlyOneHour() {
        assertThat(TimeFormatUtils.formatRemainingTime(3600)).isEqualTo("1 小时");
    }

    @Test
    @DisplayName("7265 秒返回 2 小时")
    void shouldFormatHours() {
        assertThat(TimeFormatUtils.formatRemainingTime(7265)).isEqualTo("2 小时");
    }
}
