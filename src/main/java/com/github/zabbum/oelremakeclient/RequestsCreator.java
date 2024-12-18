package com.github.zabbum.oelremakeclient;

import com.github.zabbum.oelrlib.game.BaseGame;
import com.github.zabbum.oelrlib.requests.*;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

@AllArgsConstructor
public class RequestsCreator {
    private String wsEndpointUrl;
    private String httpEndPointUrl;

    public BaseGame oelRequest(OelRequest oelRequest) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OelRequest> entity = new HttpEntity<>(oelRequest, headers);

        return restTemplate.postForObject(httpEndPointUrl + getPath(oelRequest.getClass()), entity, BaseGame.class);
    }

    private String getPath(Class<? extends OelRequest> oelRequest) {
        if (oelRequest.equals(StarterRequest.class))
            return "/start";
        if (oelRequest.equals(JoinRequest.class))
            return "/connect";
        if (oelRequest.equals(BuyOilfieldRequest.class))
            return "/buyOilfield";
        if (oelRequest.equals(BuyIndustryRequest.class))
            return "/buyIndustry";
        if (oelRequest.equals(BuyProductsRequest.class))
            return "/buyProducts";
        if (oelRequest.equals(ChangePricesRequest.class))
            return "/changePrices";
        if (oelRequest.equals(SabotageRequest.class))
            return "/sabotage";
        if (oelRequest.equals(PassRequest.class))
            return "/pass";
        if (oelRequest.equals(SummaryRequest.class))
            return "/summary";

        throw new RuntimeException("No path for this tape of request: " + oelRequest);
    }
}
