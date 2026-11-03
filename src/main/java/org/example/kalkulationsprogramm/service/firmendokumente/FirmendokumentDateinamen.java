package org.example.kalkulationsprogramm.service.firmendokumente;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;

/**
 * Dateinamen der Firmendokumente: bereinigter Originalname zur Anzeige, Endung und Name für den Download.
 * Keiner dieser Namen landet im Dateisystem – dort liegt jede Datei unter einer UUID ({@link FirmendokumentDateiablage}).
 */
public final class FirmendokumentDateinamen {

    private static final String STANDARD_NAME = "dokument";
    private static final int MAX_ANZEIGE = 255;
    /**
     * Eine Endung besteht aus Buchstaben und Ziffern, auch in Teilen mit Bindestrich („appref-ms“), höchstens
     * 20 Zeichen („Bericht v2.0 final“ hat keine).
     */
    private static final Pattern ENDUNG = Pattern.compile("[a-z0-9]++(?:-[a-z0-9]++)*+");
    private static final int MAX_ENDUNG = 20;

    /**
     * Programmdateien und Dateien, die Windows/macOS beim Doppelklick ausführen, einbinden oder als Verknüpfung
     * öffnen. Office-Dateien mit Makros (docm, xlsm, pptm) sind bewusst NICHT gesperrt: Handwerksbetriebe arbeiten
     * mit Kalkulations-xlsm, und Office warnt beim Öffnen selbst vor Makros.
     */
    private static final Set<String> PROGRAMM_ENDUNGEN = Set.of("exe", "msi", "msc", "msp", "mst", "bat", "cmd",
            "com", "scr", "pif", "ps1", "ps1xml", "psm1", "psd1", "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta",
            "jar", "dll", "cpl", "lnk", "reg", "scf", "url", "website", "chm", "inf", "gadget", "application",
            "appref-ms", "settingcontent-ms", "library-ms", "xbap", "iso", "img", "vhd", "vhdx", "sh", "command",
            "app", "pkg", "dmg");
    /** Endung der alten Ablage: Bis zur Freigabe aller Formate wurden nur geprüfte PDFs als {@code <UUID>.pdf} abgelegt. */
    private static final String ALTE_PDF_ABLAGE = ".pdf";

    private FirmendokumentDateinamen() {
    }

    /** Originalname nur zur Anzeige: ohne Pfadanteile, ohne Steuer- und Sonderzeichen, höchstens 255 Zeichen. */
    public static String anzeigeName(String original) {
        if (original == null || original.isBlank()) {
            return STANDARD_NAME;
        }
        String ohnePfad = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        String sauber = ohnePfad.replaceAll("[:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (sauber.isEmpty()) {
            return STANDARD_NAME;
        }
        return sauber.length() > MAX_ANZEIGE ? sauber.substring(0, MAX_ANZEIGE) : sauber;
    }

    /**
     * Letzte Endung in Kleinbuchstaben ohne Punkt, sonst {@code ""}. Punkte und Leerzeichen am Ende zählen nicht
     * (Windows schneidet sie ab: „virus.exe.“ ist eine .exe).
     */
    public static String endung(String name) {
        if (name == null) {
            return "";
        }
        String ohneEnde = ohnePunkteAmEnde(name);
        int punkt = ohneEnde.lastIndexOf('.');
        if (punkt < 0) {
            return "";
        }
        String endung = ohneEnde.substring(punkt + 1).toLowerCase(Locale.ROOT);
        return endung.length() <= MAX_ENDUNG && ENDUNG.matcher(endung).matches() ? endung : "";
    }

    /** Ist das eine gesperrte Programmendung (klein, ohne Punkt, wie {@link #endung})? */
    public static boolean istProgrammEndung(String endung) {
        return endung != null && PROGRAMM_ENDUNGEN.contains(endung);
    }

    /** Name ohne Punkte und Leerzeichen am Ende (Windows schneidet sie ab). */
    private static String ohnePunkteAmEnde(String name) {
        int ende = name.length();
        while (ende > 0 && (name.charAt(ende - 1) == '.' || Character.isWhitespace(name.charAt(ende - 1)))) {
            ende--;
        }
        return name.substring(0, ende);
    }

    /**
     * Dateiname für den Browser: Dokumentname ohne Sonderzeichen plus „.endung“, wenn sie nicht schon dransteht.
     * Endet der Name selbst auf eine Programmendung (alte Daten), wird deren Punkt zu „_“ – der Browser soll nie
     * eine „.exe“ speichern.
     */
    public static String downloadName(String name, String endung) {
        String sauber = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (sauber.isEmpty()) {
            sauber = STANDARD_NAME;
        }
        if (istProgrammEndung(endung(sauber))) {
            String ohneEnde = ohnePunkteAmEnde(sauber);
            int punkt = ohneEnde.lastIndexOf('.');
            sauber = ohneEnde.substring(0, punkt) + "_" + ohneEnde.substring(punkt + 1);
        }
        if (endung == null || endung.isEmpty() || sauber.toLowerCase(Locale.ROOT).endsWith("." + endung)) {
            return sauber;
        }
        return sauber + "." + endung;
    }

    /** Endung eines abgelegten Dokuments: aus dem Originalnamen; Dateien der alten PDF-Ablage sind immer PDFs. */
    public static String endungDesDokuments(Firmendokument dokument) {
        String gespeichert = dokument.getGespeicherterDateiname();
        if (gespeichert != null && gespeichert.endsWith(ALTE_PDF_ABLAGE)) {
            return "pdf";
        }
        return endung(dokument.getOriginalDateiname());
    }
}
