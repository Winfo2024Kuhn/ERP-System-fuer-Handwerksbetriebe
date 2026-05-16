package org.example.kalkulationsprogramm.service;

/**
 * Pluggables Chat-Backend für den Anfrage-Funnel-Spam-Filter.
 *
 * <p>Implementierungen entscheiden, woher die KI-Antwort kommt – z. B. ein
 * lokal laufendes LLM oder ein konfigurierter externer Chat-Endpoint. Der
 * eigentliche Spam-Filter-Service kennt nur dieses Interface, damit die
 * Wahl des Backends rein über Konfiguration steuerbar ist und keine
 * personenbezogenen Daten ungewollt einen bestimmten Pfad nehmen.
 */
public interface SpamFilterChatBackend {

    /** Kennung, mit der dieses Backend in den System-Settings ausgewählt wird (z. B. "lokal", "extern"). */
    String identifier();

    /** {@code true}, wenn das Backend konfiguriert und einsatzbereit ist. */
    boolean isEnabled();

    /**
     * Sendet einen System-Prompt + User-Prompt an das Backend und liefert die rohe
     * Text-Antwort zurück. Aufrufer parsen das Ergebnis selbst.
     *
     * @throws Exception bei Netzwerk-, Auth- oder Parse-Fehlern des Backends.
     */
    String chat(String systemPrompt, String userMessage) throws Exception;
}
