package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * Unit-Tests fuer den BelegService — Kernlogik der Buchhaltungs-Belegerfassung.
 * Fokus: Permission-Check ueber Abteilungs-Berechtigungen, Upload-Validierung
 * (Path-Traversal, MIME-Whitelist, Groessen-Cap) und Kassenbuch-Saldo.
 */
@ExtendWith(MockitoExtension.class)
class BelegServiceTest {

    @Mock private BelegRepository belegRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private AbteilungDokumentBerechtigungRepository berechtigungRepository;
    @Mock private SachkontoRepository sachkontoRepository;
    @Mock private BelegKiAnalyseService kiAnalyseService;
    @Mock private LieferantDokumentRepository lieferantDokumentRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;

    @InjectMocks
    private BelegService service;

    @BeforeEach
    void setup() {
        // uploadPath nicht @Value-injected im Mock-Kontext — bleibt null, ist OK
        // weil unsere Tests entweder validierungs-bedingt vor file write abbrechen
        // (ungueltige Dateien) oder Upload nicht aufrufen.
    }

    // ===================== Permissions =====================

    @Test
    @DisplayName("getPermissions: Mitarbeiter ohne Abteilung darf nichts")
    void getPermissions_ohneAbteilung_keineRechte() {
        Mitarbeiter m = mitarbeiter(7L, Set.of());

        BelegDto.PermissionResponse r = service.getPermissions(m);

        assertThat(r.isDarfSehen()).isFalse();
        assertThat(r.isDarfScannen()).isFalse();
    }

    @Test
    @DisplayName("getPermissions: Mitarbeiter in BELEG-berechtigter Abteilung sieht und scannt")
    void getPermissions_mitBelegBerechtigung_alleRechte() {
        Abteilung a = abteilung(11L);
        Mitarbeiter m = mitarbeiter(7L, Set.of(a));

        given(berechtigungRepository.findSichtbareTypenByAbteilungIds(List.of(11L)))
                .willReturn(List.of("BELEG", "RECHNUNG"));
        given(berechtigungRepository.findScanbarTypenByAbteilungIds(List.of(11L)))
                .willReturn(List.of("BELEG"));

        BelegDto.PermissionResponse r = service.getPermissions(m);

        assertThat(r.isDarfSehen()).isTrue();
        assertThat(r.isDarfScannen()).isTrue();
    }

    @Test
    @DisplayName("getPermissions: Sehen ohne BELEG -> beides false")
    void getPermissions_BelegNichtInListe_keineRechte() {
        Abteilung a = abteilung(11L);
        Mitarbeiter m = mitarbeiter(7L, Set.of(a));

        given(berechtigungRepository.findSichtbareTypenByAbteilungIds(List.of(11L)))
                .willReturn(List.of("RECHNUNG", "LIEFERSCHEIN"));
        given(berechtigungRepository.findScanbarTypenByAbteilungIds(List.of(11L)))
                .willReturn(List.of());

        BelegDto.PermissionResponse r = service.getPermissions(m);

        assertThat(r.isDarfSehen()).isFalse();
        assertThat(r.isDarfScannen()).isFalse();
    }

    @Test
    @DisplayName("darfScannen: nur wenn BELEG in scanbar-Liste")
    void darfScannen_helper() {
        Abteilung a = abteilung(11L);
        Mitarbeiter m = mitarbeiter(7L, Set.of(a));

        given(berechtigungRepository.findSichtbareTypenByAbteilungIds(anyList()))
                .willReturn(List.of("BELEG"));
        given(berechtigungRepository.findScanbarTypenByAbteilungIds(anyList()))
                .willReturn(List.of("BELEG"));

        assertThat(service.darfScannen(m)).isTrue();
    }

    @Test
    @DisplayName("darfScannen: null-Mitarbeiter -> false (kein NPE)")
    void darfScannen_null_false() {
        assertThat(service.darfScannen(null)).isFalse();
    }

    // ===================== Upload-Validierung =====================

    @Test
    @DisplayName("uploadBeleg blockt Path-Traversal im Dateinamen")
    void uploadBeleg_pathTraversal_400() {
        MockMultipartFile f = new MockMultipartFile(
                "datei", "../../etc/passwd.pdf", "application/pdf", new byte[]{1, 2, 3});
        Mitarbeiter m = mitarbeiter(7L, Set.of());

        assertThatThrownBy(() -> service.uploadBeleg(f, m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ungueltiger Dateiname");
    }

    @Test
    @DisplayName("uploadBeleg blockt leere Datei")
    void uploadBeleg_leereDatei_400() {
        MockMultipartFile f = new MockMultipartFile(
                "datei", "leer.pdf", "application/pdf", new byte[0]);
        Mitarbeiter m = mitarbeiter(7L, Set.of());

        assertThatThrownBy(() -> service.uploadBeleg(f, m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Datei fehlt");
    }

    @Test
    @DisplayName("uploadBeleg blockt SVG (XSS-Vektor)")
    void uploadBeleg_svg_400() {
        MockMultipartFile f = new MockMultipartFile(
                "datei", "evil.svg", "image/svg+xml", "<svg/>".getBytes());
        Mitarbeiter m = mitarbeiter(7L, Set.of());

        assertThatThrownBy(() -> service.uploadBeleg(f, m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dateityp");
    }

    @Test
    @DisplayName("uploadBeleg blockt EXE auch mit gefaktem MIME-Type")
    void uploadBeleg_exeExtensionTrotzPdfMime_400() {
        MockMultipartFile f = new MockMultipartFile(
                "datei", "trojan.exe", "application/pdf", new byte[]{1, 2, 3});
        Mitarbeiter m = mitarbeiter(7L, Set.of());

        // MIME ist whitelisted, aber Extension nicht — defense in depth fängt das
        assertThatThrownBy(() -> service.uploadBeleg(f, m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dateiendung");
    }

    @Test
    @DisplayName("uploadBeleg blockt Datei groesser 25 MB")
    void uploadBeleg_zuGross_400() {
        byte[] riesig = new byte[26 * 1024 * 1024];
        MockMultipartFile f = new MockMultipartFile(
                "datei", "gross.pdf", "application/pdf", riesig);
        Mitarbeiter m = mitarbeiter(7L, Set.of());

        assertThatThrownBy(() -> service.uploadBeleg(f, m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zu groß");
    }

    // ===================== Kassenbuch-Saldo =====================

    @Test
    @DisplayName("Kassenbuch: Einnahme positiv, Ausgabe und Privatentnahme negativ")
    void kassenbuch_saldoLogik() {
        Beleg einnahme = belegMit(1L, BelegKategorie.KASSE_EINNAHME,
                LocalDate.of(2026, 1, 5), new BigDecimal("100.00"));
        Beleg ausgabe = belegMit(2L, BelegKategorie.KASSE_AUSGABE,
                LocalDate.of(2026, 1, 6), new BigDecimal("30.00"));
        Beleg privat = belegMit(3L, BelegKategorie.PRIVATENTNAHME,
                LocalDate.of(2026, 1, 7), new BigDecimal("50.00"));

        given(belegRepository.findValidierteByKategorien(
                org.mockito.ArgumentMatchers.eq(BelegStatus.VALIDIERT),
                anyList()))
                .willReturn(List.of(einnahme, ausgabe, privat));

        BelegDto.KassenbuchResponse r = service.getKassenbuch(null, null);

        assertThat(r.getSummeEinnahmen()).isEqualByComparingTo("100.00");
        assertThat(r.getSummeAusgaben()).isEqualByComparingTo("30.00");
        assertThat(r.getSummePrivatentnahmen()).isEqualByComparingTo("50.00");
        // Saldo: +100 - 30 - 50 = 20
        assertThat(r.getSaldoEnde()).isEqualByComparingTo("20.00");
        assertThat(r.getBewegungen()).hasSize(3);
        // Letzte Bewegung (Privatentnahme) ist Saldo 20
        assertThat(r.getBewegungen().get(2).getSaldoNachher()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Kassenbuch: Belege ohne Datum werden ignoriert")
    void kassenbuch_ohneDatum_ignoriert() {
        Beleg ohneDatum = belegMit(1L, BelegKategorie.KASSE_EINNAHME,
                null, new BigDecimal("999.00"));
        Beleg mitDatum = belegMit(2L, BelegKategorie.KASSE_EINNAHME,
                LocalDate.of(2026, 1, 5), new BigDecimal("100.00"));

        given(belegRepository.findValidierteByKategorien(any(), anyList()))
                .willReturn(List.of(ohneDatum, mitDatum));

        BelegDto.KassenbuchResponse r = service.getKassenbuch(null, null);

        assertThat(r.getBewegungen()).hasSize(1);
        assertThat(r.getSaldoEnde()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Kassenbuch: leerer Zeitraum -> Saldo 0")
    void kassenbuch_leer_saldoNull() {
        given(belegRepository.findValidierteByKategorien(any(), anyList()))
                .willReturn(List.of());

        BelegDto.KassenbuchResponse r = service.getKassenbuch(null, null);

        assertThat(r.getSaldoEnde()).isEqualByComparingTo("0.00");
        assertThat(r.getBewegungen()).isEmpty();
    }

    // ===================== Umbuchung (ohne Datei) =====================

    @Test
    @DisplayName("createUmbuchung: Happy-Path -> sofort validiert + ist_umbuchung=true")
    void createUmbuchung_happyPath() {
        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("PRIVATENTNAHME");
        req.setBelegDatum(LocalDate.of(2026, 5, 11));
        req.setBetragBrutto(new BigDecimal("250.00"));
        req.setBeschreibung("Privatentnahme Bar");
        Mitarbeiter m = mitarbeiter(7L, Set.of());

        given(belegRepository.save(any(Beleg.class)))
                .willAnswer(inv -> {
                    Beleg b = inv.getArgument(0);
                    b.setId(99L);
                    return b;
                });

        Beleg result = service.createUmbuchung(req, m);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getStatus()).isEqualTo(BelegStatus.VALIDIERT);
        assertThat(result.getIstUmbuchung()).isTrue();
        assertThat(result.getBelegKategorie()).isEqualTo(BelegKategorie.PRIVATENTNAHME);
        assertThat(result.getBetragBrutto()).isEqualByComparingTo("250.00");
        assertThat(result.getGespeicherterDateiname()).isNull(); // keine Datei -> NULL erlaubt
        assertThat(result.getUploadedBy()).isEqualTo(m);
        assertThat(result.getValidiertVon()).isEqualTo(m);
    }

    @Test
    @DisplayName("createUmbuchung: UNZUGEORDNET ist nicht erlaubt (sonst Geisterbuchung)")
    void createUmbuchung_unzugeordnet_400() {
        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("UNZUGEORDNET");
        req.setBelegDatum(LocalDate.of(2026, 5, 11));
        req.setBetragBrutto(new BigDecimal("10.00"));

        assertThatThrownBy(() -> service.createUmbuchung(req, mitarbeiter(7L, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kategorie");
    }

    @Test
    @DisplayName("createUmbuchung: negativer Betrag wird abgelehnt")
    void createUmbuchung_negativerBetrag_400() {
        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("KASSE_EINNAHME");
        req.setBelegDatum(LocalDate.of(2026, 5, 11));
        req.setBetragBrutto(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> service.createUmbuchung(req, mitarbeiter(7L, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Betrag");
    }

    @Test
    @DisplayName("createUmbuchung: fehlendes Datum wird abgelehnt")
    void createUmbuchung_ohneDatum_400() {
        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("BANK");
        req.setBelegDatum(null);
        req.setBetragBrutto(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.createUmbuchung(req, mitarbeiter(7L, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Datum");
    }

    @Test
    @DisplayName("createUmbuchung: ueberlange Beschreibung wird abgelehnt (Limit-Check)")
    void createUmbuchung_beschreibungZuLang_400() {
        BelegDto.UmbuchungCreateRequest req = new BelegDto.UmbuchungCreateRequest();
        req.setBelegKategorie("PRIVATENTNAHME");
        req.setBelegDatum(LocalDate.of(2026, 5, 11));
        req.setBetragBrutto(new BigDecimal("1.00"));
        req.setBeschreibung("X".repeat(501));

        assertThatThrownBy(() -> service.createUmbuchung(req, mitarbeiter(7L, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Beschreibung");
    }

    // ===================== findCaller: Token + Session-Mapping =====================
    //
    // Regression: vor diesem Fix mappte der Controller den eingeloggten PC-User
    // ueber email-Match auf Mitarbeiter. Wenn der Frontend-Login-Username NICHT
    // mit Mitarbeiter.email uebereinstimmte, kam null raus -> 403 auf alle
    // Buchhaltungs-Endpoints, obwohl die FK FrontendUserProfile.mitarbeiter
    // korrekt verknuepft war. findCaller nutzt nun primaer diese FK.

    @Test
    @DisplayName("findCaller: PC-Login mit verknuepftem Mitarbeiter via Profile-FK -> Treffer")
    void findCaller_pcLogin_mitProfileFk_liefertMitarbeiter() {
        Mitarbeiter m = mitarbeiter(42L, Set.of());
        m.setEmail("buchhalter@firma.example");
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setId(7L);
        profile.setMitarbeiter(m);
        given(frontendUserProfileRepository.findById(7L)).willReturn(java.util.Optional.of(profile));
        Authentication auth = pcAuth(7L, "thomas-login");

        Mitarbeiter result = service.findCaller(null, auth);

        assertThat(result).isEqualTo(m);
    }

    @Test
    @DisplayName("findCaller: Username != Email aber Profile-FK gesetzt -> kein 403 (Hauptbug)")
    void findCaller_usernameUngleichEmail_aberFkVerknuepft_liefertMitarbeiter() {
        Mitarbeiter m = mitarbeiter(42L, Set.of());
        m.setEmail("max@firma.example");
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setId(7L);
        profile.setMitarbeiter(m);
        given(frontendUserProfileRepository.findById(7L)).willReturn(java.util.Optional.of(profile));
        // Login-Name hat absichtlich nichts mit der Mitarbeiter-Email zu tun
        Authentication auth = pcAuth(7L, "anderer-username");

        Mitarbeiter result = service.findCaller(null, auth);

        assertThat(result).as("FK-Mapping muss vor Email-Fallback greifen").isEqualTo(m);
    }

    @Test
    @DisplayName("findCaller: PC-Login ohne Profile-FK faellt auf Email-Match zurueck")
    void findCaller_ohneProfileFk_emailFallback() {
        Mitarbeiter m = mitarbeiter(42L, Set.of());
        m.setEmail("buchhalter@firma.example");
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setId(7L);
        profile.setMitarbeiter(null); // FK noch nicht gesetzt
        given(frontendUserProfileRepository.findById(7L)).willReturn(java.util.Optional.of(profile));
        given(mitarbeiterRepository.findAll()).willReturn(List.of(m));
        Authentication auth = pcAuth(7L, "buchhalter@firma.example");

        Mitarbeiter result = service.findCaller(null, auth);

        assertThat(result).isEqualTo(m);
    }

    @Test
    @DisplayName("findCaller: deaktivierter Mitarbeiter wird nicht akzeptiert")
    void findCaller_inaktiverMitarbeiter_null() {
        Mitarbeiter m = mitarbeiter(42L, Set.of());
        m.setAktiv(false);
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setId(7L);
        profile.setMitarbeiter(m);
        given(frontendUserProfileRepository.findById(7L)).willReturn(java.util.Optional.of(profile));
        // Email-Fallback findet ihn ebenfalls nicht
        given(mitarbeiterRepository.findAll()).willReturn(List.of(m));
        Authentication auth = pcAuth(7L, "x@y.example");

        Mitarbeiter result = service.findCaller(null, auth);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findCaller: Token hat Vorrang vor Session-Auth")
    void findCaller_tokenVorSession() {
        Mitarbeiter byToken = mitarbeiter(100L, Set.of());
        given(mitarbeiterRepository.findByLoginTokenAndAktivTrue("abc-token"))
                .willReturn(java.util.Optional.of(byToken));

        Mitarbeiter result = service.findCaller("abc-token", pcAuth(7L, "egal"));

        assertThat(result).isEqualTo(byToken);
    }

    @Test
    @DisplayName("findCaller: weder Token noch Auth -> null")
    void findCaller_keineAuthInfo_null() {
        assertThat(service.findCaller(null, null)).isNull();
        assertThat(service.findCaller("", null)).isNull();
    }

    private static Authentication pcAuth(Long profileId, String username) {
        FrontendUserPrincipal p = new FrontendUserPrincipal(profileId, username, username,
                "$2a$10$dummy", true, Set.of());
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    // ===================== Test-Helfer =====================

    private static Mitarbeiter mitarbeiter(Long id, Set<Abteilung> abteilungen) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        m.setAbteilungen(new HashSet<>(abteilungen));
        return m;
    }

    private static Abteilung abteilung(Long id) {
        Abteilung a = new Abteilung();
        a.setId(id);
        a.setName("Buchhaltung");
        return a;
    }

    private static Beleg belegMit(Long id, BelegKategorie kategorie, LocalDate datum, BigDecimal brutto) {
        Beleg b = new Beleg();
        b.setId(id);
        b.setBelegKategorie(kategorie);
        b.setStatus(BelegStatus.VALIDIERT);
        b.setBelegDatum(datum);
        b.setBetragBrutto(brutto);
        return b;
    }

    // Suppress unused warnings — used in some scenarios for thoroughness
    @SuppressWarnings("unused")
    private void unused() {
        lenient().when(belegRepository.findById(any())).thenReturn(java.util.Optional.empty());
    }
}
