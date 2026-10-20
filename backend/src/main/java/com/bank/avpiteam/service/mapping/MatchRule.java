package com.bank.avpiteam.service.mapping;

/**
 * Правила, по которым договор-кандидат набирает score.
 * Сохраняются в operation_mapping_candidate.matched_rules вместе с весом.
 */
public enum MatchRule {
    PARTY_SENDER,       // отправитель (по ИНН) - сторона договора
    PARTY_RECEIVER,     // получатель (по ИНН) - сторона договора
    BOTH_SIDES,         // и отправитель, и получатель - стороны договора
    CONTRACT_NUMBER,    // номер договора упомянут в назначении платежа
    BENEFICIARY_NAME    // ФИО третьего лица совпало с выгодоприобретателем договора
}
