package com.bank.avpiteam.settings;

public record NlpTestResponse(int settingsVersion, boolean overridden, double threshold,
                              String decision, double score, boolean thirdParty,
                              String extractedName) {}