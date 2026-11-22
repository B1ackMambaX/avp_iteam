package com.bank.avpiteam.review;

import com.bank.avpiteam.service.mapping.MappingCard.PartyInfo;

import java.util.List;

/**
 * Договор в поиске для ручного выбора. Номер не уникален - договоры с одинаковым номером
 * различаются сторонами (роль, имя, ИНН). В ручное решение передаётся contractId.
 */
public record ContractRow(long contractId, String contractNumber, String contractType, List<PartyInfo> parties) {
}
