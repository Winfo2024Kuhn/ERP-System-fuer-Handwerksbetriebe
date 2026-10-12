package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.LieferantBild;
import org.example.kalkulationsprogramm.domain.LieferantReklamation;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.ReklamationStatus;
import org.example.kalkulationsprogramm.dto.LieferantReklamationDto;
import org.example.kalkulationsprogramm.repository.LieferantBildRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/** Detailseite der Handy-App: GET /api/reklamationen/{id} nach dem Anlegen und aus der Liste. */
@ExtendWith(MockitoExtension.class)
class LieferantReklamationDetailTest {

    @Mock private LieferantReklamationRepository reklamationRepository;
    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private LieferantDokumentRepository dokumentRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private LieferantBildRepository bildRepository;
    @InjectMocks private LieferantReklamationController controller;

    @Test
    void liefertReklamationMitBildernUndErsteller() {
        var lieferant = new Lieferanten();
        lieferant.setId(3L);
        lieferant.setLieferantenname("Muster Stahlhandel GmbH");
        var ersteller = new Mitarbeiter();
        ersteller.setVorname("Max");
        ersteller.setNachname("Mustermann");
        var reklamation = new LieferantReklamation();
        reklamation.setId(5L);
        reklamation.setLieferant(lieferant);
        reklamation.setErstelltVon(ersteller);
        reklamation.setBeschreibung("<script>alert(1)</script> Träger verbogen");
        reklamation.setStatus(ReklamationStatus.OFFEN);
        var bild = new LieferantBild();
        bild.setId(7L);
        bild.setOriginalDateiname("foto.jpg");
        bild.setGespeicherterDateiname("abc_foto.jpg");
        reklamation.getBilder().add(bild);
        given(reklamationRepository.findById(5L)).willReturn(Optional.of(reklamation));

        ResponseEntity<LieferantReklamationDto> antwort = controller.getById(5L);

        assertThat(antwort.getStatusCode().value()).isEqualTo(200);
        LieferantReklamationDto dto = antwort.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getLieferantName()).isEqualTo("Muster Stahlhandel GmbH");
        assertThat(dto.getErstellerName()).isEqualTo("Max Mustermann");
        // Text geht unverändert als JSON raus; die App zeigt ihn als Text, nicht als HTML.
        assertThat(dto.getBeschreibung()).isEqualTo("<script>alert(1)</script> Träger verbogen");
        assertThat(dto.getBilder()).singleElement().satisfies(b -> {
            assertThat(b.url).isEqualTo("/api/lieferanten/bilder/file/abc_foto.jpg");
            assertThat(b.vorschauUrl).isEqualTo("/api/lieferanten/bilder/file/abc_foto.jpg/vorschau");
        });
    }

    @ParameterizedTest
    @ValueSource(longs = { 0L, -1L, Long.MAX_VALUE })
    void unbekannteOderUngueltigeIdGibt404(long id) {
        given(reklamationRepository.findById(id)).willReturn(Optional.empty());

        assertThat(controller.getById(id).getStatusCode().value()).isEqualTo(404);
    }
}
