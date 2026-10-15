package org.example.storevn.controller;

import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.example.storevn.exception.GlobalExceptionHandler;
import org.example.storevn.service.PlanCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorePlanController.class)
@Import(GlobalExceptionHandler.class)
class StorePlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlanCatalogService planCatalogService;

    @Test
    @DisplayName("GET /store/plans returns 200 with list of plans")
    void getPlans_validRequest_returns200WithList() throws Exception {
        StorePlanResponse plan = StorePlanResponse.builder()
                .id(1L)
                .code("SKY89")
                .name("Sky89")
                .price(89000L)
                .durationMonths(1)
                .quotaType("DAILY")
                .dataQuotaMb(3072L)
                .cycleDays(30)
                .build();

        when(planCatalogService.getActivePlans(1, "asc")).thenReturn(List.of(plan));

        mockMvc.perform(get("/store/plans")
                        .param("durationMonths", "1")
                        .param("sort", "asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.result[0].code").value("SKY89"))
                .andExpect(jsonPath("$.result[0].price").value(89000));
    }

    @Test
    @DisplayName("GET /store/plans/{code} returns 200 with plan details and enriched benefits")
    void getPlanByCode_existingCode_returns200WithPlan() throws Exception {
        StorePlanResponse plan = StorePlanResponse.builder()
                .id(1L)
                .code("SKY89")
                .name("Sky89")
                .price(89000L)
                .firstCycleBonuses(List.of(
                        org.example.storevn.dto.response.PlanBonusDto.builder().bonusType("DATA_MB").amount(5120L).build()))
                .appQuotas(List.of(
                        org.example.storevn.dto.response.PlanAppQuotaDto.builder().appCode("TIKTOK").appName("TikTok").quotaMb(1024L).build()))
                .benefits(org.example.storevn.dto.response.PlanBenefitsDto.builder()
                        .bonuses(List.of(org.example.storevn.dto.response.PlanBonusDto.builder().bonusType("DATA_MB").amount(5120L).build()))
                        .build())
                .build();

        when(planCatalogService.getActivePlanByCode("SKY89")).thenReturn(plan);

        mockMvc.perform(get("/store/plans/SKY89")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.code").value("SKY89"))
                .andExpect(jsonPath("$.result.name").value("Sky89"))
                .andExpect(jsonPath("$.result.firstCycleBonuses[0].bonusType").value("DATA_MB"))
                .andExpect(jsonPath("$.result.firstCycleBonuses[0].amount").value(5120))
                .andExpect(jsonPath("$.result.appQuotas[0].appCode").value("TIKTOK"))
                .andExpect(jsonPath("$.result.benefits.bonuses[0].bonusType").value("DATA_MB"));
    }

    @Test
    @DisplayName("GET /store/plans/{code} returns 404 when plan not found")
    void getPlanByCode_notFound_returns404WithErrorCode() throws Exception {
        when(planCatalogService.getActivePlanByCode("NONEXISTENT"))
                .thenThrow(new AppException(ErrorCode.PLAN_NOT_FOUND));

        mockMvc.perform(get("/store/plans/NONEXISTENT")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.message").value("Plan not found"));
    }
}
