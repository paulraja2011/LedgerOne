package com.ledgerone.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static BigDecimal round(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
