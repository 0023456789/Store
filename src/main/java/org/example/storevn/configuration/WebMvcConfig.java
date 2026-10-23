package org.example.storevn.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebMvcConfig(@Value("${storefront.web-url:http://localhost:5173}") String webUrl) {
        List<String> origins = new ArrayList<>();
        if (webUrl != null && !webUrl.isBlank()) {
            for (String origin : webUrl.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    origins.add(trimmed);
                }
            }
        }
        if (!origins.contains("http://localhost:5173")) {
            origins.add("http://localhost:5173");
        }
        if (!origins.contains("http://127.0.0.1:5173")) {
            origins.add("http://127.0.0.1:5173");
        }
        this.allowedOrigins = origins;
        log.info("Configured CORS allowed origin patterns: {}", this.allowedOrigins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location", "Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
