package com.advertisingmarketplace.common.money;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.currencies")
public record CurrencyProperties(@NotEmpty @DefaultValue({"EUR", "USD", "GBP", "RON"}) List<String> supported) {

    public boolean isSupported(String code) {
        return code != null && supported.stream().anyMatch(code::equalsIgnoreCase);
    }
}
