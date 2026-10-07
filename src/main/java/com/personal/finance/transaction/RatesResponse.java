package com.personal.finance.transaction;

import java.math.BigDecimal;
import java.util.Map;

public record RatesResponse(String base, Map<String, BigDecimal> rates) {
}
