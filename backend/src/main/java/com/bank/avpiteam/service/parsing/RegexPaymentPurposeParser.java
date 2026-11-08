package com.bank.avpiteam.service.parsing;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RegexPaymentPurposeParser implements PaymentPurposeParser {

    /**
     * Маркеры теперь компилируются как regex с границами слова
     * (\b...\b), а не ищутся через indexOf. Это закрывает баг
     * "алиментарный" -> ложное срабатывание на "алимент".
     */
    private static final List<Pattern> MARKER_PATTERNS = List.of(
            Pattern.compile("\\bвыгодоприобретател\\w*\\b", Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("\\bв\\s+пользу\\b", Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("\\bпо\\s+поручени\\w*\\b", Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("\\bалимент\\w*\\b", Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("\\bисполнительн\\w*\\b", Pattern.UNICODE_CHARACTER_CLASS),
            Pattern.compile("треть\\S*\\s+лиц\\S*")
    );

    /**
     * Слова-отрицания. Если одно из них встречается в окне
     * NEGATION_WINDOW символов ПЕРЕД маркером — считаем,
     * что маркер отрицается, и не засчитываем совпадение.
     * Закрывает баг "третьи лица НЕ привлекались".
     */
    private static final Set<String> NEGATION_WORDS = Set.of(
            "не", "нет", "отсутств", "без"
    );

    private static final int NEGATION_WINDOW = 20; // символов перед маркером

    private static final Pattern FIO_PATTERN = Pattern.compile(
            "\\b[А-ЯЁ][а-яё]+\\s+[А-ЯЁ][а-яё]+(?:\\s+[А-ЯЁ][а-яё]+)?\\b",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private static final Set<String> ORG_PREFIXES = Set.of(
            "ООО", "АО", "ЗАО", "ПАО", "ИП", "ОАО"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "выгодоприобретатель", "оплата", "перевод", "платеж", "платёж",
            "перечисление", "возврат", "погашение", "заработная", "алименты"
    );

    @Override
    public ParsedPaymentPurpose parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ParsedPaymentPurpose.notThirdParty();
        }

        String lower = rawText.toLowerCase(Locale.of("ru"));
        int markerPosition = findValidMarkerPosition(lower);
        boolean isThirdParty = markerPosition >= 0;

        if (!isThirdParty) {
            return ParsedPaymentPurpose.notThirdParty();
        }

        String beneficiaryName = extractBeneficiaryName(rawText, markerPosition);
        double confidence = (beneficiaryName != null) ? 0.7 : 0.3;

        return new ParsedPaymentPurpose(true, beneficiaryName, confidence);
    }

    /**
     * Ищет самый ранний маркер, который НЕ находится под отрицанием.
     * Если маркер найден, но прямо перед ним есть отрицание в пределах
     * NEGATION_WINDOW символов — пропускаем его и ищем следующий.
     */
    private int findValidMarkerPosition(String lowerText) {
        int earliest = -1;

        for (Pattern pattern : MARKER_PATTERNS) {
            Matcher matcher = pattern.matcher(lowerText);
            while (matcher.find()) {
                int idx = matcher.start();
                if (isNegated(lowerText, idx, matcher.end())) {
                    continue; // этот конкретный матч отрицается, ищем следующий
                }
                if (earliest == -1 || idx < earliest) {
                    earliest = idx;
                }
                break; // первый неотрицаемый матч для этого паттерна достаточен
            }
        }

        return earliest;
    }

    private boolean isNegated(String lowerText, int markerStart, int markerEnd) {
        int windowStart = Math.max(0, markerStart - NEGATION_WINDOW);
        int windowEnd = Math.min(lowerText.length(), markerEnd + NEGATION_WINDOW);
        String window = lowerText.substring(windowStart, windowEnd);
        return NEGATION_PATTERN.matcher(window).find();
    }

    private String extractBeneficiaryName(String originalText, int markerPosition) {
        Matcher matcher = FIO_PATTERN.matcher(originalText);
        String bestCandidate = null;
        int bestDistance = Integer.MAX_VALUE;

        while (matcher.find()) {
            String rawCandidate = matcher.group();
            int candidateStart = matcher.start();

            if (looksLikeOrganization(originalText, candidateStart)) {
                continue;
            }
            String cleaned = stripLeadingStopWord(rawCandidate);
            if (cleaned == null) {
                continue;
            }
            int distance = Math.abs(candidateStart - markerPosition);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCandidate = cleaned;
            }
        }
        return bestCandidate;
    }

    private String stripLeadingStopWord(String candidate) {
        String[] words = candidate.split("\\s+");
        if (words.length == 0) return null;

        String firstWordLower = words[0].toLowerCase(Locale.of("ru"));
        boolean firstIsStopWord = STOP_WORDS.stream().anyMatch(firstWordLower::startsWith);
        if (!firstIsStopWord) return candidate;

        String[] remaining = Arrays.copyOfRange(words, 1, words.length);
        if (remaining.length < 2) return null;
        return String.join(" ", remaining);
    }

    private boolean looksLikeOrganization(String text, int candidateStart) {
        String before = text.substring(0, candidateStart).stripTrailing();
        if (before.endsWith("\"") || before.endsWith("«")) return true;
        for (String prefix : ORG_PREFIXES) {
            if (before.endsWith(prefix)) return true;
        }
        return false;
    }

    private static final Pattern NEGATION_PATTERN = Pattern.compile(
            "\\b(не|нет|отсутств\\w*|без)\\b", Pattern.UNICODE_CHARACTER_CLASS
    );
}