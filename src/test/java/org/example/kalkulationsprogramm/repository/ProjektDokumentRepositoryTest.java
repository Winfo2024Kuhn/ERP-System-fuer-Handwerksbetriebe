package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
class ProjektDokumentRepositoryTest {

    @Autowired
    private ProjektDokumentRepository projektDokumentRepository;

    @Autowired
    private ProjektRepository projektRepository;

    @Autowired
    private KundeRepository kundeRepository;

    @Autowired
    private AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;

    @Test
    void findDokumentIdsByProjektIdsReturnsPairs() {
        Projekt projekt1 = createProjekt("AN1");
        Projekt projekt2 = createProjekt("AN2");
        projektRepository.saveAndFlush(projekt1);
        projektRepository.saveAndFlush(projekt2);

        ProjektGeschaeftsdokument doc1 = new ProjektGeschaeftsdokument();
        doc1.setProjekt(projekt1);
        doc1.setDokumentid("DOC1");
        doc1.setGeschaeftsdokumentart("Rechnung");
        doc1.setOriginalDateiname("orig1");
        doc1.setGespeicherterDateiname("save1");

        ProjektGeschaeftsdokument doc2 = new ProjektGeschaeftsdokument();
        doc2.setProjekt(projekt2);
        doc2.setDokumentid("DOC2");
        doc2.setGeschaeftsdokumentart("Rechnung");
        doc2.setOriginalDateiname("orig2");
        doc2.setGespeicherterDateiname("save2");

        projektDokumentRepository.saveAll(List.of(doc1, doc2));
        projektDokumentRepository.flush();

        List<Object[]> result = projektDokumentRepository.findDokumentIdsByProjektIds(List.of(projekt1.getId(), projekt2.getId()));

        assertThat(result)
                .extracting(r -> (Long) r[0], r -> (String) r[1])
                .containsExactlyInAnyOrder(
                        tuple(projekt1.getId(), "DOC1"),
                        tuple(projekt2.getId(), "DOC2")
                );
    }

    @Test
    void findOffeneGeschaeftsdokumenteFindetAusgangsrechnungUndMahnung() {
        Projekt projekt = createProjekt("AN3");
        projektRepository.saveAndFlush(projekt);

        ProjektGeschaeftsdokument offeneRechnung = new ProjektGeschaeftsdokument();
        offeneRechnung.setProjekt(projekt);
        offeneRechnung.setDokumentid("DOC3");
        offeneRechnung.setGeschaeftsdokumentart("Ausgangsrechnung");
        offeneRechnung.setOriginalDateiname("orig3");
        offeneRechnung.setGespeicherterDateiname("save3");
        offeneRechnung.setBezahlt(false);

        ProjektGeschaeftsdokument offeneMahnung = new ProjektGeschaeftsdokument();
        offeneMahnung.setProjekt(projekt);
        offeneMahnung.setDokumentid("DOC4");
        offeneMahnung.setGeschaeftsdokumentart("Mahnung 1");
        offeneMahnung.setOriginalDateiname("orig4");
        offeneMahnung.setGespeicherterDateiname("save4");
        offeneMahnung.setBezahlt(false);

        ProjektGeschaeftsdokument zahlungserinnerung = new ProjektGeschaeftsdokument();
        zahlungserinnerung.setProjekt(projekt);
        zahlungserinnerung.setDokumentid("DOC5");
        zahlungserinnerung.setGeschaeftsdokumentart("Zahlungserinnerung");
        zahlungserinnerung.setOriginalDateiname("orig5");
        zahlungserinnerung.setGespeicherterDateiname("save5");
        zahlungserinnerung.setBezahlt(false);

        ProjektGeschaeftsdokument bezahlt = new ProjektGeschaeftsdokument();
        bezahlt.setProjekt(projekt);
        bezahlt.setDokumentid("DOC6");
        bezahlt.setGeschaeftsdokumentart("Rechnung");
        bezahlt.setOriginalDateiname("orig6");
        bezahlt.setGespeicherterDateiname("save6");
        bezahlt.setBezahlt(true);

        ProjektGeschaeftsdokument sonstiges = new ProjektGeschaeftsdokument();
        sonstiges.setProjekt(projekt);
        sonstiges.setDokumentid("DOC7");
        sonstiges.setGeschaeftsdokumentart("Angebot");
        sonstiges.setOriginalDateiname("orig7");
        sonstiges.setGespeicherterDateiname("save7");
        sonstiges.setBezahlt(false);

        projektDokumentRepository.saveAll(List.of(offeneRechnung, offeneMahnung, zahlungserinnerung, bezahlt, sonstiges));
        projektDokumentRepository.flush();

        List<ProjektGeschaeftsdokument> offene = projektDokumentRepository.findOffeneGeschaeftsdokumente();

        assertThat(offene)
                .extracting(ProjektGeschaeftsdokument::getDokumentid)
                .containsExactlyInAnyOrder("DOC3", "DOC4", "DOC5");
    }

    @Test
    void offenePostenIgnorierenMahnungenEinerAbgehaktenRechnung() {
        // Im Offene-Posten-Center hat nur die Rechnung einen Bezahlt-Haken; ihre
        // Mahnungen hängen darunter und bleiben intern "offen". Sie dürfen das
        // Projekt nicht dauerhaft auf "Offen" halten.
        Projekt abgehakt = projektRepository.saveAndFlush(createProjekt("AN10"));
        ProjektGeschaeftsdokument bezahlteRechnung = geschaeftsdokument(abgehakt, "RE-10", "Rechnung", true);
        projektDokumentRepository.saveAndFlush(bezahlteRechnung);
        ProjektGeschaeftsdokument erinnerung = geschaeftsdokument(abgehakt, "ZE-10", "Zahlungserinnerung", false);
        erinnerung.setMahnstufe(Mahnstufe.ZAHLUNGSERINNERUNG);
        erinnerung.setReferenzDokument(bezahlteRechnung);
        projektDokumentRepository.saveAndFlush(erinnerung);

        Projekt offen = projektRepository.saveAndFlush(createProjekt("AN11"));
        projektDokumentRepository.saveAndFlush(geschaeftsdokument(offen, "RE-11", "Rechnung", false));

        // Alte Mahnung ohne Bezug zu einer Rechnung: steht im Offene-Posten-Center als
        // eigene Zeile mit eigenem Haken und zählt deshalb weiter.
        Projekt alteMahnung = projektRepository.saveAndFlush(createProjekt("AN12"));
        projektDokumentRepository.saveAndFlush(geschaeftsdokument(alteMahnung, "MA-12", "Mahnung", false));

        assertThat(projektDokumentRepository.existsOffenePostenByProjektId(abgehakt.getId())).isFalse();
        assertThat(projektDokumentRepository.existsOffenePostenByProjektId(offen.getId())).isTrue();
        assertThat(projektDokumentRepository.existsOffenePostenByProjektId(alteMahnung.getId())).isTrue();
    }

    @Test
    void bezahlteRechnungenOhneStornoMahnungUndAlteRechnungen() {
        Projekt projekt = createProjekt("AN9");
        projektRepository.saveAndFlush(projekt);
        LocalDate heute = LocalDate.now();

        ProjektGeschaeftsdokument bezahlt = geschaeftsdokument(projekt, "RE-B1", "Rechnung", true);
        bezahlt.setRechnungsdatum(heute.minusMonths(2));
        ProjektGeschaeftsdokument schluss = geschaeftsdokument(projekt, "RE-B2", "Schlussrechnung", true);
        schluss.setRechnungsdatum(heute.minusDays(3));
        ProjektGeschaeftsdokument offen = geschaeftsdokument(projekt, "RE-O1", "Rechnung", false);
        offen.setRechnungsdatum(heute);
        ProjektGeschaeftsdokument alt = geschaeftsdokument(projekt, "RE-ALT", "Rechnung", true);
        alt.setRechnungsdatum(heute.minusMonths(13));
        ProjektGeschaeftsdokument mahnung = geschaeftsdokument(projekt, "MA-1", "Mahnung 1", true);
        mahnung.setRechnungsdatum(heute);
        ProjektGeschaeftsdokument storniert = geschaeftsdokument(projekt, "RE-S1", "Rechnung", true);
        storniert.setRechnungsdatum(heute);
        projektDokumentRepository.saveAll(List.of(bezahlt, schluss, offen, alt, mahnung, storniert));

        AusgangsGeschaeftsDokument stornoQuelle = new AusgangsGeschaeftsDokument();
        stornoQuelle.setDokumentNummer("RE-S1");
        stornoQuelle.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        stornoQuelle.setDatum(heute);
        stornoQuelle.setGebucht(true);
        stornoQuelle.setStorniert(true);
        stornoQuelle.setProjekt(projekt);
        ausgangsGeschaeftsDokumentRepository.saveAndFlush(stornoQuelle);
        projektDokumentRepository.flush();

        assertThat(projektDokumentRepository.findBezahlteRechnungenSeit(heute.minusMonths(12)))
                .extracting(ProjektGeschaeftsdokument::getDokumentid)
                .containsExactly("RE-B2", "RE-B1");
        assertThat(projektDokumentRepository.istStorniert("RE-S1")).isTrue();
        assertThat(projektDokumentRepository.istStorniert("RE-B1")).isFalse();
    }

    private ProjektGeschaeftsdokument geschaeftsdokument(Projekt projekt, String nummer, String art, boolean bezahlt) {
        ProjektGeschaeftsdokument dokument = new ProjektGeschaeftsdokument();
        dokument.setProjekt(projekt);
        dokument.setDokumentid(nummer);
        dokument.setGeschaeftsdokumentart(art);
        dokument.setOriginalDateiname(nummer + ".pdf");
        dokument.setGespeicherterDateiname(nummer + "-gespeichert.pdf");
        dokument.setBruttoBetrag(new BigDecimal("500.00"));
        dokument.setBezahlt(bezahlt);
        return dokument;
    }

    private Projekt createProjekt(String auftragsnummer) {
        Kunde kunde = new Kunde();
        kunde.setName("Kunde");
        kunde.setKundennummer("KN" + auftragsnummer);
        kunde = kundeRepository.save(kunde);
        
        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Bau");
        projekt.setKundenId(kunde);
        projekt.setAuftragsnummer(auftragsnummer);
        projekt.setAnlegedatum(LocalDate.now());
        projekt.setBruttoPreis(BigDecimal.ZERO);
        projekt.setBezahlt(false);
        return projekt;
    }
}
