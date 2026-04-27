package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.LieferantNotiz;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantEmailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantNotizDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantStatistikDto;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.example.kalkulationsprogramm.dto.Lieferant.LieferantAttachmentViewDto;

import org.example.kalkulationsprogramm.dto.Lieferant.LieferantKommunikationDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailFileDto;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;

@Service
@RequiredArgsConstructor
public class LieferantenDetailService {

    private static final int EMAIL_LIMIT_DEFAULT = 50;

    private final LieferantenRepository lieferantenRepository;
    private final org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    private final LieferantArtikelpreisMapper artikelpreisMapper;
    private final LieferantDokumentService lieferantDokumentService;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final LieferantNotizRepository notizRepository;

    @Transactional(readOnly = true)
    public LieferantDetailDto loadDetails(Long id) {
        Lieferanten lieferant = lieferantenRepository.findById(id)
                .orElse(null);
        if (lieferant == null) {
            return null;
        }
        LieferantDetailDto dto = new LieferantDetailDto();
        dto.setId(lieferant.getId());
        dto.setLieferantenname(lieferant.getLieferantenname());
        dto.setEigeneKundennummer(lieferant.getEigeneKundennummer());
        dto.setLieferantenTyp(lieferant.getLieferantenTyp());
        dto.setVertreter(lieferant.getVertreter());
        dto.setStrasse(lieferant.getStrasse());
        dto.setPlz(lieferant.getPlz());
        dto.setOrt(lieferant.getOrt());
        dto.setTelefon(lieferant.getTelefon());
        dto.setMobiltelefon(lieferant.getMobiltelefon());
        dto.setIstAktiv(lieferant.getIstAktiv());
        dto.setStartZusammenarbeit(toLocalDate(lieferant.getStartZusammenarbeit()));
        dto.setKundenEmails(new ArrayList<>(Objects.requireNonNullElse(lieferant.getKundenEmails(), List.of())));
        if (lieferant.getStandardKostenstelle() != null) {
            dto.setStandardKostenstelleId(lieferant.getStandardKostenstelle().getId());
            dto.setStandardKostenstelleName(lieferant.getStandardKostenstelle().getBezeichnung());
        }

        dto.setArtikelpreise(artikelpreisMapper.toDtoList(lieferant.getArtikelpreise()));
        dto.setStatistik(buildStatistik(lieferant));

        // Map Emails to Kommunikation (Unified Email)
        List<org.example.kalkulationsprogramm.domain.Email> emails = loadEmailsEntities(id, EMAIL_LIMIT_DEFAULT);
        dto.setKommunikation(emails.stream().map(e -> toKommunikation(e, id)).toList());

        // Also populate emails field for unified EmailsTab in frontend
        dto.setEmails(emails.stream().map(this::toProjektEmailDto).toList());

        // Lade Dokumente
        dto.setDokumente(lieferantDokumentService.getDokumenteByLieferant(id, null));

        // Lade Notizen
        dto.setNotizen(notizRepository.findByLieferantIdOrderByErstelltAmDesc(id)
                .stream().map(this::toNotizDto).toList());

        return dto;
    }

    private LieferantNotizDto toNotizDto(LieferantNotiz notiz) {
        LieferantNotizDto dto = new LieferantNotizDto();
        dto.setId(notiz.getId());
        dto.setText(notiz.getText());
        dto.setErstelltAm(notiz.getErstelltAm());
        return dto;
    }

    private LieferantKommunikationDto toKommunikation(org.example.kalkulationsprogramm.domain.Email email,
            Long lieferantId) {
        LieferantKommunikationDto dto = new LieferantKommunikationDto();
        dto.setId(email.getId());
        dto.setReferenzId(lieferantId);
        dto.setReferenzTyp("LIEFERANT");
        dto.setReferenzName("Lieferant");
        dto.setSubject(email.getSubject());
        dto.setAbsender(email.getFromAddress());
        dto.setEmpfaenger(email.getRecipient());
        dto.setZeitpunkt(email.getSentAt());
        dto.setDirection(email.getDirection());

        String body = email.getBody(); // Plain text body preferred for snippet
        if (body == null)
            body = "";
        String textOnly = body.trim(); // Already plain text usually
        dto.setSnippet(textOnly.length() > 100 ? textOnly.substring(0, 100) + "..." : textOnly);
        dto.setBody(email.getHtmlBody() != null ? email.getHtmlBody() : email.getBody());

        if (email.getAttachments() != null) {
            dto.setAttachments(email.getAttachments().stream()
                    .map(a -> toAttachmentView(a, lieferantId, email.getId()))
                    .toList());
        } else {
            dto.setAttachments(List.of());
        }
        return dto;
    }

    private LieferantAttachmentViewDto toAttachmentView(org.example.kalkulationsprogramm.domain.EmailAttachment att,
            Long lieferantId, Long emailId) {
        LieferantAttachmentViewDto dto = new LieferantAttachmentViewDto();
        dto.setId(att.getId());
        dto.setFilename(att.getOriginalFilename());
        // Generate URL: /api/emails/{emailId}/attachments/{attachmentId}
        // Unified Controller endpoint format
        dto.setUrl("/api/emails/" + emailId + "/attachments/" + att.getId());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<LieferantEmailDto> loadEmails(Long lieferantId, int limit, String query) {
        // This method returned old DTOs, but maybe we can adapt or remove it if not
        // used elsewhere?
        // It is used by Controller. So we map entities to old DTO if needed or update
        // Controller.
        // Let's implement it mapping Email -> LieferantEmailDto for compatibility
        var entities = loadEmailsEntities(lieferantId, limit);
        // Filter by query if needed? Repository search would be better.
        // For now simple list
        return entities.stream().map(this::toEmailDto).toList();
    }

    private List<org.example.kalkulationsprogramm.domain.Email> loadEmailsEntities(Long lieferantId, int limit) {
        // Need to add findByLieferantId to EmailRepository or use existing methods
        // Assuming findByLieferantIdDesc or similar
        // For now: placeholder query logic using existing repo if possible
        return emailRepository.findByLieferantIdOrderBySentAtDesc(lieferantId);
    }

    private LieferantEmailDto toEmailDto(org.example.kalkulationsprogramm.domain.Email email) {
        LieferantEmailDto dto = new LieferantEmailDto();
        dto.setId(email.getId());
        dto.setSubject(email.getSubject());
        dto.setFrom(email.getFromAddress());
        dto.setTo(email.getRecipient());
        dto.setSentAt(email.getSentAt());
        dto.setDirection(email.getDirection());
        dto.setBodyHtml(email.getHtmlBody());
        return dto;
    }

    /**
     * Maps Email entity to ProjektEmailDto for unified EmailsTab in frontend.
     */
    private ProjektEmailDto toProjektEmailDto(org.example.kalkulationsprogramm.domain.Email email) {
        ProjektEmailDto dto = new ProjektEmailDto();
        dto.setId(email.getId());
        dto.setDirection(email.getDirection());
        dto.setFrom(email.getFromAddress());
        dto.setTo(email.getRecipient());
        dto.setSubject(email.getSubject());
        dto.setSentAt(email.getSentAt());
        dto.setBodyHtml(email.getHtmlBody() != null ? email.getHtmlBody() : email.getBody());

        // Thread-Info
        dto.setParentEmailId(email.getParentEmail() != null ? email.getParentEmail().getId() : null);
        dto.setReplyCount(countAncestors(email) + countAllReplies(email));

        if (email.getAttachments() != null) {
            final Long emailId = email.getId();
            dto.setAttachments(email.getAttachments().stream()
                    .map(att -> {
                        ProjektEmailFileDto fileDto = new ProjektEmailFileDto();
                        fileDto.setId(att.getId());
                        fileDto.setOriginalFilename(att.getOriginalFilename());
                        fileDto.setStoredFilename(att.getStoredFilename());
                        fileDto.setContentId(att.getContentId());
                        fileDto.setInline(att.isInline());
                        // Download-URL setzen
                        fileDto.setUrl("/api/emails/" + emailId + "/attachments/" + att.getId());
                        return fileDto;
                    })
                    .toList());
        }
        return dto;
    }

    private int countAllReplies(org.example.kalkulationsprogramm.domain.Email email) {
        if (email.getReplies() == null || email.getReplies().isEmpty()) return 0;
        int count = email.getReplies().size();
        for (org.example.kalkulationsprogramm.domain.Email reply : email.getReplies()) {
            count += countAllReplies(reply);
        }
        return count;
    }

    private int countAncestors(org.example.kalkulationsprogramm.domain.Email email) {
        int count = 0;
        java.util.Set<Long> visited = new java.util.HashSet<>();
        org.example.kalkulationsprogramm.domain.Email current = email.getParentEmail();
        while (current != null && !visited.contains(current.getId())) {
            visited.add(current.getId());
            count++;
            current = current.getParentEmail();
        }
        return count;
    }

    private LieferantStatistikDto buildStatistik(Lieferanten lieferant) {
        LieferantStatistikDto statistik = new LieferantStatistikDto();
        statistik.setArtikelAnzahl(
                (int) Objects.requireNonNullElse(lieferant.getArtikelpreise(), List.<LieferantenArtikelPreise>of())
                        .stream()
                        .map(LieferantenArtikelPreise::getArtikel)
                        .filter(Objects::nonNull)
                        .map(a -> a.getId() != null ? a.getId() : -1L)
                        .distinct()
                        .count());
        long emailCount = emailRepository.countByLieferantId(lieferant.getId());
        statistik.setEmailAnzahl(emailCount);
        // last email sent at?
        var last = emailRepository.findFirstByLieferantIdOrderBySentAtDesc(lieferant.getId());
        last.ifPresent(e -> statistik.setLetzteEmail(e.getSentAt()));

        List<String> domains = Objects.requireNonNullElse(lieferant.getKundenEmails(), List.<String>of())
                .stream()
                .map(this::extractDomain)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        statistik.setEmailDomains(domains);

        // Berechne Bestellungen (Auftragsbestätigungen), Lieferzeit und Gesamtkosten
        try {
            Long bestellungen = geschaeftsdokumentRepository.countBestellungenByLieferantId(lieferant.getId());
            if (bestellungen != null) {
                statistik.setBestellungAnzahl(bestellungen.intValue());
            }

            Double avgLieferzeit = geschaeftsdokumentRepository
                    .calculateAverageLieferzeitByLieferantId(lieferant.getId());
            if (avgLieferzeit != null && avgLieferzeit > 0) {
                statistik.setLieferzeit(avgLieferzeit.intValue());
            }

            Double gesamtKosten = geschaeftsdokumentRepository.sumGesamtkostenByLieferantId(lieferant.getId());
            if (gesamtKosten != null) {
                statistik.setGesamtKosten(gesamtKosten);
            }
        } catch (Exception e) {
            System.err.println("[LieferantenDetailService] Fehler bei Statistik-Berechnung: " + e.getMessage());
        }

        return statistik;
    }

    private LocalDate toLocalDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String extractDomain(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at < 0 || at == normalized.length() - 1) {
            return null;
        }
        return normalized.substring(at + 1);
    }
}
