package com.bank.avpiteam.service.mapping;

import com.bank.avpiteam.config.MappingProperties;
import com.bank.avpiteam.domain.Operation.OperationStatus;
import com.bank.avpiteam.service.mapping.MappingDecider.MappingDecision;
import com.bank.avpiteam.service.mapping.MappingDecider.Reason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Скоринг + решение на типовых ситуациях из docs/mapping.md. */
class MappingDeciderTest {

    private final MappingProperties properties = new MappingProperties();
    private final CandidateScorer scorer = new CandidateScorer(properties);
    private final MappingDecider decider = new MappingDecider(properties);

    private MappingDecision decide(OperationEvidence evidence) {
        List<ScoredCandidate> candidates = scorer.score(evidence);
        return decider.decide(evidence, candidates);
    }

    private static OperationEvidence evidence(Set<Long> sender, Set<Long> receiver, Set<Long> number,
                                              Set<Long> beneficiary, boolean thirdParty) {
        return new OperationEvidence(1, sender, receiver, number, beneficiary, thirdParty, false);
    }

    @Test
    void noCandidatesIsNotFound() {
        MappingDecision decision = decide(evidence(Set.of(), Set.of(), Set.of(), Set.of(), false));
        assertThat(decision.status()).isEqualTo(OperationStatus.NOT_FOUND);
        assertThat(decision.reason()).isEqualTo(Reason.NO_CANDIDATES);
    }

    @Test
    void contractNumberPlusPartyIsAutoMappedAmongOtherContracts() {
        // у отправителя три договора, номер указывает на 20
        MappingDecision decision = decide(evidence(Set.of(10L, 20L, 30L), Set.of(), Set.of(20L), Set.of(), false));
        assertThat(decision.status()).isEqualTo(OperationStatus.AUTO_MAPPED);
        assertThat(decision.contractId()).isEqualTo(20L);
        assertThat(decision.reason()).isEqualTo(Reason.CONFIDENT_MATCH);
    }

    @Test
    void singleContractOfPartyIsAutoMapped() {
        MappingDecision decision = decide(evidence(Set.of(), Set.of(7L), Set.of(), Set.of(), false));
        assertThat(decision.status()).isEqualTo(OperationStatus.AUTO_MAPPED);
        assertThat(decision.contractId()).isEqualTo(7L);
        assertThat(decision.reason()).isEqualTo(Reason.SINGLE_CANDIDATE);
    }

    @Test
    void severalContractsWithoutNumberAreAmbiguous() {
        MappingDecision decision = decide(evidence(Set.of(1L, 2L), Set.of(), Set.of(), Set.of(), false));
        assertThat(decision.status()).isEqualTo(OperationStatus.AMBIGUOUS);
        assertThat(decision.contractId()).isNull();
    }

    @Test
    void bothSidesOfOneContractIsConfident() {
        // 0.4 + 0.2 = 0.6 >= порога, второй кандидат 0.4 - отрыв ровно 0.2
        MappingDecision decision = decide(evidence(Set.of(5L, 6L), Set.of(5L), Set.of(), Set.of(), false));
        assertThat(decision.status()).isEqualTo(OperationStatus.AUTO_MAPPED);
        assertThat(decision.contractId()).isEqualTo(5L);
    }

    @Test
    void numberWithoutPartyNeedsOperator() {
        // номер есть, но ни отправитель, ни получатель не участвуют в договоре
        MappingDecision decision = decide(evidence(Set.of(), Set.of(), Set.of(9L), Set.of(), false));
        assertThat(decision.status()).isEqualTo(OperationStatus.MANUAL_REQUIRED);
        assertThat(decision.reason()).isEqualTo(Reason.WEAK_EVIDENCE);
    }

    @Test
    void thirdPartyMappedByBeneficiaryName() {
        // у получателя три договора, выгодоприобретатель совпал только в 3
        MappingDecision decision = decide(evidence(Set.of(), Set.of(1L, 2L, 3L), Set.of(), Set.of(3L, 99L), true));
        assertThat(decision.status()).isEqualTo(OperationStatus.AUTO_MAPPED);
        assertThat(decision.contractId()).isEqualTo(3L);
    }

    @Test
    void thirdPartyWithoutBeneficiaryMatchIsNotAutoMapped() {
        // номер и сторона совпали, но платёж за третье лицо, а выгодоприобретатель не подтверждён
        MappingDecision decision = decide(evidence(Set.of(4L), Set.of(), Set.of(4L), Set.of(), true));
        assertThat(decision.status()).isEqualTo(OperationStatus.MANUAL_REQUIRED);
        assertThat(decision.reason()).isEqualTo(Reason.THIRD_PARTY_UNCONFIRMED);
    }

    @Test
    void missingParseBlocksAutoMapping() {
        OperationEvidence evidence = new OperationEvidence(1, Set.of(4L), Set.of(), Set.of(4L), Set.of(), false, true);
        MappingDecision decision = decider.decide(evidence, scorer.score(evidence));
        assertThat(decision.status()).isEqualTo(OperationStatus.MANUAL_REQUIRED);
        assertThat(decision.reason()).isEqualTo(Reason.PARSE_UNAVAILABLE);
    }

    @Test
    void scoreIsCappedAndRulesAreExplained() {
        List<ScoredCandidate> candidates = scorer.score(
                evidence(Set.of(1L), Set.of(1L), Set.of(1L), Set.of(1L), true));
        ScoredCandidate only = candidates.get(0);
        assertThat(only.score()).isEqualTo(1.0);
        assertThat(only.rules()).containsOnlyKeys(MatchRule.PARTY_SENDER, MatchRule.BOTH_SIDES,
                MatchRule.CONTRACT_NUMBER, MatchRule.BENEFICIARY_NAME);
    }
}
