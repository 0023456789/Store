package org.example.storevn.vnpay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.storevn.entity.PaymentAttempt;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class VnpayQueryClient {

    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public record QueryResult(String responseJson, JsonNode response, boolean signatureValid) {}

    private final VnpayProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient restClient;

    public VnpayQueryClient(VnpayProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public String buildRequest(PaymentAttempt attempt) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = format(clock.instant());
        String orderInfo = "Query " + attempt.getTxnRef();
        // QueryDR ký chuỗi theo đúng thứ tự trường quy định; TransactionDate là ngày tạo giao dịch ban đầu.
        String hashData = String.join("|", requestId, properties.getVersion(), "querydr",
                properties.getTmnCode(), attempt.getTxnRef(), attempt.getVnpCreateDate(), createDate,
                properties.getServerIp(), orderInfo);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", properties.getVersion());
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", properties.getTmnCode());
        body.put("vnp_TxnRef", attempt.getTxnRef());
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", attempt.getVnpCreateDate());
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", properties.getServerIp());
        body.put("vnp_SecureHash", VnpaySigner.hmacSHA512(properties.getHashSecret(), hashData));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize VNPAY QueryDR request", e);
        }
    }

    public QueryResult query(String requestJson) {
        String responseJson = restClient.post().uri(properties.getApiUrl())
                .contentType(MediaType.APPLICATION_JSON).body(requestJson).retrieve().body(String.class);
        try {
            JsonNode response = objectMapper.readTree(responseJson);
            // Chuỗi kiểm chữ ký phản hồi có thứ tự cố định theo tài liệu QueryDR của VNPAY.
            String signatureData = String.join("|", text(response, "vnp_ResponseId"),
                    text(response, "vnp_Command"), text(response, "vnp_ResponseCode"), text(response, "vnp_Message"),
                    text(response, "vnp_TmnCode"), text(response, "vnp_TxnRef"), text(response, "vnp_Amount"),
                    text(response, "vnp_BankCode"), text(response, "vnp_PayDate"), text(response, "vnp_TransactionNo"),
                    text(response, "vnp_TransactionType"), text(response, "vnp_TransactionStatus"),
                    text(response, "vnp_OrderInfo"), text(response, "vnp_PromotionCode"), text(response, "vnp_PromotionAmount"));
            String expected = VnpaySigner.hmacSHA512(properties.getHashSecret(), signatureData);
            String received = text(response, "vnp_SecureHash").toLowerCase(Locale.ROOT);
            // So sánh constant-time để tránh tiết lộ thông tin chữ ký qua thời gian xử lý.
            boolean valid = MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    received.getBytes(StandardCharsets.US_ASCII));
            return new QueryResult(responseJson, response, valid);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("VNPAY QueryDR returned invalid JSON", e);
        }
    }

    private String format(java.time.Instant instant) {
        return VNP_TIME.format(instant.atZone(VN_ZONE));
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }
}
