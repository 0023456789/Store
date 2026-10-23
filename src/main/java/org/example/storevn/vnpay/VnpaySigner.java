package org.example.storevn.vnpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class VnpaySigner {

    private static final String HMAC_SHA512 = "HmacSHA512";

    private VnpaySigner() {}

    /**
     * Dựng chuỗi ký theo quy tắc VNPAY: sắp xếp tên trường, bỏ giá trị rỗng và hai trường chữ ký;
     * mã hóa giá trị kiểu URL (dấu cách thành '+'). Sai thứ tự hoặc cách mã hóa sẽ làm chữ ký lệch.
     */
    public static String buildHashData(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String fieldName : fieldNames) {
            if ("vnp_SecureHash".equalsIgnoreCase(fieldName)
                    || "vnp_SecureHashType".equalsIgnoreCase(fieldName)) {
                continue;
            }
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isBlank()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(fieldName).append("=");
                try {
                    sb.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    sb.append(fieldValue);
                }
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * Tạo HMAC-SHA512 và trả về chuỗi hex chữ thường.
     */
    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key and data must not be null");
            }
            Mac hmac = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA512", ex);
        }
    }

    /**
     * Ký các tham số bằng khóa bí mật của merchant.
     */
    public static String sign(Map<String, String> params, String secret) {
        String hashData = buildHashData(params);
        return hmacSHA512(secret, hashData);
    }

    /**
     * Kiểm tra chữ ký bằng phép so sánh constant-time.
     */
    public static boolean verify(Map<String, String> params, String secret, String expectedSecureHash) {
        if (expectedSecureHash == null || expectedSecureHash.isBlank() || secret == null) {
            return false;
        }
        String calculated = sign(params, secret);
        return MessageDigest.isEqual(
                calculated.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                expectedSecureHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }
}
