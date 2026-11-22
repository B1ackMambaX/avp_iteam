package com.bank.avpiteam.review;

import com.bank.avpiteam.domain.Operation.OperationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Строка списка операций и очереди ручного разбора.
 *
 * @param date            дата операции (из operationDate)
 * @param bestScore       лучший score среди кандидатов; null, если кандидатов нет
 * @param candidatesCount число сохранённых кандидатов; 0, если их нет
 */
public record OperationRow(
        long operationId,
        LocalDate date,
        LocalDateTime operationDate,
        BigDecimal amount,
        String currencyCode,
        String senderName,
        String receiverName,
        String paymentPurpose,
        OperationStatus status,
        BigDecimal bestScore,
        int candidatesCount
) {
}
