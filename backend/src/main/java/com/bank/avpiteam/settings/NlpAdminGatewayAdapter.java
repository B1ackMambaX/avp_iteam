package com.bank.avpiteam.settings;

import com.bank.avpiteam.client.NlpServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
class NlpAdminGatewayAdapter implements NlpAdminGateway {

    private final NlpServiceClient client;
    private final RestClient rest;

    NlpAdminGatewayAdapter(NlpServiceClient client,
                           @Value("${nlp.service.base-url}") String baseUrl) {
        this.client = client;
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Classification classify(String text, double threshold,
                                   List<String> triggerWords, List<String> negationWords) {
        var r = client.classify(text, threshold, triggerWords, negationWords);
        return new Classification(r.decision(), r.score(), r.isThirdParty());
    }

    @Override
    public String extractName(String text) {
        return client.extractName(text).fullName();
    }

    @Override
    public Examples examples() {
        return rest.get().uri("/examples").retrieve().body(Examples.class);
    }
}