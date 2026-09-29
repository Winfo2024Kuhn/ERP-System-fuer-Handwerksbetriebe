package org.example.kalkulationsprogramm.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenEntityManagerInViewConfigTest {

    /** Macht die geschützte Interceptor-Liste für den Test sichtbar. */
    private static class PruefRegistry extends InterceptorRegistry {
        List<Object> alle() {
            return getInterceptors();
        }
    }

    private static PruefRegistry registriere(EntityManagerFactory emf) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (emf != null) {
            beanFactory.registerSingleton("entityManagerFactory", emf);
        }
        PruefRegistry registry = new PruefRegistry();
        new OpenEntityManagerInViewConfig(beanFactory.getBeanProvider(EntityManagerFactory.class))
                .addInterceptors(registry);
        return registry;
    }

    private static boolean greiftBei(MappedInterceptor interceptor, String pfad) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", pfad);
        ServletRequestPathUtils.parseAndCache(request);
        return interceptor.matches(request);
    }

    @Test
    void liveStreckeLaeuftOhneOpenInView() {
        List<Object> interceptors = registriere(mock(EntityManagerFactory.class)).alle();

        assertThat(interceptors).hasSize(1);
        MappedInterceptor osiv = (MappedInterceptor) interceptors.get(0);
        assertThat(greiftBei(osiv, "/api/telefon/live")).isFalse();
    }

    @Test
    void normaleEndpunkteBehaltenOpenInView() {
        MappedInterceptor osiv = (MappedInterceptor) registriere(mock(EntityManagerFactory.class)).alle().get(0);

        assertThat(greiftBei(osiv, "/api/telefon/status")).isTrue();
        assertThat(greiftBei(osiv, "/api/projekte/1")).isTrue();
        assertThat(greiftBei(osiv, "/api/auth/me")).isTrue();
    }

    @Test
    void ohneEntityManagerFactoryWirdNichtsRegistriert() {
        assertThat(registriere(null).alle()).isEmpty();
    }
}
