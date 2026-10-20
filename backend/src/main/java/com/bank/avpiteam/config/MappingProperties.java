package com.bank.avpiteam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры модуля маппинга. Правила сопоставления заказчиком пока не заданы,
 * поэтому веса и пороги вынесены в конфиг - их можно подкрутить без пересборки.
 * Описание правил - docs/mapping.md.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mapping")
public class MappingProperties {

    // Сколько операций обрабатывается за одну транзакцию
    private int batchSize = 500;

    // Сколько лучших кандидатов сохраняется в operation_mapping_candidate
    private int maxStoredCandidates = 10;

    // Клиент-"хаб" с большим числом договоров не перебирается целиком:
    // его договоры попадают в кандидаты только по номеру договора или выгодоприобретателю
    private int maxContractsPerParty = 200;

    // Слишком распространённое ФИО (много тёзок) не используется для поиска кандидатов
    private int maxClientsPerName = 50;

    // Автопривязка: лучший кандидат набрал не меньше порога и отрывается от второго на minGap
    private double autoThreshold = 0.6;
    private double minGap = 0.2;

    private Weights weights = new Weights();

    @Getter
    @Setter
    public static class Weights {
        // Отправитель или получатель - сторона договора
        private double party = 0.4;
        // Обе стороны операции - стороны одного договора
        private double bothSides = 0.2;
        // Номер договора упомянут в назначении платежа
        private double contractNumber = 0.4;
        // ФИО третьего лица из назначения совпало с выгодоприобретателем договора
        private double beneficiaryName = 0.3;
    }
}
