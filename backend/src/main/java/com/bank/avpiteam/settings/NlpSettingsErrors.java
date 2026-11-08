package com.bank.avpiteam.settings;

import java.util.Map;

public final class NlpSettingsErrors {
    private NlpSettingsErrors() {
    }

    public static class Validation extends RuntimeException {
        private final transient Map<String, String> fields;

        public Validation(Map<String, String> fields) {
            super("invalid nlp settings");
            this.fields = fields;
        }

        public Map<String, String> fields() {
            return fields;
        }
    }

    public static class Conflict extends RuntimeException {
        private final int currentVersion;

        public Conflict(int expected, int currentVersion) {
            super("expected version " + expected + ", current " + currentVersion);
            this.currentVersion = currentVersion;
        }

        public int currentVersion() {
            return currentVersion;
        }
    }

    public static class NotFound extends RuntimeException {
        public NotFound(int version) {
            super("settings version " + version + " not found");
        }
    }
}