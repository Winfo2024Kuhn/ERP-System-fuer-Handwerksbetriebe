package org.example.kalkulationsprogramm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Weiterleitungen und statische Ressourcen der beiden Oberflächen (PC-SPA und Mobile-PWA).
 * CORS steht bewusst nicht hier, sondern in den Security-Ketten
 * ({@link ApiCorsConfigurationSource}): Nur dort greift es vor der Anmeldung und für jede Kette
 * gezielt.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // PC-SPA: Reload auf React-Routen (z.B. /dokumentuebersicht) wird vom
        // SpaErrorController via 404-Forward auf /index.html behandelt.
        // Hier nur Mobile-PWA, da diese auf einem anderen index.html liegt.
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
