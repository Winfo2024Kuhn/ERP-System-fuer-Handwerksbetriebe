package org.example.kalkulationsprogramm.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RechnungsuebersichtEingangRepositoryTest {
    @Autowired LieferantenRepository lieferantenRepository;
    @Autowired LieferantDokumentRepository dokumentRepository;
    @Autowired LieferantGeschaeftsdokumentRepository repository;

    @Test
    void monatsUndGesamtansichtEnthaltenGutschriftenMitNegativemBetrag() {
        speichern("RE-1", LieferantDokumentTyp.RECHNUNG, "2026-09-01", "119");
        speichern("GU-1", LieferantDokumentTyp.GUTSCHRIFT, "2026-09-30", "-119");
        speichern("GU-ALT", LieferantDokumentTyp.GUTSCHRIFT, "2026-08-31", "-59.5");
        speichern("LS-1", LieferantDokumentTyp.LIEFERSCHEIN, "2026-09-15", "119");

        var monat = repository.findRechnungenByDatumBetween(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(monat).extracting(LieferantGeschaeftsdokument::getDokumentNummer).containsExactlyInAnyOrder("RE-1", "GU-1");
        assertThat(monat.stream().map(LieferantGeschaeftsdokument::getBetragBrutto).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");
        assertThat(repository.findAllEingangsrechnungen()).extracting(LieferantGeschaeftsdokument::getDokumentNummer).containsExactly("GU-1", "RE-1", "GU-ALT");
    }

    private void speichern(String nummer, LieferantDokumentTyp typ, String datum, String betrag) {
        var lieferant = new org.example.kalkulationsprogramm.domain.Lieferanten();
        lieferant.setLieferantenname("Testlieferant " + nummer);
        lieferantenRepository.saveAndFlush(lieferant);
        var dokument = new LieferantDokument();
        dokument.setLieferant(lieferant);
        dokument.setTyp(typ);
        dokument.setOriginalDateiname(nummer + ".pdf");
        dokument.setGespeicherterDateiname(nummer + ".pdf");
        dokumentRepository.saveAndFlush(dokument);
        var daten = new LieferantGeschaeftsdokument();
        daten.setDokument(dokument);
        daten.setDokumentNummer(nummer);
        daten.setDokumentDatum(LocalDate.parse(datum));
        daten.setBetragBrutto(new BigDecimal(betrag));
        repository.saveAndFlush(daten);
    }

    @Autowired AusgangsGeschaeftsDokumentRepository ausgangsRepository;

    @Test
    void ausgangFiltertRechnungstypenUndMonatInDerDatenbank() {
        for (int i = 0; i < 5; i++) {
            var d = new org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument();
            d.setDokumentNummer("TEST-" + i);
            d.setTyp(i == 1 ? org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp.STORNO
                    : i == 2 ? org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp.ANGEBOT
                    : org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            d.setDatum(LocalDate.of(2026, i == 4 ? 8 : 9, 1));
            d.setGebucht(i != 3);
            ausgangsRepository.saveAndFlush(d);
        }
        var typen = java.util.Set.of(org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp.RECHNUNG,
                org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp.STORNO);
        assertThat(ausgangsRepository.findRechnungenFuerUebersicht(typen, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .extracting(org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument::getDokumentNummer)
                .containsExactly("TEST-1", "TEST-0");
        assertThat(ausgangsRepository.findRechnungenFuerUebersicht(typen, null, null))
                .extracting(org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument::getDokumentNummer)
                .containsExactly("TEST-1", "TEST-0", "TEST-4");
    }
}
