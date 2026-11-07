package com.bank.avpiteam.service.parsing.beneficiary;

import com.bank.avpiteam.client.NlpServiceClient;
import com.bank.avpiteam.domain.Operation;
import com.bank.avpiteam.service.mapping.Normalization;
import com.bank.avpiteam.settings.EffectiveNlpSettings;
import com.bank.avpiteam.settings.NlpSettingsProvider;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class BeneficiaryDetectionServiceImpl implements BeneficiaryDetectionService {

    private final NlpServiceClient nlpServiceClient;
    private final DetectionMetrics metrics;
    private final NlpSettingsProvider settingsProvider;

    // Конструктор со всеми тремя зависимостями
    public BeneficiaryDetectionServiceImpl(NlpServiceClient nlpServiceClient,
                                           DetectionMetrics metrics,
                                           NlpSettingsProvider settingsProvider) {
        this.nlpServiceClient = nlpServiceClient;
        this.metrics = metrics;
        this.settingsProvider = settingsProvider;
    }

    @Override
    public BeneficiaryDetectionResult detect(Operation operation) {
        BeneficiaryDetectionResult result = metrics.time(() -> doDetect(operation));
        metrics.record(result);
        return result;
    }

    private BeneficiaryDetectionResult doDetect(Operation operation) {
        String purpose = operation.getPaymentPurpose();

        // Получаем настройки один раз
        EffectiveNlpSettings settings = settingsProvider.current();

        // Передаём в Python порог и списки слов
        var classification = nlpServiceClient.classify(
                purpose,
                settings.threshold(),
                settings.triggerWords(),
                settings.negationWords()
        );

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

    static boolean isNotOperationParty(String extractedName, String senderName, String receiverName) {
        String name = Normalization.fullName(extractedName);
        return !Objects.equals(name, Normalization.fullName(senderName))
                && !Objects.equals(name, Normalization.fullName(receiverName));
    }
}