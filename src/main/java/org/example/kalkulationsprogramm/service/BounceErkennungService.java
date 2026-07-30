package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.ZustellStatus;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Erkennt eingehende Unzustellbarkeits-Meldungen ("Bounces") und markiert die
 * betroffene Ausgangsmail im ERP als unzustellbar.
 *
 * <p><strong>Warum das noetig ist:</strong> {@code Transport.send()} gilt als
 * erfolgreich, sobald der Relay des eigenen Providers die Mail annimmt. Lehnt
 * der Zielserver sie danach ab (Postfach existiert nicht), kommt der Fehler
 * asynchron als eigene E-Mail zurueck. Ohne diese Auswertung steht im ERP
 * weiterhin "versendet", obwohl beim Kunden nie etwas ankam — bei Angeboten
 * kostet das Auftraege, bei Rechnungen ist der Versandnachweis schlicht
 * falsch.</p>
 *
 * <p><strong>Zwei Formate:</strong> Moderne Provider schicken einen
 * strukturierten DSN nach RFC 3464 ({@code multipart/report} mit
 * {@code message/delivery-status}). T-Online (und andere Exim-Installationen)
 * schicken stattdessen eine reine Text-Mail, in der die fehlgeschlagene
 * Adresse und die Original-Header nur als Fliesstext stehen. Beide Formate
 * werden unterstuetzt, indem der komplette MIME-Baum zu einem Text
 * eingesammelt und darin gesucht wird.</p>
 *
 * <p><strong>Sicherung gegen Fehlalarme:</strong> Markiert wird ausschliesslich,
 * wenn die Mail als Fehlermeldung erkannt wurde <em>und</em> im Text eine
 * Message-ID steckt, die zu einer eigenen Ausgangsmail
 * ({@link EmailDirection#OUT}) in der Datenbank gehoert. Eine gewoehnliche
 * Kundenantwort, die unsere Mail zitiert, loest damit nichts aus.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BounceErkennungService
{
    /**
     * Obergrenze fuer den eingesammelten Text. Ein Bounce haengt die
     * Original-Mail an — bei einem Angebot mit PDF waeren das mehrere MB, die
     * wir fuer die Suche nach Message-ID und Fehlergrund nicht brauchen.
     */
    private static final int MAX_TEXT_LAENGE = 200_000;

    /** Schutz gegen verschachtelte oder zyklische MIME-Baeume. */
    private static final int MAX_TIEFE = 10;

    /** Message-ID-Zeile — in Original-Headern, die der Bounce zitiert. */
    private static final Pattern MESSAGE_ID =
            Pattern.compile("(?im)^\\s*message-id\\s*:\\s*(<[^>\\r\\n]{1,510}>)");

    /** Strukturierter DSN: maschinenlesbarer Fehlertext des Zielservers. */
    private static final Pattern DIAGNOSTIC_CODE =
            Pattern.compile("(?im)^\\s*diagnostic-code\\s*:\\s*(?:smtp\\s*;\\s*)?(.{3,300}?)\\s*$");

    /** Strukturierter DSN: permanenter Statuscode nach RFC 3463, z.B. "5.1.1". */
    private static final Pattern STATUS_CODE =
            Pattern.compile("(?im)^\\s*status\\s*:\\s*(5\\.\\d{1,3}\\.\\d{1,3})\\s*$");

    /** Strukturierter DSN: {@code Action: failed} — endgueltig gescheitert. */
    private static final Pattern ACTION_FAILED =
            Pattern.compile("(?im)^\\s*action\\s*:\\s*failed\\s*$");

    /** Permanenter SMTP-Antwortcode (5xx) im Diagnose-Feld. */
    private static final Pattern DIAGNOSE_5XX =
            Pattern.compile("(?im)^\\s*diagnostic-code\\s*:\\s*(?:smtp\\s*;\\s*)?5\\d{2}\\b");

    /**
     * Merkmale einer <strong>voruebergehenden</strong> Zustellverzoegerung.
     *
     * <p>Entscheidend fuer die Korrektheit: RFC 3464 nutzt fuer Verzoegerungen
     * dasselbe {@code multipart/report}-Format wie fuer endgueltige Fehler, und
     * auch T-Online/Exim verschickt Verzoegerungs-Warnungen vom
     * {@code mailer-daemon}. Wer nur auf Absender und Format schaut, markiert
     * eine Mail, die 20 Minuten spaeter erfolgreich ankommt, dauerhaft als
     * unzustellbar — mit Greylisting passiert das regelmaessig.</p>
     */
    private static final Pattern VERZOEGERUNG = Pattern.compile(
            "(?im)^\\s*action\\s*:\\s*delayed\\s*$"
            + "|^\\s*status\\s*:\\s*4\\.\\d{1,3}\\.\\d{1,3}\\s*$"
            + "|\\bwarning:\\s*message\\b"
            + "|\\bstill trying\\b"
            + "|\\bwill retry\\b"
            + "|\\bdelivery[^\\n]{0,30}delayed\\b"
            + "|\\bnachricht[^\\n]{0,30}verz(?:oe|ö)gert\\b");

    /**
     * Uebliche Markierungen, ab denen ein Bounce die Original-Nachricht
     * anhaengt. Ab hier stammt der Text von UNS und darf die Bewertung
     * "endgueltiger Fehler oder nur Verzoegerung" nicht mehr beeinflussen.
     */
    private static final Pattern ORIGINAL_BEGINNT = Pattern.compile(
            "(?i)message header follows"
            + "|this is a copy of the message"
            + "|below this line is a copy"
            + "|original message follows"
            + "|-{3,}\\s*original message"
            + "|content-type:\\s*message/rfc822");

    /** T-Online/Exim-Klartext: Block mit den fehlgeschlagenen Adressen. */
    private static final Pattern FAILED_ADDRESSES_BLOCK =
            Pattern.compile("(?is)failed addresses follow:?[^\\n]*\\n(.{0,400}?)(?:\\n\\s*\\|?-{5,}|\\z)");

    /** Absender-Adressen, unter denen Mailserver Fehlermeldungen verschicken. */
    private static final Set<String> DAEMON_LOCALPARTS = Set.of(
            "mailer-daemon", "mail-daemon", "maildaemon", "postmaster");

    /** Typische Betreffzeilen von Unzustellbarkeits-Meldungen. */
    private static final List<String> BETREFF_INDIKATOREN = List.of(
            "mail delivery failed", "delivery status notification (failure)",
            "undelivered mail returned to sender", "undeliverable",
            "returned mail", "delivery has failed", "delivery failure",
            "mail delivery system", "failure notice",
            "unzustellbar", "nicht zustellbar", "unzustellbare nachricht");

    /**
     * Formulierungen, die einen <strong>dauerhaften</strong> Ablehnungsgrund
     * belegen — das Postfach oder die Domain gibt es schlicht nicht.
     *
     * <p>Bewusst NICHT enthalten: "quota exceeded", "mailbox full", "over
     * quota". Ein volles Postfach ist ein voruebergehender Zustand (SMTP 4.2.2)
     * — der Kunde raeumt auf, und die naechste Mail kommt an. Als "unzustellbar"
     * markiert wuerde die Adresse dauerhaft falsch dastehen.</p>
     */
    private static final List<String> GRUND_INDIKATOREN = List.of(
            "unknown user", "user unknown", "no such user", "does not exist",
            "existiert nicht", "unbekannter empf", "mailbox unavailable",
            "mailbox not found", "recipient rejected", "address rejected",
            "unrouteable address", "host unknown", "domain not found");

    private final EmailRepository emailRepository;

    /**
     * Prueft eine eingehende Nachricht auf eine Unzustellbarkeits-Meldung und
     * markiert bei einem Treffer die zugehoerige Ausgangsmail.
     *
     * <p>Fehler werden geschluckt und geloggt: ein unlesbarer Bounce darf den
     * laufenden IMAP-Import nicht abbrechen.</p>
     *
     * @return {@code true}, wenn eine Ausgangsmail als unzustellbar markiert
     *         wurde.
     */
    /**
     * {@code REQUIRES_NEW}: der Aufruf kommt aus der Import-Transaktion einer
     * ganz anderen Mail. Ohne eigene Transaktion wuerde ein fehlgeschlagenes
     * {@code save()} hier die umgebende Transaktion auf rollback-only setzen —
     * der {@code catch} unten schluckt die Exception zwar, der Import dieser
     * Mail scheitert dann aber spaeter mit {@code UnexpectedRollbackException}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verarbeiteRuecklaeufer(Message msg)
    {
        if (msg == null) return false;
        try
        {
            if (!istUnzustellbarkeitsMeldung(msg)) return false;

            String text = sammleText(msg);
            if (text.isBlank()) return false;

            // Nur ENDGUELTIGE Fehler markieren. Eine Verzoegerungs-Meldung sieht
            // formal identisch aus, die Mail ist aber weiter unterwegs — sie als
            // unzustellbar zu markieren waere schlicht falsch und liesse sich im
            // ERP nicht wieder zuruecknehmen.
            if (!istDauerhafterFehler(text))
            {
                log.debug("[Bounce] Meldung ist kein endgueltiger Zustellfehler — ignoriert");
                return false;
            }

            return markiereBetroffeneAusgangsmail(text);
        }
        catch (Exception e)
        {
            log.warn("[Bounce] Rueckläufer konnte nicht ausgewertet werden: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sucht im Bounce-Text nach Message-IDs eigener Ausgangsmails und markiert
     * die erste gefundene. Mehrere Kandidaten sind normal — der Bounce zitiert
     * ausser der Original-Message-ID oft noch eigene IDs des Mailservers.
     */
    private boolean markiereBetroffeneAusgangsmail(String text)
    {
        String grund = extrahiereGrund(text);
        for (String messageId : extrahiereMessageIds(text))
        {
            Optional<Email> treffer = emailRepository.findByMessageId(messageId);
            if (treffer.isEmpty()) continue;

            Email original = treffer.get();
            if (original.getDirection() != EmailDirection.OUT) continue;
            if (original.getZustellStatus() == ZustellStatus.UNZUSTELLBAR)
            {
                // Schon markiert (z.B. Mail wurde erneut importiert) — kein
                // erneutes Schreiben, aber als "erkannt" melden.
                return true;
            }

            original.markiereUnzustellbar(grund);
            emailRepository.save(original);
            // Bewusst ohne Empfaenger-Adresse UND ohne Grundtext geloggt (DSGVO):
            // der Grund stammt aus dem Fremdserver-Text und enthaelt fast immer
            // die Adresse des Empfaengers ("<...> unknown user"). Er gehoert in
            // die DB-Spalte, die nur der angemeldete Nutzer sieht — nicht ins
            // Logfile. Die Message-ID reicht zum Wiederfinden im E-Mail-Center.
            log.warn("[Bounce] Ausgangsmail {} als unzustellbar markiert", messageId);
            return true;
        }
        return false;
    }

    // ======================= Erkennung =======================

    /**
     * Entscheidet, ob eine eingehende Nachricht ueberhaupt eine
     * Fehlermeldung ist. Bewusst grosszuegig: die eigentliche Absicherung
     * gegen Fehlalarme ist der Abgleich der Message-ID gegen die eigenen
     * Ausgangsmails.
     */
    boolean istUnzustellbarkeitsMeldung(Message msg) throws MessagingException
    {
        if (istDeliveryStatusReport(msg)) return true;
        if (stammtVonMailDaemon(msg)) return true;

        String betreff = msg.getSubject();
        if (betreff == null) return false;
        String klein = betreff.toLowerCase(Locale.ROOT);
        return BETREFF_INDIKATOREN.stream().anyMatch(klein::contains);
    }

    /**
     * {@code true}, wenn der Text einen <strong>endgueltigen</strong>
     * Zustellfehler belegt. Verzoegerungen werden vorrangig ausgeschlossen: ein
     * DSN kann beide Faelle enthalten (z.B. eine Sammelmeldung ueber mehrere
     * Empfaenger), und im Zweifel ist "noch unterwegs" die richtige Annahme —
     * eine faelschlich als unzustellbar markierte Mail laesst sich im ERP nicht
     * zurueckstellen.
     */
    static boolean istDauerhafterFehler(String text)
    {
        if (text == null || text.isBlank()) return false;

        // Nur den Melde-Teil auswerten, nicht die vom Bounce zitierte
        // Original-Mail: sonst entscheidet unser eigener Angebotstext ueber die
        // Bewertung — ein "existiert nicht" im Fliesstext wuerde einen
        // Permanent-Fehler vortaeuschen, ein "wir melden uns" mit dem Wort
        // "verzoegert" einen echten Bounce verschlucken.
        String bericht = berichtsTeil(text);

        if (VERZOEGERUNG.matcher(bericht).find()) return false;

        if (ACTION_FAILED.matcher(bericht).find()) return true;
        if (STATUS_CODE.matcher(bericht).find()) return true;
        if (DIAGNOSE_5XX.matcher(bericht).find()) return true;

        String klein = bericht.toLowerCase(Locale.ROOT);
        return GRUND_INDIKATOREN.stream().anyMatch(klein::contains);
    }

    /**
     * Schneidet den Text an der Stelle ab, an der der Mailserver die
     * Original-Nachricht anhaengt. Findet sich keine solche Markierung, bleibt
     * es beim vollstaendigen Text.
     *
     * <p>Wichtig: Die Message-ID-Suche laeuft weiterhin ueber den GESAMTEN Text
     * — sie steckt ja gerade in den zitierten Original-Headern. Nur die
     * Bewertung "endgueltig oder nicht" braucht den Melde-Teil.</p>
     */
    static String berichtsTeil(String text)
    {
        Matcher m = ORIGINAL_BEGINNT.matcher(text);
        return m.find() ? text.substring(0, m.start()) : text;
    }

    /** {@code multipart/report; report-type=delivery-status} nach RFC 3464. */
    private static boolean istDeliveryStatusReport(Message msg) throws MessagingException
    {
        String contentType = msg.getContentType();
        if (contentType == null) return false;
        String klein = contentType.toLowerCase(Locale.ROOT);
        return klein.contains("multipart/report") && klein.contains("delivery-status");
    }

    private static boolean stammtVonMailDaemon(Message msg) throws MessagingException
    {
        jakarta.mail.Address[] absender = msg.getFrom();
        if (absender == null) return false;
        for (jakarta.mail.Address adresse : absender)
        {
            String wert = adresse == null ? null : adresse.toString().toLowerCase(Locale.ROOT);
            if (wert == null) continue;
            int at = wert.indexOf('@');
            if (at <= 0) continue;
            // Local-Part isolieren: "Mail Delivery System <mailer-daemon@t-online.de>"
            String localPart = wert.substring(0, at);
            int trenner = Math.max(localPart.lastIndexOf('<'), localPart.lastIndexOf(' '));
            if (trenner >= 0) localPart = localPart.substring(trenner + 1);
            if (DAEMON_LOCALPARTS.contains(localPart.trim())) return true;
        }
        return false;
    }

    // ======================= Text-Extraktion =======================

    /**
     * Sammelt den gesamten Textinhalt einer Nachricht ein — inklusive der
     * Teile, die der regulaere Import ignoriert
     * ({@code message/delivery-status}, {@code message/rfc822},
     * {@code text/rfc822-headers}). Genau dort stehen bei einem strukturierten
     * DSN die Original-Message-ID und der Diagnose-Code.
     */
    static String sammleText(Part part) throws Exception
    {
        StringBuilder out = new StringBuilder();
        sammleText(part, 0, out);
        return out.toString();
    }

    private static void sammleText(Part part, int tiefe, StringBuilder out) throws Exception
    {
        if (part == null || tiefe > MAX_TIEFE || out.length() >= MAX_TEXT_LAENGE) return;

        // Header einer eingebetteten Original-Mail mitnehmen — die Message-ID
        // steckt im Header, nicht im Body.
        if (part instanceof Message eingebettet && tiefe > 0)
        {
            haengeHeaderAn(eingebettet, out);
        }


        Object inhalt;
        try
        {
            inhalt = part.getContent();
        }
        catch (Exception e)
        {
            // Unbekannter Content-Type ohne DataHandler (z.B. message/delivery-status
            // ohne dsn-Provider im Klassenpfad): Rohdaten lesen.
            haengeStreamAn(part, out);
            return;
        }

        if (inhalt instanceof String text)
        {
            haengeAn(out, text);
        }
        else if (inhalt instanceof Multipart multipart)
        {
            for (int i = 0; i < multipart.getCount() && out.length() < MAX_TEXT_LAENGE; i++)
            {
                sammleText(multipart.getBodyPart(i), tiefe + 1, out);
            }
        }
        else if (inhalt instanceof Part verschachtelt)
        {
            sammleText(verschachtelt, tiefe + 1, out);
        }
        else if (inhalt instanceof InputStream stream)
        {
            try (InputStream zuSchliessen = stream)
            {
                haengeAn(out, leseBegrenzt(zuSchliessen, ermittleCharset(part)));
            }
        }
    }

    /**
     * Haengt die Header einer eingebetteten Original-Mail als Text an.
     * Bewusst ueber {@code getAllHeaders()} statt {@code getAllHeaderLines()}:
     * letzteres gibt es nur auf {@code MimeMessage}, waehrend ein
     * {@code message/rfc822}-Teil je nach Provider auch als schlichte
     * {@link Message}-Implementierung ankommt.
     */
    private static void haengeHeaderAn(Message msg, StringBuilder out) throws MessagingException
    {
        Enumeration<Header> header = msg.getAllHeaders();
        while (header != null && header.hasMoreElements() && out.length() < MAX_TEXT_LAENGE)
        {
            Header h = header.nextElement();
            if (h != null) haengeAn(out, h.getName() + ": " + h.getValue());
        }
    }

    private static void haengeStreamAn(Part part, StringBuilder out)
    {
        try (InputStream stream = part.getInputStream())
        {
            haengeAn(out, leseBegrenzt(stream, ermittleCharset(part)));
        }
        catch (Exception e)
        {
            log.debug("[Bounce] MIME-Teil nicht lesbar: {}", e.getMessage());
        }
    }

    /**
     * Liest den im Teil deklarierten Zeichensatz aus. Wichtig, weil
     * Exim/T-Online-Bounces regelmaessig {@code ISO-8859-1} verwenden — mit
     * festem UTF-8 landet der Grund sonst mit kaputten Umlauten in der
     * Datenbank und damit auch in der Anzeige beim Nutzer.
     */
    private static Charset ermittleCharset(Part part)
    {
        try
        {
            String contentType = part.getContentType();
            if (contentType == null) return StandardCharsets.UTF_8;
            String name = new ContentType(contentType).getParameter("charset");
            if (name == null || name.isBlank()) return StandardCharsets.UTF_8;
            return Charset.forName(MimeUtility.javaCharset(name.trim()));
        }
        catch (Exception e)
        {
            // Unbekannter oder kaputter Charset-Name: UTF-8 ist der beste Rat.
            return StandardCharsets.UTF_8;
        }
    }

    private static String leseBegrenzt(InputStream stream, Charset charset) throws IOException
    {
        byte[] daten = stream.readNBytes(MAX_TEXT_LAENGE);
        return new String(daten, charset);
    }

    private static void haengeAn(StringBuilder out, String text)
    {
        if (text == null || text.isEmpty()) return;
        int platz = MAX_TEXT_LAENGE - out.length();
        if (platz <= 0) return;
        out.append(text.length() <= platz ? text : text.substring(0, platz)).append('\n');
    }

    // ======================= Parsing =======================

    /**
     * Liefert alle im Text vorkommenden Message-IDs in Reihenfolge des
     * Auftretens, ohne Duplikate. Es werden ausschliesslich
     * {@code Message-ID:}-Zeilen gelesen — {@code In-Reply-To} und
     * {@code References} bleiben bewusst aussen vor, damit eine zitierte
     * Kundenantwort nie als Bounce durchgeht.
     */
    static List<String> extrahiereMessageIds(String text)
    {
        Set<String> ids = new LinkedHashSet<>();
        if (text == null) return List.of();
        Matcher m = MESSAGE_ID.matcher(text);
        while (m.find())
        {
            ids.add(m.group(1).trim());
        }
        return new ArrayList<>(ids);
    }

    /**
     * Baut einen kurzen, fuer Handwerker verstaendlichen Fehlergrund. Reihenfolge:
     * strukturierter Diagnose-Code, dann der Klartext-Block von T-Online/Exim,
     * dann eine Zeile mit typischer Ablehnungs-Formulierung. Greift nichts,
     * bleibt es beim allgemeinen Hinweis.
     */
    static String extrahiereGrund(String text)
    {
        if (text == null || text.isBlank()) return "Zustellung fehlgeschlagen";

        Matcher diagnose = DIAGNOSTIC_CODE.matcher(text);
        if (diagnose.find())
        {
            return kuerze(diagnose.group(1));
        }

        Matcher block = FAILED_ADDRESSES_BLOCK.matcher(text);
        if (block.find())
        {
            String zusammengefasst = fasseZeilenZusammen(block.group(1));
            if (!zusammengefasst.isBlank()) return kuerze(zusammengefasst);
        }

        for (String zeile : text.split("\\R"))
        {
            String getrimmt = zeile.trim();
            if (getrimmt.isEmpty()) continue;
            String klein = getrimmt.toLowerCase(Locale.ROOT);
            if (GRUND_INDIKATOREN.stream().anyMatch(klein::contains))
            {
                return kuerze(getrimmt);
            }
        }

        Matcher status = STATUS_CODE.matcher(text);
        if (status.find())
        {
            return "Zustellung dauerhaft fehlgeschlagen (Code " + status.group(1) + ")";
        }
        return "Zustellung fehlgeschlagen";
    }

    /** Nicht-leere Zeilen eines Blocks zu einer Zeile verketten. */
    private static String fasseZeilenZusammen(String block)
    {
        List<String> teile = new ArrayList<>();
        for (String zeile : block.split("\\R"))
        {
            String getrimmt = zeile.trim();
            if (!getrimmt.isEmpty()) teile.add(getrimmt);
        }
        return String.join(" — ", teile);
    }

    private static String kuerze(String wert)
    {
        String bereinigt = wert.replaceAll("\\s+", " ").trim();
        return bereinigt.length() <= 300 ? bereinigt : bereinigt.substring(0, 300);
    }
}
