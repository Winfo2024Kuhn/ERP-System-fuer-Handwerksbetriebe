package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.SteuerberaterAnsprechpartner;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.dto.SteuerberaterAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für die Ansprechpartner-Logik im SteuerberaterKontaktService:
 * - Beim Speichern wird die Ansprechpartner-Liste in-place gemerged
 *   (orphanRemoval-konform).
 * - Genau ein Ansprechpartner darf das Lohn-Flag haben.
 * - Anrede wird über Anrede.fromString in das Enum gemappt.
 */
class SteuerberaterKontaktServiceTest {

    private SteuerberaterKontaktRepository repository;
    private SteuerberaterKontaktService service;

    @BeforeEach
    void setUp() {
        repository = mock(SteuerberaterKontaktRepository.class);
        service = new SteuerberaterKontaktService(repository);
        // save() gibt den übergebenen Kontakt zurück, damit toDto() sauber arbeiten kann.
        when(repository.save(any(SteuerberaterKontakt.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Für validateOverlap: keine bestehenden Steuerberater.
        given(repository.findByAktivTrue()).willReturn(new ArrayList<>());
    }

    @Test
    void neueAnsprechpartnerWerdenGespeichertUndZurueckgegeben() {
        SteuerberaterKontaktDto dto = baseDto();
        dto.setAnsprechpartnerListe(List.of(
                ansprechpartnerDto(null, "HERR", "Max", "Mustermann", true),
                ansprechpartnerDto(null, "FRAU", "Erika", "Musterfrau", false)
        ));

        SteuerberaterKontaktDto saved = service.speichern(dto);

        assertThat(saved.getAnsprechpartnerListe()).hasSize(2);
        assertThat(saved.getAnsprechpartnerListe())
                .extracting(SteuerberaterAnsprechpartnerDto::getNachname)
                .containsExactly("Mustermann", "Musterfrau");
        assertThat(saved.getAnsprechpartnerListe().get(0).getAnrede()).isEqualTo("HERR");
        assertThat(saved.getAnsprechpartnerListe().get(0).getIstLohnAnsprechpartner()).isTrue();
        assertThat(saved.getAnsprechpartnerListe().get(1).getIstLohnAnsprechpartner()).isFalse();
    }

    @Test
    void nurEinAnsprechpartnerDarfLohnFlagHaben() {
        SteuerberaterKontaktDto dto = baseDto();
        dto.setAnsprechpartnerListe(List.of(
                ansprechpartnerDto(null, "HERR", "Max", "Mustermann", true),
                ansprechpartnerDto(null, "FRAU", "Erika", "Musterfrau", true)
        ));

        SteuerberaterKontaktDto saved = service.speichern(dto);

        // Erwartung: nur der erste behält das Flag, alle weiteren werden auf false gesetzt.
        long lohn = saved.getAnsprechpartnerListe().stream()
                .filter(SteuerberaterAnsprechpartnerDto::getIstLohnAnsprechpartner)
                .count();
        assertThat(lohn).isEqualTo(1L);
        assertThat(saved.getAnsprechpartnerListe().get(0).getIstLohnAnsprechpartner()).isTrue();
        assertThat(saved.getAnsprechpartnerListe().get(1).getIstLohnAnsprechpartner()).isFalse();
    }

    @Test
    void bestehendeAnsprechpartnerWerdenAnhandIdAktualisiertNichtNeuErzeugt() {
        // Bestehender Steuerberater mit einem Ansprechpartner (id=42).
        SteuerberaterKontakt bestand = new SteuerberaterKontakt();
        bestand.setId(7L);
        bestand.setName("Kanzlei Test");
        SteuerberaterAnsprechpartner ap = new SteuerberaterAnsprechpartner();
        ap.setId(42L);
        ap.setNachname("Alt");
        ap.setAnrede(Anrede.HERR);
        ap.setSteuerberater(bestand);
        bestand.getAnsprechpartnerListe().add(ap);

        given(repository.findById(7L)).willReturn(Optional.of(bestand));

        SteuerberaterKontaktDto dto = baseDto();
        dto.setId(7L);
        SteuerberaterAnsprechpartnerDto apDto = ansprechpartnerDto(42L, "HERR", "Max", "Neu", true);
        dto.setAnsprechpartnerListe(List.of(apDto));

        service.speichern(dto);

        // Die Liste darf nicht durch eine neue ersetzt worden sein, sondern denselben
        // Eintrag mit aktualisierten Werten enthalten (orphanRemoval-konform).
        assertThat(bestand.getAnsprechpartnerListe()).hasSize(1);
        SteuerberaterAnsprechpartner inplace = bestand.getAnsprechpartnerListe().get(0);
        assertThat(inplace.getId()).isEqualTo(42L);
        assertThat(inplace.getNachname()).isEqualTo("Neu");
        assertThat(inplace.getVorname()).isEqualTo("Max");
        assertThat(inplace.getAnrede()).isEqualTo(Anrede.HERR);
        assertThat(inplace.getIstLohnAnsprechpartner()).isTrue();
    }

    @Test
    void leereAnsprechpartnerListeLeertBestand() {
        SteuerberaterKontakt bestand = new SteuerberaterKontakt();
        bestand.setId(8L);
        SteuerberaterAnsprechpartner ap = new SteuerberaterAnsprechpartner();
        ap.setId(99L);
        ap.setNachname("Mustermann");
        ap.setSteuerberater(bestand);
        bestand.getAnsprechpartnerListe().add(ap);

        given(repository.findById(8L)).willReturn(Optional.of(bestand));

        SteuerberaterKontaktDto dto = baseDto();
        dto.setId(8L);
        dto.setAnsprechpartnerListe(List.of());

        service.speichern(dto);

        assertThat(bestand.getAnsprechpartnerListe()).isEmpty();
    }

    @Test
    void unbekannteAnredeWirdAufNullGemappt() {
        SteuerberaterKontaktDto dto = baseDto();
        dto.setAnsprechpartnerListe(List.of(
                ansprechpartnerDto(null, "DR_PROF_PHANTASIE", "Max", "Mustermann", true)
        ));

        SteuerberaterKontaktDto saved = service.speichern(dto);

        assertThat(saved.getAnsprechpartnerListe()).hasSize(1);
        assertThat(saved.getAnsprechpartnerListe().get(0).getAnrede()).isNull();
    }

    // --- Helpers ---------------------------------------------------------

    private SteuerberaterKontaktDto baseDto() {
        SteuerberaterKontaktDto dto = new SteuerberaterKontaktDto();
        dto.setName("Kanzlei Mustermann");
        dto.setEmail("kanzlei@example.com");
        dto.setAktiv(true);
        dto.setAutoProcessEmails(true);
        return dto;
    }

    private SteuerberaterAnsprechpartnerDto ansprechpartnerDto(
            Long id, String anrede, String vorname, String nachname, boolean lohn) {
        SteuerberaterAnsprechpartnerDto dto = new SteuerberaterAnsprechpartnerDto();
        dto.setId(id);
        dto.setAnrede(anrede);
        dto.setVorname(vorname);
        dto.setNachname(nachname);
        dto.setEmail(vorname.toLowerCase() + "@example.com");
        dto.setIstLohnAnsprechpartner(lohn);
        return dto;
    }
}
