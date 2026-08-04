package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAufteilungsModus;
import org.example.kalkulationsprogramm.domain.BelegPosition;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegPositionRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BelegSplitServiceTest {

    @Mock private BelegRepository belegRepository;
    @Mock private BelegPositionRepository belegPositionRepository;
    @Mock private BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;

    private BelegSplitService service;

    @BeforeEach
    void setUp() {
        service = new BelegSplitService(belegRepository, belegPositionRepository, new MwstRechnerService(),
                belegKostenstellenAnteilRepository);
    }

    @Test
    void recomputeFirmaSummenSummiertNurAngehaktePositionen() {
        Beleg beleg = new Beleg();
        beleg.setId(1L);
        beleg.setAufteilungsModus(BelegAufteilungsModus.TEILWEISE);

        BelegPosition kaffee = pos(1L, "Kaffee", new BigDecimal("4.99"), new BigDecimal("19"), true);
        BelegPosition brot = pos(2L, "Brot", new BigDecimal("2.50"), new BigDecimal("7"), false);
        BelegPosition wasser = pos(3L, "Wasser", new BigDecimal("1.99"), new BigDecimal("19"), true);

        when(belegPositionRepository.findByBelegIdOrderBySortierungAsc(1L))
                .thenReturn(List.of(kaffee, brot, wasser));

        service.recomputeFirmaSummen(beleg);

        // 4.99 + 1.99 = 6.98 brutto bei 19%
        // Netto wird pro Position gerundet: 4.99/1.19 = 4.19  + 1.99/1.19 = 1.67  => 5.86
        assertThat(beleg.getBetragFirmaBrutto()).isEqualByComparingTo("6.98");
        assertThat(beleg.getBetragFirmaNetto()).isEqualByComparingTo("5.86");
        assertThat(beleg.getBetragFirmaMwst()).isEqualByComparingTo("1.12");
    }

    @Test
    void recomputeBeiVollstaendigLeertFirmaFelder() {
        Beleg beleg = new Beleg();
        beleg.setId(2L);
        beleg.setAufteilungsModus(BelegAufteilungsModus.VOLLSTAENDIG);
        beleg.setBetragFirmaNetto(new BigDecimal("10.00"));

        service.recomputeFirmaSummen(beleg);

        assertThat(beleg.getBetragFirmaNetto()).isNull();
        assertThat(beleg.getBetragFirmaBrutto()).isNull();
        assertThat(beleg.getBetragFirmaMwst()).isNull();
    }

    @Test
    void aktualisiereAuswahlWirftBeiVollstaendigMode() {
        Beleg beleg = new Beleg();
        beleg.setId(3L);
        beleg.setAufteilungsModus(BelegAufteilungsModus.VOLLSTAENDIG);
        when(belegRepository.findById(3L)).thenReturn(Optional.of(beleg));

        assertThatThrownBy(() -> service.aktualisiereAuswahl(3L, Set.of(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEILWEISE");
    }

    @Test
    void aktualisiereAuswahlSetztNurMarkiertePositionenAufFirma() {
        Beleg beleg = new Beleg();
        beleg.setId(4L);
        beleg.setAufteilungsModus(BelegAufteilungsModus.TEILWEISE);

        BelegPosition p1 = pos(10L, "A", new BigDecimal("10.00"), new BigDecimal("19"), false);
        BelegPosition p2 = pos(11L, "B", new BigDecimal("20.00"), new BigDecimal("19"), true);

        when(belegRepository.findById(4L)).thenReturn(Optional.of(beleg));
        when(belegPositionRepository.findByBelegIdOrderBySortierungAsc(4L)).thenReturn(List.of(p1, p2));
        when(belegRepository.save(any(Beleg.class))).thenAnswer(inv -> inv.getArgument(0));

        service.aktualisiereAuswahl(4L, Set.of(10L));

        assertThat(p1.isIstFuerFirma()).isTrue();
        assertThat(p2.isIstFuerFirma()).isFalse();
    }

    private BelegPosition pos(Long id, String beschreibung, BigDecimal brutto, BigDecimal satz, boolean firma) {
        BelegPosition p = new BelegPosition();
        p.setId(id);
        p.setBeschreibung(beschreibung);
        p.setBetragBrutto(brutto);
        p.setMwstSatz(satz);
        p.setIstFuerFirma(firma);
        return p;
    }
}
