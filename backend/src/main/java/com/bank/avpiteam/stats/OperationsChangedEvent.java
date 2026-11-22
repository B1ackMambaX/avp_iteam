package com.bank.avpiteam.stats;

/**
 * Операции массово изменились (завершён прогон конвейера, сброс демо) -
 * закешированные счётчики дашборда устарели.
 */
public record OperationsChangedEvent(String source) {
}
