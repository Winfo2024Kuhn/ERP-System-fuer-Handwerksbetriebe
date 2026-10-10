package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotiz;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Echte H2-Abfragen und Objektbeziehungen einschließlich der Notizbild-EntityGraphs. */
@DataJpaTest
@Import(MobileObjectAccessService.class)
class MobileObjectAccessServiceTest {
    @Autowired private TestEntityManager em;
    @Autowired private MobileObjectAccessService access;

    private Projekt projekt(String nummer) {
        Projekt projekt = new Projekt();
        projekt.setAuftragsnummer(nummer);
        projekt.setBauvorhaben("Testbaustelle");
        projekt.setAnlegedatum(LocalDate.of(2026, 10, 9));
        projekt.setBruttoPreis(BigDecimal.ZERO);
        return em.persist(projekt);
    }

    private Mitarbeiter mitarbeiter() {
        Mitarbeiter person = new Mitarbeiter();
        person.setVorname("Max");
        person.setNachname("Mustermann");
        return em.persist(person);
    }

    private ProjektDokument projektDokument(Projekt projekt, String name, DokumentGruppe gruppe) {
        ProjektDokument dokument = new ProjektDokument();
        dokument.setProjekt(projekt);
        dokument.setOriginalDateiname("Original.jpg");
        dokument.setGespeicherterDateiname(name);
        dokument.setDokumentGruppe(gruppe);
        return em.persist(dokument);
    }

    @Test
    void gespeicherteProjektbilderSindFreigegebenAndereGruppenUndOriginalnamenNicht() {
        Projekt projekt = projekt("TEST-1");
        projektDokument(projekt, "bild.jpg", DokumentGruppe.BILDER);
        projektDokument(projekt, "rechnung.jpg", DokumentGruppe.GESCHAEFTSDOKUMENTE);
        em.flush();
        em.clear();

        assertThat(access.requireReadableFile("bild.jpg", 7L).speicher())
                .isEqualTo(MobileObjectAccessService.Speicher.PROJEKT);
        assertThatThrownBy(() -> access.requireReadableFile("rechnung.jpg", 7L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> access.requireReadableFile("Original.jpg", 7L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> access.requireReadableFile("unbekannt.jpg", 7L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anfrageDokumenteWerdenNurMitBildgruppeUndElternobjektFreigegeben() {
        Anfrage anfrage = em.persist(new Anfrage());
        AnfrageDokument bild = new AnfrageDokument();
        bild.setAnfrage(anfrage);
        bild.setGespeicherterDateiname("anfrage.jpg");
        bild.setOriginalDateiname("Foto.jpg");
        bild.setDokumentGruppe(DokumentGruppe.BILDER);
        em.persistAndFlush(bild);
        em.clear();

        assertThat(access.requireReadableFile("anfrage.jpg", 7L).speicher())
                .isEqualTo(MobileObjectAccessService.Speicher.ANFRAGE);
        bild.setAnfrage(null);
        assertThat(MobileObjectAccessService.canReadAnfrageDokument(bild)).isFalse();
        bild.setAnfrage(anfrage);
        bild.setDokumentGruppe(DokumentGruppe.KALKULATIONSDOKUMENTE);
        assertThat(MobileObjectAccessService.canReadAnfrageDokument(bild)).isFalse();
    }

    @Test
    void projektNotizBilderErbenMobileSichtbarkeitUndErstellerbindung() {
        Mitarbeiter owner = mitarbeiter();
        long ownerId = owner.getId();
        ProjektNotiz notiz = new ProjektNotiz();
        notiz.setProjekt(projekt("TEST-2"));
        notiz.setMitarbeiter(owner);
        notiz.setNotiz("Testnotiz");
        notiz.setNurFuerErsteller(true);
        em.persist(notiz);
        ProjektNotizBild bild = new ProjektNotizBild();
        bild.setNotiz(notiz);
        bild.setGespeicherterDateiname("notiz.jpg");
        em.persistAndFlush(bild);
        em.clear();

        assertThat(access.requireReadableFile("notiz.jpg", ownerId).speicher())
                .isEqualTo(MobileObjectAccessService.Speicher.PROJEKT);
        assertThatThrownBy(() -> access.requireReadableFile("notiz.jpg", ownerId + 1))
                .isInstanceOf(NotFoundException.class);
        ProjektNotiz verwaltet = em.find(ProjektNotiz.class, notiz.getId());
        verwaltet.setMobileSichtbar(false);
        em.flush();
        em.clear();
        assertThatThrownBy(() -> access.requireReadableFile("notiz.jpg", ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anfrageNotizBilderWerdenMitEchtemOwnerJoinGeprueft() {
        Mitarbeiter owner = mitarbeiter();
        long ownerId = owner.getId();
        AnfrageNotiz notiz = new AnfrageNotiz();
        notiz.setAnfrage(em.persist(new Anfrage()));
        notiz.setMitarbeiter(owner);
        notiz.setNotiz("Testnotiz");
        notiz.setNurFuerErsteller(true);
        em.persist(notiz);
        AnfrageNotizBild bild = new AnfrageNotizBild();
        bild.setNotiz(notiz);
        bild.setGespeicherterDateiname("anfrage-notiz.jpg");
        em.persistAndFlush(bild);
        em.clear();

        assertThat(access.requireReadableFile("anfrage-notiz.jpg", ownerId).speicher())
                .isEqualTo(MobileObjectAccessService.Speicher.BILD);
        assertThatThrownBy(() -> access.requireReadableFile("anfrage-notiz.jpg", ownerId + 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void falschGruppierteGeschaeftsdokumenteSindKeineMobilenBilder() {
        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setDokumentid("TEST-RECHNUNG-3");
        rechnung.setGeschaeftsdokumentart("Rechnung");
        rechnung.setProjekt(projekt("TEST-3"));
        rechnung.setDokumentGruppe(DokumentGruppe.BILDER);
        rechnung.setGespeicherterDateiname("fehlklassifiziert.jpg");
        rechnung.setOriginalDateiname("Rechnung.jpg");
        em.persistAndFlush(rechnung);
        em.clear();
        assertThatThrownBy(() -> access.requireReadableFile("fehlklassifiziert.jpg", 7L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void verwaisteBilderHabenKeineFreigabe() {
        projektDokument(null, "verwaist.jpg", DokumentGruppe.BILDER);
        em.flush();
        em.clear();
        assertThatThrownBy(() -> access.requireReadableFile("verwaist.jpg", 7L)).isInstanceOf(NotFoundException.class);
    }
}
