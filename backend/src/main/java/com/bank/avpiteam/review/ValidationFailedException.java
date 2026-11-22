package com.bank.avpiteam.review;

import java.util.Map;

/**
 * Некорректные параметры запроса. errors: поле -> сообщение,
 * чтобы Vaadin мог показать ошибку рядом с полем ввода.
 */
public class ValidationFailedException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationFailedException(Map<String, String> errors) {
        super("Validation failed: " + errors);
        this.errors = Map.copyOf(errors);
    }

    public static ValidationFailedException of(String field, String message) {
        return new ValidationFailedException(Map.of(field, message));
    }

    public Map<String, String> errors() {
        return errors;
    }
}
