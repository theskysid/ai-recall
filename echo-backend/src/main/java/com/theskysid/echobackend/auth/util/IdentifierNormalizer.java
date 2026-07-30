package com.theskysid.echobackend.auth.util;

import java.util.Locale;

public final class IdentifierNormalizer {

    private IdentifierNormalizer() {
    }

    public static boolean isEmail(String identifier) {
        return normalizeIdentifier(identifier).contains("@");
    }

    public static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim();
    }

    public static String normalizeUsername(String username) {
        return normalizeIdentifier(username);
    }

    public static String normalizeEmail(String email) {
        return normalizeIdentifier(email).toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String phone) {
        String normalized = normalizeIdentifier(phone);
        if (normalized.isEmpty()) {
            return normalized;
        }

        boolean hasLeadingPlus = normalized.startsWith("+");
        String digitsOnly = normalized.replaceAll("\\D", "");
        return hasLeadingPlus ? "+" + digitsOnly : digitsOnly;
    }
}
