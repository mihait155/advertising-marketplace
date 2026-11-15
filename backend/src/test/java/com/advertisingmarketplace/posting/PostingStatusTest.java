package com.advertisingmarketplace.posting;

import static org.assertj.core.api.Assertions.assertThat;

import com.advertisingmarketplace.user.UserType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PostingStatusTest {

    @ParameterizedTest
    @CsvSource({
            "DRAFT, OPEN, true",
            "DRAFT, CLOSED, false",
            "DRAFT, DRAFT, false",
            "OPEN, CLOSED, true",
            "OPEN, DRAFT, false",
            "OPEN, OPEN, false",
            "CLOSED, OPEN, true",
            "CLOSED, DRAFT, false",
            "CLOSED, CLOSED, false"
    })
    void transitions(PostingStatus from, PostingStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({
            "MARKETER_POSTING, CUSTOMER",
            "CUSTOMER_POSTING, MARKETER"
    })
    void offersComeFromTheOtherSide(PostingCategory category, UserType offerMaker) {
        assertThat(category.offerMakerType()).isEqualTo(offerMaker);
    }
}
