package com.github.zabbum.oelremakeclient;

import com.github.zabbum.oelrlib.game.BaseGame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.*;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;

@Slf4j
@AllArgsConstructor
public class WaitStompSessionHandler implements StompSessionHandler {
    private BlockingQueue<BaseGame> gamesQueue;
    private BaseGame baseGame;

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("Connected");
        session.subscribe("/topic/game-progress/" + baseGame.getGameId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return BaseGame.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("Received: {}", payload);
                try {
                    gamesQueue.put((BaseGame)payload);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {

    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {

    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return null;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {

    }
}
