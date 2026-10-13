package org.example.kalkulationsprogramm.service;

/**
 * Was der Kunde bei der digitalen Annahme zusätzlich zu Name und Code erklärt bzw. gesehen hat –
 * der Rechtsnachweis der Freigabe (siehe V423).
 *
 * <p>Alle Texte kommen von der Firmen-Website und werden vor dem Speichern bereinigt
 * ({@link #bereinigt()}): Steuerzeichen raus, Leerraum zusammengefasst, auf die Spaltenlänge
 * gekürzt. Leere Texte werden {@code null}.</p>
 *
 * @param vorzeitigerBeginn {@code true} = Beginn vor Ablauf der Widerrufsfrist ausdrücklich
 *                          verlangt, {@code false} = nicht verlangt, {@code null} = unbekannt
 *                          (älterer Website-Stand)
 * @param widerrufStand     Stand der Widerrufsbelehrung
 * @param datenschutzStand  Stand der Datenschutzerklärung
 * @param bestaetigungText  Wortlaut des Pflicht-Häkchens
 * @param knopfText         Beschriftung des auslösenden Knopfs
 */
public record FreigabeErklaerung(Boolean vorzeitigerBeginn, String widerrufStand, String datenschutzStand,
                                 String bestaetigungText, String knopfText)
{
    /** Keine Angaben – für Aufrufer und Tests ohne Rechtsnachweis. */
    public static final FreigabeErklaerung KEINE = new FreigabeErklaerung(null, null, null, null, null);

    /** Kopie mit bereinigten, auf die Spaltenlängen gekürzten Texten. */
    public FreigabeErklaerung bereinigt()
    {
        return new FreigabeErklaerung(vorzeitigerBeginn,
                kuerze(widerrufStand, 20),
                kuerze(datenschutzStand, 20),
                kuerze(bestaetigungText, 500),
                kuerze(knopfText, 100));
    }

    private static String kuerze(String wert, int max)
    {
        if (wert == null) return null;
        String sauber = wert.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s++", " ").trim();
        if (sauber.isEmpty()) return null;
        return sauber.length() <= max ? sauber : sauber.substring(0, max);
    }
}
