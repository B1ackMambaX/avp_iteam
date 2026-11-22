package com.bank.avpiteam.review;

/**
 * Ручное решение по операции из очереди.
 *
 * @param contractId      договор для подтверждения; null - отклонение ("договор не найден")
 * @param comment         до 1000 символов; обязателен при отклонении и при договоре не из кандидатов
 * @param expectedVersion MappingCard.operation().version() из прочитанной карточки;
 *                        null - без проверки (повторное решение всё равно отклонится по статусу)
 */
public record ResolveCommand(long operationId, Long contractId, String comment, Long expectedVersion) {
}
