package com.bank.avpiteam.security;

import java.util.Map;

public final class UserErrors {
    private UserErrors() {}

    public static class Validation extends RuntimeException {
        private final Map<String, String> errors;
        public Validation(Map<String, String> errors) {
            super("Validation failed");
            this.errors = errors;
        }
        public Map<String, String> errors() { return errors; }
    }

    public static class NotFound extends RuntimeException {
        public NotFound(long id) { super("User " + id + " not found"); }
    }

    /** Нарушение правила: дубликат логина, самоблокировка, последний админ. */
    public static class RuleViolation extends RuntimeException {
        public RuleViolation(String message) { super(message); }
    }
}