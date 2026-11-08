package com.bank.avpiteam.util;

/**
 * Определение типа владельца счёта по номеру расчётного счёта
 * на основе Плана счетов ЦБ РФ (Положение № 579-П).
 * Номер счёта - 20 цифр, первые 5 цифр - это номер балансового
 * счёта, который и указывает на тип владельца.
 */
public final class AccountTypeResolver {

    private AccountTypeResolver() {
    }

    public static AccountOwnerType resolve(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 5) {
            return AccountOwnerType.UNKNOWN;
        }

        String prefix5 = accountNumber.substring(0, 5);
        String prefix3 = accountNumber.substring(0, 3);

        // ИП - счета второго порядка 40802
        if (prefix5.equals("40802")) {
            return AccountOwnerType.ENTREPRENEUR;
        }

        // Физлица - текущие счета 40817, 40820 (нерезиденты)
        if (prefix5.equals("40817") || prefix5.equals("40820")) {
            return AccountOwnerType.INDIVIDUAL;
        }

        // Вклады физлиц
        if (prefix3.equals("423")) {
            return AccountOwnerType.INDIVIDUAL;
        }

        // Юрлица - балансовый счёт первого порядка 407
        if (prefix3.equals("407")) {
            return AccountOwnerType.LEGAL_ENTITY;
        }

        return AccountOwnerType.UNKNOWN;
    }

    public enum AccountOwnerType {
        INDIVIDUAL,
        ENTREPRENEUR,
        LEGAL_ENTITY,
        UNKNOWN
    }
}