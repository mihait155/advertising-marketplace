package com.advertisingmarketplace.image.scan;

public record ScanResult(boolean clean, String signature) {

    public static ScanResult ok() {
        return new ScanResult(true, null);
    }

    public static ScanResult infected(String signature) {
        return new ScanResult(false, signature);
    }
}
