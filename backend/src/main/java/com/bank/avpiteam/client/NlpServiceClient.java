package com.bank.avpiteam.client;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public interface NlpServiceClient {

    ClassificationResponse classify(String paymentPurpose, double threshold,
                                    List<String> triggerWords, List<String> negationWords);
    ExtractionResponse extractName(String paymentPurpose);

    record ClassificationResponse(
            @JsonProperty("thirdParty")    boolean thirdParty,
            @JsonProperty("decision")      String decision,
            @JsonProperty("score")         double score,
            @JsonProperty("negativeSimilarity") Double negativeSimilarity
    ) {}

    record ExtractionResponse(@JsonProperty("fullName") String fullName) {}
}