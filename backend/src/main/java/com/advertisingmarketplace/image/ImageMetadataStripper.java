package com.advertisingmarketplace.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ImageMetadataStripper {

    private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final int ORIENTATION_TAG = 0x0112;
    private static final Set<String> PNG_KEPT_CHUNKS = Set.of(
            "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "pHYs", "bKGD",
            "acTL", "fcTL", "fdAT");
    private static final Set<String> WEBP_DROPPED_CHUNKS = Set.of("EXIF", "XMP ");
    private static final byte WEBP_EXIF_FLAG = 0x08;
    private static final byte WEBP_XMP_FLAG = 0x04;

    public static byte[] strip(byte[] content, ImageType type) {
        try {
            return switch (type) {
                case JPEG -> stripJpeg(content);
                case PNG -> stripPng(content);
                case WEBP -> stripWebp(content);
                case GIF -> content;
            };
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new ImageExceptions.UnsupportedImageTypeException();
        }
    }

    static byte[] stripJpeg(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 2);
        int orientation = 1;
        int pos = 2;
        boolean orientationWritten = false;
        while (pos < in.length) {
            if ((in[pos] & 0xFF) != 0xFF) {
                throw new IllegalArgumentException("Corrupt JPEG marker");
            }
            int marker = in[pos + 1] & 0xFF;
            if (marker == 0xFF) {
                pos++;
                continue;
            }
            if (marker == 0xDA) {
                if (orientation != 1 && !orientationWritten) {
                    out.writeBytes(orientationOnlyExif(orientation));
                }
                out.write(in, pos, in.length - pos);
                return out.toByteArray();
            }
            if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                out.write(in, pos, 2);
                pos += 2;
                continue;
            }
            int length = ((in[pos + 2] & 0xFF) << 8) | (in[pos + 3] & 0xFF);
            int end = pos + 2 + length;
            if (length < 2 || end > in.length) {
                throw new IllegalArgumentException("Corrupt JPEG segment");
            }
            boolean isExif = marker == 0xE1 && startsWith(in, pos + 4, EXIF_HEADER);
            if (isExif) {
                orientation = readOrientation(in, pos + 4 + EXIF_HEADER.length, end);
            }
            boolean drop = marker == 0xE1 || marker == 0xED || marker == 0xFE
                    || (marker >= 0xE3 && marker <= 0xEF && marker != 0xEE);
            if (!drop) {
                out.write(in, pos, end - pos);
                if (marker == 0xE0 && orientation != 1 && !orientationWritten) {
                    out.writeBytes(orientationOnlyExif(orientation));
                    orientationWritten = true;
                }
            }
            pos = end;
        }
        return out.toByteArray();
    }

    static int readOrientation(byte[] in, int tiffStart, int end) {
        try {
            return parseOrientation(in, tiffStart, end);
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return 1;
        }
    }

    private static int parseOrientation(byte[] in, int tiffStart, int end) {
        ByteOrder order = in[tiffStart] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer tiff = ByteBuffer.wrap(in, tiffStart, end - tiffStart).slice().order(order);
        int ifd = tiff.getInt(4);
        int entries = Short.toUnsignedInt(tiff.getShort(ifd));
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (Short.toUnsignedInt(tiff.getShort(entry)) == ORIENTATION_TAG) {
                int value = Short.toUnsignedInt(tiff.getShort(entry + 8));
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    static byte[] orientationOnlyExif(int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);
        tiff.putShort((short) ORIENTATION_TAG).putShort((short) 3).putInt(1).putShort((short) orientation)
                .putShort((short) 0);
        tiff.putInt(0);
        int length = 2 + EXIF_HEADER.length + tiff.capacity();
        ByteBuffer segment = ByteBuffer.allocate(2 + length);
        segment.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(EXIF_HEADER).put(tiff.array());
        return segment.array();
    }

    static byte[] stripPng(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, 8);
        int pos = 8;
        while (pos < in.length) {
            int length = ByteBuffer.wrap(in, pos, 4).getInt();
            if (length < 0 || pos + 12L + length > in.length) {
                throw new IllegalArgumentException("Corrupt PNG chunk");
            }
            String type = new String(in, pos + 4, 4, StandardCharsets.ISO_8859_1);
            int total = 12 + length;
            if (PNG_KEPT_CHUNKS.contains(type)) {
                out.write(in, pos, total);
            }
            pos += total;
            if (type.equals("IEND")) {
                break;
            }
        }
        return out.toByteArray();
    }

    static byte[] stripWebp(byte[] in) {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream(in.length);
        int pos = 12;
        while (pos + 8 <= in.length) {
            String fourcc = new String(in, pos, 4, StandardCharsets.ISO_8859_1);
            int size = ByteBuffer.wrap(in, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int padded = size + (size & 1);
            if (size < 0 || pos + 8L + padded > in.length) {
                throw new IllegalArgumentException("Corrupt WebP chunk");
            }
            if (!WEBP_DROPPED_CHUNKS.contains(fourcc)) {
                byte[] chunk = Arrays.copyOfRange(in, pos, pos + 8 + padded);
                if (fourcc.equals("VP8X")) {
                    chunk[8] &= (byte) ~(WEBP_EXIF_FLAG | WEBP_XMP_FLAG);
                }
                chunks.writeBytes(chunk);
            }
            pos += 8 + padded;
        }
        ByteBuffer out = ByteBuffer.allocate(12 + chunks.size()).order(ByteOrder.LITTLE_ENDIAN);
        out.put(in, 0, 4).putInt(4 + chunks.size()).put(in, 8, 4).put(chunks.toByteArray());
        return out.array();
    }

    private static boolean startsWith(byte[] in, int offset, byte[] prefix) {
        return in.length >= offset + prefix.length
                && Arrays.equals(in, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
