package org.example.storevn.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.dto.response.ApiResponse;
import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.service.PlanCatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@RestController
@RequestMapping("/store/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Danh mục gói cước lấy từ API AdminSky")
@Slf4j
public class StorePlanController {

    private final PlanCatalogService planCatalogService;

    @GetMapping
    @Operation(summary = "Danh sách gói cước đang bán", description = "Có thể lọc theo thời hạn và sắp xếp giá tăng/giảm. " +
            "Kịch bản: không lọc; durationMonths=1; sort=desc; AdminSky không khả dụng (503).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách gói cước", content = @Content(examples =
            @ExampleObject(value = "{\"status\":200,\"code\":\"SUCCESS\",\"message\":\"Success\",\"result\":[{\"code\":\"SKY89\",\"name\":\"Sky89\",\"price\":89000,\"durationMonths\":1}]}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Không truy cập được API catalog AdminSky",
            content = @Content(examples = @ExampleObject(value = "{\"status\":503,\"code\":\"CATALOG_UNAVAILABLE\",\"message\":\"Plan catalog is temporarily unavailable\"}")))
    public ApiResponse<List<StorePlanResponse>> getPlans(
            @Parameter(description = "Lọc gói theo số tháng", example = "1")
            @RequestParam(required = false) Integer durationMonths,
            @Parameter(description = "Thứ tự giá: asc hoặc desc", example = "asc", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(defaultValue = "asc") String sort) {
        List<StorePlanResponse> plans = planCatalogService.getActivePlans(durationMonths, sort);
        if (plans.isEmpty()) {
            log.warn("No active plans available: durationMonths={} sort={}", durationMonths, sort);
            return ApiResponse.<List<StorePlanResponse>>builder()
                    .status(200)
                    .code("SUCCESS")
                    .message("Hiện tại chưa có gói cước khả dụng")
                    .result(plans)
                    .build();
        }
        return ApiResponse.success(plans);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Chi tiết gói cước", description = "Trả chi tiết gói đang bán, gồm ưu đãi chu kỳ đầu và quota ứng dụng. Thử mã không tồn tại để nhận 404.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thông tin gói cước")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy gói đang bán",
            content = @Content(examples = @ExampleObject(value = "{\"status\":404,\"code\":\"PLAN_NOT_FOUND\",\"message\":\"Plan not found\"}")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Catalog AdminSky tạm thời không khả dụng",
            content = @Content(examples = @ExampleObject(value = "{\"status\":503,\"code\":\"CATALOG_UNAVAILABLE\",\"message\":\"Plan catalog is temporarily unavailable\"}")))
    public ApiResponse<StorePlanResponse> getPlanByCode(
            @Parameter(description = "Mã gói cước", example = "SKY89", required = true) @PathVariable String code) {
        return ApiResponse.success(planCatalogService.getActivePlanByCode(code));
    }
}
