package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.KundenZaehler;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.KundenZaehlerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KundennummerServiceTest {

    private KundeRepository kundeRepository;
    private KundenZaehlerRepository kundenZaehlerRepository;
    private KundennummerService service;

    @BeforeEach
    void setUp() {
        kundeRepository = mock(KundeRepository.class);
        kundenZaehlerRepository = mock(KundenZaehlerRepository.class);
        service = new KundennummerService(kundeRepository, kundenZaehlerRepository);
    }

    @Test
    void reserviereLiefertAktuelleZaehlerNummerUndInkrementiert() {
        KundenZaehler zaehler = new KundenZaehler();
        zaehler.setId(1);
        zaehler.setNaechsteNummer(1042L);
        given(kundenZaehlerRepository.lockAndGet()).willReturn(zaehler);

        String nummer = service.reserviereNaechsteKundennummer();

        assertThat(nummer).isEqualTo("1042");
        assertThat(zaehler.getNaechsteNummer()).isEqualTo(1043L);
        verify(kundeRepository, never()).findMaxKundennummer();
    }

    @Test
    void reserviereFaelltAufMaxPlusEinsZurueckWennZaehlerFehlt() {
        given(kundenZaehlerRepository.lockAndGet()).willReturn(null);
        given(kundeRepository.findMaxKundennummer()).willReturn(Optional.of("1099"));

        String nummer = service.reserviereNaechsteKundennummer();

        assertThat(nummer).isEqualTo("1100");
    }

    @Test
    void generiereStartetBei1000WennNochKeinKunde() {
        given(kundeRepository.findMaxKundennummer()).willReturn(Optional.empty());

        assertThat(service.generiereNaechsteKundennummer()).isEqualTo("1000");
    }

    @Test
    void generiereSpringtAufFallbackBeiNichtNumerischerNummer() {
        given(kundeRepository.findMaxKundennummer()).willReturn(Optional.of("ABC"));

        assertThat(service.generiereNaechsteKundennummer()).isEqualTo("1000");
    }
}
