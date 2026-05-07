package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifiziert searchLieferscheine: Treffer ueber drei Quellen
 * (geschaeftsdaten.dokumentNummer, originalDateiname, attachment.originalFilename)
 * und zwei Negativfaelle (falscher Lieferant, falscher Typ).
 */
@DataJpaTest
@DirtiesContext
class LieferantDokumentRepositorySearchTest {

    @Autowired
    private LieferantDokumentRepository repository;

    @PersistenceContext
    private EntityManager em;

    private Lieferanten lieferant;
    private Lieferanten andererLieferant;

    @BeforeEach
    void setUp() {
        lieferant = new Lieferanten();
        lieferant.setLieferantenname("Mustermann GmbH");
        em.persist(lieferant);

        andererLieferant = new Lieferanten();
        andererLieferant.setLieferantenname("Andere GmbH");
        em.persist(andererLieferant);
    }

    @Test
    void findetTrefferUeberDokumentNummer() {
        var doc = persistLieferschein(lieferant, "scan_anonym.pdf", null);
        persistGeschaeftsdaten(doc, "LS-2026-9981");

        em.flush();
        em.clear();

        List<LieferantDokument> result = repository.searchLieferscheine(lieferant.getId(), "9981");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGeschaeftsdaten().getDokumentNummer()).isEqualTo("LS-2026-9981");
    }

    @Test
    void findetTrefferUeberOriginalDateinameBeiManuellemUpload() {
        persistLieferschein(lieferant, "Lieferschein-12345-MaxMustermann.pdf", null);

        em.flush();
        em.clear();

        List<LieferantDokument> result = repository.searchLieferscheine(lieferant.getId(), "12345");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalDateiname()).contains("12345");
    }

    @Test
    void findetTrefferUeberAttachmentOriginalFilename() {
        var attachment = persistEmailAttachment("LS_77777_anhang.pdf");
        persistLieferschein(lieferant, null, attachment);

        em.flush();
        em.clear();

        List<LieferantDokument> result = repository.searchLieferscheine(lieferant.getId(), "77777");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEffektiverDateiname()).contains("77777");
    }

    @Test
    void liefertNichtsBeiFalschemLieferanten() {
        var doc = persistLieferschein(andererLieferant, "scan.pdf", null);
        persistGeschaeftsdaten(doc, "LS-XYZ");

        em.flush();
        em.clear();

        List<LieferantDokument> result = repository.searchLieferscheine(lieferant.getId(), "XYZ");

        assertThat(result).isEmpty();
    }

    @Test
    void ignoriertNichtLieferscheinTypen() {
        var rechnung = new LieferantDokument();
        rechnung.setLieferant(lieferant);
        rechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
        rechnung.setOriginalDateiname("Rechnung-LS-99999.pdf");
        rechnung.setUploadDatum(LocalDateTime.now());
        em.persist(rechnung);

        em.flush();
        em.clear();

        List<LieferantDokument> result = repository.searchLieferscheine(lieferant.getId(), "99999");

        assertThat(result).isEmpty();
    }

    private LieferantDokument persistLieferschein(Lieferanten owner, String originalDateiname, EmailAttachment attachment) {
        LieferantDokument doc = new LieferantDokument();
        doc.setLieferant(owner);
        doc.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);
        doc.setOriginalDateiname(originalDateiname);
        doc.setAttachment(attachment);
        doc.setUploadDatum(LocalDateTime.now());
        em.persist(doc);
        return doc;
    }

    private void persistGeschaeftsdaten(LieferantDokument doc, String dokumentNummer) {
        LieferantGeschaeftsdokument g = new LieferantGeschaeftsdokument();
        g.setDokument(doc);
        g.setDokumentNummer(dokumentNummer);
        doc.setGeschaeftsdaten(g);
        em.persist(g);
    }

    private EmailAttachment persistEmailAttachment(String filename) {
        Email email = new Email();
        email.setMessageId("test-msg-" + filename + "-" + System.nanoTime());
        email.setDirection(EmailDirection.IN);
        em.persist(email);

        EmailAttachment a = new EmailAttachment();
        a.setEmail(email);
        a.setOriginalFilename(filename);
        a.setStoredFilename("stored_" + filename);
        em.persist(a);
        return a;
    }
}
