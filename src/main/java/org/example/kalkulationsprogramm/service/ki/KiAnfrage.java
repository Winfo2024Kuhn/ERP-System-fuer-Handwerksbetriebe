package org.example.kalkulationsprogramm.service.ki;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Anbieterneutrale Anfrage an eine KI. Jeder {@link KiAnbieter} übersetzt sie
 * in sein eigenes Format.
 *
 * @param systemAnweisung feste Anweisung an die KI, darf leer sein
 * @param nachrichten     Gesprächsverlauf, die letzte Nachricht ist die aktuelle Runde
 * @param jsonAntwort     true: die KI antwortet mit einem JSON-Objekt
 * @param schema          optional die Form der JSON-Antwort, im Gemini-Stil
 *                        ({@code "type": "OBJECT"}); andere Anbieter übersetzen es
 * @param temperatur      optional, 0 = möglichst gleichbleibende Antworten
 * @param maxAusgabe      optional, höchstens so viele Tokens Antwort
 * @param zeitlimit       wie lange auf die Antwort gewartet wird
 */
public record KiAnfrage(
        String systemAnweisung,
        List<KiNachricht> nachrichten,
        boolean jsonAntwort,
        JsonNode schema,
        Double temperatur,
        Integer maxAusgabe,
        Duration zeitlimit) {

    static final Duration STANDARD_ZEITLIMIT = Duration.ofSeconds(90);

    public KiAnfrage {
        systemAnweisung = systemAnweisung == null ? "" : systemAnweisung;
        nachrichten = nachrichten == null ? List.of() : List.copyOf(nachrichten);
        zeitlimit = zeitlimit == null ? STANDARD_ZEITLIMIT : zeitlimit;
    }

    public static Bauer mitAnweisung(String systemAnweisung) {
        return new Bauer(systemAnweisung);
    }

    /** Alle Dateien der Anfrage, quer über alle Nachrichten. */
    public List<KiTeil.Datei> dateien() {
        List<KiTeil.Datei> dateien = new ArrayList<>();
        for (KiNachricht nachricht : nachrichten) {
            for (KiTeil teil : nachricht.teile()) {
                if (teil instanceof KiTeil.Datei datei) {
                    dateien.add(datei);
                }
            }
        }
        return dateien;
    }

    /** Baut eine Anfrage Schritt für Schritt; nur Anweisung und eine Nachricht sind Pflicht. */
    public static final class Bauer {
        private final String systemAnweisung;
        private final List<KiNachricht> nachrichten = new ArrayList<>();
        private boolean jsonAntwort;
        private JsonNode schema;
        private Double temperatur;
        private Integer maxAusgabe;
        private Duration zeitlimit;

        private Bauer(String systemAnweisung) {
            this.systemAnweisung = systemAnweisung;
        }

        public Bauer nachricht(KiNachricht nachricht) {
            nachrichten.add(nachricht);
            return this;
        }

        public Bauer nutzer(KiTeil... teile) {
            return nachricht(KiNachricht.nutzer(List.of(teile)));
        }

        public Bauer json() {
            this.jsonAntwort = true;
            return this;
        }

        public Bauer json(JsonNode schema) {
            this.jsonAntwort = true;
            this.schema = schema;
            return this;
        }

        public Bauer temperatur(double temperatur) {
            this.temperatur = temperatur;
            return this;
        }

        public Bauer maxAusgabe(int maxAusgabe) {
            this.maxAusgabe = maxAusgabe;
            return this;
        }

        public Bauer zeitlimit(Duration zeitlimit) {
            this.zeitlimit = zeitlimit;
            return this;
        }

        public KiAnfrage bauen() {
            return new KiAnfrage(systemAnweisung, nachrichten, jsonAntwort, schema, temperatur, maxAusgabe,
                    zeitlimit);
        }
    }
}
