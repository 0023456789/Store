package org.example.storevn.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.storevn.dto.request.CreateOrderRequest;
import org.example.storevn.dto.response.StoreOrderResponse;
import org.example.storevn.enums.OrderStatus;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.example.storevn.exception.GlobalExceptionHandler;
import org.example.storevn.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StoreOrderController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class StoreOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    @DisplayName("POST /store/orders with valid body returns 201 Created and order details")
    void createOrder_validRequest_returns201WithOrder() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .planCode("SKY89")
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .customerEmail("a@example.com")
                .build();

        StoreOrderResponse response = StoreOrderResponse.builder()
                .orderCode("ORDABC123456")
                .planCode("SKY89")
                .planName("Sky89")
                .durationMonths(1)
                .cycleDays(30)
                .planPrice(89000L)
                .simFee(0L)
                .discount(0L)
                .amount(89000L)
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .customerEmail("a@example.com")
                .status(OrderStatus.PENDING_PAYMENT)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/store/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.result.orderCode").value("ORDABC123456"))
                .andExpect(jsonPath("$.result.planCode").value("SKY89"))
                .andExpect(jsonPath("$.result.amount").value(89000))
                .andExpect(jsonPath("$.result.status").value("PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("POST /store/orders with invalid msisdn returns 400 Bad Request")
    void createOrder_invalidMsisdn_returns400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .planCode("SKY89")
                .msisdn("invalid-phone")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .build();

        mockMvc.perform(post("/store/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MSISDN_INVALID"));
    }

    @Test
    @DisplayName("POST /store/orders with missing customer name returns 400 Bad Request")
    void createOrder_blankCustomerName_returns400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .planCode("SKY89")
                .msisdn("0987654321")
                .customerName("")
                .customerPhone("0912345678")
                .build();

        mockMvc.perform(post("/store/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("CUSTOMER_NAME_INVALID"));
    }

    @Test
    @DisplayName("GET /store/orders/{orderCode}?phone=... returns 200 with order details")
    void getOrderByCode_matchingPhone_returns200() throws Exception {
        StoreOrderResponse response = StoreOrderResponse.builder()
                .orderCode("ORDABC123456")
                .planCode("SKY89")
                .planName("Sky89")
                .amount(89000L)
                .msisdn("0987654321")
                .customerName("Nguyen Van A")
                .customerPhone("0912345678")
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        when(orderService.getOrderByCode("ORDABC123456", "0912345678")).thenReturn(response);

        mockMvc.perform(get("/store/orders/ORDABC123456")
                        .param("phone", "0912345678")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.orderCode").value("ORDABC123456"))
                .andExpect(jsonPath("$.result.customerPhone").value("0912345678"));
    }

    @Test
    @DisplayName("GET /store/orders/{orderCode} without phone returns 400 CUSTOMER_PHONE_INVALID")
    void getOrderByCode_missingPhone_returns400() throws Exception {
        mockMvc.perform(get("/store/orders/ORDABC123456")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("CUSTOMER_PHONE_INVALID"))
                .andExpect(jsonPath("$.message").value("Customer phone is invalid"));
    }

    @Test
    @DisplayName("GET /store/orders/{orderCode} with mismatched phone returns 403 Forbidden")
    void getOrderByCode_phoneMismatch_returns403() throws Exception {
        when(orderService.getOrderByCode("ORDABC123456", "0999999999"))
                .thenThrow(new AppException(ErrorCode.ORDER_PHONE_MISMATCH));

        mockMvc.perform(get("/store/orders/ORDABC123456")
                        .param("phone", "0999999999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ORDER_PHONE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("Phone number does not match this order"));
    }

    @Test
    @DisplayName("GET /store/orders/{orderCode} with non-existent order returns 404 Not Found")
    void getOrderByCode_notFound_returns404() throws Exception {
        when(orderService.getOrderByCode("NONEXISTENT", "0912345678"))
                .thenThrow(new AppException(ErrorCode.ORDER_NOT_FOUND));

        mockMvc.perform(get("/store/orders/NONEXISTENT")
                        .param("phone", "0912345678")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }
}
