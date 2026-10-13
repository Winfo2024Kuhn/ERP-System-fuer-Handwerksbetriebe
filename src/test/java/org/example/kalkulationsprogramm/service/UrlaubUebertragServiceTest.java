package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.UrlaubUebertrag;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.UrlaubUebertragRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** Resturlaub von Hand festlegen. Dummy-Daten: Max Mustermann. */
@ExtendWith(MockitoExtension.class)
class UrlaubUebertragServiceTest {

    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock UrlaubUebertragRepository uebertragRepository;
    @Mock UrlaubskontoService urlaubskontoService;

    private UrlaubUebertragService service;
    private Mitarbeiter max;

    @BeforeEach
    void setUp() {
        service = new UrlaubUebertragService(mitarbeiterRepository, uebertragRepository, urlaubskontoService,
                Clock.systemDefaultZone());
        max = new Mitarbeiter();
        max.setId(1L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        lenient().when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(max));
    }

    private static UrlaubskontoService.UebertragStand stand(String tage, String berechnet, boolean manuell,
            LocalDate verfall) {
        return new UrlaubskontoService.UebertragStand(2027, new BigDecimal(tage), new BigDecimal(berechnet), manuell,
                verfall);
    }

    @Test
    void ladenZeigtBerechnetenWert() {
        when(urlaubskontoService.uebertragStand(max, 2027)).thenReturn(stand("4", "4", false, LocalDate.of(2027, 3, 31)));

        UrlaubUebertragService.Stand stand = service.laden(1L, 2027);

        assertThat(stand.tage()).isEqualByComparingTo("4");
        assertThat(stand.berechnet()).isEqualByComparingTo("4");
        assertThat(stand.manuell()).isFalse();
        assertThat(stand.verfallsdatum()).isEqualTo("2027-03-31");
    }

    @Test
    void speichernLegtHandwertFestUndZeigtDenBerechnetenDaneben() {
        when(uebertragRepository.findByMitarbeiterIdAndJahr(1L, 2027)).thenReturn(Optional.empty());
        when(urlaubskontoService.uebertragStand(max, 2027)).thenReturn(stand("2.5", "4", true, null));

        UrlaubUebertragService.Stand stand = service.speichern(1L, 2027, new BigDecimal("2.5"), 3L);

        ArgumentCaptor<UrlaubUebertrag> captor = ArgumentCaptor.forClass(UrlaubUebertrag.class);
        verify(uebertragRepository).save(captor.capture());
        assertThat(captor.getValue().getTage()).isEqualByComparingTo("2.5");
        assertThat(captor.getValue().getJahr()).isEqualTo(2027);
        assertThat(captor.getValue().getMitarbeiterId()).isEqualTo(1L);
        assertThat(captor.getValue().getGeaendertVonId()).isEqualTo(3L);
        assertThat(stand.manuell()).isTrue();
        assertThat(stand.berechnet()).isEqualByComparingTo("4");
        assertThat(stand.verfallsdatum()).isNull();
    }

    @Test
    void leerSpeichernLoeschtDenHandwert() {
        UrlaubUebertrag eintrag = new UrlaubUebertrag();
        when(uebertragRepository.findByMitarbeiterIdAndJahr(1L, 2027)).thenReturn(Optional.of(eintrag));
        when(urlaubskontoService.uebertragStand(max, 2027)).thenReturn(stand("4", "4", false, LocalDate.of(2027, 3, 31)));

        service.speichern(1L, 2027, null, 3L);

        verify(uebertragRepository).delete(eintrag);
        verify(uebertragRepository, never()).save(any());
    }

    @Test
    void lehntUngueltigeEingabenAb() {
        assertThatThrownBy(() -> service.speichern(1L, 2027, new BigDecimal("-1"), 3L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("zwischen 0 und 365");
        assertThatThrownBy(() -> service.speichern(1L, 2027, new BigDecimal("366"), 3L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.speichern(1L, 2027, new BigDecimal("1.3"), 3L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("halbe Tage");
        assertThatThrownBy(() -> service.laden(1L, 1999)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.laden(1L, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.laden(0L, 2027)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.laden(-5L, 2027)).isInstanceOf(ResponseStatusException.class);
        when(mitarbeiterRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.laden(Long.MAX_VALUE, 2027))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nicht gefunden");
        verify(uebertragRepository, never()).save(any());
    }

    @Test
    void geschaeftsfuehrungBrauchtKeinenUebertrag() {
        max.setIstGeschaeftsfuehrer(true);
        assertThatThrownBy(() -> service.speichern(1L, 2027, BigDecimal.ONE, 3L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Geschäftsführung");
    }
}
