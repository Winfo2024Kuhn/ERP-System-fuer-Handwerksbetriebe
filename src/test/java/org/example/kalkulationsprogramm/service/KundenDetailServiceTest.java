package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeKommunikationDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KundenDetailServiceTest {

    @Mock private KundeRepository kundeRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private DateiSpeicherService dateiSpeicherService;
    @Mock private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    private KundenDetailService service;

    @BeforeEach
    void setUp() {
        service = new KundenDetailService(kundeRepository, projektRepository, anfrageRepository,
                emailRepository, dateiSpeicherService, ausgangsGeschaeftsDokumentService);
    }

    private Kunde erstelleKunde(Long id) {
        Kunde kunde = new Kunde();
        kunde.setId(id);
        kunde.setName("Max Mustermann");
        kunde.setKundennummer("K-001");
        kunde.setStrasse("Musterstraße 1");
        kunde.setPlz("12345");
        kunde.setOrt("Musterstadt");
        kunde.setKundenEmails(List.of("test@example.com"));
        return kunde;
    }

    private Projekt erstelleProjekt(Long id, Kunde kunde) {
        Projekt projekt = new Projekt();
        projekt.setId(id);
        projekt.setKundenId(kunde);
        return projekt;
    }

    private Email erstelleEmail(Long id, Projekt projekt, String subject) {
        Email email = new Email();
        email.setId(id);
        email.setSubject(subject);
        email.setFromAddress("test@example.com");
        email.setRecipient("firma@example.com");
        email.setSentAt(LocalDateTime.now());
        email.setDirection(EmailDirection.IN);
        email.setBody("Test Body");
        email.setProjekt(projekt);
        email.setAttachments(new ArrayList<>());
        email.setReplies(new ArrayList<>());
        return email;
    }

    // ═══════════════════════════════════════════════════════════════
    // loadDetails
    // ═══════════════════════════════════════════════════════════════

    @Nested
    class LoadDetails {

        /**
         * Sets up the common mocks needed for loadDetails to work.
         * loadAnfragenForKunde uses findByProjektIdIn and findByKunde_KundennummerIgnoreCase.
         */
        private void setupAnfrageMocks(Kunde kunde, List<Projekt> projekte, List<Anfrage> anfragen) {
            List<Long> projektIds = projekte.stream().map(Projekt::getId).toList();
            if (!projektIds.isEmpty()) {
                lenient().when(anfrageRepository.findByProjektIdIn(projektIds)).thenReturn(anfragen);
            }
            lenient().when(anfrageRepository.findByKunde_KundennummerIgnoreCase(kunde.getKundennummer()))
                    .thenReturn(anfragen);
        }

        @Test
        void gibtEmptyWennKundeNichtGefunden() {
            when(kundeRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<KundeDetailDto> result = service.loadDetails(999L);

            assertThat(result).isEmpty();
        }

        @Test
        void mapptKundeGrunddaten() {
            Kunde kunde = erstelleKunde(1L);
            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of());
            setupAnfrageMocks(kunde, List.of(), List.of());

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            KundeDetailDto dto = result.get();
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getName()).isEqualTo("Max Mustermann");
            assertThat(dto.getKundennummer()).isEqualTo("K-001");
        }

        @Test
        void mapptKommunikationMitParentEmailId() {
            Kunde kunde = erstelleKunde(1L);
            Projekt projekt = erstelleProjekt(10L, kunde);

            Email parentEmail = erstelleEmail(100L, projekt, "Erste Nachricht");
            Email replyEmail = erstelleEmail(101L, projekt, "Re: Erste Nachricht");
            replyEmail.setParentEmail(parentEmail);
            parentEmail.getReplies().add(replyEmail);

            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            setupAnfrageMocks(kunde, List.of(projekt), List.of());
            when(emailRepository.findByProjektInOrderBySentAtDesc(List.of(projekt)))
                    .thenReturn(List.of(parentEmail, replyEmail));

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            List<KundeKommunikationDto> komm = result.get().getKommunikation();
            assertThat(komm).hasSize(2);

            // Parent Email
            KundeKommunikationDto parentDto = komm.stream()
                    .filter(k -> k.getId().equals(100L)).findFirst().orElseThrow();
            assertThat(parentDto.getParentEmailId()).isNull();
            assertThat(parentDto.getReplyCount()).isEqualTo(1);

            // Reply Email
            KundeKommunikationDto replyDto = komm.stream()
                    .filter(k -> k.getId().equals(101L)).findFirst().orElseThrow();
            assertThat(replyDto.getParentEmailId()).isEqualTo(100L);
            assertThat(replyDto.getReplyCount()).isEqualTo(1); // 1 Vorfahre (parentEmail) + 0 Nachfolger = Gesamtthread 2
        }

        @Test
        void mapptKommunikationOhneParent() {
            Kunde kunde = erstelleKunde(1L);
            Projekt projekt = erstelleProjekt(10L, kunde);
            Email email = erstelleEmail(100L, projekt, "Einzelne Nachricht");

            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            setupAnfrageMocks(kunde, List.of(projekt), List.of());
            when(emailRepository.findByProjektInOrderBySentAtDesc(List.of(projekt)))
                    .thenReturn(List.of(email));

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            List<KundeKommunikationDto> komm = result.get().getKommunikation();
            assertThat(komm).hasSize(1);
            assertThat(komm.get(0).getParentEmailId()).isNull();
            assertThat(komm.get(0).getReplyCount()).isEqualTo(0);
        }

        @Test
        void mapptKommunikationMitAnfrageEmails() {
            Kunde kunde = erstelleKunde(1L);
            Anfrage anfrage = new Anfrage();
            anfrage.setId(20L);
            anfrage.setKunde(kunde);

            Email email = new Email();
            email.setId(200L);
            email.setSubject("Anfrage-Email");
            email.setFromAddress("test@example.com");
            email.setRecipient("firma@example.com");
            email.setSentAt(LocalDateTime.now());
            email.setDirection(EmailDirection.IN);
            email.setAnfrage(anfrage);
            email.setAttachments(new ArrayList<>());
            email.setReplies(new ArrayList<>());

            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of());
            setupAnfrageMocks(kunde, List.of(), List.of(anfrage));
            when(emailRepository.findByAnfrageInOrderBySentAtDesc(List.of(anfrage)))
                    .thenReturn(List.of(email));

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            List<KundeKommunikationDto> komm = result.get().getKommunikation();
            assertThat(komm).hasSize(1);
            assertThat(komm.get(0).getSubject()).isEqualTo("Anfrage-Email");
            assertThat(komm.get(0).getReferenzTyp()).isEqualTo("ANFRAGE");
        }

        @Test
        void mapptAttachmentsMitDownloadUrl() {
            Kunde kunde = erstelleKunde(1L);
            Projekt projekt = erstelleProjekt(10L, kunde);
            Email email = erstelleEmail(100L, projekt, "Mit Anhang");

            EmailAttachment attachment = new EmailAttachment();
            attachment.setId(500L);
            attachment.setOriginalFilename("rechnung.pdf");
            attachment.setStoredFilename("stored-rechnung.pdf");
            email.getAttachments().add(attachment);

            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            setupAnfrageMocks(kunde, List.of(projekt), List.of());
            when(emailRepository.findByProjektInOrderBySentAtDesc(List.of(projekt)))
                    .thenReturn(List.of(email));

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            KundeKommunikationDto komm = result.get().getKommunikation().get(0);
            assertThat(komm.getAttachments()).hasSize(1);
            assertThat(komm.getAttachments().get(0).getFilename()).isEqualTo("rechnung.pdf");
        }

        @Test
        void mapptDirectionKorrekt() {
            Kunde kunde = erstelleKunde(1L);
            Projekt projekt = erstelleProjekt(10L, kunde);
            Email eingehend = erstelleEmail(100L, projekt, "Eingehend");
            eingehend.setDirection(EmailDirection.IN);
            Email ausgehend = erstelleEmail(101L, projekt, "Ausgehend");
            ausgehend.setDirection(EmailDirection.OUT);

            when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
            when(projektRepository.findByKundenId_Id(1L)).thenReturn(List.of(projekt));
            setupAnfrageMocks(kunde, List.of(projekt), List.of());
            when(emailRepository.findByProjektInOrderBySentAtDesc(List.of(projekt)))
                    .thenReturn(List.of(eingehend, ausgehend));

            Optional<KundeDetailDto> result = service.loadDetails(1L);

            assertThat(result).isPresent();
            List<KundeKommunikationDto> komm = result.get().getKommunikation();
            assertThat(komm).hasSize(2);
            assertThat(komm.stream().map(KundeKommunikationDto::getDirection))
                    .containsExactlyInAnyOrder(EmailDirection.IN, EmailDirection.OUT);
        }
    }
}
