package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Kunde;
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
