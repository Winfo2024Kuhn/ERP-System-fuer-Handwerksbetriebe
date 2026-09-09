package org.example.kalkulationsprogramm.domain;

public enum MitarbeiterArt {
    MENSCH("Mensch"),
    SYSTEM("System");

    private final String bezeichnung;

    MitarbeiterArt(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }
}
