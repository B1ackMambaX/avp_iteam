package com.bank.avpiteam.settings;

import java.util.List;

/** Слова = null означает «значения по умолчанию на стороне Python». */
public record EffectiveNlpSettings(int version, double threshold,
                                   List<String> triggerWords, List<String> negationWords) {}