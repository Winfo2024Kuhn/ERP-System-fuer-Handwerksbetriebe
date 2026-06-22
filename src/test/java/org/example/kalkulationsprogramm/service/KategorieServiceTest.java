package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieResponseDto;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests fuer die Lieferanten-Rollen-Logik in KategorieService:
 * Setzen der typischen Rollen einer Kategorie und Vererbung an Unterkategorien
 * ohne eigene Rollen (siehe Kategorie#typischeRollen).
 */
@ExtendWith(MockitoExtension.class)
class KategorieServiceTest {

    @Mock
    private KategorieRepository kategorieRepository;

    @InjectMocks
    private KategorieService kategorieService;

    @Test
    void erstelleKategorieUebernimmtTypischeRollen() {
        KategorieCreateDto dto = new KategorieCreateDto();
        dto.setBezeichnung("Schrauben");
        dto.setTypischeRollen(Set.of(LieferantRolle.SCHRAUBEN_NORMTEILE));

        Kategorie gespeichert = new Kategorie();
        gespeichert.setId(1);
        gespeichert.setBeschreibung("Schrauben");
        gespeichert.setTypischeRollen(Set.of(LieferantRolle.SCHRAUBEN_NORMTEILE));
        when(kategorieRepository.save(any(Kategorie.class))).thenReturn(gespeichert);

        KategorieResponseDto result = kategorieService.erstelleKategorie(dto);

        assertEquals(Set.of(LieferantRolle.SCHRAUBEN_NORMTEILE), result.getTypischeRollen());
    }

    @Test
    void aktualisiereTypischeRollenSpeichertNeueRollen() {
        Kategorie kategorie = new Kategorie();
        kategorie.setId(5);
        kategorie.setBeschreibung("Beschichtung");
        when(kategorieRepository.findById(5)).thenReturn(Optional.of(kategorie));
        when(kategorieRepository.save(any(Kategorie.class))).thenAnswer(inv -> inv.getArgument(0));

        KategorieResponseDto result = kategorieService.aktualisiereTypischeRollen(5,
                Set.of(LieferantRolle.BESCHICHTUNG_VERZINKEN, LieferantRolle.LACKIERER));

        assertEquals(Set.of(LieferantRolle.BESCHICHTUNG_VERZINKEN, LieferantRolle.LACKIERER), result.getTypischeRollen());
        verify(kategorieRepository).save(kategorie);
    }

    @Test
    void aktualisiereTypischeRollenMitLeeremSetLoeschtZuordnung() {
        Kategorie kategorie = new Kategorie();
        kategorie.setId(5);
        kategorie.setTypischeRollen(Set.of(LieferantRolle.IT));
        when(kategorieRepository.findById(5)).thenReturn(Optional.of(kategorie));
        when(kategorieRepository.save(any(Kategorie.class))).thenAnswer(inv -> inv.getArgument(0));

        KategorieResponseDto result = kategorieService.aktualisiereTypischeRollen(5, Set.of());

        assertTrue(result.getTypischeRollen().isEmpty());
    }

    @Test
    void aktualisiereTypischeRollenWirftNotFoundBeiUnbekannterKategorie() {
        when(kategorieRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> kategorieService.aktualisiereTypischeRollen(999, Set.of(LieferantRolle.IT)));
    }

    @Test
    void findeEffektiveRollenLiefertEigeneRollenWennVorhanden() {
        Kategorie kategorie = new Kategorie();
        kategorie.setId(1);
        kategorie.setTypischeRollen(Set.of(LieferantRolle.STAHLHANDEL));
        when(kategorieRepository.findById(1)).thenReturn(Optional.of(kategorie));

        assertEquals(Set.of(LieferantRolle.STAHLHANDEL), kategorieService.findeEffektiveRollen(1));
    }

    @Test
    void findeEffektiveRollenErbtVonOberkategorieWennLeer() {
        Kategorie parent = new Kategorie();
        parent.setId(1);
        parent.setTypischeRollen(Set.of(LieferantRolle.EDELSTAHL));

        Kategorie kind = new Kategorie();
        kind.setId(2);
        kind.setParentKategorie(parent);
        kind.setTypischeRollen(Set.of());

        when(kategorieRepository.findById(2)).thenReturn(Optional.of(kind));

        assertEquals(Set.of(LieferantRolle.EDELSTAHL), kategorieService.findeEffektiveRollen(2));
    }

    @Test
    void findeEffektiveRollenLiefertLeeresSetWennNirgendsRollenHinterlegt() {
        Kategorie kategorie = new Kategorie();
        kategorie.setId(3);
        kategorie.setTypischeRollen(Set.of());
        when(kategorieRepository.findById(3)).thenReturn(Optional.of(kategorie));

        assertTrue(kategorieService.findeEffektiveRollen(3).isEmpty());
    }

    @Test
    void findeEffektiveRollenLiefertLeeresSetWennKategorieNichtExistiert() {
        when(kategorieRepository.findById(404)).thenReturn(Optional.empty());

        assertTrue(kategorieService.findeEffektiveRollen(404).isEmpty());
    }
}
