package org.example.kalkulationsprogramm.service.ki;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.Locale;
import java.util.Objects;

/**
 * Ein Baustein einer {@link KiNachricht}: Text, eine Datei (Bild, PDF, Ton, XML),
 * ein Werkzeug-Aufruf der KI oder das Ergebnis eines solchen Aufrufs.
 */
public sealed interface KiTeil {

    static KiTeil text(String text) {
        return new Text(text);
    }

    static KiTeil datei(byte[] daten, String mimeType) {
        return new Datei(daten, mimeType);
    }

    record Text(String text) implements KiTeil {
        public Text {
            text = text == null ? "" : text;
        }

        /** Nur die Länge: Texte enthalten Fragen, Belege und E-Mails (DSGVO). */
        @Override
        public String toString() {
            return "Text[" + text.length() + " Zeichen]";
        }
    }

    /**
     * Eine Datei, die mitgeschickt wird. Die Bytes werden nicht kopiert:
     * Dokumente sind groß, und keiner der Dienste ändert sie nach dem Bauen der Anfrage.
     */
    @SuppressWarnings("java:S6218") // equals/hashCode über das Array braucht keiner
    record Datei(byte[] daten, String mimeType) implements KiTeil {
        public Datei {
            Objects.requireNonNull(daten, "daten");
            mimeType = vereinheitliche(mimeType);
        }

        /** Klein geschrieben und ohne Leerzeichen; das verbreitete {@code image/jpg} heißt richtig {@code image/jpeg}. */
        private static String vereinheitliche(String mimeType) {
            if (mimeType == null || mimeType.isBlank()) {
                return "application/octet-stream";
            }
            String klein = mimeType.trim().toLowerCase(Locale.ROOT);
            return "image/jpg".equals(klein) ? "image/jpeg" : klein;
        }

        public boolean istBild() {
            return mimeType.startsWith("image/");
        }

        public boolean istPdf() {
            return "application/pdf".equals(mimeType);
        }

        public boolean istAudio() {
            return mimeType.startsWith("audio/");
        }

        /** Klartext-Formate (XML-Rechnungen, Text), die jeder Anbieter als Text lesen kann. */
        public boolean istKlartext() {
            return mimeType.startsWith("text/") || mimeType.endsWith("/xml") || mimeType.endsWith("+xml")
                    || "application/json".equals(mimeType);
        }
    }

    /**
     * Die KI möchte ein Werkzeug ({@link KiWerkzeug}) aufrufen. Steht in einer
     * Nachricht der KI; das Ergebnis geht mit {@link #ergebnis(JsonNode)} zurück.
     *
     * @param id        Kennung des Aufrufs, damit das Ergebnis seinem Aufruf zugeordnet wird;
     *                  leer, wenn der Anbieter keine vergibt
     * @param name      Name des Werkzeugs
     * @param argumente Argumente als JSON-Objekt, nie {@code null}
     */
    record WerkzeugAufruf(String id, String name, JsonNode argumente) implements KiTeil {
        public WerkzeugAufruf {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            argumente = argumente == null || !argumente.isObject()
                    ? JsonNodeFactory.instance.objectNode()
                    : argumente;
        }

        /** Ohne Inhalt: Argumente und Ergebnisse enthalten Fragen und Geschäftsdaten (DSGVO). */
        @Override
        public String toString() {
            return "WerkzeugAufruf[id=" + id + ", name=" + name + "]";
        }

        /** Das Ergebnis dieses Aufrufs, mit derselben Kennung. */
        public WerkzeugErgebnis ergebnis(JsonNode ergebnis) {
            return new WerkzeugErgebnis(id, name, ergebnis);
        }
    }

    /**
     * Ergebnis eines Werkzeug-Aufrufs, steht in einer Nachricht mit der Rolle
     * {@link KiNachricht.Rolle#WERKZEUG}. Kann Geschäftsdaten enthalten: nie loggen.
     */
    record WerkzeugErgebnis(String id, String name, JsonNode ergebnis) implements KiTeil {
        public WerkzeugErgebnis {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            ergebnis = ergebnis == null ? JsonNodeFactory.instance.objectNode() : ergebnis;
        }

        /** Ohne Inhalt: Argumente und Ergebnisse enthalten Fragen und Geschäftsdaten (DSGVO). */
        @Override
        public String toString() {
            return "WerkzeugErgebnis[id=" + id + ", name=" + name + "]";
        }
    }
}
