package org.example.kalkulationsprogramm.service.telefon;

/**
 * Bringt Rufnummern in jeder Schreibweise in ein einheitliches E.164-Format
 * ({@code +49931123456}), damit "0931 / 123 45-6", "+49 (0) 931 123456" und
 * "123456" (bei Ortsvorwahl 0931) als dieselbe Nummer erkannt werden.
 * <p>
 * Bewusst ohne Regex (ReDoS-sicher) – eine einfache Zeichenschleife.
 */
public final class RufnummerNormalisierer {

    private static final int MIN_ZIFFERN = 3;
    private static final int MAX_LAENGE = 40;
    /** Längste Durchwahl, die hinter einer Stammnummer erkannt wird. */
    public static final int MAX_DURCHWAHL = 5;
    /**
     * Mindestlänge einer Stammnummer (Ziffern inkl. Ländervorwahl). Schützt davor,
     * dass ein Bindestrich nur die Vorwahl abtrennt ("09721-5555") und dann jeder
     * Anrufer aus diesem Ortsnetz als die Firma erkannt würde.
     */
    static final int MIN_STAMM_ZIFFERN = 9;

    private RufnummerNormalisierer() {
    }

    /**
     * @param roh           Nummer wie eingegeben oder von der Telefonanlage geliefert
     * @param landesvorwahl z.B. "49", "+49" oder "0049"; leer → "49"
     * @param ortsvorwahl   z.B. "0931" oder "931"; leer → Ortsnetznummern werden nicht erkannt
     * @return E.164-Nummer oder {@code null}, wenn keine sinnvolle Nummer erkennbar ist
     */
    public static String normalisiere(String roh, String landesvorwahl, String ortsvorwahl) {
        if (roh == null) {
            return null;
        }
        String ziffern = nurZiffernMitPlus(entferneNullInKlammern(roh.trim()));
        int eigeneZiffern = ziffern.startsWith("+") ? ziffern.length() - 1 : ziffern.length();
        if (eigeneZiffern < MIN_ZIFFERN) {
            return null;
        }
        String land = ohneFuehrendeNullen(nurZiffern(landesvorwahl));
        if (land.isEmpty()) {
            land = "49";
        }
        String ort = ohneFuehrendeNullen(nurZiffern(ortsvorwahl));

        String ergebnis;
        if (ziffern.startsWith("+")) {
            ergebnis = ziffern;
        } else if (ziffern.startsWith("00")) {
            ergebnis = "+" + ziffern.substring(2);
        } else if (ziffern.startsWith("0")) {
            ergebnis = "+" + land + ziffern.substring(1);
        } else if (!ort.isEmpty()) {
            ergebnis = "+" + land + ort + ziffern;
        } else {
            return null;
        }
        int anzahlZiffern = ergebnis.length() - 1;
        if (anzahlZiffern < MIN_ZIFFERN || ergebnis.length() > MAX_LAENGE) {
            return null;
        }
        return ergebnis;
    }

    /**
     * Stammnummer einer Firmennummer in Durchwahl-Schreibweise (DIN 5008), z.B.
     * "09721 5555-0" (Zentrale) oder "0931 4444-12" → "+4997215555" bzw.
     * "+499314444". Anrufe von jeder Durchwahl dieser Stammnummer gehören zur Firma.
     * <p>
     * Deutsche Rufnummern sind innerhalb einer Vorwahl präfixfrei: Eine
     * vollständige Stammnummer kann nicht der Anfang einer fremden Nummer sein.
     * Das gilt aber nur, wenn vor dem Strich wirklich die Stammnummer steht.
     * Deshalb keine Stammnummer bei weiteren Bindestrichen davor
     * ("09721 12-34-56" ist nur gegliedert) und keine bei Mobilfunk
     * (015x/016x/017x) – Handys haben keine Durchwahlen.
     *
     * @return Stammnummer in E.164 oder {@code null}, wenn keine Durchwahl-Schreibweise
     *         vorliegt oder die Stammnummer zu kurz ist
     */
    public static String stammnummer(String roh, String landesvorwahl, String ortsvorwahl) {
        if (roh == null) {
            return null;
        }
        String s = roh.trim();
        int strich = s.lastIndexOf('-');
        if (strich <= 0) {
            return null;
        }
        String durchwahl = s.substring(strich + 1).trim();
        if (durchwahl.isEmpty() || durchwahl.length() > MAX_DURCHWAHL || !nurZiffern(durchwahl).equals(durchwahl)) {
            return null;
        }
        String vorDemStrich = s.substring(0, strich);
        if (vorDemStrich.indexOf('-') >= 0) {
            return null;
        }
        String stamm = normalisiere(vorDemStrich, landesvorwahl, ortsvorwahl);
        if (stamm == null || stamm.length() - 1 < MIN_STAMM_ZIFFERN || istDeutscherMobilfunk(stamm)) {
            return null;
        }
        return stamm;
    }

    private static boolean istDeutscherMobilfunk(String e164) {
        return e164.startsWith("+4915") || e164.startsWith("+4916") || e164.startsWith("+4917");
    }

    /** "+49 (0) 931" → "+49  931": die deutsche Konvention "(0)" wird verworfen. */
    private static String entferneNullInKlammern(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '(') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) == ' ') {
                    j++;
                }
                if (j < s.length() && s.charAt(j) == '0') {
                    int k = j + 1;
                    while (k < s.length() && s.charAt(k) == ' ') {
                        k++;
                    }
                    if (k < s.length() && s.charAt(k) == ')') {
                        i = k + 1;
                        continue;
                    }
                }
            }
            sb.append(s.charAt(i));
            i++;
        }
        return sb.toString();
    }

    /** Behält Ziffern und ein führendes "+", alles andere fällt weg. */
    private static String nurZiffernMitPlus(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else if (c == '+' && sb.isEmpty()) {
                sb.append(c);
            }
        }
        return "+".contentEquals(sb) ? "" : sb.toString();
    }

    private static String nurZiffern(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String ohneFuehrendeNullen(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '0') {
            i++;
        }
        return s.substring(i);
    }
}
