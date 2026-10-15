package org.example.storevn.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.dto.response.StorePlanResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Read-only repository querying the admin database catalog via JdbcClient.
 */
@Repository
@Slf4j
public class PlanCatalogRepository {

    private final JdbcClient catalogJdbcClient;

    public PlanCatalogRepository(@Qualifier("catalogJdbcClient") JdbcClient catalogJdbcClient) {
        this.catalogJdbcClient = catalogJdbcClient;
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
     * Lists active plans with optional duration filter and price sorting.
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

        return catalogJdbcClient.sql(sql)
                .param("durationMonths", durationMonths)
                .query(PLAN_ROW_MAPPER)
                .list();
    }

    /**
     * Finds an active plan by its unique code.
     */
    public Optional<StorePlanResponse> findActiveByCode(String code) {
        String sql = """
                SELECT id, code, name, description, price, duration_months, quota_type,
                       data_quota_mb, cycle_days, voice_minutes, cutoff_policy, throttle_speed_kbps
                FROM plan
                WHERE is_active = true AND code = :code
                """;

        return catalogJdbcClient.sql(sql)
                .param("code", code)
                .query(PLAN_ROW_MAPPER)
                .optional();
    }
}
