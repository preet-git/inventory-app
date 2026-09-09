package com.ablsoft.inventory.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Lets the dashboard call the API from its own origin.
 *
 * <p>The browser refuses a cross-origin request unless the server says otherwise, and the dashboard
 * is served from a different port to the API. The allowed origins are configuration rather than a
 * constant so a deployment names its own front end; the default covers the two this project uses --
 * the Angular dev server, and the dashboard's container.
 *
 * <p>A filter rather than {@code WebMvcConfigurer#addCorsMappings}, so the preflight is answered
 * before anything else in the chain can reject it.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        // No cookies or auth header is ever sent, so credentials stay off.
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return new CorsFilter(source);
    }
}
