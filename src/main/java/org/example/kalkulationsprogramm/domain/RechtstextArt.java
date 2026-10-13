package org.example.kalkulationsprogramm.domain;

import java.time.LocalDate;

/**
 * Rechtstexte, die der Betrieb im Formularwesen selbst pflegt. Jede Art bringt einen
 * Standardtext mit (Datei unter {@code resources/rechtstexte/}), der gilt, solange keine eigene
 * Fassung gespeichert ist.
 */
public enum RechtstextArt
{
    WIDERRUFSBELEHRUNG("Widerrufsbelehrung", "rechtstexte/widerrufsbelehrung-standard.html",
            LocalDate.of(2026, 10, 13));

    private final String bezeichnung;
    private final String standardDatei;
    private final LocalDate standardStand;

    RechtstextArt(String bezeichnung, String standardDatei, LocalDate standardStand)
    {
        this.bezeichnung = bezeichnung;
        this.standardDatei = standardDatei;
        this.standardStand = standardStand;
    }

    public String getBezeichnung()
    {
        return bezeichnung;
    }

    /** Klassenpfad der Datei mit dem mitgelieferten Standardtext. */
    public String getStandardDatei()
    {
        return standardDatei;
    }

    /**
     * Stand des mitgelieferten Standardtexts. Wer den Text in der Datei ändert, hebt dieses Datum
     * an – und passt die Website-Fassung mit an.
     */
    public LocalDate getStandardStand()
    {
        return standardStand;
    }
}
