package com.github.zabbum.oelremakeclient.artloader;

public class BadImageSizeProvidedException extends Exception {
    public BadImageSizeProvidedException() {
        super("Image size provided in file is smaller than actual ASCII art size.");
    }
}
