package com.bank.avpiteam.benchmark;

/**
 * Одна строка размеченного тестового датасета -
 * назначение платежа и эталонный (ожидаемый) ответ.
 */
public record TestDatasetRow(
        long id,
        String paymentPurpose,
        boolean expectedThirdParty,
        String expectedBeneficiaryName
) {
}