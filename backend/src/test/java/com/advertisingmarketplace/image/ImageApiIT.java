package com.advertisingmarketplace.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.support.AbstractIntegrationTest;
import com.advertisingmarketplace.support.ExternalServicesConfiguration;
import com.advertisingmarketplace.support.TestFiles;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

class ImageApiIT extends AbstractIntegrationTest {

    @Autowired
    private S3Client s3;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        token = accessToken(signup("Jane", "jane@example.com", "MARKETER"));
    }

    private long objectCount() {
        return s3.listObjectsV2Paginator(b -> b.bucket(ExternalServicesConfiguration.BUCKET)).contents().stream()
                .map(S3Object::key).count();
    }

    @Test
    void cleanImageIsStoredPrivatelyAndDownloadableViaSignedUrl() throws Exception {
        byte[] png = TestFiles.png();

        String body = upload(token, png)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(png.length))
                .andReturn().getResponse().getContentAsString();

        String key = jdbcTemplate.queryForObject("SELECT storage_key FROM images WHERE id = ?::uuid", String.class,
                (String) JsonPath.read(body, "$.id"));
        byte[] stored = s3.getObjectAsBytes(b -> b.bucket(ExternalServicesConfiguration.BUCKET).key(key)).asByteArray();
        assertThat(stored).isEqualTo(png);

        String url = JsonPath.read(body, "$.url");
        assertThat(url).contains("X-Amz-Signature=");
        HttpResponse<byte[]> download = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(png);
    }

    @Test
    void infectedFileIsRejectedByClamAvAndNeverStored() throws Exception {
        long objectsBefore = objectCount();

        upload(token, TestFiles.eicar())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The file was rejected by the virus scanner"));

        assertThat(objectCount()).isEqualTo(objectsBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM images", Integer.class)).isZero();
    }

    @Test
    void nonImageFileIsRejected() throws Exception {
        upload(token, "<svg onload=alert(1)>".getBytes())
                .andExpect(status().isUnsupportedMediaType());
        upload(token, new byte[0])
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM images", Integer.class)).isZero();
    }

    @Test
    void oversizedFileIsRejected() throws Exception {
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(TestFiles.png(), 0, big, 0, TestFiles.png().length);

        upload(token, big).andExpect(status().isContentTooLarge());
    }

    @Test
    void locationAndCameraMetadataIsStrippedBeforeStorage() throws Exception {
        byte[] gps = "GPS-LAT-44.4268-LON-26.1025".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.io.ByteArrayOutputStream jpeg = new java.io.ByteArrayOutputStream();
        jpeg.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xD8});
        byte[] exif = new byte[6 + gps.length];
        System.arraycopy("Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), 0, exif, 0, 6);
        System.arraycopy(gps, 0, exif, 6, gps.length);
        jpeg.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xE1, 0, (byte) (exif.length + 2)});
        jpeg.writeBytes(exif);
        jpeg.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xDA, 0, 4, 1, 2, 7, 7, 7, (byte) 0xFF, (byte) 0xD9});

        String body = upload(token, jpeg.toByteArray())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andReturn().getResponse().getContentAsString();

        String key = jdbcTemplate.queryForObject("SELECT storage_key FROM images WHERE id = ?::uuid", String.class,
                (String) JsonPath.read(body, "$.id"));
        byte[] stored = s3.getObjectAsBytes(b -> b.bucket(ExternalServicesConfiguration.BUCKET).key(key)).asByteArray();
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("GPS-LAT");
        assertThat(stored).startsWith((byte) 0xFF, (byte) 0xD8).endsWith(7, 7, 7, (byte) 0xFF, (byte) 0xD9);
    }

    @Test
    void uploadsAreRateLimitedPerUser() throws Exception {
        for (int i = 0; i < 5; i++) {
            uploadImage(token, TestFiles.png());
        }

        upload(token, TestFiles.png())
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"));
        uploadImage(accessToken(signup("Bob", "bob@example.com", "MARKETER")), TestFiles.png());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/images")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", TestFiles.png())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unattachedImageIsPrivateToItsOwner() throws Exception {
        String imageId = uploadImage(token, TestFiles.png());
        String otherToken = accessToken(signup("Bob", "bob@example.com", "CUSTOMER"));

        mockMvc.perform(bearer(get("/api/v1/images/{id}", imageId), token)).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/images/{id}", imageId), otherToken)).andExpect(status().isNotFound());
        mockMvc.perform(bearer(get("/api/v1/images/{id}", imageId), adminAccessToken())).andExpect(status().isOk());
    }
}
