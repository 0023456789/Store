package org.example.storevn.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.storevn.dto.request.CreateOrderRequest;
import org.example.storevn.dto.response.ApiResponse;
import org.example.storevn.dto.response.StoreOrderResponse;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.example.storevn.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@RestController
@RequestMapping("/store/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Tạo đơn mua gói và tra cứu đơn bằng số điện thoại đã đăng ký")
public class StoreOrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo đơn hàng", description = "Đơn mới ở trạng thái PENDING_PAYMENT, TTL mặc định 15 phút. " +
            "Kịch bản lỗi: body/field sai (400), gói không tồn tại (404), gói ngừng bán (422), catalog lỗi (503).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo đơn thành công",
            content = @Content(examples = @ExampleObject(value = "{\"status\":201,\"code\":\"SUCCESS\",\"message\":\"Success\",\"result\":{\"orderCode\":\"ORD...\",\"planCode\":\"SKY89\",\"amount\":89000,\"status\":\"PENDING_PAYMENT\",\"expiresAt\":\"2026-10-22T10:15:00Z\"}}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Body hoặc trường đầu vào không hợp lệ",
            content = @Content(examples = @ExampleObject(value = "{\"status\":400,\"code\":\"MSISDN_INVALID\",\"message\":\"Subscriber number is invalid\",\"result\":[{\"field\":\"msisdn\",\"code\":\"MSISDN_INVALID\",\"message\":\"Subscriber number is invalid\"}]}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Gói cước không tồn tại",
            content = @Content(examples = @ExampleObject(value = "{\"status\":404,\"code\":\"PLAN_NOT_FOUND\",\"message\":\"Plan not found\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Gói cước không còn mở bán",
            content = @Content(examples = @ExampleObject(value = "{\"status\":422,\"code\":\"PLAN_INACTIVE\",\"message\":\"Plan is not available for purchase\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Catalog AdminSky tạm thời không khả dụng",
            content = @Content(examples = @ExampleObject(value = "{\"status\":503,\"code\":\"CATALOG_UNAVAILABLE\",\"message\":\"Plan catalog is temporarily unavailable\"}")))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "Thông tin khách hàng và gói muốn mua",
            content = @Content(examples = @ExampleObject(value = "{\"planCode\":\"SKY89\",\"msisdn\":\"0987654321\",\"customerName\":\"Nguyen Van A\",\"customerPhone\":\"0912345678\",\"customerEmail\":\"a@example.com\"}")))
    public ApiResponse<StoreOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        StoreOrderResponse response = orderService.createOrder(request);
        return ApiResponse.success(response, HttpStatus.CREATED.value());
    }

    @GetMapping("/{orderCode}")
    @Operation(summary = "Tra cứu đơn hàng", description = "phone phải khớp số điện thoại dùng lúc tạo đơn. " +
            "Kịch bản: đúng phone (200), thiếu phone (400), sai phone (403), mã đơn không tồn tại (404).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thông tin đơn hàng")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu hoặc sai số điện thoại",
            content = @Content(examples = @ExampleObject(value = "{\"status\":400,\"code\":\"CUSTOMER_PHONE_INVALID\",\"message\":\"Customer phone is invalid\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Số điện thoại không khớp đơn hàng",
            content = @Content(examples = @ExampleObject(value = "{\"status\":403,\"code\":\"ORDER_PHONE_MISMATCH\",\"message\":\"Phone number does not match this order\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng",
            content = @Content(examples = @ExampleObject(value = "{\"status\":404,\"code\":\"ORDER_NOT_FOUND\",\"message\":\"Order not found\"}")))
    public ApiResponse<StoreOrderResponse> getOrder(
            @Parameter(description = "Mã đơn trả về khi tạo đơn", example = "ORD...", required = true)
            @PathVariable String orderCode,
            @Parameter(description = "Số điện thoại khách khai báo khi tạo đơn", example = "0912345678", required = true)
            @RequestParam(required = false) String phone) {
        if (phone == null || phone.isBlank()) {
            throw new AppException(ErrorCode.CUSTOMER_PHONE_INVALID);
        }
        StoreOrderResponse response = orderService.getOrderByCode(orderCode, phone);
        return ApiResponse.success(response);
    }
}
