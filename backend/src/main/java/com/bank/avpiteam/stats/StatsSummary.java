package com.bank.avpiteam.stats;

import java.time.LocalDate;

/**
 * Ответ GET /api/stats/summary. Корзины не пересекаются, их сумма равна loaded:
 * inProgress + autoMapped + manuallyMapped + awaitingManual.* + notFound = loaded.
 */
public record StatsSummary(
        LocalDate date,
        boolean demo,
        long loaded,
        long inProgress,
        long autoMapped,
        long manuallyMapped,
        AwaitingManual awaitingManual,
        long notFound,
        double autoShare,
        long backlogAwaitingManual
) {

    public record AwaitingManual(long ambiguous, long manualRequired) {
    }
}
