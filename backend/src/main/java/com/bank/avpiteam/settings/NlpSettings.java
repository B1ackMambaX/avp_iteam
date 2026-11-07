package com.bank.avpiteam.settings;

import java.time.OffsetDateTime;
import java.util.List;

public record NlpSettings(int version, double threshold, List<String> triggerWords,
                          List<String> negationWords, OffsetDateTime changedAt,
                          String changedBy, String comment) {
}