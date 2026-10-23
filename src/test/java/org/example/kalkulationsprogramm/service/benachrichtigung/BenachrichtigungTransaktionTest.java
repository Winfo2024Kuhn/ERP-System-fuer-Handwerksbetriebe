package org.example.kalkulationsprogramm.service.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.BenachrichtigungErledigtRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.MonatsabschlussBerechtigungService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.service.ZeiterfassungEinstellungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBerechtigungService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Glocke mit echtem Transaktionsmanager: Wirft eine Quelle in einem anderen transaktionalen
 * Service, darf das weder die Glocke noch das Erledigen ganzer Spalten mitreißen (früher:
 * gemeinsame Transaktion → "nur noch zurückrollen" → 500).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ BenachrichtigungService.class, BenachrichtigungErledigtService.class,
        org.example.kalkulationsprogramm.service.EmailGelesenService.class,
        BenachrichtigungTransaktionTest.TelefonMitDefekterQuelle.class })
class BenachrichtigungTransaktionTest {

    /** Anrufbeantworter wirft innerhalb seiner eigenen Transaktion, verpasste Anrufe gehen. */
    @TestConfiguration
    static class TelefonMitDefekterQuelle {
        @Bean
        TelefonBenachrichtigungService telefonBenachrichtigungService() {
            return new DefekterAnrufbeantworter();
        }
    }

    static class DefekterAnrufbeantworter extends TelefonBenachrichtigungService {
        DefekterAnrufbeantworter() {
            super(null, null, null, null);
        }

        @Override
        @Transactional(readOnly = true)
        public List<Eintrag> neueSprachnachrichten() {
            throw new IllegalStateException("Anrufbeantworter nicht erreichbar");
        }

        @Override
        @Transactional(readOnly = true)
        public List<Eintrag> offeneVerpassteAnrufe() {
            return List.of(new Eintrag("VERPASSTER_ANRUF", "Max Mustermann", "Verpasst · heute 08:20",
                    LocalDateTime.now(), "/telefon/anrufe?anruf=7"));
        }
    }

    @Autowired private BenachrichtigungService service;
    @Autowired private FrontendUserProfileRepository benutzerRepository;
    @Autowired private BenachrichtigungErledigtRepository erledigtRepository;
    @Autowired private org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    @Autowired private org.example.kalkulationsprogramm.repository.EmailAbsenderRepository postfachRepository;

    @MockitoBean private MonatsabschlussBerechtigungService monatsabschlussBerechtigungService;
    @MockitoBean private TelefonBerechtigungService telefonBerechtigungService;
    @MockitoBean private org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService zeiterfassungSichtbarkeitService;
    @MockitoBean private PostfachSichtbarkeitService postfachSichtbarkeitService;
    @MockitoBean private ZeiterfassungEinstellungService zeiterfassungEinstellungService;
    @MockitoBean private FrontendUserProfileService frontendUserProfileService;
    @MockitoBean private BetriebsstatusService betriebsstatusService;

    private final Authentication max = UsernamePasswordAuthenticationToken.authenticated("max", null, List.of());

    @BeforeEach
    void benutzer() {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setDisplayName("Max Mustermann");
        profil.setShortCode("MM");
        profil = benutzerRepository.save(profil);
        given(frontendUserProfileService.findByUsername("max")).willReturn(Optional.of(profil));
        given(telefonBerechtigungService.darfTelefonSehen(any(Authentication.class))).willReturn(true);
        given(postfachSichtbarkeitService.verborgeneEmailIds(any())).willReturn(Set.of());
    }

    private final List<org.example.kalkulationsprogramm.domain.Email> angelegteMails = new java.util.ArrayList<>();
    private final List<org.example.kalkulationsprogramm.domain.EmailAbsender> angelegtePostfaecher = new java.util.ArrayList<>();

    @AfterEach
    void aufraeumen() {
        emailRepository.deleteAll(angelegteMails);
        postfachRepository.deleteAll(angelegtePostfaecher);
        erledigtRepository.deleteAll();
        benutzerRepository.deleteAll();
    }

    @Test
    @DisplayName("Eine werfende Quelle fehlt – die übrigen erscheinen, keine UnexpectedRollbackException")
    void defekteQuelleReisstGlockeNichtMit() {
        var summary = service.zusammenfassung(null, max);

        assertThat(summary.categories()).noneMatch(c -> c.type().equals("SPRACHNACHRICHTEN"));
        assertThat(summary.categories()).filteredOn(c -> c.type().equals("VERPASSTE_ANRUFE")).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));
    }

    @Test
    @DisplayName("Spalte erledigen: die heile Quelle wird gemerkt, obwohl die andere wirft")
    void spalteErledigenTrotzDefekterQuelle() {
        int anzahl = service.kategorienErledigen(List.of("SPRACHNACHRICHTEN", "VERPASSTE_ANRUFE"), max);

        assertThat(anzahl).isEqualTo(1);
        assertThat(erledigtRepository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getSchluessel()).isEqualTo("VERPASSTER_ANRUF:7"));
        assertThat(service.zusammenfassung(null, max).categories())
                .noneMatch(c -> c.type().equals("VERPASSTE_ANRUFE"));
    }

    @Test
    @DisplayName("Doppelt gemerkt bleibt ein Eintrag")
    void doppeltGemerktBleibtEiner() {
        service.erledigen(List.of("TERMIN:5"), max);
        service.erledigen(List.of("TERMIN:5"), max);
        assertThat(erledigtRepository.findAll()).hasSize(1);
    }

    private org.example.kalkulationsprogramm.domain.EmailAbsender postfach(String adresse) {
        var p = new org.example.kalkulationsprogramm.domain.EmailAbsender();
        p.setEmailAdresse(adresse);
        p = postfachRepository.save(p);
        angelegtePostfaecher.add(p);
        return p;
    }

    private org.example.kalkulationsprogramm.domain.Email mail(String betreff,
            org.example.kalkulationsprogramm.domain.EmailAbsender postfach, long uid) {
        var e = new org.example.kalkulationsprogramm.domain.Email();
        e.setMessageId("<" + uid + "@example.org>");
        e.setSubject(betreff);
        e.setFromAddress("kunde@example.org");
        e.setDirection(org.example.kalkulationsprogramm.domain.EmailDirection.IN);
        e.setSentAt(LocalDateTime.of(2026, 10, 11, 8, 0));
        e.ordnePostfachZu(postfach, "INBOX", uid);
        e = emailRepository.saveAndFlush(e);
        angelegteMails.add(e);
        return e;
    }

    @Test
    @DisplayName("Mitarbeiter (nicht Admin) klickt Mail an: sichtbare wird gelesen, fremde bleibt ungelesen")
    void mailErledigenOhneAdminLaedtZuordnungenInDerTransaktion() {
        var info = postfach("info@musterbetrieb.example");
        var chef = postfach("chef@musterbetrieb.example");
        var sichtbar = mail("Anfrage Carport", info, 1L);
        var fremd = mail("Persönlich an den Chef", chef, 2L);
        given(postfachSichtbarkeitService.fuer(max))
                .willReturn(new org.example.kalkulationsprogramm.service.PostfachSichtbarkeit(false, Set.of(info.getId())));

        service.erledigen(List.of("EMAIL:" + sichtbar.getId(), "EMAIL:" + fremd.getId()), max);

        assertThat(emailRepository.findById(sichtbar.getId())).get()
                .satisfies(e -> assertThat(e.isRead()).isTrue());
        assertThat(emailRepository.findById(fremd.getId())).get()
                .satisfies(e -> assertThat(e.isRead()).isFalse());
    }
}
