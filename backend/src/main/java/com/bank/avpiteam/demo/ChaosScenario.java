package com.bank.avpiteam.demo;

import java.util.List;
import java.util.Optional;

/**
 * Хаотичные сценарии. Исход здесь не единственный, а допустимый набор статусов.
 * reference: CHAOS-{сценарий}-{статусы через +}-{id договора}-{номер}.
 */
enum ChaosScenario {
    PARTIAL_NAME_ONLY("AMBIGUOUS", "MANUAL_REQUIRED", "NOT_FOUND"),
    NOISY_NUMBER_WITH_PARTY("AUTO_MAPPED", "AMBIGUOUS"),
    /** Неполное имя, у контрагента один договор: верный AUTO допустим, NOT_FOUND нет (контрагент известен). */
    PARTIAL_NAME_SINGLE_CONTRACT("AUTO_MAPPED", "AMBIGUOUS", "MANUAL_REQUIRED"),
    /** Искажённый номер, у контрагента один договор: ждём AUTO_MAPPED, всё остальное считается ошибкой. */
    NOISY_NUMBER_SINGLE_CONTRACT("AUTO_MAPPED");
    static final String PREFIX = "CHAOS-";

    private final List<String> allowed;

    ChaosScenario(String... allowed) {
        this.allowed = List.of(allowed);
    }

    List<String> allowedStatuses() {
        return allowed;
    }

    String reference(long contractId, int seq) {
        return "%s%s-%s-%d-%06d".formatted(PREFIX, name(), String.join("+", allowed), contractId, seq);
    }

    record ChaosRef(ChaosScenario scenario, List<String> allowed, long contractId) {
        /** Исход верен, если статус допустим; AUTO_MAPPED верен только на заложенном договоре. */
        boolean isCorrect(String status, Long mappedContractId) {
            if (!allowed.contains(status)) return false;
            return !"AUTO_MAPPED".equals(status) || (mappedContractId != null && mappedContractId == contractId);
        }
    }

    static Optional<ChaosRef> parse(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) return Optional.empty();
        String[] p = reference.split("-");
        if (p.length != 5) return Optional.empty();
        try {
            return Optional.of(new ChaosRef(valueOf(p[1]), List.of(p[2].split("\\+")), Long.parseLong(p[3])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}