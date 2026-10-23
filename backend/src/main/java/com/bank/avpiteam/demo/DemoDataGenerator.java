package com.bank.avpiteam.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Демо-данные для показа маппинга на объёме (профиль demo).
 *
 * Справочник (клиенты, договоры, стороны) генерируется SQL-запросами прямо в Postgres -
 * 10 млн договоров создаются за минуты, а не часы, как через JPA.
 * Операции генерируются по сценариям {@link DemoScenario} с известным правильным ответом,
 * точность маппинга считает DemoReportService.
 *
 * Повторный запуск безопасен: справочник и операции создаются, только если их ещё нет.
 */
@Slf4j
@Component
@Profile("demo")
public class DemoDataGenerator implements CommandLineRunner {

    private static final String[] SURNAMES = {
            "Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев", "Петров", "Соколов", "Михайлов",
            "Новиков", "Федоров", "Морозов", "Волков", "Алексеев", "Лебедев", "Семенов", "Егоров",
            "Павлов", "Козлов", "Степанов", "Николаев", "Орлов", "Андреев", "Макаров", "Никитин",
            "Захаров", "Зайцев", "Соловьев", "Борисов", "Яковлев", "Григорьев", "Романов", "Воробьев",
            "Сергеев", "Кузьмин", "Фролов", "Александров", "Дмитриев", "Королев", "Гусев", "Киселев",
            "Ильин", "Максимов", "Поляков", "Сорокин", "Виноградов", "Ковалев", "Белов", "Медведев",
            "Антонов", "Тарасов", "Жуков", "Баранов", "Филиппов", "Комаров", "Давыдов", "Беляев",
            "Герасимов", "Богданов", "Осипов", "Сидоров"
    };
    private static final String[] MALE_NAMES = {
            "Александр", "Сергей", "Дмитрий", "Андрей", "Алексей", "Максим", "Евгений", "Иван",
            "Михаил", "Артем", "Николай", "Владимир", "Денис", "Павел", "Роман", "Игорь", "Олег",
            "Антон", "Кирилл", "Виктор", "Юрий", "Константин", "Илья", "Егор", "Григорий", "Степан",
            "Тимур", "Василий", "Борис", "Леонид"
    };
    private static final String[] FEMALE_NAMES = {
            "Елена", "Ольга", "Наталья", "Татьяна", "Ирина", "Анна", "Светлана", "Мария", "Юлия",
            "Екатерина", "Марина", "Галина", "Людмила", "Анастасия", "Дарья", "Виктория", "Ксения",
            "Алина", "Полина", "Вера", "Надежда", "Любовь", "Валентина", "Жанна", "Лариса", "Оксана",
            "Яна", "Алла", "Софья", "Евгения"
    };
    private static final String[] MALE_PATRONYMICS = {
            "Александрович", "Сергеевич", "Дмитриевич", "Андреевич", "Алексеевич", "Евгеньевич",
            "Иванович", "Михайлович", "Николаевич", "Владимирович", "Павлович", "Романович",
            "Игоревич", "Олегович", "Викторович", "Юрьевич", "Константинович", "Петрович",
            "Борисович", "Васильевич", "Григорьевич", "Анатольевич", "Валерьевич", "Геннадьевич",
            "Степанович"
    };
    private static final String[] FEMALE_PATRONYMICS = {
            "Александровна", "Сергеевна", "Дмитриевна", "Андреевна", "Алексеевна", "Евгеньевна",
            "Ивановна", "Михайловна", "Николаевна", "Владимировна", "Павловна", "Романовна",
            "Игоревна", "Олеговна", "Викторовна", "Юрьевна", "Константиновна", "Петровна",
            "Борисовна", "Васильевна", "Григорьевна", "Анатольевна", "Валерьевна", "Геннадьевна",
            "Степановна"
    };
    private static final String[] LEGAL_FORMS = {"ООО", "АО", "ПАО"};
    private static final String[] COMPANY_PREFIXES = {
            "Альфа", "Гранд", "Евро", "Норд", "Интер", "Мега", "Проф", "Сиб", "Урал", "Волга",
            "Тех", "Строй", "Агро", "Трейд", "Восток"
    };
    private static final String[] COMPANY_SUFFIXES = {
            "Сервис", "Групп", "Лайн", "Ресурс", "Комплекс", "Систем", "Холдинг", "Партнер", "Инвест", "Логистик"
    };

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate tx;
    private final DemoProperties properties;

    public DemoDataGenerator(JdbcTemplate jdbc,
                             NamedParameterJdbcTemplate named,
                             PlatformTransactionManager transactionManager,
                             DemoProperties properties) {
        this.jdbc = jdbc;
        this.named = named;
        this.tx = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (count("SELECT count(*) FROM clients") == 0) {
            generateReferenceData();
        } else {
            log.info("Демо: справочник уже есть ({} договоров), пропускаем", count("SELECT count(*) FROM contracts"));
        }
        if (count("SELECT count(*) FROM operations WHERE reference LIKE 'DEMO-%'") == 0) {
            generateOperations();
        } else {
            log.info("Демо: операции уже есть, пропускаем");
        }
    }

    // ===================================================================
    // Справочник: клиенты, договоры, стороны договоров
    // ===================================================================

    private void generateReferenceData() {
        int clients = properties.getClients();
        int contracts = properties.getContracts();
        // Часть клиентов - "одиночки": у каждого ровно один договор (для сценария SINGLE_CONTRACT)
        int singles = Math.min(clients / 5, contracts / 10);
        int regularClients = clients - singles;

        log.info("Демо: генерация {} клиентов и {} договоров...", clients, contracts);
        long started = System.currentTimeMillis();

        tx.executeWithoutResult(status -> {
            jdbc.execute("SELECT setseed(" + (properties.getSeed() % 1000) / 1000.0 + ")");

            jdbc.update(fill("""
                    INSERT INTO clients (client_type, inn, last_name, first_name, middle_name, birth_date,
                                         organization_name, legal_form, is_bank_client)
                    SELECT
                        CASE WHEN r.legal THEN 'LEGAL_ENTITY' ELSE 'INDIVIDUAL' END,
                        CASE WHEN r.legal THEN (7700000000 + r.g)::text ELSE (500000000000 + r.g)::text END,
                        CASE WHEN r.legal THEN NULL
                             ELSE ({SURNAMES})[r.si] || CASE WHEN r.female THEN 'а' ELSE '' END END,
                        CASE WHEN r.legal THEN NULL WHEN r.female THEN ({FEMALE_NAMES})[r.fi]
                             ELSE ({MALE_NAMES})[r.mi] END,
                        CASE WHEN r.legal THEN NULL WHEN r.female THEN ({FEMALE_PATRONYMICS})[r.pi]
                             ELSE ({MALE_PATRONYMICS})[r.pi] END,
                        CASE WHEN r.legal THEN NULL ELSE DATE '1955-01-01' + r.bd END,
                        CASE WHEN r.legal
                             THEN ({LEGAL_FORMS})[r.lf] || ' «' || ({COMPANY_PREFIXES})[r.w1] || ({COMPANY_SUFFIXES})[r.w2] || '»' END,
                        CASE WHEN r.legal THEN ({LEGAL_FORMS})[r.lf] END,
                        TRUE
                    FROM (
                        SELECT g,
                               g % 4 = 0 AS legal,
                               g % 2 = 1 AS female,
                               1 + floor(random() * {SURNAMES_LEN})::int AS si,
                               1 + floor(random() * {MALE_NAMES_LEN})::int AS mi,
                               1 + floor(random() * {FEMALE_NAMES_LEN})::int AS fi,
                               1 + floor(random() * {PATRONYMICS_LEN})::int AS pi,
                               floor(random() * 15000)::int AS bd,
                               1 + floor(random() * {LEGAL_FORMS_LEN})::int AS lf,
                               1 + floor(random() * {COMPANY_PREFIXES_LEN})::int AS w1,
                               1 + floor(random() * {COMPANY_SUFFIXES_LEN})::int AS w2
                        FROM generate_series(1, {CLIENTS}) g
                    ) r
                    """).replace("{CLIENTS}", String.valueOf(clients)));
            log.info("Демо: клиенты готовы ({} с)", seconds(started));

            // Порядковые номера клиентов: договоры ссылаются на случайный номер, а не на id
            jdbc.execute("""
                    CREATE TEMP TABLE demo_client_idx ON COMMIT DROP AS
                    SELECT row_number() OVER (ORDER BY id) AS rn, id, client_type FROM clients
                    """);
            jdbc.execute("CREATE UNIQUE INDEX ON demo_client_idx (rn)");
            jdbc.execute("""
                    CREATE TEMP TABLE demo_individual_idx ON COMMIT DROP AS
                    SELECT row_number() OVER (ORDER BY id) AS rn, id
                    FROM demo_client_idx
                    WHERE client_type = 'INDIVIDUAL' AND rn <= %d
                    """.formatted(regularClients));
            jdbc.execute("CREATE UNIQUE INDEX ON demo_individual_idx (rn)");
            long individuals = count("SELECT count(*) FROM demo_individual_idx");

            // Последние `singles` договоров получают контрагентом "одиночку" - каждого ровно раз.
            // Агенты и выгодоприобретатели берутся только из обычных клиентов.
            jdbc.execute("""
                    CREATE TEMP TABLE demo_contract_gen ON COMMIT DROP AS
                    SELECT nextval('contract_seq') AS id,
                           g,
                           floor(random() * 100)::int AS roll,
                           CASE WHEN g > %1$d - %2$d THEN %3$d + (g - (%1$d - %2$d))
                                ELSE 1 + floor(random() * %3$d)::bigint END AS counterparty_rn,
                           1 + floor(random() * %3$d)::bigint AS agent_rn,
                           1 + floor(random() * %4$d)::bigint AS beneficiary_rn
                    FROM generate_series(1, %1$d) g
                    """.formatted(contracts, singles, regularClients, individuals));

            // Номера в двух форматах: "ДГ-00000124" и "123/КР-2023"
            jdbc.update("""
                    INSERT INTO contracts (id, contract_type, contract_number)
                    SELECT id,
                           CASE WHEN roll < 70 THEN 'STANDARD'
                                WHEN roll < 85 THEN 'WITH_BENEFICIARY'
                                WHEN roll < 95 THEN 'WITH_AGENT'
                                ELSE 'WITH_AGENT_AND_BENEFICIARY' END,
                           CASE WHEN g % 2 = 0 THEN 'ДГ-' || lpad(g::text, 8, '0')
                                ELSE g || '/КР-' || (2020 + g % 6) END
                    FROM demo_contract_gen
                    """);
            log.info("Демо: договоры готовы ({} с)", seconds(started));

            jdbc.update("""
                    INSERT INTO contract_parties (contract_id, client_id, role)
                    SELECT c.id, x.id, 'COUNTERPARTY'
                    FROM demo_contract_gen c JOIN demo_client_idx x ON x.rn = c.counterparty_rn
                    UNION ALL
                    SELECT c.id, x.id, 'AGENT'
                    FROM demo_contract_gen c JOIN demo_client_idx x ON x.rn = c.agent_rn
                    WHERE c.roll >= 85
                    UNION ALL
                    SELECT c.id, x.id, 'BENEFICIARY'
                    FROM demo_contract_gen c JOIN demo_individual_idx x ON x.rn = c.beneficiary_rn
                    WHERE (c.roll >= 70 AND c.roll < 85) OR c.roll >= 95
                    """);
            log.info("Демо: стороны договоров готовы ({} с)", seconds(started));
        });

        jdbc.execute("ANALYZE clients");
        jdbc.execute("ANALYZE contracts");
        jdbc.execute("ANALYZE contract_parties");
        log.info("Демо: справочник создан за {} с: клиентов {}, договоров {}, сторон {}",
                seconds(started), count("SELECT count(*) FROM clients"),
                count("SELECT count(*) FROM contracts"), count("SELECT count(*) FROM contract_parties"));
    }

    private static String fill(String sql) {
        return sql
                .replace("{SURNAMES_LEN}", String.valueOf(SURNAMES.length))
                .replace("{MALE_NAMES_LEN}", String.valueOf(MALE_NAMES.length))
                .replace("{FEMALE_NAMES_LEN}", String.valueOf(FEMALE_NAMES.length))
                .replace("{PATRONYMICS_LEN}", String.valueOf(MALE_PATRONYMICS.length))
                .replace("{LEGAL_FORMS_LEN}", String.valueOf(LEGAL_FORMS.length))
                .replace("{COMPANY_PREFIXES_LEN}", String.valueOf(COMPANY_PREFIXES.length))
                .replace("{COMPANY_SUFFIXES_LEN}", String.valueOf(COMPANY_SUFFIXES.length))
                .replace("{SURNAMES}", sqlArray(SURNAMES))
                .replace("{MALE_NAMES}", sqlArray(MALE_NAMES))
                .replace("{FEMALE_NAMES}", sqlArray(FEMALE_NAMES))
                .replace("{MALE_PATRONYMICS}", sqlArray(MALE_PATRONYMICS))
                .replace("{FEMALE_PATRONYMICS}", sqlArray(FEMALE_PATRONYMICS))
                .replace("{LEGAL_FORMS}", sqlArray(LEGAL_FORMS))
                .replace("{COMPANY_PREFIXES}", sqlArray(COMPANY_PREFIXES))
                .replace("{COMPANY_SUFFIXES}", sqlArray(COMPANY_SUFFIXES));
    }

    private static String sqlArray(String[] values) {
        return Arrays.stream(values).map(v -> "'" + v + "'").collect(Collectors.joining(",", "ARRAY[", "]"));
    }

    // ===================================================================
    // Операции по сценариям
    // ===================================================================

    private record ClientInfo(long id, String type, String inn, String lastName, String firstName,
                              String middleName, String organizationName) {

        boolean individual() {
            return !"LEGAL_ENTITY".equals(type);
        }

        boolean female() {
            return lastName != null && lastName.endsWith("а");
        }

        String displayName() {
            return individual() ? lastName + " " + firstName + " " + middleName : organizationName;
        }

        String genitiveName() {
            return RussianNames.genitive(lastName, firstName, middleName, female());
        }
    }

    private record SampledContract(long id, String number, long counterpartyId,
                                   Long beneficiaryId, int counterpartyContracts) {
    }

    private record Side(String inn, String name, String account) {
    }

    private void generateOperations() {
        int total = properties.getOperations();
        Random rnd = new Random(properties.getSeed());
        long started = System.currentTimeMillis();

        List<SampledContract> sample = sampleContracts(total * 4);
        Set<Long> clientIds = new HashSet<>();
        sample.forEach(c -> {
            clientIds.add(c.counterpartyId());
            if (c.beneficiaryId() != null) {
                clientIds.add(c.beneficiaryId());
            }
        });
        Map<Long, ClientInfo> clients = loadClients(clientIds);

        List<SampledContract> single = new ArrayList<>();
        List<SampledContract> multi = new ArrayList<>();
        List<SampledContract> withBeneficiary = new ArrayList<>();
        List<SampledContract> individualCounterparty = new ArrayList<>();
        for (SampledContract c : sample) {
            (c.counterpartyContracts() == 1 ? single : multi).add(c);
            if (c.beneficiaryId() != null && c.beneficiaryId() != c.counterpartyId()) {
                withBeneficiary.add(c);
            }
            if (clients.get(c.counterpartyId()).individual()) {
                individualCounterparty.add(c);
            }
        }
        List<SampledContract> all = new ArrayList<>(sample);
        for (List<SampledContract> list : List.of(all, single, multi, withBeneficiary, individualCounterparty)) {
            Collections.shuffle(list, rnd);
        }

        List<SqlParameterSource> rows = new ArrayList<>(total);
        Map<DemoScenario, Integer> created = new HashMap<>();
        int[] cursor = new int[1];
        for (DemoScenario scenario : DemoScenario.values()) {
            int quota = (int) Math.round(total * scenario.percent / 100.0);
            for (int i = 0; i < quota && rows.size() < total; i++) {
                SqlParameterSource row = switch (scenario) {
                    case NUMBER_AND_PARTY -> numberAndParty(next(all, cursor), clients, rnd, rows.size());
                    case SINGLE_CONTRACT -> partyOnly(scenario, pick(single, all, rnd), clients, rnd, rows.size());
                    case AMBIGUOUS -> partyOnly(scenario, pick(multi, all, rnd), clients, rnd, rows.size());
                    case THIRD_PARTY_NUMBER, THIRD_PARTY_NAME ->
                            thirdParty(scenario, pick(withBeneficiary, all, rnd), clients, rnd, rows.size());
                    case FALSE_THIRD_PARTY -> falseThirdParty(pick(individualCounterparty, all, rnd), clients, rnd, rows.size());
                    case NOT_FOUND -> notFound(rnd, rows.size());
                };
                rows.add(row);
                created.merge(scenario, 1, Integer::sum);
            }
        }

        named.batchUpdate("""
                INSERT INTO operations (
                    reference, operation_date,
                    sender_pin, sender_branch, sender_inn, sender_account, sender_name,
                    receiver_pin, receiver_branch, receiver_inn, receiver_account, receiver_name,
                    currency_code, amount, amount_rub, payment_purpose, status)
                VALUES (
                    :reference, :operationDate,
                    :senderPin, :senderBranch, :senderInn, :senderAccount, :senderName,
                    :receiverPin, :receiverBranch, :receiverInn, :receiverAccount, :receiverName,
                    '643', :amount, :amount, :purpose, 'PENDING')
                """, rows.toArray(SqlParameterSource[]::new));
        log.info("Демо: создано {} операций за {} с, по сценариям {}", rows.size(), seconds(started), created);
    }

    private List<SampledContract> sampleContracts(int wanted) {
        long totalContracts = count("SELECT count(*) FROM contracts");
        int limit = (int) Math.min(totalContracts, wanted);
        double percent = Math.min(100.0, 100.0 * limit * 1.5 / Math.max(1, totalContracts));

        // TABLESAMPLE читает случайные страницы, а не всю таблицу - быстро и на 10 млн.
        // Выборку перемешиваем до LIMIT: иначе обрезаются договоры из конца таблицы ("одиночки")
        List<long[]> rows = jdbc.query("""
                SELECT s.id,
                       (SELECT client_id FROM contract_parties p
                        WHERE p.contract_id = s.id AND p.role = 'COUNTERPARTY' LIMIT 1) AS counterparty_id,
                       (SELECT client_id FROM contract_parties p
                        WHERE p.contract_id = s.id AND p.role = 'BENEFICIARY' LIMIT 1) AS beneficiary_id
                FROM (
                    SELECT c.id FROM contracts c TABLESAMPLE SYSTEM (%1$s) REPEATABLE (%2$d)
                    ORDER BY md5(c.id::text || '%2$d')
                    LIMIT %3$d
                ) s
                """.formatted(String.format(Locale.ROOT, "%.6f", percent), properties.getSeed(), limit),
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getObject(3) == null ? -1 : rs.getLong(3)});

        Map<Long, String> numbers = new HashMap<>();
        Map<Long, Integer> counterpartyContracts = new HashMap<>();
        List<Long> contractIds = rows.stream().map(r -> r[0]).toList();
        List<Long> counterparties = rows.stream().map(r -> r[1]).distinct().toList();
        for (List<Long> chunk : chunks(contractIds)) {
            named.query("SELECT id, contract_number FROM contracts WHERE id IN (:ids)", Map.of("ids", chunk),
                    rs -> {
                        numbers.put(rs.getLong(1), rs.getString(2));
                    });
        }
        for (List<Long> chunk : chunks(counterparties)) {
            named.query("""
                    SELECT client_id, count(*) FROM contract_parties
                    WHERE client_id IN (:ids) GROUP BY client_id
                    """, Map.of("ids", chunk),
                    rs -> {
                        counterpartyContracts.put(rs.getLong(1), rs.getInt(2));
                    });
        }

        List<SampledContract> result = new ArrayList<>(rows.size());
        for (long[] r : rows) {
            result.add(new SampledContract(r[0], numbers.get(r[0]), r[1], r[2] < 0 ? null : r[2],
                    counterpartyContracts.getOrDefault(r[1], 0)));
        }
        return result;
    }

    private Map<Long, ClientInfo> loadClients(Set<Long> ids) {
        Map<Long, ClientInfo> result = new HashMap<>();
        for (List<Long> chunk : chunks(new ArrayList<>(ids))) {
            named.query("""
                    SELECT id, client_type, inn, last_name, first_name, middle_name, organization_name
                    FROM clients WHERE id IN (:ids)
                    """, Map.of("ids", chunk),
                    rs -> {
                        result.put(rs.getLong("id"), new ClientInfo(rs.getLong("id"), rs.getString("client_type"),
                                rs.getString("inn"), rs.getString("last_name"), rs.getString("first_name"),
                                rs.getString("middle_name"), rs.getString("organization_name")));
                    });
        }
        return result;
    }

    // --- сценарии ---

    private SqlParameterSource numberAndParty(SampledContract c, Map<Long, ClientInfo> clients, Random rnd, int seq) {
        String number = numberVariant(c.number(), rnd);
        String purpose = switch (rnd.nextInt(4)) {
            case 0 -> "Оплата по договору № " + number + " за услуги";
            case 1 -> "Оплата по договору " + number + " согласно счету № " + (1000 + rnd.nextInt(9000));
            case 2 -> "Перечисление аванса по дог. " + number;
            default -> "Оплата по договору поставки №" + number + ", в т.ч. НДС 20%";
        };
        return partyOperation(DemoScenario.NUMBER_AND_PARTY, c, clients.get(c.counterpartyId()), purpose, rnd, seq);
    }

    private SqlParameterSource partyOnly(DemoScenario scenario, SampledContract c, Map<Long, ClientInfo> clients,
                                         Random rnd, int seq) {
        String purpose = switch (rnd.nextInt(4)) {
            case 0 -> "Оплата за услуги по счету № " + (1000 + rnd.nextInt(9000)) + " от " + randomDate(rnd);
            case 1 -> "Оплата за товар по счету-фактуре № " + (100 + rnd.nextInt(900));
            case 2 -> "Предоплата за поставку продукции";
            default -> "Оплата за оказанные услуги за " + randomMonth(rnd);
        };
        return partyOperation(scenario, c, clients.get(c.counterpartyId()), purpose, rnd, seq);
    }

    /** Сторона операции - контрагент договора (отправитель или получатель), вторая сторона - внешняя. */
    private SqlParameterSource partyOperation(DemoScenario scenario, SampledContract c, ClientInfo party,
                                              String purpose, Random rnd, int seq) {
        Side known = side(party, rnd);
        Side external = externalSide(rnd);
        boolean partyIsSender = rnd.nextBoolean();
        return operation(scenario, c.id(), partyIsSender ? known : external, partyIsSender ? external : known,
                purpose, rnd, seq);
    }

    /** Внешний плательщик платит контрагенту договора за выгодоприобретателя. */
    private SqlParameterSource thirdParty(DemoScenario scenario, SampledContract c, Map<Long, ClientInfo> clients,
                                          Random rnd, int seq) {
        if (c.beneficiaryId() == null) {
            return numberAndParty(c, clients, rnd, seq);
        }
        ClientInfo beneficiary = clients.get(c.beneficiaryId());

        if (beneficiary == null || !beneficiary.individual()) {
            return numberAndParty(c, clients, rnd, seq);
        }
        String purpose;
        if (scenario == DemoScenario.THIRD_PARTY_NUMBER) {
            String number = numberVariant(c.number(), rnd);
            purpose = rnd.nextBoolean()
                    ? "Оплата по договору № " + number + " за третье лицо, выгодоприобретатель " + beneficiary.displayName()
                    : "Оплата страховой премии по договору " + number + ", выгодоприобретатель " + beneficiary.displayName();
        } else {
            purpose = switch (rnd.nextInt(3)) {
                case 0 -> "Оплата за третье лицо " + beneficiary.displayName();
                case 1 -> "Перевод в пользу третьего лица " + beneficiary.genitiveName();
                default -> "Выгодоприобретатель " + beneficiary.displayName() + ", оплата обучения";
            };
        }
        return operation(scenario, c.id(), externalSide(rnd), side(clients.get(c.counterpartyId()), rnd),
                purpose, rnd, seq);
    }

    /** Физлицо платит по своему договору и само указано "выгодоприобретателем". */
    private SqlParameterSource falseThirdParty(SampledContract c, Map<Long, ClientInfo> clients, Random rnd, int seq) {
        ClientInfo payer = clients.get(c.counterpartyId());
        if (!payer.individual()) {
            return numberAndParty(c, clients, rnd, seq);
        }
        String purpose = "Платеж по договору № " + numberVariant(c.number(), rnd)
                + ", выгодоприобретатель " + payer.displayName();
        return operation(DemoScenario.FALSE_THIRD_PARTY, c.id(), side(payer, rnd), externalSide(rnd), purpose, rnd, seq);
    }

    private SqlParameterSource notFound(Random rnd, int seq) {
        String purpose = rnd.nextBoolean()
                ? "Оплата по договору № ДГ-" + String.format("%09d", rnd.nextInt(1_000_000_000))
                : "Оплата за услуги по счету № " + (1000 + rnd.nextInt(9000));
        return operation(DemoScenario.NOT_FOUND, 0, externalSide(rnd), externalSide(rnd), purpose, rnd, seq);
    }

    private SqlParameterSource operation(DemoScenario scenario, long expectedContract, Side sender, Side receiver,
                                         String purpose, Random rnd, int seq) {
        String reference = "DEMO-%s-%s-%d-%06d".formatted(scenario.name(), scenario.expected.name(),
                scenario.expected.name().equals("AUTO_MAPPED") ? expectedContract : 0, seq);
        BigDecimal amount = BigDecimal.valueOf(100 + rnd.nextInt(500_000));
        return new MapSqlParameterSource()
                .addValue("reference", reference)
                .addValue("operationDate", LocalDateTime.now().minusDays(rnd.nextInt(30)).minusMinutes(rnd.nextInt(1440)))
                .addValue("senderPin", pin(rnd))
                .addValue("senderBranch", String.format("%04d", rnd.nextInt(10_000)))
                .addValue("senderInn", sender.inn())
                .addValue("senderAccount", sender.account())
                .addValue("senderName", sender.name())
                .addValue("receiverPin", pin(rnd))
                .addValue("receiverBranch", String.format("%04d", rnd.nextInt(10_000)))
                .addValue("receiverInn", receiver.inn())
                .addValue("receiverAccount", receiver.account())
                .addValue("receiverName", receiver.name())
                .addValue("amount", amount)
                .addValue("purpose", purpose);
    }

    // --- вспомогательные ---

    private static Side side(ClientInfo client, Random rnd) {
        String prefix = client.individual() ? "40817" : "40702";
        return new Side(client.inn(), client.displayName(), account(prefix, rnd));
    }

    // ИНН внешних контрагентов начинаются с 99 - в справочнике таких нет
    private static Side externalSide(Random rnd) {
        int n = rnd.nextInt(100_000_000);
        return new Side("99" + String.format("%08d", n), "ООО «Внешний партнер " + n + "»", account("40702", rnd));
    }

    // Номер в назначении пишут по-разному: регистр, пробел вместо дефиса - нормализация это сглаживает.
    // Пробел допустим только после буквенного префикса ("ДГ 00000124"): в "123/КР 2023" хвост уже не отличить от текста
    private static String numberVariant(String number, Random rnd) {
        return switch (rnd.nextInt(4)) {
            case 0 -> number.toLowerCase();
            case 1 -> number.startsWith("ДГ-") ? number.replace('-', ' ') : number;
            default -> number;
        };
    }

    private static String account(String prefix, Random rnd) {
        StringBuilder sb = new StringBuilder(prefix).append("810");
        for (int i = 0; i < 12; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }

    private static String pin(Random rnd) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String randomDate(Random rnd) {
        return LocalDate.now().minusDays(rnd.nextInt(60)).toString();
    }

    private static String randomMonth(Random rnd) {
        String[] months = {"январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август",
                "сентябрь", "октябрь"};
        return months[rnd.nextInt(months.length)] + " 2026 г.";
    }

    private static SampledContract next(List<SampledContract> list, int[] cursor) {
        SampledContract value = list.get(cursor[0] % list.size());
        cursor[0]++;
        return value;
    }

    // Берём из подходящего списка без повторов, пока он не кончится; дальше - любой договор
    private static SampledContract pick(List<SampledContract> preferred, List<SampledContract> fallback, Random rnd) {
        if (!preferred.isEmpty()) {
            return preferred.remove(preferred.size() - 1);
        }
        return fallback.get(rnd.nextInt(fallback.size()));
    }

    private static List<List<Long>> chunks(List<Long> values) {
        List<List<Long>> result = new ArrayList<>();
        for (int from = 0; from < values.size(); from += 1000) {
            result.add(values.subList(from, Math.min(from + 1000, values.size())));
        }
        return result;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static long seconds(long startedMillis) {
        return (System.currentTimeMillis() - startedMillis) / 1000;
    }
}
