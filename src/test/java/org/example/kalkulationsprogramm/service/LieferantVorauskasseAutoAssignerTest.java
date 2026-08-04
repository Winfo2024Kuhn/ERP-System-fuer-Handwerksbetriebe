package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LieferantVorauskasseAutoAssignerTest {

    @Mock
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    @InjectMocks
    private LieferantVorauskasseAutoAssigner autoAssigner;

    private Lieferanten lieferant;
    private LieferantGeschaeftsdokument geschaeftsdaten;
    private LieferantDokument dokument;

    @BeforeEach
    void setUp() {
        lieferant = new Lieferanten();
        lieferant.setId(7L);
        lieferant.setLieferantenname("Musterhandel GmbH");
        lieferant.setVorauskasse(true);

        geschaeftsdaten = new LieferantGeschaeftsdokument();
        geschaeftsdaten.setBereitsGezahlt(false);

        dokument = new LieferantDokument();
        dokument.setId(123L);
        dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
        dokument.setLieferant(lieferant);
        dokument.setGeschaeftsdaten(geschaeftsdaten);
    }

    @Test
    void markiertRechnungVonVorauskasseLieferantAlsBereitsGezahlt() {
        autoAssigner.applyIfApplicable(dokument);

        assertThat(geschaeftsdaten.getBereitsGezahlt()).isTrue();
        assertThat(geschaeftsdaten.getZahlungsart()).isEqualTo("VORAUSKASSE");
        verify(geschaeftsdokumentRepository).save(geschaeftsdaten);
    }

    @Test
    void behaeltErkannteZahlungsartBei() {
        geschaeftsdaten.setZahlungsart("SEPA_LASTSCHRIFT");

        autoAssigner.applyIfApplicable(dokument);

        assertThat(geschaeftsdaten.getBereitsGezahlt()).isTrue();
        assertThat(geschaeftsdaten.getZahlungsart()).isEqualTo("SEPA_LASTSCHRIFT");
    }

    @Test
    void ueberspringtWennLieferantKeineVorauskasseHat() {
        lieferant.setVorauskasse(false);

        autoAssigner.applyIfApplicable(dokument);

        assertThat(geschaeftsdaten.getBereitsGezahlt()).isFalse();
        verify(geschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    void ueberspringtWennDokumentKeineRechnungIst() {
        dokument.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);

        autoAssigner.applyIfApplicable(dokument);

        assertThat(geschaeftsdaten.getBereitsGezahlt()).isFalse();
        verify(geschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    void istIdempotentWennFlagBereitsGesetztIst() {
        geschaeftsdaten.setBereitsGezahlt(true);

        autoAssigner.applyIfApplicable(dokument);

        verify(geschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    void ignoriertDokumentOhneGeschaeftsdaten() {
        dokument.setGeschaeftsdaten(null);

        autoAssigner.applyIfApplicable(dokument);

        verify(geschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    void ignoriertDokumentOhneLieferant() {
        dokument.setLieferant(null);

        autoAssigner.applyIfApplicable(dokument);

        verify(geschaeftsdokumentRepository, never()).save(any());
    }

    @Test
    void ignoriertNullDokument() {
        autoAssigner.applyIfApplicable(null);

        verify(geschaeftsdokumentRepository, never()).save(any());
    }
}
