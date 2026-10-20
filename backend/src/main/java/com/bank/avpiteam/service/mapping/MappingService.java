package com.bank.avpiteam.service.mapping;

import com.bank.avpiteam.config.MappingProperties;
import com.bank.avpiteam.service.mapping.MappingDecider.MappingDecision;
import com.bank.avpiteam.service.mapping.MappingJdbcRepository.OperationRow;
import com.bank.avpiteam.service.mapping.MappingJdbcRepository.OperationUpdate;
import com.bank.avpiteam.service.mapping.MappingJdbcRepository.ParseRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Модуль маппинга: сопоставляет разобранные (PARSED) операции с договорами.
 *
 * На пачку операций выполняется фиксированное число запросов, все по индексам:
 * клиенты по ИНН, их договоры через contract_parties, договоры по нормализованному
 * номеру, выгодоприобретатели по нормализованному ФИО. Скоринг и решение - в памяти.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MappingService {

    private final MappingJdbcRepository repository;
    private final CandidateScorer scorer;
    private final MappingDecider decider;
    private final MappingProperties properties;

    public record BatchResult(int processed, Map<String, Integer> byStatus, long millis) {

        static BatchResult empty() {
            return new BatchResult(0, Map.of(), 0);
        }
    }

    @Transactional
    public BatchResult mapBatch() {
        long started = System.nanoTime();
        List<OperationRow> operations = repository.claimParsed(properties.getBatchSize());
        if (operations.isEmpty()) {
            return BatchResult.empty();
        }

        List<Long> operationIds = operations.stream().map(OperationRow::id).toList();
        Map<Long, ParseRow> parses = repository.latestParse(operationIds);

        // 1. Стороны операции по ИНН и их договоры
        Set<String> inns = new HashSet<>();
        for (OperationRow op : operations) {
            addIfPresent(inns, op.senderInn());
            addIfPresent(inns, op.receiverInn());
        }
        Map<String, List<Long>> clientsByInn = repository.clientIdsByInn(inns);
        Set<Long> partyClients = new HashSet<>();
        clientsByInn.values().forEach(partyClients::addAll);
        Map<Long, Set<Long>> contractsByClient =
                repository.contractsByPartyClients(partyClients, properties.getMaxContractsPerParty());

        // 2. Номера договоров из назначения платежа
        Map<Long, Set<String>> numbersByOperation = new HashMap<>();
        Set<String> allNumbers = new HashSet<>();
        for (OperationRow op : operations) {
            Set<String> numbers = ContractNumberExtractor.extractNormalized(op.paymentPurpose());
            numbersByOperation.put(op.id(), numbers);
            allNumbers.addAll(numbers);
        }
        Map<String, Set<Long>> contractsByNumber = repository.contractsByNumberNorm(allNumbers);

        // 3. Выгодоприобретатель по ФИО - только для подтверждённых платежей за третье лицо
        Set<String> names = new HashSet<>();
        for (ParseRow parse : parses.values()) {
            if (parse.isGenuineThirdParty() && parse.extractedNameNorm() != null) {
                names.add(parse.extractedNameNorm());
            }
        }
        Map<String, Set<Long>> contractsByBeneficiary =
                repository.beneficiaryContractsByName(names, properties.getMaxClientsPerName());

        // 4. Стороны договоров, найденных по номеру и ФИО: нужны для проверки,
        //    участвует ли в них отправитель или получатель (в т.ч. клиент-"хаб")
        Set<Long> extraContracts = new HashSet<>();
        contractsByNumber.values().forEach(extraContracts::addAll);
        contractsByBeneficiary.values().forEach(extraContracts::addAll);
        Map<Long, Set<Long>> partiesOfExtra = repository.partiesOfContracts(extraContracts);

        // 5. Скоринг и решение по каждой операции
        Map<Long, List<ScoredCandidate>> candidatesToStore = new HashMap<>();
        List<OperationUpdate> updates = new ArrayList<>(operations.size());
        Map<String, Integer> byStatus = new HashMap<>();
        Map<MappingDecider.Reason, Integer> byReason = new EnumMap<>(MappingDecider.Reason.class);

        for (OperationRow op : operations) {
            ParseRow parse = parses.get(op.id());

            Set<Long> numberContracts = union(numbersByOperation.get(op.id()), contractsByNumber);
            Set<Long> beneficiaryContracts = new HashSet<>();
            boolean genuineThirdParty = parse != null && parse.isGenuineThirdParty();
            if (genuineThirdParty && parse.extractedNameNorm() != null) {
                beneficiaryContracts.addAll(
                        contractsByBeneficiary.getOrDefault(parse.extractedNameNorm(), Set.of()));
            }

            Set<Long> extraForOperation = new HashSet<>(numberContracts);
            extraForOperation.addAll(beneficiaryContracts);

            OperationEvidence evidence = new OperationEvidence(
                    op.id(),
                    sideContracts(op.senderInn(), clientsByInn, contractsByClient, extraForOperation, partiesOfExtra),
                    sideContracts(op.receiverInn(), clientsByInn, contractsByClient, extraForOperation, partiesOfExtra),
                    numberContracts,
                    beneficiaryContracts,
                    genuineThirdParty,
                    parse == null);

            List<ScoredCandidate> candidates = scorer.score(evidence);
            MappingDecision decision = decider.decide(evidence, candidates);

            candidatesToStore.put(op.id(),
                    candidates.subList(0, Math.min(candidates.size(), properties.getMaxStoredCandidates())));
            updates.add(new OperationUpdate(op.id(), decision.status().name(),
                    decision.contractId(), decision.reason().name()));
            byStatus.merge(decision.status().name(), 1, Integer::sum);
            byReason.merge(decision.reason(), 1, Integer::sum);
        }

        repository.replaceCandidates(operationIds, candidatesToStore);
        repository.updateOperations(updates);

        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("Маппинг: {} операций за {} мс, статусы {}, причины {}",
                operations.size(), millis, byStatus, byReason);
        return new BatchResult(operations.size(), byStatus, millis);
    }

    /**
     * Договоры, где сторона операции (по ИНН) - участник: её собственные договоры
     * плюс договоры, найденные по номеру/ФИО, в которых она числится стороной.
     */
    private static Set<Long> sideContracts(String inn,
                                           Map<String, List<Long>> clientsByInn,
                                           Map<Long, Set<Long>> contractsByClient,
                                           Set<Long> extraContracts,
                                           Map<Long, Set<Long>> partiesOfExtra) {
        List<Long> clients = inn == null ? List.of() : clientsByInn.getOrDefault(inn, List.of());
        if (clients.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>();
        for (Long clientId : clients) {
            result.addAll(contractsByClient.getOrDefault(clientId, Set.of()));
        }
        for (Long contractId : extraContracts) {
            Set<Long> parties = partiesOfExtra.getOrDefault(contractId, Set.of());
            for (Long clientId : clients) {
                if (parties.contains(clientId)) {
                    result.add(contractId);
                    break;
                }
            }
        }
        return result;
    }

    private static Set<Long> union(Collection<String> keys, Map<String, Set<Long>> index) {
        Set<Long> result = new LinkedHashSet<>();
        for (String key : keys) {
            result.addAll(index.getOrDefault(key, Set.of()));
        }
        return result;
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }
}
