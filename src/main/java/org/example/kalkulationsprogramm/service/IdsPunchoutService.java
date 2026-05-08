package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.domain.Bestellposition;
import org.example.kalkulationsprogramm.domain.Bestellung;
import org.example.kalkulationsprogramm.domain.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.LieferantIdsKonfig;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BestellungRepository;
import org.example.kalkulationsprogramm.repository.LieferantIdsKonfigRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Punchout-Flow für IDS-Connect 2.5 (ZVSHK).
 *
 * <p>Baut die Form-Felder, mit denen der Browser auf die Punchout-URL
 * des Lieferanten zugeleitet wird (Auto-Submit-Form im Frontend) und
 * verarbeitet den zurueckkommenden Cart, indem er ihn als neue
 * {@link Bestellung} mit {@link Bestellposition}en ablegt.</p>
 *
 * <p>Das Klartext-Passwort wird hier kurzzeitig entschluesselt und
 * sofort in das Form-Feld geschrieben — es verlaesst das Backend
 * niemals als persistierter Wert. Die Backend-{@code PunchoutForm}-
 * Antwort enthaelt das Passwort by-design im Klartext, da der Browser
 * es als Hidden-Field an den Lieferanten-Shop POSTen muss. Konsequenz:
 * niemals {@code PunchoutForm} in Frontend-Logs ausgeben.</p>
 */
@Service
@RequiredArgsConstructor
public class IdsPunchoutService {

    /** Maximal-Laenge der externen Artikelnummer pro Bestellposition (DB-Spalte begrenzt). */
    private static final int MAX_ARTNR_LEN = 100;
    /** Maximal-Laenge der Freitext-Bezeichnung (DB-Spalte begrenzt). */
    private static final int MAX_PRODUKTNAME_LEN = 500;
    /** Vorgangs-Token: Gueltigkeit nach Punchout-Start, danach laeuft der Cart-Return ins Leere. */
    private static final Duration TOKEN_GUELTIGKEIT = Duration.ofMinutes(60);
    /** Maximale Anzahl Positionen pro Cart — defensiv gegen aufgeblaehte Form-Bodies. */
    private static final int MAX_CART_POSITIONS = 500;

    private final LieferantIdsKonfigRepository konfigRepository;
    private final LieferantenRepository lieferantenRepository;
    private final BestellungRepository bestellungRepository;
    private final IdsCryptoService cryptoService;

    /**
     * Master-Key fuer das HMAC des Punchout-Tokens. Wird aus
     * {@code ids.encryption.key} abgeleitet (gleiche Property wie
     * {@link IdsCryptoService}), damit nur ein Secret zu pflegen ist.
     */
    @Value("${ids.encryption.key:}")
    private String tokenSigningKeyBase64;

    private final SecureRandom random = new SecureRandom();

    /**
     * Ergebnis von {@link #buildPunchoutForm}: an welche URL und mit
     * welchen Feldern soll der Browser POSTen — und welcher
     * {@code enctype} ist dafür zu verwenden. Der enctype unterscheidet
     * sich pro Lieferanten-Profil: IDS-Connect 2.5 nutzt
     * {@code application/x-www-form-urlencoded}, Würth verlangt
     * {@code multipart/form-data}.
     */
    public record PunchoutForm(String action, String enctype, Map<String, String> fields) {
    }

    /**
     * Baut die Auto-Submit-Form-Felder für einen Login-Punchout (IDS-Connect 2.5,
     * FUNCTION=OrderRequest / ACTION=Login). Der Bauleiter klickt sich im Shop
     * den Warenkorb zusammen, der Shop posted ihn beim Klick auf "Übernehmen"
     * an die HOOK_URL zurueck — diese enthaelt einen kurzlebigen, signierten
     * Vorgangs-Token, der den Return an den startenden Bauleiter bindet
     * (Replay-Schutz).
     *
     * @param returnUrlBase HOOK-URL ohne Query-String — der Token wird hier angehaengt.
     * @param mitarbeiterId Optionale ID des startenden Bauleiters fuer Audit/Token-Binding.
     */
    @Transactional(readOnly = true)
    public PunchoutForm buildPunchoutForm(Long lieferantId, String returnUrlBase, Long mitarbeiterId) {
        LieferantIdsKonfig konfig = konfigRepository.findByLieferantId(lieferantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Keine IDS-Konfig für Lieferant " + lieferantId));
        if (!konfig.isAktiviert()) {
            throw new IllegalStateException("IDS-Schnittstelle ist für diesen Lieferanten nicht aktiviert.");
        }
        String shopUrl = konfig.getPunchoutUrl();
        if (shopUrl == null || shopUrl.isBlank()) {
            throw new IllegalStateException("Es ist keine Punchout-URL hinterlegt.");
        }
        validierePunchoutUrl(shopUrl);

        // Klartext-Passwort nur fuer diesen einen Punchout entschluesseln —
        // wird direkt in das Form-Feld geschrieben und sonst nirgendwo gehalten.
        String passwortKlartext = cryptoService.decrypt(konfig.getPasswortVerschluesselt());

        String token = generiereVorgangsToken(lieferantId, mitarbeiterId);
        String returnUrl = returnUrlBase + (returnUrlBase.contains("?") ? "&" : "?") + "token=" + token;

        IdsProtokoll protokoll = konfig.getProtokoll() == null
                ? IdsProtokoll.IDS_CONNECT_2_5
                : konfig.getProtokoll();

        Map<String, String> fields = new LinkedHashMap<>();
        String enctype;
        if (protokoll == IdsProtokoll.WUERTH_LEGACY) {
            // Würth ViewIDSCatalogService-IDSInBound (Intershop-Pipeline):
            // verlangt ausschließlich diese 4 lowercase-Feldnamen und
            // multipart/form-data als enctype. Die in der Würth-Anbindungs-
            // mail von Christian Heier explizit aufgeführten Parameter:
            //   kndnr / name_kunde / pw_kunde / hookurl
            // — alle anderen Felder (FUNCTION, ACTION, USERNAME, PASSWORD,
            // KUNDENNR, HOOK_URL, TARGET) werden vom Würth-Endpunkt
            // ignoriert oder mit "IDS-Parameter ... ist nicht gültig"
            // zurückgewiesen.
            fields.put("kndnr", emptyIfNull(konfig.getKundennummer()));
            fields.put("name_kunde", emptyIfNull(konfig.getLoginName()));
            fields.put("pw_kunde", emptyIfNull(passwortKlartext));
            fields.put("hookurl", returnUrl);
            enctype = "multipart/form-data";
        } else {
            // IDS-Connect 2.5 (ZVSHK-Standard): Großschreibung,
            // application/x-www-form-urlencoded.
            fields.put("FUNCTION", "OrderRequest");
            fields.put("ACTION", "Login");
            fields.put("USERNAME", emptyIfNull(konfig.getLoginName()));
            fields.put("PASSWORD", emptyIfNull(passwortKlartext));
            fields.put("KUNDENNR", emptyIfNull(konfig.getKundennummer()));
            fields.put("HOOK_URL", returnUrl);
            // TARGET ist im IDS-Connect 2.5 das Frame-Target fuer den Return — "_top"
            // weist den Shop an, den Cart in das oberste Browser-Fenster zu posten.
            fields.put("TARGET", "_top");
            enctype = "application/x-www-form-urlencoded";
        }

        return new PunchoutForm(shopUrl, enctype, fields);
    }

    /**
     * Verarbeitet den vom Lieferanten-Shop zurueckgesandten Cart und legt
     * eine neue {@link Bestellung} im Status {@link BestellStatus#ENTWURF}
     * an. Der Nutzer prueft sie anschliessend im normalen Bestellfluss
     * und versendet sie (oder verwirft sie).
     *
     * <p>IDS-Connect 2.5 Cart-Format: Form-encoded mit indexierten
     * Feldern {@code ARTNR_n}, {@code BESTNR_n}, {@code MENGE_n},
     * {@code BEZ1_n}, {@code PREIS_n}, {@code MEINH_n}.</p>
     */
    @Transactional
    public Bestellung verarbeiteCart(Long lieferantId, Map<String, String> cartFelder, Mitarbeiter erstellt) {
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId)
                .orElseThrow(() -> new IllegalArgumentException("Lieferant " + lieferantId + " nicht gefunden"));

        Bestellung bestellung = new Bestellung();
        bestellung.setLieferant(lieferant);
        bestellung.setBestellnummer(generiereBestellnummer());
        bestellung.setStatus(BestellStatus.ENTWURF);
        bestellung.setBestelltAm(LocalDate.now());
        bestellung.setErstelltAm(LocalDateTime.now());
        if (erstellt != null) {
            bestellung.setErstelltVon(erstellt);
            bestellung.setErstelltVonName(buildName(erstellt));
        }

        int positionsnummer = 1;
        // IDS-Connect indexiert ab 1 hoch (ARTNR_1, ARTNR_2, ...). Wir lesen
        // solange, bis der Index keine ARTNR mehr hat. Hard-Limit verhindert
        // beliebig grosse Cart-POSTs.
        for (int i = 1; i <= MAX_CART_POSITIONS; i++) {
            String artnr = cartFelder.get("ARTNR_" + i);
            String bestnr = cartFelder.get("BESTNR_" + i);
            String menge = cartFelder.get("MENGE_" + i);
            if (isBlank(artnr) && isBlank(bestnr) && isBlank(menge)) {
                break;
            }
            Bestellposition pos = new Bestellposition();
            pos.setPositionsnummer(positionsnummer++);
            pos.setExterneArtikelnummer(truncate(firstNonBlank(bestnr, artnr), MAX_ARTNR_LEN));
            pos.setFreitextProduktname(truncate(firstNonBlank(
                    cartFelder.get("BEZ1_" + i),
                    cartFelder.get("BEZ2_" + i),
                    "Position " + i), MAX_PRODUKTNAME_LEN));
            pos.setMenge(parseBigDecimal(menge));
            pos.setEinheit(truncate(emptyIfNull(cartFelder.get("MEINH_" + i)), 50));
            pos.setPreisProEinheit(parseBigDecimal(cartFelder.get("PREIS_" + i)));
            bestellung.addPosition(pos);
        }

        return bestellungRepository.save(bestellung);
    }

    /** Liste aller Lieferanten, deren IDS-Konfig aktiviert ist. */
    @Transactional(readOnly = true)
    public List<Lieferanten> findeAktivierteLieferanten() {
        return konfigRepository.findAll().stream()
                .filter(LieferantIdsKonfig::isAktiviert)
                .map(LieferantIdsKonfig::getLieferant)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(
                        l -> l.getLieferantenname() == null ? "" : l.getLieferantenname().toLowerCase()))
                .toList();
    }

    /**
     * Validiert einen vom Shop zurueckgesendeten Vorgangs-Token. Wirft
     * {@link IllegalStateException}, wenn der Token gefaelscht, abgelaufen
     * oder fuer einen anderen Lieferanten ausgestellt wurde.
     */
    public void validiereVorgangsToken(String token, Long lieferantId) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Punchout-Token fehlt im Cart-Return.");
        }
        String[] teile = token.split("\\.");
        if (teile.length != 2) {
            throw new IllegalStateException("Punchout-Token hat ungueltiges Format.");
        }
        byte[] payload;
        byte[] sig;
        try {
            payload = Base64.getUrlDecoder().decode(teile[0]);
            sig = Base64.getUrlDecoder().decode(teile[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Punchout-Token nicht decodierbar.");
        }
        byte[] expectedSig = hmac(payload);
        if (!constantTimeEquals(sig, expectedSig)) {
            throw new IllegalStateException("Punchout-Token-Signatur ungueltig.");
        }
        String[] payloadTeile = new String(payload, StandardCharsets.UTF_8).split("\\|");
        if (payloadTeile.length < 3) {
            throw new IllegalStateException("Punchout-Token-Payload unvollstaendig.");
        }
        long tokenLieferantId;
        long expiryEpochSeconds;
        try {
            tokenLieferantId = Long.parseLong(payloadTeile[0]);
            expiryEpochSeconds = Long.parseLong(payloadTeile[2]);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Punchout-Token-Payload ist nicht numerisch.", e);
        }
        if (tokenLieferantId != lieferantId) {
            throw new IllegalStateException("Punchout-Token gilt fuer einen anderen Lieferanten.");
        }
        if (Instant.now().getEpochSecond() > expiryEpochSeconds) {
            throw new IllegalStateException("Punchout-Token ist abgelaufen.");
        }
    }

    /** HMAC-signierter Vorgangs-Token: {@code base64(lieferantId|mitarbeiterId|expiry).base64(sig)}. */
    private String generiereVorgangsToken(Long lieferantId, Long mitarbeiterId) {
        long expiry = Instant.now().plus(TOKEN_GUELTIGKEIT).getEpochSecond();
        // Nonce verhindert, dass identische Eingaben den selben Token erzeugen
        byte[] nonce = new byte[8];
        random.nextBytes(nonce);
        String payload = lieferantId + "|"
                + (mitarbeiterId == null ? "" : mitarbeiterId) + "|"
                + expiry + "|"
                + HexFormat.of().formatHex(nonce);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] sig = hmac(payloadBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    private byte[] hmac(byte[] data) {
        if (tokenSigningKeyBase64 == null || tokenSigningKeyBase64.isBlank()) {
            throw new IllegalStateException(
                    "ids.encryption.key fehlt — Punchout-Token kann nicht signiert werden.");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(tokenSigningKeyBase64);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-Berechnung fehlgeschlagen.", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /** Erlaubt nur {@code https://}-URLs. {@code http://} wird nur fuer localhost-Tests durchgelassen. */
    private static void validierePunchoutUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Punchout-URL ist keine gueltige URL: " + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        boolean istLokal = uri.getHost() != null
                && (uri.getHost().equals("localhost") || uri.getHost().startsWith("127."));
        if ("https".equals(scheme)) return;
        if ("http".equals(scheme) && istLokal) return;
        throw new IllegalStateException(
                "Punchout-URL muss mit https:// beginnen (aktuell: " + scheme + ").");
    }

    private String generiereBestellnummer() {
        // 32 Zeichen UUID-Hex sind kollisionssicher — der eindeutige Bezeichner
        // wird ohnehin nur intern angezeigt, nicht an den Lieferanten gemeldet.
        return "B-IDS-" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private static String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) return "";
        for (String c : candidates) {
            if (!isBlank(c)) return c.trim();
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static BigDecimal parseBigDecimal(String raw) {
        if (isBlank(raw)) return null;
        try {
            // IDS-Lieferanten posten oft mit Komma (DE) oder Punkt (EN) — beides erlauben
            return new BigDecimal(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildName(Mitarbeiter m) {
        StringBuilder sb = new StringBuilder();
        if (m.getVorname() != null) sb.append(m.getVorname());
        if (m.getNachname() != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(m.getNachname());
        }
        return sb.toString();
    }
}
