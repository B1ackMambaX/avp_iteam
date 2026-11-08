package com.bank.avpiteam.service.mapping;

import com.bank.avpiteam.config.MappingProperties;
import com.bank.avpiteam.domain.Operation.OperationStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Решение по операции на основе отсортированных кандидатов.
 * Порядок правил описан в docs/mapping.md.
 */
@Component
public class MappingDecider {

    private static final double EPS = 1e-9;

    private final double autoThreshold;
    private final double minGap;

    public MappingDecider(MappingProperties properties) {
        this.autoThreshold = properties.getAutoThreshold();
        this.minGap = properties.getMinGap();
    }

    public MappingDecision decide(OperationEvidence evidence, List<ScoredCandidate> candidates) {
        if (candidates.isEmpty()) {
            return MappingDecision.of(OperationStatus.NOT_FOUND, Reason.NO_CANDIDATES);
        }

        ScoredCandidate best = candidates.get(0);
        double second = candidates.size() > 1 ? candidates.get(1).score() : 0.0;
        boolean clearLead = best.score() - second >= minGap - EPS;

        MappingDecision decision;
        if (best.score() >= autoThreshold - EPS && clearLead) {
            decision = MappingDecision.auto(best.contractId(), Reason.CONFIDENT_MATCH);
        } else if (candidates.size() == 1 && best.hasParty()) {
            // единственный договор у стороны операции
            decision = MappingDecision.auto(best.contractId(), Reason.SINGLE_CANDIDATE);
        } else if (!clearLead) {
            return MappingDecision.of(OperationStatus.AMBIGUOUS, Reason.CLOSE_CANDIDATES);
        } else {
            return MappingDecision.of(OperationStatus.MANUAL_REQUIRED, Reason.WEAK_EVIDENCE);
        }

        // Платёж за третье лицо (115-ФЗ) привязываем автоматически только если
        // выгодоприобретатель договора подтверждён ФИО из назначения платежа
        if (evidence.genuineThirdParty() && !best.has(MatchRule.BENEFICIARY_NAME)) {
            return MappingDecision.of(OperationStatus.MANUAL_REQUIRED, Reason.THIRD_PARTY_UNCONFIRMED);
        }
        // Без разбора назначения нельзя исключить третье лицо - автопривязку не делаем
        if (evidence.parseMissing()) {
            return MappingDecision.of(OperationStatus.MANUAL_REQUIRED, Reason.PARSE_UNAVAILABLE);
        }
        return decision;
    }

    public enum Reason {
        NO_CANDIDATES,           // ни одного договора по сторонам, номеру и ФИО
        CONFIDENT_MATCH,         // score >= autoThreshold и отрыв от второго >= minGap
        SINGLE_CANDIDATE,        // единственный договор, где сторона операции - участник
        CLOSE_CANDIDATES,        // несколько кандидатов с близким score
        WEAK_EVIDENCE,           // лидер есть, но признаков мало (например, только номер или только ФИО)
        THIRD_PARTY_UNCONFIRMED, // платёж за третье лицо, но выгодоприобретатель не совпал
        PARSE_UNAVAILABLE        // NLP не разобрал назначение платежа
    }

    public record MappingDecision(OperationStatus status, Long contractId, Reason reason) {

        static MappingDecision auto(long contractId, Reason reason) {
            return new MappingDecision(OperationStatus.AUTO_MAPPED, contractId, reason);
        }

        static MappingDecision of(OperationStatus status, Reason reason) {
            return new MappingDecision(status, null, reason);
        }
    }
}
