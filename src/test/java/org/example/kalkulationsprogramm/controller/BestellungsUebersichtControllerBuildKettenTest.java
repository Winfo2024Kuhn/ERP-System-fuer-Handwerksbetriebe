package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressionstest für den NPE in {@link BestellungsUebersichtController#buildKetten(List)},
 * der auftrat, wenn ein verknüpftes Dokument nicht in der gefilterten Eingabeliste
 * enthalten war (z. B. weil es ausgeblendet ist) und die HashSet-Iteration zufällig
 * dessen ID zuerst lieferte → {@code dokMap.get(id)} → null → NPE.
 */
class BestellungsUebersichtControllerBuildKettenTest {

    private final BestellungsUebersichtController controller = new BestellungsUebersichtController(
            null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void crashtNichtWennVerknuepftesDokumentNichtInEingabeliste() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(1L);
        lieferant.setLieferantenname("Max Mustermann GmbH");

        LieferantDokument anfrage = neuesDokument(10L, lieferant, LieferantDokumentTyp.ANGEBOT);
        LieferantDokument rechnung = neuesDokument(11L, lieferant, LieferantDokumentTyp.RECHNUNG);

        // Beide Dokumente sind verknüpft – aber nur die Rechnung ist in der Eingabeliste
        // (z. B. weil die Anfrage ausgeblendet wurde).
        Set<LieferantDokument> verknuepft = new HashSet<>();
        verknuepft.add(anfrage);
        rechnung.setVerknuepfteDokumente(verknuepft);

        List<BestellungsUebersichtController.DokumentenKette> ketten =
                controller.buildKetten(List.of(rechnung));

        assertThat(ketten).hasSize(1);
        BestellungsUebersichtController.DokumentenKette kette = ketten.get(0);
        assertThat(kette.lieferantId()).isEqualTo(1L);
        assertThat(kette.lieferantName()).isEqualTo("Max Mustermann GmbH");
        assertThat(kette.dokumente()).hasSize(1);
        assertThat(kette.dokumente().get(0).id).isEqualTo(11L);
    }

    @Test
    void liefertLeereListeWennEingabeleer() {
        assertThat(controller.buildKetten(List.of())).isEmpty();
    }

    @Test
    void verarbeitetMehrereVerknuepfteDokumenteInDerListe() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(2L);
        lieferant.setLieferantenname("Test Lieferant");

        LieferantDokument anfrage = neuesDokument(20L, lieferant, LieferantDokumentTyp.ANGEBOT);
        LieferantDokument rechnung = neuesDokument(21L, lieferant, LieferantDokumentTyp.RECHNUNG);
        // Verknüpfung bidirektional pflegen, wie es JPA-seitig in der Realität geschieht
        rechnung.setVerknuepfteDokumente(new HashSet<>(List.of(anfrage)));
        anfrage.setVerknuepfteDokumente(new HashSet<>(List.of(rechnung)));

        List<BestellungsUebersichtController.DokumentenKette> ketten =
                controller.buildKetten(List.of(anfrage, rechnung));

        assertThat(ketten).hasSize(1);
        assertThat(ketten.get(0).dokumente()).hasSize(2);
    }

    private LieferantDokument neuesDokument(long id, Lieferanten lieferant, LieferantDokumentTyp typ) {
        LieferantDokument d = new LieferantDokument();
        d.setId(id);
        d.setLieferant(lieferant);
        d.setTyp(typ);
        d.setUploadDatum(LocalDateTime.now());
        return d;
    }
}
