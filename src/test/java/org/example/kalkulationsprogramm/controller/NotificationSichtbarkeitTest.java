package org.example.kalkulationsprogramm.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.UrlaubsantragRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.MonatsabschlussBerechtigungService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBerechtigungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Benachrichtigungs-Glocke mit echter SecurityConfig und echtem Sichtbarkeits-Service: Mails aus
 * Postfächern, die der Benutzer nicht sieht, werden weder gezählt noch mit Betreff/Absender gezeigt.
 */
@WebMvcTest(controllers = NotificationController.class)
@Import({ SecurityConfig.class, NotificationSichtbarkeitTest.EchteFilterBeans.class, PostfachSichtbarkeitService.class })
class NotificationSichtbarkeitTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private EmailRepository emailRepository;
    @MockBean private UrlaubsantragRepository urlaubsantragRepository;
    @MockBean private ProjektNotizRepository projektNotizRepository;
    @MockBean private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @MockBean private ProjektDokumentRepository projektDokumentRepository;
    @MockBean private KalenderEintragRepository kalenderEintragRepository;
    @MockBean private LieferantDokumentRepository lieferantDokumentRepository;
    @MockBean private LieferantReklamationRepository lieferantReklamationRepository;
    @MockBean private DokumentFreigabeRepository dokumentFreigabeRepository;
    @MockBean private AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    @MockBean private AnfrageDokumentRepository anfrageDokumentRepository;
    @MockBean private AnfrageRepository anfrageRepository;
    @MockBean private ZeitbuchungRepository zeitbuchungRepository;
    @MockBean private MonatsSaldoRepository monatsSaldoRepository;
    @MockBean private MonatsabschlussBerechtigungService monatsabschlussBerechtigungService;
    @MockBean private TelefonBerechtigungService telefonBerechtigungService;
    @MockBean private TelefonBenachrichtigungService telefonBenachrichtigungService;
    @MockBean private EmailAbsenderRepository postfachRepository;
    @MockBean private EmailPostfachZuordnungRepository zuordnungRepository;
    @MockBean private FrontendUserProfileService frontendUserProfileService;

    @BeforeEach
    void daten() {
        Mockito.reset(emailRepository, zuordnungRepository, postfachRepository);
        EmailAbsender info = postfach(1L, "info@example.com", true, true);
        EmailAbsender max = postfach(3L, "max@example.com", false, false);
        given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willReturn(List.of(info, max));
        Email infoMail = mail(10L, "Anfrage Treppe", info, EmailZuordnungTyp.KEINE);
        Email fremdeMail = mail(20L, "Persönlich an Max", max, EmailZuordnungTyp.KEINE);
        Email fremdeProjektMail = mail(30L, "Aufmaß vertraulich", max, EmailZuordnungTyp.PROJEKT);
        given(emailRepository.findInboxFiltered()).willReturn(List.of(infoMail, fremdeMail));
        given(emailRepository.findProjectEmails()).willReturn(List.of(fremdeProjektMail));
        given(zuordnungRepository.findEmailIdsNurInAnderenPostfaechern(Set.of(1L))).willReturn(Set.of(20L, 30L));
    }

    private static EmailAbsender postfach(Long id, String adresse, boolean haupt, boolean fuerAlle) {
        EmailAbsender p = new EmailAbsender();
        p.setId(id);
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        p.setSichtbarFuerAlle(fuerAlle);
        return p;
    }

    private static Email mail(Long id, String betreff, EmailAbsender postfach, EmailZuordnungTyp typ) {
        Email e = new Email();
        e.setId(id);
        e.setSubject(betreff);
        e.setFromAddress("kunde@example.org");
        e.setDirection(EmailDirection.IN);
        e.setZuordnungTyp(typ);
        e.setSentAt(LocalDateTime.of(2026, 10, 10, 9, 0).minusMinutes(id));
        e.ordnePostfachZu(postfach, "INBOX", id);
        return e;
    }

    @Test
    void ohneAnmeldung401() throws Exception {
        mockMvc.perform(get("/api/notifications/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void glockeZeigtNurSichtbareMails() throws Exception {
        mockMvc.perform(get("/api/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.type == 'EMAILS')].count").value(1))
                .andExpect(jsonPath("$.categories[*].type", not(hasItem("EMAILS_PROJECTS"))))
                .andExpect(jsonPath("$.recentItems[*].title", hasItem("Anfrage Treppe")))
                .andExpect(jsonPath("$.recentItems[*].title", not(hasItem("Persönlich an Max"))))
                .andExpect(jsonPath("$.recentItems[*].title", not(hasItem("Aufmaß vertraulich"))));
    }

    @Test
    @WithMockUser(username = "chefin", roles = "ADMIN")
    void adminSiehtAlles() throws Exception {
        mockMvc.perform(get("/api/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.type == 'EMAILS')].count").value(2))
                .andExpect(jsonPath("$.categories[?(@.type == 'EMAILS_PROJECTS')].count").value(1))
                .andExpect(jsonPath("$.recentItems[*].title", hasItem("Persönlich an Max")));
    }

    @Test
    @WithMockUser(username = "erika", roles = "USER")
    void sichtbarkeitNichtErmittelbarKeineMailHinweise() throws Exception {
        given(postfachRepository.findByAktivTrueOrderBySortierungAscIdAsc()).willThrow(new IllegalStateException("DB weg"));

        mockMvc.perform(get("/api/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[*].type", not(hasItem("EMAILS"))))
                .andExpect(jsonPath("$.recentItems[*].title", not(hasItem("Anfrage Treppe"))));
    }
}
