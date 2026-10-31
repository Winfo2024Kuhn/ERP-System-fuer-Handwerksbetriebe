package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiNichtVerfuegbar;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
import org.example.kalkulationsprogramm.service.ki.KiZuordnung;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

/**
 * KI-Zugang speziell für die E-Mail-Zuordnung (eingehende E-Mails zu Projekten
 * und Anfragen). Läuft über {@link KiFunktion#EMAIL_ZUORDNUNG} mit der schnellen
 * Modellstufe, damit die Zuordnung zügig und günstig bleibt.
 */
@Service
@RequiredArgsConstructor
public class EmailClassificationKiClient {

    private final KiDienst kiDienst;

    public boolean isEnabled() {
        return kiDienst.verfuegbar(KiFunktion.EMAIL_ZUORDNUNG);
    }

    /** Anbieter, über den die Zuordnung gerade läuft, z. B. {@code mistral}; {@code keiner} ohne. */
    public String anbieter() {
        KiZuordnung zuordnung = kiDienst.zuordnung(KiFunktion.EMAIL_ZUORDNUNG);
        return zuordnung.verfuegbar() ? zuordnung.anbieter().typ().kuerzel() : "keiner";
    }

    /**
     * Sendet eine Klassifizierungs-Anfrage.
     *
     * @param systemPrompt System-Anweisungen (Rollenbeschreibung, Output-Format)
     * @param userMessage  Benutzer-Inhalt (eingehende Email + Kandidaten)
     * @return Roher Antwort-Text (typischerweise JSON)
     * @throws IOException wenn keine KI eingerichtet ist oder der Anbieter einen Fehler meldet
     */
    public String chat(String systemPrompt, String userMessage) throws IOException {
        KiAnfrage anfrage = KiAnfrage.mitAnweisung(systemPrompt)
                .nutzer(KiTeil.text(userMessage))
                // Niedrige Temperatur für eine gleichbleibende Zuordnung, JSON erzwingen
                .temperatur(0.1)
                .json()
                .zeitlimit(Duration.ofSeconds(60))
                .bauen();
        try {
            return kiDienst.sende(KiFunktion.EMAIL_ZUORDNUNG, anfrage).text();
        } catch (KiNichtVerfuegbar | KiAnbieterFehler e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
