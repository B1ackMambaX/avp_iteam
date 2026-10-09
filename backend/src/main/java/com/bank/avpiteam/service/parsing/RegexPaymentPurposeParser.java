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

    // Простые substring-маркеры. "алимент" и "исполнительн" уже
    // работают как поиск по корню - ловят все словоформы автоматически.
    private static final List<String> SIMPLE_MARKERS = List.of(
            "выгодоприобретатель",
            "в пользу",
            "по поручению",
            "алимент",
            "исполнительн"
    );

    /**
     * Отдельный паттерн для фразы "третье лицо" - заменён на поиск
     * по корню, чтобы ловить все падежные формы: "третье лицо",
     * "третьего лица", "третьему лицу", "третьих лиц" и т.д.
     */
    private static final Pattern THIRD_PARTY_PHRASE = Pattern.compile(
            "треть\\S*\\s+лиц\\S*"
    );

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
        int markerPosition = findMarkerPosition(lower);
        boolean isThirdParty = markerPosition >= 0;

        if (!isThirdParty) {
            return ParsedPaymentPurpose.notThirdParty();
        }

        String beneficiaryName = extractBeneficiaryName(rawText, markerPosition);
        double confidence = (beneficiaryName != null) ? 0.7 : 0.3;

        return new ParsedPaymentPurpose(true, beneficiaryName, confidence);
    }

    /**
     * Ищет либо простой substring-маркер, либо regex-паттерн
     * "третье лицо" в любой словоформе. Возвращает позицию самого
     * раннего совпадения, либо -1, если ничего не найдено.
     */
    private int findMarkerPosition(String lowerText) {
        int earliest = -1;

        for (String marker : SIMPLE_MARKERS) {
            int idx = lowerText.indexOf(marker);
            if (idx >= 0 && (earliest == -1 || idx < earliest)) {
                earliest = idx;
            }
        }

        Matcher phraseMatcher = THIRD_PARTY_PHRASE.matcher(lowerText);
        if (phraseMatcher.find()) {
            int idx = phraseMatcher.start();
            if (earliest == -1 || idx < earliest) {
                earliest = idx;
            }
        }

        return earliest;
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
        if (words.length == 0) {
            return null;
        }

        String firstWordLower = words[0].toLowerCase(Locale.of("ru"));
        boolean firstIsStopWord = STOP_WORDS.stream()
                .anyMatch(firstWordLower::startsWith);

        if (!firstIsStopWord) {
            return candidate;
        }

        String[] remaining = Arrays.copyOfRange(words, 1, words.length);
        if (remaining.length < 2) {
            return null;
        }
        return String.join(" ", remaining);
    }

    private boolean looksLikeOrganization(String text, int candidateStart) {
        String before = text.substring(0, candidateStart).stripTrailing();

        if (before.endsWith("\"") || before.endsWith("«")) {
            return true;
        }
        for (String prefix : ORG_PREFIXES) {
            if (before.endsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}