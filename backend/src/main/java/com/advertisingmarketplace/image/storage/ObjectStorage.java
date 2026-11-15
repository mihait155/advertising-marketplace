package com.advertisingmarketplace.image.storage;

import java.net.URI;
import java.time.Instant;

public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    void delete(String key);

    SignedUrl signedGetUrl(String key);

    record SignedUrl(URI url, Instant expiresAt) {
    }
}
