package org.example.kalkulationsprogramm.dto.Einkauf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.einkauf.Dokumentart;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;

public final class EinkaufPositionDto {
    private EinkaufPositionDto() {
    }

    /**
     * Menge einer Position. {@code gesamtgewichtKg} und {@code mantelflaecheM2} sind optionale Gesamtwerte
     * für genau diese Menge (z. B. aus der HiCAD-Profilsummenliste). Sie wachsen und schrumpfen mit der Menge
     * und gehören deshalb hierher und nicht zur Identität der Position. Alte JSON-Stände ohne die Felder
     * werden mit {@code null} gelesen.
     */
    public record Mengenbasis(BigDecimal menge, Einheit einheit, BigDecimal stueckzahl,
            BigDecimal einzelLaengeMm, BigDecimal kgJeMeter, String faktorQuelle,
            BigDecimal gesamtgewichtKg, BigDecimal mantelflaecheM2) {
        public Mengenbasis(BigDecimal menge, Einheit einheit, BigDecimal stueckzahl,
                BigDecimal einzelLaengeMm, BigDecimal kgJeMeter, String faktorQuelle) {
            this(menge, einheit, stueckzahl, einzelLaengeMm, kgJeMeter, faktorQuelle, null, null);
        }

        /**
         * Gleiche Basis mit neuer Menge; Gewicht und Mantelfläche werden anteilig mitgeführt.
         * Bei anderer Einheit lässt sich der Anteil nicht bestimmen – dann entfallen beide Werte.
         */
        public Mengenbasis mitAnteiligenGesamtwerten(BigDecimal neueMenge, Einheit neueEinheit, BigDecimal neueStueckzahl) {
            boolean gleicheEinheit = neueEinheit == einheit;
            return new Mengenbasis(neueMenge, neueEinheit, neueStueckzahl, einzelLaengeMm, kgJeMeter, faktorQuelle,
                    gleicheEinheit ? anteilig(gesamtgewichtKg, neueMenge, 3) : null,
                    gleicheEinheit ? anteilig(mantelflaecheM2, neueMenge, 4) : null);
        }

        private BigDecimal anteilig(BigDecimal gesamt, BigDecimal neueMenge, int stellen) {
            if (gesamt == null || menge == null || neueMenge == null || menge.signum() <= 0) return null;
            if (neueMenge.compareTo(menge) == 0) return gesamt;
            BigDecimal wert = gesamt.multiply(neueMenge).divide(menge, stellen, java.math.RoundingMode.HALF_UP);
            return wert.signum() > 0 ? wert : null;
        }
    }

    public record DokumentSoll(Dokumentart art, String grundlage, String grundlageVersion,
            boolean fachlichBestaetigt) {
    }

    /** Supplier and workshop details retained with the immutable purchase position. */
    public record Beschaffungsdetails(Long lieferantId, Long kategorieId, Long schnittbildId,
            Long schnittAchseId, String externeArtikelnummer) {}

    /**
     * {@code positionsnummer} ist die Positionsnummer aus der Konstruktion (HiCAD „Pos.“, bei Stangenware
     * mehrere, z. B. „1100, 1102“). Sie ist bewusst ein eigenes Feld: Bei Katalogartikeln überschreibt der
     * Artikel die {@code interneReferenz} mit seiner Artikelnummer, die Pos.-Nummer muss aber erhalten bleiben.
     */
    public record PositionSnapshot(Positionsart art, Long artikelId, String interneReferenz,
            String zeichnungsnummer, String zeichnungsrevision, String bezeichnung, String werkstoff,
            String abmessung, Mengenbasis basis, String schnittForm, String winkelLinks,
            String winkelRechts, String bearbeitung, String oberflaeche, List<DokumentSoll> dokumente,
            List<Long> anlageVersionIds, Beschaffungsdetails beschaffungsdetails, String positionsnummer) {
        public PositionSnapshot(Positionsart art, Long artikelId, String interneReferenz,
                String zeichnungsnummer, String zeichnungsrevision, String bezeichnung, String werkstoff,
                String abmessung, Mengenbasis basis, String schnittForm, String winkelLinks,
                String winkelRechts, String bearbeitung, String oberflaeche, List<DokumentSoll> dokumente,
                List<Long> anlageVersionIds, Beschaffungsdetails beschaffungsdetails) {
            this(art, artikelId, interneReferenz, zeichnungsnummer, zeichnungsrevision, bezeichnung,
                    werkstoff, abmessung, basis, schnittForm, winkelLinks, winkelRechts, bearbeitung,
                    oberflaeche, dokumente, anlageVersionIds, beschaffungsdetails, null);
        }

        public PositionSnapshot(Positionsart art, Long artikelId, String interneReferenz,
                String zeichnungsnummer, String zeichnungsrevision, String bezeichnung, String werkstoff,
                String abmessung, Mengenbasis basis, String schnittForm, String winkelLinks,
                String winkelRechts, String bearbeitung, String oberflaeche, List<DokumentSoll> dokumente,
                List<Long> anlageVersionIds) {
            this(art, artikelId, interneReferenz, zeichnungsnummer, zeichnungsrevision, bezeichnung,
                    werkstoff, abmessung, basis, schnittForm, winkelLinks, winkelRechts, bearbeitung,
                    oberflaeche, dokumente, anlageVersionIds, null, null);
        }

        public PositionSnapshot {
            dokumente = dokumente == null ? List.of() : List.copyOf(dokumente);
            anlageVersionIds = anlageVersionIds == null ? List.of() : List.copyOf(anlageVersionIds);
        }

        /** Kopie mit anderer Mengenbasis; alle übrigen Felder (inkl. Positionsnummer) bleiben erhalten. */
        public PositionSnapshot mitBasis(Mengenbasis neueBasis) {
            return new PositionSnapshot(art, artikelId, interneReferenz, zeichnungsnummer, zeichnungsrevision,
                    bezeichnung, werkstoff, abmessung, neueBasis, schnittForm, winkelLinks, winkelRechts, bearbeitung,
                    oberflaeche, dokumente, anlageVersionIds, beschaffungsdetails, positionsnummer);
        }
    }

    public record Herkunft(Long bedarfId, long version, BigDecimal menge) {
    }

    public record Liefergruppe(String lieferadresse, LocalDate bedarfstermin, Long projektId,
            String lagerzweck) {
    }
}
