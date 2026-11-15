package com.advertisingmarketplace.image;

import com.advertisingmarketplace.image.dto.ImageResponse;
import com.advertisingmarketplace.security.CurrentUser;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageResponse> upload(@RequestPart("file") MultipartFile file, Authentication authentication)
            throws IOException {
        ImageResponse image = imageService.upload(CurrentUser.id(authentication), file.getBytes());
        return ResponseEntity.created(URI.create("/api/v1/images/" + image.id())).body(image);
    }

    @GetMapping("/{id}")
    public ImageResponse get(@PathVariable UUID id, Authentication authentication) {
        return imageService.get(id, CurrentUser.id(authentication), CurrentUser.isAdmin(authentication));
    }
}
