package org.example.kalkulationsprogramm.service.spam;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.AbzockeArt;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.util.HtmlKlartext;

/**
 * Erkennt bekannte Betrugsmaschen gegen Handwerksbetriebe. Läuft für jede Eingangsmail, auch für
 * zugeordnete und solche von bekannten Lieferanten (eine gefälschte „neue Bankverbindung“ kommt
 * gerade im Namen echter Lieferanten). Das Ergebnis ist nur ein Warnhinweis, verschoben wird nie.
 *
 * <p>Jede Masche braucht eine Kombination mehrerer Merkmale; ein einzelnes Wort reicht nie.
 * Die Suche arbeitet ohne Backtracking-anfällige Ausdrücke (siehe {@link Textsuche}), Groß- und
 * Kleinschreibung sowie Umlaute ({@code ä}/{@code ae}) sind egal. Reine Logik.
 */
public final class AbzockeErkennung {

    /** Längere Mails werden nur bis hierhin geprüft – Maschen stehen am Anfang. */
    private static final int MAX_TEXT_LAENGE = 50_000;

    // ─── Bankverbindung ─────────────────────────────────────────────

    private static final List<String> BANK_WOERTER = List.of(
            "bankverbindung", "kontoverbindung", "bankdaten", "bankkonto", "kontodaten",
            "zahlungsverbindung", "kontonummer", "bank details", "bank account");
    private static final List<String> AENDERUNGS_WOERTER = List.of(
            "neue ", "neuen ", "neuer ", "neues ", "geaendert", "aendert", "aenderung ", "aenderung:",
            "gewechselt", "umgestellt", "umstellung", "aktualisiert", "new ", "updated", "changed");
    private static final List<String> KONTOWECHSEL_WOERTER = List.of(
            "kontoaenderung", "kontowechsel", "bankwechsel", "bankverbindungsaenderung",
            "kontoverbindungsaenderung");
    private static final int ABSTAND_BANK = 25;
    private static final int ABSTAND_IBAN = 12;
    /** So nah müssen Verzeichnis- und Kosten-Wort beieinanderstehen (Zeichen). */
    private static final int ABSTAND_BRANCHE = 120;

    /** Kandidat für eine IBAN im normalisierten Text; geprüft wird danach mit Länge und Prüfziffer. */
    private static final Pattern IBAN_KANDIDAT =
            Pattern.compile("\\b[a-z]{2}\\d{2}(?> ?[0-9a-z]{4}){2,8}+(?> ?[0-9a-z]{1,4})?+");

    /** IBAN-Länge je Land (Europa). Unbekannte Länder zählen nicht als IBAN. */
    private static final Map<String, Integer> IBAN_LAENGEN = Map.ofEntries(
            Map.entry("DE", 22), Map.entry("AT", 20), Map.entry("CH", 21), Map.entry("LI", 21),
            Map.entry("NL", 18), Map.entry("BE", 16), Map.entry("LU", 20), Map.entry("FR", 27),
            Map.entry("IT", 27), Map.entry("ES", 24), Map.entry("PT", 25), Map.entry("PL", 28),
            Map.entry("CZ", 24), Map.entry("SK", 24), Map.entry("HU", 28), Map.entry("RO", 24),
            Map.entry("BG", 22), Map.entry("HR", 21), Map.entry("SI", 19), Map.entry("DK", 18),
            Map.entry("SE", 24), Map.entry("FI", 18), Map.entry("NO", 15), Map.entry("GB", 22),
            Map.entry("IE", 22), Map.entry("EE", 20), Map.entry("LV", 21), Map.entry("LT", 20),
            Map.entry("GR", 27), Map.entry("CY", 28), Map.entry("MT", 31));
    private static final BigInteger SIEBENUNDNEUNZIG = BigInteger.valueOf(97);

    // ─── Gericht / Behörde ──────────────────────────────────────────

    private static final List<String> BEHOERDEN_GANZE_WOERTER = List.of("gericht", "gerichts");
    private static final List<String> BEHOERDEN_WORTANFAENGE = List.of(
            "amtsgericht", "landgericht", "oberlandesgericht", "verwaltungsgericht", "finanzgericht",
            "sozialgericht", "arbeitsgericht", "bundesgerichtshof", "staatsanwalt", "gerichtsvollzieh",
            "finanzamt", "finanzbehoerde", "hauptzollamt", "zollamt", "zollbehoerde", "zollverwaltung",
            "generalzolldirektion", "bundeszentralamt", "bundesamt", "ordnungsamt", "polizei",
            "bundespolizei", "landeskriminalamt", "bundeskriminalamt", "behoerde", "bundesanstalt",
            "bundesnetzagentur", "europol", "interpol");
    private static final List<String> FORDERUNGS_WOERTER = List.of(
            "zahlung", "bezahl", "ueberweis", "begleich", "frist", "forderung", "pfaend", "kontopfaend",
            "vollstreck", "zwangsvollstreck", "haftbefehl", "bussgeld", "geldstrafe", "strafverfahren",
            "ermittlungsverfahren", "strafanzeige", "mahnbescheid", "saeumnis", "zollgebuehr");
    /** Harte Drohungen: im Betreff genügen sie, wenn der Text eine Behörde nennt. */
    private static final List<String> STARKE_DROHUNGEN = List.of(
            "pfaend", "kontopfaend", "vollstreck", "zwangsvollstreck", "haftbefehl", "bussgeld",
            "geldstrafe", "strafverfahren", "ermittlungsverfahren", "strafanzeige", "mahnbescheid");
    private static final List<String> AMTLICHE_DOMAINS = List.of(
            "bund.de", "zoll.de", "elster.de", "bzst.de", "arbeitsagentur.de", "deutsche-rentenversicherung.de",
            "polizei.de", "justiz.de", "justiz-bw.de", "europa.eu", "gv.at", "admin.ch",
            "bayern.de", "nrw.de", "niedersachsen.de", "sachsen.de", "hessen.de", "berlin.de", "hamburg.de",
            "bremen.de", "saarland.de", "thueringen.de", "brandenburg.de", "sachsen-anhalt.de",
            "schleswig-holstein.de", "rlp.de", "bwl.de", "baden-wuerttemberg.de", "service-bw.de",
            "mv-regierung.de", "landsh.de");

    // ─── Google-Unternehmensprofil ──────────────────────────────────

    private static final List<String> GOOGLE_PROFIL_WOERTER = List.of(
            "unternehmensprofil", "google business", "google my business", "business profile",
            "businessprofil", "firmenprofil", "google-profil", "google profil", "google-eintrag",
            "google eintrag", "google maps eintrag", "google-maps-eintrag");
    private static final List<String> GOOGLE_DRUCK_WOERTER = List.of(
            "verifizier", "bestaetig", "loesch", "geloescht", "gesperrt", "sperrung", "deaktivier",
            "gebuehr", "kosten", "frist", "aktualisier", "verify", "suspend", "removed", "deleted");
    private static final List<String> GOOGLE_DOMAINS = List.of("google.com", "google.de");

    // ─── Branchenbuch ───────────────────────────────────────────────

    private static final List<String> VERZEICHNIS_WOERTER = List.of(
            "branchenbuch", "branchenverzeichnis", "branchen-verzeichnis", "branchenregister", "branchenportal",
            "firmenverzeichnis", "firmenregister", "firmendatenbank", "gewerbeverzeichnis", "gewerberegister",
            "gewerbeauskunft", "gewerbe-auskunft", "unternehmensverzeichnis", "unternehmensregister",
            "handelsregister", "onlineverzeichnis", "online-verzeichnis", "online verzeichnis",
            "brancheneintrag", "firmeneintrag", "eintragungsofferte", "eintragungsantrag", "adressverzeichnis");
    private static final List<String> EINTRAG_WOERTER = List.of("eintrag", "eintrags", "eintragung", "eintragungen");
    private static final List<String> KOSTEN_WOERTER = List.of(
            "kostenpflichtig", "korrekturabzug", "korrektur-abzug", "korrekturfahne", "jahresgebuehr",
            "jahresbeitrag", "jahrespreis", "eintragungsgebuehr", "eintragsgebuehr", "eintragungskosten",
            "zahlungspflichtig", "pro jahr", "jaehrlich", "laufzeit", "mindestlaufzeit", "vertragsdauer",
            "offerte", "zuruecksenden", "zurueckfaxen", "per fax");
    /** Kosten-Wörter, die auch neben einem schlichten „Eintrag“ genügen. */
    private static final List<String> STARKE_KOSTEN_WOERTER = List.of(
            "kostenpflichtig", "korrekturabzug", "korrektur-abzug", "eintragungsgebuehr", "eintragsgebuehr",
            "jahresgebuehr");
    private static final List<String> ECHTE_VERZEICHNISSE = List.of(
            "gelbeseiten.de", "dasoertliche.de", "dastelefonbuch.de", "11880.com", "unternehmensregister.de",
            "handelsregister.de");

    private AbzockeErkennung() {
    }

    /**
     * Die erkannte Masche oder {@code null}. Bei mehreren gewinnt die gefährlichste
     * (Bankverbindung vor Behörde vor Google-Profil vor Branchenbuch).
     */
    public static AbzockeArt erkenne(Email email) {
        if (email == null) {
            return null;
        }
        String betreff = Textsuche.normalisiere(email.getSubject(), MAX_TEXT_LAENGE);
        String text = (betreff + " " + Textsuche.normalisiere(inhalt(email), MAX_TEXT_LAENGE)).trim();
        if (text.isEmpty()) {
            return null;
        }
        String domain = MailDomain.vonAdresse(email.getFromAddress());

        if (bankverbindungGeaendert(text)) {
            return AbzockeArt.BANKVERBINDUNG_GEAENDERT;
        }
        // Die eigene Steuerkanzlei schreibt zu Recht über Finanzamt, Fristen und Zahlungen.
        if (email.getSteuerberater() == null && angeblichVonBehoerde(betreff, email.getFromName(), text, domain)) {
            return AbzockeArt.BEHOERDE_GERICHT;
        }
        if (angeblichVonGoogle(text, domain)) {
            return AbzockeArt.GOOGLE_PROFIL;
        }
        if (branchenbuch(text, domain)) {
            return AbzockeArt.BRANCHENBUCH;
        }
        return null;
    }

    private static String inhalt(Email email) {
        if (email.getBody() != null && !email.getBody().isBlank()) {
            return email.getBody();
        }
        String html = email.getHtmlBody();
        if (html == null || html.isBlank()) {
            return "";
        }
        return HtmlKlartext.zuText(html.length() > MAX_TEXT_LAENGE * 4 ? html.substring(0, MAX_TEXT_LAENGE * 4) : html);
    }

    static boolean bankverbindungGeaendert(String text) {
        boolean aenderung = Textsuche.naheBeieinander(text, BANK_WOERTER, AENDERUNGS_WOERTER, ABSTAND_BANK)
                || Textsuche.naheBeieinander(text, List.of("iban"), AENDERUNGS_WOERTER, ABSTAND_IBAN)
                || Textsuche.enthaeltIrgendeinenWortanfang(text, KONTOWECHSEL_WOERTER);
        return aenderung && enthaeltIban(text);
    }

    /**
     * Sucht eine gültige IBAN (Länge des Landes und Prüfziffer nach ISO 13616).
     */
    static boolean enthaeltIban(String text) {
        Matcher m = IBAN_KANDIDAT.matcher(text);
        int geprueft = 0;
        while (m.find() && geprueft++ < 20) {
            String kompakt = m.group().replace(" ", "").toUpperCase(Locale.ROOT);
            Integer laenge = IBAN_LAENGEN.get(kompakt.substring(0, 2));
            if (laenge != null && kompakt.length() >= laenge && pruefzifferStimmt(kompakt.substring(0, laenge))) {
                return true;
            }
        }
        return false;
    }

    private static boolean pruefzifferStimmt(String iban) {
        String umgestellt = iban.substring(4) + iban.substring(0, 4);
        StringBuilder ziffern = new StringBuilder(umgestellt.length() * 2);
        for (int i = 0; i < umgestellt.length(); i++) {
            ziffern.append(Character.getNumericValue(umgestellt.charAt(i)));
        }
        return new BigInteger(ziffern.toString()).mod(SIEBENUNDNEUNZIG).intValue() == 1;
    }

    /**
     * Gericht/Behörde/Zoll und eine Forderung, von einer nicht-amtlichen Domain. Zusätzlich muss
     * sich der Absender als Behörde ausgeben: Behörde im Absendernamen, Behörde und Forderung im
     * Betreff, oder eine harte Drohung (Pfändung, Haftbefehl …) im Betreff. So warnt weder eine
     * Kundenmail „für das Finanzamt brauche ich die Rechnung, Zahlung per Überweisung“ noch die
     * Mahnung eines Lieferanten, die im Text einen Mahnbescheid beim Amtsgericht ankündigt.
     */
    static boolean angeblichVonBehoerde(String betreff, String anzeigename, String text, String domain) {
        if (istAmtlich(domain) || !Textsuche.enthaeltIrgendeinenWortanfang(text, FORDERUNGS_WOERTER)) {
            return false;
        }
        boolean nameIstBehoerde = nenntBehoerde(Textsuche.normalisiere(anzeigename, 1000));
        boolean betreffDroht = nenntBehoerde(betreff)
                && Textsuche.enthaeltIrgendeinenWortanfang(betreff, FORDERUNGS_WOERTER);
        boolean harteDrohung = Textsuche.enthaeltIrgendeinenWortanfang(betreff, STARKE_DROHUNGEN)
                && nenntBehoerde(text);
        return nameIstBehoerde || betreffDroht || harteDrohung;
    }

    private static boolean nenntBehoerde(String text) {
        return Textsuche.enthaeltIrgendeinWort(text, BEHOERDEN_GANZE_WOERTER)
                || Textsuche.enthaeltIrgendeinenWortanfang(text, BEHOERDEN_WORTANFAENGE);
    }

    static boolean istAmtlich(String domain) {
        return gehoertZuEinerDavon(domain, AMTLICHE_DOMAINS);
    }

    static boolean angeblichVonGoogle(String text, String domain) {
        return Textsuche.enthaeltWort(text, "google")
                && Textsuche.enthaeltIrgendeinenWortanfang(text, GOOGLE_PROFIL_WOERTER)
                && Textsuche.enthaeltIrgendeinenWortanfang(text, GOOGLE_DRUCK_WOERTER)
                && !gehoertZuEinerDavon(domain, GOOGLE_DOMAINS);
    }

    static boolean branchenbuch(String text, String domain) {
        if (gehoertZuEinerDavon(domain, ECHTE_VERZEICHNISSE) || istAmtlich(domain)) {
            return false;
        }
        // Nah beieinander statt irgendwo im Text: Firmen-Signaturen nennen das „Handelsregister“,
        // echte Aufträge bitten um „unterschrieben zurücksenden“ – zusammen ergab das Fehlalarme.
        boolean verzeichnis = Textsuche.naheBeieinander(text, VERZEICHNIS_WOERTER, KOSTEN_WOERTER, ABSTAND_BRANCHE);
        boolean eintrag = Textsuche.enthaeltIrgendeinWort(text, EINTRAG_WOERTER)
                && Textsuche.enthaeltIrgendeinenWortanfang(text, STARKE_KOSTEN_WOERTER);
        return verzeichnis || eintrag;
    }

    private static boolean gehoertZuEinerDavon(String domain, List<String> domains) {
        for (String d : domains) {
            if (MailDomain.gehoertZu(domain, d)) {
                return true;
            }
        }
        return false;
    }
}
