package org.example.storevn.controller;

import lombok.RequiredArgsConstructor;
import org.example.storevn.dto.response.ApiResponse;
import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.service.PlanCatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/store/plans")
@RequiredArgsConstructor
public class StorePlanController {

    private final PlanCatalogService planCatalogService;

    @GetMapping
    public ApiResponse<List<StorePlanResponse>> getPlans(
            @RequestParam(required = false) Integer durationMonths,
            @RequestParam(defaultValue = "asc") String sort) {
        return ApiResponse.success(planCatalogService.getActivePlans(durationMonths, sort));
    }

    @GetMapping("/{code}")
    public ApiResponse<StorePlanResponse> getPlanByCode(@PathVariable String code) {
        return ApiResponse.success(planCatalogService.getActivePlanByCode(code));
    }
}
