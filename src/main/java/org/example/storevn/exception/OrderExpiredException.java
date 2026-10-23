package org.example.storevn.exception;

/** Cho phép lưu trạng thái đơn hết hạn trước khi trả lỗi cho client. */
public class OrderExpiredException extends AppException {
    public OrderExpiredException() {
        super(ErrorCode.ORDER_EXPIRED);
    }
}
