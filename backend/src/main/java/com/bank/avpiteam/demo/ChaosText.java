package com.bank.avpiteam.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** Тексты для хаотичных сценариев. Только чистые функции, ничего не знает про БД и клиентов. */
final class ChaosText {
    private ChaosText() {
    }

    enum NameShape { FIRST_MIDDLE, LAST_MIDDLE }

    private static final String CYR = "\u0410\u0412\u0415\u041A\u041C\u041D\u041E\u0420\u0421\u0422\u0425";
    private static final String LAT = "ABEKMHOPCTX";

    static String partialName(String last, String first, String middle, boolean female,
                              NameShape shape, boolean genitive) {
        boolean useLast = shape == NameShape.LAST_MIDDLE;
        boolean useFirst = shape == NameShape.FIRST_MIDDLE;
        if (genitive) {
            return RussianNames.genitivePart(last, first, middle, female, useLast, useFirst, true);
        }
        List<String> parts = new ArrayList<>();
        parts.add(useLast ? last : first);
        parts.add(middle);
        return parts.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" "));
    }

    static String partialNamePurpose(String last, String first, String middle, boolean female, Random rnd) {
        NameShape shape = NameShape.values()[rnd.nextInt(2)];
        return switch (rnd.nextInt(3)) {
            case 0 -> "Оплата за третье лицо " + partialName(last, first, middle, female, shape, false);
            case 1 -> "Перевод в пользу третьего лица " + partialName(last, first, middle, female, shape, true);
            default -> "Выгодоприобретатель " + partialName(last, first, middle, female, shape, false)
                    + ", оплата обучения";
        };
    }

    static String noisyNumber(String number, Random rnd, UnaryOperator<String> normalizer) {
        List<UnaryOperator<String>> ops = List.of(
                ChaosText::latinLookalike,
                n -> n.replaceFirst("^\\D+", ""),
                n -> n.replaceFirst("0", "\u041E"),
                n -> n.length() > 4 ? n.substring(0, n.length() - 2) : n,
                n -> "No " + n);
        String base = normalizer.apply(number);
        int start = rnd.nextInt(ops.size());
        for (int i = 0; i < ops.size(); i++) {
            String candidate = ops.get((start + i) % ops.size()).apply(number);
            if (!Objects.equals(normalizer.apply(candidate), base)) {
                return candidate;
            }
        }
        return number + "\u0425";
    }

    static String noisyNumberPurpose(String number, Random rnd, UnaryOperator<String> normalizer) {
        String n = noisyNumber(number, rnd, normalizer);
        return switch (rnd.nextInt(3)) {
            case 0 -> "Оплата по договору № " + n + " за услуги";
            case 1 -> "Перечисление аванса по дог. " + n;
            default -> "Оплата по договору " + n + ", в т.ч. НДС 20%";
        };
    }

    private static String latinLookalike(String number) {
        for (int i = 0; i < number.length(); i++) {
            int k = CYR.indexOf(number.charAt(i));
            if (k >= 0) {
                return number.substring(0, i) + LAT.charAt(k) + number.substring(i + 1);
            }
        }
        return number;
    }
}