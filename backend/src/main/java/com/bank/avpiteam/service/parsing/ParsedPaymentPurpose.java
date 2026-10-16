package com.bank.avpiteam.service.parsing;

/**
 * Результат разбора поля "назначение платежа".
 *
 * @param thirdParty       признак того, что это платёж через третье лицо
 * @param beneficiaryName  извлечённое ФИО выгодоприобретателя (null, если не найдено)
 * @param confidence       условная уверенность парсера в результате, 0..1.
 *                         Для гипотезы А - эвристическая оценка, не настоящая
 *                         вероятность, но полезна для сравнения с гипотезой Б.
 */
public record ParsedPaymentPurpose(
        boolean thirdParty,
        String beneficiaryName,
        double confidence
) {
    public static ParsedPaymentPurpose notThirdParty() {
        return new ParsedPaymentPurpose(false, null, 0.9);
    }
}