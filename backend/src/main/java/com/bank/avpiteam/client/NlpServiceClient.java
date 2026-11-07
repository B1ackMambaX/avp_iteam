package com.bank.avpiteam.client;

import java.util.List;

public interface NlpServiceClient {
    ClassificationResponse classify(String paymentPurpose, double threshold,
                                    List<String> triggerWords, List<String> negationWords);
    ExtractionResponse extractName(String paymentPurpose);

    record ClassificationResponse(boolean isThirdParty, double score, String decision) {}
    record ExtractionResponse(String fullName) {}
}