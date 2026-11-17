package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStandArt;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandEintragDto;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandSeiteDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Der ganze Ablauf gegen eine echte Datenbank, ohne umschließende Test-Transaktion (wie im Betrieb):
 * Speichern erzeugt keinen Stand, Export schon (nur bei Änderung), Wiederherstellen sichert offene
 * Änderungen und schreibt den Audit. Ruft die Services in derselben Reihenfolge wie die Controller.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments",
        "file.upload-dir=target/test-dokumentstand-uploads" })
class DokumentStandIntegrationTest {

    private static final String POSITIONEN = "{\"blocks\":[{\"id\":\"a\",\"type\":\"SERVICE\",\"pos\":\"1\","
            + "\"title\":\"Geländer Edelstahl\",\"quantity\":2,\"unit\":\"Stk\",\"price\":120}]}";

    @Autowired AusgangsGeschaeftsDokumentService dokumentService;
    @Autowired AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Autowired AusgangsGeschaeftsDokumentAuditService auditService;
    @Autowired FrontendUserProfileRepository benutzerRepository;
    @Autowired AusgangsDokumentStandRepository standRepository;
    @Autowired DokumentStandService standService;
    @Autowired DokumentStandAbfrageService abfrage;
    @Autowired DokumentStandWiederherstellenService wiederherstellen;

    private FrontendUserProfile benutzer(String name) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setDisplayName(name);
        p.setUsername(name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID());
        return benutzerRepository.save(p);
    }

    private AusgangsGeschaeftsDokument angebot() {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setDokumentNummer("AN-" + UUID.randomUUID().toString().substring(0, 8));
        d.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
        d.setDatum(LocalDate.of(2026, 11, 17));
        d.setBetreff("Geländer Max Mustermann");
        d.setMwstSatz(new BigDecimal("0.19"));
        d.setBetragNetto(new BigDecimal("240.00"));
        d.setPositionenJson(POSITIONEN);
        return dokumentRepository.save(d);
    }

    private void speichern(Long id, Long benutzerId, String positionen, BigDecimal netto) {
        AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
        dto.setPositionenJson(positionen);
        dto.setBetragNetto(netto);
        dokumentService.aktualisieren(id, dto);
        standService.merkeBearbeitung(id, benutzerId);
    }

    private void exportieren(Long id, Long benutzerId) {
        String datei = dokumentService.speicherePdfFuerDokument(id, new byte[] { 37, 80, 68, 70 });
        standService.festhaltenNachExport(id, benutzerId, datei);
    }

    @Test
    @DisplayName("Speichern → kein Stand; Export → Stand 1; Änderung + Export → Stand 2; Wiederherstellen")
    void ganzerAblauf() {
        FrontendUserProfile max = benutzer("Max Mustermann");
        FrontendUserProfile erika = benutzer("Erika Mustermann");
        Long id = angebot().getId();

        for (int i = 0; i < 3; i++) {
            speichern(id, max.getId(), POSITIONEN, new BigDecimal("240.00"));
        }
        assertThat(abfrage.seite(id, 0, 25).gesamt()).isZero();
        assertThat(abfrage.seite(id, 0, 25).offeneAenderungen()).isNull();

        exportieren(id, max.getId());
        StandSeiteDto nachErstemExport = abfrage.seite(id, 0, 25);
        assertThat(nachErstemExport.staende()).singleElement().satisfies(s -> {
            assertThat(s.nummer()).isEqualTo(1);
            assertThat(s.aenderungen()).containsExactly("Erste Fassung");
            assertThat(s.festgehaltenVon()).isEqualTo("Max Mustermann");
            assertThat(s.bearbeitetVon()).containsExactly("Max Mustermann");
            assertThat(s.aktuell()).isTrue();
            assertThat(s.pdfVorhanden()).isTrue();
        });
        assertThat(nachErstemExport.offeneAenderungen()).isEmpty();

        String billiger = POSITIONEN.replace("\"price\":120", "\"price\":95");
        speichern(id, erika.getId(), billiger, new BigDecimal("190.00"));
        assertThat(abfrage.seite(id, 0, 25).offeneAenderungen())
                .containsExactly("Preis Pos. 1: 120,00 € → 95,00 €", "Summe netto: 240,00 € → 190,00 €");
        exportieren(id, max.getId());
        exportieren(id, max.getId());

        StandSeiteDto nachZweitem = abfrage.seite(id, 0, 25);
        assertThat(nachZweitem.gesamt()).isEqualTo(2);
        StandEintragDto zwei = nachZweitem.staende().get(0);
        assertThat(zwei.nummer()).isEqualTo(2);
        assertThat(zwei.aenderungen()).containsExactly("Preis Pos. 1: 120,00 € → 95,00 €",
                "Summe netto: 240,00 € → 190,00 €");
        assertThat(zwei.bearbeitetVon()).containsExactly("Erika Mustermann");
        assertThat(nachZweitem.staende().get(1).aktuell()).isFalse();

        // Offene Änderung, dann Stand 1 zurückholen
        speichern(id, erika.getId(), POSITIONEN.replace("\"quantity\":2", "\"quantity\":3"), new BigDecimal("285.00"));
        Long stand1 = nachZweitem.staende().get(1).id();
        wiederherstellen.wiederherstellen(id, stand1, max.getId(), "127.0.0.1");

        AusgangsGeschaeftsDokument nachher = dokumentRepository.findById(id).orElseThrow();
        assertThat(nachher.getPositionenJson()).isEqualTo(POSITIONEN);
        assertThat(nachher.getBetragNetto()).isEqualByComparingTo("240.00");
        assertThat(nachher.getBetragBrutto()).isEqualByComparingTo("285.60");
        assertThat(nachher.getDatum()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(dokumentService.findById(id).getWiederherstellungsZaehler()).isEqualTo(1);

        StandSeiteDto nachWiederherstellen = abfrage.seite(id, 0, 25);
        assertThat(nachWiederherstellen.gesamt()).isEqualTo(3);
        StandEintragDto gesichert = nachWiederherstellen.staende().get(0);
        assertThat(gesichert.art()).isEqualTo(AusgangsDokumentStandArt.VOR_WIEDERHERSTELLEN);
        assertThat(gesichert.aenderungen()).contains("Menge Pos. 1: 2 → 3 Stk");
        assertThat(gesichert.bearbeitetVon()).containsExactly("Erika Mustermann");
        assertThat(nachWiederherstellen.staende().get(2).aktuell()).isTrue();

        List<Map<String, Object>> historie = auditService.getHistorie(id);
        assertThat(historie).anySatisfy(e -> assertThat(String.valueOf(e.get("aenderungsgrund")))
                .startsWith("Stand 1 vom ").endsWith(" wiederhergestellt"));

        // Nächster Export nennt den zurückgeholten Stand
        exportieren(id, max.getId());
        StandEintragDto vier = abfrage.seite(id, 0, 1).staende().get(0);
        assertThat(vier.nummer()).isEqualTo(4);
        assertThat(vier.wiederhergestelltAusNummer()).isEqualTo(1);
        assertThat(vier.bearbeitetVon()).containsExactly("Max Mustermann");
        assertThat(abfrage.seite(id, 0, 1).hatMehr()).isTrue();
    }
}
