package org.example.kalkulationsprogramm.service.benachrichtigung;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Was die nächtliche Sicherung und das Nachtupdate zuletzt gemeldet haben.
 *
 * <p>Beide laufen außerhalb des ERP im Updater-Container ({@code deployment/docker-server/sicherung.sh}
 * und {@code nachtupdate.sh}) und schreiben nach jedem Lauf eine kleine JSON-Datei in den Ordner
 * {@code betriebsstatus/}, den das ERP nur lesend eingebunden bekommt. Bewusst Dateien statt Datenbank:
 * Ein Rollback spielt die Datenbank zurück – eine Meldung darin wäre danach weg.
 *
 * <p>Ist kein Ordner eingestellt ({@code erp.betriebsstatus.ordner}) oder fehlt eine Datei, gibt es
 * keine Meldung: Dann ist die Installation nicht über den Updater eingerichtet.
 */
@Slf4j
@Service
public class BetriebsstatusService {

    static final String SICHERUNG_DATEI = "sicherung.json";
    static final String NACHTUPDATE_DATEI = "nachtupdate.json";
    /** Die Sicherung läuft täglich – länger nichts heißt: Rechner nachts aus oder Updater gestoppt. */
    static final Duration SICHERUNG_UEBERFAELLIG_NACH = Duration.ofHours(26);
    /** Die Skripte schreiben wenige hundert Byte; alles darüber ist keine Statusdatei von ihnen. */
    static final long MAX_DATEIGROESSE = 64 * 1024L;
    static final int MAX_TITEL = 200;
    static final int MAX_TEXT = 2000;

    private static final String ADMIN_ROLLE = "ROLE_ADMIN";
    private static final Set<String> IN_ORDNUNG = Set.of("OK", "NEU");
    private static final DateTimeFormatter SCHLUESSEL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter ANZEIGE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");

    /** Eine Meldung für die Benachrichtigungen; {@code schluessel} wechselt mit jedem Lauf. */
    public record Meldung(String schluessel, String titel, String text, LocalDateTime zeitpunkt) {
    }

    /** Inhalt einer Statusdatei, so wie die Skripte sie schreiben. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusDatei(String ergebnis, String zeitpunkt, String titel, String text, String letzterErfolg) {
    }

    private final Path ordner;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BetriebsstatusService(@Value("${erp.betriebsstatus.ordner:}") String ordner, ObjectMapper objectMapper,
            Clock clock) {
        this.ordner = ordner == null || ordner.isBlank() ? null : Path.of(ordner);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Sicherung und Updates betreffen den ganzen Betrieb – nur Admins können etwas dagegen tun. */
    public boolean darfSehen(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLLE::equals);
    }

    /**
     * Meldung zur täglichen Sicherung: wenn sie gescheitert oder nur teilweise gelungen ist – oder
     * wenn seit über einem Tag keine mehr gelaufen ist.
     */
    public Optional<Meldung> sicherung() {
        return lesen(SICHERUNG_DATEI).flatMap(status -> {
            LocalDateTime zeitpunkt = ortszeit(status.zeitpunkt());
            if (zeitpunkt == null) {
                return Optional.empty();
            }
            if (zeitpunkt.isBefore(LocalDateTime.now(clock).minus(SICHERUNG_UEBERFAELLIG_NACH))) {
                return Optional.of(sicherungUeberfaellig(status, zeitpunkt));
            }
            if (IN_ORDNUNG.contains(ergebnis(status))) {
                return Optional.empty();
            }
            String titel = titelOder(status, switch (ergebnis(status)) {
                case "TEILWEISE" -> "Sicherung nur teilweise";
                case "NOTFALL" -> "NOTFALL bei der Sicherung";
                default -> "Sicherung fehlgeschlagen";
            });
            return Optional.of(new Meldung(schluessel("SICHERUNG", zeitpunkt), titel,
                    kuerzen(zeilen(text(status), letzteVollstaendige(status)), MAX_TEXT), zeitpunkt));
        });
    }

    /** Meldung zum Nachtupdate: nur, wenn es gescheitert ist (alte Version läuft) oder ein Notfall besteht. */
    public Optional<Meldung> nachtupdate() {
        return lesen(NACHTUPDATE_DATEI).flatMap(status -> {
            LocalDateTime zeitpunkt = ortszeit(status.zeitpunkt());
            if (zeitpunkt == null || IN_ORDNUNG.contains(ergebnis(status))) {
                return Optional.empty();
            }
            String titel = titelOder(status,
                    "NOTFALL".equals(ergebnis(status)) ? "NOTFALL beim Update" : "Update fehlgeschlagen");
            return Optional.of(new Meldung(schluessel("UPDATE", zeitpunkt), titel,
                    kuerzen(text(status), MAX_TEXT), zeitpunkt));
        });
    }

    private Meldung sicherungUeberfaellig(StatusDatei status, LocalDateTime zeitpunkt) {
        String text = "NEU".equals(ergebnis(status))
                ? "Die tägliche Sicherung ist noch nie gelaufen (eingerichtet am " + zeitpunkt.format(ANZEIGE_FORMAT)
                        + "). War der Rechner nachts aus? Sonst bitte den Updater prüfen."
                : zeilen("Die letzte Sicherung lief am " + zeitpunkt.format(ANZEIGE_FORMAT)
                        + ". Seitdem wurde nicht mehr gesichert. War der Rechner nachts aus? Sonst bitte den Updater prüfen.",
                        letzteVollstaendige(status));
        // Eigener Schlüssel je letztem Lauf: Läuft wieder eine Sicherung, ist die Meldung von selbst weg.
        return new Meldung("SICHERUNG:ALT-" + zeitpunkt.format(SCHLUESSEL_FORMAT),
                "Seit über einem Tag keine Sicherung", kuerzen(text, MAX_TEXT), zeitpunkt);
    }

    private String letzteVollstaendige(StatusDatei status) {
        LocalDateTime erfolg = ortszeit(status.letzterErfolg());
        return erfolg == null ? "Es gibt noch keine vollständige Sicherung."
                : "Letzte vollständige Sicherung: " + erfolg.format(ANZEIGE_FORMAT) + ".";
    }

    private Optional<StatusDatei> lesen(String dateiname) {
        if (ordner == null) {
            return Optional.empty();
        }
        Path datei = ordner.resolve(dateiname);
        try {
            if (!Files.isRegularFile(datei) || Files.size(datei) > MAX_DATEIGROESSE) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(datei.toFile(), StatusDatei.class));
        } catch (IOException | RuntimeException e) {
            // Halb geschriebene oder fremde Datei: lieber keine Meldung als eine kaputte Glocke.
            log.debug("Statusdatei {} nicht lesbar: {}", dateiname, e.getMessage());
            return Optional.empty();
        }
    }

    /** Zeitpunkt der Skripte (ISO mit Zeitzone, z. B. {@code 2026-10-11T02:00:17+02:00}) in Ortszeit. */
    private LocalDateTime ortszeit(String wert) {
        if (wert == null || wert.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(wert.trim()).atZoneSameInstant(clock.getZone()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Unbekannte Ergebnisse gelten als Fehler – lieber einmal zu viel melden als einen Ausfall verschweigen. */
    private static String ergebnis(StatusDatei status) {
        return status.ergebnis() == null ? "FEHLER" : status.ergebnis().trim().toUpperCase(Locale.ROOT);
    }

    private static String titelOder(StatusDatei status, String standard) {
        String titel = bereinigen(status.titel());
        return titel.isBlank() ? standard : kuerzen(titel, MAX_TITEL);
    }

    private static String text(StatusDatei status) {
        return bereinigen(status.text()).strip();
    }

    private static String schluessel(String art, LocalDateTime zeitpunkt) {
        return art + ":" + zeitpunkt.format(SCHLUESSEL_FORMAT);
    }

    /** Steuerzeichen außer Zeilenumbrüchen raus – die Texte landen 1:1 in der Oberfläche. */
    private static String bereinigen(String wert) {
        return wert == null ? "" : wert.replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
    }

    private static String zeilen(String... teile) {
        return String.join("\n", Arrays.stream(teile).filter(t -> !t.isBlank()).toList());
    }

    private static String kuerzen(String wert, int max) {
        return wert.length() <= max ? wert : wert.substring(0, max - 1) + "…";
    }
}
