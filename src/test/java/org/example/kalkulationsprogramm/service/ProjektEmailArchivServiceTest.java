package org.example.kalkulationsprogramm.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjektEmailArchivServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void archiviertOutMailMitProjektUndPdfAnhang() throws Exception
    {
        EmailRepository emailRepository = mock(EmailRepository.class);
        when(emailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ProjektEmailArchivService service =
                new ProjektEmailArchivService(emailRepository, tempDir.toString());
        Path pdf = tempDir.resolve("quelle.pdf");
        byte[] inhalt = new byte[] { 1, 2, 3, 4 };
        Files.write(pdf, inhalt);
        Projekt projekt = new Projekt();

        service.archiviereVersandteEmail(projekt,
                "max.mustermann@example.org", "firma@example.org",
                "Zahlungserinnerung RE-1", "<p>Text</p>", "<msg-1>",
                pdf, "Mahnung.pdf");

        ArgumentCaptor<Email> captor = ArgumentCaptor.forClass(Email.class);
        verify(emailRepository).save(captor.capture());
        Email gespeichert = captor.getValue();
        assertThat(gespeichert.getProjekt()).isSameAs(projekt);
        assertThat(gespeichert.getDirection()).isEqualTo(EmailDirection.OUT);
        assertThat(gespeichert.getRecipient()).isEqualTo("max.mustermann@example.org");
        assertThat(gespeichert.getMessageId()).isEqualTo("<msg-1>");
        assertThat(gespeichert.getAttachments()).hasSize(1);
        var anhang = gespeichert.getAttachments().getFirst();
        assertThat(anhang.getEmail()).isSameAs(gespeichert);
        assertThat(anhang.getOriginalFilename()).isEqualTo("Mahnung.pdf");
        assertThat(anhang.getMimeType()).isEqualTo("application/pdf");
        assertThat(anhang.getSizeBytes()).isEqualTo(4L);
        assertThat(Files.readAllBytes(tempDir.resolve(anhang.getStoredFilename())))
                .containsExactly(inhalt);
    }

    @Test
    void dbFehlerEntferntBereitsKopiertenAnhang() throws Exception
    {
        EmailRepository emailRepository = mock(EmailRepository.class);
        when(emailRepository.save(any())).thenThrow(new IllegalStateException("DB weg"));
        ProjektEmailArchivService service =
                new ProjektEmailArchivService(emailRepository, tempDir.toString());
        Path pdf = tempDir.resolve("quelle.pdf");
        Files.write(pdf, new byte[] { 1 });

        assertThatThrownBy(() -> service.archiviereVersandteEmail(new Projekt(),
                "max.mustermann@example.org", "firma@example.org", "Betreff",
                "<p>Text</p>", "<msg-2>", pdf, "Mahnung.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nicht archiviert");

        try (var dateien = Files.list(tempDir))
        {
            assertThat(dateien.filter(path -> !path.equals(pdf))).isEmpty();
        }
    }

    @Test
    void archiviertMehrerePdfAnhaenge() throws Exception
    {
        EmailRepository emailRepository = mock(EmailRepository.class);
        when(emailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ProjektEmailArchivService service =
                new ProjektEmailArchivService(emailRepository, tempDir.toString());
        Path ab = tempDir.resolve("ab.pdf");
        Path widerruf = tempDir.resolve("widerruf.pdf");
        Files.write(ab, new byte[] { 1, 2 });
        Files.write(widerruf, new byte[] { 3, 4, 5 });

        Email gespeichert = service.archiviereVersandteEmail(new Projekt(),
                "max.mustermann@example.org", "firma@example.org", "AB", "<p>Text</p>", "<msg-3>",
                java.util.List.of(new ProjektEmailArchivService.PdfAnhang(ab, "Auftragsbestaetigung_1.pdf"),
                        new ProjektEmailArchivService.PdfAnhang(widerruf, "../Widerruf:Stand.pdf")));

        assertThat(gespeichert.getAttachments()).hasSize(2);
        assertThat(gespeichert.getAttachments().get(0).getOriginalFilename()).isEqualTo("Auftragsbestaetigung_1.pdf");
        assertThat(gespeichert.getAttachments().get(1).getOriginalFilename()).isEqualTo(".._Widerruf_Stand.pdf");
        assertThat(gespeichert.getAttachments().get(1).getSizeBytes()).isEqualTo(3L);
    }

    @Test
    void zweiterAnhangFehltEntferntAuchDenErsten() throws Exception
    {
        EmailRepository emailRepository = mock(EmailRepository.class);
        ProjektEmailArchivService service =
                new ProjektEmailArchivService(emailRepository, tempDir.toString());
        Path ab = tempDir.resolve("ab.pdf");
        Files.write(ab, new byte[] { 1 });

        assertThatThrownBy(() -> service.archiviereVersandteEmail(new Projekt(),
                "max.mustermann@example.org", "firma@example.org", "AB", "<p>Text</p>", "<msg-4>",
                java.util.List.of(new ProjektEmailArchivService.PdfAnhang(ab, "AB.pdf"),
                        new ProjektEmailArchivService.PdfAnhang(tempDir.resolve("fehlt.pdf"), "W.pdf"))))
                .isInstanceOf(IllegalStateException.class);

        try (var dateien = Files.list(tempDir))
        {
            assertThat(dateien.filter(path -> !path.equals(ab))).isEmpty();
        }
    }

    @Test
    void leereOderUnvollstaendigeAnhanglisteWirdAbgelehnt()
    {
        ProjektEmailArchivService service =
                new ProjektEmailArchivService(mock(EmailRepository.class), tempDir.toString());

        assertThatThrownBy(() -> service.archiviereVersandteEmail(new Projekt(), "a", "b", "c", "d", "e",
                java.util.List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.archiviereVersandteEmail(new Projekt(), "a", "b", "c", "d", "e",
                (java.util.List<ProjektEmailArchivService.PdfAnhang>) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.archiviereVersandteEmail(new Projekt(), "a", "b", "c", "d", "e",
                java.util.List.of(new ProjektEmailArchivService.PdfAnhang(null, "x.pdf"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leeresAttachmentVerzeichnisWirdAbgelehnt()
    {
        assertThatThrownBy(() -> new ProjektEmailArchivService(mock(EmailRepository.class), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mail-attachment-dir");
    }
}
