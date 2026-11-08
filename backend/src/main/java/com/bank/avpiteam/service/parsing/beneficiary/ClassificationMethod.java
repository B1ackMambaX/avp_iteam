package com.bank.avpiteam.service.parsing.beneficiary;

public enum ClassificationMethod {
    MARKER,   // legacy-подход, у нас сейчас не используется, оставлен для совместимости со старыми записями
    SEMANTIC, // embedding similarity — наша Гипотеза В
    NLP_NER   // извлечение ФИО через Natasha
}