package org.example.kalkulationsprogramm.dto.WebsiteAnalytics;

import java.time.LocalDate;

/**
 * Ein Tag im Besucherverlauf der Website, aufbereitet fuer das ERP-Frontend.
 *
 * <p>Bewusst schlank: die schweren JSON-Spalten des Snapshots (Funnel,
 * Top-Seiten, Geraete, Browser, Staedte) werden fuer den Verlauf nicht
 * gelesen, sonst muesste das Frontend fuer eine Linie ueber 90 Tage
 * 90-mal ungenutzte Listen entgegennehmen.
 *
 * <p>Zur Benennung: nur {@code besucherAmTag} ist nachweislich der Wert
 * dieses einen Tages (Entity-Feld {@code visitorsToday}). Ob die
 * {@code totals*}-Felder der Website kumuliert oder tagesbezogen sind, ist
 * auf ERP-Seite nicht belegt, deshalb tragen sie hier das Wort "Gesamt"
 * und werden unveraendert durchgereicht.
 */
public record VerlaufPunktDto(
        LocalDate snapshotDate,
        long besucherAmTag,
        long besucherGesamt,
        long seitenaufrufeGesamt,
        long anfragenGesamt,
        int conversion) {
}
