package org.example.storevn.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateOrderRequest {

    @Schema(description = "Mã gói cước đang mở bán", example = "SKY89", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "INVALID_KEY")
    String planCode;

    @Schema(description = "Số thuê bao cần đăng ký", example = "0987654321", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "MSISDN_INVALID")
    @Pattern(regexp = "^[0-9]{9,15}$", message = "MSISDN_INVALID")
    String msisdn;

    @Schema(description = "Tên khách hàng", example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "CUSTOMER_NAME_INVALID")
    @Size(max = 150, message = "CUSTOMER_NAME_INVALID")
    String customerName;

    @Schema(description = "Số điện thoại dùng để tra cứu đơn", example = "0912345678", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "CUSTOMER_PHONE_INVALID")
    @Pattern(regexp = "^[0-9]{9,15}$", message = "CUSTOMER_PHONE_INVALID")
    String customerPhone;

    @Schema(description = "Email nhận thông tin đơn hàng", example = "a@example.com")
    @Size(max = 150, message = "INVALID_KEY")
    String customerEmail;
}
