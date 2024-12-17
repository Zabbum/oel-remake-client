package com.github.zabbum.oelremakeclient;

import com.github.zabbum.oelrlib.game.BaseGame;
import com.github.zabbum.oelrlib.requests.JoinRequest;
import com.github.zabbum.oelrlib.requests.StarterRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@AllArgsConstructor
public class RequestsCreator {
    private String wsEndpointUrl;
    private String httpEndPointUrl;

    public BaseGame createGame(String playerName, int playersAmount) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        StarterRequest starterRequest = StarterRequest.builder()
                .playerName(playerName)
                .playersAmount(playersAmount)
                .build();

        HttpEntity<StarterRequest> entity = new HttpEntity<>(starterRequest, headers);

        return restTemplate.postForObject(httpEndPointUrl + "/start", entity, BaseGame.class);
    }

    public BaseGame joinGame(String playerName, String gameId) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JoinRequest joinRequest = JoinRequest.builder()
                .gameId(gameId)
                .playerName(playerName)
                .build();

        HttpEntity<JoinRequest> entity = new HttpEntity<>(joinRequest, headers);

        return restTemplate.postForObject(httpEndPointUrl + "/connect", entity, BaseGame.class);
    }
}
