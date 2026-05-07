package org.example.kalkulationsprogramm.dto.Freigabe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
}
