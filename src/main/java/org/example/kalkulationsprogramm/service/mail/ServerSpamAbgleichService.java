package org.example.kalkulationsprogramm.service.mail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.ServerSpamStatus;
import org.example.kalkulationsprogramm.dto.Email.ServerSpamKandidat;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachServerOrdnerDto;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.example.kalkulationsprogramm.service.mail.SpamOrdnerRegeln.ServerOrdner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sun.mail.imap.AppendUID;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.StoreClosedException;
import jakarta.mail.search.HeaderTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spam auch auf dem Mail-Server einsortieren: Was das ERP als Spam führt (automatisch erkannt
 * oder per Knopf), verschiebt dieser Abgleich im jeweiligen Postfach aus dem Posteingang in den
 * Spam-Ordner. „Kein Spam“ holt die Mail zurück in den Posteingang.
 *
 * <p>Bewusst eng gefasst, damit nichts verloren geht:</p>
 * <ul>
 *   <li>Nur aus dem Posteingang ({@code INBOX}) – was jemand in einen Unterordner einsortiert hat, bleibt dort.</li>
 *   <li>Nur Eingangsmails ohne Projekt, Anfrage oder Lieferant, nichts aus dem ERP-Papierkorb, und nie eine
 *       Mail, deren Message-ID (bei der Suche zusätzlich Absender und Betreff) nicht passt.</li>
 *   <li>Es wird verschoben, nie gelöscht – und nie in Papierkorb, Gesendet oder Entwürfe.</li>
 *   <li>Zurückgeholt werden nur Mails, die das ERP selbst verschoben hat – auch wenn der Haken
 *       am Postfach inzwischen aus ist.</li>
 * </ul>
 *
 * <p>Der Import liest nur Posteingang und Gesendet; Mails im Spam-Ordner kommen also nicht doppelt.
 * Ordner und UID an der {@code EmailPostfachZuordnung} wandern mit, damit das endgültige Löschen
 * die Mail weiterhin findet (und nur dort sucht).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerSpamAbgleichService {

    static final String POSTEINGANG = SpamOrdnerRegeln.POSTEINGANG;
    static final int MAX_JE_LAUF = 100;
    static final int MAX_ORDNER = 500;
    /** Danach wird eine Mail nicht mehr versucht – sonst blockierten dauerhaft abgelehnte Mails die Warteschlange. */
    static final int MAX_VERSUCHE = 3;

    static final String KEIN_SPAM_ORDNER =
            "Auf dem Server wurde kein Spam-Ordner gefunden. Bitte den Spam-Ordner im Postfach auswählen.";
    static final String GEWAEHLTER_ORDNER_FEHLT =
            "Der gewählte Spam-Ordner „%s“ ist auf dem Server nicht vorhanden. Bitte im Postfach einen anderen auswählen.";
    static final String UNGEEIGNETER_ORDNER =
            "„%s“ eignet sich nicht als Spam-Ordner (Posteingang, Papierkorb, Gesendet oder Entwürfe). "
                    + "Bitte im Postfach einen anderen auswählen.";
    static final String VERBINDUNG_FEHLGESCHLAGEN =
            "Verbindung zum Posteingangs-Server fehlgeschlagen. Bitte den Zugang prüfen.";
    static final String ABGLEICH_FEHLGESCHLAGEN =
            "Der Spam-Abgleich mit dem Mail-Server ist fehlgeschlagen. Das ERP versucht es später erneut.";
    static final String VERSCHIEBEN_ABGELEHNT =
            "Der Mail-Server hat das Verschieben abgelehnt (z. B. weil das Postfach voll ist). "
                    + "Das ERP versucht es später erneut.";
    static final String ZURUECKHOLEN_ABGELEHNT =
            "Der Mail-Server hat das Zurückholen aus dem Spam-Ordner abgelehnt. Das ERP versucht es später erneut; "
                    + "notfalls die Mail am Handy oder im Webmail selbst in den Posteingang ziehen.";
    static final String KEIN_ZUGANG = "Für dieses Postfach ist noch kein Abruf-Zugang gespeichert.";

    @Value("${email.features.enabled:true}")
    private boolean emailFeaturesEnabled;

    private final PostfachZugangService postfachZugangService;
    private final PostfachService postfachService;
    private final EmailAbsenderRepository postfachRepository;
    private final EmailPostfachZuordnungRepository zuordnungRepository;

    /** Fehlversuche je Zuordnung (nur Ids, keine Personendaten); nach einem Neustart wieder bei null. */
    private final Map<Long, Integer> fehlversuche = new ConcurrentHashMap<>();

    /** Ergebnis eines Verschiebe-Durchgangs. */
    record Ergebnis(int verschoben, int fehlgeschlagen) {
        static final Ergebnis LEER = new Ergebnis(0, 0);

        Ergebnis plus(Ergebnis anderes) {
            return new Ergebnis(verschoben + anderes.verschoben, fehlgeschlagen + anderes.fehlgeschlagen);
        }
    }

    /**
     * Alle zwei Minuten, jedes Postfach für sich – ein Fehler bei max@ hält info@ nicht auf.
     * Ohne offene Arbeit wird keine Verbindung aufgebaut.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 90_000)
    public void gleicheAb() {
        if (!emailFeaturesEnabled) {
            return;
        }
        for (PostfachZugang zugang : postfachZugangService.abrufbarePostfaecher()) {
            try {
                gleichePostfachAb(zugang);
            } catch (MessagingException | RuntimeException e) {
                log.warn("[SpamAbgleich] Postfach {}: {}", zugang.postfachId(), EmailImportService.fehlerKurz(e));
            }
        }
    }

    /** @return Anzahl der auf dem Server verschobenen Mails (beide Richtungen) */
    int gleichePostfachAb(PostfachZugang zugang) throws MessagingException {
        if (zugang.postfachId() == null) {
            // Rückfall auf das Konto aus den System-Einstellungen: kein Postfach, kein Schalter.
            return 0;
        }
        EmailAbsender postfach = postfachRepository.findById(zugang.postfachId()).orElse(null);
        if (postfach == null) {
            return 0;
        }
        Long postfachId = postfach.getId();
        // Der Haken sperrt nur neues Verschieben. Schon verschobene Mails holt „Kein Spam“ immer zurück.
        List<ServerSpamKandidat> rein = postfach.isSpamAufServerVerschieben()
                ? zuordnungRepository.findSpamZumVerschieben(postfachId, POSTEINGANG, EmailDirection.IN,
                        Limit.of(MAX_JE_LAUF))
                : List.of();
        List<ServerSpamKandidat> zurueck = zuordnungRepository.findZurueckZuHolen(
                postfachId, ServerSpamStatus.VERSCHOBEN, Limit.of(MAX_JE_LAUF));
        if (rein.isEmpty() && zurueck.isEmpty()) {
            return 0;
        }

        Store verbindung;
        try {
            verbindung = ImapVerbindung.verbinde(zugang.imapZugang());
        } catch (MessagingException e) {
            merkeErgebnis(postfach, postfach.getSpamOrdnerErkannt(), VERBINDUNG_FEHLGESCHLAGEN);
            throw e;
        }
        try (Store store = verbindung) {
            Ergebnis gesamt = holeZurueck(store, zurueck);
            if (gesamt.verschoben() == 0 && gesamt.fehlgeschlagen() > 0) {
                merkeErgebnis(postfach, postfach.getSpamOrdnerErkannt(), ZURUECKHOLEN_ABGELEHNT);
            }
            if (!rein.isEmpty()) {
                Optional<String> spamOrdner = bestimmeSpamOrdner(store, postfach);
                if (spamOrdner.isPresent()) {
                    Ergebnis hinein = verschiebe(store, POSTEINGANG, spamOrdner.get(), rein, ServerSpamStatus.VERSCHOBEN);
                    if (hinein.verschoben() == 0 && hinein.fehlgeschlagen() > 0) {
                        merkeErgebnis(postfach, spamOrdner.get(), VERSCHIEBEN_ABGELEHNT);
                    }
                    gesamt = gesamt.plus(hinein);
                }
            }
            return gesamt.verschoben();
        } catch (MessagingException e) {
            merkeErgebnis(postfach, postfach.getSpamOrdnerErkannt(), ABGLEICH_FEHLGESCHLAGEN);
            throw e;
        }
    }

    /**
     * Ordner für die Auswahl im Postfach-Dialog – ohne Posteingang, Papierkorb, Gesendet und
     * Entwürfe. Verbindet sich mit dem gespeicherten Zugang.
     *
     * @throws NoSuchElementException   Postfach unbekannt
     * @throws IllegalArgumentException kein Zugang oder Server nicht erreichbar (Satz für den Nutzer)
     */
    public PostfachServerOrdnerDto serverOrdner(Long postfachId) {
        if (postfachId == null || postfachId <= 0) {
            throw new NoSuchElementException("Postfach nicht gefunden.");
        }
        EmailAbsender postfach = postfachRepository.findById(postfachId)
                .orElseThrow(() -> new NoSuchElementException("Postfach nicht gefunden."));
        PostfachZugang zugang = postfachZugangService.zugangVon(postfach);
        if (!zugang.hatAbrufZugang()) {
            throw new IllegalArgumentException(KEIN_ZUGANG);
        }
        try (Store store = ImapVerbindung.verbinde(zugang.imapZugang())) {
            List<ServerOrdner> ordner = listeOrdner(store);
            String erkannt = SpamOrdnerRegeln.erkenneSpamOrdner(ordner).orElse(null);
            return new PostfachServerOrdnerDto(
                    ordner.stream()
                            .filter(SpamOrdnerRegeln::eignetSich)
                            .map(o -> new PostfachServerOrdnerDto.Ordner(o.name(), o.name().equals(erkannt)))
                            .toList(),
                    erkannt);
        } catch (MessagingException e) {
            log.warn("[SpamAbgleich] Ordnerliste von Postfach {} nicht lesbar: {}", postfachId,
                    EmailImportService.fehlerKurz(e));
            throw new IllegalArgumentException(VERBINDUNG_FEHLGESCHLAGEN);
        }
    }

    // ==================== Spam-Ordner finden ====================

    /** Von Hand gewählter Ordner, sonst der erkannte. Merkt sich das Ergebnis für die Anzeige. */
    Optional<String> bestimmeSpamOrdner(Store store, EmailAbsender postfach) throws MessagingException {
        List<ServerOrdner> ordner = listeOrdner(store);
        String gewaehlt = postfach.getSpamOrdner();
        if (gewaehlt != null && !gewaehlt.isBlank()) {
            Optional<ServerOrdner> vorhanden = ordner.stream().filter(o -> o.name().equals(gewaehlt)).findFirst();
            if (vorhanden.isEmpty()) {
                merkeErgebnis(postfach, null, String.format(GEWAEHLTER_ORDNER_FEHLT, gewaehlt));
                return Optional.empty();
            }
            if (!SpamOrdnerRegeln.eignetSich(vorhanden.get())) {
                merkeErgebnis(postfach, null, String.format(UNGEEIGNETER_ORDNER, gewaehlt));
                return Optional.empty();
            }
            merkeErgebnis(postfach, gewaehlt, null);
            return Optional.of(gewaehlt);
        }
        Optional<String> erkannt = SpamOrdnerRegeln.erkenneSpamOrdner(ordner);
        merkeErgebnis(postfach, erkannt.orElse(null), erkannt.isPresent() ? null : KEIN_SPAM_ORDNER);
        return erkannt;
    }

    static List<ServerOrdner> listeOrdner(Store store) throws MessagingException {
        List<ServerOrdner> liste = new ArrayList<>();
        for (Folder f : store.getDefaultFolder().list("*")) {
            if (liste.size() >= MAX_ORDNER) {
                break;
            }
            List<String> attribute = f instanceof IMAPFolder imap && imap.getAttributes() != null
                    ? Arrays.stream(imap.getAttributes()).map(a -> a.toLowerCase(Locale.ROOT)).toList()
                    : List.of();
            if (attribute.contains("\\noselect") || attribute.contains("\\nonexistent")) {
                continue;
            }
            boolean sonder = attribute.stream().anyMatch(SpamOrdnerRegeln.SONDER_ATTRIBUTE::contains);
            liste.add(new ServerOrdner(f.getFullName(),
                    attribute.contains(SpamOrdnerRegeln.JUNK_ATTRIBUT.toLowerCase(Locale.ROOT)), sonder));
        }
        liste.sort(Comparator.comparing(ServerOrdner::name, String.CASE_INSENSITIVE_ORDER));
        return liste;
    }

    /** Schreibt nur, wenn sich etwas geändert hat – der Abgleich läuft alle zwei Minuten. */
    private void merkeErgebnis(EmailAbsender postfach, String ordner, String fehler) {
        if (Objects.equals(ordner, postfach.getSpamOrdnerErkannt())
                && Objects.equals(fehler, postfach.getSpamAbgleichFehler())) {
            return;
        }
        postfachService.merkeSpamAbgleich(postfach.getId(), ordner, fehler);
        postfach.setSpamOrdnerErkannt(ordner);
        postfach.setSpamAbgleichFehler(fehler);
    }

    // ==================== Verschieben ====================

    /** „Kein Spam“: zurück in den Posteingang, aus dem Ordner, in den das ERP sie gelegt hat. */
    private Ergebnis holeZurueck(Store store, List<ServerSpamKandidat> zurueck) throws MessagingException {
        Map<String, List<ServerSpamKandidat>> jeOrdner = new LinkedHashMap<>();
        for (ServerSpamKandidat k : zurueck) {
            if (k.imapOrdner() == null) {
                zuordnungRepository.merkeServerSpamStatus(k.zuordnungId(), ServerSpamStatus.NICHT_GEFUNDEN);
                continue;
            }
            jeOrdner.computeIfAbsent(k.imapOrdner(), o -> new ArrayList<>()).add(k);
        }
        Ergebnis gesamt = Ergebnis.LEER;
        for (Map.Entry<String, List<ServerSpamKandidat>> eintrag : jeOrdner.entrySet()) {
            gesamt = gesamt.plus(verschiebe(store, eintrag.getKey(), POSTEINGANG, eintrag.getValue(), null));
        }
        return gesamt;
    }

    /**
     * Verschiebt die Mails von {@code von} nach {@code nach} und merkt sich den neuen Ort.
     * Nicht gefundene Mails (z. B. am Handy gelöscht) werden markiert und nicht erneut versucht;
     * vom Server abgelehnte nach {@link #MAX_VERSUCHE} Versuchen ebenso.
     */
    Ergebnis verschiebe(Store store, String von, String nach, List<ServerSpamKandidat> kandidaten,
            ServerSpamStatus neuerStatus) throws MessagingException {
        Folder quellOrdner = store.getFolder(von);
        if (!(quellOrdner instanceof IMAPFolder quelle) || !quelle.exists()) {
            kandidaten.forEach(k -> zuordnungRepository.merkeServerSpamStatus(
                    k.zuordnungId(), ServerSpamStatus.NICHT_GEFUNDEN));
            return Ergebnis.LEER;
        }
        Folder ziel = store.getFolder(nach);
        if (!ziel.exists()) {
            log.warn("[SpamAbgleich] Zielordner '{}' fehlt auf dem Server – nichts verschoben", nach);
            return Ergebnis.LEER;
        }
        int verschoben = 0;
        int fehlgeschlagen = 0;
        quelle.open(Folder.READ_WRITE);
        try {
            for (ServerSpamKandidat k : kandidaten) {
                try {
                    Message msg = finde(quelle, k);
                    if (msg == null) {
                        zuordnungRepository.merkeServerSpamStatus(k.zuordnungId(), ServerSpamStatus.NICHT_GEFUNDEN);
                        fehlversuche.remove(k.zuordnungId());
                        continue;
                    }
                    Long neueUid = verschiebeNachricht((IMAPStore) store, quelle, msg, ziel);
                    zuordnungRepository.merkeServerOrt(k.zuordnungId(), ziel.getFullName(), neueUid, neuerStatus);
                    fehlversuche.remove(k.zuordnungId());
                    verschoben++;
                } catch (FolderClosedException | StoreClosedException e) {
                    log.warn("[SpamAbgleich] Verbindung getrennt in Ordner '{}': {}", von, EmailImportService.fehlerKurz(e));
                    break;
                } catch (MessagingException e) {
                    fehlgeschlagen++;
                    merkeFehlversuch(k, von, nach, e);
                }
            }
        } finally {
            if (quelle.isOpen()) {
                quelle.close(false);
            }
        }
        if (verschoben > 0) {
            log.info("[SpamAbgleich] {} Mail(s) von '{}' nach '{}' verschoben", verschoben, von, nach);
        }
        return new Ergebnis(verschoben, fehlgeschlagen);
    }

    private void merkeFehlversuch(ServerSpamKandidat k, String von, String nach, MessagingException e) {
        int versuche = fehlversuche.merge(k.zuordnungId(), 1, Integer::sum);
        // Bewusst ohne Betreff und Absender (DSGVO).
        if (versuche >= MAX_VERSUCHE) {
            fehlversuche.remove(k.zuordnungId());
            zuordnungRepository.merkeServerSpamStatus(k.zuordnungId(), ServerSpamStatus.FEHLGESCHLAGEN);
            log.warn("[SpamAbgleich] Mail {} ließ sich {}-mal nicht von '{}' nach '{}' verschieben – "
                    + "wird nicht mehr versucht: {}", k.zuordnungId(), versuche, von, nach, EmailImportService.fehlerKurz(e));
        } else {
            log.warn("[SpamAbgleich] Mail {} ließ sich nicht von '{}' nach '{}' verschieben (Versuch {}): {}",
                    k.zuordnungId(), von, nach, versuche, EmailImportService.fehlerKurz(e));
        }
    }

    /**
     * Per UID (schnell), sonst per Suche – aber immer nur bei exakt gleicher Message-ID, bei der
     * Suche zusätzlich mit gleichem Absender und Betreff. Zum Löschen markierte Exemplare zählen nicht.
     */
    static Message finde(IMAPFolder quelle, ServerSpamKandidat k) throws MessagingException {
        String messageId = k.messageId();
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        if (k.imapUid() != null) {
            Message msg = quelle.getMessageByUID(k.imapUid());
            if (msg != null && nochDa(msg) && gleicheMessageId(msg, messageId)) {
                return msg;
            }
        }
        for (Message msg : quelle.search(new HeaderTerm("Message-ID", messageId))) {
            // HeaderTerm sucht Teilstrings – erst der genaue Vergleich entscheidet.
            if (nochDa(msg) && gleicheMessageId(msg, messageId)
                    && EmailImportService.passtZu(k.absender(), k.betreff(), msg)) {
                return msg;
            }
        }
        return null;
    }

    private static boolean nochDa(Message msg) throws MessagingException {
        return !msg.isExpunged() && !msg.isSet(Flags.Flag.DELETED);
    }

    private static boolean gleicheMessageId(Message msg, String erwartet) throws MessagingException {
        String[] ids = msg.getHeader("Message-ID");
        return ids != null && ids.length > 0 && ids[0] != null && ids[0].trim().equals(erwartet.trim());
    }

    /**
     * MOVE, wenn der Server es kann. Sonst Kopie und gezieltes Entfernen nur dieser Mail –
     * nie ein allgemeines Aufräumen, das andere zum Löschen markierte Mails mitnähme.
     *
     * @return neue UID im Zielordner, {@code null} wenn der Server sie nicht nennt
     */
    static Long verschiebeNachricht(IMAPStore store, IMAPFolder quelle, Message msg, Folder ziel)
            throws MessagingException {
        Message[] eine = { msg };
        AppendUID[] neu;
        if (store.hasCapability("MOVE")) {
            neu = quelle.moveUIDMessages(eine, ziel);
        } else if (store.hasCapability("UIDPLUS")) {
            neu = quelle.copyUIDMessages(eine, ziel);
            msg.setFlag(Flags.Flag.DELETED, true);
            quelle.expunge(eine);
        } else {
            // Ohne UIDPLUS lässt sich nicht gezielt entfernen: Die Mail bleibt als gelöscht
            // markiert stehen, bis der Mail-Server oder das Mailprogramm aufräumt.
            quelle.copyMessages(eine, ziel);
            msg.setFlag(Flags.Flag.DELETED, true);
            neu = null;
        }
        return neu != null && neu.length > 0 && neu[0] != null ? neu[0].uid : null;
    }
}
