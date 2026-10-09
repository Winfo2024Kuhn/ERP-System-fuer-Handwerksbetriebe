package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.PositionsSuchtreffer;
import org.example.kalkulationsprogramm.dto.PositionsTrefferDto;
import org.example.kalkulationsprogramm.repository.LieferantDokumentPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LieferantDokumentSucheServiceTest {

    @Mock
    private LieferantDokumentPositionRepository positionRepository;
    @Mock
    private LieferantDokumentService dokumentService;
    private LieferantDokumentSucheService service;

    @BeforeEach
    void setUp() {
        service = new LieferantDokumentSucheService(positionRepository, dokumentService, new ObjectMapper());
    }

    private static PositionsSuchtreffer treffer(long dokumentId, int nr, String bezeichnung) {
        return new PositionsSuchtreffer(dokumentId, nr, bezeichnung, "S235JR+AR", "123456", "50x5",
                new BigDecimal("12.000"), "Stück");
    }

    @Test
    void gruppiertJeDokumentUndZaehltWeitere() {
        when(positionRepository.suche(anyCollection(), eq(7L), eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 12, 31)), eq("%flachstahl%"), eq("%50x5%"), isNull(), isNull(), isNull(),
                any(Pageable.class)))
                .thenReturn(List.of(treffer(20, 1, "Flachstahl 50x5"), treffer(20, 3, "Flachstahl 50x5 lang"),
                        treffer(10, 2, "Flachstahl 50x5")));

        Map<Long, PositionsTrefferDto> ergebnis = service.suchePositionen("Flachstahl 50 x 5", 7L,
                EnumSet.allOf(LieferantDokumentTyp.class), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(ergebnis.keySet()).containsExactly(20L, 10L);
        assertThat(ergebnis.get(20L).weitereTreffer()).isEqualTo(1);
        assertThat(ergebnis.get(20L).trefferText())
                .isEqualTo("Flachstahl 50x5 · S235JR+AR · Charge 123456 · 12 Stück");
        assertThat(ergebnis.get(10L).weitereTreffer()).isZero();
    }

    @Test
    void zuKurzeEingabeOderKeineTypenSuchenNicht() {
        var alle = EnumSet.allOf(LieferantDokumentTyp.class);
        assertThat(service.suchePositionen("x", null, alle, null, null)).isEmpty();
        assertThat(service.suchePositionen(null, null, alle, null, null)).isEmpty();
        assertThat(service.suchePositionen("Flachstahl", null, List.of(), null, null)).isEmpty();
        assertThat(service.suchePositionen("Flachstahl", null, null, null, null)).isEmpty();
        verifyNoInteractions(positionRepository);
    }

    @Test
    void trefferTextOhneDoppelteAngaben() {
        assertThat(LieferantDokumentSucheService.trefferText(new PositionsSuchtreffer(1L, 1,
                "Flachstahl 50x5 S235JR", "S235JR", null, "50x5", new BigDecimal("2.5"), null)))
                .isEqualTo("Flachstahl 50x5 S235JR · 2,5");
        assertThat(LieferantDokumentSucheService.trefferText(new PositionsSuchtreffer(1L, 1,
                " ", null, " ", null, null, null))).isEmpty();
        // "50 × 5" steht normalisiert schon in der Bezeichnung
        assertThat(LieferantDokumentSucheService.trefferText(new PositionsSuchtreffer(1L, 1,
                "Flachstahl 50x5", null, null, "50 × 5", null, null))).isEqualTo("Flachstahl 50x5");
    }

    private static LieferantGeschaeftsdokument eingang(String nummer) {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Musterstahl GmbH");
        LieferantDokument d = new LieferantDokument();
        d.setTyp(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        d.setLieferant(l);
        LieferantGeschaeftsdokument g = new LieferantGeschaeftsdokument();
        g.setDokument(d);
        g.setDokumentNummer(nummer);
        return g;
    }

    @Test
    void eingangssucheUeberKopfdaten() {
        LieferantGeschaeftsdokument g = eingang("Z-4711");
        g.setReferenzNummer("LS-880011");
        g.setBestellnummer("B-2026-44");
        g.setBetragBrutto(new BigDecimal("119.00"));

        assertThat(service.passtZurEingangssuche(g, "4711")).isTrue();
        assertThat(service.passtZurEingangssuche(g, "880011")).isTrue();
        assertThat(service.passtZurEingangssuche(g, "b-2026")).isTrue();
        assertThat(service.passtZurEingangssuche(g, "119.00")).isTrue();
        assertThat(service.passtZurEingangssuche(g, "werkstoff")).isTrue();
        assertThat(service.passtZurEingangssuche(g, "musterstahl")).isTrue();
        assertThat(service.passtZurEingangssuche(g, "nichts")).isFalse();
        assertThat(service.passtZurEingangssuche(new LieferantGeschaeftsdokument(), "x")).isFalse();
    }

    @Test
    void eingangssucheUeberKommission() {
        LieferantGeschaeftsdokument kommission = eingang("RE-3");
        kommission.setAiRawJson("{\"kommission\":\"BV Mustermann\",\"lieferantName\":\"x\"}");
        LieferantGeschaeftsdokument nurImJson = eingang("RE-4");
        nurImJson.setAiRawJson("{\"lieferantName\":\"Mustermann\"}");
        LieferantGeschaeftsdokument kaputt = eingang("RE-5");
        kaputt.setAiRawJson("{mustermann");
        LieferantGeschaeftsdokument keinText = eingang("RE-6");
        keinText.setAiRawJson("{\"kommission\":4711,\"x\":\"mustermann\"}");

        assertThat(service.passtZurEingangssuche(kommission, "mustermann")).isTrue();
        assertThat(service.passtZurEingangssuche(nurImJson, "mustermann")).isFalse();
        assertThat(service.passtZurEingangssuche(kaputt, "mustermann")).isFalse();
        assertThat(service.passtZurEingangssuche(keinText, "mustermann")).isFalse();
    }

    @Test
    void mitMitarbeiterNurSichtbareTypen() {
        when(dokumentService.getBerechtigungen(5L)).thenReturn(LieferantDokumentDto.BerechtigungenResponse.builder()
                .sichtbareTypen(List.of(LieferantDokumentTyp.WERKSTOFFZEUGNIS)).scanbarTypen(List.of()).build());
        when(positionRepository.suche(anyCollection(), eq(3L), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(treffer(1, 1, "Flachstahl")));

        List<PositionsTrefferDto> ergebnis = service.sucheBeiLieferant(3L, "Flachstahl", 5L);

        assertThat(ergebnis).extracting(PositionsTrefferDto::dokumentId).containsExactly(1L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<LieferantDokumentTyp>> typen = ArgumentCaptor.forClass(Collection.class);
        verify(positionRepository).suche(typen.capture(), eq(3L), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(typen.getValue()).containsExactly(LieferantDokumentTyp.WERKSTOFFZEUGNIS);
    }

    @Test
    void ohneMitarbeiterAlleTypen() {
        when(positionRepository.suche(anyCollection(), eq(3L), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.sucheBeiLieferant(3L, "Flachstahl", null)).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<LieferantDokumentTyp>> typen = ArgumentCaptor.forClass(Collection.class);
        verify(positionRepository).suche(typen.capture(), eq(3L), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(typen.getValue()).containsExactlyInAnyOrder(LieferantDokumentTyp.values());
        verifyNoInteractions(dokumentService);
    }
}
