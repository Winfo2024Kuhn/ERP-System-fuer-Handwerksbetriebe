package org.example.kalkulationsprogramm.service.spam;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.util.HtmlKlartext;

/**
 * Erkennt die {@link SpamSignal Spam-Signale} einer Mail. Reine Logik ohne Datenbank, damit
 * Regel-Score ({@code SpamFilterService}) und Lernfilter ({@code SpamBayesService}) dieselben
 * Signale sehen.
 *
 * <p>{@link SpamSignal#SPAM_WOERTER} erkennt nur der {@code SpamFilterService} (er kennt die
 * gewichtete Wörterliste). Ein Free-Mail-Absender (gmail.com, web.de …) ist nie ein Signal.
 */
public final class SpamSignale {

    /**
     * Anhänge, die Programme oder Makros ausführen können.
     */
    public static final Set<String> GEFAEHRLICHE_ENDUNGEN = Set.of(
            // Ausführbare Dateien
            "exe", "bat", "cmd", "com", "scr", "pif", "cpl",
            // Skripte
            "js", "vbs", "wsf", "wsh", "ps1", "sh",
            // Installer / Archive mit Code
            "jar", "msi", "msp", "mst",
            // Office-Makros
            "docm", "xlsm", "pptm", "dotm", "xltm",
            // Andere gefährliche Formate
            "hta", "inf", "reg", "rgs", "sct", "lnk", "iso", "img");

    /** Laut RFC 8601, z. B. {@code dmarc=fail}. SPF/DKIM allein brechen bei Weiterleitungen und zählen nicht. */
    private static final Pattern DMARC_FAIL = Pattern.compile("\\bdmarc=fail\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern IMG_TAG = Pattern.compile("<img\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern LINK_HREF = Pattern.compile("href\\s*+=\\s*+[\"']?+https?:", Pattern.CASE_INSENSITIVE);

    /** Darunter gilt eine Mail mit Bild und Link als „nur Bild“. */
    private static final int MIN_SICHTBARER_TEXT = 100;

    /** Anzeigenamen werden nur bis hierhin nach Adressen durchsucht. */
    private static final int MAX_NAME_LAENGE = 1000;

    private SpamSignale() {
    }

    /**
     * Alle Signale, die auf die Mail zutreffen. Nie {@code null}; veränderbar.
     */
    public static Set<SpamSignal> erkenne(Email email) {
        Set<SpamSignal> signale = EnumSet.noneOf(SpamSignal.class);
        if (email == null) {
            return signale;
        }
        if (AbsenderFaelschungErkennung.istGefaelscht(email)) {
            signale.add(SpamSignal.ABSENDER_GEFAELSCHT);
        }
        if (antwortUmgeleitet(email)) {
            signale.add(SpamSignal.ANTWORT_UMGELEITET);
        }
        if (hatGefaehrlichenAnhang(email)) {
            signale.add(SpamSignal.GEFAEHRLICHER_ANHANG);
        }
        if (email.getAuthenticationResults() != null
                && DMARC_FAIL.matcher(email.getAuthenticationResults()).find()) {
            signale.add(SpamSignal.ECHTHEIT_FEHLGESCHLAGEN);
        }
        if (Boolean.TRUE.equals(email.getAnbieterSpamFlag())) {
            signale.add(SpamSignal.ANBIETER_SPAM);
        }
        if (email.getSentAt() == null) {
            signale.add(SpamSignal.DATUM_FEHLT);
        }
        if (nurBildMitLink(email.getHtmlBody())) {
            signale.add(SpamSignal.NUR_BILD_MIT_LINK);
        }
        return signale;
    }

    /**
     * Antworten gehen an eine andere Haupt-Domain als der Absender. Subdomains derselben Firma
     * zählen nicht ({@code info@mail.firma.de} zu {@code noreply@firma.de}), ebenso wenig das
     * Kontaktformular, bei dem die Reply-To-Adresse auch im Anzeigenamen steht.
     */
    static boolean antwortUmgeleitet(Email email) {
        String antwort = email.getReplyToAddress();
        String antwortDomain = MailDomain.vonAdresse(antwort);
        String absenderDomain = MailDomain.vonAdresse(email.getFromAddress());
        if (antwortDomain == null || absenderDomain == null
                || MailDomain.gleicheOrganisation(antwortDomain, absenderDomain)) {
            return false;
        }
        String name = Textsuche.normalisiere(email.getFromName(), MAX_NAME_LAENGE);
        return !AbsenderFaelschungErkennung.adressenImNamen(name)
                .contains(antwort.trim().toLowerCase(Locale.ROOT));
    }

    static boolean hatGefaehrlichenAnhang(Email email) {
        if (email.getAttachments() == null) {
            return false;
        }
        for (EmailAttachment anhang : email.getAttachments()) {
            if (anhang == null) {
                continue;
            }
            String name = anhang.getOriginalFilename();
            int punkt = name == null ? -1 : name.lastIndexOf('.');
            if (punkt > 0 && GEFAEHRLICHE_ENDUNGEN.contains(name.substring(punkt + 1).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mindestens ein Bild, mindestens ein Link und kaum sichtbarer Text. Handwerker-Fotos ohne
     * Link lösen das nicht aus.
     */
    static boolean nurBildMitLink(String html) {
        if (html == null || html.isBlank()
                || !IMG_TAG.matcher(html).find()
                || !LINK_HREF.matcher(html).find()) {
            return false;
        }
        // Bis zum Fixpunkt: verschachtelte Tags wie "<<b>b>" dürfen nicht als Text zählen.
        String sichtbar = HtmlKlartext.entferneTags(html, " ").replaceAll("\\s++", " ").trim();
        return sichtbar.length() < MIN_SICHTBARER_TEXT;
    }
}
