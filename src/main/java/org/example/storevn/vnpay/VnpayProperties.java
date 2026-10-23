package org.example.storevn.vnpay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Data
public class VnpayProperties {
    private String tmnCode = "";
    private String hashSecret = "";
    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    /** IP public của máy chủ gửi QueryDR; không phải IP của trình duyệt khách hàng. */
    private String serverIp = "https://unbranded-genotypical-aurea.ngrok-free.dev";
    /** Bật/tắt tác vụ nền dùng QueryDR để tìm kết quả khi IPN không đến. */
    private boolean reconcileEnabled = true;
    /** Chu kỳ chạy tác vụ đối soát, tính bằng mili giây. */
    private long reconcileIntervalMs = 60000;
    /** Thời gian chờ IPN trước khi lượt PENDING đủ điều kiện QueryDR. */
    private int reconcileGraceMinutes = 2;
    private String version = "2.1.0";
    private String command = "pay";
    private String currCode = "VND";
    private String defaultOrderType = "other";
}
