package org.example.kalkulationsprogramm.dto.Email;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Eine E-Mail-Signatur, wie sie das Signatur-Studio und die Signaturliste sehen.
 *
 * @param html             fertiges Signatur-HTML (bereinigt beim Speichern)
 * @param vorlageDaten     Formularzustand des Studios als JSON; {@code null} = von Hand geschriebenes HTML.
 *                         In der Liste immer {@code null} – erst {@code GET /{id}} liefert sie.
 * @param defaultSignature Standard-Signatur des angemeldeten Benutzers
 * @param isSystemDefault  wird an automatisch versendete Mails angehängt
 * @param besitzerId       Benutzer, dem die Signatur gehört; {@code null} = ohne Besitzer (nur Admins)
 * @param eigene           gehört dem angemeldeten Benutzer
 * @param images           Bilder alter Datei-Uploads (Altbestand)
 */
public record EmailSignaturDto(
        Long id,
        String name,
        String html,
        String vorlageDaten,
        boolean defaultSignature,
        @JsonProperty("isSystemDefault") boolean isSystemDefault,
        Long besitzerId,
        String besitzerName,
        boolean eigene,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Bild> images) {

    /** Bild aus einem alten Datei-Upload, im HTML per {@code cid:} eingebunden. */
    public record Bild(Long id, String cid, String originalFilename, String contentType, long sizeBytes) {
    }
}
