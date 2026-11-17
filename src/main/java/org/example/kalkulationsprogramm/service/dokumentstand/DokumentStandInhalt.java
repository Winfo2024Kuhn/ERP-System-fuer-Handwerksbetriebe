package org.example.kalkulationsprogramm.service.dokumentstand;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Der Teil eines Ausgangsdokuments, der einen Stand ausmacht: Betreff, Netto, MwSt,
 * Zahlungsziel, abweichende Rechnungsadresse und die Positionen.
 *
 * <p>Bewusst ohne Datum (ändert sich beim Versand ohnehin) und ohne {@code htmlInhalt}
 * (wird aus den Positionen erzeugt). Zwei Inhalte mit gleichem {@link #fingerabdruck()} gelten
 * als derselbe Stand – so entsteht beim erneuten Export ohne Änderung kein neuer Stand.</p>
 */
public record DokumentStandInhalt(String betreff, BigDecimal betragNetto, BigDecimal mwstSatz,
        Integer zahlungszielTage, String rechnungsadresseOverride, String positionenJson) {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();
    private static final String TRENNER = "\u001f";

    public static DokumentStandInhalt von(AusgangsGeschaeftsDokument d) {
        return new DokumentStandInhalt(d.getBetreff(), d.getBetragNetto(), d.getMwstSatz(), d.getZahlungszielTage(),
                d.getRechnungsadresseOverride(), d.getPositionenJson());
    }

    public static DokumentStandInhalt von(AusgangsDokumentStand s) {
        return new DokumentStandInhalt(s.getBetreff(), s.getBetragNetto(), s.getMwstSatz(), s.getZahlungszielTage(),
                s.getRechnungsadresseOverride(), s.getPositionenJson());
    }

    /**
     * SHA-256 (hex, 64 Zeichen) über den normalisierten Inhalt: Beträge ohne nachlaufende Nullen,
     * JSON ohne Leerraum-Unterschiede. Leerer Text und {@code null} zählen gleich.
     */
    public String fingerabdruck() {
        String text = String.join(TRENNER,
                text(betreff), zahl(betragNetto), zahl(mwstSatz),
                zahlungszielTage == null ? "" : zahlungszielTage.toString(),
                text(rechnungsadresseOverride), json(positionenJson));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in dieser Java-Laufzeit", e);
        }
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }

    private static String zahl(BigDecimal b) {
        return b == null ? "" : b.stripTrailingZeros().toPlainString();
    }

    private static String json(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(MAPPER.readTree(s));
        } catch (JacksonException e) {
            return s;
        }
    }
}
