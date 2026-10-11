package org.example.email;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wandelt im HTML eingebettete Bilder ({@code <img src="data:image/png;base64,…">}) vor dem
 * Versand in Inline-Anhänge mit {@code cid:}-Verweis um.
 *
 * <p>Hintergrund: Im Signatur-Studio hochgeladene Bilder stehen als Base64 direkt im HTML.
 * Gmail, Outlook und viele Firmen-Mailserver zeigen solche {@code data:}-Bilder nicht an.
 * Als Inline-Anhang ({@code multipart/related}) erscheinen sie überall.</p>
 */
public final class EingebetteteBilder {

    /**
     * {@code src="data:image/…;base64,…"} mit einfachen oder doppelten Anführungszeichen.
     * Possessive Quantifizierer – kein Backtracking bei großen Base64-Blöcken.
     */
    private static final Pattern DATEN_BILD = Pattern.compile(
            "(?i)(\\bsrc\\s*+=\\s*+)([\"'])data:(image/(?:png|jpe?g|gif|webp));base64,([A-Za-z0-9+/=\\s]++)\\2");

    /** Ein herausgelöstes Bild. */
    public record Bild(String cid, String contentType, byte[] daten) {
    }

    /** HTML mit {@code cid:}-Verweisen und die zugehörigen Bilder. */
    public record Ergebnis(String html, List<Bild> bilder) {
    }

    private EingebetteteBilder() {
    }

    /**
     * Löst alle eingebetteten Bilder heraus. Kaputtes Base64 bleibt unverändert im HTML stehen.
     */
    public static Ergebnis herausloesen(String html) {
        if (html == null || !html.toLowerCase(Locale.ROOT).contains("data:image/")) {
            return new Ergebnis(html, List.of());
        }
        List<Bild> bilder = new ArrayList<>();
        Matcher m = DATEN_BILD.matcher(html);
        StringBuilder sb = new StringBuilder(html.length());
        while (m.find()) {
            byte[] daten;
            try {
                daten = Base64.getMimeDecoder().decode(m.group(4));
            } catch (IllegalArgumentException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String typ = m.group(3).toLowerCase(Locale.ROOT).replace("image/jpg", "image/jpeg");
            String cid = "bild-" + UUID.randomUUID() + "@erp";
            bilder.add(new Bild(cid, typ, daten));
            String ersatz = m.group(1) + m.group(2) + "cid:" + cid + m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(ersatz));
        }
        m.appendTail(sb);
        return new Ergebnis(sb.toString(), List.copyOf(bilder));
    }

    /**
     * Hängt den HTML-Teil und die herausgelösten Bilder an einen {@code multipart/related}-Teil.
     */
    public static void fuegeHtmlHinzu(MimeMultipart related, String htmlBody) throws MessagingException {
        Ergebnis ergebnis = herausloesen(htmlBody != null ? htmlBody : "");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(ergebnis.html(), "text/html; charset=utf-8");
        related.addBodyPart(htmlPart);
        int nr = 1;
        for (Bild bild : ergebnis.bilder()) {
            MimeBodyPart teil = new MimeBodyPart();
            teil.setDataHandler(new DataHandler(new ByteArrayDataSource(bild.daten(), bild.contentType())));
            teil.setFileName("bild-" + nr++ + "." + endung(bild.contentType()));
            teil.setDisposition(MimeBodyPart.INLINE);
            teil.setHeader("Content-ID", "<" + bild.cid() + ">");
            related.addBodyPart(teil);
        }
    }

    private static String endung(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }
}
