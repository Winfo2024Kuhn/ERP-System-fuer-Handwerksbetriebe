package org.example.kalkulationsprogramm.service.zeitbericht;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.FeiertagService;
import org.example.kalkulationsprogramm.service.MonatsSaldoService;
import org.example.kalkulationsprogramm.service.TagesSollService;
import org.example.kalkulationsprogramm.service.Urlaubskonto;
import org.example.kalkulationsprogramm.service.UrlaubskontoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MitarbeiterBerichtServiceTest {

    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock ZeitbuchungRepository zeitbuchungRepository;
    @Mock AbwesenheitRepository abwesenheitRepository;
    @Mock FeiertagService feiertagService;
    @Mock TagesSollService tagesSollService;
    @Mock MonatsSaldoService monatsSaldoService;
    @Mock UrlaubskontoService urlaubskontoService;
    @InjectMocks MitarbeiterBerichtService service;

    private static final LocalDate MO = LocalDate.of(2026, 10, 5);
    private static final LocalDate DI = MO.plusDays(1);
    private static final LocalDate MI = MO.plusDays(2);

    @Test
    void rechnetTagFuerTagUndSummiert() {
        Mitarbeiter max = new Mitarbeiter();
        max.setId(1L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        when(mitarbeiterRepository.findById(1L)).thenReturn(Optional.of(max));
        when(feiertagService.getFeiertageZwischen(MO, MI)).thenReturn(List.of(feiertag(MI, "Musterfeiertag")));
        when(tagesSollService.periodenSollJeTag(1L, MO, MI)).thenReturn(Map.of(MO, d("8"), DI, d("8"), MI, d("8")));
        when(tagesSollService.feiertagsGutschriftJeTag(1L, MO, MI)).thenReturn(Map.of(MO, d("0"), DI, d("0"), MI, d("8")));
        when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(any(), any(), any())).thenReturn(List.of(
                buchung(MO, 7, 0, 12, 0, BuchungsTyp.ARBEIT),
                buchung(MO, 12, 0, 12, 30, BuchungsTyp.PAUSE),
                buchung(MO, 12, 30, 17, 15, BuchungsTyp.ARBEIT),
                buchung(MO, 18, 0, -1, -1, BuchungsTyp.ARBEIT)));
        when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(1L, MO, MI))
                .thenReturn(List.of(abwesenheit(DI, AbwesenheitsTyp.URLAUB, "8")));
        when(monatsSaldoService.berechneGesamtsaldo(1L, MI)).thenReturn(d("4.25"));
        Urlaubskonto konto = new Urlaubskonto(2026, d("30"), d("2"), false, null, d("0"), d("0"), d("10"), d("3"),
                d("0"), d("19"));
        when(urlaubskontoService.berechne(max, 2026)).thenReturn(konto);

        MitarbeiterBerichtDaten bericht = service.erstelle(List.of(1L), MO, MI).get(0);

        assertThat(bericht.name()).isEqualTo("Max Mustermann");
        assertThat(bericht.tage()).hasSize(3);
        var montag = bericht.tage().get(0);
        assertThat(montag.arbeit()).isEqualByComparingTo("9.75");
        assertThat(montag.differenz()).isEqualByComparingTo("1.75");
        var dienstag = bericht.tage().get(1);
        assertThat(dienstag.abwesenheit()).isEqualTo("Urlaub");
        assertThat(dienstag.differenz()).isEqualByComparingTo("0");
        var mittwoch = bericht.tage().get(2);
        assertThat(mittwoch.feiertag()).isEqualTo("Musterfeiertag");
        assertThat(mittwoch.ist()).isEqualByComparingTo("8");
        assertThat(bericht.summen().soll()).isEqualByComparingTo("24");
        assertThat(bericht.summen().urlaub()).isEqualByComparingTo("8");
        assertThat(bericht.summen().krankheit()).isEqualByComparingTo("0");
        assertThat(bericht.summen().feiertage()).isEqualByComparingTo("8");
        assertThat(bericht.summen().differenz()).isEqualByComparingTo("1.75");
        assertThat(bericht.stundenkontoStand()).isEqualByComparingTo("4.25");
        assertThat(bericht.urlaub().verbleibend()).isEqualByComparingTo("19");
    }

    @Test
    void unbekannterMitarbeiterIst404() {
        when(feiertagService.getFeiertageZwischen(MO, MI)).thenReturn(List.of());
        when(mitarbeiterRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.erstelle(List.of(Long.MAX_VALUE), MO, MI))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void ohneAuswahlOderMitFalschemZeitraumWirdNichtsGeladen() {
        assertThatThrownBy(() -> service.erstelle(List.of(), MO, MI)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.erstelle(List.of(1L), MI, MO)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mitarbeiterRepository, zeitbuchungRepository, feiertagService);
    }

    @Test
    void bezeichnungenInHandwerkerSprache() {
        assertThat(MitarbeiterBerichtService.bezeichnung(AbwesenheitsTyp.KRANKHEIT)).isEqualTo("Krank");
        assertThat(MitarbeiterBerichtService.bezeichnung(AbwesenheitsTyp.FORTBILDUNG)).isEqualTo("Fortbildung");
        assertThat(MitarbeiterBerichtService.bezeichnung(AbwesenheitsTyp.ZEITAUSGLEICH)).isEqualTo("Zeitausgleich");
        assertThat(MitarbeiterBerichtService.bezeichnung(null)).isEqualTo("Abwesend");
    }

    private static Zeitbuchung buchung(LocalDate tag, int vonH, int vonM, int bisH, int bisM, BuchungsTyp typ) {
        Zeitbuchung b = new Zeitbuchung();
        b.setStartZeit(LocalDateTime.of(tag, java.time.LocalTime.of(vonH, vonM)));
        b.setEndeZeit(bisH < 0 ? null : LocalDateTime.of(tag, java.time.LocalTime.of(bisH, bisM)));
        b.setTyp(typ);
        return b;
    }

    private static Abwesenheit abwesenheit(LocalDate tag, AbwesenheitsTyp typ, String stunden) {
        Abwesenheit a = new Abwesenheit();
        a.setDatum(tag);
        a.setTyp(typ);
        a.setStunden(d(stunden));
        return a;
    }

    private static Feiertag feiertag(LocalDate tag, String name) {
        Feiertag f = new Feiertag();
        f.setDatum(tag);
        f.setBezeichnung(name);
        return f;
    }

    private static BigDecimal d(String wert) {
        return new BigDecimal(wert);
    }
}
