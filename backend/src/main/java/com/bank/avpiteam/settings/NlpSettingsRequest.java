package com.bank.avpiteam.settings;

import java.util.List;

public record NlpSettingsRequest(Integer version, Double threshold, List<String> triggerWords,
                                 List<String> negationWords, String comment) {
}