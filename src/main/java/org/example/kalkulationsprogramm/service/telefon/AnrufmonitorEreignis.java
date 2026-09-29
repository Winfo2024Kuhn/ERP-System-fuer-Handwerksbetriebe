package org.example.kalkulationsprogramm.service.telefon;

/**
 * Eine Zeile des FRITZ!Box-Anrufmonitors (TCP-Port 1012), z.B.
 * {@code 29.09.26 11:55:01;RING;0;09311234567;2323;SIP0;}.
 *
 * @param nummer       RING: Anrufer; CALL: angerufene Nummer; CONNECT: Gegenüber
 * @param eigeneNummer RING: angerufene eigene Nummer; CALL: genutzte eigene Nummer
 * @param nebenstelle  CALL/CONNECT: Nebenstelle (40–49 = Anrufbeantworter)
 */
public record AnrufmonitorEreignis(Typ typ, String verbindungsId, String nummer, String eigeneNummer,
                                   Integer nebenstelle, Integer dauerSekunden) {

    public enum Typ { RING, CALL, CONNECT, DISCONNECT }

    private static final int MAX_FELD = 40;

    /** Zerlegt eine Zeile; null bei unbekanntem oder kaputtem Format. */
    public static AnrufmonitorEreignis parse(String zeile) {
        if (zeile == null || zeile.length() > 500) {
            return null;
        }
        String[] f = zeile.trim().split(";", -1);
        if (f.length < 4) {
            return null;
        }
        Typ typ;
        try {
            typ = Typ.valueOf(f[1].trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String id = feld(f, 2);
        if (id.isEmpty()) {
            return null;
        }
        return switch (typ) {
            case RING -> new AnrufmonitorEreignis(typ, id, feld(f, 3), feld(f, 4), null, null);
            case CALL -> new AnrufmonitorEreignis(typ, id, feld(f, 5), feld(f, 4), zahl(feld(f, 3)), null);
            case CONNECT -> new AnrufmonitorEreignis(typ, id, feld(f, 4), "", zahl(feld(f, 3)), null);
            case DISCONNECT -> new AnrufmonitorEreignis(typ, id, "", "", null, zahl(feld(f, 3)));
        };
    }

    public boolean istAnrufbeantworter() {
        return nebenstelle != null && nebenstelle >= 40 && nebenstelle <= 49;
    }

    private static String feld(String[] f, int i) {
        if (i >= f.length) {
            return "";
        }
        String s = f[i].trim();
        return s.length() > MAX_FELD ? s.substring(0, MAX_FELD) : s;
    }

    private static Integer zahl(String s) {
        if (s.isEmpty() || s.length() > 9) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return null;
            }
        }
        return Integer.parseInt(s);
    }
}
