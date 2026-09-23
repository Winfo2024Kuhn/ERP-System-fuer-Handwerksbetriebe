package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBedarf;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufLagerentnahme;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufLagerentnahmeDto.EntnahmeRequest;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Herkunft;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.repository.EinkaufBedarfRepository;
import org.example.kalkulationsprogramm.repository.EinkaufLagerentnahmeRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMengenbuchungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufLagerentnahmeService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufMengenService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EinkaufLagerentnahmeServiceTest {
    private final EinkaufBedarfRepository bedarfRepository = mock(EinkaufBedarfRepository.class);
    private final EinkaufLagerentnahmeRepository entnahmeRepository = mock(EinkaufLagerentnahmeRepository.class);
    private final FrontendUserProfileRepository profileRepository = mock(FrontendUserProfileRepository.class);
    private final EinkaufMengenService mengenService = mock(EinkaufMengenService.class);
    private final EinkaufLagerentnahmeService service = new EinkaufLagerentnahmeService(
            bedarfRepository, entnahmeRepository, profileRepository, mengenService);

    @Test
    void bewertetDreiVonZehnEntnommenenEinheitenMitSechsEuroUndLaesstSiebenOffen() {
        UUID key = UUID.randomUUID();
        EinkaufBedarf bedarf = bedarf(new BigDecimal("10"), BigDecimal.ZERO);
        when(bedarfRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(bedarf));
        when(entnahmeRepository.findByIdempotenzKey(key)).thenReturn(Optional.empty());
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile()));
        when(entnahmeRepository.saveAndFlush(any(EinkaufLagerentnahme.class))).thenAnswer(call -> {
            EinkaufLagerentnahme entnahme = call.getArgument(0);
            entnahme.setId(41L);
            return entnahme;
        });

        var result = service.bestaetigen(new EntnahmeRequest(new Herkunft(17L, 0, new BigDecimal("3")),
                new BigDecimal("2"), "Lagerpreis am Entnahmetag", Instant.parse("2026-09-23T09:00:00Z"), key), 5L);

        assertEquals(new BigDecimal("6.00"), result.bewerteterBetrag());
        assertEquals(0, new BigDecimal("7").compareTo(result.offenerBedarf()));
        assertFalse(result.bewertungOffen());
    }

    @Test
    void unbewerteteEntnahmeBleibtOffenUndWirdNichtAlsKostenNullAusgegeben() {
        UUID key = UUID.randomUUID();
        EinkaufBedarf bedarf = bedarf(new BigDecimal("10"), BigDecimal.ZERO);
        when(bedarfRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(bedarf));
        when(entnahmeRepository.findByIdempotenzKey(key)).thenReturn(Optional.empty());
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile()));
        when(entnahmeRepository.saveAndFlush(any(EinkaufLagerentnahme.class))).thenAnswer(call -> {
            EinkaufLagerentnahme entnahme = call.getArgument(0);
            entnahme.setId(42L);
            return entnahme;
        });

        var result = service.bestaetigen(new EntnahmeRequest(new Herkunft(17L, 0, new BigDecimal("3")),
                null, null, Instant.parse("2026-09-23T09:00:00Z"), key), 5L);

        assertTrue(result.bewertungOffen());
        assertTrue(result.bewerteterBetrag() == null);
        verify(mengenService).buche(any(), any(), any(), any(), any());
    }

    @Test
    void wiederholtIdentischenIdempotenzschluesselOhneWeitereMengenbuchung() {
        UUID key = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicReference<EinkaufLagerentnahme> gespeichert = new java.util.concurrent.atomic.AtomicReference<>();
        when(bedarfRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(bedarf(new BigDecimal("10"), BigDecimal.ZERO)));
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile()));
        when(entnahmeRepository.findByIdempotenzKey(key)).thenAnswer(call -> Optional.ofNullable(gespeichert.get()));
        when(entnahmeRepository.saveAndFlush(any(EinkaufLagerentnahme.class))).thenAnswer(call -> {
            EinkaufLagerentnahme entity = call.getArgument(0);
            entity.setId(43L);
            gespeichert.set(entity);
            return entity;
        });
        EntnahmeRequest request = new EntnahmeRequest(new Herkunft(17L, 0, new BigDecimal("3")),
                new BigDecimal("2"), "Lagerpreis am Entnahmetag", Instant.parse("2026-09-23T09:00:00Z"), key);

        service.bestaetigen(request, 5L);
        service.bestaetigen(request, 5L);

        verify(mengenService).buche(any(), any(), any(), any(), any());
    }

    @Test
    void entnimmtKeineMengeDieBereitsReserviertIst() {
        UUID key = UUID.randomUUID();
        EinkaufBedarf bedarf = bedarf(new BigDecimal("10"), new BigDecimal("8"));
        when(bedarfRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(bedarf));
        when(bedarfRepository.findeAlleFuerUpdate(List.of(17L))).thenReturn(List.of(bedarf));
        when(entnahmeRepository.findByIdempotenzKey(key)).thenReturn(Optional.empty());
        when(profileRepository.findById(5L)).thenReturn(Optional.of(profile()));
        when(entnahmeRepository.saveAndFlush(any(EinkaufLagerentnahme.class))).thenAnswer(call -> {
            EinkaufLagerentnahme entnahme = call.getArgument(0);
            entnahme.setId(44L);
            return entnahme;
        });
        EinkaufMengenbuchungRepository buchungRepository = mock(EinkaufMengenbuchungRepository.class);
        EinkaufLagerentnahmeService serviceWithRealMengenpruefung = new EinkaufLagerentnahmeService(
                bedarfRepository, entnahmeRepository, profileRepository,
                new EinkaufMengenService(bedarfRepository, buchungRepository));

        EntnahmeRequest request = new EntnahmeRequest(new Herkunft(17L, 0, new BigDecimal("3")),
                new BigDecimal("2"), "Dummy Lagerpreis", Instant.parse("2026-09-23T09:00:00Z"), key);

        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> serviceWithRealMengenpruefung.bestaetigen(request, 5L));
        verify(buchungRepository, never()).saveAll(any());
    }

    private static EinkaufBedarf bedarf(BigDecimal menge, BigDecimal reserviert) {
        PositionSnapshot position = new PositionSnapshot(Positionsart.ARTIKEL, 2L, "A-2", null, null,
                "Profil", "S235", "20x20", new Mengenbasis(menge, Einheit.STUECK, menge,
                null, null, null), null, null, null, null, null, List.of(), List.of());
        EinkaufBedarf bedarf = new EinkaufBedarf(position, new Liefergruppe(null, null, 9L, null),
                9L, 3L, false);
        bedarf.setId(17L);
        bedarf.setVersion(0L);
        bedarf.setReserviert(reserviert);
        return bedarf;
    }

    private static FrontendUserProfile profile() {
        FrontendUserProfile profile = new FrontendUserProfile();
        profile.setActive(true);
        Mitarbeiter mitarbeiter = new Mitarbeiter();
        mitarbeiter.setId(55L);
        profile.setMitarbeiter(mitarbeiter);
        return profile;
    }
}
