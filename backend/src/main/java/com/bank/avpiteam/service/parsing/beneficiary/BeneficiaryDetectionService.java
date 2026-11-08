package com.bank.avpiteam.service.parsing.beneficiary;

import com.bank.avpiteam.domain.Operation;

public interface BeneficiaryDetectionService {
    BeneficiaryDetectionResult detect(Operation operation);
}