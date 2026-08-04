package org.example.kalkulationsprogramm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs für das Beleg-Modul (Buchhaltung).
 */
public class BelegDto {

    private BelegDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String belegKategorie;
        private String dokumentTyp;          // KI-Klassifikation: RECHNUNG, GUTSCHRIFT, ...
        private Boolean istUmbuchung;        // belegfreie Buchung (Privatentnahme, Kasse->Bank etc.)
        private String status;
        private String kiAnalyseStatus;
        private LocalDate belegDatum;
        private String belegNummer;
        private String beschreibung;
        private BigDecimal betragNetto;
        private BigDecimal betragBrutto;
        private BigDecimal mwstSatz;
        private String zahlungsart;
        private Long lieferantId;
        private String lieferantName;
        private Long sachkontoId;
        private String sachkontoBezeichnung;
        private String sachkontoNummer;
        private String sachkontoTyp;
        // Echte Zuordnung Kostenstelle ("wofuer war die Ausgabe")
        private Long kostenstelleId;
        private String kostenstelleBezeichnung;
        private String kostenstelleTyp;
        private Boolean kostenstelleIstFixkosten;
        private String kiVorgeschlagenerLieferant;
        private BigDecimal kiConfidence;
        // KI-Agent-Vorschlag fuer Kostenstelle + Sachkonto (aus DB-Liste gewaehlt)
        private Long kiVorgeschlagenerKostenstelleId;
        private String kiVorgeschlagenerKostenstelleBezeichnung;
        private Long kiVorgeschlagenerSachkontoId;
        private String kiVorgeschlagenerSachkontoBezeichnung;
        private BigDecimal kiKostenkontoConfidence;
        private String kiKostenkontoBegruendung;
        private String kiFehlerText;
        private String originalDateiname;
        private String mimeType;
        private LocalDateTime uploadDatum;
        private Long uploadedById;
        private String uploadedByName;
        private LocalDateTime validiertAm;
        private Long validiertVonId;
        private String validiertVonName;
        private String notiz;
        // Falls aus dem Beleg automatisch ein Eingangsrechnungs-Datensatz erzeugt wurde,
        // verweist dieses Feld auf die Eingangsrechnungs-ID (LieferantGeschaeftsdokument.id).
        private Long eingangsrechnungId;
        // Beleg-Aufteilung (VOLLSTAENDIG / TEILWEISE) und die per Checkbox-Auswahl
        // berechneten Firma-Summen. Bei VOLLSTAENDIG sind die betragFirma*-Felder null
        // und der Buchhalter liest die Standard-Betraege.
        private String aufteilungsModus;
        private BigDecimal betragFirmaNetto;
        private BigDecimal betragFirmaBrutto;
        private BigDecimal betragFirmaMwst;
        private List<PositionResponse> positionen;
        // Kostenstellen-Splits (#60): mehrere Kostenstellen pro Beleg mit
        // Prozent/Absolut-Verteilung und optionaler Streckung ueber mehrere
        // Jahre. Wenn leer, gilt weiterhin die Einzel-Kostenstelle (kostenstelleId).
        private List<KostenstellenSplitDto> kostenstellenSplits;
        // Festschreibung (GoBD): sobald festgeschrieben, sperrt das Frontend
        // Datum, Betrag, MwSt, Art der Buchung, Zahlungsart, Verwendungszweck
        // und Belegnummer. Die Kontierung bleibt bedienbar.
        private Long laufendeNummer;
        private Boolean festgeschrieben;
        private LocalDateTime festgeschriebenAm;
        // Storno-Verweise in beide Richtungen, damit das Frontend an beiden
        // Zeilen zeigen kann, dass sie zusammengehoeren.
        private Long stornoFuerBelegId;
        private Long storniertDurchBelegId;
        private LocalDateTime storniertAm;
        private String stornoGrund;
        /** SHA-256 der Belegdatei – Nachweis, dass das Bild nicht ausgetauscht wurde. */
        private String dateiHash;
    }

    /**
     * Ein Kostenstellen-Anteil am Beleg (Issue #60). Beim Speichern reicht
     * eines von {@code prozent} und {@code absoluterBetrag}; das andere bleibt
     * null. {@code berechneterBetrag} ist read-only und wird vom Backend
     * gesetzt. {@code streckungJahre} > 1 streckt den Anteil ueber mehrere
     * Auswertungsjahre — Default 1 (kein Streck-Effekt).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KostenstellenSplitDto {
        private Long id;
        private Long kostenstelleId;
        private String kostenstelleBezeichnung;
        private Boolean kostenstelleIstFixkosten;
        private Integer prozent;
        private BigDecimal absoluterBetrag;
        private BigDecimal berechneterBetrag;
        private String beschreibung;
        private Integer streckungJahre;
        private Integer streckungStartJahr;
    }

    /**
     * Eine einzelne KI-extrahierte Beleg-Position fuer das Checkbox-UI am Handy.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionResponse {
        private Long id;
        private int sortierung;
        private String beschreibung;
        private BigDecimal menge;
        private String einheit;
        private BigDecimal einzelpreis;
        private BigDecimal betragNetto;
        private BigDecimal betragBrutto;
        private BigDecimal mwstSatz;
        private boolean istFuerFirma;
    }

    /**
     * Request des Mobile-Clients zum Speichern der Checkbox-Auswahl.
     * {@code firmaPositionIds} ist die vollstaendige Ist-Liste — alle nicht
     * enthaltenen Positionen werden auf {@code istFuerFirma=false} gesetzt.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionAuswahlRequest {
        private List<Long> firmaPositionIds;
    }

    /**
     * Request fuer den MwSt-Rechner ({@code POST /api/buchhaltung/mwst-rechner}).
     * Genau eines der drei Felder darf null sein — der Service rechnet es aus.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MwstRechnerRequest {
        private BigDecimal netto;
        private BigDecimal brutto;
        private BigDecimal satzProzent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MwstRechnerResponse {
        private BigDecimal netto;
        private BigDecimal brutto;
        private BigDecimal satzProzent;
        private BigDecimal mwstBetrag;
    }

    /**
     * Request-Body für die Validierung am PC. Alle Felder optional — nur das, was
     * der Buchhalter geändert hat, wird gesendet.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String belegKategorie;
        private String status;
        private LocalDate belegDatum;
        private String belegNummer;
        private String beschreibung;
        private BigDecimal betragNetto;
        private BigDecimal betragBrutto;
        private BigDecimal mwstSatz;
        private String zahlungsart;
        private Long lieferantId;
        private Long sachkontoId;
        private Long kostenstelleId;
        private String notiz;
        // Wechsel zwischen VOLLSTAENDIG <-> TEILWEISE am PC moeglich, falls der
        // Buchhalter nachtraeglich umschwenkt (z.B. urspruenglich VOLLSTAENDIG
        // gescannt, jetzt doch nur Teile fuer Firma).
        private String aufteilungsModus;
        // Kostenstellen-Splits (#60). null = "nicht aenderbar" (Liste bleibt
        // unveraendert), leere Liste = "alle bestehenden Splits loeschen".
        private List<KostenstellenSplitDto> kostenstellenSplits;
    }

    /**
     * Antwort von /api/buchhaltung/me/permissions — sagt dem Frontend, ob der
     * eingeloggte Mitarbeiter Belege scannen / sehen darf.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionResponse {
        private boolean darfScannen;
        private boolean darfSehen;
    }

    /**
     * Eintrag im Kassenbuch (chronologisch). Saldo ist der laufende Bestand
     * NACH dieser Bewegung.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KassenBewegung {
        private Long belegId;
        private LocalDate datum;
        private String kategorie;
        private String beschreibung;
        private String lieferantName;
        private BigDecimal betrag;          // signiert: + Einnahme, - Ausgabe/Privatentnahme
        private BigDecimal saldoNachher;
        /**
         * Dauerhafte, lückenlose Belegnummer aus dem Monatsabschluss. null =
         * der Monat ist noch offen. Ersetzt die früher im Frontend gezählte
         * Zeilennummer, die sich mit jedem Zeitraumwechsel verschoben hat.
         */
        private Long laufendeNummer;
        private Boolean festgeschrieben;
        private String sachkontoNummer;
        private String sachkontoBezeichnung;
        private String zahlungsart;
        private BigDecimal mwstSatz;
        private BigDecimal mwstBetrag;
        /** Diese Zeile ist die Gegenbuchung zu jener Beleg-ID. */
        private Long stornoFuerBelegId;
        /** Diese Zeile wurde durch jene Beleg-ID aufgehoben. */
        private Long storniertDurchBelegId;
    }

    /**
     * Eine Zeile fuer den Steuerberater-Beleg-Export (Issue #58).
     *
     * Pro Beleg ein Eintrag — bei {@code aufteilungsModus=TEILWEISE} stehen in
     * {@code betragNetto/Brutto/Mwst} die Firma-Anteile, nicht die Gesamt-
     * Belegsummen. Das Frontend rendert daraus eine HTML-Tabelle, die der
     * Buchhalter im E-Mail-Modal noch editieren kann. Beleg-PDFs werden NICHT
     * mitgeschickt — der Steuerberater hat die physischen Belege bereits;
     * Spalte {@code belegNummer} ist die Referenz dafuer.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SteuerberaterExportEntry {
        private Long belegId;
        private LocalDate belegDatum;
        private String belegNummer;
        private String lieferantName;
        private String belegKategorie;
        private String dokumentTyp;
        private String sachkontoNummer;
        private String sachkontoBezeichnung;
        // Bei TEILWEISE: Firma-Anteil. Bei VOLLSTAENDIG: Gesamtsumme.
        private BigDecimal betragNetto;
        private BigDecimal betragBrutto;
        private BigDecimal betragMwst;
        private BigDecimal mwstSatz;
        private String notiz;
        private String beschreibung;
        // Nur gesetzt bei TEILWEISE — gibt dem Steuerberater den Hinweis, dass
        // hier nur ein Teil betrieblich war + die Gesamt-Brutto-Summe des
        // Original-Belegs zum Abgleich mit dem physisch vorliegenden Beleg.
        private String aufteilungsModus;
        private BigDecimal gesamtBruttoOriginal;
        private Integer anzahlPositionenGesamt;
        private Integer anzahlPositionenFirma;
        // Kurztext mit den gewaehlten Position-Beschreibungen (max ~3),
        // damit der Steuerberater nicht jeden Beleg manuell aufschluesseln muss.
        private String positionenHinweis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KassenbuchResponse {
        private BigDecimal saldoStart;
        private BigDecimal saldoEnde;
        private BigDecimal summeEinnahmen;
        private BigDecimal summeAusgaben;
        private BigDecimal summePrivatentnahmen;
        private BigDecimal summePrivateinlagen;
        private List<KassenBewegung> bewegungen;
        /**
         * Letzter abgeschlossener Monat als "JJJJ-MM" – bis hierhin ist alles
         * festgeschrieben. null = es wurde noch nie ein Monat abgeschlossen.
         */
        private String letzterAbschluss;
        /** Wie viele Bewegungen im Zeitraum noch offen (änderbar) sind. */
        private int offeneBewegungen;
    }

    /**
     * Request zur Erfassung einer Umbuchung OHNE Beleg-Datei.
     *
     * Use-Cases:
     *  - Privatentnahme (Bargeld aus Kasse, kein Beleg vorhanden)
     *  - Umbuchung Kasse -> Bank
     *  - Privat -> Firma
     *  - Geldeingang auf Konto (ohne Bankauszug-Scan)
     *
     * Pflichtfelder: belegKategorie + betragBrutto + belegDatum.
     * Die Kategorie muss eine Kassen-Bewegungskategorie sein
     * (KASSE_EINNAHME|KASSE_AUSGABE|PRIVATENTNAHME|PRIVATEINLAGE|BANK|KREDITKARTE) — eine
     * Eingangsrechnung kann nicht als Umbuchung erfasst werden, weil das
     * weder GoBD- noch DSGVO-konform waere (es waere dann eine Buchung ohne
     * Originalbeleg, die als Rechnung in die Buchhaltung einfliesst).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UmbuchungCreateRequest {
        private String belegKategorie;
        private LocalDate belegDatum;
        private BigDecimal betragBrutto;
        private String beschreibung;
        private String zahlungsart;
        private Long sachkontoId;
        private String notiz;
    }
}
