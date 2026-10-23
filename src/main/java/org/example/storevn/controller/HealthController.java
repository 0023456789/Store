package org.example.storevn.controller;

import org.example.storevn.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health", description = "Kiểm tra trạng thái ứng dụng")
public class HealthController {

    @GetMapping("/ping")
    @Operation(summary = "Kiểm tra API", description = "Trả pong nếu ứng dụng đang nhận request.")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }
}
