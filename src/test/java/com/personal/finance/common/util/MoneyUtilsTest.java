package com.personal.finance.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Banker's rounding and decimal precision rules (TX-03, ACC-09, TRF-08).
 */
class MoneyUtilsTest {

    @Test
    void bankerRoundingRoundsHalfToEven() {
        assertThat(MoneyUtils.round(new BigDecimal("1.005"))).isEqualByComparingTo("1.00");
        assertThat(MoneyUtils.round(new BigDecimal("1.015"))).isEqualByComparingTo("1.02");
        assertThat(MoneyUtils.round(new BigDecimal("1.025"))).isEqualByComparingTo("1.02");
        assertThat(MoneyUtils.round(new BigDecimal("1.035"))).isEqualByComparingTo("1.04");
    }

    @Test
    void valuesWithMoreThanTwoDecimalsAreDetected() {
        assertThat(MoneyUtils.hasMoreThanTwoDecimals(new BigDecimal("10.999"))).isTrue();
        assertThat(MoneyUtils.hasMoreThanTwoDecimals(new BigDecimal("10.995"))).isTrue();
    }

    @Test
    void valuesWithTwoOrFewerDecimalsAreAccepted() {
        assertThat(MoneyUtils.hasMoreThanTwoDecimals(new BigDecimal("10.99"))).isFalse();
        assertThat(MoneyUtils.hasMoreThanTwoDecimals(new BigDecimal("10"))).isFalse();
        assertThat(MoneyUtils.hasMoreThanTwoDecimals(new BigDecimal("100.00"))).isFalse();
    }
}
