package org.example.storevn.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FieldErrorResponse {
    String field;

    @Schema(description = "Mã lỗi nghiệp vụ dạng chữ", example = "MSISDN_INVALID")
    String code;
    String message;
}
