package com.bank.avpiteam.service.mapping;

import java.util.Map;

/**
 * Договор-кандидат с итоговым score (0..1) и сработавшими правилами с их весами.
 */
public record ScoredCandidate(long contractId, double score, Map<MatchRule, Double> rules) {

    public boolean has(MatchRule rule) {
        return rules.containsKey(rule);
    }

    public boolean hasParty() {
        return has(MatchRule.PARTY_SENDER) || has(MatchRule.PARTY_RECEIVER);
    }
}
