package com.bank.avpiteam.settings;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Проверка и нормализация настроек: нижний регистр, без дублей, `*` только в конце слова. */
@Component
public final class NlpSettingsValidator {
    static final int MAX_WORDS = 100;
    static final int MIN_LEN = 2;
    static final int MAX_LEN = 50;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}-]+\\*?");

    public NlpSettingsValidator() {
    }

    /** Возвращает копию с нормализованными значениями или бросает Validation. */
    public static NlpSettingsRequest validate(NlpSettingsRequest r) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (r == null) {
            throw new NlpSettingsErrors.Validation(Map.of("body", "required"));
        }
        if (r.version() == null) errors.put("version", "required");
        Double t = r.threshold();
        if (t == null || t.isNaN() || t < 0 || t > 1) errors.put("threshold", "must be between 0 and 1");
        List<String> trigger = words("triggerWords", r.triggerWords(), errors);
        List<String> negation = words("negationWords", r.negationWords(), errors);
        if (!errors.isEmpty()) throw new NlpSettingsErrors.Validation(errors);
        return new NlpSettingsRequest(r.version(), Math.round(t * 1000) / 1000.0, trigger, negation, r.comment());
    }

    private static List<String> words(String field, List<String> raw, Map<String, String> errors) {
        if (raw == null || raw.isEmpty()) {
            errors.put(field, "must not be empty");
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String w : raw) {
            String s = w == null ? "" : w.trim().toLowerCase(Locale.ROOT);
            String stem = s.endsWith("*") ? s.substring(0, s.length() - 1) : s;
            boolean ok = WORD.matcher(s).matches()
                    && stem.length() >= MIN_LEN && stem.length() <= MAX_LEN
                    && stem.codePoints().anyMatch(Character::isLetter);
            if (!ok) {
                errors.put(field, "invalid word '" + w + "': 2-50 letters or digits, optional * at the end");
                return List.of();
            }
            out.add(s);
        }
        if (out.size() > MAX_WORDS) {
            errors.put(field, "at most " + MAX_WORDS + " words");
            return List.of();
        }
        return List.copyOf(out);
    }
}