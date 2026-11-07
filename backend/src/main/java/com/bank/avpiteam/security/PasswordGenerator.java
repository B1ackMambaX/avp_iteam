package com.bank.avpiteam.security;

import java.security.SecureRandom;
import java.util.Base64;

final class PasswordGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {}

    static String generate() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}