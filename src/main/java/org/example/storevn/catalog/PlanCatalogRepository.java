package org.example.storevn.catalog;

import lombok.extern.slf4j.Slf4j;
import org.example.storevn.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only repository querying the admin database catalog via JdbcClient.
 * Enriches plans with optional bonus, app quota, and voice allowances based on CatalogAvailability.
 */
@Repository
@Slf4j
public class PlanCatalogRepository {

    private final JdbcClient catalogJdbcClient;
    private final CatalogAvailability availability;

    @Autowired
    public PlanCatalogRepository(
            @Qualifier("catalogJdbcClient") JdbcClient catalogJdbcClient,
            CatalogAvailability availability) {
        this.catalogJdbcClient = catalogJdbcClient;
        this.availability = availability;
    }

    public PlanCatalogRepository(JdbcClient catalogJdbcClient) {
        this(catalogJdbcClient, new CatalogAvailability(false, false, false));
    }

    private static final RowMapper<StorePlanResponse> PLAN_ROW_MAPPER = (rs, rowNum) -> {
        Integer cycleDays = rs.getObject("cycle_days") != null ? rs.getInt("cycle_days") : 30;
        Integer voiceMinutes = rs.getObject("voice_minutes") != null ? rs.getInt("voice_minutes") : 0;
        Integer throttleSpeed = rs.getObject("throttle_speed_kbps") != null ? rs.getInt("throttle_speed_kbps") : null;
        BigDecimal price = rs.getBigDecimal("price");

        return StorePlanResponse.builder()
                .id(rs.getLong("id"))
                .code(rs.getString("code"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .price(price != null ? price.longValue() : 0L)
                .durationMonths(rs.getInt("duration_months"))
                .quotaType(rs.getString("quota_type"))
                .dataQuotaMb(rs.getLong("data_quota_mb"))
                .cycleDays(cycleDays)
                .voiceMinutes(voiceMinutes)
                .cutoffPolicy(rs.getString("cutoff_policy"))
                .throttleSpeedKbps(throttleSpeed)
                .build();
    };

    /**
     * Lists active plans with optional duration filter and price sorting, enriched with benefits.
     */
    public List<StorePlanResponse> findActivePlans(Integer durationMonths, String sortOrder) {
        String direction = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        String sql = """
                SELECT id, code, name, description, price, duration_months, quota_type,
                       data_quota_mb, cycle_days, voice_minutes, cutoff_policy, throttle_speed_kbps
                FROM plan
                WHERE is_active = true
                  AND (:durationMonths IS NULL OR duration_months = :durationMonths)
                ORDER BY price %s, id ASC
                """.formatted(direction);

        List<StorePlanResponse> plans = catalogJdbcClient.sql(sql)
                .param("durationMonths", durationMonths)
                .query(PLAN_ROW_MAPPER)
                .list();

        return enrich(plans);
    }

    /**
     * Finds an active plan by its unique code, enriched with benefits.
     */
    public Optional<StorePlanResponse> findActiveByCode(String code) {
        String sql = """
                SELECT id, code, name, description, price, duration_months, quota_type,
                       data_quota_mb, cycle_days, voice_minutes, cutoff_policy, throttle_speed_kbps
                FROM plan
                WHERE is_active = true AND code = :code
                """;

        Optional<StorePlanResponse> planOpt = catalogJdbcClient.sql(sql)
                .param("code", code)
                .query(PLAN_ROW_MAPPER)
                .optional();

        planOpt.ifPresent(plan -> enrich(List.of(plan)));
        return planOpt;
    }

    /**
     * Enriches plans with optional bonuses, app quotas, and voice allowances.
     */
    private List<StorePlanResponse> enrich(List<StorePlanResponse> plans) {
        if (plans == null || plans.isEmpty()) {
            return plans;
        }

        List<Long> planIds = plans.stream().map(StorePlanResponse::getId).toList();

        Map<Long, List<PlanBonusDto>> bonusMap = (availability != null && availability.isBonusAvailable())
                ? loadBonuses(planIds) : Map.of();

        Map<Long, List<PlanAppQuotaDto>> appQuotaMap = (availability != null && availability.isAppsAvailable())
                ? loadAppQuotas(planIds) : Map.of();

        Map<Long, List<PlanVoiceAllowanceDto>> voiceMap = (availability != null && availability.isVoiceAvailable())
                ? loadVoiceAllowances(planIds) : Map.of();

        for (StorePlanResponse plan : plans) {
            List<PlanBonusDto> bonuses = bonusMap.getOrDefault(plan.getId(), List.of());
            List<PlanAppQuotaDto> appQuotas = appQuotaMap.getOrDefault(plan.getId(), List.of());
            List<PlanVoiceAllowanceDto> voiceAllowances = voiceMap.getOrDefault(plan.getId(), List.of());

            plan.setFirstCycleBonuses(bonuses);
            plan.setAppQuotas(appQuotas);
            plan.setVoiceAllowances(voiceAllowances);

            plan.setBenefits(PlanBenefitsDto.builder()
                    .bonuses(bonuses)
                    .appQuotas(appQuotas)
                    .voiceAllowances(voiceAllowances)
                    .build());
        }

        return plans;
    }

    private Map<Long, List<PlanBonusDto>> loadBonuses(List<Long> planIds) {
        String sql = """
                SELECT plan_id, bonus_type, amount
                FROM plan_first_cycle_bonus
                WHERE plan_id IN (:planIds)
                ORDER BY id ASC
                """;

        return catalogJdbcClient.sql(sql)
                .param("planIds", planIds)
                .query((rs, rowNum) -> new AbstractMap.SimpleEntry<>(
                        rs.getLong("plan_id"),
                        PlanBonusDto.builder()
                                .bonusType(rs.getString("bonus_type"))
                                .amount(rs.getLong("amount"))
                                .build()
                ))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private Map<Long, List<PlanAppQuotaDto>> loadAppQuotas(List<Long> planIds) {
        String sql = """
                SELECT paq.plan_id, paq.app_id, a.code AS app_code, a.name AS app_name, paq.quota_type, paq.quota_mb
                FROM plan_app_quota paq
                JOIN app a ON a.id = paq.app_id
                WHERE paq.plan_id IN (:planIds)
                ORDER BY paq.id ASC
                """;

        return catalogJdbcClient.sql(sql)
                .param("planIds", planIds)
                .query((rs, rowNum) -> new AbstractMap.SimpleEntry<>(
                        rs.getLong("plan_id"),
                        PlanAppQuotaDto.builder()
                                .appId(rs.getLong("app_id"))
                                .appCode(rs.getString("app_code"))
                                .appName(rs.getString("app_name"))
                                .quotaType(rs.getString("quota_type"))
                                .quotaMb(rs.getLong("quota_mb"))
                                .build()
                ))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private Map<Long, List<PlanVoiceAllowanceDto>> loadVoiceAllowances(List<Long> planIds) {
        String sql = """
                SELECT pva.plan_id, pva.network_id, n.code AS network_code, n.name AS network_name, pva.minutes
                FROM plan_voice_allowance pva
                JOIN network n ON n.id = pva.network_id
                WHERE pva.plan_id IN (:planIds)
                ORDER BY pva.id ASC
                """;

        return catalogJdbcClient.sql(sql)
                .param("planIds", planIds)
                .query((rs, rowNum) -> new AbstractMap.SimpleEntry<>(
                        rs.getLong("plan_id"),
                        PlanVoiceAllowanceDto.builder()
                                .networkId(rs.getLong("network_id"))
                                .networkCode(rs.getString("network_code"))
                                .networkName(rs.getString("network_name"))
                                .minutes(rs.getInt("minutes"))
                                .build()
                ))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }
}

