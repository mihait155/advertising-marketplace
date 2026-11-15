package com.advertisingmarketplace.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.advertisingmarketplace.support.TestFiles;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void detectsSupportedFormatsFromMagicBytes() {
        assertThat(ImageType.detect(TestFiles.png())).contains(ImageType.PNG);
        assertThat(ImageType.detect(TestFiles.jpegHeader())).contains(ImageType.JPEG);
        assertThat(ImageType.detect(ascii("GIF89a...."))).contains(ImageType.GIF);
        assertThat(ImageType.detect(ascii("GIF87a...."))).contains(ImageType.GIF);
        assertThat(ImageType.detect(ascii("RIFF\0\0\0\0WEBPVP8 "))).contains(ImageType.WEBP);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>"))).isEmpty();
        assertThat(ImageType.detect(ascii("%PDF-1.7"))).isEmpty();
        assertThat(ImageType.detect(ascii("RIFF\0\0\0\0WAVEfmt "))).isEmpty();
        assertThat(ImageType.detect(ascii("GIF8"))).isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0x89, 'P', 'N'})).isEmpty();
    }

    @Test
    void exposesContentTypeAndExtension() {
        assertThat(ImageType.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.WEBP.contentType()).isEqualTo("image/webp");
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
