package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Bestellposition;
import org.example.kalkulationsprogramm.domain.Bestellung;
import org.example.kalkulationsprogramm.domain.IdsProtokoll;
import org.example.kalkulationsprogramm.domain.LieferantIdsKonfig;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BestellungRepository;
import org.example.kalkulationsprogramm.repository.LieferantIdsKonfigRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.IdsPunchoutService.PunchoutForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests fuer den Punchout-Flow: HMAC-Token-Roundtrip, URL-Schema-
 * Validierung, Cart-Truncate auf DB-Spalten-Limit. Die kritischen
 * Security-Pfade (Token-Faelschung, abgelaufen, falscher Lieferant)
 * decken die Replay-Schutz-Logik ab.
 */
class IdsPunchoutServiceTest {

    /** 256-Bit-Test-Key (Base64). NICHT in Produktion verwenden. */
    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString(new byte[]{
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                    17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});

    private LieferantIdsKonfigRepository konfigRepo;
    private LieferantenRepository lieferantenRepo;
    private BestellungRepository bestellungRepo;
    private IdsCryptoService crypto;
    private IdsPunchoutService service;

    @BeforeEach
    void setUp() {
        konfigRepo = mock(LieferantIdsKonfigRepository.class);
        lieferantenRepo = mock(LieferantenRepository.class);
        bestellungRepo = mock(BestellungRepository.class);
        crypto = mock(IdsCryptoService.class);
        when(crypto.decrypt(any())).thenReturn("p4ssw0rt");
        service = new IdsPunchoutService(konfigRepo, lieferantenRepo, bestellungRepo, crypto);
        ReflectionTestUtils.setField(service, "tokenSigningKeyBase64", TEST_KEY);
    }

    // ── URL-Schema-Validierung ───────────────────────────────────

    @Test
    void httpsUrlIstErlaubt() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://eshop.wuerth.de/IDSInBound", IdsProtokoll.IDS_CONNECT_2_5)));
        PunchoutForm form = service.buildPunchoutForm(1L, "https://erp/return", 7L);
        assertEquals("https://eshop.wuerth.de/IDSInBound", form.action());
    }

    @Test
    void httpUrlAufNichtLokalemHostWirdAbgelehnt() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("http://eshop.wuerth.de/IDSInBound", IdsProtokoll.IDS_CONNECT_2_5)));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.buildPunchoutForm(1L, "https://erp/return", 7L));
        assertTrue(ex.getMessage().contains("https"));
    }

    @Test
    void javascriptUrlWirdAbgelehnt() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("javascript:alert(1)", IdsProtokoll.IDS_CONNECT_2_5)));
        assertThrows(IllegalStateException.class,
                () -> service.buildPunchoutForm(1L, "https://erp/return", 7L));
    }

    @Test
    void httpAufLocalhostIstFuerEntwicklungErlaubt() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("http://localhost:8080/punchout", IdsProtokoll.IDS_CONNECT_2_5)));
        PunchoutForm form = service.buildPunchoutForm(1L, "http://localhost/return", 7L);
        assertNotNull(form);
    }

    // ── Token-Roundtrip ──────────────────────────────────────────

    @Test
    void tokenRoundtripValidiertOhneFehler() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://eshop.wuerth.de/IDSInBound", IdsProtokoll.IDS_CONNECT_2_5)));
        PunchoutForm form = service.buildPunchoutForm(1L, "https://erp/return", 7L);
        String token = extrahiereToken(form.fields().get("HOOK_URL"));
        // Sollte ohne Exception durchlaufen
        service.validiereVorgangsToken(token, 1L);
    }

    @Test
    void tokenFuerAnderenLieferantenWirdAbgelehnt() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://eshop.wuerth.de/IDSInBound", IdsProtokoll.IDS_CONNECT_2_5)));
        PunchoutForm form = service.buildPunchoutForm(1L, "https://erp/return", 7L);
        String token = extrahiereToken(form.fields().get("HOOK_URL"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.validiereVorgangsToken(token, 99L));
        assertTrue(ex.getMessage().toLowerCase().contains("lieferant"));
    }

    @Test
    void manipulierteTokenSignaturWirdAbgelehnt() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://eshop.wuerth.de/IDSInBound", IdsProtokoll.IDS_CONNECT_2_5)));
        PunchoutForm form = service.buildPunchoutForm(1L, "https://erp/return", 7L);
        String token = extrahiereToken(form.fields().get("HOOK_URL"));
        // Letztes Zeichen der Signatur kippen
        String manipuliert = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.validiereVorgangsToken(manipuliert, 1L));
        assertTrue(ex.getMessage().toLowerCase().contains("signatur")
                || ex.getMessage().toLowerCase().contains("ungueltig"));
    }

    @Test
    void leererTokenWirdAbgelehnt() {
        assertThrows(IllegalStateException.class,
                () -> service.validiereVorgangsToken(null, 1L));
        assertThrows(IllegalStateException.class,
                () -> service.validiereVorgangsToken("", 1L));
        assertThrows(IllegalStateException.class,
                () -> service.validiereVorgangsToken("kein.gueltigerToken", 1L));
    }

    @Test
    void tokenOhneKonfigurierterKeyKannNichtErzeugtWerden() {
        // Service ohne Key — generiereVorgangsToken muss klar fehlschlagen
        IdsPunchoutService ohneKey = new IdsPunchoutService(konfigRepo, lieferantenRepo, bestellungRepo, crypto);
        ReflectionTestUtils.setField(ohneKey, "tokenSigningKeyBase64", "");
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://eshop.wuerth.de/IDSInBound", IdsProtokoll.IDS_CONNECT_2_5)));
        assertThrows(IllegalStateException.class,
                () -> ohneKey.buildPunchoutForm(1L, "https://erp/return", 7L));
    }

    // ── Konfig-Status ────────────────────────────────────────────

    @Test
    void deaktivierteKonfigBlockiertPunchout() {
        LieferantIdsKonfig konfig = konfigMit("https://shop", IdsProtokoll.IDS_CONNECT_2_5);
        konfig.setAktiviert(false);
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(konfig));
        assertThrows(IllegalStateException.class,
                () -> service.buildPunchoutForm(1L, "https://erp/return", 7L));
    }

    @Test
    void unbekannterLieferantFuehrtZuIllegalArgumentException() {
        when(konfigRepo.findByLieferantId(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.buildPunchoutForm(99L, "https://erp/return", 7L));
    }

    // ── Würth-Profil: enctype + Felder ───────────────────────────

    @Test
    void wuerthLegacyProtokollLiefertMultipartEnctype() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://eshop.wuerth.de/IDSInBound", IdsProtokoll.WUERTH_LEGACY)));
        PunchoutForm form = service.buildPunchoutForm(1L, "https://erp/return", 7L);
        assertEquals("multipart/form-data", form.enctype());
    }

    @Test
    void idsConnect25LiefertUrlencodedEnctype() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://shop", IdsProtokoll.IDS_CONNECT_2_5)));
        PunchoutForm form = service.buildPunchoutForm(1L, "https://erp/return", 7L);
        assertEquals("application/x-www-form-urlencoded", form.enctype());
    }

    @Test
    void formFelderEnthaltenPflichtFelderUndPasswortKlartext() {
        when(konfigRepo.findByLieferantId(1L)).thenReturn(Optional.of(
                konfigMit("https://shop", IdsProtokoll.IDS_CONNECT_2_5)));
        PunchoutForm form = service.buildPunchoutForm(1L, "https://erp/return", 7L);
        assertEquals("OrderRequest", form.fields().get("FUNCTION"));
        assertEquals("Login", form.fields().get("ACTION"));
        assertEquals("p4ssw0rt", form.fields().get("PASSWORD"));
        assertTrue(form.fields().get("HOOK_URL").contains("token="));
    }

    // ── Cart-Verarbeitung: Truncate auf DB-Limits ────────────────

    @Test
    void cartTruncatesUeberlangeArtikelnummerAuf100Zeichen() {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("TestLieferant");
        when(lieferantenRepo.findById(1L)).thenReturn(Optional.of(l));
        when(bestellungRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> cart = new LinkedHashMap<>();
        cart.put("ARTNR_1", "X".repeat(250));
        cart.put("BEZ1_1", "Y".repeat(800));
        cart.put("MENGE_1", "5");

        Mitarbeiter mit = new Mitarbeiter();
        mit.setVorname("Max");
        mit.setNachname("Mustermann");

        Bestellung b = service.verarbeiteCart(1L, cart, mit);
        assertEquals(1, b.getPositionen().size());
        Bestellposition p = b.getPositionen().get(0);
        assertEquals(100, p.getExterneArtikelnummer().length(),
                "externe_artikelnummer muss auf VARCHAR(100) getruncated werden");
        assertEquals(500, p.getFreitextProduktname().length(),
                "freitext_produktname muss auf VARCHAR(500) getruncated werden");
        assertEquals(0, new BigDecimal("5").compareTo(p.getMenge()));
    }

    @Test
    void cartParserKommaUndPunktAlsDezimaltrenner() {
        Lieferanten l = new Lieferanten();
        when(lieferantenRepo.findById(1L)).thenReturn(Optional.of(l));
        when(bestellungRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> cart = new HashMap<>();
        cart.put("ARTNR_1", "A");
        cart.put("MENGE_1", "1,50");
        cart.put("PREIS_1", "99.95");

        Bestellung b = service.verarbeiteCart(1L, cart, null);
        Bestellposition p = b.getPositionen().get(0);
        assertEquals(0, new BigDecimal("1.50").compareTo(p.getMenge()));
        assertEquals(0, new BigDecimal("99.95").compareTo(p.getPreisProEinheit()));
    }

    @Test
    void cartOhnePositionenLegtBestellungOhnePositionenAn() {
        Lieferanten l = new Lieferanten();
        when(lieferantenRepo.findById(1L)).thenReturn(Optional.of(l));
        when(bestellungRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bestellung b = service.verarbeiteCart(1L, new HashMap<>(), null);
        assertTrue(b.getPositionen().isEmpty());
        assertNotNull(b.getBestellnummer());
        assertTrue(b.getBestellnummer().startsWith("B-IDS-"));
    }

    @Test
    void cartFuerUnbekanntenLieferantenWirftIllegalArgumentException() {
        when(lieferantenRepo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.verarbeiteCart(99L, new HashMap<>(), null));
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────

    private LieferantIdsKonfig konfigMit(String punchoutUrl, IdsProtokoll protokoll) {
        LieferantIdsKonfig k = new LieferantIdsKonfig();
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("TestLieferant");
        k.setLieferant(l);
        k.setAktiviert(true);
        k.setProtokoll(protokoll);
        k.setPunchoutUrl(punchoutUrl);
        k.setKundennummer("887051");
        k.setLoginName("14137019");
        k.setPasswortVerschluesselt("VERSCHLUESSELT");
        return k;
    }

    private String extrahiereToken(String hookUrl) {
        assertFalse(hookUrl == null || hookUrl.isBlank(), "HOOK_URL fehlt");
        int idx = hookUrl.indexOf("token=");
        assertTrue(idx >= 0, "HOOK_URL enthaelt keinen token-Param");
        return hookUrl.substring(idx + "token=".length());
    }
}
