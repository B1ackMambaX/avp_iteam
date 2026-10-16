package com.bank.avpiteam.service.parsing.beneficiary;

import com.bank.avpiteam.client.NlpServiceClient;
import com.bank.avpiteam.domain.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class BeneficiaryDetectionServiceImpl implements BeneficiaryDetectionService {

    private final NlpServiceClient nlpServiceClient;
    private final double semanticThreshold;

    public BeneficiaryDetectionServiceImpl(NlpServiceClient nlpServiceClient,
                                           @Value("${nlp.semantic.threshold}") double semanticThreshold) {
        this.nlpServiceClient = nlpServiceClient;
        this.semanticThreshold = semanticThreshold;
    }

    @Override
    public BeneficiaryDetectionResult detect(Operation operation) {
        String purpose = operation.getPaymentPurpose();

        var classification = nlpServiceClient.classify(purpose);
        boolean isThirdParty = classification.isThirdParty() && classification.score() >= semanticThreshold;

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

    /**
     * Черновая сверка (вопрос 6 в ответах заказчика): если извлечённое имя совпадает
     * с отправителем или получателем операции — это не третье лицо, а ложное
     * срабатывание классификатора на формулировках вида "перевод за ...".
     * TODO: заменить normalize() на общую SQL/Java-функцию normalize_full_name,
     * если второй бэк вынесет её в БД (см. комментарий к PR).
     */
    private boolean isGenuineThirdParty(String extractedName, Operation operation) {
        String normalizedExtracted = normalize(extractedName);
        return !normalizedExtracted.equals(normalize(operation.getSenderName()))
                && !normalizedExtracted.equals(normalize(operation.getReceiverName()));
    }

    private String normalize(String name) {
        if (name == null) return "";
        return name.trim()
                .toLowerCase(Locale.of("ru"))
                .replaceAll("\\s+", " ");
    }
}