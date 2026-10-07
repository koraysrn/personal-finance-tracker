package com.personal.finance.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Validated
@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public RatesResponse rates() {
        return new RatesResponse(exchangeRateService.getBaseCurrency(), exchangeRateService.rates());
    }

    @PutMapping
    public RatesResponse updateRate(@RequestBody RateUpdateRequest request) {
        exchangeRateService.updateRate(request.currency(), request.rate());
        return rates();
    }

    @PutMapping("/base")
    public RatesResponse setBase(@RequestBody BaseCurrencyRequest request) {
        exchangeRateService.setBaseCurrency(request.currency());
        return rates();
    }

    public record RateUpdateRequest(
            @NotBlank(message = "Currency is required")
            @Size(min = 3, max = 3, message = "Currency must be a 3 letter code")
            String currency,

            @DecimalMin(value = "0.0", inclusive = false, message = "Rate must be greater than zero")
            @Digits(integer = 16, fraction = 2, message = "Rate must have at most 2 decimal places")
            BigDecimal rate) {
    }

    public record BaseCurrencyRequest(
            @NotBlank(message = "Currency is required")
            @Size(min = 3, max = 3, message = "Currency must be a 3 letter code")
            String currency) {
    }
}
