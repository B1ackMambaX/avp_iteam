package com.bank.avpiteam.service.mapping;

import com.bank.avpiteam.domain.Operation.OperationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Типизированная карточка маппинга операции (BE-2): операция, результат разбора, кандидаты.
 * parse = null - назначение ещё не разобрано; candidates пустой - кандидатов нет.
 * Карточка - результат ручного решения (BE-4).
 */
public record MappingCard(OperationInfo operation, ParseInfo parse, List<CandidateInfo> candidates) {

    /**
     * @param version       версия записи для ручного решения: передаётся обратно в resolve,
     *                      чтобы обнаружить, что карточку изменили после прочтения
     * @param mappingSource AUTO (маппинг) или MANUAL (оператор); null, если договор не привязывался
     */
    public record OperationInfo(
            long id,
            String reference,
            LocalDateTime operationDate,
            String senderInn,
            String senderName,
            String receiverInn,
            String receiverName,
            BigDecimal amount,
            String currencyCode,
            String paymentPurpose,
            OperationStatus status,
            String mappingReason,
            Long contractId,
            String contractNumber,
            String mappedBy,
            OffsetDateTime mappedAt,
            String mappingSource,
            long version
    ) {
    }

    public record ParseInfo(
            String parser,
            String parserVersion,
            String classificationMethod,
            Double classificationScore,
            boolean thirdParty,
            Boolean genuineThirdParty,
            String extractedName,
            String extractedNameNorm,
            String partyRole,
            OffsetDateTime parsedAt
    ) {
    }

    /** @param matchedRules сработавшие правила скоринга и их веса */
    public record CandidateInfo(
            long contractId,
            String contractNumber,
            String contractType,
            BigDecimal score,
            Map<MatchRule, Double> matchedRules,
            List<PartyInfo> parties
    ) {
    }

    /** Сторона договора: роль, клиент, отображаемое имя (организация или ФИО), ИНН. */
    public record PartyInfo(String role, long clientId, String name, String inn) {
    }
}
