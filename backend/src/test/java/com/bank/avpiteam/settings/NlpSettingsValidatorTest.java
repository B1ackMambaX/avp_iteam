package com.bank.avpiteam.settings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NlpSettingsValidatorTest {

    private static NlpSettingsRequest req(Double t, List<String> trigger, List<String> negation) {
        return new NlpSettingsRequest(1, t, trigger, negation, null);
    }

    private static NlpSettingsErrors.Validation fails(NlpSettingsRequest r) {
        return assertThrows(NlpSettingsErrors.Validation.class, () -> NlpSettingsValidator.validate(r));
    }

    @Test
    void normalizesCaseWhitespaceAndDuplicates() {
        var r = NlpSettingsValidator.validate(req(0.5, List.of(" Треть* ", "треть*", "ПОЛЬЗ*"), List.of("не")));
        assertEquals(List.of("треть*", "польз*"), r.triggerWords());
    }

    @Test
    void thresholdBounds() {
        assertTrue(fails(req(1.01, List.of("аб"), List.of("не"))).fields().containsKey("threshold"));
        assertTrue(fails(req(-0.1, List.of("аб"), List.of("не"))).fields().containsKey("threshold"));
        assertTrue(fails(req(null, List.of("аб"), List.of("не"))).fields().containsKey("threshold"));
        assertEquals(0.0, NlpSettingsValidator.validate(req(0.0, List.of("аб"), List.of("не"))).threshold());
        assertEquals(1.0, NlpSettingsValidator.validate(req(1.0, List.of("аб"), List.of("не"))).threshold());
    }

    @Test
    void emptyListRejected() {
        assertTrue(fails(req(0.5, List.of(), List.of("не"))).fields().containsKey("triggerWords"));
        assertTrue(fails(req(0.5, List.of("аб"), null)).fields().containsKey("negationWords"));
    }

    @Test
    void wordLengthAndShape() {
        for (String bad : List.of("а", "а*", "*", "п*р", "аб cd", "аб.", ".*", "--", "x".repeat(51))) {
            assertTrue(fails(req(0.5, List.of(bad), List.of("не"))).fields().containsKey("triggerWords"), bad);
        }
        for (String ok : List.of("аб", "аб*", "x".repeat(50), "по-русски*", "n1")) {
            assertDoesNotThrow(() -> NlpSettingsValidator.validate(req(0.5, List.of(ok), List.of("не"))), ok);
        }
    }

    @Test
    void atMost100Words() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) many.add("слово" + i);
        assertTrue(fails(req(0.5, many, List.of("не"))).fields().containsKey("triggerWords"));
        assertDoesNotThrow(() -> NlpSettingsValidator.validate(req(0.5, many.subList(0, 100), List.of("не"))));
    }

    @Test
    void versionRequired() {
        var r = new NlpSettingsRequest(null, 0.5, List.of("аб"), List.of("не"), null);
        assertTrue(fails(r).fields().containsKey("version"));
    }
}