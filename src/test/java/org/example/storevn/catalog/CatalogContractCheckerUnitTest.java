package org.example.storevn.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class CatalogContractCheckerUnitTest {

    private DataSource dataSource;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setup() {
        dataSource = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcClient = JdbcClient.create(dataSource);
    }

    @Test
    @DisplayName("Throws IllegalStateException when core plan table is missing")
    void catalogAvailability_missingCoreTable_throwsIllegalStateException() {
        CatalogContractChecker checker = new CatalogContractChecker(jdbcClient);

        IllegalStateException ex = assertThrows(IllegalStateException.class, checker::catalogAvailability);
        assertTrue(ex.getMessage().contains("Catalog contract violation"));
        assertTrue(ex.getMessage().contains("plan.id"));
    }

    @Test
    @DisplayName("Throws IllegalStateException when core column is missing from plan table")
    void catalogAvailability_missingCoreColumn_throwsIllegalStateException() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Missing price and quota_type
            stmt.execute("""
                    CREATE TABLE plan (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        code VARCHAR(50) NOT NULL,
                        name VARCHAR(150) NOT NULL,
                        description TEXT,
                        duration_months SMALLINT NOT NULL,
                        data_quota_mb BIGINT NOT NULL,
                        cutoff_policy VARCHAR(20) NOT NULL,
                        throttle_speed_kbps INTEGER,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);
        }

        CatalogContractChecker checker = new CatalogContractChecker(jdbcClient);

        IllegalStateException ex = assertThrows(IllegalStateException.class, checker::catalogAvailability);
        assertTrue(ex.getMessage().contains("plan.price"));
    }

    @Test
    @DisplayName("Passes with core columns present; optional groups detected correctly")
    void catalogAvailability_fullSchema_detectsBonusAndAppsAvailable() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Core
            stmt.execute("""
                    CREATE TABLE plan (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        code VARCHAR(50) NOT NULL,
                        name VARCHAR(150) NOT NULL,
                        description TEXT,
                        price NUMERIC(12,0) NOT NULL,
                        duration_months SMALLINT NOT NULL,
                        quota_type VARCHAR(20) NOT NULL,
                        data_quota_mb BIGINT NOT NULL,
                        cutoff_policy VARCHAR(20) NOT NULL,
                        throttle_speed_kbps INTEGER,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);

            // Bonus
            stmt.execute("""
                    CREATE TABLE plan_first_cycle_bonus (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        plan_id BIGINT NOT NULL,
                        bonus_type VARCHAR(20) NOT NULL,
                        amount BIGINT NOT NULL
                    )
                    """);

            // Apps
            stmt.execute("""
                    CREATE TABLE app (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        code VARCHAR(50) NOT NULL,
                        name VARCHAR(100) NOT NULL
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE plan_app_quota (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        plan_id BIGINT NOT NULL,
                        app_id BIGINT NOT NULL,
                        quota_type VARCHAR(20) NOT NULL,
                        quota_mb BIGINT NOT NULL
                    )
                    """);
        }

        CatalogContractChecker checker = new CatalogContractChecker(jdbcClient);
        CatalogAvailability availability = checker.catalogAvailability();

        assertNotNull(availability);
        assertTrue(availability.isBonusAvailable(), "Bonus group should be available");
        assertTrue(availability.isAppsAvailable(), "Apps group should be available");
        assertFalse(availability.isVoiceAvailable(), "Voice group should NOT be available (tables not created)");
    }
}
