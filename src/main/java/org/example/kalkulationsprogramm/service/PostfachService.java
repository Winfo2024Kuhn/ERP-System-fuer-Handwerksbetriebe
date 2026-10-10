package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.Postfach.AbsenderPostfachDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachSpeichernRequest;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestErgebnis;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestRequest;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.example.kalkulationsprogramm.service.mail.PostfachZugang;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verwaltung der E-Mail-Postfächer (Einstellungen → E-Mail → Postfächer).
 *
 * <p>Regeln: genau ein Hauptpostfach, höchstens ein Postfach für Rechnungen und
 * Mahnungen, Login und Adresse auf derselben Domain, Passwort nur verschlüsselt.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostfachService {

    static final int MAX_FELD_LAENGE = 255;

    /** Gleiche Prüfung wie bei den Absender-Adressen; possessiv gegen ReDoS. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]++@[^@\\s.]++(?:\\.[^@\\s.]++)++$");

    private final EmailAbsenderRepository repository;
    private final EmailPostfachZuordnungRepository zuordnungRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;
    private final MailSecretService mailSecretService;
    private final SystemSettingsService systemSettingsService;

    // ==================== Lesen ====================

    @Transactional(readOnly = true)
    public List<PostfachDto> alle() {
        Map<Long, List<FrontendUserProfile>> benutzerJePostfach = frontendUserProfileRepository
                .findByEmailAbsenderIsNotNull().stream()
                .collect(Collectors.groupingBy(u -> u.getEmailAbsender().getId()));
        return repository.findAllByOrderBySortierungAscIdAsc().stream()
                .map(p -> toDto(p, benutzerJePostfach.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PostfachDto eins(Long id) {
        EmailAbsender postfach = finde(id);
        return toDto(postfach, frontendUserProfileRepository.findByEmailAbsenderId(id));
    }

    /**
     * Auswahl „Senden von“: alle aktiven Postfächer, eigenes zuerst, dann das
     * Hauptpostfach, dann nach Sortierung. (Etappe 2: nur sichtbare.)
     */
    @Transactional(readOnly = true)
    public List<AbsenderPostfachDto> absenderAuswahl(Long eigenesPostfachId) {
        List<AbsenderPostfachDto> liste = new ArrayList<>(repository.findByAktivTrueOrderBySortierungAscIdAsc().stream()
                .map(p -> new AbsenderPostfachDto(p.getId(), p.getEmailAdresse(), p.getAnzeigename(),
                        p.getId().equals(eigenesPostfachId), p.isHauptpostfach()))
                .toList());
        // Stabile Sortierung: die Sortierung aus der Datenbank bleibt innerhalb der Gruppen erhalten.
        liste.sort(Comparator.comparing((AbsenderPostfachDto a) -> !a.eigenes())
                .thenComparing(a -> !a.hauptpostfach()));
        return liste;
    }

    // ==================== Schreiben ====================

    @Transactional
    public PostfachDto anlegen(PostfachSpeichernRequest request) {
        EmailAbsender postfach = new EmailAbsender();
        uebernehme(postfach, request, true);
        return toDto(postfach, List.of());
    }

    @Transactional
    public PostfachDto aendern(Long id, PostfachSpeichernRequest request) {
        EmailAbsender postfach = finde(id);
        uebernehme(postfach, request, false);
        return toDto(postfach, frontendUserProfileRepository.findByEmailAbsenderId(id));
    }

    @Transactional
    public void loeschen(Long id) {
        EmailAbsender postfach = finde(id);
        if (postfach.isHauptpostfach()) {
            throw new IllegalArgumentException("Das Hauptpostfach kann nicht gelöscht werden.");
        }
        if (zuordnungRepository.existsByPostfachId(id)) {
            throw new IllegalArgumentException("Dieses Postfach hat schon E-Mails – bitte stattdessen ausschalten.");
        }
        for (FrontendUserProfile benutzer : frontendUserProfileRepository.findByEmailAbsenderId(id)) {
            benutzer.setEmailAbsender(null);
            frontendUserProfileRepository.save(benutzer);
        }
        repository.delete(postfach);
    }

    /**
     * Merkt sich das Ergebnis eines Abrufs. Eigene Transaktion: Der Abruf läuft
     * ohne umgebende Transaktion, der Status soll auch nach einem Fehler stehen.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void merkeAbruf(Long postfachId, String fehler) {
        if (postfachId == null) {
            return;
        }
        repository.findById(postfachId).ifPresent(p -> {
            p.setLetzterAbrufAm(LocalDateTime.now());
            p.setLetzterAbrufFehler(fehler == null ? null : kuerze(fehler, 500));
            repository.save(p);
        });
    }

    // ==================== Verbindungstest ====================

    @Transactional(readOnly = true)
    public PostfachTestErgebnis teste(PostfachTestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Daten fehlen.");
        }
        EmailAbsender gespeichert = request.id() == null ? null : finde(request.id());
        pruefeLaenge(request.benutzername(), request.passwort(), request.smtpHost(), request.imapHost(),
                request.testEmpfaenger());

        String benutzername = gesetzt(request.benutzername()) ? request.benutzername().trim()
                : gespeichert != null ? gespeichert.effektiverBenutzername() : null;
        String passwort = request.passwort();
        if (!gesetzt(passwort) && gespeichert != null) {
            // Das gespeicherte Passwort geht nur an den gespeicherten Server mit dem
            // gespeicherten Anmeldenamen – sonst ließe es sich per Test an einen
            // fremden Server schicken.
            if (!gleich(request.smtpHost(), gespeichert.getSmtpHost())
                    || !gleich(request.imapHost(), gespeichert.getImapHost())
                    || !gleich(benutzername, gespeichert.effektiverBenutzername())) {
                throw new IllegalArgumentException(
                        "Bitte das Passwort erneut eingeben, wenn Sie Server oder Anmeldename ändern.");
            }
            passwort = entschluessele(gespeichert);
        }
        if (!gesetzt(benutzername) || !gesetzt(passwort)) {
            return new PostfachTestErgebnis(false, false, "Bitte Adresse und Passwort des Postfachs eintragen.");
        }

        int smtpPort = port(request.smtpPort(), PostfachZugang.STANDARD_SMTP_PORT);
        int imapPort = port(request.imapPort(), PostfachZugang.STANDARD_IMAP_PORT);

        boolean versandOk = false;
        String versandText = "Kein Server für den Versand eingetragen.";
        if (gesetzt(request.smtpHost())) {
            SystemSettingsService.TestResult smtp = systemSettingsService.testSmtp(request.smtpHost().trim(), smtpPort,
                    benutzername, passwort, gesetzt(request.testEmpfaenger()) ? request.testEmpfaenger().trim() : null);
            versandOk = smtp.success();
            versandText = smtp.message();
        }

        boolean abrufOk = false;
        String abrufText = "Kein Posteingangs-Server eingetragen.";
        if (gesetzt(request.imapHost())) {
            SystemSettingsService.TestResult imap = systemSettingsService.testImap(request.imapHost().trim(), imapPort,
                    benutzername, passwort);
            abrufOk = imap.success();
            abrufText = imap.message();
        }

        return new PostfachTestErgebnis(versandOk, abrufOk, "Versand: " + versandText + " · Abruf: " + abrufText);
    }

    // ==================== intern ====================

    private void uebernehme(EmailAbsender postfach, PostfachSpeichernRequest request, boolean neu) {
        if (request == null) {
            throw new IllegalArgumentException("Daten fehlen.");
        }
        pruefeLaenge(request.emailAdresse(), request.anzeigename(), request.benutzername(), request.passwort(),
                request.smtpHost(), request.imapHost());
        pruefePort(request.smtpPort());
        pruefePort(request.imapPort());

        String adresse = request.emailAdresse() == null ? "" : request.emailAdresse().trim();
        if (adresse.isEmpty()) {
            throw new IllegalArgumentException("Bitte die E-Mail-Adresse des Postfachs eintragen.");
        }
        if (!EMAIL_PATTERN.matcher(adresse).matches()) {
            throw new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse eintragen.");
        }
        repository.findByEmailAdresseIgnoreCase(adresse).ifPresent(vorhanden -> {
            if (neu || !vorhanden.getId().equals(postfach.getId())) {
                throw new IllegalArgumentException("Diese E-Mail-Adresse ist bereits als Postfach angelegt.");
            }
        });

        String benutzername = gesetzt(request.benutzername()) ? request.benutzername().trim() : null;
        if (benutzername != null && !EMAIL_PATTERN.matcher(benutzername).matches()) {
            throw new IllegalArgumentException("Bitte als Benutzernamen die vollständige Adresse des Postfachs eintragen.");
        }
        if (benutzername != null && !domainVon(benutzername).equals(domainVon(adresse))) {
            throw new IllegalArgumentException("Adresse und Anmeldename müssen zur selben Domain gehören. "
                    + "Sonst stuft der Empfänger die Mail als Fälschung ein. Erwartet wird eine Adresse auf @"
                    + domainVon(benutzername) + ".");
        }

        boolean aktiv = request.aktiv() == null || request.aktiv();
        boolean wirdHaupt = Boolean.TRUE.equals(request.hauptpostfach());
        if (!wirdHaupt && aktiv && repository.findFirstByHauptpostfachTrueOrderByIdAsc().isEmpty()) {
            // Ohne Hauptpostfach wüsste der Versand nicht, wohin er zurückfallen soll:
            // das erste aktive Postfach übernimmt die Rolle.
            wirdHaupt = true;
        }
        if (postfach.isHauptpostfach() && !wirdHaupt) {
            throw new IllegalArgumentException(
                    "Es muss ein Hauptpostfach geben – bitte ein anderes Postfach als Hauptpostfach markieren.");
        }
        if (wirdHaupt && !aktiv) {
            throw new IllegalArgumentException("Das Hauptpostfach kann nicht ausgeschaltet werden.");
        }

        pruefePasswortBeiServerwechsel(postfach, neu, adresse, benutzername, request);

        postfach.setEmailAdresse(adresse);
        postfach.setAnzeigename(gesetzt(request.anzeigename()) ? request.anzeigename().trim() : null);
        postfach.setAktiv(aktiv);
        postfach.setSortierung(request.sortierung() == null ? postfach.getSortierung() : request.sortierung());
        postfach.setBenutzername(benutzername);
        postfach.setSmtpHost(gesetzt(request.smtpHost()) ? request.smtpHost().trim() : null);
        postfach.setSmtpPort(request.smtpPort());
        postfach.setImapHost(gesetzt(request.imapHost()) ? request.imapHost().trim() : null);
        postfach.setImapPort(request.imapPort());
        if (gesetzt(request.passwort())) {
            try {
                postfach.setPasswortVerschluesselt(mailSecretService.encrypt(request.passwort()));
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException("Das Passwort kann nicht sicher gespeichert werden: "
                        + "Der Schlüssel für Mailzugänge ist auf dem Server nicht eingerichtet.");
            }
        }

        boolean wirdGeschaeftsdokumente = Boolean.TRUE.equals(request.fuerGeschaeftsdokumente());
        postfach.setHauptpostfach(wirdHaupt);
        postfach.setFuerGeschaeftsdokumente(wirdGeschaeftsdokumente);
        EmailAbsender gespeichert = repository.save(postfach);

        // Es gibt nur ein Hauptpostfach und nur ein Rechnungs-Postfach: der neue Haken gewinnt.
        if (wirdHaupt) {
            for (EmailAbsender anderes : repository.findByHauptpostfachTrue()) {
                if (!anderes.getId().equals(gespeichert.getId())) {
                    anderes.setHauptpostfach(false);
                    repository.save(anderes);
                }
            }
        }
        if (wirdGeschaeftsdokumente) {
            for (EmailAbsender anderes : repository.findByFuerGeschaeftsdokumenteTrue()) {
                if (!anderes.getId().equals(gespeichert.getId())) {
                    anderes.setFuerGeschaeftsdokumente(false);
                    repository.save(anderes);
                }
            }
        }
        // Bewusst ohne Passwort geloggt.
        log.info("[Postfach] Postfach {} gespeichert (Hauptpostfach: {}, Rechnungen: {}, aktiv: {})",
                gespeichert.getId(), wirdHaupt, wirdGeschaeftsdokumente, aktiv);
    }

    /**
     * Ein gespeichertes Passwort darf nicht an einen anderen Server oder für einen anderen
     * Anmeldenamen weiterverwendet werden – sonst bekäme ein fremder Server es beim nächsten
     * Abruf geschickt. Wer Server oder Anmeldenamen ändert, gibt das Passwort neu ein.
     */
    private static void pruefePasswortBeiServerwechsel(EmailAbsender postfach, boolean neu, String adresse,
            String benutzername, PostfachSpeichernRequest request) {
        if (neu || gesetzt(request.passwort()) || !gesetzt(postfach.getPasswortVerschluesselt())) {
            return;
        }
        String neuerLogin = benutzername != null ? benutzername : adresse;
        if (!gleich(request.smtpHost(), postfach.getSmtpHost())
                || !gleich(request.imapHost(), postfach.getImapHost())
                || !gleich(neuerLogin, postfach.effektiverBenutzername())) {
            throw new IllegalArgumentException(
                    "Bitte das Passwort erneut eingeben, wenn Sie Server oder Anmeldename ändern.");
        }
    }

    private EmailAbsender finde(Long id) {
        if (id == null || id <= 0) {
            throw new NoSuchElementException("Postfach nicht gefunden.");
        }
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Postfach nicht gefunden."));
    }

    private PostfachDto toDto(EmailAbsender p, List<FrontendUserProfile> benutzer) {
        boolean passwortGesetzt = gesetzt(p.getPasswortVerschluesselt());
        boolean abrufAktiv = p.isAktiv() && passwortGesetzt && gesetzt(p.getImapHost());
        return new PostfachDto(
                p.getId(),
                p.getEmailAdresse(),
                p.getAnzeigename(),
                p.isAktiv(),
                p.getSortierung(),
                p.isHauptpostfach(),
                p.isFuerGeschaeftsdokumente(),
                p.getBenutzername(),
                passwortGesetzt,
                p.getSmtpHost(),
                p.getSmtpPort(),
                p.getImapHost(),
                p.getImapPort(),
                abrufAktiv,
                p.getLetzterAbrufAm(),
                p.getLetzterAbrufFehler(),
                benutzer.stream()
                        .map(u -> new PostfachDto.BenutzerRefDto(u.getId(), u.getDisplayName()))
                        .toList());
    }

    private String entschluessele(EmailAbsender postfach) {
        try {
            return mailSecretService.decrypt(postfach.getPasswortVerschluesselt());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void pruefeLaenge(String... werte) {
        for (String wert : werte) {
            if (wert != null && wert.length() > MAX_FELD_LAENGE) {
                throw new IllegalArgumentException("Eine der Eingaben ist zu lang (höchstens "
                        + MAX_FELD_LAENGE + " Zeichen).");
            }
        }
    }

    private static void pruefePort(Integer port) {
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Der Port muss zwischen 1 und 65535 liegen.");
        }
    }

    private static int port(Integer port, int standard) {
        return port != null && port > 0 ? port : standard;
    }

    static String domainVon(String adresse) {
        if (adresse == null) {
            return "";
        }
        int at = adresse.lastIndexOf('@');
        return at < 0 ? "" : adresse.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** Gleicher Wert ohne Rücksicht auf Leerzeichen und Groß-/Kleinschreibung; leer gilt als gleich leer. */
    static boolean gleich(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return x.equalsIgnoreCase(y);
    }

    private static boolean gesetzt(String wert) {
        return wert != null && !wert.isBlank();
    }

    private static String kuerze(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** Für den Versand: Postfach per Id, nur wenn aktiv. */
    @Transactional(readOnly = true)
    public Optional<EmailAbsender> aktivesPostfach(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).filter(EmailAbsender::isAktiv);
    }
}
