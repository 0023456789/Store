package org.example.storevn.vnpay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderInfoSanitizerTest {

    @Test
    @DisplayName("sanitize removes Vietnamese diacritics, special symbols, and handles d/D")
    void sanitize_vietnameseText_returnsAsciiAlphanumeric() {
        String input = "Thanh toán gói cước ĐẮC BIỆT #1 @2026!";
        String output = OrderInfoSanitizer.sanitize(input);

        assertThat(output).isEqualTo("Thanh toan goi cuoc DAC BIET 1 2026");
    }

    @Test
    @DisplayName("sanitize handles null and empty text with safe fallback")
    void sanitize_nullOrEmpty_returnsDefaultSafeText() {
        assertThat(OrderInfoSanitizer.sanitize(null)).isEqualTo("Thanh toan don hang");
        assertThat(OrderInfoSanitizer.sanitize("   ")).isEqualTo("Thanh toan don hang");
        assertThat(OrderInfoSanitizer.sanitize("###@@@$$$")).isEqualTo("Thanh toan don hang");
    }

    @Test
    @DisplayName("sanitize limits output length to 255 chars")
    void sanitize_overlongText_truncatedTo255() {
        String longInput = "Don hang " + "A".repeat(300);
        String output = OrderInfoSanitizer.sanitize(longInput);

        assertThat(output.length()).isLessThanOrEqualTo(255);
    }
}
