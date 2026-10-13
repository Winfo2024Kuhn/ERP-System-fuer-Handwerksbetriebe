package org.example.kalkulationsprogramm.dto.Freigabe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Antwort nach erfolgreicher Annahme. Enthält den Acceptance-Hash, damit Astro
 * ihn dem Kunden als Bestätigungs-Quittung anzeigen kann.
 */
@Getter
@Builder
@AllArgsConstructor
public class FreigabeAkzeptiertResponse
{
    private String uuid;
    private String dokumentNummer;
    private String dokumentArt;
    private LocalDateTime akzeptiertAm;
    private String hashAcceptance;
    /** Vor- und Nachname der freigebenden Person, normalisiert wie gespeichert. */
    private String unterzeichnerName;
    /** Gespeicherte Entscheidung zum vorzeitigen Beginn ({@code null} = keine Angabe). */
    private Boolean vorzeitigerBeginn;
}
