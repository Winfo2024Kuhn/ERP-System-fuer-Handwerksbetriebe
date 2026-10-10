package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.mail.PostfachZugang;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import org.example.kalkulationsprogramm.util.EmailThreadTeilnehmer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entscheidet, über welches Postfach eine Mail aus dem E-Mail-Center rausgeht, und
 * baut den passenden Versand (SMTP-Zugang, Absender, Gesendet-Kopie).
 *
 * <ul>
 *   <li>Antwort/Weiterleitung: fest das Postfach, in dem die Original-Mail ankam
 *       ({@link #antwortPostfach(Email)}).</li>
 *   <li>Rechnung, Mahnung, Angebot, AB: das Postfach für Geschäftsdokumente.</li>
 *   <li>Neue Mail: das gewählte Postfach, sonst das eigene, sonst das Hauptpostfach.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostfachVersandService {

    /** Obergrenze für Sammel-Mails – darüber sperrt der Anbieter das Postfach gern als Spam-Schleuder. */
    public static final int MAX_EINZELVERSAND_EMPFAENGER = 50;

    private final EmailAbsenderRepository postfachRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;
    private final PostfachZugangService postfachZugangService;
    private final SystemSettingsService systemSettingsService;
    private final SentMailArchiver sentMailArchiver;

    /**
     * Fertiger Versand: Dienst mit Zugang und Gesendet-Kopie, Absender-Adresse und das
     * Postfach, dem die gespeicherte Mail zugeordnet wird ({@code null} ohne Postfächer).
     */
    public record Versand(EmailService dienst, String absenderAdresse, EmailAbsender postfach) {
    }

    // ==================== Postfach bestimmen ====================

    /**
     * Postfach für eine neue Mail.
     *
     * @throws IllegalArgumentException gewähltes Postfach unbekannt oder ausgeschaltet
     */
    @Transactional(readOnly = true)
    public EmailAbsender postfachFuerNeueMail(Long gewaehltesPostfachId, Long frontendUserId,
            boolean geschaeftsdokument) {
        if (geschaeftsdokument) {
            Optional<EmailAbsender> rechnung = postfachRepository
                    .findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc();
            if (rechnung.isPresent()) {
                return rechnung.get();
            }
            return hauptpostfach().orElse(null);
        }
        if (gewaehltesPostfachId != null) {
            return postfachRepository.findById(gewaehltesPostfachId)
                    .filter(EmailAbsender::isAktiv)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Dieses Postfach gibt es nicht (mehr) oder es ist ausgeschaltet. Bitte ein anderes wählen."));
        }
        return eigenesPostfach(frontendUserId)
                .or(this::hauptpostfach)
                .or(() -> postfachRepository.findFirstByAktivTrueOrderBySortierungAscIdAsc())
                .orElse(null);
    }

    /** Eigenes, aktives Postfach eines Benutzers. */
    @Transactional(readOnly = true)
    public Optional<EmailAbsender> eigenesPostfach(Long frontendUserId) {
        if (frontendUserId == null) {
            return Optional.empty();
        }
        return frontendUserProfileRepository.findById(frontendUserId)
                .map(FrontendUserProfile::getEmailAbsender)
                .filter(EmailAbsender::isAktiv);
    }

    /**
     * Postfach, aus dem eine Antwort oder Weiterleitung auf {@code email} rausgeht –
     * fest, ohne Auswahl.
     *
     * <ul>
     *   <li>Eigene gesendete Mail: das Postfach, aus dem sie rausging.</li>
     *   <li>Eingang: das Postfach, an das die Mail im „An“ adressiert war, sonst im
     *       „CC“, sonst das Hauptpostfach, sonst das erste, in dem sie liegt.</li>
     *   <li>Mail aus der Zeit vor den Postfächern: Postfach mit der Absender-Adresse
     *       (Ausgang), sonst das Hauptpostfach.</li>
     *   <li>Ist das ermittelte Postfach ausgeschaltet: das Hauptpostfach.</li>
     * </ul>
     *
     * @return {@code null} nur, wenn es gar kein Postfach gibt
     */
    @Transactional(readOnly = true)
    public EmailAbsender antwortPostfach(Email email) {
        EmailAbsender postfach = antwortPostfachOhnePruefung(email);
        // Ein ausgeschaltetes Postfach verschickt nichts mehr – die Antwort geht dann übers Hauptpostfach.
        if (postfach != null && !postfach.isAktiv()) {
            return hauptpostfach().orElse(null);
        }
        return postfach;
    }

    private EmailAbsender antwortPostfachOhnePruefung(Email email) {
        if (email == null) {
            return hauptpostfach().orElse(null);
        }
        List<EmailAbsender> postfaecher = email.getPostfachZuordnungen() == null ? List.of()
                : email.getPostfachZuordnungen().stream()
                        .sorted(Comparator.comparing(EmailPostfachZuordnung::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(EmailPostfachZuordnung::getPostfach)
                        .filter(p -> p != null)
                        .toList();

        if (email.getDirection() == EmailDirection.OUT) {
            if (!postfaecher.isEmpty()) {
                return postfaecher.get(0);
            }
            return postfachMitAdresse(email.getFromAddress()).or(this::hauptpostfach).orElse(null);
        }

        if (postfaecher.isEmpty()) {
            return hauptpostfach().orElse(null);
        }
        Optional<EmailAbsender> imAn = erstesMitAdresseIn(postfaecher, email.getRecipient());
        if (imAn.isPresent()) {
            return imAn.get();
        }
        Optional<EmailAbsender> imCc = erstesMitAdresseIn(postfaecher, email.getCc());
        if (imCc.isPresent()) {
            return imCc.get();
        }
        return postfaecher.stream().filter(EmailAbsender::isHauptpostfach).findFirst()
                .orElse(postfaecher.get(0));
    }

    // ==================== Versand bauen ====================

    /**
     * Versand über ein Postfach.
     *
     * <ul>
     *   <li>Postfach mit eigenem Zugang: dessen SMTP, Gesendet-Kopie in dessen Posteingang.</li>
     *   <li>Reine Absender-Adresse ohne Zugang: über das Hauptpostfach mit dieser
     *       Adresse als Absender – wie vor den Postfächern.</li>
     *   <li>{@code null} (gar kein Postfach): Standard-Konto aus den Einstellungen.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Versand versandUeber(EmailAbsender postfach) {
        if (postfach == null) {
            SystemSettingsService.MailKonto konto = systemSettingsService.getStandardMailKonto();
            EmailService dienst = dienst(konto, konto.fromName())
                    .mitSentKopie(sentMailArchiver.fuer(systemSettingsService.getStandardImapZugang()));
            return new Versand(dienst, konto.fromAddress(), null);
        }
        PostfachZugang zugang = postfachZugangService.zugangVon(postfach);
        if (zugang.hatVersandZugang()) {
            SystemSettingsService.MailKonto konto = zugang.mailKonto();
            EmailService dienst = dienst(konto, anzeigenameOder(postfach, systemSettingsService.getMailAbsenderName()))
                    .mitSentKopie(sentMailArchiver.fuer(zugang.imapZugang()));
            return new Versand(dienst, postfach.getEmailAdresse(), postfach);
        }
        SystemSettingsService.MailKonto standard = systemSettingsService.getStandardMailKonto();
        EmailService dienst = dienst(standard, anzeigenameOder(postfach, standard.fromName()))
                .mitSentKopie(sentMailArchiver.fuer(systemSettingsService.getStandardImapZugang()));
        return new Versand(dienst, postfach.getEmailAdresse(), postfach);
    }

    // ==================== Einzelversand ====================

    /** Eine einzelne Sendung an genau einen Empfänger; liefert z. B. die gespeicherte Mail. */
    @FunctionalInterface
    public interface Einzelsendung<T> {
        T sendeAn(String empfaenger) throws Exception;
    }

    public record Fehlschlag(String adresse, String grund) {
    }

    public record EinzelversandErgebnis<T>(List<T> verschickt, List<Fehlschlag> fehlgeschlagen) {
    }

    /**
     * Prüft die Empfängerliste für eine Sammel-Mail.
     *
     * @return bereinigte Empfänger (getrimmt, ohne Leere und ohne Doppelte)
     * @throws IllegalArgumentException mit Meldung in Handwerker-Sprache
     */
    public List<String> pruefeEinzelversand(List<String> empfaenger, List<String> cc) {
        if (cc != null && cc.stream().anyMatch(a -> a != null && !a.isBlank())) {
            throw new IllegalArgumentException("Beim einzelnen Verschicken bitte keine Kopie-Empfänger eintragen.");
        }
        List<String> bereinigt = new ArrayList<>();
        Set<String> gesehen = new java.util.HashSet<>();
        if (empfaenger != null) {
            for (String adresse : empfaenger) {
                if (adresse == null || adresse.isBlank()) {
                    continue;
                }
                String getrimmt = adresse.trim();
                if (gesehen.add(getrimmt.toLowerCase(Locale.ROOT))) {
                    bereinigt.add(getrimmt);
                }
            }
        }
        if (bereinigt.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens einen Empfänger angeben.");
        }
        if (bereinigt.size() > MAX_EINZELVERSAND_EMPFAENGER) {
            throw new IllegalArgumentException("Höchstens " + MAX_EINZELVERSAND_EMPFAENGER
                    + " Empfänger pro Sammel-Mail.");
        }
        return bereinigt;
    }

    /**
     * Schickt jedem Empfänger eine eigene Mail. Ein Fehler bei einem Empfänger hält die
     * anderen nicht auf. Schlägt die Anmeldung am Postfach fehl, wird nicht weiter
     * probiert – jeder weitere Versuch würde genauso scheitern und kann das Postfach
     * sperren lassen.
     */
    public <T> EinzelversandErgebnis<T> versendeEinzeln(List<String> empfaenger, Einzelsendung<T> sendung) {
        List<T> verschickt = new ArrayList<>();
        List<Fehlschlag> fehlgeschlagen = new ArrayList<>();
        String abbruchGrund = null;
        for (String adresse : empfaenger) {
            if (abbruchGrund != null) {
                fehlgeschlagen.add(new Fehlschlag(adresse, abbruchGrund));
                continue;
            }
            try {
                verschickt.add(sendung.sendeAn(adresse));
            } catch (Exception e) {
                String grund = fehlerGrund(e);
                fehlgeschlagen.add(new Fehlschlag(adresse, grund));
                // Bewusst ohne Adresse geloggt (DSGVO).
                log.warn("[Einzelversand] Versand an einen Empfänger fehlgeschlagen: {}", e.getClass().getSimpleName());
                if (istAnmeldeFehler(e)) {
                    abbruchGrund = grund;
                }
            }
        }
        return new EinzelversandErgebnis<>(verschickt, fehlgeschlagen);
    }

    /** Verständlicher Grund für die Ergebnis-Anzeige. */
    static String fehlerGrund(Throwable e) {
        if (istAnmeldeFehler(e)) {
            return "Anmeldung am Postfach fehlgeschlagen";
        }
        Throwable t = e;
        while (t != null) {
            if (t instanceof AddressException) {
                return "Adresse ungültig";
            }
            if (t instanceof SendFailedException) {
                return "Empfänger vom Mail-Server abgelehnt";
            }
            t = t.getCause();
        }
        return "Versand fehlgeschlagen";
    }

    private static boolean istAnmeldeFehler(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof AuthenticationFailedException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    // ==================== intern ====================

    private Optional<EmailAbsender> hauptpostfach() {
        return postfachRepository.findFirstByHauptpostfachTrueOrderByIdAsc().filter(EmailAbsender::isAktiv);
    }

    private Optional<EmailAbsender> postfachMitAdresse(String adresse) {
        if (adresse == null || adresse.isBlank()) {
            return Optional.empty();
        }
        Set<String> gesucht = EmailThreadTeilnehmer.adressen(adresse);
        return postfachRepository.findAllByOrderBySortierungAscIdAsc().stream()
                .filter(p -> p.getEmailAdresse() != null
                        && gesucht.contains(p.getEmailAdresse().trim().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private static Optional<EmailAbsender> erstesMitAdresseIn(List<EmailAbsender> postfaecher, String kopfzeile) {
        if (kopfzeile == null || kopfzeile.isBlank()) {
            return Optional.empty();
        }
        Set<String> adressen = EmailThreadTeilnehmer.adressen(kopfzeile).stream()
                .map(a -> a.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return postfaecher.stream()
                .filter(p -> p.getEmailAdresse() != null
                        && adressen.contains(p.getEmailAdresse().trim().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private static String anzeigenameOder(EmailAbsender postfach, String ersatz) {
        return postfach.getAnzeigename() != null && !postfach.getAnzeigename().isBlank()
                ? postfach.getAnzeigename()
                : ersatz;
    }

    /** Eigene Methode, damit Tests den Dienst ohne echten Mail-Server prüfen können. */
    EmailService dienst(SystemSettingsService.MailKonto konto, String anzeigename) {
        return new EmailService(konto.host(), konto.port(), konto.username(), konto.password())
                .mitAbsenderName(anzeigename);
    }
}
