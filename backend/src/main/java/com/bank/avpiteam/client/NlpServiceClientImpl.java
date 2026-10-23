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
    public ClassificationResponse classify(String paymentPurpose, double threshold) {
        return webClient.post()
                .uri("/classify")
                .bodyValue(new ClassifyRequest(paymentPurpose, threshold))
                .retrieve()
                .bodyToMono(ClassificationResponse.class)
                .block();
    }

    @Override
    public ExtractionResponse extractName(String paymentPurpose) {
        return webClient.post()
                .uri("/extract-name")
                .bodyValue(new ExtractRequest(paymentPurpose))
                .retrieve()
                .bodyToMono(ExtractionResponse.class)
                .block();
    }

    private record ClassifyRequest(String text, double threshold) {}
    private record ExtractRequest(String text) {}
}