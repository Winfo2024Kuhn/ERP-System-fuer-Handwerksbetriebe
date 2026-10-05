package org.example.kalkulationsprogramm.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;

/**
 * Prüft, ob zwei Mails mit gleichem Betreff wirklich zum selben Gespräch gehören.
 *
 * <p>Hintergrund: Ohne In-Reply-To-Kopfzeile verknüpft der Import über den Betreff.
 * Bei allgemeinen Betreffs ("AW: Bitte um Angebot", "AW: Anfrage") hängen sonst Antworten
 * verschiedener Firmen aneinander. Gemeinsam ist ihnen nur unsere eigene Adresse – die zählt
 * deshalb nicht als gemeinsamer Teilnehmer.
 */
public final class EmailThreadTeilnehmer {

    private static final Pattern ADRESSE = Pattern.compile("[^\\s<>,;\"'()@]++@[^\\s<>,;\"'()@]++");
    private static final Pattern WEITERLEITUNG = Pattern.compile("^\\s*+(?:fwd?|wg)\\s*+:", Pattern.CASE_INSENSITIVE);

    private EmailThreadTeilnehmer() {
    }

    /**
     * {@code true}, wenn {@code antwort} als Antwort auf {@code original} plausibel ist:
     * beide teilen mindestens einen externen Teilnehmer (Absender, An, CC). Hat eine Seite
     * gar keinen externen Teilnehmer (z. B. Versand an sich selbst mit BCC), entscheidet der
     * Betreff allein. Eigene Weiterleitungen bleiben im Verlauf, auch an neue Empfänger.
     */
    public static boolean gehoertZumGespraech(Email antwort, Email original, Set<String> eigeneAdressen) {
        if (antwort.getDirection() == EmailDirection.OUT && istWeiterleitung(antwort.getSubject())) {
            return true;
        }
        Set<String> extern = externeTeilnehmer(antwort, eigeneAdressen);
        Set<String> externOriginal = externeTeilnehmer(original, eigeneAdressen);
        if (extern.isEmpty() || externOriginal.isEmpty()) {
            return true;
        }
        return !Collections.disjoint(extern, externOriginal);
    }

    /**
     * {@code true}, wenn eine bestehende Verknüpfung nachträglich gelöst werden darf.
     *
     * <p>Bewusst eng: Verknüpfungen über In-Reply-To oder eine Antwort im ERP lassen sich in den
     * Daten nicht von reinen Betreff-Treffern unterscheiden. Gelöst wird deshalb nur das typische
     * Fehlbild – zwei <em>eingehende</em> Mails von verschiedenen Absender-Domains ohne
     * gemeinsamen Teilnehmer (z. B. Antworten zweier Lieferanten auf dieselbe Preisanfrage).
     * Eine Antwort an eine Reply-To-Adresse oder die Antwort eines Kollegen aus derselben Firma
     * bleiben dadurch immer verknüpft.
     */
    public static boolean darfGeloestWerden(Email antwort, Email original, Set<String> eigeneAdressen) {
        if (antwort.getDirection() != EmailDirection.IN || original.getDirection() != EmailDirection.IN) {
            return false;
        }
        String domain = domain(antwort.getFromAddress());
        if (domain.isEmpty() || domain.equals(domain(original.getFromAddress()))) {
            return false;
        }
        return !gehoertZumGespraech(antwort, original, eigeneAdressen);
    }

    private static String domain(String feld) {
        Set<String> adressen = adressen(feld);
        if (adressen.isEmpty()) return "";
        String adresse = adressen.iterator().next();
        return adresse.substring(adresse.lastIndexOf('@') + 1);
    }

    /** Alle Adressen aus Absender, Reply-To, An und CC ohne die eigenen (klein geschrieben). */
    static Set<String> externeTeilnehmer(Email email, Set<String> eigeneAdressen) {
        Set<String> adressen = adressen(email.getFromAddress(), email.getReplyToAddress(), email.getRecipient(), email.getCc());
        adressen.removeAll(eigeneAdressen);
        if (email.getDirection() == EmailDirection.OUT && email.getFromAddress() != null) {
            // Der Absender einer gesendeten Mail sind immer wir – auch unter einem Alias.
            adressen.removeAll(adressen(email.getFromAddress()));
        }
        return adressen;
    }

    /** Extrahiert E-Mail-Adressen aus Feldern wie {@code "Name" <a@b.de>, c@d.de}. */
    public static Set<String> adressen(String... felder) {
        Set<String> result = new LinkedHashSet<>();
        for (String feld : felder) {
            if (feld == null || feld.indexOf('@') < 0) continue;
            Matcher matcher = ADRESSE.matcher(feld);
            while (matcher.find()) {
                result.add(matcher.group().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    static boolean istWeiterleitung(String betreff) {
        return betreff != null && WEITERLEITUNG.matcher(betreff).find();
    }
}
