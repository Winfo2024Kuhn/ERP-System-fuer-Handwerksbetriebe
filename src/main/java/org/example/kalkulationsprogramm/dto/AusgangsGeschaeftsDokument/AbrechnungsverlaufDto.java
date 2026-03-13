package org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument;

import lombok.Data;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * DTO für den Abrechnungsverlauf eines Basisdokuments (Anfrage/AB).
 * Zeigt alle bereits erstellten Rechnungen und den verbleibenden Restbetrag.
 */
@Data
public class AbrechnungsverlaufDto {

    /** ID des Basisdokuments (Anfrage oder AB) */
    private Long basisdokumentId;

    /** Dokumentnummer des Basisdokuments */
    private String basisdokumentNummer;

    /** Typ des Basisdokuments (ANFRAGE oder AUFTRAGSBESTAETIGUNG) */
    private AusgangsGeschaeftsDokumentTyp basisdokumentTyp;

    /** Datum des Basisdokuments */
    private LocalDate basisdokumentDatum;

    /** Nettobetrag des Basisdokuments */
    private BigDecimal basisdokumentBetragNetto;

    /** Liste aller Rechnungen, die aus diesem Basisdokument erstellt wurden */
    private List<AbrechnungspositionDto> positionen;

    /** Summe aller bereits abgerechneten Beträge (nicht-storniert) */
    private BigDecimal bereitsAbgerechnet;

    /** Verbleibender Restbetrag = basisdokumentBetragNetto - bereitsAbgerechnet */
    private BigDecimal restbetrag;

    /** Block-IDs aus positionenJson, die bereits in nicht-stornierten Teilrechnungen abgerechnet wurden */
    private Set<String> bereitsAbgerechneteBlockIds;

    /**
     * Einzelposition im Abrechnungsverlauf (eine erstellte Rechnung).
     */
    @Data
    public static class AbrechnungspositionDto {
        private Long id;
        private String dokumentNummer;
        private AusgangsGeschaeftsDokumentTyp typ;
        private LocalDate datum;
        private LocalDateTime erstelltAm;
        private BigDecimal betragNetto;
        private Integer abschlagsNummer;
        private boolean storniert;
    }
}
