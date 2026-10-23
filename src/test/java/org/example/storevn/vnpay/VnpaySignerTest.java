package org.example.storevn.vnpay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpaySignerTest {

    private static final String SECRET = "TESTSECRETKEY0123456789";
    private static final String EXPECTED_HASH = "74e5925be3bea98003879e3e82dc0e6f7c6b105cf4a4b28d30cf7a95c4d2aede88e82193928a62b2afdf4960d27b99813bf83341646e07b36235f1490f92bbd6";
    private static final String EXPECTED_QUERY = "vnp_Amount=8900000&vnp_Command=pay&vnp_CreateDate=20261013153000&vnp_CurrCode=VND&vnp_ExpireDate=20261013154500&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+goi+cuoc+SKY4GB+don+SK20261013A1&vnp_OrderType=other&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A8081%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=DEMOV210&vnp_TxnRef=SK20261013A101&vnp_Version=2.1.0";

    @Test
    @DisplayName("buildHashData and sign matches exact test vector from documentation including empty param omission")
    void sign_matchesOfficialTestVector() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "DEMOV210");
        params.put("vnp_Amount", "8900000");
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_BankCode", ""); // Empty param should be excluded
        params.put("vnp_TxnRef", "SK20261013A101");
        params.put("vnp_OrderInfo", "Thanh toan goi cuoc SKY4GB don SK20261013A1");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", "http://localhost:8081/api/v1/payments/vnpay/return");
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", "20261013153000");
        params.put("vnp_ExpireDate", "20261013154500");

        String hashData = VnpaySigner.buildHashData(params);
        assertThat(hashData).isEqualTo(EXPECTED_QUERY);

        String signature = VnpaySigner.sign(params, SECRET);
        assertThat(signature).isEqualTo(EXPECTED_HASH);

        boolean verified = VnpaySigner.verify(params, SECRET, EXPECTED_HASH);
        assertThat(verified).isTrue();
    }

    @Test
    @DisplayName("verify fails when signature or secret is altered")
    void verify_invalidSignatureOrSecret_returnsFalse() {
        Map<String, String> params = Map.of(
                "vnp_Amount", "8900000",
                "vnp_TxnRef", "SK20261013A101"
        );

        assertThat(VnpaySigner.verify(params, SECRET, "tampered_signature")).isFalse();
        assertThat(VnpaySigner.verify(params, "WRONG_SECRET", EXPECTED_HASH)).isFalse();
        assertThat(VnpaySigner.verify(params, SECRET, null)).isFalse();
    }
}
