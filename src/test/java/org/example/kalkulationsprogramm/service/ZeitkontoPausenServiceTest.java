package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZeitkontoPausenServiceTest {
    @Mock ZeitkontoRepository zeitkontoRepository;
    @Mock MitarbeiterRepository mitarbeiterRepository;
    @Mock TagesSollService tagesSollService;
    @Mock ZeitkontoVersionRepository versionRepository;
    @Mock ZeitkontoPauseRepository pauseRepository;
    @Mock ZeitbuchungRepository zeitbuchungRepository;
    @Mock MonatsSaldoRepository monatsSaldoRepository;
    @Mock EntityManager entityManager;
    @Mock jakarta.persistence.TypedQuery<MonatsSaldo> abschlussQuery;
    @Spy Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @InjectMocks ZeitkontoService service;
    Mitarbeiter mitarbeiter;
    ZeitkontoVersion letzte;
    LocalDate heute = LocalDate.now();

    @BeforeEach void setup() {
        mitarbeiter = new Mitarbeiter(); mitarbeiter.setId(1L); mitarbeiter.setVersion(2L);
        mitarbeiter.setVorname("Max"); mitarbeiter.setNachname("Mustermann");
        lenient().when(entityManager.createQuery(anyString(), eq(MonatsSaldo.class))).thenReturn(abschlussQuery);
        lenient().when(abschlussQuery.setParameter(anyString(), any())).thenReturn(abschlussQuery);
        lenient().when(abschlussQuery.setLockMode(any())).thenReturn(abschlussQuery);
        lenient().when(abschlussQuery.setMaxResults(1)).thenReturn(abschlussQuery);
        letzte = new ZeitkontoVersion(); letzte.setId(10L); letzte.setVersion(3L);
        letzte.setMitarbeiter(mitarbeiter); letzte.setGueltigVon(heute.minusMonths(2));
    }
    void sperre() {
        when(entityManager.find(Mitarbeiter.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(mitarbeiter);
    }
    void bestand() {
        sperre(); when(versionRepository.findFirstByMitarbeiterIdOrderByGueltigVonDesc(1L)).thenReturn(Optional.of(letzte));
    }
    ZeitkontenmodellDto.Arbeitszeit stunden(String montag) {
        return new ZeitkontenmodellDto.Arbeitszeit(new BigDecimal(montag), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }
    ZeitkontoWechselDto request(LocalDate start) {
        return new ZeitkontoWechselDto(start, 2L, 10L, 3L, null, null, stunden("7"));
    }
    void speichert() {
        when(versionRepository.saveAndFlush(any())).thenAnswer(i -> {
            ZeitkontoVersion v = i.getArgument(0); v.setId(11L); v.setVersion(0L); return v;
        });
    }
    void konflikt(Runnable action) {
        assertEquals(409, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
        verify(monatsSaldoRepository, never()).invalidiereAlle(any());
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test void neueVersionSchliesstNurVorversionUndInvalidiertCache() {
        bestand(); speichert();
        var result = service.zuweisen(1L, request(heute));
        assertEquals(heute.minusDays(1), letzte.getGueltigBis());
        assertEquals(heute, result.gueltigVon()); assertEquals(new BigDecimal("7"), result.arbeitszeit().montagStunden());
        verify(entityManager).refresh(mitarbeiter, LockModeType.PESSIMISTIC_WRITE);
        verify(monatsSaldoRepository).invalidiereAlle(1L);
        verifyNoInteractions(zeitkontoRepository, mitarbeiterRepository);
    }
    @Test void ersteZuweisungHatFreienBeginnUndKeineStandardstunden() {
        sperre(); speichert();
        var result = service.zuweisen(1L, new ZeitkontoWechselDto(heute.minusYears(1), 2L, null, null, null, null, stunden("0")));
        assertEquals(BigDecimal.ZERO, result.arbeitszeit().montagStunden());
        assertEquals(heute.minusYears(1), result.gueltigVon()); assertTrue(mitarbeiter.getFuehrtZeitkonto());
    }
    @Test void ersteZuweisungOhneArbeitszeitWirdAbgelehnt() {
        sperre();
        var ex = assertThrows(ResponseStatusException.class, () -> service.zuweisen(1L,
                new ZeitkontoWechselDto(heute, 2L, null, null, null, null, null)));
        assertEquals(400, ex.getStatusCode().value()); verify(versionRepository, never()).saveAndFlush(any());
    }
    @Test void paralleleErstzuweisungMitVeraltetemLeerstandWirdAbgelehnt() {
        bestand(); konflikt(() -> service.zuweisen(1L, new ZeitkontoWechselDto(heute, 2L, null, null, null, null, stunden("7"))));
    }
    @Test void veralteterMitarbeiterWirdAbgelehnt() {
        sperre(); mitarbeiter.setVersion(4L); konflikt(() -> service.zuweisen(1L, request(heute)));
    }
    @Test void veralteteVersionWirdAbgelehnt() {
        bestand(); letzte.setVersion(4L); konflikt(() -> service.zuweisen(1L, request(heute)));
    }
    @Test void rueckwirkenderOderGleicherBeginnWirdAbgelehnt() {
        bestand(); konflikt(() -> service.zuweisen(1L, request(letzte.getGueltigVon())));
        konflikt(() -> service.zuweisen(1L, request(letzte.getGueltigVon().minusDays(1))));
    }
    @Test void geschlosseneVersionOhneDokumentiertePauseWirdNieUeberschrieben() {
        bestand(); letzte.setGueltigBis(heute.minusDays(2));
        konflikt(() -> service.zuweisen(1L, request(heute)));
        assertEquals(heute.minusDays(2), letzte.getGueltigBis());
    }
    @Test void ausschaltenErzeugtPauseUndErhaeltAlteStunden() {
        bestand(); letzte.setMontagStunden(new BigDecimal("8"));
        service.ausschalten(1L, new ZeitkontoWechselDto.Ausschalten(2L, 10L, 3L));
        var captor = ArgumentCaptor.forClass(ZeitkontoPause.class); verify(pauseRepository).save(captor.capture());
        assertEquals(heute, captor.getValue().getGueltigVon()); assertNull(captor.getValue().getGueltigBis());
        assertEquals(heute.minusDays(1), letzte.getGueltigBis());
        assertEquals(new BigDecimal("8"), letzte.getMontagStunden()); assertFalse(mitarbeiter.getFuehrtZeitkonto());
    }
    @Test void ausschaltenAmErstenTagErzeugtKeinLeeresIntervall() {
        bestand(); letzte.setGueltigVon(heute);
        konflikt(() -> service.ausschalten(1L, new ZeitkontoWechselDto.Ausschalten(2L, 10L, 3L)));
        verify(pauseRepository, never()).save(any());
    }
    @Test void laufendeBuchungMussVorAusschaltenBeendetWerden() {
        bestand(); when(zeitbuchungRepository.findByMitarbeiterIdAndEndeZeitIsNull(1L)).thenReturn(List.of(new Zeitbuchung()));
        konflikt(() -> service.ausschalten(1L, new ZeitkontoWechselDto.Ausschalten(2L, 10L, 3L)));
        assertNull(letzte.getGueltigBis()); assertTrue(mitarbeiter.getFuehrtZeitkonto());
    }
    ZeitkontoPause pause(LocalDate start) {
        letzte.setGueltigBis(start.minusDays(1)); mitarbeiter.setFuehrtZeitkonto(false);
        ZeitkontoPause pause = new ZeitkontoPause(); pause.setMitarbeiter(mitarbeiter); pause.setGueltigVon(start);
        when(pauseRepository.findByMitarbeiterIdAndGueltigBisIsNull(1L)).thenReturn(Optional.of(pause)); return pause;
    }
    @Test void wiederaktivierungSchliesstExplizitePauseUndErhaeltGeschlosseneVersion() {
        bestand(); speichert(); var pause = pause(heute.minusDays(5)); LocalDate altEnde = letzte.getGueltigBis();
        service.zuweisen(1L, request(heute));
        assertEquals(heute.minusDays(1), pause.getGueltigBis()); assertEquals(altEnde, letzte.getGueltigBis());
        verify(versionRepository, never()).save(letzte); assertTrue(mitarbeiter.getFuehrtZeitkonto());
    }
    @Test void wiederaktivierungAmPausenbeginnOderNichtHeuteIstKonflikt() {
        bestand(); pause(heute);
        konflikt(() -> service.zuweisen(1L, request(heute)));
        konflikt(() -> service.zuweisen(1L, request(heute.plusDays(1))));
    }
    @Test void unpassendePauseErlaubtKeineLuecke() {
        bestand(); pause(heute.minusDays(5)); letzte.setGueltigBis(heute.minusDays(7));
        konflikt(() -> service.zuweisen(1L, request(heute)));
    }
    @Test void systemDarfKeineVersionErhalten() {
        sperre(); mitarbeiter.setArt(MitarbeiterArt.SYSTEM); konflikt(() -> service.zuweisen(1L, request(heute)));
    }
    @Test void vorlageWirdKopiertUndPersoenlicheAbweichungIstMoeglich() {
        bestand(); speichert(); var modell = new Zeitkontenmodell(); modell.setId(4L); modell.setVersion(1L);
        modell.setMontagStunden(new BigDecimal("8")); modell.setBuchungEndeZeit(java.time.LocalTime.of(18,0));
        when(entityManager.find(Zeitkontenmodell.class, 4L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(modell);
        var kopie = service.zuweisen(1L, new ZeitkontoWechselDto(heute, 2L, 10L, 3L, 4L, 1L, null));
        modell.setMontagStunden(new BigDecimal("6"));
        assertEquals(new BigDecimal("8"), kopie.arbeitszeit().montagStunden());
        assertEquals(java.time.LocalTime.of(18,0), kopie.arbeitszeit().buchungEndeZeit());
        letzte.setGueltigBis(null);
        var persoenlich = service.zuweisen(1L, new ZeitkontoWechselDto(heute, 2L, 10L, 3L, 4L, 1L, stunden("7")));
        assertEquals(new BigDecimal("7"), persoenlich.arbeitszeit().montagStunden()); assertEquals(4L, persoenlich.vorlageId());
    }

    @Test void ersteZuweisungBeiFalseNurAbHeute() {
        sperre(); mitarbeiter.setFuehrtZeitkonto(false);
        konflikt(() -> service.zuweisen(1L, new ZeitkontoWechselDto(heute.minusDays(1), 2L, null, null, null, null, stunden("7"))));
        speichert();
        assertEquals(heute, service.zuweisen(1L, new ZeitkontoWechselDto(heute, 2L, null, null, null, null, stunden("7"))).gueltigVon());
    }
    @Test void ausschaltenOhneBisherigeArbeitszeitErzeugtNurPause() {
        sperre(); service.ausschalten(1L, new ZeitkontoWechselDto.Ausschalten(2L, null, null));
        assertFalse(mitarbeiter.getFuehrtZeitkonto());
        verify(versionRepository, never()).save(any()); verify(versionRepository, never()).saveAndFlush(any());
        var captor = ArgumentCaptor.forClass(ZeitkontoPause.class); verify(pauseRepository).save(captor.capture());
        assertEquals(heute, captor.getValue().getGueltigVon());
    }
    @Test void wiederaktivierungNachPauseOhneFruehereVersionIstExplizitMoeglich() {
        sperre(); speichert(); var pause = pause(heute.minusDays(2));
        service.zuweisen(1L, new ZeitkontoWechselDto(heute, 2L, null, null, null, null, stunden("7")));
        assertEquals(heute.minusDays(1), pause.getGueltigBis()); assertTrue(mitarbeiter.getFuehrtZeitkonto());
    }
    @Test void abgeschlossenerBetroffenerMonatSperrtNeueVersion() {
        bestand(); when(abschlussQuery.getResultList()).thenReturn(List.of(new MonatsSaldo()));
        konflikt(() -> service.zuweisen(1L, request(heute.minusMonths(1))));
        assertNull(letzte.getGueltigBis()); verify(versionRepository, never()).save(any());
    }
}
