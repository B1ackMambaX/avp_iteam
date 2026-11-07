package com.bank.avpiteam.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.bank.avpiteam.demo.ChaosScenario.ChaosRef;
import com.bank.avpiteam.demo.ChaosText.NameShape;
import java.util.List;
import java.util.Random;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ChaosTextTest {

    private static final UnaryOperator<String> NORM =
            s -> s.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase().replace('Ё', 'Е');

    @Test
    void nominativeShapes() {
        assertEquals("Иван Иванович",
                ChaosText.partialName("Иванов", "Иван", "Иванович", false, NameShape.FIRST_MIDDLE, false));
        assertEquals("Иванов Иванович",
                ChaosText.partialName("Иванов", "Иван", "Иванович", false, NameShape.LAST_MIDDLE, false));
    }

    @Test
    void genitiveMale() {
        assertEquals("Ивана Ивановича",
                ChaosText.partialName("Иванов", "Иван", "Иванович", false, NameShape.FIRST_MIDDLE, true));
        assertEquals("Иванова Ивановича",
                ChaosText.partialName("Иванов", "Иван", "Иванович", false, NameShape.LAST_MIDDLE, true));
    }

    @Test
    void genitiveFemale() {
        assertEquals("Марии Петровны",
                ChaosText.partialName("Иванова", "Мария", "Петровна", true, NameShape.FIRST_MIDDLE, true));
        assertEquals("Ивановой Петровны",
                ChaosText.partialName("Иванова", "Мария", "Петровна", true, NameShape.LAST_MIDDLE, true));
    }

    @Test
    void emptyPartsDoNotLeaveStrayLetters() {
        assertEquals("Ивана Ивановича", RussianNames.genitivePart("", "Иван", "Иванович", false, true, true, true));
        assertEquals("", RussianNames.genitivePart(null, null, null, false, true, true, true));
    }

    @Test
    void noisyNumberAlwaysChangesNormalizedForm() {
        for (String number : List.of("ДГ-123/2024", "468125/КР-2025", "7", "ДГ 77", "12345")) {
            for (int seed = 0; seed < 500; seed++) {
                String noisy = ChaosText.noisyNumber(number, new Random(seed), NORM);
                assertNotEquals(NORM.apply(number), NORM.apply(noisy), number + " seed=" + seed);
            }
        }
    }

    @Test
    void deterministicForSameSeed() {
        assertEquals(ChaosText.noisyNumberPurpose("ДГ-123/2024", new Random(5), NORM),
                ChaosText.noisyNumberPurpose("ДГ-123/2024", new Random(5), NORM));
        assertEquals(ChaosText.partialNamePurpose("Иванов", "Иван", "Иванович", false, new Random(5)),
                ChaosText.partialNamePurpose("Иванов", "Иван", "Иванович", false, new Random(5)));
    }

    @Test
    void referenceRoundTrip() {
        String ref = ChaosScenario.NOISY_NUMBER_WITH_PARTY.reference(77L, 12);
        assertEquals("CHAOS-NOISY_NUMBER_WITH_PARTY-AUTO_MAPPED+AMBIGUOUS-77-000012", ref);
        ChaosRef parsed = ChaosScenario.parse(ref).orElseThrow();
        assertEquals(ChaosScenario.NOISY_NUMBER_WITH_PARTY, parsed.scenario());
        assertTrue(parsed.isCorrect("AMBIGUOUS", null));
        assertTrue(parsed.isCorrect("AUTO_MAPPED", 77L));
        assertFalse(parsed.isCorrect("AUTO_MAPPED", 78L));
        assertFalse(parsed.isCorrect("NOT_FOUND", null));
        assertTrue(ChaosScenario.parse("DEMO-NUMBER_AND_PARTY-AUTO_MAPPED-1-000002").isEmpty());
    }

    @Test
    void partialNameNeverAllowsAuto() {
        assertFalse(ChaosScenario.PARTIAL_NAME_ONLY.allowedStatuses().contains("AUTO_MAPPED"));
    }
}