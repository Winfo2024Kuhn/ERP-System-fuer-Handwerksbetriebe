package org.example.kalkulationsprogramm.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * Weiterleitungen und statische Ressourcen der beiden Oberflächen (PC-SPA und Mobile-PWA).
 *
 * <p><b>Browser-Cache:</b> Dateien unter {@code assets/} tragen einen Hash im Namen (Vite) und
 * ändern sich nie – der Browser darf sie ein Jahr behalten, ohne nachzufragen. Alles andere
 * ({@code index.html}, {@code sw.js}, Manifeste, Logos) fragt bei jedem Laden kurz nach, ob es
 * neuer ist ({@code no-cache} + Last-Modified → 304), damit ein Update sofort ankommt.
 * Ohne eigenen Header setzt Spring Security {@code no-store}, und jeder Reload lädt das ganze
 * Bundle neu – über Tailscale von unterwegs dauerte das ewig.
 * CORS steht bewusst nicht hier, sondern in den Security-Ketten
 * ({@link ApiCorsConfigurationSource}): Nur dort greift es vor der Anmeldung und für jede Kette
 * gezielt.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    static final CacheControl ASSETS_CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final ZeiterfassungZugriffInterceptor zeiterfassungZugriffInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(zeiterfassungZugriffInterceptor)
                .addPathPatterns(ZeiterfassungZugriffInterceptor.GESPERRTE_PFADE)
                .excludePathPatterns(ZeiterfassungZugriffInterceptor.FREIE_PFADE);
    }

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
        // Gehashte Bundles beider Oberflächen: unveränderlich
        registry.addResourceHandler("/zeiterfassung/assets/**")
                .addResourceLocations("classpath:/static/zeiterfassung/assets/")
                .setCacheControl(ASSETS_CACHE);
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(ASSETS_CACHE);

        // Explizites Mapping für die Zeiterfassungs-App
        registry.addResourceHandler("/zeiterfassung/**")
                .addResourceLocations("classpath:/static/zeiterfassung/")
                .setCacheControl(CacheControl.noCache());

        // Standard Static Mapping sicherstellen
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/resources/",
                        "classpath:/META-INF/resources/")
                .setCacheControl(CacheControl.noCache());
    }
}
