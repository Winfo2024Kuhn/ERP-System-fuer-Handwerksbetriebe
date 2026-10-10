package org.example.kalkulationsprogramm.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class PublicMobileIngressTest {
    @Test void publicIngressVerweigertDesktopUndInterneRouten() throws Exception {
        for (String path : new String[]{"/api/frontend-users", "/api/internal/funnel", "/login", "/actuator/health", "/api/projekte/preise-nachtragen"}) {
            var request = new MockHttpServletRequest("POST", path);
            request.addHeader("X-ERP-Public-Mobile", "1");
            var response = new MockHttpServletResponse();
            var passed = new AtomicBoolean();
            new PublicMobileIngressFilter().doFilter(request, response, (req,res) -> passed.set(true));
            assertThat(response.getStatus()).as(path).isEqualTo(404);
            assertThat(response.getHeader("X-ERP-Public-Mobile")).isEqualTo("1");
            assertThat(passed).isFalse();
        }
    }
    @Test void mobileRoutePassiertZurTokenPruefungAberUnbekannteMethodeNicht() throws Exception {
        for (String method : new String[]{"GET", "DELETE"}) {
            var request = new MockHttpServletRequest(method, "/api/zeiterfassung/projekte");
            request.addHeader("X-ERP-Public-Mobile", "1");
            var response = new MockHttpServletResponse();
            var passed = new AtomicBoolean();
            new PublicMobileIngressFilter().doFilter(request, response, (req,res) -> passed.set(true));
            assertThat(passed.get()).isEqualTo(method.equals("GET"));
        }
    }
    @Test void desktopOhnePublicMarkerBleibtUnveraendert() throws Exception {
        var passed = new AtomicBoolean();
        new PublicMobileIngressFilter().doFilter(new MockHttpServletRequest("GET", "/api/frontend-users"),
            new MockHttpServletResponse(), (req,res) -> passed.set(true));
        assertThat(passed).isTrue();
    }
}
