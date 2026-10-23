package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.controller.LieferantDokumentController;
import org.example.kalkulationsprogramm.controller.UnifiedEmailController;
import org.example.kalkulationsprogramm.controller.ZeitverwaltungController;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Massenläufe ohne Knopf in der Oberfläche (Mail-Import, Spam-/Anfrage-Scans, Anhänge per KI
 * verarbeiten, Zeitkonten-Cache) dürfen nur Admins starten. Läuft durch die echten
 * Security-Ketten; ein Probe-Controller ersetzt die schweren Fach-Controller.
 */
@WebMvcTest(controllers = AdminMassenlaeufeSecurityTest.ProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, AdminMassenlaeufeSecurityTest.ProbeController.class, AdminMassenlaeufeSecurityTest.FilterBeans.class})
class AdminMassenlaeufeSecurityTest {

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @RestController
    static class ProbeController {
        @RequestMapping({
                "/api/emails/import", "/api/emails/scan-spam", "/api/emails/scan-inquiries",
                "/api/emails/scan-assignments", "/api/emails/scan-steuerberater", "/api/emails/backfill-parents",
                "/api/emails/spam-model/rescore", "/api/emails/spam-model/bootstrap",
                "/api/lieferant-dokumente/process-assigned-emails",
                "/api/lieferant-dokumente/process-email/{emailId}",
                "/api/lieferant-dokumente/lieferant/{lieferantId}/process-emails",
                "/api/zeitverwaltung/saldo-cache/warmup"})
        String lauf() { return "gestartet"; }

        @PostMapping("/api/emails/normaler-endpunkt") String normal() { return "ok"; }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;

    static Stream<String> massenlaeufe() {
        return Arrays.stream(SecurityConfig.ADMIN_MASSENLAEUFE).map(pfad -> pfad.replace("*", "42"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("massenlaeufe")
    void normalerBenutzerBekommt403(String pfad) throws Exception {
        mvc.perform(post(pfad).with(user("max.mustermann").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        // Auch über GET kein Hintertürchen.
        mvc.perform(get(pfad).with(user("max.mustermann").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("massenlaeufe")
    void adminDarfStarten(String pfad) throws Exception {
        mvc.perform(post(pfad).with(user("chef").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("massenlaeufe")
    void ohneAnmeldungBekommt401(String pfad) throws Exception {
        mvc.perform(post(pfad).with(csrf())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("massenlaeufe")
    void adminOhneCsrfTokenBekommt403(String pfad) throws Exception {
        mvc.perform(post(pfad).with(user("chef").roles("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void andereEndpunkteBleibenFuerAngemeldeteOffen() throws Exception {
        mvc.perform(post("/api/emails/normaler-endpunkt").with(user("max.mustermann").roles("USER")).with(csrf()))
                .andExpect(status().isOk());
    }

    /** Schützt vor Tippfehlern: Jeder gesperrte Pfad muss ein echtes POST-Mapping treffen. */
    @Test
    void jederGesperrtePfadGehoertZuEinemEchtenEndpunkt() {
        Set<String> echtePostPfade = new HashSet<>();
        for (Class<?> controller : new Class<?>[] {
                UnifiedEmailController.class, LieferantDokumentController.class, ZeitverwaltungController.class}) {
            String basis = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class).value()[0];
            for (Method methode : controller.getDeclaredMethods()) {
                PostMapping mapping = AnnotatedElementUtils.findMergedAnnotation(methode, PostMapping.class);
                if (mapping == null) continue;
                for (String pfad : mapping.value()) {
                    echtePostPfade.add((basis + pfad).replaceAll("\\{[^/}]++}", "*"));
                }
            }
        }

        assertThat(echtePostPfade).contains(SecurityConfig.ADMIN_MASSENLAEUFE);
    }
}
