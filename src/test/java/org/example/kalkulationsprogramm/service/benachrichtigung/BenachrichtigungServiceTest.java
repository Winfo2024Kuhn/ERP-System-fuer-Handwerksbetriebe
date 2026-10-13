package org.example.kalkulationsprogramm.service.benachrichtigung;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungEintragDto;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungZusammenfassungDto;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
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
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
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
 * Inhalt der Benachrichtigungs-Glocke (früher Tests des NotificationControllers):
 * 1. Selbst gesendete (OUT) E-Mails dürfen nicht in der Glocke gezählt werden,
 *    auch nicht in den Zusatz-Ordnern (Projekte, Angebote, Lieferanten,
 *    Spam, Newsletter), deren Repo-Queries die Direction nicht filtern.
 * 2. Digital angenommene Freigaben sollen 30 Tage lang in der Glocke
 *    erscheinen (vorher nur 7 Tage – Annahmen verschwanden zu schnell).
 *
 * Bewusst kein @SpringBootTest: der Service hat viele Repo-Dependencies,
 * der Spring-Kontext wäre teurer als nötig. Mockito-Lenient-Mode lässt
 * uns nur die zwei jeweils relevanten Repos gezielt stubben; alle anderen
 * Repository-Pfade laufen leer durch (try/catch im Service).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenachrichtigungServiceTest {

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
    @Mock private ZeitbuchungRepository zeitbuchungRepository;

    @Mock org.example.kalkulationsprogramm.repository.MonatsSaldoRepository monatsSaldoRepository;
    @Mock org.example.kalkulationsprogramm.service.MonatsabschlussBerechtigungService monatsabschlussBerechtigungService;
    @Mock org.example.kalkulationsprogramm.service.telefon.TelefonBerechtigungService telefonBerechtigungService;
    @Mock org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService telefonBenachrichtigungService;
    @Mock org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService postfachSichtbarkeitService;
    @Mock org.example.kalkulationsprogramm.service.ZeiterfassungEinstellungService zeiterfassungEinstellungService;
    @Mock org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService zeiterfassungSichtbarkeitService;

    @Mock org.example.kalkulationsprogramm.service.FrontendUserProfileService frontendUserProfileService;
    @Mock BenachrichtigungErledigtService erledigtService;
    @Mock BetriebsstatusService betriebsstatusService;
    @Mock org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Mock org.example.kalkulationsprogramm.service.EmailGelesenService emailGelesenService;

    @InjectMocks
    private BenachrichtigungService service;

    @org.junit.jupiter.api.BeforeEach
    void gelesenWieBisher() {
        org.example.kalkulationsprogramm.service.GelesenTestHilfe.wieGemeinsam(emailGelesenService);
        // Standard: Zugang darf die Zeiterfassung sehen (Urlaubsanträge in der Glocke wie bisher).
        given(zeiterfassungSichtbarkeitService.darfZeiterfassungSehen(org.mockito.ArgumentMatchers.any())).willReturn(true);
    }

    @Test
    void ohneZeiterfassungsRecht_keineUrlaubsantraegeKeineBeendetenZeitenKeinMonatsabschluss() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        given(zeiterfassungSichtbarkeitService.darfZeiterfassungSehen(auth)).willReturn(false);
        given(monatsabschlussBerechtigungService.darfMonatAbschliessen(auth)).willReturn(true);

        var kategorien = service.quellen(1L, auth).stream().map(q -> q.typ()).toList();

        org.assertj.core.api.Assertions.assertThat(kategorien)
                .doesNotContain("URLAUBSANTRAEGE", "ZEITEN_AUTO_BEENDET", "MONATSABSCHLUSS")
                .contains("TERMINE");
    }

    @Test
    void mitZeiterfassungsRecht_urlaubsantraegeUndAutomatischBeendeteZeitenInDerGlocke() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());

        var kategorien = service.quellen(1L, auth).stream().map(q -> q.typ()).toList();

        org.assertj.core.api.Assertions.assertThat(kategorien).contains("URLAUBSANTRAEGE", "ZEITEN_AUTO_BEENDET");
    }

    @Test
    void abschlussHinweisNurAusSessionUndGebundeltMitKalenderlink() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        given(monatsabschlussBerechtigungService.darfMonatAbschliessen(auth)).willReturn(true);
        var month = org.mockito.Mockito.mock(org.example.kalkulationsprogramm.repository.MonatsSaldoRepository.OffenerMonat.class);
        given(month.getJahr()).willReturn(2026); given(month.getMonat()).willReturn(8); given(month.getAnzahl()).willReturn(3);
        given(monatsSaldoRepository.findOffeneAbschlussMonate(java.time.LocalDate.now().withDayOfMonth(1), null)).willReturn(List.of(month));
        var summary = service.zusammenfassung(999L, auth);
        assertThat(summary.categories()).filteredOn(c -> c.type().equals("MONATSABSCHLUSS")).singleElement()
                .satisfies(c -> { assertThat(c.count()).isEqualTo(3); assertThat(c.link()).isEqualTo("/monatsabschluss?jahr=2026&monat=8"); });
        assertThat(summary.recentItems()).filteredOn(c -> c.type().equals("MONATSABSCHLUSS")).hasSize(1);
        org.mockito.Mockito.verify(monatsSaldoRepository).findOffeneAbschlussMonate(java.time.LocalDate.now().withDayOfMonth(1), null);
    }

    @Test
    @DisplayName("Monatsabschluss: Monate vor dem Start der Zeiterfassung werden nicht verlangt")
    void abschlussHinweisErstAbStartDerZeiterfassung() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        var start = java.time.LocalDate.of(2025, 3, 1);
        given(monatsabschlussBerechtigungService.darfMonatAbschliessen(auth)).willReturn(true);
        given(zeiterfassungEinstellungService.ersterAbschlussMonat()).willReturn(start);
        given(monatsSaldoRepository.findOffeneAbschlussMonate(any(), any())).willReturn(List.of());

        var summary = service.zusammenfassung(999L, auth);

        org.mockito.Mockito.verify(monatsSaldoRepository).findOffeneAbschlussMonate(java.time.LocalDate.now().withDayOfMonth(1), start);
        assertThat(summary.categories()).noneMatch(c -> c.type().equals("MONATSABSCHLUSS"));
    }

    @Test
    @DisplayName("Telefon: neue AB-Nachrichten und verpasste Anrufe nur mit Telefon-Recht")
    void telefonSpalteNurMitRecht() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        var zeit = LocalDateTime.of(2026, 9, 29, 8, 14);
        given(telefonBenachrichtigungService.neueSprachnachrichten()).willReturn(List.of(
                new org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService.Eintrag(
                        "SPRACHNACHRICHT", "Mustermann GmbH", "AB Nacht · heute 08:14 · 0:42", zeit, "/telefon/anrufbeantworter?nachricht=3")));
        given(telefonBenachrichtigungService.offeneVerpassteAnrufe()).willReturn(List.of(
                new org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService.Eintrag(
                        "VERPASSTER_ANRUF", "0931 1234567", "Verpasst · heute 08:20", zeit.plusMinutes(6), "/telefon/anrufe?anruf=7"),
                new org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService.Eintrag(
                        "VERPASSTER_ANRUF", "0931 7654321", "Verpasst · heute 08:10", zeit.minusMinutes(4), "/telefon/anrufe?anruf=6")));

        given(telefonBerechtigungService.darfTelefonSehen(auth)).willReturn(false);
        assertThat(service.zusammenfassung(null, auth).categories())
                .noneMatch(c -> c.type().equals("SPRACHNACHRICHTEN") || c.type().equals("VERPASSTE_ANRUFE"));

        given(telefonBerechtigungService.darfTelefonSehen(auth)).willReturn(true);
        var summary = service.zusammenfassung(null, auth);
        assertThat(summary.categories()).filteredOn(c -> c.type().equals("SPRACHNACHRICHTEN")).singleElement()
                .satisfies(c -> { assertThat(c.count()).isEqualTo(1); assertThat(c.link()).isEqualTo("/telefon/anrufbeantworter"); });
        assertThat(summary.categories()).filteredOn(c -> c.type().equals("VERPASSTE_ANRUFE")).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(2));
        assertThat(summary.recentItems()).filteredOn(i -> i.type().equals("VERPASSTER_ANRUF")).hasSize(2);
        assertThat(summary.recentItems()).filteredOn(i -> i.type().equals("SPRACHNACHRICHT")).singleElement()
                .satisfies(i -> assertThat(i.link()).isEqualTo("/telefon/anrufbeantworter?nachricht=3"));
        assertThat(summary.totalCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Telefon-Fehler bricht die Glocke nicht")
    void telefonFehlerIstHarmlos() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        given(telefonBerechtigungService.darfTelefonSehen(auth)).willReturn(true);
        given(telefonBenachrichtigungService.neueSprachnachrichten()).willThrow(new IllegalStateException("DB weg"));
        assertThat(service.zusammenfassung(null, auth).categories()).noneMatch(c -> c.type().equals("SPRACHNACHRICHTEN"));
    }

    @Test
    void freiUebermittelteMitarbeiterIdGibtKeinenAbschlussHinweis() {
        assertThat(service.zusammenfassung(9L, null).categories()).noneMatch(c -> c.type().equals("MONATSABSCHLUSS"));
        org.mockito.Mockito.verifyNoInteractions(monatsSaldoRepository);
    }

    @Test
    @DisplayName("Selbst gesendete (OUT) Projekt-E-Mails zählen NICHT in der Glocke")
    void outboundEmailsAreIgnoredInProjectFolder() {
        Email eingehendUngelesen = email("Anfrage Bauvorhaben",
                "kunde@example.com", EmailDirection.IN, false, EmailZuordnungTyp.PROJEKT);
        Email selbstGesendet = email("Re: Anfrage Bauvorhaben",
                "max.mustermann@firma.example", EmailDirection.OUT, false, EmailZuordnungTyp.PROJEKT);

        given(emailRepository.findProjectEmails())
                .willReturn(List.of(eingehendUngelesen, selbstGesendet));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, null);

        // Genau eine Projekt-Email-Kategorie, Counter = 1 (OUT wurde rausgefiltert).
        assertThat(summary.categories())
                .filteredOn(c -> "EMAILS_PROJECTS".equals(c.type()))
                .singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));

        // Im RecentItem-Stream ist nur die IN-Mail; das Subject der OUT-Mail
        // taucht garantiert nicht auf.
        assertThat(summary.recentItems())
                .extracting(BenachrichtigungEintragDto::title)
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

        given(emailRepository.findSpamOrdner())
                .willReturn(List.of(spamEingehend, spamSelbstGesendet));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, null);

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

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, null);

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

    @Test
    @DisplayName("Automatisch beendete Zeitbuchung erscheint als Prüffall in der Glocke")
    void automatischBeendeteZeitbuchungErscheintInDerGlocke() {
        // REGRESSION 29.07.2026: Der Auto-Stop schrieb geschätzte 20:00-Endezeiten in
        // die Zeitkonten, ohne dass das irgendwo sichtbar war. Die erfundenen Stunden
        // fielen erst am nächsten Morgen zufällig auf.
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(4711L);
        mitarbeiter.setVorname("Max");
        mitarbeiter.setNachname("Mustermann");

        Zeitbuchung buchung = new Zeitbuchung();
        buchung.setId(1903L);
        buchung.setMitarbeiter(mitarbeiter);
        buchung.setStartZeit(LocalDateTime.now().minusDays(1).withHour(16).withMinute(48));
        buchung.setEndeZeit(LocalDateTime.now().minusDays(1).withHour(20).withMinute(0));
        buchung.setAutomatischBeendet(true);

        given(zeitbuchungRepository
                .findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(any(LocalDateTime.class)))
                .willReturn(List.of(buchung));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, null);

        assertThat(summary.categories())
                .filteredOn(c -> "ZEITEN_AUTO_BEENDET".equals(c.type()))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.count()).isEqualTo(1);
                    // Handwerker-Sprache: kein "Auto-Stop", kein "Buchungszeitfenster"
                    assertThat(c.label()).isEqualTo("Zeiten ohne Feierabend");
                    assertThat(c.link()).isEqualTo("/zeitbuchungen");
                });

        assertThat(summary.recentItems())
                .filteredOn(i -> "ZEIT_AUTO_BEENDET".equals(i.type()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.title()).contains("Max Mustermann");
                    assertThat(i.subtitle()).contains("Kein Feierabend gestempelt");
                    assertThat(i.subtitle()).contains("20:00");
                    assertThat(i.link()).isEqualTo("/zeitbuchungen?mitarbeiterId=4711");
                });
    }

    @Test
    @DisplayName("Ohne offene Prüffälle taucht die Zeiterfassungs-Kategorie gar nicht auf")
    void keineKategorieOhneAutomatischBeendeteBuchungen() {
        given(zeitbuchungRepository
                .findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(any(LocalDateTime.class)))
                .willReturn(List.of());

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, null);

        assertThat(summary.categories())
                .noneMatch(c -> "ZEITEN_AUTO_BEENDET".equals(c.type()));
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

    @Test
    @DisplayName("Glocke: Mails aus nicht sichtbaren Postfächern fehlen, fehlende Angaben werden ersetzt")
    void glockeFiltertVerborgeneMailsUndErsetztLeereFelder() {
        Email sichtbar = email("leer", "leer", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        sichtbar.setSubject(null);
        sichtbar.setFromAddress(null);
        sichtbar.setId(10L);
        sichtbar.setSentAt(null);
        Email verborgen = email("Persönlich an Max", "kunde@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        verborgen.setId(20L);
        Email gelesen = email("Gelesen", "kunde@example.org", EmailDirection.IN, true, EmailZuordnungTyp.KEINE);
        gelesen.setId(30L);
        Email nichtZugeordnet = email("Bekannter Kunde", "kunde@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        nichtZugeordnet.setId(40L);
        given(emailRepository.findUnassigned()).willReturn(List.of(nichtZugeordnet));
        given(emailRepository.findInboxFiltered()).willReturn(List.of(sichtbar, verborgen, gelesen, nichtZugeordnet));
        Email geloeschterNewsletter = email("Alt", "news@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        geloeschterNewsletter.setDeletedAt(LocalDateTime.now());
        Email newsletter = email("Neu", "news@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        newsletter.setId(50L);
        given(emailRepository.findNewsletterOrdner()).willReturn(List.of(geloeschterNewsletter, newsletter));
        Email geloeschterSpam = email("Spam alt", "spam@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        geloeschterSpam.setDeletedAt(LocalDateTime.now());
        given(emailRepository.findSpamOrdner()).willReturn(List.of(geloeschterSpam));
        given(postfachSichtbarkeitService.verborgeneEmailIds(any())).willReturn(java.util.Set.of(20L));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, null);

        assertThat(summary.categories()).filteredOn(c -> "EMAILS".equals(c.type())).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));
        assertThat(summary.recentItems()).filteredOn(i -> i.link().startsWith("/emails/inbox/")).singleElement()
                .satisfies(i -> {
                    assertThat(i.title()).isEqualTo("Kein Betreff");
                    assertThat(i.subtitle()).isEqualTo("Von: Unbekannt");
                });
        assertThat(summary.categories()).filteredOn(c -> "EMAILS_NEWSLETTER".equals(c.type())).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));
        assertThat(summary.categories()).noneMatch(c -> "EMAILS_SPAM".equals(c.type()));
    }

    // ── Erledigt-Speicher ─────────────────────────────────────────────────

    private static final org.springframework.security.core.Authentication MAX =
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                    "max", null, List.of());

    private void maxAngemeldet() {
        org.example.kalkulationsprogramm.domain.FrontendUserProfile profil =
                new org.example.kalkulationsprogramm.domain.FrontendUserProfile();
        profil.setId(7L);
        given(frontendUserProfileService.findByUsername("max")).willReturn(java.util.Optional.of(profil));
    }

    private static org.example.kalkulationsprogramm.domain.Urlaubsantrag antrag(long id) {
        org.example.kalkulationsprogramm.domain.Urlaubsantrag a = new org.example.kalkulationsprogramm.domain.Urlaubsantrag();
        a.setId(id);
        a.setTyp(org.example.kalkulationsprogramm.domain.Urlaubsantrag.Typ.URLAUB);
        a.setStatus(org.example.kalkulationsprogramm.domain.Urlaubsantrag.Status.OFFEN);
        return a;
    }

    private static org.example.kalkulationsprogramm.domain.KalenderEintrag termin(long id, String titel) {
        org.example.kalkulationsprogramm.domain.KalenderEintrag t = new org.example.kalkulationsprogramm.domain.KalenderEintrag();
        t.setId(id);
        t.setTitel(titel);
        t.setDatum(java.time.LocalDate.now().plusDays(1));
        t.setStartZeit(java.time.LocalTime.of(10, 0));
        return t;
    }

    @Test
    @DisplayName("Erledigte Einträge fehlen in Liste UND Zähler – auch jenseits der angezeigten drei")
    void erledigteEintraegeZaehlenNicht() {
        maxAngemeldet();
        given(urlaubsantragRepository.findByStatus(org.example.kalkulationsprogramm.domain.Urlaubsantrag.Status.OFFEN))
                .willReturn(List.of(antrag(1), antrag(2), antrag(3), antrag(4)));
        given(erledigtService.erledigteSchluessel(7L)).willReturn(java.util.Set.of("URLAUBSANTRAG:2"));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, MAX);

        assertThat(summary.categories()).filteredOn(c -> "URLAUBSANTRAEGE".equals(c.type())).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(3));
        assertThat(summary.recentItems()).filteredOn(i -> "URLAUBSANTRAG".equals(i.type()))
                .extracting(BenachrichtigungEintragDto::id)
                .containsExactly("URLAUBSANTRAG:1", "URLAUBSANTRAG:3", "URLAUBSANTRAG:4");
    }

    @Test
    @DisplayName("Sind alle Einträge erledigt, verschwindet die Kategorie samt Zähler")
    void alleErledigtKeineKategorie() {
        maxAngemeldet();
        given(urlaubsantragRepository.findByStatus(org.example.kalkulationsprogramm.domain.Urlaubsantrag.Status.OFFEN))
                .willReturn(List.of(antrag(1)));
        given(erledigtService.erledigteSchluessel(7L)).willReturn(java.util.Set.of("URLAUBSANTRAG:1"));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, MAX);

        assertThat(summary.categories()).noneMatch(c -> "URLAUBSANTRAEGE".equals(c.type()));
        assertThat(summary.totalCount()).isZero();
    }

    @Test
    @DisplayName("Termine am selben Tag haben eigene Schlüssel – einen wegklicken lässt den anderen stehen")
    void termineGleichenTagsEigeneIds() {
        maxAngemeldet();
        given(kalenderEintragRepository.findByDatumBetween(any(), any()))
                .willReturn(List.of(termin(1, "Aufmaß Bad"), termin(2, "Abnahme Treppe")));
        given(erledigtService.erledigteSchluessel(7L)).willReturn(java.util.Set.of("TERMIN:1"));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, MAX);

        assertThat(summary.categories()).filteredOn(c -> "TERMINE".equals(c.type())).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));
        assertThat(summary.recentItems()).filteredOn(i -> "TERMIN".equals(i.type())).singleElement()
                .satisfies(i -> {
                    assertThat(i.id()).isEqualTo("TERMIN:2");
                    assertThat(i.title()).isEqualTo("Abnahme Treppe");
                });
    }

    @Test
    @DisplayName("Ohne Anmeldung wird nichts gefiltert – der Erledigt-Speicher bekommt keinen Benutzer")
    void ohneAnmeldungKeinBenutzer() {
        service.zusammenfassung(null, null);
        org.mockito.Mockito.verify(erledigtService).erledigteSchluessel(null);
    }

    @Test
    @DisplayName("Monatsabschluss: erledigter Monat zählt nicht, Link zeigt auf den nächsten offenen")
    void monatsabschlussErledigterMonat() {
        maxAngemeldet();
        given(monatsabschlussBerechtigungService.darfMonatAbschliessen(MAX)).willReturn(true);
        var august = org.mockito.Mockito.mock(org.example.kalkulationsprogramm.repository.MonatsSaldoRepository.OffenerMonat.class);
        given(august.getJahr()).willReturn(2026); given(august.getMonat()).willReturn(8); given(august.getAnzahl()).willReturn(3);
        var september = org.mockito.Mockito.mock(org.example.kalkulationsprogramm.repository.MonatsSaldoRepository.OffenerMonat.class);
        given(september.getJahr()).willReturn(2026); given(september.getMonat()).willReturn(9); given(september.getAnzahl()).willReturn(2);
        given(monatsSaldoRepository.findOffeneAbschlussMonate(any(), any())).willReturn(List.of(august, september));
        given(erledigtService.erledigteSchluessel(7L)).willReturn(java.util.Set.of("MONATSABSCHLUSS:2026-08"));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, MAX);

        assertThat(summary.categories()).filteredOn(c -> "MONATSABSCHLUSS".equals(c.type())).singleElement()
                .satisfies(c -> {
                    assertThat(c.count()).isEqualTo(2);
                    assertThat(c.link()).isEqualTo("/monatsabschluss?jahr=2026&monat=9");
                });
        assertThat(summary.recentItems()).filteredOn(i -> "MONATSABSCHLUSS".equals(i.type()))
                .extracting(BenachrichtigungEintragDto::id).containsExactly("MONATSABSCHLUSS:2026-09");
    }

    @Test
    @DisplayName("Ein kaputter Eintrag blockiert die Glocke nicht – die Kategorie bleibt mit Zähler")
    void kaputterEintragBlockiertNicht() {
        org.example.kalkulationsprogramm.domain.Urlaubsantrag ohneTyp = antrag(5);
        ohneTyp.setTyp(null);
        given(urlaubsantragRepository.findByStatus(org.example.kalkulationsprogramm.domain.Urlaubsantrag.Status.OFFEN))
                .willReturn(List.of(ohneTyp));

        BenachrichtigungZusammenfassungDto summary = service.zusammenfassung(null, null);

        assertThat(summary.categories()).filteredOn(c -> "URLAUBSANTRAEGE".equals(c.type())).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(1));
        assertThat(summary.recentItems()).noneMatch(i -> "URLAUBSANTRAG".equals(i.type()));
    }

    @Test
    @DisplayName("Erledigen: sichtbare Mails werden gelesen, fremde nicht; andere Einträge werden gemerkt")
    void erledigenMarkiertMailsUndMerktRest() {
        maxAngemeldet();
        org.example.kalkulationsprogramm.domain.EmailAbsender info = new org.example.kalkulationsprogramm.domain.EmailAbsender();
        info.setId(1L);
        org.example.kalkulationsprogramm.domain.EmailAbsender fremd = new org.example.kalkulationsprogramm.domain.EmailAbsender();
        fremd.setId(3L);
        Email sichtbar = email("Anfrage Carport", "kunde@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        sichtbar.setId(11L);
        sichtbar.ordnePostfachZu(info, "INBOX", 11L);
        Email verborgen = email("Persönlich an Erika", "kunde@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        verborgen.setId(12L);
        verborgen.ordnePostfachZu(fremd, "INBOX", 12L);
        given(postfachSichtbarkeitService.fuer(MAX))
                .willReturn(new org.example.kalkulationsprogramm.service.PostfachSichtbarkeit(false, java.util.Set.of(1L)));
        given(emailRepository.findAllById(any())).willReturn(List.of(sichtbar, verborgen));

        service.erledigen(List.of("EMAIL:11", "EMAIL:12", "EMAIL:abc", "TERMIN:5"), MAX);

        assertThat(sichtbar.isRead()).isTrue();
        assertThat(sichtbar.getFirstViewedAt()).isNotNull();
        assertThat(verborgen.isRead()).isFalse();
        org.mockito.Mockito.verify(erledigtService).merken(7L, List.of("TERMIN:5"));
    }

    @Test
    void erledigenOhneAnmeldungGehtNicht() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.erledigen(List.of("TERMIN:1"), null))
                .isInstanceOf(BenachrichtigungService.NichtAngemeldetException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.kategorienErledigen(List.of("TERMINE"), MAX))
                .isInstanceOf(BenachrichtigungService.NichtAngemeldetException.class);
        org.mockito.Mockito.verifyNoInteractions(erledigtService);
    }

    @Test
    @DisplayName("Ganze Spalte: ALLE ungelesenen Mails werden gelesen und alle offenen Termine gemerkt")
    void kategorienErledigenNimmtAlleEintraege() {
        maxAngemeldet();
        List<Email> ungelesen = new java.util.ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            Email e = email("Mail " + i, "kunde@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
            e.setId(100 + i);
            ungelesen.add(e);
        }
        given(emailRepository.findInboxFiltered()).willReturn(ungelesen);
        Email spam = email("Gewinnspiel", "spam@example.org", EmailDirection.IN, false, EmailZuordnungTyp.KEINE);
        spam.setId(200L);
        given(emailRepository.findSpamOrdner()).willReturn(List.of(spam));
        given(postfachSichtbarkeitService.verborgeneEmailIds(any())).willReturn(java.util.Set.of());
        given(kalenderEintragRepository.findByDatumBetween(any(), any()))
                .willReturn(List.of(termin(1, "A"), termin(2, "B"), termin(3, "C"), termin(4, "D")));
        given(erledigtService.erledigteSchluessel(7L)).willReturn(java.util.Set.of("TERMIN:4"));

        int anzahl = service.kategorienErledigen(List.of("EMAILS", "TERMINE"), MAX);

        assertThat(anzahl).isEqualTo(8);
        assertThat(ungelesen).allSatisfy(e -> assertThat(e.isRead()).isTrue());
        assertThat(spam.isRead()).as("Spam wurde nicht angefordert").isFalse();
        org.mockito.Mockito.verify(erledigtService).merken(7L, List.of("TERMIN:1", "TERMIN:2", "TERMIN:3"));
    }

    @Test
    @DisplayName("Sicherung & Update: Admins sehen gescheiterte Läufe – ohne Link, mit vollem Text")
    void betriebsmeldungenFuerAdmins() {
        var admin = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        var zeit = LocalDateTime.of(2026, 10, 11, 2, 0, 17);
        given(betriebsstatusService.darfSehen(admin)).willReturn(true);
        given(betriebsstatusService.sicherung()).willReturn(java.util.Optional.of(new BetriebsstatusService.Meldung(
                "SICHERUNG:20261011-020017", "Sicherung nur teilweise", "Externe Festplatte nicht gefunden.\nZeile 2", zeit)));
        given(betriebsstatusService.nachtupdate()).willReturn(java.util.Optional.of(new BetriebsstatusService.Meldung(
                "UPDATE:20261011-030119", "Update fehlgeschlagen", "Alte Version läuft wieder.", zeit.plusHours(1))));

        var summary = service.zusammenfassung(null, admin);

        assertThat(summary.categories()).filteredOn(c -> c.type().equals("SICHERUNG")).singleElement()
                .satisfies(c -> {
                    assertThat(c.count()).isEqualTo(1);
                    assertThat(c.link()).isEmpty();
                    assertThat(c.icon()).isEqualTo("HardDrive");
                });
        assertThat(summary.categories()).anyMatch(c -> c.type().equals("UPDATE"));
        assertThat(summary.recentItems()).filteredOn(i -> i.type().equals("SICHERUNG")).singleElement()
                .satisfies(i -> {
                    assertThat(i.id()).isEqualTo("SICHERUNG:20261011-020017");
                    assertThat(i.subtitle()).isEqualTo("Externe Festplatte nicht gefunden.\nZeile 2");
                    assertThat(i.timestamp()).isEqualTo("2026-10-11T02:00:17");
                    assertThat(i.link()).isEmpty();
                });
    }

    @Test
    @DisplayName("Sicherung & Update: ohne Admin-Recht weder Zähler noch Text")
    void betriebsmeldungenNichtFuerMitarbeiter() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        given(betriebsstatusService.darfSehen(auth)).willReturn(false);

        var summary = service.zusammenfassung(null, auth);

        assertThat(summary.categories()).noneMatch(c -> c.type().equals("SICHERUNG") || c.type().equals("UPDATE"));
        org.mockito.Mockito.verify(betriebsstatusService, org.mockito.Mockito.never()).sicherung();
        org.mockito.Mockito.verify(betriebsstatusService, org.mockito.Mockito.never()).nachtupdate();
    }

    @Test
    @DisplayName("Erledigte Sicherungs-Meldung fällt aus Zähler und Liste")
    void erledigteBetriebsmeldungFaelltWeg() {
        maxAngemeldet();
        given(betriebsstatusService.darfSehen(MAX)).willReturn(true);
        given(betriebsstatusService.sicherung()).willReturn(java.util.Optional.of(new BetriebsstatusService.Meldung(
                "SICHERUNG:20261011-020017", "Sicherung fehlgeschlagen", "Text", LocalDateTime.of(2026, 10, 11, 2, 0))));
        given(betriebsstatusService.nachtupdate()).willReturn(java.util.Optional.empty());
        given(erledigtService.erledigteSchluessel(7L)).willReturn(java.util.Set.of("SICHERUNG:20261011-020017"));

        var summary = service.zusammenfassung(null, MAX);

        assertThat(summary.categories()).noneMatch(c -> c.type().equals("SICHERUNG"));
        assertThat(summary.recentItems()).noneMatch(i -> i.type().equals("SICHERUNG"));
    }

    @Test
    @DisplayName("Spalte „Sicherung & Updates“ erledigen: Nicht-Admins erledigen nichts und lesen keine Statusdatei")
    void betriebsmeldungenErledigenNurFuerAdmins() {
        maxAngemeldet();
        given(betriebsstatusService.darfSehen(MAX)).willReturn(false);

        assertThat(service.kategorienErledigen(List.of("SICHERUNG", "UPDATE"), MAX)).isZero();

        org.mockito.Mockito.verify(betriebsstatusService, org.mockito.Mockito.never()).sicherung();
        org.mockito.Mockito.verify(betriebsstatusService, org.mockito.Mockito.never()).nachtupdate();
        org.mockito.Mockito.verify(erledigtService).merken(7L, List.of());
    }

    @Test
    @DisplayName("Spalte „Sicherung & Updates“ erledigen: Admin merkt sich genau die offenen Meldungen")
    void betriebsmeldungenErledigenFuerAdmins() {
        maxAngemeldet();
        given(betriebsstatusService.darfSehen(MAX)).willReturn(true);
        given(betriebsstatusService.sicherung()).willReturn(java.util.Optional.of(new BetriebsstatusService.Meldung(
                "SICHERUNG:ALT-20261009-020000", "Seit über einem Tag keine Sicherung", "Text",
                LocalDateTime.of(2026, 10, 9, 2, 0))));
        given(betriebsstatusService.nachtupdate()).willReturn(java.util.Optional.empty());

        assertThat(service.kategorienErledigen(List.of("SICHERUNG", "UPDATE"), MAX)).isEqualTo(1);

        org.mockito.Mockito.verify(erledigtService).merken(7L, List.of("SICHERUNG:ALT-20261009-020000"));
    }

    @Test
    @DisplayName("Seite (alle=true): alle Einträge je Kategorie statt nur der jüngsten drei")
    void seiteBekommtAlleEintraege() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        List<org.example.kalkulationsprogramm.domain.KalenderEintrag> termine = new java.util.ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            termine.add(termin(i, "Termin " + i));
        }
        given(kalenderEintragRepository.findByDatumBetween(any(), any())).willReturn(termine);

        var glocke = service.zusammenfassung(null, auth);
        var seite = service.zusammenfassung(null, auth, true);

        assertThat(glocke.recentItems()).filteredOn(i -> i.type().equals("TERMIN")).hasSize(3);
        assertThat(seite.recentItems()).filteredOn(i -> i.type().equals("TERMIN")).hasSize(8);
        assertThat(seite.categories()).filteredOn(c -> c.type().equals("TERMINE")).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(8));
    }

    @Test
    @DisplayName("Seite (alle=true): höchstens 100 Einträge je Kategorie, Zähler nennt trotzdem alle")
    void seiteBegrenztJeKategorie() {
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("session", null, List.of());
        List<org.example.kalkulationsprogramm.domain.KalenderEintrag> termine = new java.util.ArrayList<>();
        for (long i = 1; i <= 130; i++) {
            termine.add(termin(i, "Termin " + i));
        }
        given(kalenderEintragRepository.findByDatumBetween(any(), any())).willReturn(termine);

        var seite = service.zusammenfassung(null, auth, true);

        assertThat(seite.recentItems()).filteredOn(i -> i.type().equals("TERMIN"))
                .hasSize(BenachrichtigungService.SEITE_EINTRAEGE_JE_KATEGORIE);
        assertThat(seite.categories()).filteredOn(c -> c.type().equals("TERMINE")).singleElement()
                .satisfies(c -> assertThat(c.count()).isEqualTo(130));
        assertThat(service.zusammenfassung(null, auth).recentItems()).hasSizeLessThanOrEqualTo(60);
    }

    @Test
    void telefonSchluesselAusDemLink() {
        var anruf = new org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService.Eintrag(
                "VERPASSTER_ANRUF", "0931 1234567", "Verpasst", LocalDateTime.now(), "/telefon/anrufe?anruf=7");
        var ohneId = new org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService.Eintrag(
                "SPRACHNACHRICHT", "Max Mustermann", "AB", LocalDateTime.now(), "/telefon/anrufbeantworter");
        assertThat(BenachrichtigungService.telefonSchluessel(anruf)).isEqualTo("VERPASSTER_ANRUF:7");
        assertThat(BenachrichtigungService.telefonSchluessel(ohneId)).matches("SPRACHNACHRICHT:[0-9a-f]+");
    }
}
