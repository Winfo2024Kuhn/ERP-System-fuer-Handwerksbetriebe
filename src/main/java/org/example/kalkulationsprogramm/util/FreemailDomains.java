package org.example.kalkulationsprogramm.util;

import java.util.Locale;
import java.util.Set;

/**
 * Freemail-Anbieter (gmail.com, gmx.de, t-online.de, …) für die Zuordnung von
 * E-Mails zu Lieferanten.
 *
 * <p>Bei einer eigenen Firmen-Domain gehört jede Adresse dahinter zum selben Partner –
 * bei gmx.de oder t-online.de sagt die Domain gar nichts aus. Hat ein Lieferant so eine
 * Adresse, würde sonst jede fremde Mail derselben Domain auf seiner Karte landen. Bei
 * diesen Domains zählt deshalb nur die exakt hinterlegte Adresse, nie die Domain.</p>
 *
 * <p>Einzige Quelle im Backend (genutzt von {@code LieferantEmailResolver} und dem
 * Rückwirkend-Zuordnen in {@code EmailBackfillEventListener}). Das PC-Frontend führt
 * dieselbe Liste in {@code react-pc-frontend/src/lib/freemailDomains.ts};
 * {@code FreemailDomainsTest} prüft, dass beide gleich bleiben.</p>
 */
public final class FreemailDomains {

    /** Alle bekannten Freemail-Domains, kleingeschrieben. */
    public static final Set<String> DOMAINS = Set.of(
            "gmail.com", "googlemail.com",
            "gmx.de", "gmx.net", "gmx.at", "gmx.ch",
            "web.de", "t-online.de", "freenet.de", "arcor.de", "1und1.de",
            "outlook.com", "outlook.de", "hotmail.com", "hotmail.de",
            "live.com", "live.de", "msn.com",
            "yahoo.com", "yahoo.de", "yahoo.co.uk", "ymail.com", "aol.com", "aol.de",
            "icloud.com", "me.com", "mac.com",
            "mail.de", "posteo.de", "mailbox.org", "protonmail.com", "proton.me",
            "gmx.com", "gmx.li", "googlemail.de", "hotmail.fr",
            "email.de", "online.de", "kabelmail.de", "vodafone.de",
            "netcologne.de", "ewetel.net", "unitybox.de", "unitymedia.de", "firemail.de",
            "t-online.com", "telekom.de", "arcor.net");

    private FreemailDomains() {
    }

    /** Prüft, ob eine Domain zu einem Freemail-Anbieter gehört (Groß-/Kleinschreibung egal). */
    public static boolean istFreemailDomain(String domain) {
        return domain != null && DOMAINS.contains(domain.trim().toLowerCase(Locale.ROOT));
    }

    /** Prüft, ob die Domain einer Adresse ({@code name@domain}) ein Freemail-Anbieter ist. */
    public static boolean istFreemailAdresse(String adresse) {
        if (adresse == null) {
            return false;
        }
        int at = adresse.lastIndexOf('@');
        return at >= 0 && istFreemailDomain(adresse.substring(at + 1));
    }
}
