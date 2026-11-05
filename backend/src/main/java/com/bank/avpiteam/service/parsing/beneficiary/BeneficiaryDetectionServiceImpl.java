package com.bank.avpiteam.service.parsing.beneficiary;

import com.bank.avpiteam.client.NlpServiceClient;
import com.bank.avpiteam.domain.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.bank.avpiteam.service.mapping.Normalization;
import java.util.Objects;

@Service
public class BeneficiaryDetectionServiceImpl implements BeneficiaryDetectionService {

    private final NlpServiceClient nlpServiceClient;
    private final double semanticThreshold;
    private final DetectionMetrics metrics;

    public BeneficiaryDetectionServiceImpl(NlpServiceClient nlpServiceClient,
                                           @Value("${nlp.semantic.threshold}") double semanticThreshold,
                                           DetectionMetrics metrics) {
        this.nlpServiceClient = nlpServiceClient;
        this.semanticThreshold = semanticThreshold;
        this.metrics = metrics;
    }

    @Override
    public BeneficiaryDetectionResult detect(Operation operation) {
        BeneficiaryDetectionResult result = metrics.time(() -> doDetect(operation));
        metrics.record(result);
        return result;
    }

    private BeneficiaryDetectionResult doDetect(Operation operation) {
        String purpose = operation.getPaymentPurpose();

        var classification = nlpServiceClient.classify(purpose, semanticThreshold);
        boolean isThirdParty = classification.isThirdParty();

        if (!isThirdParty) {
            return new BeneficiaryDetectionResult(
                    false, null, classification.score(), ClassificationMethod.SEMANTIC, false
            );
        }

        String extractedName = nlpServiceClient.extractName(purpose).fullName();

        boolean isGenuine = extractedName != null && isGenuineThirdParty(extractedName, operation);

        return new BeneficiaryDetectionResult(
                true, extractedName, classification.score(), ClassificationMethod.NLP_NER, isGenuine
        );
    }

    private boolean isGenuineThirdParty(String extractedName, Operation operation) {
        return isNotOperationParty(extractedName, operation.getSenderName(), operation.getReceiverName());
    }
    // TODO: то же правило есть в IntakeService.isNotOperationParty (marker-режим).
    //Свести в одно место, согласовав с коллегой (зона mapping/pipeline).
    static boolean isNotOperationParty(String extractedName, String senderName, String receiverName) {
        String name = Normalization.fullName(extractedName);
        return !Objects.equals(name, Normalization.fullName(senderName))
                && !Objects.equals(name, Normalization.fullName(receiverName));
    }

}