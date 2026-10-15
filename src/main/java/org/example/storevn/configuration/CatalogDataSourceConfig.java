package org.example.storevn.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

/**
 * Multi-datasource setup:
 * 1. Primary datasource: Store DB (JPA, Flyway) -> spring.datasource
 * 2. Secondary datasource: Admin/Catalog DB (JdbcClient, read-only) -> catalog.datasource
 */
@Configuration
public class CatalogDataSourceConfig {

    // ── Primary DataSource: Store DB (JPA + Flyway) ──
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties storeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource storeDataSource() {
        return storeDataSourceProperties().initializeDataSourceBuilder().build();
    }

    // ── Secondary DataSource: Admin/Catalog DB (Read-Only JdbcClient) ──
    @Bean
    @ConfigurationProperties("catalog.datasource")
    public DataSourceProperties catalogDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource catalogDataSource() {
        return catalogDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcClient catalogJdbcClient(@Qualifier("catalogDataSource") DataSource catalogDataSource) {
        return JdbcClient.create(catalogDataSource);
    }
}

