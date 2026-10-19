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
     *
     * @param sichtbareTypen Lieferanten-Dokumenttypen, die der Benutzer sehen darf – als
     *                       Lieferanten-Dokument eines gesperrten Typs abgelegte Anhänge gehen
     *                       nicht mit (sonst umginge das Weiterleiten die Dokumentrechte)
     * @param maxBytesJeAnhang Grenze je Datei
     * @throws IllegalArgumentException mit einer Meldung für den Benutzer
     */
    public List<EmailService.Attachment> lade(Email original, List<Long> anhangIds,
            Set<LieferantDokumentTyp> sichtbareTypen, long maxBytesJeAnhang) {
        if (anhangIds == null || anhangIds.isEmpty()) {
            return List.of();
        }
        Map<Long, EmailAttachment> anhaengeDerMail = original.getAttachments().stream()
                .filter(a -> a.getId() != null)
                .collect(Collectors.toMap(EmailAttachment::getId, Function.identity(), (a, b) -> a));

        List<EmailService.Attachment> ergebnis = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(anhangIds.stream().filter(Objects::nonNull).toList())) {
            EmailAttachment anhang = anhaengeDerMail.get(id);
            if (anhang == null || !lieferantDokumentZugriffService.istAnhangSichtbar(id, sichtbareTypen)) {
                throw new IllegalArgumentException("Ein Anhang gehört nicht zur weitergeleiteten E-Mail.");
            }
            ergebnis.add(lies(anhang, maxBytesJeAnhang));
        }
        return ergebnis;
    }

    private EmailService.Attachment lies(EmailAttachment anhang, long maxBytesJeAnhang) {
        String name = anzeigeName(anhang);
        Path pfad = anhangDateien.resolveAttachmentPath(anhang);
        if (pfad == null || !Files.isRegularFile(pfad)) {
            throw new IllegalArgumentException("Der Anhang '" + name + "' ist nicht mehr vorhanden.");
        }
        try {
            if (Files.size(pfad) > maxBytesJeAnhang) {
                throw new IllegalArgumentException("Der Anhang '" + name
                        + "' überschreitet die maximal zulässige Einzelgröße von 15 MB.");
            }
            return new EmailService.Attachment(Files.readAllBytes(pfad), name, anhang.getMimeType());
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
