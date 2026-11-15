package com.advertisingmarketplace.messaging.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.advertisingmarketplace.messaging.MessagingProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContactDetailsMaskerTest {

    private static MessagingProperties properties(boolean mask) {
        return new MessagingProperties(30, 10, mask, Duration.ofSeconds(60), Duration.ofMinutes(30), 5);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "mail me at jane.doe+ads@example.co.uk please|mail me at [contact details hidden] please",
            "call +40 712 345 678 tomorrow|call [contact details hidden] tomorrow",
            "or (021) 555-0199.|or [contact details hidden].",
            "budget is 1500 EUR for 3 streams|budget is 1500 EUR for 3 streams",
            "deliver by 2026-12-01|deliver by 2026-12-01",
            "version 1.2.3 of the overlay|version 1.2.3 of the overlay",
            "my number is 0712345678|my number is [contact details hidden]",
            "or 0712 345 678|or [contact details hidden]",
            "pay range 1500-2000 EUR|pay range 1500-2000 EUR",
            "10000-20000 for the campaign|10000-20000 for the campaign",
            "I have 250000 followers|I have 250000 followers"
    })
    void masksEmailsAndPhoneNumbersOnly(String input, String expected) {
        assertThat(new ContactDetailsMasker(properties(true)).apply(input)).isEqualTo(expected);
    }

    @Test
    void disabledByDefault() {
        assertThat(new ContactDetailsMasker(properties(false)).apply("jane@example.com")).isEqualTo("jane@example.com");
    }
}
