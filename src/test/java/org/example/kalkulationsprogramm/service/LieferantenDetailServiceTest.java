package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LieferantenDetailServiceTest {

    @Mock private LieferantenRepository lieferantenRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private LieferantArtikelpreisMapper artikelpreisMapper;
    @Mock private LieferantDokumentService lieferantDokumentService;
    @Mock private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @Mock private LieferantNotizRepository notizRepository;

    private LieferantenDetailService service;

    @BeforeEach
    void setUp() {
        service = new LieferantenDetailService(lieferantenRepository, emailRepository,
                artikelpreisMapper, lieferantDokumentService, geschaeftsdokumentRepository, notizRepository);
    }

    private Lieferanten erstelleLieferant(Long id) {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(id);
        lieferant.setLieferantenname("Test Lieferant GmbH");
        lieferant.setIstAktiv(true);
        lieferant.setKundenEmails(List.of("lieferant@example.com"));
        lieferant.setArtikelpreise(new ArrayList<>());
        return lieferant;
    }

    private Email erstelleEmail(Long id, String subject) {
        Email email = new Email();
        email.setId(id);
        email.setSubject(subject);
        email.setFromAddress("lieferant@example.com");
        email.setRecipient("firma@example.com");
        email.setSentAt(LocalDateTime.now());
        email.setDirection(EmailDirection.IN);
        email.setBody("Test Body");
        email.setHtmlBody("<p>Test Body</p>");
        email.setAttachments(new ArrayList<>());
        email.setReplies(new ArrayList<>());
        return email;
    }

    // ═══════════════════════════════════════════════════════════════
    // loadDetails
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LoadDetails {

        @Test
        void gibtNullWennLieferantNichtGefunden() {
            when(lieferantenRepository.findById(999L)).thenReturn(Optional.empty());

            LieferantDetailDto result = service.loadDetails(999L);

            assertThat(result).isNull();
        }

        @Test
        void mapptGrunddatenKorrekt() {
            Lieferanten lieferant = erstelleLieferant(1L);
            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(emailRepository.findByLieferantIdOrderBySentAtDesc(1L)).thenReturn(List.of());
            when(lieferantDokumentService.getDokumenteByLieferant(1L, null)).thenReturn(List.of());
            when(notizRepository.findByLieferantIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

            LieferantDetailDto result = service.loadDetails(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getLieferantenname()).isEqualTo("Test Lieferant GmbH");
        }

        @Test
        void mapptEmailsMitParentEmailId() {
            Lieferanten lieferant = erstelleLieferant(1L);

            Email parentEmail = erstelleEmail(100L, "Bestellung");
            Email replyEmail = erstelleEmail(101L, "Re: Bestellung");
            replyEmail.setParentEmail(parentEmail);
            parentEmail.getReplies().add(replyEmail);

            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(emailRepository.findByLieferantIdOrderBySentAtDesc(1L))
                    .thenReturn(List.of(parentEmail, replyEmail));
            when(lieferantDokumentService.getDokumenteByLieferant(1L, null)).thenReturn(List.of());
            when(notizRepository.findByLieferantIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

            LieferantDetailDto result = service.loadDetails(1L);

            assertThat(result).isNotNull();
            List<ProjektEmailDto> emails = result.getEmails();
            assertThat(emails).hasSize(2);

            // Parent Email
            ProjektEmailDto parentDto = emails.stream()
                    .filter(e -> e.getId().equals(100L)).findFirst().orElseThrow();
            assertThat(parentDto.getParentEmailId()).isNull();
            assertThat(parentDto.getReplyCount()).isEqualTo(1);

            // Reply Email
            ProjektEmailDto replyDto = emails.stream()
                    .filter(e -> e.getId().equals(101L)).findFirst().orElseThrow();
            assertThat(replyDto.getParentEmailId()).isEqualTo(100L);
            assertThat(replyDto.getReplyCount()).isEqualTo(1); // 1 Vorfahre (parentEmail) + 0 Nachfolger = Gesamtthread 2
        }

        @Test
        void mapptEmailOhneParent() {
            Lieferanten lieferant = erstelleLieferant(1L);
            Email email = erstelleEmail(100L, "Einzelne Email");

            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(emailRepository.findByLieferantIdOrderBySentAtDesc(1L)).thenReturn(List.of(email));
            when(lieferantDokumentService.getDokumenteByLieferant(1L, null)).thenReturn(List.of());
            when(notizRepository.findByLieferantIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

            LieferantDetailDto result = service.loadDetails(1L);

            assertThat(result).isNotNull();
            ProjektEmailDto emailDto = result.getEmails().get(0);
            assertThat(emailDto.getParentEmailId()).isNull();
            assertThat(emailDto.getReplyCount()).isEqualTo(0);
        }

        @Test
        void mapptAttachmentsMitUrl() {
            Lieferanten lieferant = erstelleLieferant(1L);
            Email email = erstelleEmail(100L, "Mit Anhang");
            EmailAttachment att = new EmailAttachment();
            att.setId(500L);
            att.setOriginalFilename("rechnung.pdf");
            att.setStoredFilename("stored.pdf");
            email.getAttachments().add(att);

            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(emailRepository.findByLieferantIdOrderBySentAtDesc(1L)).thenReturn(List.of(email));
            when(lieferantDokumentService.getDokumenteByLieferant(1L, null)).thenReturn(List.of());
            when(notizRepository.findByLieferantIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

            LieferantDetailDto result = service.loadDetails(1L);

            ProjektEmailDto emailDto = result.getEmails().get(0);
            assertThat(emailDto.getAttachments()).hasSize(1);
            assertThat(emailDto.getAttachments().get(0).getOriginalFilename()).isEqualTo("rechnung.pdf");
            assertThat(emailDto.getAttachments().get(0).getUrl()).isEqualTo("/api/emails/100/attachments/500");
        }

        @Test
        void mapptDirectionUndSubjectKorrekt() {
            Lieferanten lieferant = erstelleLieferant(1L);
            Email email = erstelleEmail(100L, "Test Betreff");
            email.setDirection(EmailDirection.OUT);

            when(lieferantenRepository.findById(1L)).thenReturn(Optional.of(lieferant));
            when(emailRepository.findByLieferantIdOrderBySentAtDesc(1L)).thenReturn(List.of(email));
            when(lieferantDokumentService.getDokumenteByLieferant(1L, null)).thenReturn(List.of());
            when(notizRepository.findByLieferantIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

            LieferantDetailDto result = service.loadDetails(1L);

            ProjektEmailDto emailDto = result.getEmails().get(0);
            assertThat(emailDto.getSubject()).isEqualTo("Test Betreff");
            assertThat(emailDto.getDirection()).isEqualTo(EmailDirection.OUT);
            assertThat(emailDto.getFrom()).isEqualTo("lieferant@example.com");
            assertThat(emailDto.getTo()).isEqualTo("firma@example.com");
        }
    }
}
