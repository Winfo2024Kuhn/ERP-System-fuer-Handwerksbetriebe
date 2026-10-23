package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebsiteInternalApiKeyFilterTest {

    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    private boolean passiert(WebsiteInternalApiKeyFilter filter, MockHttpServletRequest request,
            MockHttpServletResponse response) throws Exception {
        AtomicBoolean durch = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> durch.set(true));
        return durch.get();
    }

    @Test void richtigerSchluesselPassiert() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/internal/anfrage");
        request.addHeader(WebsiteInternalApiKeyFilter.HEADER, "dummy-key");
        var response = new MockHttpServletResponse();
        assertThat(passiert(new WebsiteInternalApiKeyFilter("dummy-key", mapper), request, response)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test void fehlenderOderFalscherSchluesselErgibt401MitJson() throws Exception {
        var filter = new WebsiteInternalApiKeyFilter("dummy-key", mapper);
        for (String header : new String[]{null, "", "anderer-key", "dummy-key-plus"}) {
            var request = new MockHttpServletRequest("GET", "/api/internal/freigabe/x");
            if (header != null) request.addHeader(WebsiteInternalApiKeyFilter.HEADER, header);
            var response = new MockHttpServletResponse();
            assertThat(passiert(filter, request, response)).as("Header=%s", header).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("\"success\":false");
            assertThat(response.getContentAsString()).doesNotContain("dummy-key");
        }
    }

    @Test void ohneKonfiguriertenSchluesselIstAllesGesperrt() throws Exception {
        for (String konfiguriert : new String[]{null, "", "   "}) {
            var filter = new WebsiteInternalApiKeyFilter(konfiguriert, mapper);
            var request = new MockHttpServletRequest("GET", "/api/internal/freigabe/x");
            request.addHeader(WebsiteInternalApiKeyFilter.HEADER, "");
            var response = new MockHttpServletResponse();
            assertThat(passiert(filter, request, response)).as("Konfiguriert=%s", konfiguriert).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test void gueltigerCloudflareJwtErsetztDenSchluessel() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/internal/freigabe/x");
        request.setAttribute(CloudflareAccessJwtFilter.VERIFIED_ATTRIBUTE, Boolean.TRUE);
        assertThat(passiert(new WebsiteInternalApiKeyFilter("dummy-key", mapper), request,
                new MockHttpServletResponse())).isTrue();
    }
}
