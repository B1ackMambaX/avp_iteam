package com.bank.avpiteam.ui;

import com.bank.avpiteam.settings.NlpSettingsRequest;
import com.bank.avpiteam.settings.NlpTestRequest;
import java.util.Arrays;
import java.util.List;

final class NlpFormData {
    private NlpFormData() {}

    static List<String> words(String text) {
        return Arrays.stream(text.split("[,\\n]"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static String lines(List<String> words) { return String.join("\n", words); }

    static NlpSettingsRequest settings(int version, double threshold, String triggers,
                                       String negations, String comment) {
        return new NlpSettingsRequest(version, threshold, words(triggers), words(negations), comment);
    }

    static NlpTestRequest test(String text, double threshold, String triggers, String negations) {
        return new NlpTestRequest(text, threshold, words(triggers), words(negations));
    }
}
