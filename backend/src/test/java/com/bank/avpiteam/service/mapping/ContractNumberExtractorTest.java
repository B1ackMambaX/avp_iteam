package com.bank.avpiteam.service.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContractNumberExtractorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Оплата по договору № ДГ-00000124 за услуги         | ДГ00000124",
            "Оплата по договору дг-00000124 согласно счету № 55 | ДГ00000124",
            "Перечисление аванса по дог. ДГ 00000124            | ДГ00000124",
            "Оплата по договору поставки №123/КР-2023, НДС 20%  | 123КР2023",
            "Оплата за третье лицо по договору №110-кр          | 110КР",
            "Платеж по договору № 123/кр-2023                   | 123КР2023",
    })
    void extractsNormalizedNumber(String purpose, String expected) {
        assertThat(ContractNumberExtractor.extractNormalized(purpose)).containsExactly(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Оплата за услуги по счету № 4521 от 2026-10-01",
            "Оплата по счету-фактуре № 812",
            "Платеж по поручению Сидорова Алексея Алексеевича, основание - договор подряда",
            "Предоплата за поставку продукции",
    })
    void ignoresNumbersNotAfterContractWord(String purpose) {
        assertThat(ContractNumberExtractor.extractNormalized(purpose)).isEmpty();
    }

    @Test
    void extractsSeveralNumbers() {
        assertThat(ContractNumberExtractor.extractNormalized("Оплата по договору № 12-А и договору № 13-Б"))
                .containsExactly("12А", "13Б");
    }

    @Test
    void normalizationMatchesSqlGeneratedColumn() {
        // те же примеры, что проверялись на contracts.contract_number_norm в V2
        assertThat(Normalization.contractNumber("№ 110-кр")).isEqualTo("110КР");
        assertThat(Normalization.contractNumber("DOG-00000001")).isEqualTo("DOG00000001");
        assertThat(Normalization.fullName("  ЁЛКИН   Пётр ")).isEqualTo("елкин петр");
    }
}
