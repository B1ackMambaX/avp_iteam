package com.bank.avpiteam.service.mapping;

import com.bank.avpiteam.config.MappingProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Считает score каждого договора-кандидата операции как сумму весов сработавших правил.
 * Чистая логика без БД: все кандидаты и признаки уже собраны в {@link OperationEvidence}.
 */
@Component
public class CandidateScorer {

    private final MappingProperties.Weights weights;

    public CandidateScorer(MappingProperties properties) {
        this.weights = properties.getWeights();
    }

    /** Кандидаты по убыванию score; при равенстве - по id договора, чтобы порядок был стабильным. */
    public List<ScoredCandidate> score(OperationEvidence evidence) {
        Set<Long> contractIds = new LinkedHashSet<>();
        contractIds.addAll(evidence.senderContracts());
        contractIds.addAll(evidence.receiverContracts());
        contractIds.addAll(evidence.numberContracts());
        if (evidence.genuineThirdParty()) {
            contractIds.addAll(evidence.beneficiaryContracts());
        }

        List<ScoredCandidate> result = new ArrayList<>(contractIds.size());
        for (long contractId : contractIds) {
            result.add(scoreContract(contractId, evidence));
        }
        result.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparingLong(ScoredCandidate::contractId));
        return result;
    }

    private ScoredCandidate scoreContract(long contractId, OperationEvidence evidence) {
        Map<MatchRule, Double> rules = new EnumMap<>(MatchRule.class);

        boolean senderParty = evidence.senderContracts().contains(contractId);
        boolean receiverParty = evidence.receiverContracts().contains(contractId);
        if (senderParty) {
            rules.put(MatchRule.PARTY_SENDER, weights.getParty());
        } else if (receiverParty) {
            rules.put(MatchRule.PARTY_RECEIVER, weights.getParty());
        }
        if (senderParty && receiverParty) {
            rules.put(MatchRule.BOTH_SIDES, weights.getBothSides());
        }
        if (evidence.numberContracts().contains(contractId)) {
            rules.put(MatchRule.CONTRACT_NUMBER, weights.getContractNumber());
        }
        if (evidence.genuineThirdParty() && evidence.beneficiaryContracts().contains(contractId)) {
            rules.put(MatchRule.BENEFICIARY_NAME, weights.getBeneficiaryName());
        }

        double sum = rules.values().stream().mapToDouble(Double::doubleValue).sum();
        return new ScoredCandidate(contractId, round(Math.min(1.0, sum)), rules);
    }

    // score хранится как NUMERIC(5,4); округляем сразу, чтобы 0.4 + 0.2 не давало 0.6000000000000001
    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
