package org.example.storevn.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.catalog.PlanCatalogRepository;
import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanCatalogService {

    private final PlanCatalogRepository planCatalogRepository;

    public List<StorePlanResponse> getActivePlans(Integer durationMonths, String sort) {
        return planCatalogRepository.findActivePlans(durationMonths, sort);
    }

    public StorePlanResponse getActivePlanByCode(String code) {
        return planCatalogRepository.findActiveByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
    }
}
