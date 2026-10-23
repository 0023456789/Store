package org.example.storevn.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePaymentRequest {
    @Schema(description = "Mã ngân hàng VNPAY (không bắt buộc)", example = "NCB")
    String bankCode;
}
