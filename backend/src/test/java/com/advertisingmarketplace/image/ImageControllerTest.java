package com.advertisingmarketplace.image;

import static com.advertisingmarketplace.support.WithJwt.as;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.advertisingmarketplace.image.ImageExceptions.ImageNotFoundException;
import com.advertisingmarketplace.image.ImageExceptions.InfectedFileException;
import com.advertisingmarketplace.image.ImageExceptions.UnsupportedImageTypeException;
import com.advertisingmarketplace.image.dto.ImageResponse;
import com.advertisingmarketplace.image.scan.VirusScanUnavailableException;
import com.advertisingmarketplace.security.SecurityConfig;
import com.advertisingmarketplace.support.TestFiles;
import com.advertisingmarketplace.user.UserType;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImageController.class)
@Import(SecurityConfig.class)
class ImageControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "photo.png", "image/png", content);
    }

    @Test
    void uploadReturns201WithSignedUrl() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] png = TestFiles.png();
        when(imageService.upload(eq(USER_ID), eq(png))).thenReturn(new ImageResponse(id, "image/png", png.length,
                URI.create("https://bucket.example/images/x.png?sig"), Instant.now().plusSeconds(3600), Instant.now()));

        mockMvc.perform(multipart("/api/v1/images").file(file(png)).with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/images/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.url").value("https://bucket.example/images/x.png?sig"));
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/images").file(file(TestFiles.png())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(imageService);
    }

    @Test
    void missingFilePartIs400() throws Exception {
        mockMvc.perform(multipart("/api/v1/images").with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void infectedFileIs422() throws Exception {
        when(imageService.upload(any(), any())).thenThrow(new InfectedFileException());

        mockMvc.perform(multipart("/api/v1/images").file(file(TestFiles.eicar())).with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The file was rejected by the virus scanner"));
    }

    @Test
    void unsupportedTypeIs415() throws Exception {
        when(imageService.upload(any(), any())).thenThrow(new UnsupportedImageTypeException());

        mockMvc.perform(multipart("/api/v1/images").file(file("hello".getBytes())).with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void scannerDownIs503WithRetryAfter() throws Exception {
        when(imageService.upload(any(), any())).thenThrow(new VirusScanUnavailableException("down"));

        mockMvc.perform(multipart("/api/v1/images").file(file(TestFiles.png())).with(as(USER_ID, UserType.MARKETER)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.detail").value("File scanning is temporarily unavailable, please try again later"));
    }

    @Test
    void getPassesViewerAndAdminFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(imageService.get(id, USER_ID, true)).thenThrow(new ImageNotFoundException(id));

        mockMvc.perform(get("/api/v1/images/{id}", id).with(as(USER_ID, UserType.ADMIN)))
                .andExpect(status().isNotFound());
    }
}
