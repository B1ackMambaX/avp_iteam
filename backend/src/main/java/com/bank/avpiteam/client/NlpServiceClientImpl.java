package com.bank.avpiteam.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class NlpServiceClientImpl implements NlpServiceClient {

    private final WebClient webClient;

    public NlpServiceClientImpl(WebClient.Builder builder,
                                @Value("${nlp.service.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public ClassificationResponse classify(String paymentPurpose) {
        return webClient.post()
                .uri("/classify")
                .bodyValue(new TextRequest(paymentPurpose))
                .retrieve()
                .bodyToMono(ClassificationResponse.class)
                .block();
    }

    @Override
    public ExtractionResponse extractName(String paymentPurpose) {
        return webClient.post()
                .uri("/extract-name")
                .bodyValue(new TextRequest(paymentPurpose))
                .retrieve()
                .bodyToMono(ExtractionResponse.class)
                .block();
    }

    private record TextRequest(String text) {}
}