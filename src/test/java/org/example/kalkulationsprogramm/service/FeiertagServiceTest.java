package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Bundesland;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-Tests für FeiertagService: Bundesland aus den Firmendaten, abgeschaltete und
 * eigene Feiertage, Neu-Laden ohne Verlust der Anpassungen (Issue #222).
 * Die Feiertags-API ist ausgeschaltet, es greift die eingebaute Liste.
 */
@ExtendWith(MockitoExtension.class)
class FeiertagServiceTest {

    @Mock
    private FeiertagRepository feiertagRepository;
    @Mock
    private FirmeninformationRepository firmeninformationRepository;
    @Mock
    private MonatsSaldoRepository monatsSaldoRepository;

    @InjectMocks
    private FeiertagService feiertagService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(feiertagService, "self", feiertagService);
        ReflectionTestUtils.setField(feiertagService, "apiAktiv", false);
    }

    private void firmaIn(String bundesland) {
        Firmeninformation firma = new Firmeninformation();
        firma.setBundesland(bundesland);
        lenient().when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firma));
    }

    private static Feiertag feiertag(long id, LocalDate datum, String name, String land) {
        Feiertag f = new Feiertag(datum, name, land);
        f.setId(id);
        return f;
    }

    // ==================== Bundesland ====================

    @Test
    void aktuellesBundesland_kommtAusDenFirmendaten() {
        firmaIn("HE");
        assertEquals("HE", feiertagService.aktuellesBundesland());
        // zweiter Aufruf aus dem Zwischenspeicher
        assertEquals("HE", feiertagService.aktuellesBundesland());
        verify(firmeninformationRepository, times(1)).findFirmeninformation();
    }

    @Test
    void aktuellesBundesland_ohneFirmendatenOderUngueltig_istBayern() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        assertEquals("BY", feiertagService.aktuellesBundesland());

        ReflectionTestUtils.setField(feiertagService, "bundeslandCache", null);
        firmaIn("XX");
        assertEquals("BY", feiertagService.aktuellesBundesland());
    }

    @Test
    void bundeslandGeaendert_setztNeuesLandUndRechnetOffeneMonateNeu() {
        firmaIn("BY");
        feiertagService.bundeslandGeaendert("nw");

        assertEquals("NW", feiertagService.aktuellesBundesland());
        verify(monatsSaldoRepository).invalidiereAlleOffenen();
    }

    // ==================== Lesen ====================

    @Test
    void istFeiertag_fragtNurAktiveImEingestelltenLand() {
        firmaIn("BW");
        LocalDate dreiKoenige = LocalDate.of(2026, 1, 6);
        when(feiertagRepository.findByJahrAndBundesland(2026, "BW"))
                .thenReturn(List.of(feiertag(1, dreiKoenige, "Heilige Drei Könige", "BW")));
        when(feiertagRepository.existsByDatumAndBundeslandAndAktivTrue(dreiKoenige, "BW")).thenReturn(true);

        assertTrue(feiertagService.istFeiertag(dreiKoenige));
        verify(feiertagRepository, never()).saveAll(anyList());
    }

    @Test
    void leeresJahr_wirdEinmalAusDerEingebautenListeGeladen() {
        firmaIn("SN");
        when(feiertagRepository.findByJahrAndBundesland(2026, "SN")).thenReturn(List.of());
        when(feiertagRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        feiertagService.getFeiertageForJahr(2026);
        feiertagService.getFeiertageForJahr(2026);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Feiertag>> gespeichert = ArgumentCaptor.forClass(List.class);
        verify(feiertagRepository, times(1)).saveAll(gespeichert.capture());
        List<Feiertag> liste = gespeichert.getValue();
        assertTrue(liste.stream().allMatch(f -> "SN".equals(f.getBundesland())));
        assertTrue(liste.stream().anyMatch(f -> f.getBezeichnung().equals("Buß- und Bettag")));
        assertTrue(liste.stream().noneMatch(f -> f.getBezeichnung().equals("Fronleichnam")));
    }

    @Test
    void getFeiertageForJahr_laesstAbgeschalteteWeg_VerwaltungZeigtSie() {
        firmaIn("BY");
        Feiertag mariae = feiertag(1, LocalDate.of(2026, 8, 15), "Mariä Himmelfahrt", "BY");
        mariae.setAktiv(false);
        Feiertag einheit = feiertag(2, LocalDate.of(2026, 10, 3), "Tag der Deutschen Einheit", "BY");
        when(feiertagRepository.findByJahrAndBundesland(2026, "BY")).thenReturn(List.of(mariae, einheit));

        assertEquals(List.of(einheit), feiertagService.getFeiertageForJahr(2026));
        assertEquals(2, feiertagService.getFeiertageZurVerwaltung(2026).size());
    }

    @Test
    void getFeiertageZwischen_nurAktiveImLand() {
        firmaIn("BY");
        LocalDate von = LocalDate.of(2026, 12, 1);
        LocalDate bis = LocalDate.of(2026, 12, 31);
        Feiertag erster = feiertag(1, LocalDate.of(2026, 12, 25), "1. Weihnachtstag", "BY");
        Feiertag heiligabend = feiertag(2, LocalDate.of(2026, 12, 24), "Heiligabend", "BY");
        heiligabend.setAktiv(false);
        when(feiertagRepository.findByJahrAndBundesland(2026, "BY")).thenReturn(List.of(erster));
        when(feiertagRepository.findByDatumBetweenAndBundesland(von, bis, "BY")).thenReturn(List.of(heiligabend, erster));

        assertEquals(List.of(erster), feiertagService.getFeiertageZwischen(von, bis));
    }

    @Test
    void halberFeiertag_undAbgeschalteterZaehltNicht() {
        firmaIn("BY");
        LocalDate silvester = LocalDate.of(2026, 12, 31);
        Feiertag halb = feiertag(1, silvester, "Silvester", "BY");
        halb.setHalbTag(true);
        when(feiertagRepository.findByJahrAndBundesland(2026, "BY")).thenReturn(List.of(halb));
        when(feiertagRepository.findByDatumAndBundesland(silvester, "BY")).thenReturn(Optional.of(halb));

        assertTrue(feiertagService.istHalberFeiertag(silvester));

        halb.setAktiv(false);
        assertFalse(feiertagService.istHalberFeiertag(silvester));
        assertTrue(feiertagService.getFeiertagInfo(silvester).isEmpty());
    }

    @Test
    void vorschau_speichertNichtsUndMerktSichDieListe() {
        List<Feiertag> erste = feiertagService.vorschau(2026, Bundesland.TH);
        List<Feiertag> zweite = feiertagService.vorschau(2026, Bundesland.TH);

        assertSame(erste, zweite);
        assertTrue(erste.stream().anyMatch(f -> f.getBezeichnung().equals("Weltkindertag")));
        verifyNoInteractions(feiertagRepository);
    }

    // ==================== Pflegen ====================

    @Test
    void aendern_setztHalbTagUndAktivUndMarkiertAngepasst() {
        Feiertag f = feiertag(5, LocalDate.of(2026, 8, 15), "Mariä Himmelfahrt", "BY");
        when(feiertagRepository.findById(5L)).thenReturn(Optional.of(f));
        when(feiertagRepository.save(f)).thenReturn(f);

        Feiertag ergebnis = feiertagService.aendern(5L, true, false);

        assertTrue(ergebnis.isHalbTag());
        assertFalse(ergebnis.isAktiv());
        assertTrue(ergebnis.isAngepasst());
        verify(monatsSaldoRepository).invalidiereMonatFuerAlle(2026, 8);
    }

    @Test
    void aendern_unbekannteId_gibt404() {
        when(feiertagRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> feiertagService.aendern(99L, false, true));
        assertEquals(404, e.getStatusCode().value());
        verifyNoInteractions(monatsSaldoRepository);
    }

    @Test
    void anlegen_eigenerFeiertagMitBereinigtemNamen() {
        firmaIn("BY");
        LocalDate friedensfest = LocalDate.of(2026, 8, 8);
        when(feiertagRepository.findByJahrAndBundesland(2026, "BY"))
                .thenReturn(List.of(feiertag(1, LocalDate.of(2026, 1, 1), "Neujahr", "BY")));
        when(feiertagRepository.findByDatumAndBundesland(friedensfest, "BY")).thenReturn(Optional.empty());
        when(feiertagRepository.save(any(Feiertag.class))).thenAnswer(inv -> inv.getArgument(0));

        Feiertag f = feiertagService.anlegen(friedensfest, "  Augsburger Friedensfest ", false);

        assertEquals("Augsburger Friedensfest", f.getBezeichnung());
        assertEquals("BY", f.getBundesland());
        assertTrue(f.isEigener());
        assertTrue(f.isAngepasst());
        assertTrue(f.isAktiv());
        verify(monatsSaldoRepository).invalidiereMonatFuerAlle(2026, 8);
    }

    @Test
    void anlegen_xssTextWirdAlsTextGespeichert() {
        firmaIn("BY");
        LocalDate tag = LocalDate.of(2026, 12, 24);
        when(feiertagRepository.findByJahrAndBundesland(2026, "BY")).thenReturn(List.of(feiertag(1, tag.minusDays(1), "x", "BY")));
        when(feiertagRepository.findByDatumAndBundesland(tag, "BY")).thenReturn(Optional.empty());
        when(feiertagRepository.save(any(Feiertag.class))).thenAnswer(inv -> inv.getArgument(0));

        Feiertag f = feiertagService.anlegen(tag, "<script>alert(1)</script>", true);

        assertEquals("<script>alert(1)</script>", f.getBezeichnung(), "React escapt beim Anzeigen; gespeichert wird der Text");
        assertTrue(f.isHalbTag());
    }

    @Test
    void anlegen_pflichtfelderUndLaenge() {
        assertEquals("Bitte ein Datum wählen.",
                assertThrows(ResponseStatusException.class, () -> feiertagService.anlegen(null, "Fest", false)).getReason());
        assertEquals("Bitte eine Bezeichnung eingeben.",
                assertThrows(ResponseStatusException.class,
                        () -> feiertagService.anlegen(LocalDate.of(2026, 1, 2), "   ", false)).getReason());
        ResponseStatusException zuLang = assertThrows(ResponseStatusException.class,
                () -> feiertagService.anlegen(LocalDate.of(2026, 1, 2), "x".repeat(10_001), false));
        assertEquals(400, zuLang.getStatusCode().value());
        verify(feiertagRepository, never()).save(any());
    }

    @Test
    void anlegen_tagMitFeiertag_gibt400MitDeutschemDatum() {
        firmaIn("BY");
        LocalDate weihnachten = LocalDate.of(2026, 12, 25);
        Feiertag vorhanden = feiertag(1, weihnachten, "1. Weihnachtstag", "BY");
        when(feiertagRepository.findByJahrAndBundesland(2026, "BY")).thenReturn(List.of(vorhanden));
        when(feiertagRepository.findByDatumAndBundesland(weihnachten, "BY")).thenReturn(Optional.of(vorhanden));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> feiertagService.anlegen(weihnachten, "Doppelt", false));
        assertEquals("Am 25.12.2026 ist schon ein Feiertag eingetragen.", e.getReason());
    }

    @Test
    void loeschen_nurEigeneFeiertage() {
        Feiertag gesetzlich = feiertag(1, LocalDate.of(2026, 5, 1), "Tag der Arbeit", "BY");
        when(feiertagRepository.findById(1L)).thenReturn(Optional.of(gesetzlich));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> feiertagService.loeschen(1L));
        assertEquals("Gesetzliche Feiertage lassen sich nicht löschen, nur abschalten.", e.getReason());
        verify(feiertagRepository, never()).delete(any());

        Feiertag eigener = feiertag(2, LocalDate.of(2026, 12, 24), "Heiligabend", "BY");
        eigener.setEigener(true);
        when(feiertagRepository.findById(2L)).thenReturn(Optional.of(eigener));
        feiertagService.loeschen(2L);
        verify(feiertagRepository).delete(eigener);
        verify(monatsSaldoRepository).invalidiereMonatFuerAlle(2026, 12);
    }

    @Test
    void loeschen_unbekannteId_gibt404() {
        when(feiertagRepository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> feiertagService.loeschen(Long.MAX_VALUE)).getStatusCode().value());
    }

    // ==================== Neu laden ====================

    @Test
    void regenerieren_behaeltEigeneUndAngepassteFeiertage() {
        firmaIn("BY");
        Feiertag normal = feiertag(1, LocalDate.of(2026, 1, 1), "Neujahr", "BY");
        Feiertag abgeschaltet = feiertag(2, LocalDate.of(2026, 8, 15), "Mariä Himmelfahrt", "BY");
        abgeschaltet.setAktiv(false);
        abgeschaltet.setAngepasst(true);
        Feiertag eigener = feiertag(3, LocalDate.of(2026, 12, 24), "Heiligabend", "BY");
        eigener.setEigener(true);
        eigener.setAngepasst(true);
        when(feiertagRepository.findByJahrAndBundesland(2026, "BY"))
                .thenReturn(new ArrayList<>(List.of(normal, abgeschaltet, eigener)));

        feiertagService.regeneriereFeiertage(2026, 2026);

        verify(feiertagRepository).deleteAll(List.of(normal));
        verify(feiertagRepository).flush();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Feiertag>> neu = ArgumentCaptor.forClass(List.class);
        verify(feiertagRepository).saveAll(neu.capture());
        assertTrue(neu.getValue().stream().noneMatch(f -> f.getDatum().equals(LocalDate.of(2026, 8, 15))),
                "Der abgeschaltete Feiertag darf nicht als neuer Eintrag zurückkommen");
        assertTrue(neu.getValue().stream().anyMatch(f -> f.getDatum().equals(LocalDate.of(2026, 1, 1))));
        verify(monatsSaldoRepository).invalidiereJahrFuerAlle(2026);
    }

    @Test
    void naechstesJahr_wirdAm30DezemberNeuGeladen() {
        firmaIn("BY");
        int naechstes = LocalDate.now().getYear() + 1;
        when(feiertagRepository.findByJahrAndBundesland(eq(naechstes), anyString())).thenReturn(List.of());

        feiertagService.ladeFeiertageFuerNaechstesJahr();

        verify(feiertagRepository).saveAll(anyList());
        verify(monatsSaldoRepository).invalidiereJahrFuerAlle(naechstes);
    }

    @Test
    void geladenerTag_ohneVorhandeneZeilen_ruftSaveNurEinmalJeLandUndJahr() {
        firmaIn("HB");
        when(feiertagRepository.findByJahrAndBundesland(anyInt(), eq("HB"))).thenReturn(List.of());
        when(feiertagRepository.existsByDatumAndBundeslandAndAktivTrue(any(), eq("HB"))).thenReturn(false);

        assertFalse(feiertagService.istFeiertag(LocalDate.of(2027, 3, 3)));
        assertFalse(feiertagService.istFeiertag(LocalDate.of(2027, 3, 4)));

        verify(feiertagRepository, times(1)).saveAll(anyList());
    }
}
