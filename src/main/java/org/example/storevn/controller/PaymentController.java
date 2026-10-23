package org.example.storevn.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.dto.request.CreatePaymentRequest;
import org.example.storevn.dto.response.ApiResponse;
import org.example.storevn.dto.response.CreatePaymentResponse;
import org.example.storevn.dto.response.VnpayIpnResponse;
import org.example.storevn.enums.PaymentEventSource;
import org.example.storevn.service.PaymentLogService;
import org.example.storevn.service.PaymentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments / VNPAY", description = "Tạo payment URL và các callback VNPAY")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentLogService paymentLogService;
    private final ObjectMapper objectMapper;

    /**
     * Create payment attempt and get redirect URL to VNPAY.
     */
    @PostMapping("/store/orders/{orderCode}/payments")
    @Operation(summary = "Tạo URL thanh toán", description = "Tạo attempt PENDING và trả paymentUrl để chuyển khách sang VNPAY. " +
            "Body bankCode không bắt buộc. Kịch bản lỗi: order không tồn tại (404), đã PAID (409), " +
            "đã hết hạn/đang chờ attempt khác/đối soát (422), cấu hình VNPAY thiếu (500).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả URL thanh toán",
            content = @Content(examples = @ExampleObject(value = "{\"status\":200,\"code\":\"SUCCESS\",\"message\":\"Success\",\"result\":{\"txnRef\":\"ORD...01\",\"paymentUrl\":\"https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...\"}}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn",
            content = @Content(examples = @ExampleObject(value = "{\"status\":404,\"code\":\"ORDER_NOT_FOUND\",\"message\":\"Order not found\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Đơn đã thanh toán",
            content = @Content(examples = @ExampleObject(value = "{\"status\":409,\"code\":\"ORDER_ALREADY_PAID\",\"message\":\"Order is already paid\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Đơn hết hạn hoặc chưa thể thanh toán",
            content = @Content(examples = @ExampleObject(value = "{\"status\":422,\"code\":\"ORDER_CANNOT_PAY\",\"message\":\"Order is not in a payable state\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Thiếu cấu hình merchant VNPAY",
            content = @Content(examples = @ExampleObject(value = "{\"status\":500,\"code\":\"VNPAY_CONFIG_MISSING\",\"message\":\"VNPAY configuration is incomplete\"}")))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, description = "Mã ngân hàng tùy chọn",
            content = @Content(examples = @ExampleObject(value = "{\"bankCode\":\"NCB\"}")))
    public ApiResponse<CreatePaymentResponse> createPayment(
            @Parameter(description = "Mã đơn hàng", example = "ORD...", required = true)
            @PathVariable String orderCode,
            @RequestBody(required = false) CreatePaymentRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        CreatePaymentResponse response = paymentService.createPayment(orderCode, request, clientIp);
        return ApiResponse.success(response);
    }

    /**
     * VNPAY IPN webhook endpoint (returns raw JSON RspCode/Message).
     */
    @GetMapping("/payments/vnpay/ipn")
    @Operation(summary = "VNPAY IPN callback", description = "Callback server-to-server. Dùng Swagger Try it out để mô phỏng callback có chữ ký đúng hoặc sai. " +
            "RspCode: 00 xác nhận nhận; 01 không tìm thấy đơn/TMN sai; 02 attempt đã xử lý; 04 lệch tiền; 97 chữ ký sai. " +
            "IPN là callback chính; QueryDR phía server có thể đối soát khi IPN chậm hoặc thất lạc. Return URL không đổi trạng thái. responseCode=00 và transactionStatus=00 xác nhận thành công.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Phản hồi giao thức VNPAY, không bọc ApiResponse",
            content = @Content(examples = @ExampleObject(value = "{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}")))
    @Parameters({
            @Parameter(name = "vnp_TmnCode", description = "Mã merchant", example = "TESTCODE", required = true),
            @Parameter(name = "vnp_TxnRef", description = "Mã giao dịch của attempt", example = "ORD...01", required = true),
            @Parameter(name = "vnp_Amount", description = "Số tiền theo đơn vị xu (VND x 100)", example = "8900000", required = true),
            @Parameter(name = "vnp_ResponseCode", description = "Mã phản hồi VNPAY", example = "00", required = true),
            @Parameter(name = "vnp_TransactionStatus", description = "Trạng thái giao dịch VNPAY", example = "00", required = true),
            @Parameter(name = "vnp_TransactionNo", description = "Mã giao dịch VNPAY", example = "14123456"),
            @Parameter(name = "vnp_BankCode", description = "Mã ngân hàng", example = "NCB"),
            @Parameter(name = "vnp_CardType", description = "Loại thẻ", example = "ATM"),
            @Parameter(name = "vnp_PayDate", description = "Thời gian thanh toán yyyyMMddHHmmss", example = "20261022103000"),
            @Parameter(name = "vnp_SecureHash", description = "HMAC-SHA512 chữ ký VNPAY", example = "<signed-hash>", required = true),
            @Parameter(name = "vnp_SecureHashType", description = "Trường legacy tùy chọn", example = "SHA512")
    })
    public VnpayIpnResponse handleIpn(@Parameter(hidden = true) @RequestParam Map<String, String> allParams) {
        String txnRef = allParams.get("vnp_TxnRef");
        log.info("Received VNPAY IPN: txnRef={} responseCode={} transactionStatus={}", txnRef,
                allParams.get("vnp_ResponseCode"), allParams.get("vnp_TransactionStatus"));

        VnpayIpnResponse response;
        try {
            response = paymentService.processIpn(allParams);
        } catch (RuntimeException exception) {
            log.error("VNPAY IPN processing failed: txnRef={} error={}", txnRef,
                    exception.getMessage(), exception);
            paymentLogService.logIndependent(PaymentEventSource.IPN, txnRef,
                    allParams.get("vnp_TransactionNo"), parseAmount(allParams.get("vnp_Amount")),
                    allParams.get("vnp_ResponseCode"), allParams.get("vnp_BankCode"), false,
                    "99", VnpayIpnResponse.unknownError().getMessage(), toJson(allParams));
            response = VnpayIpnResponse.unknownError();
        }

        log.info("Responding to VNPAY IPN: txnRef={} httpStatus=200 rspCode={} message={}", txnRef,
                response.getRspCode(), response.getMessage());
        return response;
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Long parseAmount(String amount) {
        try {
            return amount == null ? null : Long.parseLong(amount) / 100;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * VNPAY Return URL (browser redirect from VNPAY back to Storefront).
     */
    @GetMapping("/payments/vnpay/return")
    @Operation(summary = "VNPAY Return URL", description = "VNPAY redirect trình duyệt về endpoint này; backend chỉ chuyển tiếp orderCode sang storefront result page. " +
            "FE lấy trạng thái hiện tại từ API tra cứu đơn. Return chỉ điều hướng; kể cả responseCode=24 cũng không đổi trạng thái. Trạng thái được xác nhận từ IPN hoặc QueryDR phía server.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect về trang kết quả storefront")
    @Parameters({
            @Parameter(name = "vnp_TmnCode", description = "Mã merchant", example = "TESTCODE", required = true),
            @Parameter(name = "vnp_TxnRef", description = "Mã giao dịch của attempt", example = "ORD...01", required = true),
            @Parameter(name = "vnp_Amount", description = "Số tiền theo đơn vị xu (VND x 100)", example = "8900000", required = true),
            @Parameter(name = "vnp_ResponseCode", description = "00 thành công; 24 khách hủy; mã khác là lỗi thanh toán", example = "24", required = true),
            @Parameter(name = "vnp_TransactionStatus", description = "Trạng thái giao dịch", example = "02"),
            @Parameter(name = "vnp_TransactionNo", description = "Mã giao dịch VNPAY", example = "14123456"),
            @Parameter(name = "vnp_BankCode", description = "Mã ngân hàng", example = "NCB"),
            @Parameter(name = "vnp_CardType", description = "Loại thẻ", example = "ATM"),
            @Parameter(name = "vnp_PayDate", description = "Thời gian thanh toán yyyyMMddHHmmss", example = "20261022103000"),
            @Parameter(name = "vnp_SecureHash", description = "HMAC-SHA512 chữ ký VNPAY", example = "<signed-hash>", required = true),
            @Parameter(name = "vnp_SecureHashType", description = "Trường legacy tùy chọn", example = "SHA512")
    })
    public ResponseEntity<Void> handleReturn(@Parameter(hidden = true) @RequestParam Map<String, String> allParams) {
        String redirectUrl = paymentService.processReturn(allParams);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
