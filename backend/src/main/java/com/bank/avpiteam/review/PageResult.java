package com.bank.avpiteam.review;

import java.util.List;

/** Страница выборки: нумерация с 0, totalElements - с теми же фильтрами, что и content. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
}
