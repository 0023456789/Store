package org.example.storevn.controller;

import org.example.storevn.dto.response.CreatePaymentResponse;
import org.example.storevn.dto.response.VnpayIpnResponse;
import org.example.storevn.exception.GlobalExceptionHandler;
import org.example.storevn.service.PaymentLogService;
import org.example.storevn.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentLogService paymentLogService;

    @Test
    @DisplayName("POST /store/orders/{orderCode}/payments returns 200 with payment URL")
    void createPayment_validRequest_returns200() throws Exception {
        CreatePaymentResponse res = CreatePaymentResponse.builder()
                .txnRef("ORD12301")
                .paymentUrl("https://sandbox.vnpayment.vn/pay?vnp_TxnRef=ORD12301")
                .build();

        when(paymentService.createPayment(eq("ORD123"), any(), any())).thenReturn(res);

        mockMvc.perform(post("/store/orders/ORD123/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankCode\":\"NCB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.txnRef").value("ORD12301"))
                .andExpect(jsonPath("$.result.paymentUrl").value("https://sandbox.vnpayment.vn/pay?vnp_TxnRef=ORD12301"));
    }

    @Test
    @DisplayName("GET /payments/vnpay/ipn returns raw JSON RspCode and Message without ApiResponse wrapper")
    void handleIpn_validRequest_returnsRawJson() throws Exception {
        when(paymentService.processIpn(any())).thenReturn(VnpayIpnResponse.success());

        mockMvc.perform(get("/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "TXN123")
                        .param("vnp_SecureHash", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("GET /payments/vnpay/return redirects 302 to frontend result URL")
    void handleReturn_validRequest_redirectsToFrontend() throws Exception {
        when(paymentService.processReturn(any())).thenReturn("http://localhost:5173/result?orderCode=ORD123&status=PAID");

        mockMvc.perform(get("/payments/vnpay/return")
                        .param("vnp_TxnRef", "ORD12301"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/result?orderCode=ORD123&status=PAID"));
    }
}
