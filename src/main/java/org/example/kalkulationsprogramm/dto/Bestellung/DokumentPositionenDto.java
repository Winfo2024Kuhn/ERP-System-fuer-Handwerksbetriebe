package org.example.kalkulationsprogramm.dto.Bestellung;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTOs rund um die Positionen eines Lieferanten-Dokuments und die
 * Projektaufteilung nach Positionen.
 */
public final class DokumentPositionenDto {

    private DokumentPositionenDto() {
    }

    /** Eine gespeicherte Position mit ihrem aktuellen Ziel. */
    public record Position(
            Long id,
            int positionNr,
            String positionsArt,
            String externeArtikelnummer,
            String bezeichnung,
            BigDecimal menge,
            String mengeneinheit,
            BigDecimal einzelpreis,
            String preiseinheit,
            BigDecimal gesamtpreisNetto,
            Long projektId,
            String projektName,
            Long kostenstelleId,
            String kostenstelleName) {
    }

    /**
     * Alle Positionen eines Dokuments.
     *
     * @param auslesbar        der Dokumenttyp kann Positionen haben (Knopf "Positionen auslesen")
     * @param summePositionen  Summe aller Positionen netto
     * @param abweichung       Belegnetto minus Positionssumme; {@code null} ohne Belegnetto
     * @param abweichungAuffaellig Abweichung über 1 % bzw. 1 € – Hinweis im Dialog
     * @param nachPositionenAufgeteilt die gespeicherte Projektaufteilung stammt aus den Positionen
     */
    public record Uebersicht(
            Long geschaeftsdokumentId,
            String dokumentTyp,
            boolean auslesbar,
            BigDecimal betragNetto,
            BigDecimal betragBrutto,
            BigDecimal summePositionen,
            BigDecimal abweichung,
            boolean abweichungAuffaellig,
            boolean nachPositionenAufgeteilt,
            List<Position> positionen) {
    }

    /** Ziel einer Position: Projekt ODER Kostenstelle (beides leer = nicht zugeordnet). */
    public record PositionsZiel(
            @NotNull @Positive Long positionId,
            @Positive Long projektId,
            @Positive Long kostenstelleId) {
    }

    /** Zusatzangaben je Ziel (wie bei der Prozent-/Betragsaufteilung). */
    public record ZielDetail(
            @Positive Long projektId,
            @Positive Long kostenstelleId,
            @Size(max = 255) String beschreibung,
            Integer streckungJahre) {
    }

    public record AufteilungRequest(
            @NotNull @Size(max = 2000) List<@Valid @NotNull PositionsZiel> positionen,
            @Size(max = 200) List<@Valid @NotNull ZielDetail> ziele) {
    }

    /**
     * Ergebnis je Ziel.
     *
     * @param anteilProzent Anteil am Warenwert in Prozent
     * @param betrag        verbuchter Betrag: brutto bei Projekten, netto bei Kostenstellen
     */
    public record ZielBetrag(
            Long projektId,
            Long kostenstelleId,
            BigDecimal warenwert,
            BigDecimal anteilProzent,
            BigDecimal betragNetto,
            BigDecimal betragBrutto,
            BigDecimal betrag) {
    }

    /**
     * Vorschau der Aufteilung.
     *
     * @param nichtZugeordnet Warenpositionen ohne Ziel – Speichern erst bei 0
     * @param nebenkosten     Nebenkosten und Rabatte, anteilig verteilt
     * @param abweichung      Belegnetto minus Positionssumme, ebenfalls anteilig verteilt
     */
    public record Vorschau(
            List<ZielBetrag> ziele,
            int nichtZugeordnet,
            BigDecimal warenwert,
            BigDecimal nebenkosten,
            BigDecimal abweichung,
            boolean speicherbar,
            String hinweis) {
    }
}
