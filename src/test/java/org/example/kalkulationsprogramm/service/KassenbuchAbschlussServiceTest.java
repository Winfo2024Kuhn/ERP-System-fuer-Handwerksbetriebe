package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAudit;
import org.example.kalkulationsprogramm.domain.BelegKategorie;
import org.example.kalkulationsprogramm.domain.BelegStatus;
import org.example.kalkulationsprogramm.domain.KassenbuchMonatsabschluss;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.KassenbuchAbschlussDto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KassenbuchMonatsabschlussRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests fuer den Monatsabschluss und das Storno.
 *
 * <p>Der Abschluss ist der Punkt, an dem aus vorlaeufigen Aufzeichnungen eine
 * Buchhaltung wird. Getestet wird deshalb vor allem, wann er <em>nicht</em>
 * laufen darf -- ein zu frueher oder ein Abschluss mit Luecken waere
 * schlimmer als gar keiner.</p>
 *
 * <p>DSGVO: nur Dummy-Daten (Max Mustermann).</p>
 */
@ExtendWith(MockitoExtension.class)
class KassenbuchAbschlussServiceTest {

    @Mock private BelegRepository belegRepository;
    @Mock private KassenbuchMonatsabschlussRepository abschlussRepository;
    @Mock private BelegAuditService auditService;
    @Mock private KasseSaldoService kasseSaldoService;

    @InjectMocks private KassenbuchAbschlussService service;

    private Mitarbeiter maxMustermann;

    /** Ein Monat, der garantiert vorbei ist -- sonst blockt die Vorbei-Prüfung. */
    private YearMonth abgelaufenerMonat;

    @BeforeEach
    void setUp() {
        maxMustermann = new Mitarbeiter();
        maxMustermann.setId(1L);
        maxMustermann.setVorname("Max");
        maxMustermann.setNachname("Mustermann");
        abgelaufenerMonat = YearMonth.from(LocalDate.now()).minusMonths(1);
    }

    // ===================== Vorschau =====================

    @Test
    @DisplayName("Vorschau meldet ungeprüfte Belege als Hindernis")
    void vorschauMeldetUngeprueft() {
        gibtKeinenFruererenAbschluss();
        given(belegRepository.countUngeprueftImZeitraum(any(), any())).willReturn(3L);
        given(belegRepository.countOhneBelegdatum()).willReturn(0L);
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(BigDecimal.ZERO);

        KassenbuchAbschlussDto.Vorschau v = service.vorschau(
                abgelaufenerMonat.getYear(), abgelaufenerMonat.getMonthValue());

        assertThat(v.isAbschlussMoeglich()).isFalse();
        assertThat(v.getAnzahlUngeprueft()).isEqualTo(3);
        assertThat(v.getHindernisse()).anyMatch(h -> h.contains("ungeprüfte Belege"));
    }

    @Test
    @DisplayName("Vorschau meldet Belege ohne Datum als Hindernis")
    void vorschauMeldetBelegeOhneDatum() {
        gibtKeinenFruererenAbschluss();
        given(belegRepository.countUngeprueftImZeitraum(any(), any())).willReturn(0L);
        given(belegRepository.countOhneBelegdatum()).willReturn(2L);
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(BigDecimal.ZERO);

        KassenbuchAbschlussDto.Vorschau v = service.vorschau(
                abgelaufenerMonat.getYear(), abgelaufenerMonat.getMonthValue());

        assertThat(v.isAbschlussMoeglich()).isFalse();
        assertThat(v.getHindernisse()).anyMatch(h -> h.contains("kein Datum"));
    }

    @Test
    @DisplayName("Ein noch laufender Monat kann nicht abgeschlossen werden")
    void laufenderMonatIstBlockiert() {
        YearMonth jetzt = YearMonth.from(LocalDate.now());
        gibtKeinenFruererenAbschluss();
        lenient().when(belegRepository.countUngeprueftImZeitraum(any(), any())).thenReturn(0L);
        lenient().when(belegRepository.countOhneBelegdatum()).thenReturn(0L);
        lenient().when(kasseSaldoService.berechneSaldoBis(any())).thenReturn(BigDecimal.ZERO);

        KassenbuchAbschlussDto.Vorschau v = service.vorschau(jetzt.getYear(), jetzt.getMonthValue());

        assertThat(v.isAbschlussMoeglich()).isFalse();
        assertThat(v.getHindernisse()).anyMatch(h -> h.contains("noch nicht vorbei"));
    }

    @Test
    @DisplayName("Monate müssen lückenlos aufeinander folgen")
    void monateMuessenLueckenlosFolgen() {
        // Zuletzt abgeschlossen: vor drei Monaten. Dann ist als Nächstes der
        // Monat davor dran, nicht der letzte.
        YearMonth letzter = abgelaufenerMonat.minusMonths(3);
        KassenbuchMonatsabschluss vorheriger = new KassenbuchMonatsabschluss();
        vorheriger.setJahr(letzter.getYear());
        vorheriger.setMonat(letzter.getMonthValue());
        given(abschlussRepository.findFirstByOrderByJahrDescMonatDesc()).willReturn(Optional.of(vorheriger));
        given(abschlussRepository.existsByJahrAndMonat(any(), any())).willReturn(false);
        lenient().when(belegRepository.countUngeprueftImZeitraum(any(), any())).thenReturn(0L);
        lenient().when(belegRepository.countOhneBelegdatum()).thenReturn(0L);
        lenient().when(kasseSaldoService.berechneSaldoBis(any())).thenReturn(BigDecimal.ZERO);

        KassenbuchAbschlussDto.Vorschau v = service.vorschau(
                abgelaufenerMonat.getYear(), abgelaufenerMonat.getMonthValue());

        assertThat(v.isAbschlussMoeglich()).isFalse();
        assertThat(v.getHindernisse()).anyMatch(h -> h.contains("lückenlos"));
    }

    @Test
    @DisplayName("Ein bereits abgeschlossener Monat wird nicht zweimal abgeschlossen")
    void bereitsAbgeschlossenerMonat() {
        given(abschlussRepository.existsByJahrAndMonat(any(), any())).willReturn(true);
        lenient().when(abschlussRepository.findFirstByOrderByJahrDescMonatDesc()).thenReturn(Optional.empty());
        lenient().when(kasseSaldoService.berechneSaldoBis(any())).thenReturn(BigDecimal.ZERO);

        KassenbuchAbschlussDto.Vorschau v = service.vorschau(
                abgelaufenerMonat.getYear(), abgelaufenerMonat.getMonthValue());

        assertThat(v.isAbschlussMoeglich()).isFalse();
        assertThat(v.isBereitsAbgeschlossen()).isTrue();
    }

    @Test
    @DisplayName("Ungültiger Monat wird abgewiesen")
    void ungueltigerMonat() {
        assertThatThrownBy(() -> service.vorschau(2026, 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monat");
    }

    // ===================== Abschluss =====================

    @Test
    @DisplayName("Abschluss vergibt lückenlose Nummern und schreibt die Belege fest")
    void abschlussVergibtNummern() {
        gibtKeinenFruererenAbschluss();
        given(belegRepository.countUngeprueftImZeitraum(any(), any())).willReturn(0L);
        given(belegRepository.countOhneBelegdatum()).willReturn(0L);
        given(kasseSaldoService.berechneSaldoBis(any())).willReturn(new BigDecimal("50.00"));

        Beleg einnahme = beleg(1L, BelegKategorie.KASSE_EINNAHME, "120.00");
        Beleg ausgabe = beleg(2L, BelegKategorie.KASSE_AUSGABE, "30.00");
        Beleg bank = beleg(3L, BelegKategorie.BANK, "80.00");
        given(belegRepository.findFestzuschreibendeImZeitraum(any(), any()))
                .willReturn(List.of(einnahme, ausgabe, bank));

        AtomicLong zaehler = new AtomicLong();
        given(auditService.zieheNaechsteLaufendeNummer()).willAnswer(i -> zaehler.incrementAndGet());
        given(abschlussRepository.saveAndFlush(any())).willAnswer(i -> {
            KassenbuchMonatsabschluss m = i.getArgument(0);
            if (m.getId() == null) m.setId(99L);
            return m;
        });
        BelegAudit protokoll = new BelegAudit();
        protokoll.setChainIndex(12L);
        protokoll.setEntryHash("d".repeat(64));
        given(auditService.protokolliereMonatsabschluss(any(), any(), any())).willReturn(protokoll);

        KassenbuchAbschlussDto.Ergebnis e = service.schliesseMonatAb(
                abgelaufenerMonat.getYear(), abgelaufenerMonat.getMonthValue(),
                null, maxMustermann, null);

        // Nummern lueckenlos in der Reihenfolge der Belege
        assertThat(einnahme.getLaufendeNummer()).isEqualTo(1L);
        assertThat(ausgabe.getLaufendeNummer()).isEqualTo(2L);
        assertThat(bank.getLaufendeNummer()).isEqualTo(3L);
        assertThat(einnahme.istFestgeschrieben()).isTrue();
        assertThat(einnahme.getMonatsabschlussId()).isEqualTo(99L);

        // Nur Bargeldbewegungen zaehlen in die Kassen-Summen; die Bank-Buchung
        // bekommt zwar eine Nummer, gehoert aber nicht ins Kassenbuch.
        assertThat(e.getSummeEinnahmen()).isEqualByComparingTo("120.00");
        assertThat(e.getSummeAusgaben()).isEqualByComparingTo("30.00");
        assertThat(e.getAnzahlBelege()).isEqualTo(3);
        assertThat(e.getErsteLaufendeNummer()).isEqualTo(1L);
        assertThat(e.getLetzteLaufendeNummer()).isEqualTo(3L);
        assertThat(e.getEntryHash()).isEqualTo("d".repeat(64));

        verify(auditService, org.mockito.Mockito.times(3))
                .protokolliereFestschreibung(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Abschluss mit Hindernissen wird abgelehnt und schreibt nichts fest")
    void abschlussMitHindernissenWirdAbgelehnt() {
        gibtKeinenFruererenAbschluss();
        given(belegRepository.countUngeprueftImZeitraum(any(), any())).willReturn(1L);
        given(belegRepository.countOhneBelegdatum()).willReturn(0L);

        assertThatThrownBy(() -> service.schliesseMonatAb(
                abgelaufenerMonat.getYear(), abgelaufenerMonat.getMonthValue(),
                null, maxMustermann, null))
                .isInstanceOf(KassenbuchGesperrtException.class);

        verify(belegRepository, never()).save(any());
        verify(abschlussRepository, never()).saveAndFlush(any());
    }

    // ===================== Storno =====================

    @Test
    @DisplayName("Storno einer Bar-Ausgabe erzeugt eine Einnahme in gleicher Höhe")
    void stornoDrehtDieRichtungUm() {
        Beleg original = beleg(5L, BelegKategorie.KASSE_AUSGABE, "44.00");
        original.setFestgeschrieben(true);
        original.setLaufendeNummer(12L);
        original.setBeschreibung("Werkzeug");
        given(belegRepository.findById(5L)).willReturn(Optional.of(original));
        given(abschlussRepository.existsByJahrAndMonat(any(), any())).willReturn(false);
        given(belegRepository.saveAndFlush(any())).willAnswer(i -> {
            Beleg b = i.getArgument(0);
            b.setId(77L);
            return b;
        });
        given(kasseSaldoService.projiziereSaldo(any(), any(), any(), any()))
                .willReturn(new BigDecimal("500.00"));

        Beleg storno = service.storniere(5L, "Betrag falsch erfasst", maxMustermann, null);

        assertThat(storno.getBelegKategorie()).isEqualTo(BelegKategorie.KASSE_EINNAHME);
        assertThat(storno.getBetragBrutto()).isEqualByComparingTo("44.00");
        assertThat(storno.getStornoFuerBelegId()).isEqualTo(5L);
        assertThat(storno.getBelegDatum()).isEqualTo(LocalDate.now());
        assertThat(storno.getBeschreibung()).contains("Storno zu Nr. 12").contains("Werkzeug");
        assertThat(Boolean.TRUE.equals(storno.getIstUmbuchung())).isTrue();

        // Das Original bleibt stehen und verweist auf seine Gegenbuchung.
        assertThat(original.getStorniertDurchBelegId()).isEqualTo(77L);
        assertThat(original.getStorniertAm()).isNotNull();
        assertThat(original.getStatus()).isEqualTo(BelegStatus.VALIDIERT);
    }

    @Test
    @DisplayName("Storno einer Nicht-Bar-Buchung dreht das Vorzeichen statt der Kategorie")
    void stornoBeiNichtBarNegiertDenBetrag() {
        Beleg original = beleg(6L, BelegKategorie.BANK, "200.00");
        original.setFestgeschrieben(true);
        original.setLaufendeNummer(20L);
        given(belegRepository.findById(6L)).willReturn(Optional.of(original));
        given(abschlussRepository.existsByJahrAndMonat(any(), any())).willReturn(false);
        given(belegRepository.saveAndFlush(any())).willAnswer(i -> {
            Beleg b = i.getArgument(0);
            b.setId(78L);
            return b;
        });

        Beleg storno = service.storniere(6L, "Doppelt erfasst", maxMustermann, null);

        assertThat(storno.getBelegKategorie()).isEqualTo(BelegKategorie.BANK);
        assertThat(storno.getBetragBrutto()).isEqualByComparingTo("-200.00");
    }

    @Test
    @DisplayName("Ein nicht festgeschriebener Beleg wird nicht storniert, sondern normal bearbeitet")
    void offenerBelegWirdNichtStorniert() {
        Beleg original = beleg(7L, BelegKategorie.KASSE_AUSGABE, "10.00");
        given(belegRepository.findById(7L)).willReturn(Optional.of(original));

        assertThatThrownBy(() -> service.storniere(7L, "Tippfehler", maxMustermann, null))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining("noch nicht festgeschrieben");
    }

    @Test
    @DisplayName("Ein bereits stornierter Beleg wird nicht doppelt storniert")
    void doppeltesStornoWirdAbgelehnt() {
        Beleg original = beleg(8L, BelegKategorie.KASSE_AUSGABE, "10.00");
        original.setFestgeschrieben(true);
        original.setStorniertDurchBelegId(99L);
        given(belegRepository.findById(8L)).willReturn(Optional.of(original));

        assertThatThrownBy(() -> service.storniere(8L, "Nochmal", maxMustermann, null))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining("bereits storniert");
    }

    @Test
    @DisplayName("Eine Stornobuchung kann nicht selbst storniert werden")
    void stornoEinesStornosWirdAbgelehnt() {
        Beleg storno = beleg(9L, BelegKategorie.KASSE_EINNAHME, "10.00");
        storno.setFestgeschrieben(true);
        storno.setStornoFuerBelegId(5L);
        given(belegRepository.findById(9L)).willReturn(Optional.of(storno));

        assertThatThrownBy(() -> service.storniere(9L, "Doch nicht", maxMustermann, null))
                .isInstanceOf(KassenbuchGesperrtException.class)
                .hasMessageContaining("Stornobuchung");
    }

    @Test
    @DisplayName("Storno ohne Begründung wird abgelehnt")
    void stornoOhneGrundWirdAbgelehnt() {
        assertThatThrownBy(() -> service.storniere(5L, "  ", maxMustermann, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warum");
    }

    // ===================== Hilfen =====================

    private void gibtKeinenFruererenAbschluss() {
        given(abschlussRepository.existsByJahrAndMonat(any(), any())).willReturn(false);
        given(abschlussRepository.findFirstByOrderByJahrDescMonatDesc()).willReturn(Optional.empty());
    }

    private Beleg beleg(Long id, BelegKategorie kategorie, String brutto) {
        Beleg b = new Beleg();
        b.setId(id);
        b.setBelegKategorie(kategorie);
        b.setStatus(BelegStatus.VALIDIERT);
        b.setBetragBrutto(new BigDecimal(brutto));
        b.setBelegDatum(abgelaufenerMonat.atDay(5));
        b.setUploadDatum(LocalDateTime.now());
        return b;
    }
}
