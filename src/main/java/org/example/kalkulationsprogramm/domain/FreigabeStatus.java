package org.example.kalkulationsprogramm.domain;

public enum FreigabeStatus
{
    PENDING("Wartet auf Kunde"),
    ACCEPTED("Angenommen"),
    EXPIRED("Abgelaufen"),
    REVOKED("Zurückgezogen");

    private final String label;

    FreigabeStatus(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }
}
