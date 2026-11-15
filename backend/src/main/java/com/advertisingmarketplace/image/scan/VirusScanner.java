package com.advertisingmarketplace.image.scan;

public interface VirusScanner {

    ScanResult scan(byte[] content);
}
