package org.example.kalkulationsprogramm.service.ki;

import tools.jackson.databind.JsonNode;

/**
 * Ein Zug der KI genau so, wie ihr Anbieter ihn geliefert hat. Manche Anbieter
 * verlangen ihn im nächsten Zug unverändert zurück (Gemini: Gedanken-Signaturen,
 * eigene Teile der Google-Suche). Nur derselbe Anbieter nutzt ihn; alle anderen
 * bauen den Zug aus den neutralen {@link KiTeil}en der Nachricht.
 *
 * @param anbieter wer den Zug geliefert hat
 * @param inhalt   der Zug im Format dieses Anbieters; kann Geschäftsdaten enthalten, nie loggen
 */
public record KiRohZug(KiAnbieterTyp anbieter, JsonNode inhalt) {

    @Override
    public String toString() {
        return "KiRohZug[anbieter=" + anbieter + "]";
    }
}
