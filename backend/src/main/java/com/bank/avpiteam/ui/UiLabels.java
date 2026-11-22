package com.bank.avpiteam.ui;

import com.bank.avpiteam.security.Role;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class UiLabels {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private UiLabels() {}

    static String role(Role role) {
        return switch (role) {
            case ADMIN -> "Администратор";
            case EDITOR -> "Редактор";
            case READER -> "Наблюдатель";
        };
    }

    static String role(String role) { return role(Role.valueOf(role)); }

    static String decision(String decision) {
        return switch (decision) {
            case "SEMANTIC" -> "Семантический анализ";
            case "GATED" -> "Нет слов-маркеров";
            case "NEGATED" -> "Обнаружено отрицание";
            default -> decision;
        };
    }

    static String actor(String name) { return "migration".equals(name) ? "Система" : name; }

    static String dateTime(OffsetDateTime date) {
        return date == null ? "—" : date.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME);
    }

    static String historyComment(String comment) {
        if (comment == null || comment.isBlank()) return "—";
        if ("defaults".equals(comment)) return "Исходные настройки";
        if ("reset to defaults".equals(comment)) return "Сброс к исходным настройкам";
        if (comment.startsWith("rollback to version "))
            return "Восстановление версии " + comment.substring("rollback to version ".length());
        return comment;
    }
}
