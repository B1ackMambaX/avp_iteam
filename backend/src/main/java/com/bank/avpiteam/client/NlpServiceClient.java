package com.bank.avpiteam.client;

public interface NlpServiceClient {

    ClassificationResponse classify(String paymentPurpose, double threshold);

    ExtractionResponse extractName(String paymentPurpose);

    record ClassificationResponse(boolean isThirdParty, double score) {}

    record ExtractionResponse(String fullName) {}
}

