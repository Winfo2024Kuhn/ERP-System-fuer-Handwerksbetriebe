package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegKiAnalyseStatus;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests fuer die Auto-Erzeugung der Eingangsrechnung aus dem Beleg-Scanner.
 *
 * Kernlogik:
 *  - KI liefert dokumentTyp=RECHNUNG/GUTSCHRIFT + Beleg hat Lieferant ->
 *    LieferantDokument + LieferantGeschaeftsdokument anlegen, verknuepft mit dem Beleg
 *  - Sonst: kein Auto-LGD
 *  - Doppel-Anlegen verhindern (Re-Analyse, Duplikat-Rechnungsnummer)
 *
 * Statt das Gemini-API zu mocken, testen wir den uebergeordneten Flow:
 * geminiService.analyzeFile(...) wird gestubbt und liefert ein AnalyzeResponse.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BelegKiAnalyseServiceTest {

    @Mock private BelegRepository belegRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantDokumentRepository lieferantDokumentRepository;
    @Mock private LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    @Mock private GeminiDokumentAnalyseService geminiService;
    @Mock private BelegKiKostenkontoService kostenkontoService;
    // Auch das automatische Auslesen aendert Werte am Beleg und wird deshalb
    // protokolliert. Hier gemockt; der Protokoll-Pfad selbst haengt in
    // BelegAuditKettenRoundtripTest.
    @Mock private BelegAuditService auditService;

    // ObjectMapper kein @Mock — echte Serialisierung
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BelegKiAnalyseService service;

    @BeforeEach
    void setup() throws Exception {
        // @Value-injected Felder werden im Mockito-Kontext nicht gesetzt.
        // uploadPath + objectMapper per Reflection auf Test-Werte setzen,
        // sonst NPE in Paths.get(null, ...) bzw. NPE bei JSON-Serialisierung.
        setField("uploadPath", "uploads");
        setField("objectMapper", objectMapper);
    }

    private void setField(String name, Object value) throws Exception {
        var f = BelegKiAnalyseService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    @DisplayName("RECHNUNG + Lieferant gesetzt -> LD + LGD werden angelegt")
    void rechnungMitLieferant_erzeugtEingangsrechnung() {
        Lieferanten lief = lieferant(42L, "Aral Tankstelle");
        Beleg beleg = beleg(7L, lief);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(analyzeResponseRechnung("RE-2026-001"));
        given(lieferantDokumentRepository.findByBelegId(7L)).willReturn(Optional.empty());
        given(lieferantGeschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(
                anyLong(), anyString())).willReturn(false);
        // save() echo
        given(lieferantDokumentRepository.save(any(LieferantDokument.class)))
                .willAnswer(inv -> {
                    LieferantDokument d = inv.getArgument(0);
                    d.setId(123L);
                    return d;
                });

        // ObjectMapper per Reflection einbauen — Mockito InjectMocks injiziert nur @Mock-Felder
        service.analysiereBelegAsync(7L);

        // Beleg-Update: dokumentTyp gesetzt, Status DONE
        verify(belegRepository, org.mockito.Mockito.atLeastOnce()).save(any(Beleg.class));
        assertThat(beleg.getDokumentTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(beleg.getKiAnalyseStatus()).isEqualTo(BelegKiAnalyseStatus.DONE);

        // LD wurde gespeichert mit Lieferant + Beleg-FK
        ArgumentCaptor<LieferantDokument> ldCap = ArgumentCaptor.forClass(LieferantDokument.class);
        verify(lieferantDokumentRepository).save(ldCap.capture());
        LieferantDokument ld = ldCap.getValue();
        assertThat(ld.getLieferant()).isEqualTo(lief);
        assertThat(ld.getTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
        assertThat(ld.getBeleg()).isEqualTo(beleg);
        assertThat(ld.getGespeicherterDateiname()).startsWith("belege/");

        // LGD wurde gespeichert
        ArgumentCaptor<LieferantGeschaeftsdokument> lgdCap =
                ArgumentCaptor.forClass(LieferantGeschaeftsdokument.class);
        verify(lieferantGeschaeftsdokumentRepository).save(lgdCap.capture());
        LieferantGeschaeftsdokument lgd = lgdCap.getValue();
        assertThat(lgd.getDokument()).isEqualTo(ld);
        assertThat(lgd.getDokumentNummer()).isEqualTo("RE-2026-001");
    }

    @Test
    @DisplayName("KASSENBON (SONSTIG) -> KEIN Auto-LGD")
    void sonstig_keinAutoLGD() {
        Lieferanten lief = lieferant(42L, "Aral Tankstelle");
        Beleg beleg = beleg(7L, lief);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(analyzeResponseTyp(LieferantDokumentTyp.SONSTIG, null));

        service.analysiereBelegAsync(7L);

        verify(lieferantDokumentRepository, never()).save(any());
        verify(lieferantGeschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("RECHNUNG ohne Lieferant -> KEIN Auto-LGD (Tankquittung ohne Lieferanten-Treffer)")
    void rechnungOhneLieferant_keinAutoLGD() {
        Beleg beleg = beleg(7L, null);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(analyzeResponseRechnung("RE-X"));
        given(lieferantenRepository.findByLieferantennameIgnoreCase(anyString()))
                .willReturn(Optional.empty());

        service.analysiereBelegAsync(7L);

        verify(lieferantDokumentRepository, never()).save(any());
        verify(lieferantGeschaeftsdokumentRepository, never()).save(any());
        assertThat(beleg.getDokumentTyp()).isEqualTo(LieferantDokumentTyp.RECHNUNG);
    }

    @Test
    @DisplayName("RECHNUNG mit Lieferant aber LD existiert bereits (Re-Analyse) -> kein neues LD")
    void reAnalyse_idempotent() {
        Lieferanten lief = lieferant(42L, "Aral");
        Beleg beleg = beleg(7L, lief);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(analyzeResponseRechnung("RE-1"));
        given(lieferantDokumentRepository.findByBelegId(7L))
                .willReturn(Optional.of(new LieferantDokument()));

        service.analysiereBelegAsync(7L);

        verify(lieferantDokumentRepository, never()).save(any());
        verify(lieferantGeschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("RECHNUNG mit Lieferant + Duplikat-Rechnungsnummer -> kein neues LD")
    void duplikatRechnungsnummer_idempotent() {
        Lieferanten lief = lieferant(42L, "Aral");
        Beleg beleg = beleg(7L, lief);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(analyzeResponseRechnung("RE-DUPL"));
        given(lieferantDokumentRepository.findByBelegId(7L)).willReturn(Optional.empty());
        given(lieferantGeschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(
                42L, "RE-DUPL")).willReturn(true);

        service.analysiereBelegAsync(7L);

        verify(lieferantDokumentRepository, never()).save(any());
        verify(lieferantGeschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("KI-Vorschlag-Lieferant ohne DB-Match -> Feld bleibt null (kein Falsch-Vorschlag im PC-Dialog)")
    void kiVorgeschlagenerLieferant_nullWennNichtInDb() {
        // Tankquittung Q1: KI erkennt "Q1 Energie AG", dieser Lieferant ist aber
        // NICHT in der Stammdaten-Tabelle angelegt. Der "KI-Vorschlag: Q1 Energie AG"
        // soll dann NICHT im Validier-Dialog erscheinen, weil der Buchhalter ihn
        // ohnehin nicht uebernehmen koennte.
        Beleg beleg = beleg(7L, null);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(LieferantDokumentDto.AnalyzeResponse.builder()
                        .dokumentTyp(LieferantDokumentTyp.SONSTIG)
                        .lieferantName("Q1 Energie AG")
                        .build());
        given(lieferantenRepository.findByLieferantennameIgnoreCase("Q1 Energie AG"))
                .willReturn(Optional.empty());

        service.analysiereBelegAsync(7L);

        assertThat(beleg.getLieferant()).isNull();
        assertThat(beleg.getKiVorgeschlagenerLieferant()).isNull();
    }

    @Test
    @DisplayName("KI-Vorschlag-Lieferant MIT DB-Match -> Lieferant + Vorschlag gesetzt")
    void kiVorgeschlagenerLieferant_gesetztBeiDbMatch() {
        Lieferanten lief = lieferant(42L, "Aral Tankstelle");
        Beleg beleg = beleg(7L, null);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(LieferantDokumentDto.AnalyzeResponse.builder()
                        .dokumentTyp(LieferantDokumentTyp.SONSTIG)
                        .lieferantName("aral tankstelle")
                        .build());
        given(lieferantenRepository.findByLieferantennameIgnoreCase("aral tankstelle"))
                .willReturn(Optional.of(lief));

        service.analysiereBelegAsync(7L);

        assertThat(beleg.getLieferant()).isEqualTo(lief);
        assertThat(beleg.getKiVorgeschlagenerLieferant()).isEqualTo("Aral Tankstelle");
    }

    @Test
    @DisplayName("ableitenBelegKategorie: KREDITKARTE -> KREDITKARTE, BAR -> KASSE_AUSGABE, Lastschrift/Ueberweisung/PayPal/Vorkasse -> BANK")
    void ableitenBelegKategorie_mapping() {
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("KREDITKARTE"))
                .isEqualTo(BelegKategorie.KREDITKARTE);
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("BAR"))
                .isEqualTo(BelegKategorie.KASSE_AUSGABE);
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("SEPA_LASTSCHRIFT"))
                .isEqualTo(BelegKategorie.BANK);
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("UEBERWEISUNG"))
                .isEqualTo(BelegKategorie.BANK);
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("PAYPAL"))
                .isEqualTo(BelegKategorie.BANK);
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("VORAUSKASSE"))
                .isEqualTo(BelegKategorie.BANK);
        // SONSTIGE/null -> keine Ableitung, Buchhalter waehlt manuell
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("SONSTIGE")).isNull();
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie(null)).isNull();
        assertThat(BelegKiAnalyseService.ableitenBelegKategorie("")).isNull();
    }

    @Test
    @DisplayName("Manuell gesetzte BelegKategorie wird NICHT von der KI-Ableitung ueberschrieben")
    void belegKategorie_manuellGesetzt_wirdNichtUeberschrieben() {
        // Buchhalter hat den Beleg bereits auf BANK gestellt (z.B. weil Mobile-User
        // den Upload-Knopf "Bank-Beleg" gedrueckt hat). KI erkennt KREDITKARTE als
        // Zahlungsart — die Kategorie darf trotzdem nicht auf KREDITKARTE
        // zurueckgesetzt werden.
        Beleg beleg = beleg(7L, null);
        beleg.setBelegKategorie(BelegKategorie.BANK);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(LieferantDokumentDto.AnalyzeResponse.builder()
                        .dokumentTyp(LieferantDokumentTyp.SONSTIG)
                        .zahlungsart("KREDITKARTE")
                        .build());

        service.analysiereBelegAsync(7L);

        assertThat(beleg.getBelegKategorie()).isEqualTo(BelegKategorie.BANK);
    }

    @Test
    @DisplayName("Kartenzahlung -> WO GEZAHLT wird auf KREDITKARTE gesetzt (nicht mehr UNZUGEORDNET)")
    void zahlungsart_treibtBelegKategorie() {
        Beleg beleg = beleg(7L, null);
        assertThat(beleg.getBelegKategorie()).isEqualTo(BelegKategorie.UNZUGEORDNET);

        given(belegRepository.findById(7L)).willReturn(Optional.of(beleg));
        given(geminiService.analyzeFile(any(), anyString()))
                .willReturn(LieferantDokumentDto.AnalyzeResponse.builder()
                        .dokumentTyp(LieferantDokumentTyp.SONSTIG)
                        .zahlungsart("KREDITKARTE")
                        .build());

        service.analysiereBelegAsync(7L);

        assertThat(beleg.getBelegKategorie()).isEqualTo(BelegKategorie.KREDITKARTE);
    }

    // ===================== Helfer =====================

    private static Lieferanten lieferant(Long id, String name) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        l.setLieferantenname(name);
        l.setIstAktiv(true);
        return l;
    }

    private static Beleg beleg(Long id, Lieferanten lief) {
        Beleg b = new Beleg();
        b.setId(id);
        b.setStatus(BelegStatus.NEU);
        b.setKiAnalyseStatus(BelegKiAnalyseStatus.PENDING);
        b.setBelegKategorie(BelegKategorie.UNZUGEORDNET);
        b.setLieferant(lief);
        b.setGespeicherterDateiname("UUID_abc.pdf");
        b.setOriginalDateiname("rechnung.pdf");
        return b;
    }

    private static LieferantDokumentDto.AnalyzeResponse analyzeResponseRechnung(String nummer) {
        return analyzeResponseTyp(LieferantDokumentTyp.RECHNUNG, nummer);
    }

    private static LieferantDokumentDto.AnalyzeResponse analyzeResponseTyp(
            LieferantDokumentTyp typ, String nummer) {
        return LieferantDokumentDto.AnalyzeResponse.builder()
                .dokumentTyp(typ)
                .dokumentNummer(nummer)
                .betragBrutto(new BigDecimal("123.45"))
                .aiConfidence(0.95)
                .build();
    }
}
