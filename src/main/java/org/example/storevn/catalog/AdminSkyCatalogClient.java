package org.example.storevn.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.dto.response.PlanAppQuotaDto;
import org.example.storevn.dto.response.PlanBenefitsDto;
import org.example.storevn.dto.response.PlanBonusDto;
import org.example.storevn.dto.response.StorePlanResponse;
import org.example.storevn.exception.AppException;
import org.example.storevn.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Client dùng API công khai của AdminSky để đọc catalog, không truy cập database AdminSky trực tiếp. */
@Component
@Slf4j
public class AdminSkyCatalogClient {

    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;

    public AdminSkyCatalogClient(
            RestClient.Builder builder,
            @Value("${adminsky.api.base-url:http://localhost:8080/api/v1}") String baseUrl,
            @Value("${adminsky.api.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${adminsky.api.read-timeout-ms:5000}") int readTimeoutMs) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public List<StorePlanResponse> findActivePlans(Integer durationMonths, String sortOrder) {
        List<JsonNode> summaries = findPlanSummaries(true, null, durationMonths,
                "price," + ("desc".equalsIgnoreCase(sortOrder) ? "desc" : "asc"));
        return summaries.stream().map(this::loadPlanDetails).flatMap(Optional::stream).toList();
    }

    public Optional<StorePlanResponse> findActiveByCode(String code) {
        return findPlanByCode(code, true);
    }

    public boolean existsByCode(String code) {
        return findPlanSummaries(null, code, null, "code,asc").stream()
                .anyMatch(summary -> code.equalsIgnoreCase(summary.path("code").asText()));
    }

    private Optional<StorePlanResponse> findPlanByCode(String code, Boolean active) {
        return findPlanSummaries(active, code, null, "code,asc").stream()
                .filter(summary -> code.equalsIgnoreCase(summary.path("code").asText()))
                .findFirst()
                .flatMap(this::loadPlanDetails);
    }

    private List<JsonNode> findPlanSummaries(Boolean active, String keyword,
                                              Integer durationMonths, String sort) {
        List<JsonNode> plans = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            JsonNode response = requestPlans(active, keyword, durationMonths, sort, page);
            JsonNode result = response.path("result");
            JsonNode content = result.path("content");
            if (!content.isArray()) {
                throw catalogUnavailable("AdminSky returned a plan page without content");
            }
            content.forEach(plans::add);
            totalPages = result.path("totalPages").asInt(0);
            page++;
        } while (page < totalPages);
        return plans;
    }

    private JsonNode requestPlans(Boolean active, String keyword, Integer durationMonths,
                                  String sort, int page) {
        try {
            JsonNode response = restClient.get().uri(uriBuilder -> {
                var uri = uriBuilder.path("/plans")
                        .queryParam("page", page)
                        .queryParam("size", PAGE_SIZE)
                        .queryParam("sort", sort);
                if (active != null) uri.queryParam("isActive", active);
                if (keyword != null && !keyword.isBlank()) uri.queryParam("keyword", keyword);
                if (durationMonths != null) uri.queryParam("durationMonths", durationMonths);
                return uri.build();
            }).retrieve().body(JsonNode.class);
            if (response == null || response.path("result").isMissingNode()) {
                throw catalogUnavailable("AdminSky returned an empty plan page");
            }
            return response;
        } catch (RestClientException e) {
            log.debug("AdminSky plan request failed: {}", e.getClass().getSimpleName());
            throw new AppException(ErrorCode.CATALOG_UNAVAILABLE);
        }
    }

    private Optional<StorePlanResponse> loadPlanDetails(JsonNode summary) {
        long planId = summary.path("id").asLong();
        try {
            JsonNode response = restClient.get().uri("/plans/{planId}", planId)
                    .retrieve().body(JsonNode.class);
            JsonNode plan = response == null ? null : response.path("result");
            if (plan == null || plan.isMissingNode() || plan.isNull()) {
                throw catalogUnavailable("AdminSky returned a plan without details");
            }
            // Kế hoạch có thể bị khóa sau khi lấy trang summary; không đưa gói vừa ngừng bán ra storefront.
            if (!plan.path("isActive").asBoolean(false)) return Optional.empty();
            return Optional.of(mapPlan(plan));
        } catch (RestClientException e) {
            log.debug("AdminSky plan detail request failed for id {}: {}", planId, e.getClass().getSimpleName());
            throw new AppException(ErrorCode.CATALOG_UNAVAILABLE);
        }
    }

    private StorePlanResponse mapPlan(JsonNode plan) {
        List<PlanBonusDto> bonuses = new ArrayList<>();
        JsonNode bonusRows = plan.has("firstCycleBonuses")
                ? plan.path("firstCycleBonuses") : plan.path("bonuses");
        bonusRows.forEach(item -> bonuses.add(PlanBonusDto.builder()
                .bonusType(item.path("bonusType").asText())
                .amount(item.path("amount").asLong())
                .build()));

        List<PlanAppQuotaDto> appQuotas = new ArrayList<>();
        plan.path("appQuotas").forEach(item -> appQuotas.add(PlanAppQuotaDto.builder()
                .appId(item.path("appId").asLong())
                .appCode(item.path("appCode").asText())
                .appName(item.path("appName").asText())
                .quotaType(item.path("quotaType").asText())
                .quotaMb(item.path("quotaMb").asLong())
                .build()));

        return StorePlanResponse.builder()
                .id(plan.path("id").asLong())
                .code(plan.path("code").asText())
                .name(plan.path("name").asText())
                .description(textOrNull(plan, "description"))
                .price(plan.path("price").asLong())
                .durationMonths(plan.path("durationMonths").asInt())
                .quotaType(textOrNull(plan, "quotaType"))
                .dataQuotaMb(plan.path("dataQuotaMb").asLong())
                .cycleDays(plan.path("cycleDays").isNull() ? 30 : plan.path("cycleDays").asInt(30))
                .voiceMinutes(plan.path("voiceMinutes").asInt(0))
                .cutoffPolicy(textOrNull(plan, "cutoffPolicy"))
                .throttleSpeedKbps(plan.path("throttleSpeedKbps").isNull()
                        ? null : plan.path("throttleSpeedKbps").asInt())
                .firstCycleBonuses(bonuses)
                .appQuotas(appQuotas)
                .benefits(PlanBenefitsDto.builder().bonuses(bonuses).appQuotas(appQuotas).build())
                .build();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private AppException catalogUnavailable(String detail) {
        log.debug("Invalid catalog response from AdminSky: {}", detail);
        return new AppException(ErrorCode.CATALOG_UNAVAILABLE);
    }
}
