package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantDokumentVerknuepfungSperre;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.service.LieferantDokumentDuplikatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Duplikat-Queries und der echte Löschpfad gegen H2. Ohne Test-Transaktion
 * ({@code NOT_SUPPORTED}), weil die Bereinigung je Gruppe eine eigene
 * {@code REQUIRES_NEW}-Transaktion öffnet und nur committete Daten sieht.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LieferantDokumentDuplikatRepositoryTest {

    @Autowired private LieferantDokumentRepository dokumentRepository;
    @Autowired private LieferantenRepository lieferantenRepository;
    @Autowired private EmailRepository emailRepository;
    @Autowired private EmailAttachmentRepository attachmentRepository;
    @Autowired private LieferantReklamationRepository reklamationRepository;
    @Autowired private LieferantDokumentVerknuepfungSperreRepository sperreRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void aufraeumen() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            sperreRepository.deleteAll();
            attachmentRepository.findAll().forEach(a -> a.setLieferantDokument(null));
            dokumentRepository.findAll().forEach(d -> d.getVerknuepfteDokumente().clear());
        });
        attachmentRepository.deleteAll();
        dokumentRepository.deleteAll();
        emailRepository.deleteAll();
        lieferantenRepository.deleteAll();
    }

    private Lieferanten lieferant(String name) {
        Lieferanten l = new Lieferanten();
        l.setLieferantenname(name);
        return lieferantenRepository.saveAndFlush(l);
    }

    private LieferantDokument dokument(Lieferanten lieferant, String datei, LieferantDokumentTyp typ) {
        LieferantDokument d = new LieferantDokument();
        d.setLieferant(lieferant);
        d.setTyp(typ);
        d.setGespeicherterDateiname(datei);
        d.setUploadDatum(LocalDateTime.now());
        LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
        gd.setDokument(d);
        d.setGeschaeftsdaten(gd);
        return dokumentRepository.saveAndFlush(d);
    }

    @Test
    void findetNurGleicheDateiBeimSelbenLieferanten() {
        Lieferanten muster = lieferant("Muster Stahl GmbH");
        Lieferanten andere = lieferant("Muster Metall GmbH");
        LieferantDokument a1 = dokument(muster, "uuid_a.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument a2 = dokument(muster, "uuid_a.pdf", LieferantDokumentTyp.SONSTIG);
        dokument(muster, "uuid_b.pdf", LieferantDokumentTyp.SONSTIG);
        dokument(andere, "uuid_a.pdf", LieferantDokumentTyp.SONSTIG);
        dokument(muster, null, LieferantDokumentTyp.SONSTIG);
        dokument(muster, null, LieferantDokumentTyp.SONSTIG);

        List<Object[]> zeilen = dokumentRepository.findDateiDuplikate();

        assertThat(zeilen).extracting(z -> ((Number) z[0]).longValue()).containsExactly(a1.getId(), a2.getId());
        assertThat(zeilen).allSatisfy(z -> {
            assertThat(((Number) z[1]).longValue()).isEqualTo(muster.getId());
            assertThat(z[2]).isEqualTo("uuid_a.pdf");
        });
        assertThat(dokumentRepository.findFirstByLieferantIdAndGespeicherterDateinameOrderByIdAsc(muster.getId(),
                "uuid_a.pdf")).get().extracting(LieferantDokument::getId).isEqualTo(a1.getId());
    }

    @Test
    void bereinigungLoeschtDuplikatSamtGeschaeftsdatenUndUebernimmtAnhangVerknuepfungUndSperre() {
        Lieferanten muster = lieferant("Muster Stahl GmbH");
        LieferantDokument alt = dokument(muster, "uuid_z.pdf", LieferantDokumentTyp.WERKSTOFFZEUGNIS);
        LieferantDokument neu = dokument(muster, "uuid_z.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument lieferschein = dokument(muster, "uuid_ls.pdf", LieferantDokumentTyp.LIEFERSCHEIN);
        LieferantDokument ab = dokument(muster, "uuid_ab.pdf", LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        neu.getVerknuepfteDokumente().add(lieferschein);
        dokumentRepository.saveAndFlush(neu);
        sperreRepository.saveAndFlush(new LieferantDokumentVerknuepfungSperre(neu.getId(), ab.getId(),
                LocalDateTime.of(2026, 9, 1, 8, 0)));
        Email email = new Email();
        email.setMessageId("msg-duplikat@example.com");
        email.setDirection(EmailDirection.IN);
        email = emailRepository.saveAndFlush(email);
        EmailAttachment anhang = new EmailAttachment();
        anhang.setEmail(email);
        anhang.setOriginalFilename("zeugnis.pdf");
        anhang.setStoredFilename("uuid_z.pdf");
        anhang.setLieferantDokument(neu);
        anhang = attachmentRepository.saveAndFlush(anhang);

        LieferantDokumentDuplikatService service = new LieferantDokumentDuplikatService(dokumentRepository,
                attachmentRepository, reklamationRepository, sperreRepository, null, transactionManager);
        LieferantDokumentDuplikatService.Ergebnis ergebnis = service.bereinigeDateiDuplikate();

        // Behalten wird das Werkstoffzeugnis (von Hand umgestellter Typ), nicht die SONSTIG-Kopie
        assertThat(ergebnis).isEqualTo(new LieferantDokumentDuplikatService.Ergebnis(1, 1, 0));
        assertThat(dokumentRepository.findById(neu.getId())).isEmpty();
        assertThat(attachmentRepository.findByLieferantDokumentId(alt.getId()))
                .extracting(EmailAttachment::getId).containsExactly(anhang.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> assertThat(
                dokumentRepository.findById(alt.getId()).orElseThrow().getVerknuepfteDokumente())
                .extracting(LieferantDokument::getId).containsExactly(lieferschein.getId()));
        assertThat(sperreRepository.findByBeteiligtemDokument(alt.getId()))
                .extracting(LieferantDokumentVerknuepfungSperre::getVerknuepftId).containsExactly(ab.getId());
        assertThat(dokumentRepository.findDateiDuplikate()).isEmpty();
    }

    private EmailAttachment anhang(Lieferanten lieferant, String messageId, long groesse, boolean verarbeitet,
            LieferantDokument dokument) {
        Email email = new Email();
        email.setMessageId(messageId);
        email.setDirection(EmailDirection.IN);
        email.assignToLieferant(lieferant);
        email = emailRepository.saveAndFlush(email);
        EmailAttachment a = new EmailAttachment();
        a.setEmail(email);
        a.setOriginalFilename("Widerrufsbelehrung.pdf");
        a.setStoredFilename("uuid_" + messageId + ".pdf");
        a.setSizeBytes(groesse);
        a.setAiProcessed(verarbeitet);
        a.setLieferantDokument(dokument);
        return attachmentRepository.saveAndFlush(a);
    }

    @Test
    void findetVerarbeiteteAnhaengeGleicherGroesseNurBeimSelbenLieferanten() {
        Lieferanten muster = lieferant("Muster Stahl GmbH");
        Lieferanten andere = lieferant("Muster Metall GmbH");
        EmailAttachment neu = anhang(muster, "neu@example.com", 20480, false, null);
        EmailAttachment passend = anhang(muster, "alt@example.com", 20480, true, null);
        anhang(muster, "unverarbeitet@example.com", 20480, false, null);
        anhang(muster, "groesser@example.com", 20481, true, null);
        anhang(andere, "fremd@example.com", 20480, true, null);

        assertThat(attachmentRepository.findVerarbeiteteMitGleicherGroesse(muster.getId(), 20480L, neu.getId()))
                .extracting(EmailAttachment::getId).containsExactly(passend.getId());
    }

    @Test
    void findetGleichGrosseDokumentAnhaengeVerschiedenerDokumente() {
        Lieferanten muster = lieferant("Muster Stahl GmbH");
        Lieferanten andere = lieferant("Muster Metall GmbH");
        LieferantDokument d1 = dokument(muster, "uuid_w1.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument d2 = dokument(muster, "uuid_w2.pdf", LieferantDokumentTyp.SONSTIG);
        LieferantDokument fremd = dokument(andere, "uuid_w3.pdf", LieferantDokumentTyp.SONSTIG);
        EmailAttachment a1 = anhang(muster, "w1@example.com", 20480, true, d1);
        EmailAttachment a2 = anhang(muster, "w2@example.com", 20480, true, d2);
        // Zweiter Anhang desselben Dokuments ist dabei (die Gruppierung nach Dokumenten folgt später)
        EmailAttachment a1zweit = anhang(muster, "w1-zweit@example.com", 20480, true, d1);
        // nicht dabei: anderer Lieferant, andere Größe, ohne Dokument
        anhang(andere, "w3@example.com", 20480, true, fremd);
        anhang(muster, "w4@example.com", 999, true, d2);
        anhang(muster, "w5@example.com", 20480, true, null);
        // nicht dabei: Mail von „muster“, hängt aber am Dokument von „andere“ (Mail umgehängt)
        anhang(muster, "umgehaengt@example.com", 20480, true, fremd);

        List<Object[]> zeilen = attachmentRepository.findDokumentAnhaengeMitGleicherGroesse();

        assertThat(zeilen).extracting(z -> ((Number) z[0]).longValue())
                .containsExactlyInAnyOrder(a1.getId(), a2.getId(), a1zweit.getId());
        assertThat(zeilen).allSatisfy(z -> {
            assertThat(((Number) z[1]).longValue()).isEqualTo(muster.getId());
            assertThat(((Number) z[2]).longValue()).isEqualTo(20480L);
        });
    }
}
