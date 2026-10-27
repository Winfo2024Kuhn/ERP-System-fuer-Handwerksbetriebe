package org.example.kalkulationsprogramm.service.openfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ordner-Freigabe aus dem Docker-Paket (Dienst {@code dateifreigabe}: Samba über Tailscale).
 *
 * <p>Der Dienst schreibt beim Start Adresse und Zugang in eine Datei, die das ERP nur lesend
 * eingebunden hat ({@code erp.dateifreigabe.datei}). Damit bietet die Seite Einstellungen → Dateien
 * die Adresse zum Übernehmen an, und die Einrichtung des Hilfsprogramms bringt Benutzer und
 * Passwort gleich mit. Läuft der Dienst nicht, fehlt die Datei – dann gibt es keine Freigabe.</p>
 *
 * <p>Der Inhalt wird streng geprüft: Benutzer und Passwort landen in Befehlen der Einrichtung
 * (Windows {@code cmdkey}, Mac-Schlüsselbund) und dürfen deshalb nur unbedenkliche Zeichen
 * enthalten.</p>
 */
@Slf4j
@Service
public class ContainerFreigabe {

    /** Adresse, Benutzer und Passwort des freigegebenen Ordners; {@code rechner} ist der Teil nach {@code \\}. */
    public record Zugang(String adresse, String rechner, String benutzer, String passwort) {
        @Override
        public String toString() {
            return "Zugang[adresse=" + adresse + ", benutzer=" + benutzer + "]";
        }
    }

    private static final long MAX_GROESSE = 4096;
    private static final Pattern BENUTZER = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}+");
    private static final Pattern PASSWORT = Pattern.compile("[A-Za-z0-9]{16,128}+");

    private final String datei;
    private final String uploadOrdner;
    private final ObjectMapper objectMapper;

    public ContainerFreigabe(@Value("${erp.dateifreigabe.datei:}") String datei,
            @Value("${file.upload-dir:}") String uploadOrdner, ObjectMapper objectMapper) {
        this.datei = datei == null ? "" : datei.strip();
        this.uploadOrdner = uploadOrdner == null ? "" : uploadOrdner.strip();
        this.objectMapper = objectMapper;
    }

    /** Zugang zur Freigabe – leer, wenn keine eingeschaltet ist oder die Datei unbrauchbar ist. */
    public Optional<Zugang> zugang() {
        if (datei.isEmpty()) {
            return Optional.empty();
        }
        Path pfad = Path.of(datei);
        try {
            if (!Files.isRegularFile(pfad) || Files.size(pfad) > MAX_GROESSE) {
                return Optional.empty();
            }
            JsonNode json = objectMapper.readTree(Files.readString(pfad));
            Optional<String> adresse = OpenFileLauncherService.normalisiereUnc(json.path("adresse").asString(""));
            String benutzer = json.path("benutzer").asString("");
            String passwort = json.path("passwort").asString("");
            if (adresse.isEmpty() || !BENUTZER.matcher(benutzer).matches() || !PASSWORT.matcher(passwort).matches()) {
                log.warn("Ordner-Freigabe: {} ist unvollständig oder ungültig – wird nicht benutzt", datei);
                return Optional.empty();
            }
            String rechner = adresse.get().substring(2).split("\\\\", 2)[0];
            return Optional.of(new Zugang(adresse.get(), rechner, benutzer, passwort));
        } catch (IOException | JacksonException e) {
            log.warn("Ordner-Freigabe: {} nicht lesbar ({})", datei, e.getMessage());
            return Optional.empty();
        }
    }

    /** Nur die Adresse, z. B. {@code \\erp-dateien.tailnet.ts.net\CADdrawings}. */
    public Optional<String> adresse() {
        return zugang().map(Zugang::adresse);
    }

    /**
     * Der „Ordner auf diesem Rechner“, der zur Freigabe passt: Der Dienst gibt den gleichnamigen
     * Unterordner der Uploads frei (z. B. {@code /app/uploads/CADdrawings}). Steht in den
     * Einstellungen ein anderer Ordner, zeigten die Links nach dem Umstellen ins Leere.
     */
    public Optional<String> ordnerAufDemServer() {
        if (uploadOrdner.isEmpty()) {
            return Optional.empty();
        }
        return zugang().map(z -> {
            String adresse = z.adresse();
            String freigabe = adresse.substring(adresse.lastIndexOf('\\') + 1);
            return Path.of(uploadOrdner).resolve(freigabe).normalize().toString();
        });
    }
}
