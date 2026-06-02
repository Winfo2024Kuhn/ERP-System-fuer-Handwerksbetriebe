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
}
