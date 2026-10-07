package com.personal.finance.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.common.settings.SettingsService;
import com.personal.finance.common.util.MoneyUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Currency conversion with a user configurable base currency (USD by default).
 * Rates are expressed as "how many base currency units equal one unit of the
 * given currency" and are persisted in the settings store, so they survive
 * restarts. No currency is tied to a specific country by default.
 */
@Slf4j
@Service
public class ExchangeRateService {

    private static final String KEY_BASE_CURRENCY = "currency.base";
    private static final String KEY_RATES = "currency.rates";

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private String baseCurrency = "USD";
    private final Map<String, BigDecimal> rates = new ConcurrentHashMap<>();

    public ExchangeRateService(SettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        baseCurrency = normalize(settingsService.getOrDefault(KEY_BASE_CURRENCY, "USD"));
        String json = settingsService.getOrDefault(KEY_RATES, "");
        if (json != null && !json.isBlank()) {
            try {
                Map<String, BigDecimal> loaded = objectMapper.readValue(json, new TypeReference<>() {
                });
                rates.putAll(loaded);
                return;
            } catch (JsonProcessingException ex) {
                log.warn("Could not parse stored exchange rates, falling back to defaults", ex);
            }
        }
        defaultRates();
    }

    /**
     * Converts an amount from one currency to another using banker's rounding.
     */
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        String fromNorm = normalize(from);
        String toNorm = normalize(to);
        if (fromNorm.equals(toNorm)) {
            return MoneyUtils.round(amount);
        }
        BigDecimal fromRate = rateToBase(fromNorm);
        BigDecimal toRate = rateToBase(toNorm);
        BigDecimal baseAmount = amount.multiply(fromRate);
        return MoneyUtils.round(baseAmount.divide(toRate, 10, RoundingMode.HALF_EVEN));
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String currency) {
        String normalized = normalize(currency);
        settingsService.put(KEY_BASE_CURRENCY, normalized);
        this.baseCurrency = normalized;
    }

    public Map<String, BigDecimal> rates() {
        return Map.copyOf(rates);
    }

    /**
     * Sets or updates the base-relative rate for a currency. The rate must be
     * greater than zero.
     */
    public void updateRate(String currency, BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Exchange rate must be greater than zero");
        }
        rates.put(normalize(currency), MoneyUtils.round(rate));
        persistRates();
    }

    private void defaultRates() {
        // Approximate rates relative to the base currency (USD by default).
        rates.put("EUR", new BigDecimal("0.92"));
        rates.put("GBP", new BigDecimal("0.79"));
        rates.put("JPY", new BigDecimal("149.00"));
        rates.put("CHF", new BigDecimal("0.88"));
        rates.put("CAD", new BigDecimal("1.36"));
        rates.put("AUD", new BigDecimal("1.52"));
        rates.put("CNY", new BigDecimal("7.10"));
        rates.put("INR", new BigDecimal("83.00"));
        rates.put("BRL", new BigDecimal("5.10"));
        rates.put("MXN", new BigDecimal("17.00"));
        rates.put("KRW", new BigDecimal("1350.00"));
        rates.put("SEK", new BigDecimal("10.50"));
        rates.put("NOK", new BigDecimal("10.70"));
        rates.put("DKK", new BigDecimal("6.85"));
        rates.put("PLN", new BigDecimal("4.00"));
        rates.put("CZK", new BigDecimal("22.50"));
        rates.put("HUF", new BigDecimal("360.00"));
        rates.put("ILS", new BigDecimal("3.70"));
        rates.put("ZAR", new BigDecimal("18.00"));
        rates.put("SAR", new BigDecimal("3.75"));
        rates.put("AED", new BigDecimal("3.67"));
        rates.put("SGD", new BigDecimal("1.35"));
        rates.put("HKD", new BigDecimal("7.80"));
        rates.put("NZD", new BigDecimal("1.62"));
        rates.put("TRY", new BigDecimal("33.50"));
    }

    private void persistRates() {
        try {
            settingsService.put(KEY_RATES, objectMapper.writeValueAsString(new LinkedHashMap<>(rates)));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize exchange rates", ex);
        }
    }

    private BigDecimal rateToBase(String currency) {
        if (currency.equals(baseCurrency)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = rates.get(currency);
        if (rate == null) {
            throw new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                    "No exchange rate configured for " + currency);
        }
        return rate;
    }

    private String normalize(String currency) {
        return currency == null ? "" : currency.trim().toUpperCase();
    }
}
