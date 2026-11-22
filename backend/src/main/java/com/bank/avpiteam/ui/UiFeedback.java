package com.bank.avpiteam.ui;

import com.bank.avpiteam.security.UserErrors;
import com.bank.avpiteam.settings.NlpSettingsErrors;
import com.bank.avpiteam.settings.NlpAdminUnavailable;
import com.bank.avpiteam.settings.BadTestInput;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import java.util.Map;
import java.util.stream.Collectors;

final class UiFeedback {
    private UiFeedback() {}

    static void success(String message) { show(message, NotificationVariant.LUMO_SUCCESS); }
    static void error(String message) { show(message, NotificationVariant.LUMO_ERROR); }

    static String message(RuntimeException error) {
        if (error instanceof UserErrors.Validation e) return validation(e.errors());
        if (error instanceof NlpSettingsErrors.Validation e) return validation(e.fields());
        if (error instanceof NlpSettingsErrors.Conflict) return "Настройки изменены другим пользователем. Данные обновлены.";
        if (error instanceof UserErrors.RuleViolation e) return ruleViolation(e.getMessage());
        if (error instanceof UserErrors.NotFound) return "Пользователь больше не найден. Обновите список.";
        if (error instanceof NlpSettingsErrors.NotFound) return "Версия настроек не найдена. Обновите историю.";
        if (error instanceof BadTestInput) return "Введите текст для проверки длиной не более 2000 символов.";
        if (error instanceof NlpAdminUnavailable) return "NLP-сервис недоступен. Попробуйте позже.";
        return error.getMessage() == null ? "Не удалось выполнить действие" : error.getMessage();
    }

    private static String validation(Map<String, String> errors) {
        return errors.entrySet().stream().map(e -> field(e.getKey()) + ": " + reason(e.getValue()))
                .collect(Collectors.joining("; "));
    }

    private static String field(String name) {
        return switch (name) {
            case "username" -> "Логин";
            case "role" -> "Роль";
            case "password" -> "Пароль";
            case "threshold" -> "Порог";
            case "triggerWords" -> "Слова-маркеры";
            case "negationWords" -> "Слова-отрицания";
            case "version" -> "Версия настроек";
            default -> "Форма";
        };
    }

    private static String reason(String value) {
        if (value.contains("3-64 chars")) return "нужно 3–64 символа: латиница, цифры, точка, дефис или подчёркивание";
        if (value.contains("must be one of")) return "выберите доступную роль";
        if (value.contains("at least")) return "нужно не менее 10 символов и не более 72 байт";
        if (value.contains("between 0 and 1")) return "укажите число от 0 до 1";
        if (value.contains("must not be empty")) return "добавьте хотя бы одно слово";
        if (value.contains("invalid word")) return "каждое слово должно содержать 2–50 букв или цифр; * разрешена только в конце";
        if (value.contains("at most 100")) return "можно указать не более 100 слов";
        if (value.contains("required")) return "обязательное поле";
        return "проверьте значение";
    }

    private static String ruleViolation(String value) {
        if (value.contains("Username already exists")) return "Такой логин уже занят.";
        if (value.contains("your own role")) return "Нельзя менять свою роль или блокировать себя.";
        if (value.contains("last active administrator")) return "Нельзя отключить последнего активного администратора.";
        return "Действие не выполнено из-за ограничения доступа.";
    }

    private static void show(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
}
