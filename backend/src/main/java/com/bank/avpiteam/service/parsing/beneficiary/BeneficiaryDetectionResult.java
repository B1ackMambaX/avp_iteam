package com.bank.avpiteam.service.parsing.beneficiary;

public record BeneficiaryDetectionResult(
        boolean isThirdParty,          // сырое решение семантического классификатора
        String extractedName,          // null, если не третье лицо или NER не распознал
        double classificationScore,    // score классификатора, 0..1
        ClassificationMethod method,
        boolean isGenuineThirdParty    // после сверки с операцией (вопрос 6) — на это должен смотреть маппинг
) {}