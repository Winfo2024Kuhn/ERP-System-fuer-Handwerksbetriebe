package org.example.kalkulationsprogramm.dto.Bestellung;

import java.time.LocalDate;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

/**
 * Ein Lieferanten-Dokument in einer Kette der Bestellübersicht.
 */
public class DokumentRef {
    public Long id;
    public LieferantDokumentTyp typ;
    public String dokumentNummer;
    public LocalDate dokumentDatum;
    public Double betragBrutto;
    public Double betragNetto;
    public LocalDate liefertermin;
    /** Wann das Dokument ins System kam – Ersatz, wenn kein Belegdatum erkannt wurde. */
    public LocalDate eingangsDatum;
    /** Ausgeblendet (z. B. bezahlte Rechnung) – gehört trotzdem zur Kette. */
    public boolean ausgeblendet;
    public String dateiname;
    public String pdfUrl;
}
