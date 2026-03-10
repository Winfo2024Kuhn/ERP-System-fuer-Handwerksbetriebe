package org.example.kalkulationsprogramm.domain;

/**
 * Feste Dokumenttypen für Geschäftsdokumente und Textbaustein-Zuordnung.
 */
public enum Dokumenttyp
{
    ANGEBOT("Angebot"),
    AUFTRAGSBESTAETIGUNG("Auftragsbestätigung"),
    RECHNUNG("Rechnung"),
    TEILRECHNUNG("Teilrechnung"),
    ABSCHLAGSRECHNUNG("Abschlagsrechnung"),
    SCHLUSSRECHNUNG("Schlussrechnung"),
    ZAHLUNGSERINNERUNG("Zahlungserinnerung"),
    ERSTE_MAHNUNG("1. Mahnung"),
    ZWEITE_MAHNUNG("2. Mahnung"),
    STORNORECHNUNG("Stornorechnung"),
    GUTSCHRIFT("Gutschrift");


    private final String label;

    Dokumenttyp(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    /**
     * Findet einen Dokumenttyp anhand des Labels (case-insensitive).
     */
    public static Dokumenttyp fromLabel(String label)
    {
        if (label == null) return null;
        String trimmed = label.trim();
        for (Dokumenttyp d : values())
        {
            if (d.label.equalsIgnoreCase(trimmed))
            {
                return d;
            }
        }
        // Fallback: Versuche enum name direkt
        try
        {
            return valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Unbekannter Dokumenttyp: " + label);
        }
    }
}
