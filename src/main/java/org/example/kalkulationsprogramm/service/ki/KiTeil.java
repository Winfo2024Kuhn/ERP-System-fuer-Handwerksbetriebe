package org.example.kalkulationsprogramm.service.ki;

import java.util.Locale;
import java.util.Objects;

/** Ein Baustein einer {@link KiNachricht}: Text oder eine Datei (Bild, PDF, Ton, XML). */
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
}
