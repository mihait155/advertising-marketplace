package com.advertisingmarketplace.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.advertisingmarketplace.support.TestFiles;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class ImageMetadataStripperTest {

    private static final byte[] GPS_MARKER = "GPS-LAT-44.4268".getBytes(StandardCharsets.US_ASCII);

    private static byte[] segment(int marker, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + payload.length);
        buffer.put((byte) 0xFF).put((byte) marker).putShort((short) (payload.length + 2)).put(payload);
        return buffer.array();
    }

    private static byte[] exifWithOrientation(int orientation, ByteOrder order) {
        ByteBuffer tiff = ByteBuffer.allocate(26 + GPS_MARKER.length).order(order);
        tiff.put(order == ByteOrder.LITTLE_ENDIAN ? (byte) 'I' : (byte) 'M')
                .put(order == ByteOrder.LITTLE_ENDIAN ? (byte) 'I' : (byte) 'M')
                .putShort((short) 42).putInt(8).putShort((short) 1)
                .putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0)
                .putInt(0).put(GPS_MARKER);
        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.writeBytes("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
        exif.writeBytes(tiff.array());
        return exif.toByteArray();
    }

    private static byte[] jpeg(byte[]... segments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8});
        for (byte[] s : segments) {
            out.writeBytes(s);
        }
        out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xDA, 0, 4, 1, 2, 9, 9, 9, (byte) 0xFF, (byte) 0xD9});
        return out.toByteArray();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    void removesExifXmpAndCommentsFromJpegButKeepsImageData() {
        byte[] app0 = segment(0xE0, "JFIF\0\1\1\0\0\1\0\1\0\0".getBytes(StandardCharsets.ISO_8859_1));
        byte[] icc = segment(0xE2, "ICC_PROFILE\0keep".getBytes(StandardCharsets.ISO_8859_1));
        byte[] input = jpeg(app0, segment(0xE1, exifWithOrientation(1, ByteOrder.BIG_ENDIAN)), icc,
                segment(0xE1, "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta/>".getBytes(StandardCharsets.ISO_8859_1)),
                segment(0xFE, "shot by Jane at home".getBytes(StandardCharsets.ISO_8859_1)));

        byte[] output = ImageMetadataStripper.strip(input, ImageType.JPEG);

        assertThat(contains(output, GPS_MARKER)).isFalse();
        assertThat(contains(output, "xmpmeta".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(contains(output, "shot by Jane".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(contains(output, "ICC_PROFILE".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(contains(output, "JFIF".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(output).endsWith(9, 9, 9, (byte) 0xFF, (byte) 0xD9);
    }

    @Test
    void keepsOnlyTheOrientationOfRotatedPhotos() {
        byte[] input = jpeg(segment(0xE1, exifWithOrientation(6, ByteOrder.LITTLE_ENDIAN)));

        byte[] output = ImageMetadataStripper.strip(input, ImageType.JPEG);

        assertThat(contains(output, GPS_MARKER)).isFalse();
        int exifStart = 4 + 2;
        assertThat(ImageMetadataStripper.readOrientation(output, exifStart + 6, output.length)).isEqualTo(6);
    }

    @Test
    void removesTextAndExifChunksFromPng() {
        byte[] png = TestFiles.png();
        byte[] withText = insertBeforeIend(png, pngChunk("tEXt", "Author\0Jane Doe GPS-LAT-44.4268"));
        withText = insertBeforeIend(withText, pngChunk("eXIf", "MM\0*GPS-LAT-44.4268"));

        byte[] output = ImageMetadataStripper.strip(withText, ImageType.PNG);

        assertThat(contains(output, GPS_MARKER)).isFalse();
        assertThat(output).isEqualTo(png);
    }

    @Test
    void removesExifAndXmpFromWebpAndClearsTheFlags() {
        byte[] vp8x = webpChunk("VP8X", new byte[] {0x0C, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        byte[] image = webpChunk("VP8L", new byte[] {0x2F, 1, 2, 3, 4});
        byte[] exif = webpChunk("EXIF", GPS_MARKER);
        byte[] xmp = webpChunk("XMP ", "<x:xmpmeta/>".getBytes(StandardCharsets.US_ASCII));
        byte[] input = webp(vp8x, image, exif, xmp);

        byte[] output = ImageMetadataStripper.strip(input, ImageType.WEBP);

        assertThat(contains(output, GPS_MARKER)).isFalse();
        assertThat(contains(output, "xmpmeta".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageType.detect(output)).contains(ImageType.WEBP);
        assertThat(ByteBuffer.wrap(output, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()).isEqualTo(output.length - 8);
        assertThat(output[20] & 0x0C).isZero();
        assertThat(contains(output, new byte[] {0x2F, 1, 2, 3, 4})).isTrue();
    }

    @Test
    void corruptFilesAreRejected() {
        byte[] truncated = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x10, 0x00, 1, 2};

        assertThatThrownBy(() -> ImageMetadataStripper.strip(truncated, ImageType.JPEG))
                .isInstanceOf(ImageExceptions.UnsupportedImageTypeException.class);
    }

    private static byte[] pngChunk(String type, String data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] dataBytes = data.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(dataBytes);
        return ByteBuffer.allocate(12 + dataBytes.length).putInt(dataBytes.length).put(typeBytes).put(dataBytes)
                .putInt((int) crc.getValue()).array();
    }

    private static byte[] insertBeforeIend(byte[] png, byte[] chunk) {
        int iend = png.length - 12;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, iend);
        out.writeBytes(chunk);
        out.write(png, iend, 12);
        return out.toByteArray();
    }

    private static byte[] webpChunk(String fourcc, byte[] data) {
        int padded = data.length + (data.length & 1);
        ByteBuffer buffer = ByteBuffer.allocate(8 + padded).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(fourcc.getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
        return buffer.array();
    }

    private static byte[] webp(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            body.writeBytes(chunk);
        }
        ByteBuffer out = ByteBuffer.allocate(12 + body.size()).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(4 + body.size())
                .put("WEBP".getBytes(StandardCharsets.US_ASCII)).put(body.toByteArray());
        return out.array();
    }
}
