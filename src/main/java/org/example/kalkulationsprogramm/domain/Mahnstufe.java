package org.example.kalkulationsprogramm.domain;

public enum Mahnstufe {
    ZAHLUNGSERINNERUNG("Zahlungserinnerung"),
    ERSTE_MAHNUNG("1. Mahnung"),
    ZWEITE_MAHNUNG("2. Mahnung");

    private final String beschreibung;

    Mahnstufe(String beschreibung) {
        this.beschreibung = beschreibung;
    }

    public String getBeschreibung() {
        return beschreibung;
    }
}

