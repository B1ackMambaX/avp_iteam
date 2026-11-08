package com.bank.avpiteam.service.mapping;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлекает номера договоров из назначения платежа.
 * Номер ищется только после слова "договор" / "дог." (до двух слов между ними:
 * "по договору поставки № 12-2024"), чтобы не путать с номерами счетов и накладных.
 * Номер обязан содержать цифру; допускается короткий буквенный префикс
 * через пробел или дефис: "ДГ-00000123", "ДГ 00000123", "110-кр", "13/КР-2021".
 */
public final class ContractNumberExtractor {

    private static final Pattern CONTRACT_NUMBER = Pattern.compile(
            "(?:договор\\w*|дог\\.)(?:\\s+[а-яё]+){0,2}?\\s*(?:№|#)?\\s*"
                    + "([A-Za-zА-Яа-яЁё]{0,4}[\\s\\-]?\\d[\\dA-Za-zА-Яа-яЁё\\-/.]*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private ContractNumberExtractor() {
    }

    /** Нормализованные номера договоров в порядке появления в тексте. */
    public static Set<String> extractNormalized(String paymentPurpose) {
        Set<String> result = new LinkedHashSet<>();
        if (paymentPurpose == null || paymentPurpose.isBlank()) {
            return result;
        }
        Matcher matcher = CONTRACT_NUMBER.matcher(paymentPurpose);
        while (matcher.find()) {
            String normalized = Normalization.contractNumber(matcher.group(1));
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }
}
