package org.example.storevn.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION("Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY("Invalid request", HttpStatus.BAD_REQUEST),

    // ── Catalog ──
    PLAN_NOT_FOUND("Plan not found", HttpStatus.NOT_FOUND),
    PLAN_INACTIVE("Plan is not available for purchase", HttpStatus.UNPROCESSABLE_ENTITY),
    CATALOG_UNAVAILABLE("Plan catalog is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // ── Order ──
    ORDER_NOT_FOUND("Order not found", HttpStatus.NOT_FOUND),
    ORDER_EXPIRED("Order has expired", HttpStatus.UNPROCESSABLE_ENTITY),
    ORDER_ALREADY_PAID("Order is already paid", HttpStatus.CONFLICT),
    ORDER_PHONE_MISMATCH("Phone number does not match this order", HttpStatus.FORBIDDEN),
    ORDER_CANNOT_PAY("Order is not in a payable state", HttpStatus.UNPROCESSABLE_ENTITY),
    MSISDN_INVALID("Subscriber number is invalid", HttpStatus.BAD_REQUEST),
    CUSTOMER_NAME_INVALID("Customer name is required", HttpStatus.BAD_REQUEST),
    CUSTOMER_PHONE_INVALID("Customer phone is invalid", HttpStatus.BAD_REQUEST),

    // ── Payment / VNPAY ──
    PAYMENT_SIGNATURE_INVALID("Payment signature verification failed", HttpStatus.BAD_REQUEST),
    PAYMENT_AMOUNT_MISMATCH("Payment amount does not match", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_CONFIRMED("Payment has already been confirmed", HttpStatus.CONFLICT),
    PAYMENT_CREATE_FAILED("Failed to create payment", HttpStatus.INTERNAL_SERVER_ERROR),
    VNPAY_CONFIG_MISSING("VNPAY configuration is incomplete", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(String message, HttpStatus statusCode) {
        this.message = message;
        this.statusCode = statusCode;
    }

    public String getCode() { return name(); }
}
