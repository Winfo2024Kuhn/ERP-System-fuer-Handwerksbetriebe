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
 * @param werkzeuge       Werkzeuge, die die KI aufrufen darf (braucht {@link KiFaehigkeit#WERKZEUGE})
 * @param werkzeugModus   ob die KI in dieser Runde Werkzeuge aufrufen darf
 * @param websuche        Wunsch: die KI darf im Internet suchen. Wer das nicht kann
 *                        ({@link KiFaehigkeit#WEBSUCHE}), antwortet ohne – die Einstellungsseite sagt es dazu.
 */
public record KiAnfrage(
        String systemAnweisung,
        List<KiNachricht> nachrichten,
        boolean jsonAntwort,
        JsonNode schema,
        Double temperatur,
        Integer maxAusgabe,
        Duration zeitlimit,
        List<KiWerkzeug> werkzeuge,
        WerkzeugModus werkzeugModus,
        boolean websuche) {

    static final Duration STANDARD_ZEITLIMIT = Duration.ofSeconds(90);

    /** Darf die KI in dieser Runde Werkzeuge aufrufen? */
    public enum WerkzeugModus {
        /** Die KI entscheidet selbst: antworten oder nachschlagen. */
        AUTO,
        /** Die Werkzeuge bleiben bekannt, aber die KI muss jetzt antworten (letzte Runde). */
        KEINE
    }

    public KiAnfrage {
        systemAnweisung = systemAnweisung == null ? "" : systemAnweisung;
        nachrichten = nachrichten == null ? List.of() : List.copyOf(nachrichten);
        zeitlimit = zeitlimit == null ? STANDARD_ZEITLIMIT : zeitlimit;
        werkzeuge = werkzeuge == null ? List.of() : List.copyOf(werkzeuge);
        werkzeugModus = werkzeugModus == null ? WerkzeugModus.AUTO : werkzeugModus;
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

    /** Hat die Anfrage Werkzeuge oder den Wunsch nach Websuche? */
    public boolean mitWerkzeugen() {
        return !werkzeuge.isEmpty() || websuche;
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
        private List<KiWerkzeug> werkzeuge = List.of();
        private WerkzeugModus werkzeugModus = WerkzeugModus.AUTO;
        private boolean websuche;

        private Bauer(String systemAnweisung) {
            this.systemAnweisung = systemAnweisung;
        }

        public Bauer nachricht(KiNachricht nachricht) {
            nachrichten.add(nachricht);
            return this;
        }

        public Bauer nachrichten(List<KiNachricht> verlauf) {
            nachrichten.addAll(verlauf);
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

        public Bauer werkzeuge(List<KiWerkzeug> werkzeuge, WerkzeugModus modus) {
            this.werkzeuge = werkzeuge == null ? List.of() : List.copyOf(werkzeuge);
            this.werkzeugModus = modus;
            return this;
        }

        public Bauer websuche(boolean websuche) {
            this.websuche = websuche;
            return this;
        }

        public KiAnfrage bauen() {
            return new KiAnfrage(systemAnweisung, nachrichten, jsonAntwort, schema, temperatur, maxAusgabe,
                    zeitlimit, werkzeuge, werkzeugModus, websuche);
        }
    }
}
