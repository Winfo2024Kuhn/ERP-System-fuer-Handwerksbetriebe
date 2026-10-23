package org.example.kalkulationsprogramm.service.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungKategorieDto;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailGelesenRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.EmailGelesenService;
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
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mail-Hinweise der Glocke mit echter Sichtbarkeit und echtem „gelesen pro Person“ (H2).
 *
 * <p>Früher galt „gelesen“ für alle: Öffnete der Admin eine neue Mail, bekam kein Kollege mehr
 * einen Hinweis. Jetzt hat jeder seinen eigenen Stand.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ BenachrichtigungService.class, BenachrichtigungErledigtService.class, PostfachSichtbarkeitService.class,
        EmailGelesenService.class })
class BenachrichtigungMailSichtbarkeitTest {

    @Autowired private BenachrichtigungService service;
    @Autowired private FrontendUserProfileRepository benutzerRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private EmailAbsenderRepository postfachRepository;
    @Autowired private EmailGelesenRepository gelesenRepository;

    @MockitoBean private MonatsabschlussBerechtigungService monatsabschlussBerechtigungService;
    @MockitoBean private TelefonBerechtigungService telefonBerechtigungService;
    @MockitoBean private org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService zeiterfassungSichtbarkeitService;
    @MockitoBean private TelefonBenachrichtigungService telefonBenachrichtigungService;
    @MockitoBean private ZeiterfassungEinstellungService zeiterfassungEinstellungService;
    @MockitoBean private FrontendUserProfileService frontendUserProfileService;
    @MockitoBean private BetriebsstatusService betriebsstatusService;

    private final Authentication admin = UsernamePasswordAuthenticationToken.authenticated("admin", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    private final Authentication erika = UsernamePasswordAuthenticationToken.authenticated("erika", null,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private EmailAbsender hauptpostfach;
    private Email mail;
    private int nummer;

    @BeforeEach
    void vorbereiten() {
        benutzer("admin", "Max Mustermann", "MM");
        benutzer("erika", "Erika Mustermann", "EM");
        given(telefonBerechtigungService.darfTelefonSehen(any(Authentication.class))).willReturn(false);
        given(monatsabschlussBerechtigungService.darfMonatAbschliessen(any(Authentication.class))).willReturn(false);

        hauptpostfach = new EmailAbsender();
        hauptpostfach.setEmailAdresse("info@musterbetrieb.example");
        hauptpostfach.setHauptpostfach(true);
        hauptpostfach = postfachRepository.save(hauptpostfach);

        mail = mail("Anfrage Carport");
    }

    @AfterEach
    void aufraeumen() {
        gelesenRepository.deleteAll();
        emailRepository.deleteAll();
        postfachRepository.deleteAll();
        benutzerRepository.deleteAll();
    }

    private FrontendUserProfile benutzer(String username, String name, String kurz) {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setDisplayName(name);
        profil.setShortCode(kurz);
        profil.setUsername(username);
        FrontendUserProfile gespeichert = benutzerRepository.save(profil);
        given(frontendUserProfileService.findByUsername(username)).willReturn(Optional.of(gespeichert));
        return gespeichert;
    }

    private Email mail(String betreff) {
        Email e = new Email();
        e.setMessageId("<" + (++nummer) + "@example.org>");
        e.setSubject(betreff);
        e.setFromAddress("kunde@example.org");
        e.setDirection(EmailDirection.IN);
        e.setSentAt(LocalDateTime.of(2026, 10, 12, 8, nummer));
        e.ordnePostfachZu(hauptpostfach, "INBOX", (long) nummer);
        return emailRepository.saveAndFlush(e);
    }

    private long ungeleseneMails(Authentication wer) {
        return service.zusammenfassung(null, wer).categories().stream()
                .filter(c -> c.type().equals("EMAILS"))
                .mapToInt(BenachrichtigungKategorieDto::count)
                .sum();
    }

    @Test
    @DisplayName("Neue Mail im Hauptpostfach: Admin und normaler Benutzer bekommen beide den Hinweis")
    void beideSehenNeueMail() {
        assertThat(ungeleseneMails(admin)).isEqualTo(1);
        assertThat(ungeleseneMails(erika)).isEqualTo(1);
        assertThat(service.zusammenfassung(null, erika).recentItems()).extracting(i -> i.id())
                .contains("EMAIL:" + mail.getId());
    }

    @Test
    @DisplayName("Admin liest die Mail – Erika bekommt den Hinweis trotzdem (früher: nicht mehr)")
    void adminLiestErikaSiehtWeiter() {
        service.erledigen(List.of("EMAIL:" + mail.getId()), admin);

        assertThat(ungeleseneMails(admin)).isZero();
        assertThat(ungeleseneMails(erika)).isEqualTo(1);
        // Das gemeinsame Feld heißt nur noch „irgendwer hat sie geöffnet“.
        assertThat(emailRepository.findById(mail.getId())).get().satisfies(e -> assertThat(e.isRead()).isTrue());
    }

    @Test
    @DisplayName("Erika klickt die ganze Spalte weg – nur für sie, der Admin sieht die Mail weiter")
    void spalteErledigenNurFuerSich() {
        int erledigt = service.kategorienErledigen(List.of("EMAILS"), erika);

        assertThat(erledigt).isEqualTo(1);
        assertThat(ungeleseneMails(erika)).isZero();
        assertThat(ungeleseneMails(admin)).isEqualTo(1);
        // Ein zweites Mal ist nichts mehr offen.
        assertThat(service.kategorienErledigen(List.of("EMAILS"), erika)).isZero();
    }

    @Test
    @DisplayName("Neuer Benutzer: vorhandene Mails gelten als gelesen, neue Mails melden sich")
    void neuerBenutzerStartetMitAltenMailsGelesen() {
        FrontendUserProfile neu = benutzer("max.neu", "Max Mustermann", "MN");
        Authentication neuerBenutzer = UsernamePasswordAuthenticationToken.authenticated("max.neu", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        int eingetragen = gelesenRepository.alleMailsAlsGelesen(neu.getId(), EmailDirection.IN, LocalDateTime.now());

        assertThat(eingetragen).isEqualTo(1);
        assertThat(ungeleseneMails(neuerBenutzer)).isZero();
        mail("Neue Anfrage Zaun");
        assertThat(ungeleseneMails(neuerBenutzer)).isEqualTo(1);
        // Zweiter Aufruf trägt nichts doppelt ein.
        assertThat(gelesenRepository.alleMailsAlsGelesen(neu.getId(), EmailDirection.IN, LocalDateTime.now()))
                .isEqualTo(1);
        assertThat(gelesenRepository.alleMailsAlsGelesen(neu.getId(), EmailDirection.IN, LocalDateTime.now()))
                .isZero();
    }
}
