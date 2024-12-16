package com.github.zabbum.oelremakeclient;

import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.gui2.Window;
import lombok.Data;

import java.util.Map;

@Data
public class GameProperties {
    private Map<String, String> langMap;
    private SeparateTextGUIThread textGUIThread;
    private Thread mainThread;
    private Window window;
    private Panel contentPanel;
    private Integer playerId;
}
