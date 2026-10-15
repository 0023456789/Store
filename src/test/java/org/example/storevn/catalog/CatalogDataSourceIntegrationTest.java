package org.example.storevn.catalog;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for dual-datasource setup:
 * - Store DB: JPA + Flyway (primary)
 * - Catalog DB: JdbcClient read-only (secondary)
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogDataSourceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> catalogDb = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("admin_test")
            .withUsername("test_readonly")
            .withPassword("test");

    @Container
    static PostgreSQLContainer<?> storeDb = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("store_test")
            .withUsername("postgres")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Store (primary) datasource
        registry.add("spring.datasource.url", storeDb::getJdbcUrl);
        registry.add("spring.datasource.username", storeDb::getUsername);
        registry.add("spring.datasource.password", storeDb::getPassword);

        // Catalog (secondary) datasource – uses the superuser for simplicity in test;
        // the real role store_readonly only has SELECT.
        registry.add("catalog.datasource.url", catalogDb::getJdbcUrl);
        registry.add("catalog.datasource.username", catalogDb::getUsername);
        registry.add("catalog.datasource.password", catalogDb::getPassword);

        // Flyway runs on store DB only (auto-configured); disable for clean test
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    @Qualifier("catalogJdbcClient")
    JdbcClient catalogJdbcClient;

    @Autowired
    @Qualifier("catalogDataSource")
    DataSource catalogDataSource;

    @Autowired
    CatalogAvailability catalogAvailability;

    @BeforeAll
    static void setupCatalogSchema() throws SQLException {
        // Bootstrap the admin schema in the catalog test container
        try (Connection conn = java.sql.DriverManager.getConnection(
                catalogDb.getJdbcUrl(), catalogDb.getUsername(), catalogDb.getPassword());
             Statement stmt = conn.createStatement()) {

            // Create plan table (core columns)
            stmt.execute("""
                    CREATE TABLE plan (
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
                        created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """);

            // Bonus table
            stmt.execute("""
                    CREATE TABLE plan_first_cycle_bonus (
                        id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        plan_id     BIGINT NOT NULL REFERENCES plan(id),
                        bonus_type  VARCHAR(20) NOT NULL,
                        amount      BIGINT NOT NULL
                    )
                    """);

            // App tables
            stmt.execute("""
                    CREATE TABLE app (
                        id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        code       VARCHAR(50) NOT NULL UNIQUE,
                        name       VARCHAR(100) NOT NULL,
                        is_active  BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE plan_app_quota (
                        id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        plan_id     BIGINT NOT NULL REFERENCES plan(id),
                        app_id      BIGINT NOT NULL REFERENCES app(id),
                        quota_type  VARCHAR(20) NOT NULL,
                        quota_mb    BIGINT NOT NULL
                    )
                    """);

            // Seed data
            stmt.execute("""
                    INSERT INTO plan (code, name, price, duration_months, quota_type, data_quota_mb,
                                     cycle_days, voice_minutes, cutoff_policy, throttle_speed_kbps)
                    VALUES ('SKY89', 'Sky89', 89000, 1, 'DAILY', 3072, NULL, 100, 'THROTTLE', 512)
                    """);

            stmt.execute("INSERT INTO app (code, name) VALUES ('TIKTOK', 'TikTok')");
        }
    }

    @Test
    @Order(1)
    @DisplayName("catalogJdbcClient can SELECT from plan table")
    void readFromCatalog_selectPlan_returnsData() {
        Long count = catalogJdbcClient.sql("SELECT count(*) FROM plan WHERE is_active = true")
                .query(Long.class)
                .single();
        assertTrue(count > 0, "Should find at least one active plan");
    }

    @Test
    @Order(2)
    @DisplayName("catalogJdbcClient can read plan details")
    void readFromCatalog_selectPlanByCode_returnsCorrectData() {
        var plan = catalogJdbcClient.sql("SELECT code, name, price FROM plan WHERE code = :code")
                .param("code", "SKY89")
                .query((rs, rowNum) -> new Object[]{
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBigDecimal("price").longValue()
                })
                .single();

        assertEquals("SKY89", plan[0]);
        assertEquals("Sky89", plan[1]);
        assertEquals(89000L, plan[2]);
    }

    @Test
    @Order(3)
    @DisplayName("catalogJdbcClient cannot INSERT (read-only enforcement in real DB)")
    void writeToDialog_insertPlan_failsOnReadOnlyRole() {
        // In the real environment, the store_readonly role has:
        //   ALTER ROLE store_readonly SET default_transaction_read_only = on;
        // In the test container we use the superuser, so we simulate by setting
        // the session to read-only explicitly.
        try (Connection conn = catalogDataSource.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO plan (code, name, price, duration_months, quota_type, "
                        + "data_quota_mb, cutoff_policy) VALUES ('HACK', 'Hack', 0, 1, 'DAILY', 1024, 'DISCONNECT')");
                fail("Should have thrown an exception – catalog is read-only");
            } catch (SQLException e) {
                // Expected: "cannot execute INSERT in a read-only transaction"
                assertTrue(e.getMessage().toLowerCase().contains("read-only"),
                        "Error should mention read-only: " + e.getMessage());
            }
        } catch (SQLException e) {
            fail("Connection setup failed: " + e.getMessage());
        }
    }

    @Test
    @Order(4)
    @DisplayName("CatalogAvailability is populated correctly")
    void catalogAvailability_withFullSchema_showsCorrectFlags() {
        // With the test schema: plan (core) ✓, bonus ✓, apps ✓, voice ✗ (no tables)
        assertNotNull(catalogAvailability, "CatalogAvailability bean should be created");
        assertTrue(catalogAvailability.isBonusAvailable(), "Bonus tables exist in test schema");
        assertTrue(catalogAvailability.isAppsAvailable(), "App tables exist in test schema");
        assertFalse(catalogAvailability.isVoiceAvailable(), "Voice tables do not exist in test schema");
    }
}
