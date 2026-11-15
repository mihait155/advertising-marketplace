package com.advertisingmarketplace.image.storage;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.storage.s3")
public record S3Properties(
        @NotBlank String bucket,
        @DefaultValue("eu-central-1") String region,
        String endpoint,
        String publicEndpoint,
        @DefaultValue("false") boolean pathStyleAccess,
        String accessKey,
        String secretKey,
        @DefaultValue("false") boolean createBucket,
        @DefaultValue("1h") Duration signedUrlTtl) {
}
