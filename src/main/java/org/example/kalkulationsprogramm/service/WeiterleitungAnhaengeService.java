package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Lädt die Anhänge, die beim Weiterleiten aus der Original-Mail mitgehen sollen (Issue #271).
 *
 * <p>Der Browser schickt nur die IDs; die Dateien liest der Server selbst. So muss niemand
 * etwas herunter- und wieder hochladen, und es geht nur mit, was wirklich an der
 * Original-Mail hängt und der Benutzer sehen darf.</p>
 */
@Service
@RequiredArgsConstructor
public class WeiterleitungAnhaengeService {

    private final EmailAttachmentProcessingService anhangDateien;
    private final LieferantDokumentZugriffService lieferantDokumentZugriffService;

    /**
     * Anhänge der Original-Mail in der gewählten Reihenfolge, doppelte IDs nur einmal.
     * Erst werden alle Größen geprüft, dann gelesen – zu große Auswahl lädt nichts in den Speicher.
     *
     * @param sichtbareTypen Lieferanten-Dokumenttypen, die der Benutzer sehen darf – als
     *                       Lieferanten-Dokument eines gesperrten Typs abgelegte Anhänge gehen
     *                       nicht mit (sonst umginge das Weiterleiten die Dokumentrechte)
     * @param maxBytesJeAnhang Grenze je Datei
     * @param maxBytesGesamt   noch freier Platz für alle weitergeleiteten Anhänge zusammen
     * @throws IllegalArgumentException mit einer Meldung für den Benutzer
     */
    public List<EmailService.Attachment> lade(Email original, List<Long> anhangIds,
            Set<LieferantDokumentTyp> sichtbareTypen, long maxBytesJeAnhang, long maxBytesGesamt) {
        if (anhangIds == null || anhangIds.isEmpty()) {
            return List.of();
        }
        Map<Long, EmailAttachment> anhaengeDerMail = original.getAttachments().stream()
                .filter(a -> a.getId() != null)
                .collect(Collectors.toMap(EmailAttachment::getId, Function.identity(), (a, b) -> a));

        List<EmailAttachment> gewaehlt = new ArrayList<>();
        List<Path> pfade = new ArrayList<>();
        long gesamt = 0;
        for (Long id : new LinkedHashSet<>(anhangIds.stream().filter(Objects::nonNull).toList())) {
            EmailAttachment anhang = anhaengeDerMail.get(id);
            if (anhang == null || !lieferantDokumentZugriffService.istAnhangSichtbar(id, sichtbareTypen)) {
                throw new IllegalArgumentException("Ein Anhang gehört nicht zur weitergeleiteten E-Mail.");
            }
            Path pfad = pfad(anhang);
            long groesse = groesse(pfad);
            if (groesse > maxBytesJeAnhang) {
                throw new IllegalArgumentException("Der Anhang '" + anzeigeName(anhang)
                        + "' überschreitet die maximal zulässige Einzelgröße von 15 MB.");
            }
            gesamt += groesse;
            if (gesamt > maxBytesGesamt) {
                throw new IllegalArgumentException(
                        "Die Anhänge überschreiten die maximal zulässige Gesamtgröße von 25 MB.");
            }
            gewaehlt.add(anhang);
            pfade.add(pfad);
        }

        List<EmailService.Attachment> ergebnis = new ArrayList<>();
        for (int i = 0; i < gewaehlt.size(); i++) {
            EmailAttachment anhang = gewaehlt.get(i);
            try {
                ergebnis.add(new EmailService.Attachment(Files.readAllBytes(pfade.get(i)), anzeigeName(anhang),
                        anhang.getMimeType()));
            } catch (IOException e) {
                throw new UncheckedIOException("Anhang konnte nicht gelesen werden.", e);
            }
        }
        return ergebnis;
    }

    private Path pfad(EmailAttachment anhang) {
        Path pfad = anhangDateien.resolveAttachmentPath(anhang);
        if (pfad == null || !Files.isRegularFile(pfad)) {
            throw new IllegalArgumentException("Der Anhang '" + anzeigeName(anhang) + "' ist nicht mehr vorhanden.");
        }
        return pfad;
    }

    private static long groesse(Path pfad) {
        try {
            return Files.size(pfad);
        } catch (IOException e) {
            throw new UncheckedIOException("Anhang konnte nicht gelesen werden.", e);
        }
    }

    /** Dateiname ohne Pfadanteile und ohne Zeichen, die in Dateinamen verboten sind. */
    private static String anzeigeName(EmailAttachment anhang) {
        String roh = anhang.getOriginalFilename() != null && !anhang.getOriginalFilename().isBlank()
                ? anhang.getOriginalFilename()
                : "anhang";
        String name = roh.substring(Math.max(roh.lastIndexOf('/'), roh.lastIndexOf('\\')) + 1);
        return name.isBlank() ? "anhang" : name.replaceAll("[\\\\/:*?\"<>|\\x00]", "_");
    }
}
