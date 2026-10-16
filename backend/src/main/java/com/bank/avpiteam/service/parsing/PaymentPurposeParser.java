package com.bank.avpiteam.service.parsing;

public interface PaymentPurposeParser {

    /**
     * Разбирает текст назначения платежа и извлекает признак
     * платежа через третье лицо и ФИО выгодоприобретателя.
     */
    ParsedPaymentPurpose parse(String rawText);
}