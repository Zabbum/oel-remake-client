package com.github.zabbum.oelremakeclient.artloader;

public class ColorNotFoundException extends Exception {
    public ColorNotFoundException(String color) {
        super("Color not found: " + color);
    }
}