package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basisdokument-Prüfung gegen die Datenbank (Issue #262): Eine stornierte Rechnung
 * ohne Vorgänger und ihre Stornorechnung dürfen nicht als „Basisdokument vorhanden“
 * zählen, sonst lässt sich im Projekt keine neue Rechnung mehr anlegen.
 * Nur Dummy-Daten.
 */
@DataJpaTest
class AusgangsGeschaeftsDokumentBasisdokumentRepositoryTest {

    private final AusgangsGeschaeftsDokumentRepository repository;
    private final ProjektRepository projektRepository;
    private final KundeRepository kundeRepository;

    private Projekt projekt;

    @Autowired
    AusgangsGeschaeftsDokumentBasisdokumentRepositoryTest(AusgangsGeschaeftsDokumentRepository repository,
            ProjektRepository projektRepository, KundeRepository kundeRepository) {
        this.repository = repository;
        this.projektRepository = projektRepository;
        this.kundeRepository = kundeRepository;
    }

    @BeforeEach
    void projektAnlegen() {
        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-26201");
        kunde.setName("Max Mustermann");
        kunde.setKundenEmails(List.of("max.mustermann@example.org"));
        kunde = kundeRepository.save(kunde);

        Projekt p = new Projekt();
        p.setBauvorhaben("Musterbau Gartentor");
        p.setAuftragsnummer("2026/10/26201");
        p.setAnlegedatum(LocalDate.of(2026, 10, 1));
        p.setBruttoPreis(BigDecimal.TEN);
        p.setBezahlt(false);
        p.setKundenId(kunde);
        projekt = projektRepository.save(p);
    }

    @Test
    void stornierteRechnungMitStornorechnungZaehltNichtAlsBasisdokument() {
        AusgangsGeschaeftsDokument rechnung = dokument("RE-1", AusgangsGeschaeftsDokumentTyp.RECHNUNG, null);
        rechnung.setStorniert(true);
        rechnung.setStorniertAm(LocalDate.of(2026, 10, 2));
        rechnung = repository.saveAndFlush(rechnung);
        repository.saveAndFlush(dokument("ST-1", AusgangsGeschaeftsDokumentTyp.STORNO, rechnung));

        assertThat(repository.existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(projekt.getId())).isFalse();
        assertThat(repository.existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse(
                projekt.getId(), AusgangsGeschaeftsDokumentTyp.RECHNUNG)).isFalse();
    }

    @Test
    void neueRechnungNachStornoZaehltWiederAlsBasisdokument() {
        AusgangsGeschaeftsDokument alt = dokument("RE-1", AusgangsGeschaeftsDokumentTyp.RECHNUNG, null);
        alt.setStorniert(true);
        alt = repository.saveAndFlush(alt);
        repository.saveAndFlush(dokument("ST-1", AusgangsGeschaeftsDokumentTyp.STORNO, alt));
        repository.saveAndFlush(dokument("RE-2", AusgangsGeschaeftsDokumentTyp.RECHNUNG, null));

        assertThat(repository.existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(projekt.getId())).isTrue();
    }

    @Test
    void angebotMitStornierterAbschlagsrechnungBleibtBasisdokument() {
        AusgangsGeschaeftsDokument angebot = repository.saveAndFlush(
                dokument("AN-1", AusgangsGeschaeftsDokumentTyp.ANGEBOT, null));
        AusgangsGeschaeftsDokument abschlag = dokument("AR-1", AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, angebot);
        abschlag.setStorniert(true);
        abschlag = repository.saveAndFlush(abschlag);
        repository.saveAndFlush(dokument("ST-1", AusgangsGeschaeftsDokumentTyp.STORNO, abschlag));

        assertThat(repository.existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(projekt.getId())).isTrue();
        assertThat(repository.existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse(
                projekt.getId(), AusgangsGeschaeftsDokumentTyp.ANGEBOT)).isTrue();
    }

    private AusgangsGeschaeftsDokument dokument(String nummer, AusgangsGeschaeftsDokumentTyp typ,
            AusgangsGeschaeftsDokument vorgaenger) {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setDokumentNummer(nummer + "-" + projekt.getId());
        d.setTyp(typ);
        d.setDatum(LocalDate.of(2026, 10, 1));
        d.setGebucht(true);
        d.setProjekt(projekt);
        d.setVorgaenger(vorgaenger);
        return d;
    }
}
