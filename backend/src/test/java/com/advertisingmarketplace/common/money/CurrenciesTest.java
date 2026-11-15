package com.advertisingmarketplace.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrenciesTest {

    private final CurrencyProperties properties = new CurrencyProperties(List.of("EUR", "USD", "GBP", "RON"));
    private final SupportedCurrencyValidator validator = new SupportedCurrencyValidator(properties);

    @Test
    void normalizesCodes() {
        assertThat(Currencies.normalize(" eur ")).isEqualTo("EUR");
        assertThat(Currencies.normalize("")).isNull();
        assertThat(Currencies.normalize(null)).isNull();
    }

    @Test
    void supportedCurrenciesAreCaseInsensitive() {
        assertThat(properties.isSupported("ron")).isTrue();
        assertThat(properties.isSupported("JPY")).isFalse();
        assertThat(properties.isSupported(null)).isFalse();
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("usd", null)).isTrue();
    }

    @Test
    void amountsMustFitTheCurrencysMinorUnits() {
        assertThatCode(() -> Currencies.requireAmountsFit("EUR", new BigDecimal("10.50"), new BigDecimal("3")))
                .doesNotThrowAnyException();
        assertThatCode(() -> Currencies.requireAmountsFit("JPY", new BigDecimal("1000.00")))
                .doesNotThrowAnyException();
        assertThatCode(() -> Currencies.requireAmountsFit(null, new BigDecimal("1.234"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> Currencies.requireAmountsFit("JPY", new BigDecimal("1000.5")))
                .isInstanceOf(AmountPrecisionException.class);
        assertThatThrownBy(() -> Currencies.requireAmountsFit("EUR", new BigDecimal("1.005")))
                .isInstanceOf(AmountPrecisionException.class);
    }

    @Test
    void describesCurrencies() {
        assertThat(CurrencyResponse.of("RON")).isEqualTo(new CurrencyResponse("RON", "Romanian Leu", "RON", 2, null, null));
        assertThat(CurrencyResponse.of("USD").symbol()).isEqualTo("$");
    }
}
