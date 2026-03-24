package org.example.kalkulationsprogramm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Globale CORS-Konfiguration für alle API-Endpunkte.
 * Erlaubt Anfragen von allen Origins (wichtig für die PWA auf mobilen Geräten
 * über VPN).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA-Forwarding: React-Routen an index.html weiterleiten
        registry.addViewController("/dokument-editor").setViewName("forward:/index.html");

        // Zeiterfassung PWA: alle Navigationsanfragen auf index.html weiterleiten
        // Wichtig: ohne Trailing-Slash und mit Trailing-Slash abdecken
        registry.addViewController("/zeiterfassung").setViewName("forward:/zeiterfassung/index.html");
        registry.addViewController("/zeiterfassung/").setViewName("forward:/zeiterfassung/index.html");
    }

    @Override
    public void addResourceHandlers(
            org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        // Explizites Mapping für die Zeiterfassungs-App
        registry.addResourceHandler("/zeiterfassung/**")
                .addResourceLocations("classpath:/static/zeiterfassung/");

        // Standard Static Mapping sicherstellen
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/resources/",
                        "classpath:/META-INF/resources/");
    }
}
