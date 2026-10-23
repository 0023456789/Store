package org.example.storevn.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
public class JpaConfig {

    @Bean
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
