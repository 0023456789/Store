package org.example.storevn.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    @Schema(description = "HTTP status của response; khớp với status ở HTTP response", example = "404")
    int status;

    @Schema(description = "Mã nghiệp vụ dạng chữ; SUCCESS khi thành công", example = "ORDER_NOT_FOUND")
    String code;

    String message;
    T result;

    public static <T> ApiResponse<T> success(T result) {
        return success(result, 200);
    }

    public static <T> ApiResponse<T> success(T result, int status) {
        return ApiResponse.<T>builder().status(status).code("SUCCESS").message("Success").result(result).build();
    }
}
