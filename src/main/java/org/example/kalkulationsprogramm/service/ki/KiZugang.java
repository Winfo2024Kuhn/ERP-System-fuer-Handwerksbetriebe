package org.example.kalkulationsprogramm.service.ki;

/**
 * Schlüssel und Modell für Dienste, die (noch) selbst mit Gemini sprechen:
 * Programm-Hilfe, Kostenkonto-Vorschlag, Spracheingabe, Wissenssuche.
 * {@link #toString()} verrät den Schlüssel nicht.
 */
public record KiZugang(String schluessel, String modell) {

    @Override
    public String toString() {
        return "KiZugang[modell=" + modell + "]";
    }
}
