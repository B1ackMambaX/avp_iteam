package com.bank.avpiteam.review;

import com.bank.avpiteam.domain.Operation.OperationStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * Фильтр списка операций.
 *
 * @param statuses один или несколько статусов; пусто или null - все
 * @param from     дата операции с (включительно), null - без нижней границы
 * @param to       дата операции по (включительно), null - без верхней границы
 * @param q        поиск: подстрока без учёта регистра в назначении, отправителе, получателе
 *                 или точное совпадение с ИНН отправителя/получателя или reference
 * @param page     номер страницы с 0, null - 0
 * @param size     размер страницы 1..200, null - 50
 */
public record OperationFilter(Set<OperationStatus> statuses, LocalDate from, LocalDate to,
                              String q, Integer page, Integer size) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;
    public static final int MAX_QUERY_LENGTH = 200;

    public static OperationFilter all() {
        return new OperationFilter(Set.of(), null, null, null, 0, DEFAULT_SIZE);
    }
}
