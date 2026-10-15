package org.example.storevn.catalog;

import org.example.storevn.dto.response.StorePlanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlanCatalogRepositoryTest {

    private PlanCatalogRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("plan_catalog_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
                .build();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS plan (
                        id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        code                VARCHAR(50)  NOT NULL UNIQUE,
                        name                VARCHAR(150) NOT NULL,
                        description         TEXT,
                        price               NUMERIC(12,0) NOT NULL,
                        duration_months     SMALLINT NOT NULL,
                        quota_type          VARCHAR(20) NOT NULL,
                        data_quota_mb       BIGINT NOT NULL,
                        cycle_days          SMALLINT,
                        voice_minutes       INTEGER NOT NULL DEFAULT 0,
                        cutoff_policy       VARCHAR(20) NOT NULL,
                        throttle_speed_kbps INTEGER,
                        is_active           BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at          TIMESTAMP WITH TIME ZONE DEFAULT now(),
                        updated_at          TIMESTAMP WITH TIME ZONE DEFAULT now()
                    );

                    DELETE FROM plan;

                    INSERT INTO plan (code, name, description, price, duration_months, quota_type, data_quota_mb, cycle_days, voice_minutes, cutoff_policy, throttle_speed_kbps, is_active)
                    VALUES
                        ('SKY1M', 'Gói 1 tháng', 'Gói cơ bản', 89000, 1, 'DAILY', 3072, NULL, 50, 'THROTTLE', 512, TRUE),
                        ('SKY6M', 'Gói 6 tháng', 'Gói tiết kiệm', 500000, 6, 'PER_CYCLE', 18432, 30, 300, 'DISCONNECT', NULL, TRUE),
                        ('SKY12M', 'Gói 12 tháng', 'Gói năm', 950000, 12, 'PER_CYCLE', 36864, 30, 600, 'DISCONNECT', NULL, TRUE),
                        ('INACTIVE_PLAN', 'Gói ngừng bán', 'Đã ngừng', 70000, 1, 'DAILY', 2048, 30, 0, 'DISCONNECT', NULL, FALSE);
                    """);
        }

        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        repository = new PlanCatalogRepository(jdbcClient);
    }

    @Test
    @DisplayName("findActivePlans returns only active plans, excluding inactive ones")
    void findActivePlans_noFilter_returnsActivePlansOnly() {
        List<StorePlanResponse> plans = repository.findActivePlans(null, "asc");

        assertEquals(3, plans.size());
        assertTrue(plans.stream().noneMatch(p -> "INACTIVE_PLAN".equals(p.getCode())));
    }

    @Test
    @DisplayName("findActivePlans with duration filter returns matching plans only")
    void findActivePlans_filterDuration_returnsMatchingDurationOnly() {
        List<StorePlanResponse> plans1m = repository.findActivePlans(1, "asc");
        assertEquals(1, plans1m.size());
        assertEquals("SKY1M", plans1m.get(0).getCode());

        List<StorePlanResponse> plans6m = repository.findActivePlans(6, "asc");
        assertEquals(1, plans6m.size());
        assertEquals("SKY6M", plans6m.get(0).getCode());
    }

    @Test
    @DisplayName("findActivePlans sorts price in ascending order by default")
    void findActivePlans_defaultSort_ordersByPriceAsc() {
        List<StorePlanResponse> plans = repository.findActivePlans(null, "asc");

        assertEquals(3, plans.size());
        assertEquals(89000L, plans.get(0).getPrice());
        assertEquals(500000L, plans.get(1).getPrice());
        assertEquals(950000L, plans.get(2).getPrice());
    }

    @Test
    @DisplayName("findActivePlans sorts price in descending order when requested")
    void findActivePlans_sortDesc_ordersByPriceDesc() {
        List<StorePlanResponse> plans = repository.findActivePlans(null, "desc");

        assertEquals(3, plans.size());
        assertEquals(950000L, plans.get(0).getPrice());
        assertEquals(500000L, plans.get(1).getPrice());
        assertEquals(89000L, plans.get(2).getPrice());
    }

    @Test
    @DisplayName("findActivePlans defaults cycle_days to 30 when NULL in database")
    void findActivePlans_nullCycleDays_defaultsTo30() {
        List<StorePlanResponse> plans = repository.findActivePlans(1, "asc");

        assertEquals(1, plans.size());
        assertEquals(30, plans.get(0).getCycleDays(), "cycle_days should default to 30 when null");
    }

    @Test
    @DisplayName("findActiveByCode returns plan when code exists and is active")
    void findActiveByCode_existingActivePlan_returnsPlan() {
        Optional<StorePlanResponse> plan = repository.findActiveByCode("SKY89M_FAKE");
        assertTrue(plan.isEmpty());

        Optional<StorePlanResponse> actual = repository.findActiveByCode("SKY6M");
        assertTrue(actual.isPresent());
        assertEquals("SKY6M", actual.get().getCode());
        assertEquals("Gói 6 tháng", actual.get().getName());
        assertEquals(500000L, actual.get().getPrice());
        assertEquals(6, actual.get().getDurationMonths());
    }

    @Test
    @DisplayName("findActiveByCode returns empty when plan is inactive")
    void findActiveByCode_inactivePlan_returnsEmpty() {
        Optional<StorePlanResponse> plan = repository.findActiveByCode("INACTIVE_PLAN");
        assertTrue(plan.isEmpty(), "Inactive plan should not be returned by catalog");
    }
}
