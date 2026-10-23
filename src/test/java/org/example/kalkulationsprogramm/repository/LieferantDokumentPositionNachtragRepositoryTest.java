package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.AusgelesenePosition;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PositionsArt;
import org.example.kalkulationsprogramm.service.LieferantDokumentPositionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Speicherweg des E-Mail-Imports gegen eine echte Datenbank: Dokument samt
 * kaskadierten Geschäftsdaten (@MapsId) speichern, dann Positionen anlegen. Ohne ID
 * der Geschäftsdaten würde {@code ersetzePositionen} still nichts tun – genau der
 * alte Fehler. Dazu die Abfrage des Nachtrags.
 */
@DataJpaTest
@Import(LieferantDokumentPositionService.class)
class LieferantDokumentPositionNachtragRepositoryTest {

    @Autowired private LieferantDokumentRepository dokumentRepository;
    @Autowired private LieferantenRepository lieferantenRepository;
    @Autowired private LieferantDokumentPositionRepository positionRepository;
    @Autowired private LieferantDokumentPositionService positionService;

    private LieferantDokument speichereWieEmailImport(Lieferanten lieferant, LieferantDokumentTyp typ, String antwort) {
        LieferantDokument dokument = new LieferantDokument();
        dokument.setLieferant(lieferant);
        dokument.setOriginalDateiname("Angebot.pdf");
        dokument.setGespeicherterDateiname("uuid_Angebot.pdf");
        dokument.setUploadDatum(LocalDateTime.now());
        dokument.setTyp(typ);
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokumentNummer("AN-" + typ);
        gd.setAiRawJson(antwort);
        gd.setDokument(dokument);
        dokument.setGeschaeftsdaten(gd);
        return dokumentRepository.saveAndFlush(dokument);
    }

    private Lieferanten lieferant() {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Muster Werkzeug GmbH");
        return lieferantenRepository.saveAndFlush(l);
    }

    @Test
    void positionenLandenNachDemSpeichernWieImEmailImportInDerTabelle() {
        LieferantDokument dokument = speichereWieEmailImport(lieferant(), LieferantDokumentTyp.ANGEBOT, "{}");

        assertThat(dokument.getGeschaeftsdaten().getId()).isEqualTo(dokument.getId());
        int anzahl = positionService.ersetzePositionen(dokument.getGeschaeftsdaten(), dokument.getTyp(),
                List.of(new AusgelesenePosition(PositionsArt.WARE, "4711", "Winkelschleifer 125 mm",
                        BigDecimal.ONE, "Stück", new BigDecimal("140.40"), "Stück", new BigDecimal("140.40"),
                        null, null, null, "10", List.of())));

        assertThat(anzahl).isEqualTo(1);
        assertThat(positionRepository.countByGeschaeftsdokumentId(dokument.getId())).isEqualTo(1);
    }

    @Test
    void nachtragFindetNurDokumenteMitKiAntwortOhnePositionen() {
        Lieferanten lieferant = lieferant();
        LieferantDokument ohne = speichereWieEmailImport(lieferant, LieferantDokumentTyp.ANGEBOT, "{}");
        LieferantDokument mit = speichereWieEmailImport(lieferant, LieferantDokumentTyp.RECHNUNG, "{}");
        speichereWieEmailImport(lieferant, LieferantDokumentTyp.LIEFERSCHEIN, null);
        speichereWieEmailImport(lieferant, LieferantDokumentTyp.SONSTIG, "{}");
        positionService.ersetzePositionen(mit.getGeschaeftsdaten(), mit.getTyp(),
                List.of(new AusgelesenePosition(PositionsArt.WARE, null, "Trennscheibe", null, null, null,
                        null, null, null, null, null, null, List.of())));

        List<Long> ids = positionRepository.findeIdsOhnePositionenMitKiAntwort(
                LieferantDokumentPositionService.typenMitPositionen());

        assertThat(ids).containsExactly(ohne.getId());
    }
}
