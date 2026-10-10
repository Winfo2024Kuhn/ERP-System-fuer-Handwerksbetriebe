package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Email.UnifiedEmailDto;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.AusgangsmailService;
import org.example.kalkulationsprogramm.service.ContactService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.EmailLieferantVerknuepfungService;
import org.example.kalkulationsprogramm.service.EmailThreadService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.InquiryDetectionService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.service.PostfachVersandService;
import org.example.kalkulationsprogramm.service.SpamBayesService;
import org.example.kalkulationsprogramm.service.SpamFilterService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

/**
 * Sichtbarkeit mit echten Abfragen (H2): Die Listen filtern VOR dem Seitenschnitt, sodass
 * Seiten voll bleiben und keine Mail doppelt oder gar nicht erscheint. Der Controller läuft
 * hier als normale Bean mit echtem Repository und echtem Sichtbarkeits-Service.
 */
@DataJpaTest
@Import({ UnifiedEmailController.class, PostfachSichtbarkeitService.class, EmailThreadService.class })
@TestPropertySource(properties = "file.mail-attachment-dir=target/test-attachments")
class UnifiedEmailSichtbarkeitJpaTest {

    @Autowired private UnifiedEmailController controller;
    /** Spy: „Nicht zugeordnet“ ist eine MySQL-Abfrage (DATE_SUB …), die H2 nicht kann – hier leer. */
    @org.springframework.boot.test.mock.mockito.SpyBean private EmailRepository emailRepository;
    @Autowired private EmailAbsenderRepository postfachRepository;
    @Autowired private FrontendUserProfileRepository benutzerRepository;
    @Autowired private AbteilungRepository abteilungRepository;
    @Autowired private EntityManager entityManager;

    @MockBean private EmailDraftService emailDraftService;
    @MockBean private EmailAutoAssignmentService emailAutoAssignmentService;
    @MockBean private EmailImportService emailImportService;
    @MockBean private EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean private SpamFilterService spamFilterService;
    @MockBean private InquiryDetectionService inquiryDetectionService;
    @MockBean private DateiSpeicherService dateiSpeicherService;
    @MockBean private ContactService contactService;
    @MockBean private SpamBayesService spamBayesService;
    @MockBean private FrontendUserProfileService frontendUserProfileService;
    @MockBean private PostfachService postfachService;
    @MockBean private PostfachVersandService postfachVersandService;
    @MockBean private AusgangsmailService ausgangsmailService;
    @MockBean private SteuerberaterKontaktService steuerberaterKontaktService;
    @MockBean private EmailLieferantVerknuepfungService emailLieferantVerknuepfungService;
    @MockBean private LieferantDokumentZugriffService lieferantDokumentZugriffService;

    private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken("chefin", null,
            AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
    private static final Authentication ERIKA = new UsernamePasswordAuthenticationToken("erika", null,
            AuthorityUtils.createAuthorityList("ROLE_USER"));

    private EmailAbsender info;
    private EmailAbsender max;
    private FrontendUserProfile erika;
    private int minute;

    @BeforeEach
    void daten() {
        org.mockito.Mockito.doReturn(List.of()).when(emailRepository).findUnassigned();
        org.mockito.Mockito.doReturn(0L).when(emailRepository).countUnassigned();
        info = postfach("info@example.com", true, true);
        max = postfach("max@example.com", false, false);
        erika = new FrontendUserProfile();
        erika.setDisplayName("Erika Mustermann");
        erika.setUsername("erika");
        erika = benutzerRepository.save(erika);
        given(frontendUserProfileService.findByUsername("erika")).willReturn(Optional.of(erika));
    }

    private EmailAbsender postfach(String adresse, boolean haupt, boolean fuerAlle) {
        EmailAbsender p = new EmailAbsender();
        p.setEmailAdresse(adresse);
        p.setHauptpostfach(haupt);
        p.setSichtbarFuerAlle(fuerAlle);
        return postfachRepository.save(p);
    }

    /** Jede neue Mail ist eine Minute älter als die vorige – die Liste ist nach Datum absteigend. */
    private Email mail(String betreff, EmailAbsender... postfaecher) {
        Email e = new Email();
        e.setMessageId("<" + betreff.replace(' ', '-') + "@example.org>");
        e.setDirection(EmailDirection.IN);
        e.setSubject(betreff);
        e.setFromAddress("kunde@example.org");
        e.setRecipient("info@example.com");
        e.setSentAt(LocalDateTime.of(2026, 10, 10, 12, 0).minusMinutes(minute++));
        for (EmailAbsender p : postfaecher) {
            e.ordnePostfachZu(p, "INBOX", null);
        }
        return emailRepository.save(e);
    }

    private void speichern() {
        emailRepository.flush();
        entityManager.clear();
    }

    private static List<String> betreffe(List<UnifiedEmailDto> liste) {
        return liste.stream().map(UnifiedEmailDto::getSubject).toList();
    }

    @Test
    void seitenStimmenNachDemFilter() {
        mail("Sichtbar 1", info);
        mail("Fremd 1", max);
        mail("Fremd 2", max);
        mail("Sichtbar 2", info, max);
        mail("Fremd 3", max);
        mail("Sichtbar 3");
        speichern();

        assertThat(betreffe(controller.getInboxEmails(0, 2, ERIKA))).containsExactly("Sichtbar 1", "Sichtbar 2");
        assertThat(betreffe(controller.getInboxEmails(2, 2, ERIKA))).containsExactly("Sichtbar 3");
        assertThat(betreffe(controller.getInboxEmails(0, 100, ADMIN))).hasSize(6);
        assertThat(betreffe(controller.getInboxEmails(1, 2, ADMIN))).containsExactly("Fremd 1", "Fremd 2");
    }

    @Test
    void zaehlerUndSucheNurUeberSichtbare() {
        mail("Angebot Treppe", info);
        mail("Angebot Gelaender", max);
        speichern();

        assertThat(controller.getStats(ERIKA).getInboxTotal()).isEqualTo(1);
        assertThat(controller.getStats(ERIKA).getInboxCount()).isEqualTo(1);
        assertThat(controller.getStats(ADMIN).getInboxTotal()).isEqualTo(2);
        assertThat(betreffe(controller.searchEmails("Angebot", 0, 50, ERIKA))).containsExactly("Angebot Treppe");
        assertThat(betreffe(controller.searchEmails("Angebot", 0, 50, ADMIN))).hasSize(2);
        assertThat(controller.searchEmails(null, 0, 50, ERIKA)).isEmpty();
    }

    @Test
    void freigabeFuerBenutzerMachtPostfachSichtbar() {
        mail("Fremd", max);
        max.getSichtbarFuerBenutzer().add(erika);
        postfachRepository.save(max);
        speichern();

        assertThat(betreffe(controller.getInboxEmails(0, 100, ERIKA))).containsExactly("Fremd");
    }

    @Test
    void freigabeFuerAbteilungMachtPostfachSichtbar() {
        mail("Werkstatt-Post", max);
        Abteilung werkstatt = new Abteilung();
        werkstatt.setName("Werkstatt");
        werkstatt = abteilungRepository.save(werkstatt);
        max.getSichtbarFuerAbteilungen().add(werkstatt);
        postfachRepository.save(max);
        speichern();
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.getAbteilungen().add(werkstatt);
        erika.setMitarbeiter(mitarbeiter);

        assertThat(betreffe(controller.getInboxEmails(0, 100, ERIKA))).containsExactly("Werkstatt-Post");
    }

    @Test
    void eigenesPostfachIstSichtbar() {
        mail("Meine Post", max);
        speichern();
        erika.setEmailAbsender(max);

        assertThat(betreffe(controller.getInboxEmails(0, 100, ERIKA))).containsExactly("Meine Post");
    }

    @Test
    void ohneAnmeldungNurHauptpostfachUndFuerAlle() {
        mail("Info-Post", info);
        mail("Fremd", max);
        speichern();

        assertThat(betreffe(controller.getInboxEmails(0, 100, null))).containsExactly("Info-Post");
    }

    @Test
    void projektReiterBleibtUngefiltert() {
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Treppe Musterstraße");
        projekt.setAnlegedatum(java.time.LocalDate.of(2026, 10, 1));
        projekt.setAuftragsnummer("A-4711");
        projekt.setBruttoPreis(java.math.BigDecimal.ZERO);
        projekt.setBezahlt(false);
        entityManager.persist(projekt);
        Email fremd = mail("Fremde Projekt-Mail", max);
        fremd.setProjekt(projekt);
        emailRepository.save(fremd);
        speichern();

        var antwort = controller.getEmailsByProjekt(projekt.getId(), 50, ERIKA);

        assertThat(antwort.getBody()).extracting(UnifiedEmailDto::getSubject).containsExactly("Fremde Projekt-Mail");
        // Öffnen darf Erika sie (Projekt-Reiter), bearbeiten im E-Mail-Center nicht.
        assertThat(controller.getEmailById(fremd.getId(), ERIKA).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.markAsRead(fremd.getId(), ERIKA).getStatusCode().value()).isEqualTo(404);
    }
}
