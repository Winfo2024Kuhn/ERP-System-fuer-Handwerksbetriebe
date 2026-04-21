package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SchweisserZertifikat;
import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.repository.SchweisserZertifikatRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.example.kalkulationsprogramm.service.ZertifikatMatchingService.Gueltigkeit;
import org.example.kalkulationsprogramm.service.ZertifikatMatchingService.MatchResult;
import org.example.kalkulationsprogramm.service.ZertifikatMatchingService.QualifizierterSchweisserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZertifikatMatchingServiceTest {

    @Mock
    private SchweisserZertifikatRepository zertifikatRepository;

    @Mock
    private WpsRepository wpsRepository;

    @InjectMocks
    private ZertifikatMatchingService service;

    private static final LocalDate HEUTE = LocalDate.of(2026, 4, 21);

    private Wps wps(String prozess, String werkstoff) {
        Wps w = new Wps();
        w.setWpsNummer("WPS-TEST");
        w.setNorm("EN ISO 15614-1");
        w.setSchweissProzes(prozess);
        w.setGrundwerkstoff(werkstoff);
        return w;
    }

    private SchweisserZertifikat zertifikat(String prozess, String werkstoff,
                                             LocalDate ausstellung,
                                             LocalDate letzteVerlaengerung,
                                             LocalDate ablauf) {
        SchweisserZertifikat z = new SchweisserZertifikat();
        z.setZertifikatsnummer("ZERT-001");
        z.setNorm("EN ISO 9606-1");
        z.setSchweissProzes(prozess);
        z.setGrundwerkstoff(werkstoff);
        z.setAusstellungsdatum(ausstellung);
        z.setLetzteVerlaengerung(letzteVerlaengerung);
        z.setAblaufdatum(ablauf);
        return z;
    }

    // --- istQualifiziert: Matrix ---

    @Test
    void prozessUndWerkstoffPassen_undGueltig_qualifiziert() {
        SchweisserZertifikat z = zertifikat("135 MAG", "S355",
                HEUTE.minusMonths(2), HEUTE.minusMonths(2), HEUTE.plusYears(1));
        Wps w = wps("135", "S235");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertTrue(r.qualifiziert(), "Gleicher Prozess, Gruppe 1 ↔ Gruppe 1 und gültig");
        assertTrue(r.abweichungen().isEmpty());
        assertEquals(Gueltigkeit.OK, r.gueltigkeit());
    }

    @Test
    void prozessAbweichend_nichtQualifiziert() {
        SchweisserZertifikat z = zertifikat("111", "S355",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.plusYears(1));
        Wps w = wps("135", "S355");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertFalse(r.qualifiziert());
        assertEquals(1, r.abweichungen().size());
        assertTrue(r.abweichungen().get(0).toLowerCase().contains("prozess"));
    }

    @Test
    void werkstoffgruppeAbweichend_nichtQualifiziert() {
        SchweisserZertifikat z = zertifikat("141", "1.4301",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.plusYears(1));
        Wps w = wps("141", "S355");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertFalse(r.qualifiziert());
        assertTrue(r.abweichungen().stream().anyMatch(a -> a.toLowerCase().contains("werkstoff")));
    }

    @Test
    void werkstoffSuffix_toleriert_beiPraefixMatch() {
        // „S355J2+N" soll weiterhin Gruppe 1 erkannt werden
        SchweisserZertifikat z = zertifikat("135", "S355J2+N",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.plusYears(1));
        Wps w = wps("135", "S235");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertTrue(r.qualifiziert());
        assertTrue(r.abweichungen().isEmpty());
    }

    @Test
    void ablaufdatumUeberschritten_istAbgelaufen_undNichtQualifiziert() {
        SchweisserZertifikat z = zertifikat("135", "S355",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.minusDays(1));
        Wps w = wps("135", "S355");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertEquals(Gueltigkeit.ABGELAUFEN, r.gueltigkeit());
        assertFalse(r.qualifiziert());
    }

    @Test
    void verlaengerungUeberfaellig_istAbgelaufen() {
        // letzteVerlaengerung älter als 6 Monate → überfällig
        SchweisserZertifikat z = zertifikat("135", "S355",
                HEUTE.minusYears(2), HEUTE.minusMonths(7), HEUTE.plusYears(1));
        Wps w = wps("135", "S355");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertEquals(Gueltigkeit.ABGELAUFEN, r.gueltigkeit());
        assertFalse(r.qualifiziert());
    }

    @Test
    void ablaufIn30Tagen_baldAblaufend_aberQualifiziert() {
        SchweisserZertifikat z = zertifikat("135", "S355",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.plusDays(30));
        Wps w = wps("135", "S355");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertEquals(Gueltigkeit.BALD_ABLAUFEND, r.gueltigkeit());
        assertTrue(r.qualifiziert(), "Bald ablaufend ist noch qualifiziert – nur Warnung");
    }

    @Test
    void unbekannterWerkstoffImZertifikat_fuehrtZuAbweichungHinweis() {
        SchweisserZertifikat z = zertifikat("135", "Exotic-42",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.plusYears(1));
        Wps w = wps("135", "S355");

        MatchResult r = service.istQualifiziert(z, w, HEUTE);

        assertFalse(r.qualifiziert());
        assertTrue(r.abweichungen().stream().anyMatch(a -> a.toLowerCase().contains("nicht in bekannter gruppe")));
    }

    // --- findeQualifizierteSchweisser: Aggregation ---

    @Test
    void findeQualifizierteSchweisser_wpsFehlt_liefertLeereListe() {
        when(wpsRepository.findById(99L)).thenReturn(Optional.empty());

        List<QualifizierterSchweisserDto> result = service.findeQualifizierteSchweisser(99L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findeQualifizierteSchweisser_sortiertQualifizierteNachOben() {
        Wps w = wps("135", "S355");
        w.setId(1L);
        when(wpsRepository.findById(1L)).thenReturn(Optional.of(w));

        Mitarbeiter m1 = new Mitarbeiter();
        m1.setId(10L);
        m1.setVorname("Max");
        m1.setNachname("Mustermann");

        Mitarbeiter m2 = new Mitarbeiter();
        m2.setId(20L);
        m2.setVorname("Erika");
        m2.setNachname("Musterfrau");

        SchweisserZertifikat zPasst = zertifikat("135", "S355",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.plusYears(1));
        zPasst.setId(100L);
        zPasst.setMitarbeiter(m1);

        SchweisserZertifikat zFalscherProzess = zertifikat("111", "S355",
                HEUTE.minusMonths(1), HEUTE.minusMonths(1), HEUTE.plusYears(1));
        zFalscherProzess.setId(200L);
        zFalscherProzess.setMitarbeiter(m2);

        when(zertifikatRepository.findAll()).thenReturn(List.of(zFalscherProzess, zPasst));

        List<QualifizierterSchweisserDto> result = service.findeQualifizierteSchweisser(1L);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).mitarbeiterId(), "Qualifizierter Schweißer zuerst");
        assertTrue(result.get(0).qualifiziert());
        assertFalse(result.get(1).qualifiziert());
    }

    // --- Hilfsfunktionen ---

    @Test
    void normalisiereProzess_extrahiertZiffern() {
        assertEquals("135", ZertifikatMatchingService.normalisiereProzess("135 MAG"));
        assertEquals("141", ZertifikatMatchingService.normalisiereProzess("  141-WIG "));
        assertEquals("", ZertifikatMatchingService.normalisiereProzess(null));
    }

    @Test
    void werkstoffgruppe_erkenntPraefixe() {
        assertEquals(1, ZertifikatMatchingService.werkstoffgruppe("S355"));
        assertEquals(1, ZertifikatMatchingService.werkstoffgruppe("s355j2+n"));
        assertEquals(8, ZertifikatMatchingService.werkstoffgruppe("1.4301"));
        assertEquals(null, ZertifikatMatchingService.werkstoffgruppe("Exotic"));
    }
}
