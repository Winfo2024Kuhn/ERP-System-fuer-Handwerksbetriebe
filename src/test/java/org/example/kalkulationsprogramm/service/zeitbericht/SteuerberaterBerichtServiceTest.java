package org.example.kalkulationsprogramm.service.zeitbericht;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MonatsSaldo;
import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.dto.ZeitkontoStatusDto;
import org.example.kalkulationsprogramm.dto.ZeitkontoVersionDto;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto.Stundenart;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.MonatsSaldoService;
import org.example.kalkulationsprogramm.service.Urlaubskonto;
import org.example.kalkulationsprogramm.service.UrlaubskontoService;
import org.example.kalkulationsprogramm.service.ZeitkontoWechselService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteuerberaterBerichtServiceTest {

    @Mock ZeitkontoWechselService zeitkontoWechselService;
    @Mock MonatsSaldoService monatsSaldoService;
    @Mock UrlaubskontoService urlaubskontoService;
    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock AbwesenheitRepository abwesenheitRepository;
    @Mock SteuerberaterStundenartEinstellung stundenartEinstellung;
    @InjectMocks SteuerberaterBerichtService service;

    @Test
    void stelltZeilenAusMonatssaldoStundenkontoUndUrlaubZusammen() {
        when(zeitkontoWechselService.alle()).thenReturn(List.of(
                konto(1L, "Max Mustermann", true, false, true),
                konto(2L, "Erika Musterfrau", true, false, true),
                konto(3L, "Chef Mustermann", true, true, true),
                konto(4L, "Ohne Zeitkonto", false, false, true),
                konto(5L, "Nicht eingerichtet", true, false, false)));
        Mitarbeiter max = mitarbeiter(1L);
        Mitarbeiter erika = mitarbeiter(2L);
        when(mitarbeiterRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(max, erika));
        when(monatsSaldoService.berechneOhneSpeichern(1L, 2026, 10)).thenReturn(saldo("176", "16", "8", "4", "0", true));
        when(monatsSaldoService.berechneOhneSpeichern(2L, 2026, 10)).thenReturn(saldo("88", null, "4", null, "2", false));
        when(monatsSaldoService.berechneGesamtsaldo(1L, LocalDate.of(2026, 10, 31))).thenReturn(new BigDecimal("12.46"));
        when(monatsSaldoService.berechneGesamtsaldo(2L, LocalDate.of(2026, 10, 31))).thenReturn(new BigDecimal("-3.5"));
        when(urlaubskontoService.berechne(max, 2026)).thenReturn(urlaub("7.5"));
        when(urlaubskontoService.berechne(erika, 2026)).thenReturn(urlaub("12"));
        // Offener Monat: Urlaub/Krankheit/Fortbildung kommen aus den Abwesenheiten, nicht aus dem Saldo
        when(abwesenheitRepository.sumStundenNachMonatUndTyp(List.of(1L, 2L), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31))).thenReturn(List.of(
                        summe(1L, AbwesenheitsTyp.URLAUB, "99"),
                        summe(2L, AbwesenheitsTyp.URLAUB, "4"),
                        summe(2L, AbwesenheitsTyp.FORTBILDUNG, "2"),
                        summe(2L, AbwesenheitsTyp.KRANKHEIT, "1.5"),
                        summe(2L, AbwesenheitsTyp.KRANKHEIT, "0.5")));

        SteuerberaterBerichtDto bericht = service.erstelle(2026, 10, null, Stundenart.SOLL);

        assertThat(bericht.stundenart()).isEqualTo(Stundenart.SOLL);
        assertThat(bericht.zeilen()).extracting(SteuerberaterBerichtDto.Zeile::name)
                .containsExactly("Max Mustermann", "Erika Musterfrau");
        var max1 = bericht.zeilen().get(0);
        assertThat(max1.arbeitsstunden()).isEqualByComparingTo("148.0");
        assertThat(max1.tagessoll()).isEqualByComparingTo("8.0");
        assertThat(max1.stundenkontoStand()).isEqualByComparingTo("12.5");
        assertThat(max1.resturlaubTage()).isEqualByComparingTo("7.5");
        assertThat(max1.festgeschrieben()).isTrue();
        var erika1 = bericht.zeilen().get(1);
        assertThat(erika1.urlaubStunden()).isEqualByComparingTo("4");
        assertThat(erika1.krankheitStunden()).isEqualByComparingTo("2");
        assertThat(erika1.fortbildungStunden()).isEqualByComparingTo("2");
        assertThat(erika1.arbeitsstunden()).isEqualByComparingTo("76.0");
        assertThat(erika1.stundenkontoStand()).isEqualByComparingTo("-3.5");
        assertThat(max1.urlaubStunden()).isEqualByComparingTo("16");
        assertThat(bericht.alleFestgeschrieben()).isFalse();
        verify(monatsSaldoService, never()).getOrBerechne(any(), anyInt(), anyInt());
    }

    @Test
    void auswahlBeschraenktAufGewaehlteMitarbeiter() {
        when(zeitkontoWechselService.alle()).thenReturn(List.of(
                konto(1L, "Max Mustermann", true, false, true),
                konto(2L, "Erika Musterfrau", true, false, true)));
        Mitarbeiter erika = mitarbeiter(2L);
        when(mitarbeiterRepository.findAllById(List.of(2L))).thenReturn(List.of(erika));
        when(monatsSaldoService.berechneOhneSpeichern(2L, 2026, 10)).thenReturn(saldo("88", "0", "0", "0", "0", true));
        when(monatsSaldoService.berechneGesamtsaldo(any(), any())).thenReturn(BigDecimal.ZERO);
        when(urlaubskontoService.berechne(erika, 2026)).thenReturn(urlaub("3"));
        when(abwesenheitRepository.sumStundenNachMonatUndTyp(any(), any(), any())).thenReturn(List.of());

        SteuerberaterBerichtDto bericht = service.erstelle(2026, 10, List.of(2L, 99L), Stundenart.SOLL);

        assertThat(bericht.zeilen()).singleElement().extracting(SteuerberaterBerichtDto.Zeile::mitarbeiterId).isEqualTo(2L);
        assertThat(bericht.alleFestgeschrieben()).isTrue();
    }

    @Test
    void fehlenderMitarbeiterDatensatzGibtResturlaubNull() {
        when(zeitkontoWechselService.alle()).thenReturn(List.of(konto(1L, "Max Mustermann", true, false, true)));
        when(mitarbeiterRepository.findAllById(anyList())).thenReturn(List.of());
        when(monatsSaldoService.berechneOhneSpeichern(1L, 2026, 10)).thenReturn(saldo("8", "0", "0", "0", "0", false));
        when(monatsSaldoService.berechneGesamtsaldo(any(), any())).thenReturn(null);
        when(abwesenheitRepository.sumStundenNachMonatUndTyp(any(), any(), any())).thenReturn(List.of());

        var zeile = service.erstelle(2026, 10, List.of(), Stundenart.SOLL).zeilen().get(0);

        assertThat(zeile.resturlaubTage()).isEqualByComparingTo("0");
        assertThat(zeile.stundenkontoStand()).isEqualByComparingTo("0");
        verifyNoInteractions(urlaubskontoService);
    }

    @Test
    void ungueltigerMonatWirdAbgewiesenBevorEtwasGeladenWird() {
        assertThatThrownBy(() -> service.erstelle(2026, 13, null, Stundenart.SOLL)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(zeitkontoWechselService, monatsSaldoService);
    }

    private static ZeitkontoStatusDto konto(Long id, String name, boolean fuehrt, boolean gf, boolean eingerichtet) {
        BigDecimal h = new BigDecimal("8");
        var arbeitszeit = new ZeitkontenmodellDto.Arbeitszeit(h, h, h, h, h, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        var aktuell = eingerichtet ? new ZeitkontoVersionDto(id, 0L, id, LocalDate.of(2020, 1, 1), null, null, arbeitszeit) : null;
        return new ZeitkontoStatusDto(id, 0L, name, fuehrt, gf, eingerichtet, null, aktuell, aktuell, List.of());
    }

    @Test
    void istStundenSindDieGebuchteArbeitszeit() {
        when(zeitkontoWechselService.alle()).thenReturn(List.of(konto(1L, "Max Mustermann", true, false, true)));
        when(mitarbeiterRepository.findAllById(anyList())).thenReturn(List.of());
        MonatsSaldo saldo = saldo("176", "16", "8", "0", "0", true);
        saldo.setIstStunden(new BigDecimal("161.25"));
        when(monatsSaldoService.berechneOhneSpeichern(1L, 2026, 10)).thenReturn(saldo);
        when(monatsSaldoService.berechneGesamtsaldo(any(), any())).thenReturn(BigDecimal.ZERO);
        when(abwesenheitRepository.sumStundenNachMonatUndTyp(any(), any(), any())).thenReturn(List.of());

        var bericht = service.erstelle(2026, 10, null, Stundenart.IST);

        assertThat(bericht.stundenart()).isEqualTo(Stundenart.IST);
        assertThat(bericht.zeilen().get(0).arbeitsstunden()).isEqualByComparingTo("161.3");
        assertThat(bericht.zeilen().get(0).urlaubStunden()).isEqualByComparingTo("16");
    }

    @Test
    void ohneStundenartGiltDieEinstellung() {
        when(zeitkontoWechselService.alle()).thenReturn(List.of());
        when(mitarbeiterRepository.findAllById(List.of())).thenReturn(List.of());
        when(stundenartEinstellung.oderStandard()).thenReturn(Stundenart.IST);

        assertThat(service.erstelle(2026, 10, null, null).stundenart()).isEqualTo(Stundenart.IST);
    }

    @Test
    void ohneMitarbeiterWirdNichtsAbgefragt() {
        when(zeitkontoWechselService.alle()).thenReturn(List.of());
        when(mitarbeiterRepository.findAllById(List.of())).thenReturn(List.of());

        var bericht = service.erstelle(2026, 10, null, Stundenart.IST);

        assertThat(bericht.zeilen()).isEmpty();
        verifyNoInteractions(abwesenheitRepository);
    }

    private static AbwesenheitRepository.StundenNachMonatUndTyp summe(Long id, AbwesenheitsTyp typ, String stunden) {
        return new AbwesenheitRepository.StundenNachMonatUndTyp() {
            public Long getMitarbeiterId() { return id; }
            public Integer getJahr() { return 2026; }
            public Integer getMonat() { return 10; }
            public AbwesenheitsTyp getTyp() { return typ; }
            public BigDecimal getStunden() { return new BigDecimal(stunden); }
        };
    }

    private static Mitarbeiter mitarbeiter(Long id) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        return m;
    }

    private static MonatsSaldo saldo(String soll, String urlaub, String feiertag, String krank, String fortbildung,
            boolean fest) {
        MonatsSaldo s = new MonatsSaldo();
        s.setSollStunden(new BigDecimal(soll));
        s.setUrlaubStunden(urlaub == null ? null : new BigDecimal(urlaub));
        s.setFeiertagsStunden(new BigDecimal(feiertag));
        s.setKrankheitStunden(krank == null ? null : new BigDecimal(krank));
        s.setFortbildungStunden(new BigDecimal(fortbildung));
        s.setFestgeschrieben(fest);
        return s;
    }

    private static Urlaubskonto urlaub(String verbleibend) {
        BigDecimal z = BigDecimal.ZERO;
        return new Urlaubskonto(2026, new BigDecimal("30"), z, false, null, z, z, z, z, z, new BigDecimal(verbleibend));
    }
}
