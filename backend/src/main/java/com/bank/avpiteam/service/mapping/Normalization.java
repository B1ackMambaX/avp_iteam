package com.bank.avpiteam.service.mapping;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Java-копии правил нормализации из V2__mapping_schema.sql.
 * Должны совпадать с generated columns в БД, иначе поиск по *_norm не найдёт совпадений.
 */
public final class Normalization {

    private static final Pattern NOT_ALNUM = Pattern.compile("[^0-9A-Za-zА-Яа-яЁё]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Normalization() {
    }

    /**
     * Как contracts.contract_number_norm: только буквы и цифры, верхний регистр, Ё -> Е.
     * "№ 110-кр" -> "110КР"
     */
    public static String contractNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = NOT_ALNUM.matcher(raw).replaceAll("")
                .toUpperCase(Locale.ROOT)
                .replace('Ё', 'Е');
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Как clients.full_name_norm / operation_parse_result.extracted_name_norm:
     * нижний регистр, ё -> е, одиночные пробелы.
     */
    public static String fullName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = SPACES.matcher(raw.toLowerCase(Locale.ROOT).replace('ё', 'е')).replaceAll(" ").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
