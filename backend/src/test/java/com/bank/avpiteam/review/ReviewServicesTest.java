package com.bank.avpiteam.review;

import com.bank.avpiteam.domain.Operation.OperationStatus;
import com.bank.avpiteam.review.ManualDecisionException.Code;
import com.bank.avpiteam.security.Authorities;
import com.bank.avpiteam.security.Role;
import com.bank.avpiteam.service.mapping.ContractPartiesReader;
import com.bank.avpiteam.service.mapping.MappingCard;
import com.bank.avpiteam.service.mapping.MappingQueryService;
import com.bank.avpiteam.service.mapping.MatchRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BE-1..BE-4 на настоящем PostgreSQL со схемой из миграций: выборки, карточка, поиск договора,
 * ручное решение, права на уровне сервисов. Без Docker тест пропускается.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(ReviewServicesTest.Config.class)
class ReviewServicesTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse(System.getenv().getOrDefault("AVP_TEST_PG_IMAGE", "postgres:16-alpine"))
                    .asCompatibleSubstituteFor("postgres"));

    @Configuration
    @EnableMethodSecurity
    @EnableTransactionManagement
    @Import({OperationQueryService.class, ContractSearchService.class, ManualDecisionService.class,
            MappingQueryService.class, ContractPartiesReader.class})
    static class Config {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            return ds;
        }

        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource ds) {
            return new NamedParameterJdbcTemplate(ds);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    @Autowired OperationQueryService operations;
    @Autowired ContractSearchService contracts;
    @Autowired ManualDecisionService decisions;
    @Autowired MappingQueryService cards;
    @Autowired NamedParameterJdbcTemplate named;

    JdbcTemplate jdbc;
    long alfa, beta, ivanov;              // клиенты
    long k1, k2, k3;                      // договоры; у k1 и k2 одинаковый номер "ДГ-1"
    long ambiguous, manualRequired, autoMapped, pending, notFound, ambiguousTwin;

    // ---------- данные ----------

    @BeforeEach
    void data() {
        jdbc = named.getJdbcTemplate();
        jdbc.execute("TRUNCATE operations, contracts, clients CASCADE");
        alfa = client("LEGAL_ENTITY", "7700000001", "ООО «Альфа»", null);
        beta = client("LEGAL_ENTITY", "7700000002", "ООО «Бета»", null);
        ivanov = client("INDIVIDUAL", "500000000003", null, "Иванов");

        k1 = contract("ДГ-1", Map.of(alfa, "COUNTERPARTY"));
        k2 = contract("ДГ-1", Map.of(beta, "COUNTERPARTY"));
        k3 = contract("ДГ-2", Map.of(alfa, "COUNTERPARTY", ivanov, "BENEFICIARY"));

        ambiguous = operation("AMBIGUOUS", "2026-11-09 10:00", "ООО «Альфа»", "Оплата по счету 1");
        candidate(ambiguous, k1, "0.4000", "{\"PARTY_SENDER\": 0.4}");
        candidate(ambiguous, k3, "0.4000", "{\"PARTY_SENDER\": 0.4}");
        jdbc.update("""
                INSERT INTO operation_parse_result (operation_id, parser, parser_version, is_third_party,
                    is_genuine_third_party, extracted_name, confidence, classification_method, classification_score)
                VALUES (?, 'NLP', 'test', false, false, NULL, 0.3, 'SEMANTIC', 0.31)
                """, ambiguous);
        manualRequired = operation("MANUAL_REQUIRED", "2026-11-09 09:00", "ООО «Бета»", "Оплата за третье лицо");
        candidate(manualRequired, k2, "0.4000", "{\"PARTY_RECEIVER\": 0.4}");
        autoMapped = operation("AUTO_MAPPED", "2026-11-08 12:00", "ООО «Альфа»", "Оплата по договору ДГ-2");
        candidate(autoMapped, k3, "0.8000", "{\"PARTY_SENDER\": 0.4, \"CONTRACT_NUMBER\": 0.4}");
        jdbc.update("UPDATE operations SET contract_id = ?, mapping_source = 'AUTO', mapped_by = 'AUTO' WHERE id = ?",
                k3, autoMapped);
        pending = operation("PENDING", "2026-11-07 08:00", "ИП Петров", "Аренда");
        notFound = operation("NOT_FOUND", "2026-11-09 10:00", "ООО «Внешний»", "Возврат");
        ambiguousTwin = operation("AMBIGUOUS", "2026-11-09 10:00", "ООО «Альфа»", "Оплата по счету 2");
        candidate(ambiguousTwin, k1, "0.4000", "{\"PARTY_SENDER\": 0.4}");
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    private long client(String type, String inn, String org, String lastName) {
        return jdbc.queryForObject("""
                INSERT INTO clients (client_type, inn, organization_name, last_name, first_name, middle_name)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, type, inn, org, lastName,
                lastName == null ? null : "Иван", lastName == null ? null : "Иванович");
    }

    private long contract(String number, Map<Long, String> parties) {
        long id = jdbc.queryForObject("INSERT INTO contracts (contract_type, contract_number) VALUES ('STANDARD', ?) RETURNING id",
                Long.class, number);
        parties.forEach((clientId, role) ->
                jdbc.update("INSERT INTO contract_parties (contract_id, client_id, role) VALUES (?, ?, ?)", id, clientId, role));
        return id;
    }

    private long operation(String status, String date, String sender, String purpose) {
        return jdbc.queryForObject("""
                INSERT INTO operations (reference, operation_date, sender_inn, sender_name, receiver_name,
                                        amount, amount_rub, payment_purpose, status)
                VALUES (?, CAST(? AS timestamp), '7700000001', ?, 'ООО «Банк»', 1500.50, 1500.50, ?, ?) RETURNING id
                """, Long.class, "T-" + System.nanoTime(), date, sender, purpose, status);
    }

    private void candidate(long operationId, long contractId, String score, String rules) {
        jdbc.update("""
                INSERT INTO operation_mapping_candidate (operation_id, contract_id, score, matched_rules)
                VALUES (?, ?, CAST(? AS numeric), CAST(? AS jsonb))
                """, operationId, contractId, score, rules);
    }

    private static void loginAs(Role role, String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", Authorities.of(role)));
    }

    private static List<Long> ids(PageResult<OperationRow> page) {
        return page.content().stream().map(OperationRow::operationId).toList();
    }

    // ---------- BE-1: список и очередь ----------

    @Nested
    class OperationList {

        @BeforeEach
        void reader() {
            loginAs(Role.READER, "reader");
        }

        @Test
        void sortedByDateThenIdDescending() {
            PageResult<OperationRow> all = operations.find(OperationFilter.all());

            assertThat(all.totalElements()).isEqualTo(6);
            // 09.11 10:00 - три операции с одной датой: по id по убыванию
            assertThat(ids(all)).containsExactly(ambiguousTwin, notFound, ambiguous, manualRequired, autoMapped, pending);
        }

        @Test
        void filtersAreCombined() {
            PageResult<OperationRow> page = operations.find(new OperationFilter(
                    Set.of(OperationStatus.AMBIGUOUS, OperationStatus.NOT_FOUND),
                    LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9), "альфа", 0, 50));

            assertThat(ids(page)).containsExactly(ambiguousTwin, ambiguous);
            assertThat(page.totalElements()).as("count с теми же фильтрами").isEqualTo(2);
        }

        @Test
        void dateRangeIsInclusive() {
            PageResult<OperationRow> page = operations.find(new OperationFilter(Set.of(),
                    LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8), null, 0, 50));

            assertThat(ids(page)).containsExactly(autoMapped, pending);
        }

        @Test
        void searchByPurposeSenderInnAndWildcardsAreLiteral() {
            assertThat(ids(operations.find(new OperationFilter(Set.of(), null, null, "третье лицо", 0, 50))))
                    .containsExactly(manualRequired);
            assertThat(operations.find(new OperationFilter(Set.of(), null, null, "7700000001", 0, 50)).totalElements())
                    .isEqualTo(6);
            assertThat(operations.find(new OperationFilter(Set.of(), null, null, "%", 0, 50)).totalElements())
                    .as("% ищется как символ, а не как шаблон").isZero();
        }

        @Test
        void reviewQueueHasBothCategories() {
            PageResult<OperationRow> queue = operations.reviewQueue(null, null, null, null, null, null);

            assertThat(ids(queue)).containsExactly(ambiguousTwin, ambiguous, manualRequired);
            assertThat(queue.size()).isEqualTo(OperationFilter.DEFAULT_SIZE);
            assertThatThrownBy(() -> operations.reviewQueue(Set.of(OperationStatus.AUTO_MAPPED), null, null, null, null, null))
                    .isInstanceOf(ValidationFailedException.class);
        }

        @Test
        void emptyResult() {
            PageResult<OperationRow> page = operations.find(new OperationFilter(Set.of(), null, null, "нет такого", 0, 50));

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isZero();
        }

        @Test
        void pagesDoNotOverlap() {
            List<Long> seen = new ArrayList<>();
            for (int p = 0; p < 3; p++) {
                PageResult<OperationRow> page = operations.find(new OperationFilter(Set.of(), null, null, null, p, 2));
                assertThat(page.totalElements()).isEqualTo(6);
                seen.addAll(ids(page));
            }
            assertThat(seen).doesNotHaveDuplicates().hasSize(6)
                    .containsExactlyElementsOf(ids(operations.find(OperationFilter.all())));
        }

        @Test
        void bestScoreAndCandidatesCount() {
            Map<Long, OperationRow> rows = new java.util.HashMap<>();
            operations.find(OperationFilter.all()).content().forEach(r -> rows.put(r.operationId(), r));

            assertThat(rows.get(ambiguous).bestScore()).isEqualByComparingTo("0.4");
            assertThat(rows.get(ambiguous).candidatesCount()).isEqualTo(2);
            assertThat(rows.get(autoMapped).bestScore()).isEqualByComparingTo("0.8");
            assertThat(rows.get(pending).bestScore()).isNull();
            assertThat(rows.get(pending).candidatesCount()).isZero();
            assertThat(rows.get(ambiguous).amount()).isEqualByComparingTo(new BigDecimal("1500.50"));
            assertThat(rows.get(ambiguous).date()).isEqualTo(LocalDate.of(2026, 11, 9));
        }

        @Test
        void invalidParametersAreValidationErrors() {
            assertThatThrownBy(() -> operations.find(new OperationFilter(Set.of(),
                    LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 1), null, -1, 500)))
                    .isInstanceOfSatisfying(ValidationFailedException.class, e ->
                            assertThat(e.errors()).containsOnlyKeys("from", "page", "size"));
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleCanRead(Role role) {
        loginAs(role, "u");

        assertThat(operations.find(OperationFilter.all()).totalElements()).isEqualTo(6);
        assertThat(cards.findCard(ambiguous)).isPresent();
        assertThat(contracts.search("ДГ-1", 0, 10).totalElements()).isEqualTo(2);
    }

    @Test
    void anonymousCannotRead() {
        assertThatThrownBy(() -> operations.find(OperationFilter.all()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    // ---------- BE-2: карточка ----------

    @Nested
    class Card {

        @BeforeEach
        void reader() {
            loginAs(Role.READER, "reader");
        }

        @Test
        void withParseAndSeveralCandidates() {
            MappingCard card = cards.findCard(ambiguous).orElseThrow();

            assertThat(card.operation().status()).isEqualTo(OperationStatus.AMBIGUOUS);
            assertThat(card.operation().version()).isPositive();
            assertThat(card.parse()).isNotNull();
            assertThat(card.parse().classificationMethod()).isEqualTo("SEMANTIC");
            // одинаковый score - по contractId по возрастанию
            assertThat(card.candidates()).extracting(MappingCard.CandidateInfo::contractId).containsExactly(k1, k3);
            MappingCard.CandidateInfo first = card.candidates().get(0);
            assertThat(first.matchedRules()).containsEntry(MatchRule.PARTY_SENDER, 0.4);
            assertThat(first.score()).isEqualByComparingTo("0.4");
            assertThat(first.parties()).singleElement()
                    .satisfies(p -> assertThat(p.name()).isEqualTo("ООО «Альфа»"));
            assertThat(card.candidates().get(1).parties()).extracting(MappingCard.PartyInfo::role)
                    .containsExactly("BENEFICIARY", "COUNTERPARTY");
        }

        @Test
        void autoMappedHasContractAndSource() {
            MappingCard card = cards.findCard(autoMapped).orElseThrow();

            assertThat(card.operation().contractId()).isEqualTo(k3);
            assertThat(card.operation().contractNumber()).isEqualTo("ДГ-2");
            assertThat(card.operation().mappingSource()).isEqualTo("AUTO");
            assertThat(card.candidates().get(0).matchedRules())
                    .containsOnlyKeys(MatchRule.PARTY_SENDER, MatchRule.CONTRACT_NUMBER);
        }

        @Test
        void withoutParseOrCandidates() {
            MappingCard card = cards.findCard(pending).orElseThrow();

            assertThat(card.parse()).isNull();
            assertThat(card.candidates()).isEmpty();
        }

        @Test
        void missingOperation() {
            assertThat(cards.findCard(-1)).isEmpty();
        }
    }

    // ---------- BE-3: поиск договора ----------

    @Nested
    class ContractSearch {

        @BeforeEach
        void reader() {
            loginAs(Role.READER, "reader");
        }

        @Test
        void sameNumberGivesDistinctContractsWithParties() {
            PageResult<ContractRow> page = contracts.search("дг 1", 0, 10);

            assertThat(page.content()).extracting(ContractRow::contractId).containsExactly(k1, k2);
            assertThat(page.content()).extracting(r -> r.parties().get(0).name())
                    .containsExactly("ООО «Альфа»", "ООО «Бета»");
        }

        @Test
        void byPartyInn() {
            assertThat(contracts.search("7700000001", 0, 10).content())
                    .extracting(ContractRow::contractId).containsExactly(k1, k3);
            assertThat(contracts.search("500000000003", 0, 10).content())
                    .extracting(ContractRow::contractId).containsExactly(k3);
        }

        @Test
        void emptyAndPagination() {
            assertThat(contracts.search("ДГ-999", 0, 10).content()).isEmpty();
            PageResult<ContractRow> first = contracts.search("ДГ-1", 0, 1);
            PageResult<ContractRow> second = contracts.search("ДГ-1", 1, 1);
            assertThat(first.totalElements()).isEqualTo(2);
            assertThat(first.content().get(0).contractId()).isNotEqualTo(second.content().get(0).contractId());
        }

        @Test
        void blankQueryIsValidationError() {
            assertThatThrownBy(() -> contracts.search("  ", 0, 10)).isInstanceOf(ValidationFailedException.class);
        }
    }

    // ---------- BE-4: ручное решение ----------

    @Nested
    class ManualDecision {

        @BeforeEach
        void editor() {
            loginAs(Role.EDITOR, "editor");
        }

        private long version(long operationId) {
            return cards.findCard(operationId).orElseThrow().operation().version();
        }

        @Test
        void confirmCandidate() {
            MappingCard card = decisions.resolve(new ResolveCommand(ambiguous, k1, null, version(ambiguous)));

            assertThat(card.operation().status()).isEqualTo(OperationStatus.MANUAL_MAPPED);
            assertThat(card.operation().contractId()).isEqualTo(k1);
            assertThat(card.operation().mappingSource()).isEqualTo("MANUAL");
            assertThat(card.operation().mappedBy()).isEqualTo("editor");
            assertThat(card.operation().mappedAt()).isNotNull();
            Map<String, Object> decision = jdbc.queryForMap(
                    "SELECT * FROM operation_manual_decision WHERE operation_id = ?", ambiguous);
            assertThat(decision).containsEntry("action", "CONFIRM").containsEntry("from_candidates", true)
                    .containsEntry("previous_status", "AMBIGUOUS").containsEntry("decided_by", "editor");
            assertThat(decision.get("comment")).isNull();
            assertThat(decision.get("decided_at")).isNotNull();
        }

        @Test
        void rejectNeedsComment() {
            assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(manualRequired, null, " ", null)))
                    .isInstanceOfSatisfying(ValidationFailedException.class,
                            e -> assertThat(e.errors()).containsOnlyKeys("comment"));

            MappingCard card = decisions.resolve(new ResolveCommand(manualRequired, null, "Договора нет в базе", null));

            assertThat(card.operation().status()).isEqualTo(OperationStatus.NOT_FOUND);
            assertThat(card.operation().contractId()).isNull();
            assertThat(card.operation().mappingSource()).isEqualTo("MANUAL");
            assertThat(jdbc.queryForObject("SELECT comment FROM operation_manual_decision WHERE operation_id = ?",
                    String.class, manualRequired)).isEqualTo("Договора нет в базе");
        }

        @Test
        void contractOutsideCandidatesNeedsComment() {
            assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(ambiguous, k2, null, null)))
                    .isInstanceOfSatisfying(ManualDecisionException.class,
                            e -> assertThat(e.code()).isEqualTo(Code.CONTRACT_NOT_ALLOWED));

            decisions.resolve(new ResolveCommand(ambiguous, k2, "Договор найден по ИНН вручную", null));

            assertThat(jdbc.queryForObject("SELECT from_candidates FROM operation_manual_decision WHERE operation_id = ?",
                    Boolean.class, ambiguous)).isFalse();
        }

        @Test
        void commentLimit() {
            assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(ambiguous, k1, "x".repeat(1001), null)))
                    .isInstanceOf(ValidationFailedException.class);
        }

        @Test
        void missingOperationOrContract() {
            assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(-1, k1, null, null)))
                    .isInstanceOfSatisfying(ManualDecisionException.class,
                            e -> assertThat(e.code()).isEqualTo(Code.OPERATION_NOT_FOUND));
            assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(ambiguous, -1L, "x", null)))
                    .isInstanceOfSatisfying(ManualDecisionException.class,
                            e -> assertThat(e.code()).isEqualTo(Code.CONTRACT_NOT_FOUND));
        }

        @Test
        void onlyFromReviewQueueAndNoCorrection() {
            for (long op : List.of(autoMapped, pending, notFound)) {
                assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(op, k1, "x", null)))
                        .isInstanceOfSatisfying(ManualDecisionException.class,
                                e -> assertThat(e.code()).isEqualTo(Code.FORBIDDEN_TRANSITION));
            }
            decisions.resolve(new ResolveCommand(ambiguous, k1, null, null));
            assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(ambiguous, k3, null, null)))
                    .as("принятое решение не исправляется")
                    .isInstanceOfSatisfying(ManualDecisionException.class,
                            e -> assertThat(e.code()).isEqualTo(Code.FORBIDDEN_TRANSITION));
        }

        @Test
        void staleCardIsConflict() {
            long readVersion = version(ambiguous);
            decisions.resolve(new ResolveCommand(ambiguous, k1, null, readVersion));

            assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(ambiguous, k3, null, readVersion)))
                    .isInstanceOfSatisfying(ManualDecisionException.class,
                            e -> assertThat(e.code()).isEqualTo(Code.STALE_CARD));
            assertThat(cards.findCard(ambiguous).orElseThrow().operation().contractId())
                    .as("первое решение сохранено").isEqualTo(k1);
        }

        @Test
        void twoEditorsAtOnceOneWins() throws Exception {
            long readVersion = version(ambiguous);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Object>> results = new ArrayList<>();
                for (long contractId : List.of(k1, k3)) {
                    Callable<Object> task = () -> {
                        loginAs(Role.EDITOR, "editor-" + contractId);
                        start.await();
                        try {
                            return decisions.resolve(new ResolveCommand(ambiguous, contractId, null, readVersion));
                        } catch (ManualDecisionException e) {
                            return e.code();
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    };
                    results.add(pool.submit(task));
                }
                start.countDown();
                Set<Object> outcomes = new HashSet<>();
                for (Future<Object> f : results) {
                    Object r = f.get();
                    outcomes.add(r instanceof MappingCard ? "OK" : r);
                }
                assertThat(outcomes).containsExactlyInAnyOrder("OK", Code.STALE_CARD);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_manual_decision WHERE operation_id = ?",
                        Integer.class, ambiguous)).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void decisionMovesOperationOutOfQueue() {
            decisions.resolve(new ResolveCommand(ambiguous, k1, null, null));

            assertThat(ids(operations.reviewQueue(null, null, null, null, null, null)))
                    .containsExactly(ambiguousTwin, manualRequired);
            assertThat(ids(operations.find(new OperationFilter(Set.of(OperationStatus.MANUAL_MAPPED),
                    null, null, null, 0, 50)))).containsExactly(ambiguous);
        }
    }

    @Test
    void readerCannotDecideAdminCan() {
        loginAs(Role.READER, "reader");
        assertThatThrownBy(() -> decisions.resolve(new ResolveCommand(ambiguous, k1, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        loginAs(Role.ADMIN, "admin");
        assertThat(decisions.resolve(new ResolveCommand(ambiguous, k1, null, null)).operation().mappedBy())
                .isEqualTo("admin");
    }
}
