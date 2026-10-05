package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailDraft;
import org.example.kalkulationsprogramm.domain.EmailDraftAttachment;
import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.repository.EmailDraftAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailDraftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EmailDraftServiceLueckenTest {

    @Mock EmailDraftRepository repository;
    @Mock EmailDraftAttachmentRepository attachmentRepository;
    EmailDraftService service;

    @BeforeEach
    void setUp() {
        service = new EmailDraftService(repository, attachmentRepository);
    }

    private static EmailDraftDto dto(String recipient, String subject, String body, Long reply, Long projekt,
            Long anfrage, Boolean geschaeft) {
        return new EmailDraftDto(null, recipient, null, subject, body, "info@example.com", reply, projekt, anfrage,
                geschaeft, null, null, List.of());
    }

    private static EmailDraftDto einfach() {
        return dto("max.mustermann@example.com", "Betreff", "<p>Text</p>", null, null, null, null);
    }

    private static int status(Runnable r) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, r::run);
        return ex.getStatusCode().value();
    }

    @Test
    @DisplayName("list: ohne Entwuerfe wird kein Metadaten-Query abgesetzt")
    void listLeer() {
        when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());
        assertTrue(service.list().isEmpty());
        verifyNoInteractions(attachmentRepository);
    }

    @Test
    @DisplayName("list: Anhang-Metadaten werden dem passenden Entwurf zugeordnet")
    void listMitMetadaten() {
        EmailDraft a = new EmailDraft();
        a.setId(1L);
        EmailDraft b = new EmailDraft();
        b.setId(2L);
        EmailDraftAttachmentRepository.Metadata m = mock(EmailDraftAttachmentRepository.Metadata.class);
        when(m.getDraftId()).thenReturn(2L);
        when(m.getId()).thenReturn(9L);
        when(m.getFilename()).thenReturn("plan.pdf");
        when(m.getContentType()).thenReturn("application/pdf");
        when(m.getSize()).thenReturn(123L);
        when(repository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(a, b));
        when(attachmentRepository.findMetadata(List.of(1L, 2L))).thenReturn(List.of(m));

        List<EmailDraftDto> result = service.list();

        assertEquals(2, result.size());
        assertTrue(result.get(0).attachments().isEmpty());
        assertEquals(1, result.get(1).attachments().size());
        assertEquals("plan.pdf", result.get(1).attachments().get(0).filename());
    }

    @Test
    @DisplayName("count delegiert an das Repository")
    void count() {
        when(repository.count()).thenReturn(4L);
        assertEquals(4L, service.count());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("get/delete/download: ungueltige IDs -> 400")
    void ungueltigeIds(long id) {
        assertEquals(400, status(() -> service.get(id)));
        assertEquals(400, status(() -> service.delete(id)));
        assertEquals(400, status(() -> service.download(id, 1L)));
        assertEquals(400, status(() -> service.download(1L, id)));
        assertEquals(400, status(() -> service.download(null, 1L)));
        assertEquals(400, status(() -> service.deleteAfterSuccessfulSend(id)));
    }

    @Test
    @DisplayName("get: unbekannter Entwurf -> 404")
    void getUnbekannt() {
        when(repository.findById(Long.MAX_VALUE)).thenReturn(Optional.empty());
        assertEquals(404, status(() -> service.get(Long.MAX_VALUE)));
    }

    @Test
    @DisplayName("download: Anhang nicht gefunden -> 404, gefunden -> Bytes")
    void download() {
        when(attachmentRepository.findByIdAndDraftId(5L, 1L)).thenReturn(Optional.empty());
        assertEquals(404, status(() -> service.download(1L, 5L)));

        EmailDraftAttachment att = new EmailDraftAttachment();
        att.setFilename("a.pdf");
        att.setContentType("application/pdf");
        att.setData(new byte[] {1, 2});
        when(attachmentRepository.findByIdAndDraftId(6L, 1L)).thenReturn(Optional.of(att));
        EmailDraftService.Download d = service.download(1L, 6L);
        assertEquals("a.pdf", d.filename());
        assertArrayEquals(new byte[] {1, 2}, d.data());
    }

    @Test
    @DisplayName("save: dto null -> 400")
    void saveNull() {
        assertEquals(400, status(() -> service.save(null, null, null)));
    }

    @Test
    @DisplayName("save: Projekt und Anfrage gleichzeitig -> 400")
    void projektUndAnfrage() {
        assertEquals(400, status(() -> service.save(null, dto("a@example.com", "s", "b", null, 1L, 2L, null), null)));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("save: negative/0 IDs in Verknuepfungen -> 400")
    void ungueltigeVerknuepfung() {
        assertEquals(400, status(() -> service.save(null, dto("a@example.com", "s", "b", 0L, null, null, null), null)));
        assertEquals(400, status(() -> service.save(null, dto("a@example.com", "s", "b", null, -5L, null, null), null)));
        assertEquals(400, status(() -> service.save(null, dto("a@example.com", "s", "b", null, null, -1L, null), null)));
    }

    @Test
    @DisplayName("save: ueberlange Felder (Grenzwerte) werden abgewiesen")
    void ueberlang() {
        assertEquals(400, status(() -> service.save(null,
                dto("x".repeat(10_001), "s", "b", null, null, null, null), null)));
        assertEquals(400, status(() -> service.save(null,
                dto("a@example.com", "x".repeat(10_001), "b", null, null, null, null), null)));
        assertEquals(400, status(() -> service.save(null,
                dto("a@example.com", "s", "x".repeat(1_000_001), null, null, null, null), null)));
        EmailDraftDto langeAbsender = new EmailDraftDto(null, "a@example.com", null, "s", "b", "x".repeat(256),
                null, null, null, null, null, null, List.of());
        assertEquals(400, status(() -> service.save(null, langeAbsender, null)));
        EmailDraftDto langeCc = new EmailDraftDto(null, "a@example.com", "x".repeat(10_001), "s", "b", null,
                null, null, null, null, null, null, List.of());
        assertEquals(400, status(() -> service.save(null, langeCc, null)));
    }

    @Test
    @DisplayName("save: genau am Limit wird akzeptiert")
    void amLimit() {
        when(repository.save(any(EmailDraft.class))).thenAnswer(i -> i.getArgument(0));
        assertDoesNotThrow(() -> service.save(null, dto("x".repeat(10_000), "s", null, null, null, null, null), null));
    }

    @Test
    @DisplayName("save: neuer Entwurf uebernimmt Felder, geschaeftsdokument nur wenn gesetzt")
    void saveNeu() {
        when(repository.save(any(EmailDraft.class))).thenAnswer(i -> i.getArgument(0));

        EmailDraftDto result = service.save(null, dto("max@example.com", "Betreff", "<p>Hi</p>", 3L, 4L, null, true), null);

        assertEquals("max@example.com", result.recipient());
        assertEquals(3L, result.replyEmailId());
        assertEquals(4L, result.projektId());
        assertTrue(result.geschaeftsdokument());
        assertTrue(result.body().contains("Hi"));

        // Update ohne geschaeftsdokument-Wert laesst das Flag unveraendert
        EmailDraft vorhanden = new EmailDraft();
        vorhanden.setId(8L);
        vorhanden.setGeschaeftsdokument(true);
        when(repository.findById(8L)).thenReturn(Optional.of(vorhanden));
        EmailDraftDto upd = service.save(8L, dto("max@example.com", "neu", "<p>x</p>", null, null, null, null), null);
        assertTrue(upd.geschaeftsdokument());
        assertEquals("neu", upd.subject());
    }

    @Test
    @DisplayName("save: unbekannte Entwurf-ID beim Update -> 404, nichts wird angelegt")
    void saveUpdateUnbekannt() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertEquals(404, status(() -> service.save(99L, einfach(), null)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("save: Body-XSS wird entfernt, lokale Signatur-Bilder bleiben, fremde Bild-Pfade nicht")
    void saveBodySanitizing() {
        when(repository.save(any(EmailDraft.class))).thenAnswer(i -> i.getArgument(0));
        String body = "<p onclick=\"x()\">Hallo</p><script>alert(1)</script>"
                + "<img src=\"/api/email/signatures/3/images/7\">"
                + "<img src=\"/api/emails/12/attachments/5\">"
                + "<img src=\"/api/other/1\">";

        String out = service.save(null, dto("a@example.com", "s", body, null, null, null, null), null).body();

        assertFalse(out.contains("script"), out);
        assertFalse(out.contains("onclick"), out);
        assertTrue(out.contains("src=\"/api/email/signatures/3/images/7\""), out);
        assertTrue(out.contains("src=\"/api/emails/12/attachments/5\""), out);
        assertFalse(out.contains("/api/other/1"), out);
        assertFalse(out.contains("draft-signature.invalid"), out);
    }

    @Test
    @DisplayName("save: Body null bleibt null")
    void saveBodyNull() {
        when(repository.save(any(EmailDraft.class))).thenAnswer(i -> i.getArgument(0));
        assertNull(service.save(null, dto("a@example.com", "s", null, null, null, null, null), null).body());
    }

    @Test
    @DisplayName("save: mehr als 100 Anhaenge -> 400")
    void zuvieleAnhaenge() {
        List<org.springframework.web.multipart.MultipartFile> files = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            files.add(new MockMultipartFile("f", "a" + i + ".txt", "text/plain", new byte[] {'a'}));
        }
        assertEquals(400, status(() -> service.save(null, einfach(), files)));
    }

    @Test
    @DisplayName("save: Gesamtgroesse ueber Budget -> 413")
    void zuGross() {
        MockMultipartFile gross = new MockMultipartFile("f", "a.txt", "text/plain", new byte[] {'a'}) {
            @Override
            public long getSize() {
                return EmailDraftService.MAX_ATTACHMENT_BYTES + 1;
            }
        };
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), status(() -> service.save(null, einfach(), List.of(gross))));
    }

    @Test
    @DisplayName("save: Dateinamen werden bereinigt (Path-Traversal) und ohne Namen abgelehnt")
    void dateinamen() {
        when(repository.save(any(EmailDraft.class))).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<EmailDraft> cap = ArgumentCaptor.forClass(EmailDraft.class);

        service.save(null, einfach(), List.of(
                new MockMultipartFile("f", "../../etc/passwd.txt", "text/plain", "hi".getBytes()),
                new MockMultipartFile("f", "C:\\Temp\\Plan.PDF", "application/pdf", "hi".getBytes())));

        verify(repository).save(cap.capture());
        List<EmailDraftAttachment> atts = cap.getValue().getAttachments();
        assertEquals("passwd.txt", atts.get(0).getFilename());
        assertEquals("Plan.PDF", atts.get(1).getFilename());
        assertEquals(2, atts.get(0).getSize());
        assertSame(cap.getValue(), atts.get(0).getDraft());

        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "", "text/plain", "x".getBytes())))));
        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "..", "text/plain", "x".getBytes())))));
        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "a".repeat(256) + ".txt", "text/plain", "x".getBytes())))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"virus.exe", "x.bat", "x.js", "x.svg", "seite.html", "x.sh", "noext", "x.unbekannt"})
    @DisplayName("save: gesperrte und unbekannte Dateiendungen -> 400")
    void gesperrteEndungen(String name) {
        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", name, "text/plain", "x".getBytes())))));
    }

    @Test
    @DisplayName("save: Content-Type: unbekannt -> 400, octet-stream/leer erlaubt")
    void contentTypes() {
        when(repository.save(any(EmailDraft.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "a.txt", "text/html", "x".getBytes())))));
        assertDoesNotThrow(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "a.txt", "application/octet-stream", "x".getBytes()))));
        assertDoesNotThrow(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "a.txt", "", "x".getBytes()))));
    }

    @Test
    @DisplayName("save: getarnte Programme (MZ, #!, ELF) werden anhand der Magic-Bytes abgewiesen")
    void magicBytes() {
        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "a.txt", "text/plain", "MZ...".getBytes())))));
        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "a.txt", "text/plain", "#!/bin/sh".getBytes())))));
        assertEquals(400, status(() -> service.save(null, einfach(),
                List.of(new MockMultipartFile("f", "a.txt", "text/plain", new byte[] {0x7f, 'E', 'L', 'F', 1})))));
    }

    @Test
    @DisplayName("save: leere Dateiliste ersetzt Anhaenge, null laesst sie unveraendert")
    void anhangErsetzen() {
        when(repository.save(any(EmailDraft.class))).thenAnswer(i -> i.getArgument(0));
        EmailDraft vorhanden = new EmailDraft();
        vorhanden.setId(8L);
        EmailDraftAttachment alt = new EmailDraftAttachment();
        alt.setFilename("alt.txt");
        vorhanden.getAttachments().add(alt);
        when(repository.findById(8L)).thenReturn(Optional.of(vorhanden));

        service.save(8L, einfach(), null);
        assertEquals(1, vorhanden.getAttachments().size());

        service.save(8L, einfach(), List.of());
        assertTrue(vorhanden.getAttachments().isEmpty());
    }

    @Test
    @DisplayName("delete: loescht vorhandenen Entwurf")
    void delete() {
        EmailDraft d = new EmailDraft();
        d.setId(2L);
        when(repository.findById(2L)).thenReturn(Optional.of(d));
        service.delete(2L);
        verify(repository).delete(d);
    }

    @Test
    @DisplayName("validateForSending: null ist erlaubt, passende/unpassende Antwort-ID")
    void validateForSending() {
        assertDoesNotThrow(() -> service.validateForSending(null, 5L));

        EmailDraft d = new EmailDraft();
        d.setId(2L);
        d.setReplyEmailId(5L);
        when(repository.findById(2L)).thenReturn(Optional.of(d));
        assertDoesNotThrow(() -> service.validateForSending(2L, 5L));
        assertEquals(400, status(() -> service.validateForSending(2L, 6L)));
        assertEquals(400, status(() -> service.validateForSending(2L, null)));

        when(repository.findById(3L)).thenReturn(Optional.empty());
        assertEquals(404, status(() -> service.validateForSending(3L, null)));
    }

    @Test
    @DisplayName("deleteAfterSuccessfulSend: null no-op, fehlender Entwurf ok, vorhandener wird geloescht und geflusht")
    void deleteAfterSend() {
        service.deleteAfterSuccessfulSend(null);
        verifyNoInteractions(repository);

        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.deleteAfterSuccessfulSend(1L));
        verify(repository, never()).delete(any());
        verify(repository).flush();

        EmailDraft d = new EmailDraft();
        when(repository.findById(2L)).thenReturn(Optional.of(d));
        service.deleteAfterSuccessfulSend(2L);
        verify(repository).delete(d);
    }
}
