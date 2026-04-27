package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LieferantStandardKostenstelleAutoAssignerTest {

    @Mock
    private LieferantDokumentProjektAnteilRepository anteilRepository;

    @InjectMocks
    private LieferantStandardKostenstelleAutoAssigner autoAssigner;

    private Lieferanten lieferant;
    private Kostenstelle standard;
    private LieferantDokument dokument;

    @BeforeEach
    void setUp() {
        standard = new Kostenstelle();
        standard.setId(42L);
        standard.setBezeichnung("IT");

        lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Apple");
        lieferant.setStandardKostenstelle(standard);

        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setBetragBrutto(new BigDecimal("199.99"));

        dokument = new LieferantDokument();
        dokument.setId(123L);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setLieferant(lieferant);
        dokument.setGeschaeftsdaten(gd);
    }

    @Test
    void erstelltAnteilFuerRechnungMitStandardKostenstelle() {
        when(anteilRepository.findByDokumentId(123L)).thenReturn(List.of());

        autoAssigner.applyIfApplicable(dokument);

        ArgumentCaptor<LieferantDokumentProjektAnteil> captor = ArgumentCaptor.forClass(LieferantDokumentProjektAnteil.class);
        verify(anteilRepository).save(captor.capture());

        LieferantDokumentProjektAnteil saved = captor.getValue();
        assertThat(saved.getDokument()).isSameAs(dokument);
        assertThat(saved.getKostenstelle()).isSameAs(standard);
        assertThat(saved.getProjekt()).isNull();
        assertThat(saved.getProzent()).isEqualTo(100);
        assertThat(saved.getBerechneterBetrag()).isEqualByComparingTo(new BigDecimal("199.99"));
    }

    @Test
    void ueberspringtWennLieferantKeineStandardKostenstelleHat() {
        lieferant.setStandardKostenstelle(null);

        autoAssigner.applyIfApplicable(dokument);

        verify(anteilRepository, never()).save(any());
    }

    @Test
    void ueberspringtWennDokumentKeineRechnungIst() {
        dokument.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);

        autoAssigner.applyIfApplicable(dokument);

        verify(anteilRepository, never()).save(any());
    }

    @Test
    void istIdempotentWennAnteilBereitsExistiert() {
        LieferantDokumentProjektAnteil bestehend = new LieferantDokumentProjektAnteil();
        when(anteilRepository.findByDokumentId(123L)).thenReturn(List.of(bestehend));

        autoAssigner.applyIfApplicable(dokument);

        verify(anteilRepository, never()).save(any());
    }

    @Test
    void erstelltAnteilOhneBetragWennGeschaeftsdatenFehlen() {
        dokument.setGeschaeftsdaten(null);
        when(anteilRepository.findByDokumentId(123L)).thenReturn(List.of());

        autoAssigner.applyIfApplicable(dokument);

        ArgumentCaptor<LieferantDokumentProjektAnteil> captor = ArgumentCaptor.forClass(LieferantDokumentProjektAnteil.class);
        verify(anteilRepository).save(captor.capture());
        assertThat(captor.getValue().getBerechneterBetrag()).isNull();
        assertThat(captor.getValue().getProzent()).isEqualTo(100);
    }

    @Test
    void ignoriertNullDokument() {
        autoAssigner.applyIfApplicable(null);

        verify(anteilRepository, never()).save(any());
    }
}
