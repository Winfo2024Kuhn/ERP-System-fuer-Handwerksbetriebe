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
        // PC-SPA: Reload auf React-Routen (z.B. /dokumentuebersicht, /dokument-editor,
        // /ki-assistent) wird vom SpaErrorController via 404-Forward auf /index.html
        // behandelt. Hier nur Mobile-PWA, da diese auf einem anderen index.html liegt.
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
