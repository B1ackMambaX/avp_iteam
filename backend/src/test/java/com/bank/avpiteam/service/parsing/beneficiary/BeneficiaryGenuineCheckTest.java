package com.bank.avpiteam.service.parsing.beneficiary;

import static com.bank.avpiteam.service.parsing.beneficiary.BeneficiaryDetectionServiceImpl.isNotOperationParty;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BeneficiaryGenuineCheckTest {

    @Test
    void sameNameWithYoIsNotThirdParty() {
        assertFalse(isNotOperationParty("Семёнов Игорь Васильевич",
                "Семенов Игорь Васильевич", "ООО «Внешний партнер»"));
    }

    @Test
    void receiverMatchAlsoCounts() {
        assertFalse(isNotOperationParty("Королёва Надежда", "ООО «Х»", "королева  надежда"));
    }

    @Test
    void caseAndSpacesAreIgnored() {
        assertFalse(isNotOperationParty("  КОВАЛЁВ   Александр ", "Ковалев Александр", null));
    }

    @Test
    void differentPersonIsThirdParty() {
        assertTrue(isNotOperationParty("Семенов Игнат", "Семенов Игорь", "ООО «Х»"));
    }

    @Test
    void missingPartyNamesDoNotMatch() {
        assertTrue(isNotOperationParty("Иванов Иван", null, null));
    }
}