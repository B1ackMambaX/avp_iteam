package com.bank.avpiteam.service.mapping;

import java.util.Set;

/**
 * Всё, что маппинг знает об операции, в виде множеств договоров-кандидатов.
 *
 * @param senderContracts       договоры, где отправитель (по ИНН) - сторона
 * @param receiverContracts     договоры, где получатель (по ИНН) - сторона
 * @param numberContracts       договоры с номером, упомянутым в назначении платежа
 * @param beneficiaryContracts  договоры, где выгодоприобретатель совпал с ФИО из назначения
 * @param genuineThirdParty     разбор назначения: платёж за третье лицо, и это не сам плательщик/получатель
 * @param parseMissing          разбор назначения недоступен (NLP не ответил за все попытки)
 */
public record OperationEvidence(
        long operationId,
        Set<Long> senderContracts,
        Set<Long> receiverContracts,
        Set<Long> numberContracts,
        Set<Long> beneficiaryContracts,
        boolean genuineThirdParty,
        boolean parseMissing
) {
}
