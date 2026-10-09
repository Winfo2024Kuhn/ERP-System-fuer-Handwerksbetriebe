package org.example.kalkulationsprogramm.repository;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Belegt die Backfill-Query {@link LieferantDokumentRepository#findMitXmlAnzeigedatei()}.
 * <p>
 * Hintergrund: Mail-Import-Dokumente setzen NICHT {@code LieferantDokument.attachment}
 * (FK attachment_id bleibt null) – sie sind nur über {@code EmailAttachment.lieferantDokument}
 * (Rück-FK) verknüpft und tragen die Datei in {@code gespeicherterDateiname}. Die Query
 * {@code WHERE d.attachment IS NULL AND gespeicherterDateiname LIKE '%.xml'} trifft also
 * genau diese (durch den PDF+XML-Bug falsch angelegten) Dokumente.
 */
@DataJpaTest
class LieferantDokumentRepositoryTest {

    @Autowired
    private LieferantDokumentRepository lieferantDokumentRepository;

    @Autowired
    private LieferantenRepository lieferantenRepository;

    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    private EmailAttachmentRepository emailAttachmentRepository;

    @Test
    void findMitXmlAnzeigedatei_findetNurMailImportXmlDokumente() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Test Lieferant GmbH");
        lieferant = lieferantenRepository.saveAndFlush(lieferant);

        // (1) Mail-Import-Dokument: attachment == null, Anzeige-Datei = .xml -> GEFUNDEN
        LieferantDokument xmlDoc = neuesDokument(lieferant, "Rechnung2026-0814.xml");

        // (2) attachment == null, aber .pdf -> NICHT gefunden (kein XML)
        LieferantDokument pdfDoc = neuesDokument(lieferant, "ReNr. 2026-0814.pdf");

        // (3) attachment == null, gespeicherterDateiname == null -> NICHT gefunden
        LieferantDokument ohneDatei = neuesDokument(lieferant, null);

        lieferantDokumentRepository.saveAll(List.of(xmlDoc, pdfDoc, ohneDatei));
        lieferantDokumentRepository.flush();

        // (4) .xml ABER mit gesetztem LieferantDokument.attachment -> NICHT gefunden
        Email email = new Email();
        email.setMessageId("msg-1@example.com");
        email.setDirection(EmailDirection.IN);
        email = emailRepository.saveAndFlush(email);

        EmailAttachment att = new EmailAttachment();
        att.setEmail(email);
        att.setOriginalFilename("beleg.xml");
        att.setStoredFilename("uuid_beleg.xml");
        att = emailAttachmentRepository.saveAndFlush(att);

        LieferantDokument mitAttachment = neuesDokument(lieferant, "mit_attachment.xml");
        mitAttachment.setAttachment(att);
        lieferantDokumentRepository.saveAndFlush(mitAttachment);

        List<LieferantDokument> gefunden = lieferantDokumentRepository.findMitXmlAnzeigedatei();

        assertThat(gefunden)
                .extracting(LieferantDokument::getGespeicherterDateiname)
                .containsExactly("Rechnung2026-0814.xml");
    }

    private LieferantDokument neuesDokument(Lieferanten lieferant, String gespeicherterDateiname) {
        LieferantDokument doc = new LieferantDokument();
        doc.setLieferant(lieferant);
        doc.setTyp(LieferantDokumentTyp.RECHNUNG);
        doc.setGespeicherterDateiname(gespeicherterDateiname);
        doc.setUploadDatum(LocalDateTime.now());
        return doc;
    }

    @Test
    void zaehltNurSichtbareDokumenttypenDesAngefragtenLieferanten() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Muster Lieferant GmbH");
        lieferant = lieferantenRepository.saveAndFlush(lieferant);
        Lieferanten andererLieferant = new Lieferanten();
        andererLieferant.setLieferantenname("Muster Metall GmbH");
        andererLieferant = lieferantenRepository.saveAndFlush(andererLieferant);

        LieferantDokument rechnung = neuesDokument(lieferant, "rechnung.pdf");
        LieferantDokument angebot = neuesDokument(lieferant, "angebot.pdf");
        angebot.setTyp(LieferantDokumentTyp.ANGEBOT);
        LieferantDokument fremdeRechnung = neuesDokument(andererLieferant, "fremde-rechnung.pdf");
        lieferantDokumentRepository.saveAllAndFlush(List.of(rechnung, angebot, fremdeRechnung));

        assertThat(lieferantDokumentRepository.zaehleByLieferantIdAndTypIn(lieferant.getId(),
                List.of(LieferantDokumentTyp.RECHNUNG))).isEqualTo(1);
        assertThat(lieferantDokumentRepository.zaehleByLieferantIdAndTypIn(lieferant.getId(),
                List.of(LieferantDokumentTyp.RECHNUNG, LieferantDokumentTyp.ANGEBOT))).isEqualTo(2);
        assertThat(lieferantDokumentRepository.zaehleByLieferantIdAndTypIn(lieferant.getId(),
                List.of(LieferantDokumentTyp.LIEFERSCHEIN))).isZero();
    }

    @Test
    void findetAnhaengeGesperrterDokumenttypenInBeidenVerknuepfungsrichtungen() {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname("Muster Lieferant GmbH");
        lieferant = lieferantenRepository.saveAndFlush(lieferant);
        Email email = new Email();
        email.setMessageId("msg-rechte@example.com");
        email.setDirection(EmailDirection.IN);
        email = emailRepository.saveAndFlush(email);

        // (1) Bezug über LieferantDokument.attachment (RECHNUNG) -> gesperrt
        EmailAttachment ueberDokument = anhang(email, "rechnung-a.pdf");
        LieferantDokument rechnungA = neuesDokument(lieferant, "rechnung-a.pdf");
        rechnungA.setAttachment(ueberDokument);
        lieferantDokumentRepository.saveAndFlush(rechnungA);

        // (2) Mail-Import: nur Rück-FK EmailAttachment.lieferantDokument (RECHNUNG), attachment_id bleibt leer
        LieferantDokument rechnungB = lieferantDokumentRepository.saveAndFlush(neuesDokument(lieferant, "rechnung-b.pdf"));
        EmailAttachment ueberRueckFk = anhang(email, "rechnung-b.pdf");
        ueberRueckFk.setLieferantDokument(rechnungB);
        emailAttachmentRepository.saveAndFlush(ueberRueckFk);

        // (3) Lieferschein -> bei gesperrter RECHNUNG nicht betroffen
        EmailAttachment lieferschein = anhang(email, "ls.pdf");
        LieferantDokument ls = neuesDokument(lieferant, "ls.pdf");
        ls.setTyp(LieferantDokumentTyp.LIEFERSCHEIN);
        ls.setAttachment(lieferschein);
        lieferantDokumentRepository.saveAndFlush(ls);

        // (4) Anhang ohne jedes Dokument -> nicht betroffen
        EmailAttachment ohneDokument = anhang(email, "foto.jpg");

        var ids = List.of(ueberDokument.getId(), ueberRueckFk.getId(), lieferschein.getId(), ohneDokument.getId());

        assertThat(lieferantDokumentRepository.findAnhangIdsMitDokumentTyp(ids,
                List.of(LieferantDokumentTyp.RECHNUNG)))
                .containsExactlyInAnyOrder(ueberDokument.getId(), ueberRueckFk.getId());
        assertThat(lieferantDokumentRepository.findAnhangIdsMitDokumentTyp(ids,
                List.of(LieferantDokumentTyp.LIEFERSCHEIN))).containsExactly(lieferschein.getId());
        assertThat(lieferantDokumentRepository.findAnhangIdsMitDokumentTyp(ids,
                List.of(LieferantDokumentTyp.ANGEBOT))).isEmpty();
        assertThat(lieferantDokumentRepository.findAnhangIdsMitDokumentTyp(List.of(-1L, 0L, Long.MAX_VALUE),
                List.of(LieferantDokumentTyp.RECHNUNG))).isEmpty();
    }

    private EmailAttachment anhang(Email email, String dateiname) {
        EmailAttachment att = new EmailAttachment();
        att.setEmail(email);
        att.setOriginalFilename(dateiname);
        att.setStoredFilename("uuid_" + dateiname);
        return emailAttachmentRepository.saveAndFlush(att);
    }
}
