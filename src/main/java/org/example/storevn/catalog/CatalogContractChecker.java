package org.example.storevn.catalog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks that the admin (catalog) database contains the columns the storefront needs.
 *
 * <ul>
 *   <li><b>Core group</b> (plan table essentials): missing → application refuses to start.</li>
 *   <li><b>Bonus group</b> (plan_first_cycle_bonus): missing → feature hidden.</li>
 *   <li><b>Apps group</b> (plan_app_quota + app): missing → feature hidden.</li>
 *   <li><b>Voice group</b> (plan_voice_allowance + network): missing → feature hidden.</li>
 * </ul>
 */
@Configuration
@Slf4j
public class CatalogContractChecker {

    // ── Core: plan table columns that storefront absolutely needs ──
    private static final Map<String, List<String>> CORE_COLUMNS = Map.of(
            "plan", List.of("id", "code", "name", "description", "price",
                    "duration_months", "quota_type", "data_quota_mb",
                    "cutoff_policy", "throttle_speed_kbps", "is_active")
    );

    // ── Optional groups ──
    private static final Map<String, List<String>> BONUS_COLUMNS = Map.of(
            "plan_first_cycle_bonus", List.of("id", "plan_id", "bonus_type", "amount")
    );

    private static final Map<String, List<String>> APPS_COLUMNS = Map.of(
            "plan_app_quota", List.of("id", "plan_id", "app_id", "quota_type", "quota_mb"),
            "app", List.of("id", "code", "name")
    );

    private static final Map<String, List<String>> VOICE_COLUMNS = Map.of(
            "plan_voice_allowance", List.of("id", "plan_id", "network_id", "minutes"),
            "network", List.of("id", "code", "name")
    );

    private final JdbcClient catalogJdbcClient;

    public CatalogContractChecker(@Qualifier("catalogJdbcClient") JdbcClient catalogJdbcClient) {
        this.catalogJdbcClient = catalogJdbcClient;
    }

    @Bean
    public CatalogAvailability catalogAvailability() {
        // ── Core: must exist or we fail fast ──
        List<String> coreMissing = checkGroup("core", CORE_COLUMNS);
        if (!coreMissing.isEmpty()) {
            throw new IllegalStateException(
                    "Catalog contract violation: core columns missing in admin DB: " + coreMissing
                            + ". Storefront cannot start without a valid plan catalog.");
        }

        // ── Optional groups: missing → feature disabled ──
        boolean bonusOk = checkGroup("bonus", BONUS_COLUMNS).isEmpty();
        boolean appsOk = checkGroup("apps", APPS_COLUMNS).isEmpty();
        boolean voiceOk = checkGroup("voice", VOICE_COLUMNS).isEmpty();

        CatalogAvailability availability = new CatalogAvailability(bonusOk, appsOk, voiceOk);
        log.info("Catalog contract check passed. Availability: {}", availability);
        return availability;
    }

    /**
     * Returns a list of "table.column" strings that are missing from the catalog DB.
     * An empty list means all columns for this group are present.
     */
    private List<String> checkGroup(String groupName, Map<String, List<String>> expectedColumns) {
        List<String> missing = new java.util.ArrayList<>();

        for (var entry : expectedColumns.entrySet()) {
            String table = entry.getKey();
            List<String> requiredCols = entry.getValue();

            Set<String> actualCols = getColumnsOf(table);
            if (actualCols.isEmpty()) {
                // Table does not exist at all
                requiredCols.forEach(col -> missing.add(table + "." + col));
                continue;
            }

            for (String col : requiredCols) {
                if (!actualCols.contains(col)) {
                    missing.add(table + "." + col);
                }
            }
        }

        if (!missing.isEmpty()) {
            log.warn("Catalog contract: group [{}] is missing columns: {}. Feature will be disabled.", groupName, missing);
        }
        return missing;
    }

    /**
     * Queries information_schema to find column names for a given table in the public schema.
     * Returns an empty set if the table does not exist.
     */
    private Set<String> getColumnsOf(String tableName) {
        try {
            return catalogJdbcClient.sql("""
                            SELECT lower(column_name)
                            FROM information_schema.columns
                            WHERE lower(table_schema) = 'public' AND lower(table_name) = lower(:table)
                            """)
                    .param("table", tableName)
                    .query(String.class)
                    .list()
                    .stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Failed to query information_schema for table [{}]: {}", tableName, e.getMessage());
            return Set.of();
        }
    }
}
