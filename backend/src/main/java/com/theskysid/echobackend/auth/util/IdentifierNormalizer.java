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

    /** Usernames address people in chat and URLs, so no internal whitespace. */
    public static boolean hasWhitespace(String value) {
        return value != null && value.chars().anyMatch(Character::isWhitespace);
    }

    /** Strips whitespace instead of rejecting — for names we derive, not names a user typed. */
    public static String stripWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    public static String normalizeEmail(String email) {
        return normalizeIdentifier(email).toLowerCase(Locale.ROOT);
    }
}
