package org.example.kalkulationsprogramm.service.benachrichtigung;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.LieferantReklamation;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ReklamationStatus;
import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungEintragDto;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungKategorieDto;
import org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungZusammenfassungDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository;
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
import org.example.kalkulationsprogramm.service.AnfrageFunnelService;
import org.example.kalkulationsprogramm.service.EmailGelesenService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.GelesenStand;
import org.example.kalkulationsprogramm.service.MonatsabschlussBerechtigungService;
import org.example.kalkulationsprogramm.service.ZeiterfassungSichtbarkeitService;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.example.kalkulationsprogramm.service.ZeiterfassungEinstellungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBenachrichtigungService;
import org.example.kalkulationsprogramm.service.telefon.TelefonBerechtigungService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

/**
 * Inhalt der Benachrichtigungs-Glocke.
 *
 * <p>Jede Quelle (Webseiten-Anfragen, ungelesene Mails, offene Anträge …) liefert ALLE ihre
 * Einträge mit einem stabilen Schlüssel. Was der Benutzer erledigt hat, fällt heraus, bevor
 * gezählt wird – Zähler und Liste meinen so immer dieselben Einträge. Angezeigt werden je
 * Kategorie nur die jüngsten paar.
 *
 * <p>Erledigen heißt bei Mails: für diesen Benutzer als gelesen markieren (das E-Mail-Center zeigt
 * dann dasselbe; „gelesen“ gilt pro Person, siehe {@link EmailGelesenService}). Alles andere merkt
 * sich {@link BenachrichtigungErledigtService} pro Benutzer.
 *
 * <p>Jede Quelle läuft in einer eigenen Transaktion: Wirft eine (auch tief in einem anderen
 * transaktionalen Service), rollt nur sie zurück – eine gemeinsame Transaktion würde sonst als
 * "nur noch zurückrollen" markiert und die ganze Glocke mit 500 abbrechen.
 */
@Service
@RequiredArgsConstructor
public class BenachrichtigungService {

    /** Datum in Handwerker-Schreibweise: "29.07.2026". */
    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    /** Uhrzeit ohne Sekunden: "20:00". */
    private static final DateTimeFormatter UHRZEIT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    /** So viele Einträge gehen höchstens an die Glocke (Kurzfassung ohne {@code alle}). */
    private static final int MAX_EINTRAEGE = 60;
    /** Seite „Benachrichtigungen“: so viele Einträge je Kategorie; der Zähler nennt trotzdem alle. */
    static final int SEITE_EINTRAEGE_JE_KATEGORIE = 100;

    /** Alle Kategorien, die die Glocke kennt (für die Prüfung beim Erledigen ganzer Spalten). */
    public static final Set<String> KATEGORIEN = Set.of(
            "ANFRAGEN_WEBSEITE", "EMAILS", "EMAILS_PROJECTS", "EMAILS_OFFERS", "EMAILS_SUPPLIERS",
            "EMAILS_SPAM", "EMAILS_NEWSLETTER", "URLAUBSANTRAEGE", "BAUTAGEBUCH", "EINGANG_FAELLIG",
            "AUSGANG_UEBERFAELLIG", "RECHNUNGEN", "TERMINE", "LIEFERSCHEINE", "REKLAMATIONEN",
            "FREIGABEN_ANGENOMMEN", "ZEITEN_AUTO_BEENDET", "MONATSABSCHLUSS", "SPRACHNACHRICHTEN",
            "VERPASSTE_ANRUFE", "SICHERUNG", "UPDATE");

    private static final String EMAIL_ART = "EMAIL";

    private final EmailRepository emailRepository;
    private final UrlaubsantragRepository urlaubsantragRepository;
    private final ProjektNotizRepository projektNotizRepository;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final KalenderEintragRepository kalenderEintragRepository;
    private final LieferantDokumentRepository lieferantDokumentRepository;
    private final LieferantReklamationRepository lieferantReklamationRepository;
    private final DokumentFreigabeRepository dokumentFreigabeRepository;
    private final AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    private final AnfrageDokumentRepository anfrageDokumentRepository;
    private final AnfrageRepository anfrageRepository;
    private final ZeitbuchungRepository zeitbuchungRepository;
    private final MonatsSaldoRepository monatsSaldoRepository;
    private final MonatsabschlussBerechtigungService monatsabschlussBerechtigungService;
    private final ZeiterfassungSichtbarkeitService zeiterfassungSichtbarkeitService;
    private final TelefonBerechtigungService telefonBerechtigungService;
    private final TelefonBenachrichtigungService telefonBenachrichtigungService;
    private final PostfachSichtbarkeitService postfachSichtbarkeitService;
    private final EmailGelesenService emailGelesenService;
    private final ZeiterfassungEinstellungService zeiterfassungEinstellungService;
    private final FrontendUserProfileService frontendUserProfileService;
    private final BenachrichtigungErledigtService erledigtService;
    private final BetriebsstatusService betriebsstatusService;
    private final PlatformTransactionManager transactionManager;

    /** Der Benutzer ist nicht angemeldet bzw. hat kein Profil – erledigen geht dann nicht. */
    public static class NichtAngemeldetException extends RuntimeException {
        public NichtAngemeldetException() {
            super("Bitte erneut anmelden.");
        }
    }

    /**
     * Eine Kategorie der Glocke mit allen ihren Einträgen (schon sortiert, jüngste zuerst).
     *
     * @param gewicht   wie viel ein Eintrag zum Zähler beiträgt (Monatsabschluss: Mitarbeiter je Monat)
     * @param link      Link der Kategorie, abhängig von den noch offenen Einträgen
     * @param mails     Einträge sind {@link Email}s – erledigen heißt dann: als gelesen markieren
     */
    record Quelle<T>(String typ, String label, String icon, Function<List<T>, String> link,
            List<T> eintraege, Function<T, String> schluessel, Function<T, BenachrichtigungEintragDto> anzeige,
            int anzeigeLimit, ToIntFunction<T> gewicht, boolean mails) {

        static <T> Quelle<T> von(String typ, String label, String icon, String link, List<T> eintraege,
                Function<T, String> schluessel, Function<T, BenachrichtigungEintragDto> anzeige, int anzeigeLimit) {
            return new Quelle<>(typ, label, icon, offen -> link, eintraege, schluessel, anzeige, anzeigeLimit,
                    e -> 1, false);
        }
    }

    /** Eine Quelle, die erst bei Bedarf (in ihrer eigenen Transaktion) geladen wird. */
    record QuellenAufruf(String typ, Supplier<Quelle<?>> laden) {
    }

    /** Ergebnis einer Quelle für die Glocke: Kategorie mit Zähler und die angezeigten Einträge. */
    record Teil(BenachrichtigungKategorieDto kategorie, List<BenachrichtigungEintragDto> eintraege) {
    }

    // ═══════════════════════════════════════════════════════════════
    // ZUSAMMENFASSUNG
    // ═══════════════════════════════════════════════════════════════

    /** Kurzfassung für die Glocke: je Kategorie nur die jüngsten Einträge. */
    public BenachrichtigungZusammenfassungDto zusammenfassung(Long mitarbeiterId, Authentication authentication) {
        return zusammenfassung(mitarbeiterId, authentication, false);
    }

    /**
     * @param alle {@code true} für die Seite „Benachrichtigungen“: je Kategorie bis zu
     *             {@value #SEITE_EINTRAEGE_JE_KATEGORIE} Einträge und keine Gesamtgrenze
     */
    public BenachrichtigungZusammenfassungDto zusammenfassung(Long mitarbeiterId, Authentication authentication,
            boolean alle) {
        Set<String> erledigt = erledigtService.erledigteSchluessel(benutzerId(authentication).orElse(null));
        List<BenachrichtigungKategorieDto> categories = new ArrayList<>();
        List<BenachrichtigungEintragDto> recentItems = new ArrayList<>();
        for (QuellenAufruf aufruf : quellen(mitarbeiterId, authentication)) {
            inEigenerTransaktion(true, () -> teil(aufruf.laden().get(), erledigt, alle)).ifPresent(teil -> {
                categories.add(teil.kategorie());
                recentItems.addAll(teil.eintraege());
            });
        }

        int totalCount = categories.stream().mapToInt(BenachrichtigungKategorieDto::count).sum();
        recentItems.sort(Comparator.comparing(BenachrichtigungEintragDto::timestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));
        // Limit auf 60 – Items werden im Frontend pro Gruppe gerendert, ein zu kleines Limit
        // würde komplette Gruppen leerfegen (z.B. fielen FREIGABE_ANGENOMMEN-Items raus, wenn
        // 6 Mail-Ordner viele Treffer haben).
        List<BenachrichtigungEintragDto> begrenzt = !alle && recentItems.size() > MAX_EINTRAEGE
                ? new ArrayList<>(recentItems.subList(0, MAX_EINTRAEGE))
                : recentItems;
        return new BenachrichtigungZusammenfassungDto(totalCount, categories, begrenzt);
    }

    /** Nicht erledigte Einträge einer Quelle; {@code null}, wenn nichts offen ist. */
    private static <T> Teil teil(Quelle<T> quelle, Set<String> erledigt, boolean alle) {
        if (quelle == null) {
            return null;
        }
        List<T> offen = quelle.eintraege().stream()
                .filter(e -> !erledigt.contains(quelle.schluessel().apply(e)))
                .toList();
        int anzahl = offen.stream().mapToInt(quelle.gewicht()).sum();
        if (offen.isEmpty() || anzahl <= 0) {
            return null;
        }
        List<BenachrichtigungEintragDto> eintraege = new ArrayList<>();
        int limit = alle ? Math.max(quelle.anzeigeLimit(), SEITE_EINTRAEGE_JE_KATEGORIE) : quelle.anzeigeLimit();
        offen.stream().limit(limit).forEach(e -> {
            try {
                eintraege.add(quelle.anzeige().apply(e));
            } catch (RuntimeException ignored) {
                /* Ein kaputter Eintrag darf die Glocke nicht blockieren – die Kategorie bleibt klickbar */
            }
        });
        return new Teil(new BenachrichtigungKategorieDto(quelle.typ(), quelle.label(), anzahl, quelle.icon(),
                quelle.link().apply(offen)), eintraege);
    }

    /**
     * Führt die Arbeit einer Quelle in einer eigenen Transaktion aus. Fehler bleiben bei der Quelle:
     * Ergebnis leer, die übrigen Quellen laufen weiter.
     */
    private <R> Optional<R> inEigenerTransaktion(boolean nurLesen, Supplier<R> arbeit) {
        TransactionTemplate vorlage = schreibTransaktion();
        vorlage.setReadOnly(nurLesen);
        try {
            return Optional.ofNullable(vorlage.execute(status -> arbeit.get()));
        } catch (RuntimeException ignored) {
            /* Eine Quelle darf das Notification-Center nicht blockieren */
            return Optional.empty();
        }
    }

    /**
     * Neue, eigene Transaktion (REQUIRES_NEW). Wird {@code zusammenfassung} einmal aus einer
     * laufenden Transaktion aufgerufen, belegt jede Quelle kurz eine zweite DB-Verbindung.
     */
    private TransactionTemplate schreibTransaktion() {
        TransactionTemplate vorlage = new TransactionTemplate(transactionManager);
        vorlage.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return vorlage;
    }

    // ═══════════════════════════════════════════════════════════════
    // ERLEDIGEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Einzelne Einträge erledigen. Mails werden für den Benutzer als gelesen markiert (nur, wenn er
     * sie sieht), alles andere wird für ihn gemerkt. Bewusst ohne gemeinsame Transaktion:
     * Mails und Merker speichern je für sich, ein Konflikt beim Merken rollt das Lesen nicht zurück.
     */
    public void erledigen(Collection<String> schluessel, Authentication authentication) {
        Long benutzerId = benutzerId(authentication).orElseThrow(NichtAngemeldetException::new);
        Set<String> eindeutig = new LinkedHashSet<>(schluessel);
        Set<Long> mailIds = new HashSet<>();
        List<String> merken = new ArrayList<>();
        for (String s : eindeutig) {
            Long mailId = mailId(s);
            if (mailId != null) {
                mailIds.add(mailId);
            } else if (!s.startsWith(EMAIL_ART + ":")) {
                merken.add(s);
            }
        }
        if (!mailIds.isEmpty()) {
            // Laden, Sichtbarkeit prüfen (lädt die Postfach-Zuordnungen nach) und Lesen in einer
            // Transaktion – sonst sind die Mails losgelöst und das Speichern überschriebe parallele
            // Änderungen (IMAP-Abgleich, Spam-Erkennung) an derselben Zeile.
            PostfachSichtbarkeit sicht = postfachSichtbarkeitService.fuer(authentication);
            schreibTransaktion().executeWithoutResult(status -> emailGelesenService.alsGelesenMarkieren(
                    authentication,
                    emailRepository.findAllById(mailIds).stream().filter(sicht::siehtEmail).toList()));
        }
        erledigtService.merken(benutzerId, merken);
    }

    /**
     * Ganze Kategorien erledigen – alle ihre Einträge, nicht nur die angezeigten. Mail-Kategorien:
     * alle für den Benutzer ungelesenen Mails des Ordners werden für ihn gelesen.
     *
     * @return wie viele Einträge erledigt wurden
     */
    public int kategorienErledigen(Collection<String> kategorien, Authentication authentication) {
        Long benutzerId = benutzerId(authentication).orElseThrow(NichtAngemeldetException::new);
        Set<String> gewuenscht = new HashSet<>(kategorien);
        Set<String> erledigt = erledigtService.erledigteSchluessel(benutzerId);
        int anzahl = 0;
        List<String> merken = new ArrayList<>();
        // Ohne Mitarbeiter-Filter: die Termine aller Mitarbeiter sind eine Obermenge dessen,
        // was der Benutzer in der Glocke gesehen hat.
        for (QuellenAufruf aufruf : quellen(null, authentication)) {
            if (!gewuenscht.contains(aufruf.typ())) {
                continue;
            }
            // Mails werden in derselben Transaktion gelesen, in der sie geladen wurden.
            List<String> offen = inEigenerTransaktion(false, () -> {
                Quelle<?> quelle = aufruf.laden().get();
                if (quelle == null) {
                    return List.<String>of();
                }
                if (quelle.mails()) {
                    // Gezählt wird, was wirklich gelesen wurde – eine Mail in zwei Ordnern nur einmal.
                    return emailGelesenService.alsGelesenMarkieren(authentication,
                            quelle.eintraege().stream().map(Email.class::cast).toList())
                            .stream().map(e -> EMAIL_ART + ":" + e.getId()).toList();
                }
                return offeneSchluessel(quelle, erledigt);
            }).orElse(List.of());
            anzahl += offen.size();
            offen.stream().filter(s -> !s.startsWith(EMAIL_ART + ":")).forEach(merken::add);
        }
        erledigtService.merken(benutzerId, merken);
        return anzahl;
    }

    private static <T> List<String> offeneSchluessel(Quelle<T> quelle, Set<String> erledigt) {
        return quelle.eintraege().stream()
                .map(quelle.schluessel())
                .filter(s -> !erledigt.contains(s))
                .toList();
    }

    private static Long mailId(String schluessel) {
        if (!schluessel.startsWith(EMAIL_ART + ":")) {
            return null;
        }
        try {
            long id = Long.parseLong(schluessel.substring(EMAIL_ART.length() + 1));
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Angemeldeter Benutzer – ausschließlich aus der Anmeldung, nie aus einem Parameter. */
    private Optional<Long> benutzerId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }
        return frontendUserProfileService.findByUsername(authentication.getName()).map(FrontendUserProfile::getId);
    }

    // ═══════════════════════════════════════════════════════════════
    // QUELLEN
    // ═══════════════════════════════════════════════════════════════

    /**
     * Alle Quellen in Anzeige-Reihenfolge – noch nicht geladen. Geladen wird jede einzeln in ihrer
     * eigenen Transaktion, damit eine fehlerhafte Quelle die Glocke nie blockiert.
     */
    List<QuellenAufruf> quellen(Long mitarbeiterId, Authentication authentication) {
        LocalDate heute = LocalDate.now();
        List<QuellenAufruf> quellen = new ArrayList<>();
        // Gescheiterte Sicherung / gescheitertes Update – betrifft den ganzen Betrieb, nur für Admins.
        if (betriebsstatusService.darfSehen(authentication)) {
            quellen.add(new QuellenAufruf("SICHERUNG", () -> betriebsmeldung("SICHERUNG", "Sicherung", "HardDrive",
                    betriebsstatusService.sicherung())));
            quellen.add(new QuellenAufruf("UPDATE", () -> betriebsmeldung("UPDATE", "Update", "RefreshCw",
                    betriebsstatusService.nachtupdate())));
        }
        quellen.add(new QuellenAufruf("ANFRAGEN_WEBSEITE", this::webseitenAnfragen));

        // Für diesen Benutzer ungelesene E-Mails – nur aus Postfächern, die er sieht (Admin: alle).
        // Lässt sich das nicht ermitteln, gibt es keine Mail-Hinweise (lieber nichts zeigen als
        // Betreff und Absender aus fremden Postfächern).
        Predicate<Email> ungelesen = ungeleseneSichtbareMails(authentication);
        if (ungelesen != null) {
            quellen.add(new QuellenAufruf("EMAILS", () -> posteingang(ungelesen)));
            quellen.add(new QuellenAufruf("EMAILS_PROJECTS", () -> mailOrdner(emailRepository.findProjectEmails(),
                    ungelesen, "EMAILS_PROJECTS", "Ungelesene Projekt-E-Mails", "projects")));
            quellen.add(new QuellenAufruf("EMAILS_OFFERS", () -> mailOrdner(emailRepository.findAnfrageEmails(),
                    ungelesen, "EMAILS_OFFERS", "Ungelesene Angebots-E-Mails", "offers")));
            quellen.add(new QuellenAufruf("EMAILS_SUPPLIERS", () -> mailOrdner(emailRepository.findLieferantEmails(),
                    ungelesen, "EMAILS_SUPPLIERS", "Ungelesene Lieferanten-E-Mails", "suppliers")));
            quellen.add(new QuellenAufruf("EMAILS_SPAM", () -> mailOrdner(emailRepository.findSpamOrdner(),
                    ungelesen, "EMAILS_SPAM", "Ungelesene Spam-E-Mails", "spam")));
            quellen.add(new QuellenAufruf("EMAILS_NEWSLETTER", () -> mailOrdner(emailRepository.findNewsletterOrdner(),
                    ungelesen, "EMAILS_NEWSLETTER", "Ungelesene Newsletter", "newsletter")));
        }

        // Urlaubsanträge und automatisch beendete Zeiten gehören zur Zeiterfassung – nur mit deren Recht.
        boolean zeiterfassung = zeiterfassungSichtbarkeitService.darfZeiterfassungSehen(authentication);
        if (zeiterfassung) {
            quellen.add(new QuellenAufruf("URLAUBSANTRAEGE", this::urlaubsantraege));
        }
        quellen.add(new QuellenAufruf("BAUTAGEBUCH", this::bautagebuch));
        quellen.add(new QuellenAufruf("EINGANG_FAELLIG", () -> eingangsrechnungenFaellig(heute)));
        quellen.add(new QuellenAufruf("AUSGANG_UEBERFAELLIG", () -> ausgangsrechnungenUeberfaellig(heute)));
        quellen.add(new QuellenAufruf("RECHNUNGEN", () -> neueLieferantenrechnungen(heute)));
        quellen.add(new QuellenAufruf("TERMINE", () -> termine(mitarbeiterId, heute)));
        quellen.add(new QuellenAufruf("LIEFERSCHEINE", this::lieferscheine));
        quellen.add(new QuellenAufruf("REKLAMATIONEN", this::reklamationen));
        quellen.add(new QuellenAufruf("FREIGABEN_ANGENOMMEN", this::freigaben));
        if (zeiterfassung) {
            quellen.add(new QuellenAufruf("ZEITEN_AUTO_BEENDET", this::zeitenAutomatischBeendet));
        }

        // Only the authenticated session decides who receives closure reminders. Ohne Zeiterfassungs-Recht
        // ist die Monatsabschluss-Seite gesperrt – dann kein Hinweis, der ins Leere führt.
        if (zeiterfassung && monatsabschlussBerechtigungService.darfMonatAbschliessen(authentication)) {
            quellen.add(new QuellenAufruf("MONATSABSCHLUSS", () -> monatsabschluss(heute)));
        }
        // Telefon: nur für Benutzer mit dem Abteilungs-Recht "Anrufe & Anrufbeantworter".
        if (telefonBerechtigungService.darfTelefonSehen(authentication)) {
            quellen.add(new QuellenAufruf("SPRACHNACHRICHTEN", () -> telefon("SPRACHNACHRICHTEN",
                    "Neue Nachrichten auf dem Anrufbeantworter", "Voicemail", "/telefon/anrufbeantworter",
                    telefonBenachrichtigungService.neueSprachnachrichten())));
            quellen.add(new QuellenAufruf("VERPASSTE_ANRUFE", () -> telefon("VERPASSTE_ANRUFE", "Verpasste Anrufe",
                    "PhoneMissed", "/telefon/anrufe?offen=1", telefonBenachrichtigungService.offeneVerpassteAnrufe())));
        }
        return quellen;
    }

    /**
     * Meldung von Sicherung oder Nachtupdate. Ohne Link: Es gibt keine Seite, auf der man das Problem
     * beheben kann – der Text sagt, was zu tun ist.
     */
    private static Quelle<?> betriebsmeldung(String typ, String label, String icon,
            Optional<BetriebsstatusService.Meldung> meldung) {
        return Quelle.von(typ, label, icon, "", meldung.stream().toList(),
                BetriebsstatusService.Meldung::schluessel,
                m -> new BenachrichtigungEintragDto(m.schluessel(), typ, m.titel(), m.text(),
                        m.zeitpunkt().toString(), ""),
                1);
    }

    /** Webseiten-Anfragen (Funnel) – immer zuoberst. Umgewandelte/abgeschlossene fallen raus. */
    private Quelle<?> webseitenAnfragen() {
        List<Anfrage> anfragen = anfrageRepository.findOffeneFunnelAnfragen(AnfrageFunnelService.SYSTEM_MITARBEITER_TOKEN);
        return Quelle.von("ANFRAGEN_WEBSEITE", "Neue Anfragen über Webseite", "Globe", "/anfragen", anfragen,
                a -> "ANFRAGE_WEBSEITE:" + a.getId(),
                a -> {
                    String kundenName = a.getKunde() != null && a.getKunde().getName() != null
                            && !a.getKunde().getName().isBlank() ? a.getKunde().getName() : "Unbekannt";
                    String bauvorhaben = a.getBauvorhaben() == null || a.getBauvorhaben().isBlank()
                            ? "Webseiten-Anfrage" : a.getBauvorhaben();
                    String zeit = a.getCreatedAt() != null ? a.getCreatedAt().toString()
                            : a.getAnlegedatum() != null ? a.getAnlegedatum().atStartOfDay().toString() : "";
                    return new BenachrichtigungEintragDto("ANFRAGE_WEBSEITE:" + a.getId(), "ANFRAGE_WEBSEITE",
                            "Webseite: " + kundenName, bauvorhaben, zeit, "/anfragen?anfrageId=" + a.getId());
                }, 5);
    }

    private Quelle<?> posteingang(Predicate<Email> ungelesen) {
        Set<Long> nichtZugeordnet = emailRepository.findUnassigned().stream()
                .map(Email::getId)
                .collect(Collectors.toSet());
        List<Email> offen = emailRepository.findInboxFiltered().stream()
                .filter(e -> !nichtZugeordnet.contains(e.getId()))
                .filter(ungelesen)
                .sorted(Comparator.comparing(Email::getSentAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return mailQuelle("EMAILS", "Ungelesene E-Mails", "inbox", offen);
    }

    /**
     * Selbst gesendete E-Mails (OUT) tauchen in den Ordnern Projekte/Angebote/Lieferanten sowie
     * Spam/Newsletter mit auf, weil die Repo-Queries dort nicht nach Direction filtern. Für die
     * Glocke zählen nur eingehende, ungelesene Mails – sonst klingelt es bei jeder eigenen Antwort.
     */
    private Quelle<?> mailOrdner(List<Email> mails, Predicate<Email> ungelesen, String typ, String label,
            String ordner) {
        List<Email> offen = mails.stream()
                .filter(e -> e.getDirection() == EmailDirection.IN)
                .filter(e -> e.getDeletedAt() == null)
                .filter(ungelesen)
                .sorted(Comparator.comparing(Email::getSentAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return mailQuelle(typ, label, ordner, offen);
    }

    private static Quelle<Email> mailQuelle(String typ, String label, String ordner, List<Email> ungelesen) {
        return new Quelle<>(typ, label, "Mail", offen -> ordner.equals("inbox") ? "/emails" : "/emails/" + ordner,
                ungelesen, e -> EMAIL_ART + ":" + e.getId(),
                e -> new BenachrichtigungEintragDto(EMAIL_ART + ":" + e.getId(), EMAIL_ART,
                        e.getSubject() != null ? e.getSubject() : "Kein Betreff",
                        "Von: " + (e.getFromAddress() != null ? e.getFromAddress() : "Unbekannt"),
                        e.getSentAt() != null ? e.getSentAt().toString() : "",
                        "/emails/" + ordner + "/" + e.getId()),
                3, e -> 1, true);
    }

    private Quelle<?> urlaubsantraege() {
        return Quelle.von("URLAUBSANTRAEGE", "Offene Anträge", "Plane", "/urlaubsantraege",
                urlaubsantragRepository.findByStatus(Urlaubsantrag.Status.OFFEN),
                a -> "URLAUBSANTRAG:" + a.getId(),
                a -> {
                    String name = a.getMitarbeiter() != null
                            ? a.getMitarbeiter().getVorname() + " " + a.getMitarbeiter().getNachname()
                            : "Unbekannt";
                    String zeitraum = (a.getVonDatum() != null ? a.getVonDatum().toString() : "") + " – "
                            + (a.getBisDatum() != null ? a.getBisDatum().toString() : "");
                    return new BenachrichtigungEintragDto("URLAUBSANTRAG:" + a.getId(), "URLAUBSANTRAG",
                            a.getTyp().name() + ": " + name, zeitraum,
                            a.getVonDatum() != null ? a.getVonDatum().toString() : "",
                            "/urlaubsantraege?status=OFFEN&antragId=" + a.getId());
                }, 3);
    }

    /** Bautagebuch – ProjektNotizen der letzten 7 Tage. */
    private Quelle<?> bautagebuch() {
        List<ProjektNotiz> notizen = projektNotizRepository
                .findByErstelltAmAfterOrderByErstelltAmDesc(LocalDateTime.now().minusDays(7));
        return Quelle.von("BAUTAGEBUCH", "Neue Bautagebuch-Einträge", "FileText", "/projekte?tab=notizen", notizen,
                n -> "BAUTAGEBUCH:" + n.getId(),
                n -> {
                    String projektName = n.getProjekt() != null && n.getProjekt().getBauvorhaben() != null
                            ? n.getProjekt().getBauvorhaben() : "Projekt";
                    String name = n.getMitarbeiter() != null
                            ? n.getMitarbeiter().getVorname() + " " + n.getMitarbeiter().getNachname() : "";
                    Long projektId = n.getProjekt() != null ? n.getProjekt().getId() : null;
                    return new BenachrichtigungEintragDto("BAUTAGEBUCH:" + n.getId(), "BAUTAGEBUCH",
                            "Notiz: " + projektName, "Von: " + name,
                            n.getErstelltAm() != null ? n.getErstelltAm().toString() : "",
                            projektId != null ? "/projekte?projektId=" + projektId + "&tab=notizen"
                                    : "/projekte?tab=notizen");
                }, 3);
    }

    /** Eingangsrechnungen bald fällig (≤ 3 Tage). */
    private Quelle<?> eingangsrechnungenFaellig(LocalDate heute) {
        LocalDate dreiTageVoraus = heute.plusDays(3);
        List<LieferantGeschaeftsdokument> baldFaellig = lieferantGeschaeftsdokumentRepository
                .findAllOffeneEingangsrechnungen().stream()
                .filter(gd -> gd.getZahlungsziel() != null)
                .filter(gd -> !gd.getZahlungsziel().isAfter(dreiTageVoraus))
                .toList();
        return Quelle.von("EINGANG_FAELLIG", "Eingangsrechnungen bald fällig", "AlertTriangle",
                "/offeneposten?tab=eingang", baldFaellig,
                gd -> "EINGANG_FAELLIG:" + gd.getId(),
                gd -> {
                    long tage = ChronoUnit.DAYS.between(heute, gd.getZahlungsziel());
                    String fristText = tage < 0 ? "Überfällig seit " + Math.abs(tage) + " Tagen"
                            : tage == 0 ? "Heute fällig" : "In " + tage + " Tagen fällig";
                    return new BenachrichtigungEintragDto("EINGANG_FAELLIG:" + gd.getId(), "EINGANG_FAELLIG",
                            gd.getDokumentNummer() != null ? gd.getDokumentNummer() : "Rechnung",
                            lieferantName(gd) + " – " + fristText, gd.getZahlungsziel().toString(),
                            "/offeneposten?tab=eingang&dokumentId=" + gd.getId());
                }, 3);
    }

    /** Ausgangsrechnungen überfällig (nur Rechnungen, keine Mahnungen). */
    private Quelle<?> ausgangsrechnungenUeberfaellig(LocalDate heute) {
        List<ProjektGeschaeftsdokument> ueberfaellig = projektDokumentRepository.findOffeneGeschaeftsdokumente()
                .stream()
                .filter(g -> g.getFaelligkeitsdatum() != null)
                .filter(g -> g.getFaelligkeitsdatum().isBefore(heute))
                .filter(g -> g.getMahnstufe() == null)
                .toList();
        return Quelle.von("AUSGANG_UEBERFAELLIG", "Ausgangsrechnungen überfällig", "AlertTriangle",
                "/offeneposten?tab=ausgang", ueberfaellig,
                g -> "AUSGANG_UEBERFAELLIG:" + g.getId(),
                g -> {
                    long tageUeber = ChronoUnit.DAYS.between(g.getFaelligkeitsdatum(), heute);
                    String kundenName = "";
                    try {
                        if (g.getProjekt() != null && g.getProjekt().getKunde() != null) {
                            kundenName = g.getProjekt().getKunde();
                        }
                    } catch (RuntimeException e) {
                        /* lazy loading */
                    }
                    return new BenachrichtigungEintragDto("AUSGANG_UEBERFAELLIG:" + g.getId(), "AUSGANG_UEBERFAELLIG",
                            g.getDokumentid() + " überfällig", kundenName + " – seit " + tageUeber + " Tagen",
                            g.getFaelligkeitsdatum().toString(), "/offeneposten?tab=ausgang&dokumentId=" + g.getId());
                }, 3);
    }

    /** Neue Lieferantenrechnungen (letzte 7 Tage). */
    private Quelle<?> neueLieferantenrechnungen(LocalDate heute) {
        LocalDate siebenTage = heute.minusDays(7);
        List<LieferantGeschaeftsdokument> neue = lieferantGeschaeftsdokumentRepository
                .findAllOffeneEingangsrechnungen().stream()
                .filter(gd -> gd.getDokumentDatum() != null && gd.getDokumentDatum().isAfter(siebenTage))
                .sorted(Comparator.comparing(LieferantGeschaeftsdokument::getDokumentDatum,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return Quelle.von("RECHNUNGEN", "Neue Lieferantenrechnungen", "Truck", "/offeneposten?tab=eingang", neue,
                gd -> "RECHNUNG:" + gd.getId(),
                gd -> new BenachrichtigungEintragDto("RECHNUNG:" + gd.getId(), "RECHNUNG",
                        gd.getDokumentNummer() != null ? gd.getDokumentNummer() : "Rechnung",
                        lieferantName(gd), gd.getDokumentDatum() != null ? gd.getDokumentDatum().toString() : "",
                        "/offeneposten?tab=eingang&dokumentId=" + gd.getId()),
                3);
    }

    private static String lieferantName(LieferantGeschaeftsdokument gd) {
        try {
            if (gd.getDokument() != null && gd.getDokument().getLieferant() != null) {
                return gd.getDokument().getLieferant().getLieferantenname();
            }
        } catch (RuntimeException e) {
            /* lazy loading */
        }
        return "";
    }

    /** Bevorstehende Termine (heute + morgen), heute nur, wenn sie nicht länger als eine Stunde vorbei sind. */
    private Quelle<?> termine(Long mitarbeiterId, LocalDate heute) {
        LocalDate morgen = heute.plusDays(1);
        LocalTime jetzt = LocalTime.now();
        List<KalenderEintrag> anstehend = mitarbeiterId != null
                ? kalenderEintragRepository.findByMitarbeiterAndDatumBetween(mitarbeiterId, heute, morgen)
                : kalenderEintragRepository.findByDatumBetween(heute, morgen);
        List<KalenderEintrag> relevant = anstehend.stream()
                .filter(t -> t.isGanztaegig() || !t.getDatum().equals(heute) || t.getStartZeit() == null
                        || t.getStartZeit().isAfter(jetzt.minusHours(1)))
                .sorted(Comparator.comparing(KalenderEintrag::getDatum)
                        .thenComparing(t -> t.getStartZeit() != null ? t.getStartZeit() : LocalTime.of(0, 0)))
                .toList();
        return Quelle.von("TERMINE", "Bevorstehende Termine", "CalendarClock", "/kalender", relevant,
                t -> "TERMIN:" + t.getId(),
                t -> new BenachrichtigungEintragDto("TERMIN:" + t.getId(), "TERMIN", t.getTitel(),
                        terminZeit(t, heute, jetzt), t.getDatum().toString(), "/kalender?date=" + t.getDatum()),
                3);
    }

    private static String terminZeit(KalenderEintrag t, LocalDate heute, LocalTime jetzt) {
        if (t.isGanztaegig()) {
            return t.getDatum().equals(heute) ? "Heute (ganztägig)" : "Morgen (ganztägig)";
        }
        if (t.getDatum().equals(heute) && t.getStartZeit() != null) {
            long minutenBis = java.time.Duration.between(jetzt, t.getStartZeit()).toMinutes();
            return minutenBis <= 60 && minutenBis > 0
                    ? "In " + minutenBis + " Min. (" + t.getStartZeit() + ")"
                    : "Heute " + t.getStartZeit();
        }
        return "Morgen " + (t.getStartZeit() != null ? t.getStartZeit().toString() : "");
    }

    /** Neue Lieferscheine (letzte 48 Stunden – von Mitarbeitern per Mobil-App hochgeladen). */
    private Quelle<?> lieferscheine() {
        List<LieferantDokument> neue = lieferantDokumentRepository
                .findRecentLieferscheine(LocalDateTime.now().minusHours(48));
        return Quelle.von("LIEFERSCHEINE", "Neue Lieferscheine", "Truck", "/lieferanten", neue,
                d -> "LIEFERSCHEIN:" + d.getId(),
                this::lieferscheinEintrag, 3);
    }

    private BenachrichtigungEintragDto lieferscheinEintrag(LieferantDokument d) {
        String lieferantName = "";
        Long lieferantId = null;
        String dokumentNr = "";
        String uploader = "";
        try {
            if (d.getLieferant() != null) {
                lieferantName = d.getLieferant().getLieferantenname();
                lieferantId = d.getLieferant().getId();
            }
            if (d.getGeschaeftsdaten() != null && d.getGeschaeftsdaten().getDokumentNummer() != null) {
                dokumentNr = d.getGeschaeftsdaten().getDokumentNummer();
            }
            if (d.getUploadedBy() != null) {
                uploader = d.getUploadedBy().getVorname() + " " + d.getUploadedBy().getNachname();
            }
        } catch (RuntimeException e) {
            /* lazy loading */
        }
        String titel = !dokumentNr.isBlank() ? dokumentNr
                : d.getEffektiverDateiname() != null ? d.getEffektiverDateiname() : "Lieferschein";
        return new BenachrichtigungEintragDto("LIEFERSCHEIN:" + d.getId(), "LIEFERSCHEIN", titel,
                lieferantName + (uploader.isBlank() ? "" : " – von " + uploader),
                d.getUploadDatum() != null ? d.getUploadDatum().toString() : "",
                lieferantId != null ? "/lieferanten?lieferantId=" + lieferantId + "&tab=dokumente" : "/lieferanten");
    }

    /** Offene Reklamationen (von Mitarbeitern per Mobil-App erfasst). */
    private Quelle<?> reklamationen() {
        List<LieferantReklamation> offen = lieferantReklamationRepository
                .findByStatusOrderByErstelltAmDesc(ReklamationStatus.OFFEN);
        return Quelle.von("REKLAMATIONEN", "Offene Reklamationen", "AlertTriangle", "/lieferanten", offen,
                r -> "REKLAMATION:" + r.getId(),
                r -> {
                    String lieferantName = "";
                    Long lieferantId = null;
                    try {
                        if (r.getLieferant() != null) {
                            lieferantName = r.getLieferant().getLieferantenname();
                            lieferantId = r.getLieferant().getId();
                        }
                    } catch (RuntimeException e) {
                        /* lazy loading */
                    }
                    String beschreibung = r.getBeschreibung() != null && !r.getBeschreibung().isBlank()
                            ? (r.getBeschreibung().length() > 60 ? r.getBeschreibung().substring(0, 60) + "…"
                                    : r.getBeschreibung())
                            : "Keine Beschreibung";
                    return new BenachrichtigungEintragDto("REKLAMATION:" + r.getId(), "REKLAMATION",
                            "Reklamation: " + lieferantName, beschreibung,
                            r.getErstelltAm() != null ? r.getErstelltAm().toString() : "",
                            lieferantId != null ? "/lieferanten?lieferantId=" + lieferantId + "&tab=reklamationen"
                                    : "/lieferanten");
                }, 3);
    }

    /**
     * Digital angenommene Angebote / Auftragsbestätigungen (letzte 30 Tage). Bewusst längeres
     * Fenster als bei E-Mails/Bautagebuch: eine Angebots-Annahme ist ein wichtiges
     * Geschäftsereignis, das man nicht nach einer Woche unsichtbar werden lassen will.
     */
    private Quelle<?> freigaben() {
        List<DokumentFreigabe> akzeptiert = dokumentFreigabeRepository
                .findKuerzlichAkzeptiert(LocalDateTime.now().minusDays(30));
        return Quelle.von("FREIGABEN_ANGENOMMEN", "Digital angenommen", "FileText", "/anfragen?freigabe=accepted",
                akzeptiert,
                f -> "FREIGABE_ANGENOMMEN:" + f.getId(),
                f -> {
                    String kunde = f.getKundeName() == null || f.getKundeName().isBlank()
                            ? (f.getKundeEmail() == null ? "Kunde" : f.getKundeEmail())
                            : f.getKundeName();
                    String art = f.getDokumentArt() == null ? "Dokument" : f.getDokumentArt();
                    return new BenachrichtigungEintragDto("FREIGABE_ANGENOMMEN:" + f.getId(), "FREIGABE_ANGENOMMEN",
                            art + " " + (f.getDokumentNummer() == null ? "" : f.getDokumentNummer()) + " angenommen",
                            "Von: " + kunde, f.getAkzeptiertAm() != null ? f.getAkzeptiertAm().toString() : "",
                            freigabeZuInstanzLink(f));
                }, 5);
    }

    /**
     * Automatisch beendete Zeitbuchungen (letzte 30 Tage). Der Auto-Stop hat hier eine
     * geschätzte Endezeit eingetragen, weil der Mitarbeiter nicht abgestochen hat. Diese Stunden
     * fließen ungeprüft in den Monatssaldo – sie müssen sichtbar sein.
     */
    private Quelle<?> zeitenAutomatischBeendet() {
        List<Zeitbuchung> autoBeendet = zeitbuchungRepository
                .findByAutomatischBeendetTrueAndStartZeitAfterOrderByStartZeitDesc(LocalDateTime.now().minusDays(30));
        return Quelle.von("ZEITEN_AUTO_BEENDET", "Zeiten ohne Feierabend", "Clock", "/zeitbuchungen", autoBeendet,
                z -> "ZEIT_AUTO_BEENDET:" + z.getId(),
                z -> {
                    Mitarbeiter ma = z.getMitarbeiter();
                    String name = ma != null ? ma.getVorname() + " " + ma.getNachname() : "Mitarbeiter";
                    String bis = z.getEndeZeit() != null ? z.getEndeZeit().format(UHRZEIT_FORMAT) : "?";
                    return new BenachrichtigungEintragDto("ZEIT_AUTO_BEENDET:" + z.getId(), "ZEIT_AUTO_BEENDET",
                            name + " am " + z.getStartZeit().format(TAG_FORMAT),
                            "Kein Feierabend gestempelt – automatisch auf " + bis + " Uhr gesetzt",
                            z.getStartZeit().toString(),
                            "/zeitbuchungen?mitarbeiterId=" + (ma != null ? ma.getId() : ""));
                }, 5);
    }

    /** Offene Monatsabschlüsse; Monate vor dem Start der Zeiterfassung müssen nie abgeschlossen werden. */
    private Quelle<?> monatsabschluss(LocalDate heute) {
        List<MonatsSaldoRepository.OffenerMonat> offeneMonate = monatsSaldoRepository.findOffeneAbschlussMonate(
                heute.withDayOfMonth(1), zeiterfassungEinstellungService.ersterAbschlussMonat());
        return new Quelle<>("MONATSABSCHLUSS", "Monate abschließen", "CalendarCheck",
                offen -> monatsabschlussLink(offen.getFirst().getJahr(), offen.getFirst().getMonat()),
                offeneMonate,
                m -> String.format("MONATSABSCHLUSS:%d-%02d", m.getJahr(), m.getMonat()),
                m -> new BenachrichtigungEintragDto(String.format("MONATSABSCHLUSS:%d-%02d", m.getJahr(), m.getMonat()),
                        "MONATSABSCHLUSS", String.format("%02d/%d abschließen", m.getMonat(), m.getJahr()),
                        m.getAnzahl() + " Mitarbeiter noch zu prüfen",
                        LocalDate.of(m.getJahr(), m.getMonat(), 1).atStartOfDay().toString(),
                        monatsabschlussLink(m.getJahr(), m.getMonat())),
                5, m -> m.getAnzahl() != null ? m.getAnzahl() : 0, false);
    }

    private static String monatsabschlussLink(int jahr, int monat) {
        return "/monatsabschluss?jahr=" + jahr + "&monat=" + monat;
    }

    private static Quelle<?> telefon(String typ, String label, String icon, String link,
            List<TelefonBenachrichtigungService.Eintrag> eintraege) {
        return Quelle.von(typ, label, icon, link, eintraege,
                BenachrichtigungService::telefonSchluessel,
                t -> new BenachrichtigungEintragDto(telefonSchluessel(t), t.typ(), t.titel(), t.untertitel(),
                        t.zeitpunkt().toString(), t.link()),
                10);
    }

    /** Telefon-Einträge tragen ihre ID im Link ({@code …?nachricht=3}, {@code …?anruf=7}). */
    static String telefonSchluessel(TelefonBenachrichtigungService.Eintrag t) {
        String link = t.link() == null ? "" : t.link();
        String kennung = link.substring(link.lastIndexOf('=') + 1);
        if (kennung.isEmpty() || !kennung.chars().allMatch(Character::isDigit)) {
            kennung = Integer.toHexString(link.hashCode());
        }
        return t.typ() + ":" + kennung;
    }

    /**
     * Löst aus einer Freigabe den Deep-Link auf die konkrete Anfrage- oder Projekt-Instanz auf.
     * Fallback ist der bisherige Listen-Link, damit der Klick nie ins Leere geht.
     */
    private String freigabeZuInstanzLink(DokumentFreigabe f) {
        if (f == null || f.getQuellTyp() == null) return "/anfragen";
        try {
            switch (f.getQuellTyp()) {
                case AUSGANGS_DOKUMENT: {
                    AusgangsGeschaeftsDokument dok = ausgangsGeschaeftsDokumentRepository
                            .findById(f.getQuellDokumentId()).orElse(null);
                    if (dok == null) return "/anfragen";
                    // Mit der digitalen Annahme wandelt das System die Anfrage automatisch in ein
                    // Projekt um (siehe DokumentFreigabeService). Daher zuerst auf das Projekt prüfen.
                    if (dok.getProjekt() != null) {
                        return "/projekte?projektId=" + dok.getProjekt().getId();
                    }
                    if (dok.getAnfrage() != null) {
                        Long projektId = projektIdAusAnfrage(dok.getAnfrage().getId());
                        return projektId != null ? "/projekte?projektId=" + projektId
                                : "/anfragen?anfrageId=" + dok.getAnfrage().getId();
                    }
                    return "/anfragen";
                }
                case ANFRAGE:
                    return anfrageDokumentRepository.findById(f.getQuellDokumentId())
                            .filter(d -> d instanceof AnfrageGeschaeftsdokument)
                            .map(d -> (AnfrageGeschaeftsdokument) d)
                            .filter(g -> g.getAnfrage() != null)
                            .map(g -> {
                                Long projektId = projektIdAusAnfrage(g.getAnfrage().getId());
                                return projektId != null ? "/projekte?projektId=" + projektId
                                        : "/anfragen?anfrageId=" + g.getAnfrage().getId();
                            })
                            .orElse("/anfragen");
                case PROJEKT:
                    // Projekt-Detail wird nicht über Query-Param geroutet; Liste reicht.
                    return "/projekte";
                default:
                    return "/anfragen";
            }
        } catch (RuntimeException ignored) {
            return "/anfragen";
        }
    }

    /** Projekt, in das eine Anfrage zwischenzeitlich umgewandelt wurde – oder {@code null}. */
    private Long projektIdAusAnfrage(Long anfrageId) {
        if (anfrageId == null) return null;
        try {
            return anfrageRepository.findById(anfrageId)
                    .map(Anfrage::getProjekt)
                    .map(p -> p.getId())
                    .orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Filter auf Mails, die der Benutzer sieht und selbst noch nicht gelesen hat; {@code null},
     * wenn sich das nicht ermitteln lässt (dann keine Mail-Hinweise).
     */
    private Predicate<Email> ungeleseneSichtbareMails(Authentication authentication) {
        try {
            Set<Long> verborgen = postfachSichtbarkeitService
                    .verborgeneEmailIds(postfachSichtbarkeitService.fuer(authentication));
            GelesenStand gelesen = emailGelesenService.fuer(authentication);
            return e -> !verborgen.contains(e.getId()) && gelesen.istUngelesen(e);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
