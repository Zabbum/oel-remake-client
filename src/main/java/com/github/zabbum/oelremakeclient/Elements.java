package com.github.zabbum.oelremakeclient;

import com.googlecode.lanterna.gui2.Button;

public class Elements {
    // The new confirm button
    public static Button confirmButton(Confirm confirm, String text) {
        return new Button(text, confirm::confirm);
    }
}
