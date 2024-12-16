package com.github.zabbum.oelremakeclient;

import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.screen.Screen;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.IOException;

@Slf4j
public class Application {
    public static void main(String[] args) {

        Screen screen = null;
        SeparateTextGUIThread textGUIThread = null;

        try {
            Game game = new Game(args);

            screen = game.getScreen();
            textGUIThread = game.getGameProperties().getTextGUIThread();

            game.start();
        }
        catch (IOException | FontFormatException e) {
            log.error(e.getMessage(), e);
        }
        catch (InterruptedException ignored) {
        }
        finally {
            if (screen != null) {
                try {
                    screen.stopScreen();
                    if (textGUIThread != null)
                        textGUIThread.stop();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
}
