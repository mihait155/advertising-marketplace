package com.advertisingmarketplace.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EcbExchangeRateProviderTest {

    static byte[] fixture() throws IOException {
        try (InputStream in = EcbExchangeRateProviderTest.class.getResourceAsStream("/ecb/eurofxref-daily.xml")) {
            return in.readAllBytes();
        }
    }

    @Test
    void parsesTheEcbDailyFormat() throws IOException {
        ExchangeRates rates = EcbExchangeRateProvider.parse(fixture());

        assertThat(rates.date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(rates.unitsPerEur()).hasSize(5);
        assertThat(rates.unitsPerEur().get("USD")).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(rates.unitsPerEur().get("RON")).isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    void rejectsResponsesWithoutRates() {
        byte[] xml = "<Envelope><Cube><Cube time='2026-11-03'/></Cube></Envelope>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> EcbExchangeRateProvider.parse(xml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No exchange rates found in ECB response");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> EcbExchangeRateProvider.parse("<html>maintenance".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesDoctypesSoExternalEntitiesCannotBeResolved() {
        byte[] xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <Envelope><Cube><Cube time='2026-11-03'><Cube currency='USD' rate='&x;'/></Cube></Cube></Envelope>
                """.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> EcbExchangeRateProvider.parse(xxe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not parse ECB exchange rates");
    }
}
