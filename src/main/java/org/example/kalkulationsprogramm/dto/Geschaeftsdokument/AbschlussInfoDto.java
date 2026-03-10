package org.example.kalkulationsprogramm.dto.Geschaeftsdokument;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO für Abschluss-Informationen bei Rechnungen.
 * Enthält Referenzen zu Vorgängerdokumenten und bisherige Zahlungen.
 */
@Getter
@Setter
public class AbschlussInfoDto {
    // Netto/MwSt/Brutto des aktuellen Dokuments
    private BigDecimal nettosumme;
    private BigDecimal mwstSatz; // Als Dezimal, z.B. 0.19
    private BigDecimal mwstProzent; // Als Prozent, z.B. 19
    private BigDecimal mwstBetrag;
    private BigDecimal gesamtsumme;

    // Referenzen zu Vorgängerdokumenten
    private VorgaengerInfoDto angebotReferenz;
    private VorgaengerInfoDto auftragsbestaetigungReferenz;

    // Bisherige Zahlungen aus allen vorherigen Rechnungen derselben Kette
    private List<VorherigeZahlungDto> vorherigeZahlungen = new ArrayList<>();
    private BigDecimal summeVorherigerZahlungen;

    // Finaler offener Betrag
    private BigDecimal nochZuZahlen;

    // Abschlagsrechnung-Info
    private Integer aktuelleAbschlagsNummer;
    private Integer gesamtAbschlagsAnzahl;
}
