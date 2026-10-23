package org.example.storevn.vnpay;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class OrderInfoSanitizer {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9 ]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final int MAX_LENGTH = 255;

    private OrderInfoSanitizer() {}

    /**
     * Sanitizes order description for VNPAY:
     * - Removes Vietnamese accents (e.g. đ/Đ -> d/D, diacritical marks)
     * - Removes special characters, retaining only alphanumeric and single spaces
     * - Limits to 255 characters
     */
    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "Thanh toan don hang";
        }

        String normalized = text.replace("đ", "d").replace("Đ", "D");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");

        String clean = SPECIAL_CHARS_PATTERN.matcher(normalized).replaceAll(" ");
        clean = MULTI_SPACE_PATTERN.matcher(clean).replaceAll(" ").trim();

        if (clean.isBlank()) {
            return "Thanh toan don hang";
        }

        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH).trim();
        }

        return clean;
    }
}
