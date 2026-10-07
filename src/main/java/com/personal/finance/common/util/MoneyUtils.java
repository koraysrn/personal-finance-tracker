package com.personal.finance.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central money arithmetic helpers.
 * All monetary values are stored with scale 2 and rounded using banker's
 * rounding ({@link RoundingMode#HALF_EVEN}) to avoid systematic bias.
 */
public final class MoneyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private MoneyUtils() {
    }

    /**
     * Rounds a value to the canonical 2-decimal monetary scale.
     */
    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    /**
     * Returns true when the value has more than two decimal places and would
     * therefore lose precision if stored directly.
     */
    public static boolean hasMoreThanTwoDecimals(BigDecimal value) {
        return value.stripTrailingZeros().scale() > SCALE;
    }
}
