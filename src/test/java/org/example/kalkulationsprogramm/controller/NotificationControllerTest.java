package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.controller.NotificationController.NotificationSummaryDto;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.UrlaubsantragRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit-Tests für die zwei Bugfixes im NotificationController:
 * 1. Selbst gesendete (OUT) E-Mails dürfen nicht in der Glocke gezählt werden,
 *    auch nicht in den Zusatz-Ordnern (Projekte, Angebote, Lieferanten,
 *    Spam, Newsletter), deren Repo-Queries die Direction nicht filtern.
 * 2. Digital angenommene Freigaben sollen 30 Tage lang in der Glocke
 *    erscheinen (vorher nur 7 Tage – Annahmen verschwanden zu schnell).
 *
 * Bewusst kein @SpringBootTest: der Controller hat 12 Repo-Dependencies,
 * der Spring-Kontext wäre teurer als nötig. Mockito-Lenient-Mode lässt
 * uns nur die zwei jeweils relevanten Repos gezielt stubben; alle anderen
 * Repository-Pfade laufen leer durch (try/catch im Controller).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationControllerTest {

    @Mock private EmailRepository emailRepository;
    @Mock private UrlaubsantragRepository urlaubsantragRepository;
    @Mock private ProjektNotizRepository projektNotizRepository;
    @Mock private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @Mock private ProjektDokumentRepository projektDokumentRepository;
    @Mock private KalenderEintragRepository kalenderEintragRepository;
    @Mock private LieferantDokumentRepository lieferantDokumentRepository;
    @Mock private LieferantReklamationRepository lieferantReklamationRepository;
    @Mock private DokumentFreigabeRepository dokumentFreigabeRepository;
    @Mock private AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    @Mock private AnfrageDokumentRepository anfrageDokumentRepository;
    @Mock private AnfrageRepository anfrageRepository;

    @InjectMocks
    private NotificationController controller;

    @Test
    @DisplayName("Selbst gesendete (OUT) Projekt-E-Mails zählen NICHT in der Glocke")
    void outboundEmailsAreIgnoredInProjectFolder() {
        Email eingehendUngelesen = email("Anfrage Bauvorhaben",
                "kunde@example.com", EmailDirection.IN, false, EmailZuordnungTyp.PROJEKT);
        Email selbstGesendet = email("Re: Anfrage Bauvorhaben",
                "max.mustermann@firma.example", EmailDirection.OUT, false, EmailZuordnungTyp.PROJEKT);

        given(emailRepository.findProjectEmails())
                .willReturn(List.of(eingehendUngelesen, selbstGesendet));

        NotificationSummaryDto summary = controller.getSummary(null);

        // Genau eine Projekt-Email-Kategorie, Counter = 1 (OUT wurde rausgefiltert).
        assertThat(summary.categories())
                .filteredOn(c -> "EMAILS_PROJECTS".equals(c.type()))
                .singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));

        // Im RecentItem-Stream ist nur die IN-Mail; das Subject der OUT-Mail
        // taucht garantiert nicht auf.
        assertThat(summary.recentItems())
                .extracting(NotificationController.RecentItemDto::title)
                .doesNotContain("Re: Anfrage Bauvorhaben");
    }

    @Test
    @DisplayName("Spam-Ordner: OUT-Mails zählen nicht (eigene Antworten in Spam dürfen nicht klingeln)")
    void outboundEmailsAreIgnoredInSpamFolder() {
        Email spamEingehend = email("Gewinnspiel", "spam@example.com",
                EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        spamEingehend.setSpam(true);
        Email spamSelbstGesendet = email("Antwort an Spammer", "max.mustermann@firma.example",
                EmailDirection.OUT, false, EmailZuordnungTyp.KEINE);
        spamSelbstGesendet.setSpam(true);

        given(emailRepository.findSpam())
                .willReturn(List.of(spamEingehend, spamSelbstGesendet));

        NotificationSummaryDto summary = controller.getSummary(null);

        assertThat(summary.categories())
                .filteredOn(c -> "EMAILS_SPAM".equals(c.type()))
                .singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));
    }

    @Test
    @DisplayName("Annahme vor 20 Tagen erscheint im Notification-Center (30-Tage-Fenster, vorher 7)")
    void freigabeAlterAls7TageNochSichtbar() {
        DokumentFreigabe freigabe = new DokumentFreigabe();
        freigabe.setStatus(FreigabeStatus.ACCEPTED);
        freigabe.setAkzeptiertAm(LocalDateTime.now().minusDays(20));
        freigabe.setKundeName("Max Mustermann");
        freigabe.setDokumentNummer("AB-2026-0042");
        freigabe.setDokumentArt("Auftragsbestätigung");

        given(dokumentFreigabeRepository.findKuerzlichAkzeptiert(any(LocalDateTime.class)))
                .willReturn(List.of(freigabe));

        NotificationSummaryDto summary = controller.getSummary(null);

        assertThat(summary.categories())
                .filteredOn(c -> "FREIGABEN_ANGENOMMEN".equals(c.type()))
                .singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));

        assertThat(summary.recentItems())
                .filteredOn(i -> "FREIGABE_ANGENOMMEN".equals(i.type()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.title()).contains("AB-2026-0042");
                    assertThat(i.subtitle()).contains("Max Mustermann");
                });
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Email email(String subject, String from, EmailDirection direction,
                        boolean read, EmailZuordnungTyp zuordnung) {
        Email e = new Email();
        e.setMessageId("msg-" + System.nanoTime() + "-" + subject.hashCode());
        e.setSubject(subject);
        e.setFromAddress(from);
        e.setDirection(direction);
        e.setRead(read);
        e.setZuordnungTyp(zuordnung);
        e.setSentAt(LocalDateTime.now().minusHours(1));
        return e;
    }
}
