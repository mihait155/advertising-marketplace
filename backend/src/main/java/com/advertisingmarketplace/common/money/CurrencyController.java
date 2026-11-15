package com.advertisingmarketplace.common.money;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyProperties properties;
    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public List<CurrencyResponse> list() {
        Map<String, ExchangeRate> rates = exchangeRateService.currentRates();
        return properties.supported().stream()
                .map(Currencies::normalize)
                .map(code -> CurrencyResponse.of(code, rates.get(code)))
                .toList();
    }
}
