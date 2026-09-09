package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.BelegKategorie;

import java.util.Map;
import java.util.Set;

/**
 * Bildet einen KI-erkannten Zahlungsart-Code (siehe
 * {@code GeminiDokumentAnalyseService.normalizeZahlungsart}) auf die
 * Stammdaten-Bezeichnung aus der {@code zahlungsart}-Tabelle (V308) ab, und
 * davon weiter auf die buchhalterische {@link BelegKategorie}.
 *
 * Reine, statische Utility-Klasse ohne Spring-Kontext: wird sowohl aus der
 * KI-Analyse als auch aus dem SQL-Backfill der Migration V372 benutzt (dort
 * nur als Dokumentationsquelle fuer die identische CASE-Tabelle) und muss
 * deshalb auch ohne Application-Context in Tests nutzbar sein.
 *
 * Verbindliche Tabelle: Entscheidung 1 des Orchestrators,
 * docs/superpowers/specs/2026-09-09-kasse-belege.md.
 */
public final class ZahlungsartMapper {

    private ZahlungsartMapper() {
    }

    /** KI-Code -> Stammdaten-Bezeichnung aus {@code zahlungsart}. */
    private static final Map<String, String> KI_CODE_ZU_STAMMDATEN = Map.ofEntries(
            Map.entry("BAR", "Bar"),
            Map.entry("EC", "EC-Karte"),
            Map.entry("EC_KARTE", "EC-Karte"),
            Map.entry("GIROCARD", "EC-Karte"),
            Map.entry("UEBERWEISUNG", "Überweisung"),
            Map.entry("SEPA_LASTSCHRIFT", "Lastschrift"),
            Map.entry("LASTSCHRIFT", "Lastschrift"),
            Map.entry("KREDITKARTE", "Kreditkarte"),
            Map.entry("PAYPAL", "PayPal"),
            Map.entry("AMAZON_PAY", "Online-Zahlung"),
            Map.entry("VORAUSKASSE", "Überweisung"),
            Map.entry("RECHNUNG", "Rechnung"),
            Map.entry("SCHECK", "Scheck")
    );

    /** Stammdaten-Bezeichnung -> Kategorie, fuer alle Bezeichnungen ausser "Bar" (die haengt von der Richtung ab). */
    private static final Map<String, BelegKategorie> STAMMDATEN_ZU_KATEGORIE = Map.ofEntries(
            Map.entry("EC-Karte", BelegKategorie.BANK),
            Map.entry("Überweisung", BelegKategorie.BANK),
            Map.entry("Lastschrift", BelegKategorie.BANK),
            Map.entry("Kreditkarte", BelegKategorie.KREDITKARTE),
            Map.entry("PayPal", BelegKategorie.BANK),
            Map.entry("Online-Zahlung", BelegKategorie.BANK),
            Map.entry("Rechnung", BelegKategorie.SONSTIGER_BELEG),
            Map.entry("Scheck", BelegKategorie.BANK)
    );

    /** KI-Codes, bei denen eine Eingangsrechnung mit dieser Zahlungsart bereits als bezahlt gilt. */
    private static final Set<String> GILT_ALS_BEZAHLT = Set.of(
            "BAR", "EC", "EC_KARTE", "GIROCARD", "KREDITKARTE", "PAYPAL", "AMAZON_PAY", "VORAUSKASSE"
    );

    /**
     * KI-Code (GeminiDokumentAnalyseService) -&gt; Stammdaten-Bezeichnung aus
     * {@code zahlungsart}. Liefert null, wenn kein eindeutiges Ziel existiert
     * (SONSTIGE, leer, unbekannt). Akzeptiert zusaetzlich die Klartext-
     * Bezeichnungen selbst ("Bar", "Überweisung", ...) und gibt sie
     * unveraendert zurueck, damit der Mapper auch fuer bereits migrierte
     * Werte idempotent ist.
     */
    public static String zuStammdaten(String kiCode) {
        String normalisiert = normalisiere(kiCode);
        if (normalisiert == null) {
            return null;
        }
        String stammdaten = KI_CODE_ZU_STAMMDATEN.get(normalisiert);
        if (stammdaten != null) {
            return stammdaten;
        }
        String getrimmt = kiCode.trim();
        return KI_CODE_ZU_STAMMDATEN.containsValue(getrimmt) ? getrimmt : null;
    }

    /**
     * Stammdaten-Bezeichnung -&gt; "Wo gezahlt" (BelegKategorie).
     * richtungAusgabe = true -&gt; Bar wird KASSE_AUSGABE, sonst KASSE_EINNAHME.
     * Liefert null, wenn keine Ableitung moeglich ist.
     */
    public static BelegKategorie zuKategorie(String stammdatenBezeichnung, boolean richtungAusgabe) {
        if (stammdatenBezeichnung == null || stammdatenBezeichnung.isBlank()) {
            return null;
        }
        String bezeichnung = stammdatenBezeichnung.trim();
        if (bezeichnung.equalsIgnoreCase("Bar")) {
            return richtungAusgabe ? BelegKategorie.KASSE_AUSGABE : BelegKategorie.KASSE_EINNAHME;
        }
        return STAMMDATEN_ZU_KATEGORIE.get(bezeichnung);
    }

    /** true, wenn eine Rechnung mit dieser Zahlungsart als bereits bezahlt gilt. */
    public static boolean giltAlsBezahlt(String kiCode) {
        String normalisiert = normalisiere(kiCode);
        return normalisiert != null && GILT_ALS_BEZAHLT.contains(normalisiert);
    }

    /** Trimmt, normalisiert auf Grossbuchstaben und vereinheitlicht Trennzeichen zu "_". */
    private static String normalisiere(String kiCode) {
        if (kiCode == null || kiCode.isBlank()) {
            return null;
        }
        return kiCode.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
