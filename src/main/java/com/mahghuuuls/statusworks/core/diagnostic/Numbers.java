package com.mahghuuuls.statusworks.core.diagnostic;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How decimal amounts are written in the diagnostic log and command output: at most four decimals, no trailing zeros. */
public final class Numbers {

    private Numbers() {
    }

    public static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        String text = rounded.toPlainString();
        return "-0".equals(text) ? "0" : text;
    }
}
