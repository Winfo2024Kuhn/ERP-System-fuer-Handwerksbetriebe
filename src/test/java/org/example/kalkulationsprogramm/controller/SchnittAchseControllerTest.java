package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.SchnittAchse;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittAchseDto;
import org.example.kalkulationsprogramm.dto.Schnittbilder.SchnittAchseUpsertDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.SchnittAchseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchnittAchseControllerTest {

    private SchnittAchseController controller(SchnittAchseRepository repo,
                                              KategorieRepository katRepo,
                                              ArtikelRepository artikelRepo) {
        return new SchnittAchseController(repo, katRepo, artikelRepo);
    }

    @Test
    void list_nachKategorie_liefertSortierteAchsen() {
        SchnittAchseRepository repo = mock(SchnittAchseRepository.class);
        KategorieRepository katRepo = mock(KategorieRepository.class);
        Kategorie kat = new Kategorie(); kat.setId(42);
        when(katRepo.findById(42)).thenReturn(java.util.Optional.of(kat));
        SchnittAchse a1 = new SchnittAchse(); a1.setId(1L); a1.setBildUrl("/a1.png"); a1.setKategorie(kat);
        SchnittAchse a2 = new SchnittAchse(); a2.setId(2L); a2.setBildUrl("/a2.png"); a2.setKategorie(kat);
        when(repo.findByKategorie_IdOrderByIdAsc(42)).thenReturn(List.of(a1, a2));

        ResponseEntity<List<SchnittAchseDto>> resp = controller(repo, katRepo, mock(ArtikelRepository.class))
                .list(null, null, 42);

        assertEquals(2, resp.getBody().size());
        assertEquals(42, resp.getBody().get(0).getKategorieId());
        assertEquals("/a1.png", resp.getBody().get(0).getBildUrl());
    }

    @Test
    void list_nachKategorie_erbtVonElternWennKeineEigenenAchsen() {
        SchnittAchseRepository repo = mock(SchnittAchseRepository.class);
        KategorieRepository katRepo = mock(KategorieRepository.class);

        Kategorie parent = new Kategorie(); parent.setId(10);
        Kategorie child = new Kategorie(); child.setId(20); child.setParentKategorie(parent);

        when(katRepo.findById(20)).thenReturn(java.util.Optional.of(child));
        when(repo.findByKategorie_IdOrderByIdAsc(20)).thenReturn(List.of()); // Leaf selbst hat nichts
        SchnittAchse ererbt = new SchnittAchse(); ererbt.setId(99L); ererbt.setBildUrl("/parent.png"); ererbt.setKategorie(parent);
        when(repo.findByKategorie_IdOrderByIdAsc(10)).thenReturn(List.of(ererbt));

        ResponseEntity<List<SchnittAchseDto>> resp = controller(repo, katRepo, mock(ArtikelRepository.class))
                .list(null, null, 20);

        assertEquals(1, resp.getBody().size());
        assertEquals(99L, resp.getBody().get(0).getId());
        assertEquals(10, resp.getBody().get(0).getKategorieId());
    }

    @Test
    void list_nachKategorie_eigenenAchsenUeberschreibenElternVollstaendig() {
        SchnittAchseRepository repo = mock(SchnittAchseRepository.class);
        KategorieRepository katRepo = mock(KategorieRepository.class);

        Kategorie parent = new Kategorie(); parent.setId(10);
        Kategorie child = new Kategorie(); child.setId(20); child.setParentKategorie(parent);

        when(katRepo.findById(20)).thenReturn(java.util.Optional.of(child));
        SchnittAchse eigen = new SchnittAchse(); eigen.setId(77L); eigen.setBildUrl("/eigen.png"); eigen.setKategorie(child);
        when(repo.findByKategorie_IdOrderByIdAsc(20)).thenReturn(List.of(eigen));

        ResponseEntity<List<SchnittAchseDto>> resp = controller(repo, katRepo, mock(ArtikelRepository.class))
                .list(null, null, 20);

        assertEquals(1, resp.getBody().size());
        assertEquals(77L, resp.getBody().get(0).getId());
        // Parent-Abfrage darf gar nicht mehr passieren, sobald Kind eigene Achsen hat
        verify(repo, never()).findByKategorie_IdOrderByIdAsc(10);
    }

    @Test
    void list_ohneFilter_liefertAlle() {
        SchnittAchseRepository repo = mock(SchnittAchseRepository.class);
        when(repo.findAll()).thenReturn(List.of(new SchnittAchse()));

        ResponseEntity<List<SchnittAchseDto>> resp = controller(repo,
                mock(KategorieRepository.class), mock(ArtikelRepository.class))
                .list(null, null, null);

        assertEquals(1, resp.getBody().size());
        verify(repo).findAll();
    }

    @Test
    void create_ohneBildUrl_gibt400() {
        ResponseEntity<SchnittAchseDto> resp = controller(mock(SchnittAchseRepository.class),
                mock(KategorieRepository.class), mock(ArtikelRepository.class))
                .create(new SchnittAchseUpsertDto());
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void create_mitGueltigenDatenLegtAnUndGibtDto() {
        SchnittAchseRepository repo = mock(SchnittAchseRepository.class);
        KategorieRepository katRepo = mock(KategorieRepository.class);
        Kategorie kat = new Kategorie(); kat.setId(7);
        when(katRepo.findById(7)).thenReturn(Optional.of(kat));
        when(repo.save(any(SchnittAchse.class))).thenAnswer(inv -> {
            SchnittAchse a = inv.getArgument(0);
            a.setId(123L);
            return a;
        });

        SchnittAchseUpsertDto payload = new SchnittAchseUpsertDto();
        payload.setBildUrl("  /uploads/achsen/y-y.png  ");
        payload.setKategorieId(7);

        ResponseEntity<SchnittAchseDto> resp = controller(repo, katRepo, mock(ArtikelRepository.class))
                .create(payload);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(123L, resp.getBody().getId());
        assertEquals("/uploads/achsen/y-y.png", resp.getBody().getBildUrl());
        assertEquals(7, resp.getBody().getKategorieId());
    }

    @Test
    void delete_unbekannteId_gibt404() {
        SchnittAchseRepository repo = mock(SchnittAchseRepository.class);
        when(repo.existsById(99L)).thenReturn(false);
        ResponseEntity<Void> resp = controller(repo, mock(KategorieRepository.class), mock(ArtikelRepository.class))
                .delete(99L);
        assertEquals(404, resp.getStatusCode().value());
    }
}
