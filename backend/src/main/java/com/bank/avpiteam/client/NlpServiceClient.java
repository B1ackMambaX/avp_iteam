package com.bank.avpiteam.client;

import java.util.List;

public interface NlpServiceClient {
    ClassificationResponse classify(String paymentPurpose, double threshold,
                                    List<String> triggerWords, List<String> negationWords);
    ExtractionResponse extractName(String paymentPurpose);

    // Имена компонентов = поля JSON Python-сервиса (isThirdParty, score, decision, negativeSimilarity)
    record ClassificationResponse(boolean isThirdParty, double score, String decision,
                                  Double negativeSimilarity) {}
    record ExtractionResponse(String fullName) {}
}