package org.example.kalkulationsprogramm.dto.Freigabe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Daten, die Astro auf der öffentlichen Freigabe-Seite anzeigen darf.
 * Bewusst keine internen IDs oder Hashwerte – nur, was der Kunde sehen soll.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class FreigabeAnsichtDto
{
    private String uuid;
    private String status;
    private String dokumentNummer;
    private String dokumentArt;
    private BigDecimal dokumentBetrag;
    private String bauvorhaben;
    private String kundeName;
    private String kundeEmail;
    private LocalDateTime erstelltAm;
    private LocalDateTime ablaufDatum;
    private LocalDateTime akzeptiertAm;
    private boolean abgelaufen;
    /** Pfad-Suffix, über das Astro die PDF beim ERP nachladen kann. */
    private String pdfPfad;

    /**
     * Positionen des Dokuments (HTML-Ansicht). {@code null}, wenn das Dokument keine
     * Positionen führt (z.B. Alt-Freigaben des Anfrage-/Projekt-Dokumentsystems).
     */
    private List<FreigabePositionDto> positionen;

    /** Summe aller NICHT-optionalen Leistungen (netto) – Basis ohne Alternativen. */
    private BigDecimal basisNetto;

    /** Basis-Betrag brutto (ohne Alternativen). */
    private BigDecimal basisBrutto;

    /** MwSt-Satz in Prozent (z.B. 19.0) für die Live-Berechnung der Endsumme im Frontend. */
    private BigDecimal mwstProzent;

    /** true, wenn das Dokument wählbare Alternativpositionen enthält. */
    private boolean hatAlternativen;
}
