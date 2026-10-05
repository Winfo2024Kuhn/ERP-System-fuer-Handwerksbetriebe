package org.example.kalkulationsprogramm.util;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entfernt zitierten Verlauf aus dem Klartext einer E-Mail, damit die Vorschau in der
 * Mail-Liste nur den neuen Text zeigt.
 *
 * <p>Gegenstück zur Anzeige-Logik {@code threadQuotes.ts} im Frontend. Das Frontend arbeitet
 * auf dem HTML, das Backend hier auf dem Klartext, weil die Liste nur eine kurze Vorschau
 * bekommt – würde man erst kürzen und dann filtern, bliebe ein halber Zitatkopf stehen.
 *
 * <p>Erkannt werden:
 * <ul>
 *   <li>Kopfblöcke "Von/Gesendet/An/Betreff" (Outlook, Telekom, Samsung, web.de/GMX)</li>
 *   <li>Trennzeilen "-----Ursprüngliche Nachricht-----" und Outlooks Unterstrich-Linie</li>
 *   <li>mit "&gt;" markierte Zeilen samt Zuschreibung "Am … schrieb …:"</li>
 * </ul>
 * Weiterleitungen bleiben vollständig – der weitergeleitete Text ist dort der eigentliche Inhalt.
 */
public final class EmailVorschauText {

    private enum Kopf { VON, DATUM, AN, BETREFF }

    private static final Pattern WEITERLEITUNG = Pattern.compile(
            "^[-\\s]{0,20}(?:Weitergeleitete Nachricht|Forwarded message|Anfang der weitergeleiteten Nachricht"
                    + "|Begin forwarded message)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINAL_TRENNER = Pattern.compile(
            "^-{2,40}\\s?(?:Ursprüngliche Nachricht|Original-?Nachricht|Originalnachricht|Original Message)\\s?-{2,40}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNTERSTRICH_LINIE = Pattern.compile("^_{10,200}$");
    private static final Pattern KOPF_ZEILE = Pattern.compile(
            "^(Von|From|Gesendet|Sent|Datum|Date|An|To|Betreff|Subject|Cc|Kopie)\\s{0,3}:",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ZUSCHREIBUNG = Pattern.compile(
            "^(?:Am\\s.{1,300}\\sschrieb(?:\\s.{1,300})?|On\\s.{1,300}\\swrote):$",
            Pattern.CASE_INSENSITIVE);

    private EmailVorschauText() {
    }

    /**
     * Liefert den neuen Text ohne zitierten Verlauf, auf {@code maxLaenge} gekürzt.
     * Leerzeichen und Zeilenumbrüche werden zu einfachen Leerzeichen zusammengefasst.
     */
    public static String vorschau(String klartext, int maxLaenge) {
        String text = ohneZitiertenVerlauf(klartext).replaceAll("\\s++", " ").strip();
        // Nach Zeichen (Code Points) kürzen, damit kein Emoji halbiert wird.
        return text.codePointCount(0, text.length()) > maxLaenge
                ? text.substring(0, text.offsetByCodePoints(0, maxLaenge))
                : text;
    }

    /** Entfernt den zitierten Verlauf; Zeilenumbrüche bleiben erhalten. */
    public static String ohneZitiertenVerlauf(String klartext) {
        if (klartext == null || klartext.isBlank()) {
            return "";
        }
        String[] zeilen = klartext.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> ergebnis = new ArrayList<>();
        boolean neuerText = false;
        for (int i = 0; i < zeilen.length; i++) {
            String zeile = zeilen[i].strip();
            if (WEITERLEITUNG.matcher(zeile).find()) {
                return klartext;
            }
            if (neuerText && istVerlaufsBeginn(zeilen, i)) {
                break;
            }
            if (zeile.startsWith(">")) {
                continue;
            }
            if (ZUSCHREIBUNG.matcher(zeile).matches() && naechsteZeileIstZitat(zeilen, i)) {
                continue;
            }
            if (!zeile.isEmpty() && !UNTERSTRICH_LINIE.matcher(zeile).matches()) {
                neuerText = true;
            }
            ergebnis.add(zeilen[i]);
        }
        return String.join("\n", ergebnis).strip();
    }

    private static boolean istVerlaufsBeginn(String[] zeilen, int index) {
        String zeile = zeilen[index].strip();
        if (ORIGINAL_TRENNER.matcher(zeile).matches()) {
            return true;
        }
        boolean nachTrenner = UNTERSTRICH_LINIE.matcher(zeile).matches();
        int start = nachTrenner ? naechsteNichtLeere(zeilen, index + 1) : index;
        if (start < 0 || kopfArt(zeilen[start].strip()) == null) {
            return false;
        }
        Set<Kopf> arten = kopfArtenAb(zeilen, start);
        boolean vollstaendig = arten.size() == 4;
        boolean mitTrenner = nachTrenner && arten.size() == 3;
        return arten.contains(Kopf.VON) && arten.contains(Kopf.DATUM) && (vollstaendig || mitTrenner);
    }

    /** Sammelt die Kopf-Arten eines Blocks; umbrochene Empfängerlisten sind erlaubt. */
    private static Set<Kopf> kopfArtenAb(String[] zeilen, int start) {
        Set<Kopf> arten = EnumSet.noneOf(Kopf.class);
        int luecke = 0;
        for (int i = start; i < zeilen.length && i < start + 12; i++) {
            String zeile = zeilen[i].strip();
            if (zeile.isEmpty()) {
                if (!arten.isEmpty() && ++luecke > 1) break;
                continue;
            }
            if (KOPF_ZEILE.matcher(zeile).find()) {
                luecke = 0;
                Kopf art = kopfArt(zeile);
                if (art != null) arten.add(art);
            } else if (++luecke > 2) {
                break;
            }
        }
        return arten;
    }

    private static Kopf kopfArt(String zeile) {
        Matcher matcher = KOPF_ZEILE.matcher(zeile);
        if (!matcher.find()) {
            return null;
        }
        return switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "von", "from" -> Kopf.VON;
            case "gesendet", "sent", "datum", "date" -> Kopf.DATUM;
            case "an", "to" -> Kopf.AN;
            case "betreff", "subject" -> Kopf.BETREFF;
            default -> null;
        };
    }

    private static boolean naechsteZeileIstZitat(String[] zeilen, int index) {
        int naechste = naechsteNichtLeere(zeilen, index + 1);
        return naechste < 0 || zeilen[naechste].strip().startsWith(">");
    }

    private static int naechsteNichtLeere(String[] zeilen, int ab) {
        for (int i = ab; i < zeilen.length; i++) {
            if (!zeilen[i].isBlank()) return i;
        }
        return -1;
    }
}
