package com.bank.avpiteam.util;

import com.bank.avpiteam.domain.Client;
import com.bank.avpiteam.domain.Contract;
import com.bank.avpiteam.domain.Operation;
import com.bank.avpiteam.repository.ClientRepository;
import com.bank.avpiteam.repository.ContractRepository;
import com.bank.avpiteam.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Генератор тестовых справочных данных: клиенты и договоры.
 * Запускается только с профилем datagen, чтобы не плодить
 * мусор при обычном старте приложения.
 */
@Slf4j
@Component
@Profile("datagen")
@RequiredArgsConstructor
public class TestDataGenerator implements CommandLineRunner {

    private final ClientRepository clientRepository;
    private final ContractRepository contractRepository;
    private final OperationRepository operationRepository;

    @Value("${datagen.clients-count:10000}")
    private int clientsCount;

    @Value("${datagen.contracts-count:100000}")
    private int contractsCount;

    @Value("${datagen.operations-count:1000}")
    private int operationsCount;

    private static final Random RNG = new Random(42); // фиксированный seed - воспроизводимость

    private static final String[] FIRST_NAMES = {
            "Иван", "Александр", "Сергей", "Михаил", "Андрей",
            "Дмитрий", "Алексей", "Максим", "Евгений", "Николай",
            "Ольга", "Наталья", "Елена", "Анна", "Татьяна"
    };

    private static final String[] LAST_NAMES = {
            "Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев",
            "Петров", "Соколов", "Михайлов", "Новиков", "Федоров"
    };

    private static final String[] MIDDLE_NAMES = {
            "Иванович", "Александрович", "Сергеевич", "Петрович",
            "Андреевич", "Ивановна", "Александровна", "Сергеевна"
    };

    private static final String[] LEGAL_FORMS = {
            "ООО", "АО", "ПАО", "ИП"
    };

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== Запуск генератора тестовых данных ===");

        if (clientRepository.count() > 0) {
            log.info("Данные уже существуют, пропускаем генерацию");
            return;
        }

        log.info("Генерация {} клиентов...", clientsCount);
        List<Client> clients = generateClients(clientsCount);

        log.info("Генерация {} договоров...", contractsCount);
        generateContracts(clients, contractsCount);

        log.info("Генерация {} операций (без разметки, для нагрузки)...", operationsCount);
        generateOperations(clients, operationsCount);

        log.info("=== Генерация завершена ===");
    }

    private List<Client> generateClients(int count) {
        List<Client> allSaved = new ArrayList<>(count);
        List<Client> batch = new ArrayList<>(1000);

        for (int i = 0; i < count; i++) {
            boolean isLegal = i % 3 == 0;
            Client.ClientBuilder builder = Client.builder()
                    .inn(generateInn(i, isLegal))
                    .pin(generatePin());

            if (isLegal) {
                String form = LEGAL_FORMS[RNG.nextInt(LEGAL_FORMS.length)];
                builder.clientType(Client.ClientType.LEGAL_ENTITY)
                        .organizationName(form + " \"" + randomCompanyName() + "\"")
                        .legalForm(form)
                        .bik(generateBik())
                        .ogrn(generateOgrn())
                        .ogrnDate(randomPastDate(3000));
            } else {
                builder.clientType(Client.ClientType.INDIVIDUAL)
                        .lastName(randomLastName())
                        .firstName(randomFirstName())
                        .middleName(randomMiddleName())
                        .birthDate(randomPastDate(20000))
                        .birthPlace("г. " + randomCityName())
                        .citizenship("Российская Федерация")
                        .documentType("Паспорт гражданина РФ")
                        .documentSeries(String.valueOf(1000 + RNG.nextInt(8999)))
                        .documentNumber(String.valueOf(100000 + RNG.nextInt(899999)))
                        .documentIssueDate(randomPastDate(5000))
                        .documentIssuedBy("ОВД района " + randomCityName())
                        .documentDepartmentCode(RNG.nextInt(900) + "-" + RNG.nextInt(900))
                        .registrationAddress("г. " + randomCityName() + ", ул. Ленина, д. " + (RNG.nextInt(100) + 1));
            }

            batch.add(builder.build());

            if (batch.size() == 1000) {
                allSaved.addAll(clientRepository.saveAll(batch));
                batch.clear();
                log.debug("Сохранено {} клиентов", allSaved.size());
            }
        }
        if (!batch.isEmpty()) {
            allSaved.addAll(clientRepository.saveAll(batch));
        }
        return allSaved;
    }

    private void generateContracts(List<Client> clients, int count) {
        List<Contract> batch = new ArrayList<>(1000);
        int saved = 0;

        for (int i = 0; i < count; i++) {
            Client counterparty = randomClient(clients);

            // Распределение типов договоров:
            // 70% - простые, 15% - с выгодоприобретателем,
            // 10% - с агентом, 5% - с обоими
            int roll = RNG.nextInt(100);
            Contract.ContractType type;
            Client agent = null;
            Client beneficiary = null;

            if (roll < 70) {
                type = Contract.ContractType.STANDARD;
            } else if (roll < 85) {
                type = Contract.ContractType.WITH_BENEFICIARY;
                beneficiary = randomClient(clients);
                beneficiary.setIsBeneficiary(true);
            } else if (roll < 95) {
                type = Contract.ContractType.WITH_AGENT;
                agent = randomClient(clients);
            } else {
                type = Contract.ContractType.WITH_AGENT_AND_BENEFICIARY;
                agent = randomClient(clients);
                beneficiary = randomClient(clients);
                beneficiary.setIsBeneficiary(true);
            }

            Contract contract = Contract.builder()
                    .contractNumber("DOG-" + String.format("%08d", i))
                    .contractType(type)
                    .counterparty(counterparty)
                    .agent(agent)
                    .beneficiary(beneficiary)
                    .build();

            batch.add(contract);

            if (batch.size() == 1000) {
                contractRepository.saveAll(batch);
                saved += batch.size();
                batch.clear();
                log.debug("Сохранено {} договоров", saved);
            }
        }
        if (!batch.isEmpty()) {
            contractRepository.saveAll(batch);
        }
    }

    private void generateOperations(List<Client> clients, int count) {
        List<Operation> batch = new ArrayList<>(500);

        for (int i = 0; i < count; i++) {
            Client sender = randomClient(clients);
            Client receiver = randomClient(clients);

            BigDecimal amount = BigDecimal.valueOf(100 + RNG.nextInt(999900));

            Operation op = Operation.builder()
                    .reference("REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                    .operationDate(LocalDateTime.now().minusDays(RNG.nextInt(30)))
                    .senderPin(generatePin())
                    .senderBranch(generateBranch())
                    .senderInn(sender.getInn())
                    .senderAccount(generateAccountNumber(sender.getClientType()))
                    .senderName(displayName(sender))
                    .receiverPin(generatePin())
                    .receiverBranch(generateBranch())
                    .receiverInn(receiver.getInn())
                    .receiverAccount(generateAccountNumber(receiver.getClientType()))
                    .receiverName(displayName(receiver))
                    .currencyCode("810")
                    .amount(amount)
                    .amountRub(amount)
                    .paymentPurpose("Оплата по счету №" + RNG.nextInt(9999) + " от " + LocalDate.now())
                    .status(Operation.OperationStatus.PENDING)
                    .build();

            batch.add(op);

            if (batch.size() == 500) {
                operationRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            operationRepository.saveAll(batch);
        }
    }

    // Вспомогательные методы

    private Client randomClient(List<Client> clients) {
        return clients.get(RNG.nextInt(clients.size()));
    }

    private String displayName(Client c) {
        if (c.getClientType() == Client.ClientType.LEGAL_ENTITY) {
            return c.getOrganizationName();
        }
        return c.getLastName() + " " + c.getFirstName() + " " + c.getMiddleName();
    }

    private String generateInn(int seed, boolean isLegal) {
        // ЮЛ = 10 цифр, ФЛ/ИП - 12 цифр. Упрощённая генерация без контрольных сумм.
        long base = (seed * 7919L + 1_000_000_000L);
        if (isLegal) {
            return String.format("%010d", base % 10_000_000_000L);
        }
        return String.format("%012d", base % 1_000_000_000_000L);
    }

    private String generatePin() {
        // 6-значный буквенно-цифровой код
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(RNG.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateBranch() {
        return String.format("%04d", RNG.nextInt(10000));
    }

    private String generateBik() {
        return "04" + String.format("%07d", RNG.nextInt(10_000_000));
    }

    private String generateOgrn() {
        return String.format("%013d", (long) (RNG.nextDouble() * 1_000_000_0000000L));
    }

    /**
     * Генерация номера счёта с правильным префиксом в зависимости
     * от типа клиента - чтобы AccountTypeResolver потом корректно определял тип по счёту.
     */
    private String generateAccountNumber(Client.ClientType type) {
        String prefix = switch (type) {
            case LEGAL_ENTITY -> "40702";
            case ENTREPRENEUR -> "40802";
            case INDIVIDUAL -> "40817";
        };
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 15; i++) {
            sb.append(RNG.nextInt(10));
        }
        return sb.toString();
    }

    private String randomFirstName() {
        return FIRST_NAMES[RNG.nextInt(FIRST_NAMES.length)];
    }

    private String randomLastName() {
        return LAST_NAMES[RNG.nextInt(LAST_NAMES.length)];
    }

    private String randomMiddleName() {
        return MIDDLE_NAMES[RNG.nextInt(MIDDLE_NAMES.length)];
    }

    private String randomCompanyName() {
        String[] words = {"Ромашка", "Стройсервис", "ТехноПром", "АльфаТрейд", "ВостокГрупп"};
        return words[RNG.nextInt(words.length)];
    }

    private String randomCityName() {
        String[] cities = {"Москва", "Казань", "Новосибирск", "Самара", "Екатеринбург"};
        return cities[RNG.nextInt(cities.length)];
    }

    private LocalDate randomPastDate(int maxDaysAgo) {
        return LocalDate.now().minusDays(RNG.nextInt(maxDaysAgo));
    }
}