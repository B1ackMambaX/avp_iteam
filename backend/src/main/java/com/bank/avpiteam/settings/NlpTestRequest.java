package com.bank.avpiteam.settings;

import java.util.List;

public record NlpTestRequest(String text, Double threshold,
                             List<String> triggerWords, List<String> negationWords) {}