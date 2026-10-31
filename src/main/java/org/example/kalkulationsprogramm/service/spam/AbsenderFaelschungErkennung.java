package org.example.kalkulationsprogramm.service.spam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Email;

/**
 * Erkennt gefälschte Absender am Anzeigenamen: Der Name gibt sich als bekannte Marke aus
 * („PayPal“ vor {@code service@irgendwas.example}) oder nennt eine Adresse einer fremden Firma
 * („info@sparkasse.de“ als Name vor einer anderen Adresse).
 *
 * <p>Ausnahme Kontaktformular: Website-Formulare schicken von ihrer eigenen Adresse
 * (oft {@code noreply@…}) und setzen die Adresse des Kunden in Name und Reply-To. Steht die
 * Adresse aus dem Namen auch im Reply-To, ist das kein Signal.
 *
 * <p>Allein gibt dieses Signal keine Punkte (siehe {@link SpamStufenRegel#gruppenPunkte}).
 */
public final class AbsenderFaelschungErkennung {

    /** Längere Namen werden nur bis hierhin geprüft (in der Datenbank stehen höchstens 255 Zeichen). */
    private static final int MAX_NAME_LAENGE = 1000;

    /**
     * Marke im Anzeigenamen (ganzes Wort, kleingeschrieben) und die Domains, von denen sie
     * wirklich schreibt. {@code domainEnthaelt}: Haupt-Domains, die dieses Wort enthalten,
     * gelten ebenfalls als echt (jede Sparkasse hat ihre eigene Domain).
     */
    record Marke(String wort, List<String> domains, List<String> domainEnthaelt) {
        Marke(String wort, String... domains) {
            this(wort, List.of(domains), List.of());
        }
    }

    private static final List<String> GENOSSENSCHAFTSBANKEN = List.of("vr.de", "atruvia.de", "vr-networld.de");
    private static final List<String> GENOSSENSCHAFTSBANKEN_NAMEN =
            List.of("volksbank", "vrbank", "vr-bank", "raiffeisen", "voba");

    /**
     * Häufig gefälschte Marken. Bewusst ohne kurze, mehrdeutige Wörter wie „ING“ (Dipl.-Ing.)
     * oder „Hermes“ (auch ein Schleifmittel-Lieferant).
     */
    static final List<Marke> MARKEN = List.of(
            new Marke("paypal", "paypal.com", "paypal.de", "paypal.at", "paypal.ch", "paypal.co.uk",
                    "paypal.eu", "paypal-communication.com"),
            new Marke("amazon", "amazon.de", "amazon.com", "amazon.co.uk", "amazon.fr", "amazon.it",
                    "amazon.es", "amazon.nl", "amazon.pl", "amazon.se", "amazon.com.be", "amazonaws.com"),
            new Marke("dhl", "dhl.de", "dhl.com", "deutschepost.de", "deutsche-post.de", "dpdhl.com"),
            new Marke("deutsche post", "deutschepost.de", "deutsche-post.de", "dhl.de", "dpdhl.com"),
            new Marke("dpd", "dpd.de", "dpd.com"),
            new Marke("ups", "ups.com"),
            new Marke("sparkasse", List.of("sparkasse.de", "sparkasse.at"),
                    List.of("sparkasse", "spk", "ksk", "ssk")),
            new Marke("volksbank", GENOSSENSCHAFTSBANKEN, GENOSSENSCHAFTSBANKEN_NAMEN),
            new Marke("raiffeisenbank", GENOSSENSCHAFTSBANKEN, GENOSSENSCHAFTSBANKEN_NAMEN),
            new Marke("commerzbank", "commerzbank.de", "commerzbank.com", "comdirect.de"),
            new Marke("deutsche bank", "db.com", "deutsche-bank.de", "deutschebank.de", "deutsche-bank.com"),
            new Marke("postbank", "postbank.de", "db.com"),
            new Marke("n26", "n26.com"),
            new Marke("microsoft", "microsoft.com", "microsoftonline.com", "office.com", "office365.com",
                    "sharepointonline.com"),
            new Marke("apple", "apple.com", "itunes.com"),
            new Marke("google", "google.com", "google.de", "youtube.com"),
            new Marke("telekom", "telekom.de", "telekom.com", "t-online.de", "t-systems.com"),
            new Marke("vodafone", "vodafone.de", "vodafone.com"),
            new Marke("ionos", "ionos.de", "ionos.com", "ionos.at", "1und1.de", "1and1.com"),
            new Marke("strato", "strato.de", "strato.com"),
            new Marke("ebay", "ebay.de", "ebay.com", "ebay.at", "ebay.ch", "ebay.co.uk", "kleinanzeigen.de",
                    "ebay-kleinanzeigen.de"),
            new Marke("netflix", "netflix.com"),
            new Marke("docusign", "docusign.net", "docusign.com"),
            new Marke("wetransfer", "wetransfer.com"),
            new Marke("dropbox", "dropbox.com", "dropboxmail.com"),
            new Marke("adobe", "adobe.com", "adobesign.com", "echosign.com"),
            new Marke("klarna", "klarna.com", "klarna.de"),
            new Marke("elster", "elster.de"));

    private AbsenderFaelschungErkennung() {
    }

    public static boolean istGefaelscht(Email email) {
        if (email == null) {
            return false;
        }
        return istGefaelscht(email.getFromName(), email.getFromAddress(), email.getReplyToAddress());
    }

    /**
     * @param anzeigename   Anzeigename aus dem From-Header (darf {@code null} sein)
     * @param absender      Absender-Adresse
     * @param antwortAdresse Reply-To-Adresse (darf {@code null} sein)
     */
    public static boolean istGefaelscht(String anzeigename, String absender, String antwortAdresse) {
        String absenderDomain = MailDomain.vonAdresse(absender);
        if (anzeigename == null || anzeigename.isBlank() || absenderDomain == null) {
            return false;
        }
        String name = Textsuche.normalisiere(anzeigename, MAX_NAME_LAENGE);
        return nenntFremdeAdresse(name, absenderDomain, antwortAdresse) || nenntFremdeMarke(name, absenderDomain);
    }

    private static boolean nenntFremdeAdresse(String name, String absenderDomain, String antwortAdresse) {
        String antwort = antwortAdresse == null ? null : antwortAdresse.trim().toLowerCase(Locale.ROOT);
        for (String adresse : adressenImNamen(name)) {
            String domain = MailDomain.vonAdresse(adresse);
            if (MailDomain.gleicheOrganisation(domain, absenderDomain)) {
                continue;
            }
            // Kontaktformular: Die Kundenadresse steht in Name und Reply-To.
            if (adresse.equals(antwort)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean nenntFremdeMarke(String name, String absenderDomain) {
        for (Marke marke : MARKEN) {
            if (Textsuche.enthaeltWort(name, marke.wort()) && !schreibtVonEchterDomain(marke, absenderDomain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean schreibtVonEchterDomain(Marke marke, String absenderDomain) {
        for (String erlaubt : marke.domains()) {
            if (MailDomain.gehoertZu(absenderDomain, erlaubt)) {
                return true;
            }
        }
        String haupt = MailDomain.hauptDomain(absenderDomain);
        String ersterTeil = haupt.substring(0, haupt.indexOf('.') < 0 ? haupt.length() : haupt.indexOf('.'));
        for (String teil : marke.domainEnthaelt()) {
            if (ersterTeil.contains(teil)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Alle Adressen im (normalisierten) Anzeigenamen, kleingeschrieben. Zerlegt ohne regulären
     * Ausdruck, damit auch ein 10.000 Zeichen langer Name schnell geprüft ist.
     */
    static List<String> adressenImNamen(String name) {
        List<String> adressen = new ArrayList<>();
        if (name == null || name.indexOf('@') < 0) {
            return adressen;
        }
        StringBuilder wort = new StringBuilder();
        for (int i = 0; i <= name.length(); i++) {
            char c = i < name.length() ? name.charAt(i) : ' ';
            if (Character.isLetterOrDigit(c) || "._%+-@".indexOf(c) >= 0) {
                wort.append(c);
                continue;
            }
            pruefeAdresse(wort, adressen);
            wort.setLength(0);
        }
        return adressen;
    }

    private static void pruefeAdresse(StringBuilder wort, List<String> adressen) {
        String kandidat = wort.toString().toLowerCase(Locale.ROOT);
        while (kandidat.endsWith(".")) {
            kandidat = kandidat.substring(0, kandidat.length() - 1);
        }
        int at = kandidat.indexOf('@');
        if (at > 0 && at == kandidat.lastIndexOf('@') && kandidat.indexOf('.', at) > at + 1) {
            adressen.add(kandidat);
        }
    }
}
