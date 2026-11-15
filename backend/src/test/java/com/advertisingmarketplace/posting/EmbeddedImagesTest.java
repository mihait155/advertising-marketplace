package com.advertisingmarketplace.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.advertisingmarketplace.posting.EmbeddedImages.InvalidPostingContentException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmbeddedImagesTest {

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();

    @Test
    void extractsImageReferencesInOrderWithoutDuplicates() {
        String text = """
                # My channel
                ![banner](image:%s)
                Some text with a [normal link](https://example.com).
                ![](image:%s "with a title")
                ![again](image:%s)
                """.formatted(second, first, second);

        assertThat(EmbeddedImages.referencedIn(text)).containsExactly(second, first);
    }

    @Test
    void textWithoutImagesHasNoReferences() {
        assertThat(EmbeddedImages.referencedIn("Just text, a [link](https://x.y) and an ! mark")).isEmpty();
    }

    @Test
    void acceptsUppercaseIds() {
        assertThat(EmbeddedImages.referencedIn("![x](image:" + first.toString().toUpperCase() + ")"))
                .containsExactly(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "![pixel](https://tracker.example/p.gif)",
            "![x](data:image/png;base64,AAAA)",
            "![x](image:not-a-uuid)",
            "![x](/api/v1/images/123)",
            "![x]()"
    })
    void rejectsImagesThatAreNotUploadedImages(String text) {
        assertThatThrownBy(() -> EmbeddedImages.referencedIn(text))
                .isInstanceOf(InvalidPostingContentException.class)
                .hasMessageStartingWith("Only uploaded images can be embedded");
    }
}
