package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelPreisHistorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.ArtikelPreisHistorieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArtikelPreisHookServiceTest {

    @Mock
    private ArtikelPreisHistorieRepository historieRepository;

    @Mock
    private ArtikelDurchschnittspreisService durchschnittspreisService;

    @InjectMocks
    private ArtikelPreisHookService hook;

    private Artikel artikel;
    private Lieferanten lieferant;

    @BeforeEach
    void setUp() {
        artikel = artikel(1L);
        lieferant = lieferant(2L);
    }

    @Test
    void rechnungKilogramm_schreibtHistorieUndAktualisiertDurchschnitt() {
        hook.registriere(artikel, lieferant, new BigDecimal("1.50"), new BigDecimal("100"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.RECHNUNG, "EXT-123", "RE-2026-0001", null);

        ArgumentCaptor<ArtikelPreisHistorie> captor = ArgumentCaptor.forClass(ArtikelPreisHistorie.class);
        verify(historieRepository).save(captor.capture());
        ArtikelPreisHistorie saved = captor.getValue();
        assertThat(saved.getArtikel()).isEqualTo(artikel);
        assertThat(saved.getLieferant()).isEqualTo(lieferant);
        assertThat(saved.getPreis()).isEqualByComparingTo("1.50");
        assertThat(saved.getMenge()).isEqualByComparingTo("100");
        assertThat(saved.getEinheit()).isEqualTo(Verrechnungseinheit.KILOGRAMM);
        assertThat(saved.getQuelle()).isEqualTo(PreisQuelle.RECHNUNG);
        assertThat(saved.getExterneNummer()).isEqualTo("EXT-123");
        assertThat(saved.getBelegReferenz()).isEqualTo("RE-2026-0001");
        assertThat(saved.getErfasstAm()).isNotNull();

        verify(durchschnittspreisService).aktualisiere(artikel, new BigDecimal("100"), new BigDecimal("1.50"));
    }

    @Test
    void rechnungStueck_schreibtHistorie_aberKeinDurchschnittsUpdate() {
        hook.registriere(artikel, lieferant, new BigDecimal("0.40"), new BigDecimal("500"),
                Verrechnungseinheit.STUECK, PreisQuelle.RECHNUNG, "SCHRAUBE-M8", "RE-2026-0002", null);

        ArgumentCaptor<ArtikelPreisHistorie> captor = ArgumentCaptor.forClass(ArtikelPreisHistorie.class);
        verify(historieRepository).save(captor.capture());
        assertThat(captor.getValue().getEinheit()).isEqualTo(Verrechnungseinheit.STUECK);
        assertThat(captor.getValue().getMenge()).isEqualByComparingTo("500");
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void rechnungMeter_schreibtHistorie_aberKeinDurchschnittsUpdate() {
        hook.registriere(artikel, lieferant, new BigDecimal("12.00"), new BigDecimal("6"),
                Verrechnungseinheit.LAUFENDE_METER, PreisQuelle.RECHNUNG, "ROHR-50", null, null);

        verify(historieRepository, times(1)).save(any(ArtikelPreisHistorie.class));
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void rechnungKilogrammOhneMenge_schreibtHistorie_aberKeinDurchschnittsUpdate() {
        hook.registriere(artikel, lieferant, new BigDecimal("1.50"), null,
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.RECHNUNG, "EXT-123", null, null);

        verify(historieRepository, times(1)).save(any(ArtikelPreisHistorie.class));
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void angebot_schreibtNurHistorie() {
        hook.registriere(artikel, lieferant, new BigDecimal("2.10"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.ANGEBOT, "EXT-ANG");

        verify(historieRepository, times(1)).save(any(ArtikelPreisHistorie.class));
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void katalog_schreibtNurHistorie() {
        hook.registriere(artikel, lieferant, new BigDecimal("3.00"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.KATALOG, "EXT-KAT");

        verify(historieRepository, times(1)).save(any(ArtikelPreisHistorie.class));
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void manuell_schreibtNurHistorie() {
        hook.registriere(artikel, lieferant, new BigDecimal("4.25"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.MANUELL, "EXT-MAN");

        verify(historieRepository, times(1)).save(any(ArtikelPreisHistorie.class));
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void vorschlag_schreibtNurHistorie() {
        hook.registriere(artikel, lieferant, new BigDecimal("5.80"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.VORSCHLAG, "EXT-VOR");

        verify(historieRepository, times(1)).save(any(ArtikelPreisHistorie.class));
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void nullEinheit_fallbackKilogramm() {
        hook.registriere(artikel, lieferant, new BigDecimal("1.50"), new BigDecimal("10"),
                null, PreisQuelle.RECHNUNG, null, null, null);

        ArgumentCaptor<ArtikelPreisHistorie> captor = ArgumentCaptor.forClass(ArtikelPreisHistorie.class);
        verify(historieRepository).save(captor.capture());
        assertThat(captor.getValue().getEinheit()).isEqualTo(Verrechnungseinheit.KILOGRAMM);
        verify(durchschnittspreisService).aktualisiere(artikel, new BigDecimal("10"), new BigDecimal("1.50"));
    }

    @Test
    void nullArtikel_wirdIgnoriert() {
        hook.registriere(null, lieferant, new BigDecimal("1.50"), new BigDecimal("10"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.RECHNUNG, null, null, null);

        verify(historieRepository, never()).save(any());
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void nullPreis_wirdIgnoriert() {
        hook.registriere(artikel, lieferant, null, new BigDecimal("10"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.RECHNUNG, null, null, null);

        verify(historieRepository, never()).save(any());
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void nullQuelle_wirdIgnoriert() {
        hook.registriere(artikel, lieferant, new BigDecimal("1.50"), new BigDecimal("10"),
                Verrechnungseinheit.KILOGRAMM, null, null, null, null);

        verify(historieRepository, never()).save(any());
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void negativePreis_wirdIgnoriert() {
        hook.registriere(artikel, lieferant, new BigDecimal("-0.01"), new BigDecimal("10"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.RECHNUNG, null, null, null);

        verify(historieRepository, never()).save(any());
        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    @Test
    void historieSaveWirftException_durchschnittLaeuftTrotzdem() {
        doThrow(new RuntimeException("DB voll")).when(historieRepository).save(any());

        hook.registriere(artikel, lieferant, new BigDecimal("1.50"), new BigDecimal("100"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.RECHNUNG, "EXT-123", null, null);

        verify(durchschnittspreisService).aktualisiere(artikel, new BigDecimal("100"), new BigDecimal("1.50"));
    }

    @Test
    void durchschnittWirftException_fliegtNichtNachAussen() {
        doThrow(new RuntimeException("Rechenfehler")).when(durchschnittspreisService)
                .aktualisiere(any(), any(), any());

        assertThatCode(() -> hook.registriere(artikel, lieferant,
                new BigDecimal("1.50"), new BigDecimal("100"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.RECHNUNG, "EXT-123", null, null))
                .doesNotThrowAnyException();

        verify(historieRepository).save(any(ArtikelPreisHistorie.class));
    }

    @Test
    void conveniencOverload_delegiertMitNullMengeUndBeleg() {
        hook.registriere(artikel, lieferant, new BigDecimal("2.00"),
                Verrechnungseinheit.KILOGRAMM, PreisQuelle.MANUELL, "EXT-XYZ");

        ArgumentCaptor<ArtikelPreisHistorie> captor = ArgumentCaptor.forClass(ArtikelPreisHistorie.class);
        verify(historieRepository).save(captor.capture());
        ArtikelPreisHistorie saved = captor.getValue();
        assertThat(saved.getMenge()).isNull();
        assertThat(saved.getBelegReferenz()).isNull();
        assertThat(saved.getBemerkung()).isNull();
        assertThat(saved.getExterneNummer()).isEqualTo("EXT-XYZ");
        assertThat(saved.getQuelle()).isEqualTo(PreisQuelle.MANUELL);
        assertThat(saved.getEinheit()).isEqualTo(Verrechnungseinheit.KILOGRAMM);

        verify(durchschnittspreisService, never()).aktualisiere(any(), any(), any());
    }

    private static Artikel artikel(Long id) {
        Artikel a = new Artikel();
        a.setId(id);
        return a;
    }

    private static Lieferanten lieferant(Long id) {
        Lieferanten l = new Lieferanten();
        l.setId(id);
        return l;
    }
}
