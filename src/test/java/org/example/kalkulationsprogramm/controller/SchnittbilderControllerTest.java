package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.SchnittAchse;
import org.example.kalkulationsprogramm.domain.Schnittbilder;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittbildResponseDto;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittbildUpsertDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.SchnittAchseRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchnittbilderControllerTest {

    private SchnittbilderController controller(SchnittbilderRepository sbRepo,
                                               SchnittAchseRepository achseRepo) {
        return new SchnittbilderController(sbRepo, achseRepo,
                mock(ArtikelRepository.class), mock(KategorieRepository.class),
                mock(DateiSpeicherService.class));
    }

    @Test
    void list_nachSchnittAchseId_liefertSchnittbilderInklusiveAchsenBild() {
        SchnittbilderRepository sbRepo = mock(SchnittbilderRepository.class);
        Kategorie kat = new Kategorie(); kat.setId(5);
        SchnittAchse a = new SchnittAchse(); a.setId(77L); a.setBildUrl("/achse.png"); a.setKategorie(kat);
        Schnittbilder sb = new Schnittbilder();
        sb.setId(9L);
        sb.setBildUrlSchnittbild("/schnitt.png");
        sb.setSchnittAchse(a);
        when(sbRepo.findBySchnittAchse_IdOrderByIdAsc(77L)).thenReturn(List.of(sb));

        ResponseEntity<List<SchnittbildResponseDto>> resp = controller(sbRepo, mock(SchnittAchseRepository.class))
                .list(77L, null, null, null);

        assertEquals(1, resp.getBody().size());
        SchnittbildResponseDto dto = resp.getBody().get(0);
        assertEquals(9L, dto.getId());
        assertEquals("/schnitt.png", dto.getBildUrlSchnittbild());
        assertEquals(77L, dto.getSchnittAchseId());
        assertEquals("/achse.png", dto.getSchnittAchseBildUrl());
        assertEquals(5, dto.getKategorieId());
    }

    @Test
    void list_ohneParameter_gibtLeereListe() {
        ResponseEntity<List<SchnittbildResponseDto>> resp = controller(
                mock(SchnittbilderRepository.class), mock(SchnittAchseRepository.class))
                .list(null, null, null, null);
        assertTrue(resp.getBody().isEmpty());
    }

    @Test
    void list_nachKategorie_erbtVonElternWennKeineEigenenSchnittbilder() {
        SchnittbilderRepository sbRepo = mock(SchnittbilderRepository.class);
        Kategorie parent = new Kategorie(); parent.setId(10);
        Kategorie child = new Kategorie(); child.setId(20); child.setParentKategorie(parent);

        SchnittbilderController ctrl = new SchnittbilderController(sbRepo,
                mock(SchnittAchseRepository.class),
                mock(org.example.kalkulationsprogramm.repository.ArtikelRepository.class),
                mockKatRepoMit(child, parent),
                mock(org.example.kalkulationsprogramm.service.DateiSpeicherService.class));

        when(sbRepo.findBySchnittAchse_Kategorie_IdOrderByIdAsc(20)).thenReturn(List.of());
        SchnittAchse achse = new SchnittAchse(); achse.setId(1L); achse.setBildUrl("/a.png"); achse.setKategorie(parent);
        Schnittbilder geerbt = new Schnittbilder();
        geerbt.setId(55L); geerbt.setBildUrlSchnittbild("/geerbt.png"); geerbt.setSchnittAchse(achse);
        when(sbRepo.findBySchnittAchse_Kategorie_IdOrderByIdAsc(10)).thenReturn(List.of(geerbt));

        ResponseEntity<List<SchnittbildResponseDto>> resp = ctrl.list(null, null, null, 20);

        assertEquals(1, resp.getBody().size());
        assertEquals(55L, resp.getBody().get(0).getId());
    }

    private static org.example.kalkulationsprogramm.repository.KategorieRepository mockKatRepoMit(Kategorie... kats) {
        org.example.kalkulationsprogramm.repository.KategorieRepository repo =
                mock(org.example.kalkulationsprogramm.repository.KategorieRepository.class);
        for (Kategorie k : kats) {
            when(repo.findById(k.getId())).thenReturn(java.util.Optional.of(k));
        }
        return repo;
    }

    @Test
    void create_ohnePflichtfelder_gibt400() {
        ResponseEntity<SchnittbildResponseDto> resp = controller(
                mock(SchnittbilderRepository.class), mock(SchnittAchseRepository.class))
                .create(new SchnittbildUpsertDto());
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void create_mitGueltigenDatenSpeichertUndSetztAchse() {
        SchnittbilderRepository sbRepo = mock(SchnittbilderRepository.class);
        SchnittAchseRepository achseRepo = mock(SchnittAchseRepository.class);
        Kategorie kat = new Kategorie(); kat.setId(3);
        SchnittAchse a = new SchnittAchse(); a.setId(11L); a.setBildUrl("/a.png"); a.setKategorie(kat);
        when(achseRepo.findById(11L)).thenReturn(Optional.of(a));
        when(sbRepo.save(any(Schnittbilder.class))).thenAnswer(inv -> {
            Schnittbilder s = inv.getArgument(0);
            s.setId(456L);
            return s;
        });

        SchnittbildUpsertDto payload = new SchnittbildUpsertDto();
        payload.setBildUrlSchnittbild("/schnitt.png");
        payload.setSchnittAchseId(11L);

        ResponseEntity<SchnittbildResponseDto> resp = controller(sbRepo, achseRepo).create(payload);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(456L, resp.getBody().getId());
        assertEquals(11L, resp.getBody().getSchnittAchseId());
        assertEquals(3, resp.getBody().getKategorieId());
    }
}
