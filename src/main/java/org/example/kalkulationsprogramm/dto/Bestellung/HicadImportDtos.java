package org.example.kalkulationsprogramm.dto.Bestellung;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO-Container für den HiCAD-Sägelisten-Import.
 * Flow: Upload → Preview (Backend parst + matched) → User bestätigt → Confirm (Backend legt Positionen an).
 */
public final class HicadImportDtos {

    private HicadImportDtos() {}

    /**
     * Eine geparste Sägeliste-Zeile — repräsentiert den Rohdaten-Stand aus der Excel.
     */
    @Getter
    @Setter
    public static class SaegelisteZeileDto {
        private Integer posNr;
        private Integer anzahl;
        private String bezeichnung;
        private Integer laengeMm;
        private String werkstoff;
        private String anschnittSteg;
        private String anschnittFlansch;
        private BigDecimal gewichtProStueckKg;
        private BigDecimal gesamtGewichtKg;
    }

    /**
     * Gruppierung nach (Bezeichnung + Werkstoff) — in dieser Granularität fragt das UI den User,
     * ob als Fixzuschnitt oder aggregiert (Stangenware) bestellt werden soll.
     */
    @Getter
    @Setter
    public static class ProfilGruppeDto {
        /** Stabiler Gruppen-Key (für React-Keys + Confirm-Request). */
        private String groupKey;
        private String bezeichnung;
        private String werkstoff;

        /** Gematchter Stammartikel (optional — null, wenn kein Treffer). */
        private Long artikelId;
        private String artikelProduktname;

        /** Verpackungseinheit aus Artikel (in Metern) — Basis für Stangenware-Berechnung. */
        private Long verpackungseinheitM;

        /** Vorschlag „Fixzuschnitt" (false) oder „selbst schneiden" (true) — kann User überstimmen. */
        private boolean defaultAggregieren;

        /** Summe aller Zuschnitt-Meter (Anzahl × Länge). */
        private BigDecimal summeMeter;
        /** Summe Stück (alle Anzahl-Werte der Zeilen). */
        private Integer summeStueck;
        /** Bei Aggregation: berechnete Stäbe = ceil(summeMeter / verpackungseinheit). */
        private Integer berechneteStaebe;

        private List<SaegelisteZeileDto> zeilen;
    }

    /**
     * Preview-Response nach Upload: Header + gruppierte Positionen.
     * Frontend zeigt das in der Review-Tabelle.
     */
    @Getter
    @Setter
    public static class PreviewResponseDto {
        // Header der Sägeliste
        private String zeichnungsnr;
        private String auftragsnummer;
        private String auftragstext;
        private String kunde;
        private String ersteller;
        private String erstelltAm;

        // Gefundenes Projekt per Auftragsnummer (optional — User kann zuordnen)
        private Long erkannteProjektId;
        private String erkannteProjektName;

        private List<ProfilGruppeDto> gruppen;
    }

    /**
     * Ein Import-Eintrag beim Confirm. Entspricht einer Gruppe mit User-Entscheidung.
     */
    @Getter
    @Setter
    public static class ConfirmGruppeDto {
        private String groupKey;
        private Long projektId;
        private Long lieferantId;
        private Long artikelId;
        /** Von User gewählte Kategorie für Freitext-Fall (wenn artikelId null). */
        private Integer kategorieId;

        /** true = eine Position pro Stange (aggregiert), false = 1:1 pro Sägeliste-Zeile. */
        private boolean aggregieren;

        /** Manuell überschriebene Stangenlänge in Metern (Fallback, wenn Artikel.verpackungseinheit fehlt). */
        private Long stangenlaengeM;
    }

    /**
     * Confirm-Request vom Frontend.
     */
    @Getter
    @Setter
    public static class ConfirmRequestDto {
        private Long projektId;
        private String kommentarPrefix;
        private List<ConfirmGruppeDto> gruppen;
        /**
         * Alle Gruppen aus dem Preview-Response unverändert zurückgespielt —
         * Backend nimmt die Zeilen/Bezeichnung/Werkstoff/Längen daraus, damit das Confirm
         * zustandslos bleibt (kein Cache zwischen Requests).
         */
        private List<ProfilGruppeDto> preview;
    }

    /**
     * Confirm-Response: neu angelegte Bestellpositionen.
     */
    @Getter
    @Setter
    public static class ConfirmResponseDto {
        private int angelegtePositionen;
        private List<Long> positionIds;
    }
}
