package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.kalkulationsprogramm.domain.Zeitkontenmodell;
import org.example.kalkulationsprogramm.dto.ZeitkontenmodellDto;
import org.example.kalkulationsprogramm.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZeitkontenmodellServiceTest {
    @Mock ZeitkontenmodellRepository repository;
    @Mock ZeitkontoVersionRepository versionRepository;
    @Mock EntityManager entityManager;
    @Spy Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @InjectMocks ZeitkontenmodellService service;

    ZeitkontenmodellDto.Arbeitszeit arbeitszeit(String stunden) {
        return new ZeitkontenmodellDto.Arbeitszeit(new BigDecimal(stunden), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }
    Zeitkontenmodell modell() {
        var v = new Zeitkontenmodell(); v.setId(1L); v.setVersion(3L); v.setBezeichnung("Werkstatt");
        when(entityManager.find(Zeitkontenmodell.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(v);
        return v;
    }
    @Test void erstellenKopiertExpliziteStunden() {
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.erstellen(new ZeitkontenmodellDto.Create(" Werkstatt ", arbeitszeit("6.50")));
        assertEquals("Werkstatt", result.bezeichnung()); assertEquals(new BigDecimal("6.50"), result.arbeitszeit().montagStunden());
    }
    @Test void updatePrueftSperrversionUndAendertNurKatalog() {
        var v = modell(); when(repository.saveAndFlush(v)).thenReturn(v);
        var result = service.aktualisieren(1L, new ZeitkontenmodellDto.Update(3L, "Teilzeit", arbeitszeit("7")));
        assertEquals("Teilzeit", result.bezeichnung()); verifyNoInteractions(versionRepository);
    }
    @Test void veraltetesUpdateIstKonflikt() {
        var v = modell();
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.aktualisieren(1L, new ZeitkontenmodellDto.Update(2L, "Teilzeit", arbeitszeit("7"))))
                .getStatusCode().value());
        assertEquals("Werkstatt", v.getBezeichnung()); verify(repository, never()).saveAndFlush(any());
    }
    @Test void historischeReferenzSperrtLoeschung() {
        modell(); when(versionRepository.existsByVorlageId(1L)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.loeschen(1L, 3L)).getStatusCode().value());
        verify(repository, never()).delete(any());
    }
    @Test void unbenutzteVorlageKannMitAktuellerVersionGeloeschtWerden() {
        var v = modell(); service.loeschen(1L, 3L); verify(repository).delete(v);
    }
    @Test void deleteOhneVersionWirdAbgelehnt() {
        modell(); assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.loeschen(1L, null)).getStatusCode().value());
        verify(repository, never()).delete(any());
    }
    @ParameterizedTest @ValueSource(longs = {-1, 0}) void ungueltigeIds(long id) {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.loeschen(id, 0L)).getStatusCode().value());
        verifyNoInteractions(entityManager, repository);
    }
    @Test void unbekannteIdIst404() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.loeschen(Long.MAX_VALUE, 0L)).getStatusCode().value());
    }
    @ParameterizedTest @ValueSource(strings = {"-0.01", "24.01", "1.001", "10000"}) void stundenLimits(String wert) {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.erstellen(new ZeitkontenmodellDto.Create("Werkstatt", arbeitszeit(wert)))).getStatusCode().value());
        verifyNoInteractions(repository);
    }
    @Test void nameIstPflichtUndBegrenzt() {
        for (String name : new String[]{"", "  ", "x".repeat(10001)}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.erstellen(new ZeitkontenmodellDto.Create(name, arbeitszeit("7")))).getStatusCode().value());
        }
        verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings = {"'; DROP TABLE x; --", "<script>alert(1)</script>"})
    void textBleibtEinWertUndWirdNichtAlsSqlOderHtmlAusgefuehrt(String name) {
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(name, service.erstellen(new ZeitkontenmodellDto.Create(name, arbeitszeit("0"))).bezeichnung());
        verifyNoInteractions(entityManager);
    }
}
