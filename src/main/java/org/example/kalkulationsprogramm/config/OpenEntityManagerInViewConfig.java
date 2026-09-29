package org.example.kalkulationsprogramm.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Open-EntityManager-in-View wie bei Spring Boot (dort per
 * {@code spring.jpa.open-in-view=false} abgeschaltet), aber ohne die
 * langlebigen Live-Strecken.
 * <p>
 * Bei asynchronen Anfragen (Server-Sent Events) hält Open-in-View den
 * EntityManager – und damit eine DB-Verbindung aus dem Pool – so lange fest,
 * bis die Verbindung zum Browser endet (bis zu 30 Minuten). Ein paar offene
 * Tabs reichen dann, um den ganzen Pool leerzulaufen.
 */
@Configuration
public class OpenEntityManagerInViewConfig implements WebMvcConfigurer {

    /** Endpunkte, die dauerhaft offen bleiben und deshalb ohne Open-in-View laufen. */
    private static final List<String> OHNE_OPEN_IN_VIEW = List.of("/api/telefon/live");

    private final ObjectProvider<EntityManagerFactory> entityManagerFactory;

    public OpenEntityManagerInViewConfig(ObjectProvider<EntityManagerFactory> entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        EntityManagerFactory emf = entityManagerFactory.getIfAvailable();
        if (emf == null) {
            // z.B. @WebMvcTest-Slices ohne JPA
            return;
        }
        OpenEntityManagerInViewInterceptor interceptor = new OpenEntityManagerInViewInterceptor();
        interceptor.setEntityManagerFactory(emf);
        registry.addWebRequestInterceptor(interceptor).excludePathPatterns(OHNE_OPEN_IN_VIEW);
    }
}
