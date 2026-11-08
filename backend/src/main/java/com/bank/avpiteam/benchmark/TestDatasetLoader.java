package com.bank.avpiteam.benchmark;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Загружает размеченный датасет назначений платежей из CSV-файла.
 * Ожидаемые колонки: id, payment_purpose,
 * expected_is_third_party, expected_beneficiary_name
 */
public final class TestDatasetLoader {

    private TestDatasetLoader() {
    }

    public static List<TestDatasetRow> load(Path csvPath) throws IOException {
        List<TestDatasetRow> rows = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                long id = Long.parseLong(record.get("id"));
                String purpose = record.get("payment_purpose");
                boolean expectedThirdParty = Boolean.parseBoolean(
                        record.get("expected_is_third_party"));
                String expectedName = record.get("expected_beneficiary_name");
                if (expectedName != null && expectedName.isBlank()) {
                    expectedName = null;
                }

                rows.add(new TestDatasetRow(id, purpose, expectedThirdParty, expectedName));
            }
        }

        return rows;
    }
}