package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantNotiz;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantNotizDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantStatistikDto;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
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

import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;

@Service
@RequiredArgsConstructor
public class LieferantenDetailService {

    private final LieferantenRepository lieferantenRepository;
    private final org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    private final EmailReiterRepository emailReiterRepository;
    private final LieferantArtikelpreisMapper artikelpreisMapper;
    private final LieferantDokumentService lieferantDokumentService;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final LieferantNotizRepository notizRepository;
    private final LieferantenArtikelPreiseRepository artikelpreisRepository;

    /** Alles auf einmal – für Aufrufer, die den kompletten Lieferanten brauchen (z. B. Bestellungen). */
    @Transactional(readOnly = true)
    public LieferantDetailDto loadDetails(Long id) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return null;
        }
        LieferantDetailDto dto = baueStammdaten(lieferant);
        dto.setArtikelpreise(artikelpreisMapper.toDtoList(lieferant.getArtikelpreise()));
        dto.setStatistik(buildStatistik(lieferant));

        dto.setEmailAnzahl(emailReiterRepository.anzahlVerlaeufe(EmailBezug.LIEFERANT, id));

        List<LieferantDokumentDto.Response> dokumente = lieferantDokumentService.getDokumenteByLieferant(id, null);
        dto.setDokumente(dokumente);
        dto.setDokumenteAnzahl((long) dokumente.size());

        dto.setNotizen(notizRepository.findByLieferantIdOrderByErstelltAmDesc(id)
                .stream().map(this::toNotizDto).toList());
        dto.setNotizenAnzahl((long) dto.getNotizen().size());
        return dto;
    }

    /**
     * Nur Stammdaten und Zähler für die Reiter. Die Detailseite zeigt das sofort an und
     * holt Kennzahlen, E-Mails ({@code GET /api/emails/reiter/lieferant/{id}}), Notizen und Dokumente danach einzeln nach – ein
     * Lieferant wie ein Großhändler hat sonst tausende Artikelpreise und E-Mails im Gepäck.
     */
    @Transactional(readOnly = true)
    public LieferantDetailDto loadStammdaten(Long id) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return null;
        }
        LieferantDetailDto dto = baueStammdaten(lieferant);
        dto.setEmailAnzahl(emailReiterRepository.anzahlVerlaeufe(EmailBezug.LIEFERANT, id));
        dto.setDokumenteAnzahl(lieferantDokumentService.zaehleDokumente(id));
        dto.setNotizenAnzahl(notizRepository.countByLieferantId(id));
        return dto;
    }

    /** Kennzahlen für den Kopf der Detailseite; {@code null}, wenn es den Lieferanten nicht gibt. */
    @Transactional(readOnly = true)
    public LieferantStatistikDto loadStatistik(Long id) {
        return lieferantenRepository.findById(id).map(this::buildStatistik).orElse(null);
    }

    private LieferantDetailDto baueStammdaten(Lieferanten lieferant) {
        LieferantDetailDto dto = new LieferantDetailDto();
        dto.setId(lieferant.getId());
        dto.setLieferantenname(lieferant.getLieferantenname());
        dto.setAliasName(lieferant.getAliasName());
        dto.setEigeneKundennummer(lieferant.getEigeneKundennummer());
        dto.setLieferantenTyp(lieferant.getLieferantenTyp());
        dto.setRollen(lieferant.getRollen());
        dto.setVertreter(lieferant.getVertreter());
        dto.setStrasse(lieferant.getStrasse());
        dto.setPlz(lieferant.getPlz());
        dto.setOrt(lieferant.getOrt());
        dto.setTelefon(lieferant.getTelefon());
        dto.setMobiltelefon(lieferant.getMobiltelefon());
        dto.setIstAktiv(lieferant.getIstAktiv());
        dto.setStartZusammenarbeit(toLocalDate(lieferant.getStartZusammenarbeit()));
        dto.setKundenEmails(new ArrayList<>(Objects.requireNonNullElse(lieferant.getKundenEmails(), List.of())));
        dto.setVorauskasse(Boolean.TRUE.equals(lieferant.getVorauskasse()));
        if (lieferant.getStandardKostenstelle() != null) {
            dto.setStandardKostenstelleId(lieferant.getStandardKostenstelle().getId());
            dto.setStandardKostenstelleName(lieferant.getStandardKostenstelle().getBezeichnung());
        }
        return dto;
    }

    private LieferantNotizDto toNotizDto(LieferantNotiz notiz) {
        LieferantNotizDto dto = new LieferantNotizDto();
        dto.setId(notiz.getId());
        dto.setText(notiz.getText());
        dto.setErstelltAm(notiz.getErstelltAm());
        return dto;
    }

    private LieferantStatistikDto buildStatistik(Lieferanten lieferant) {
        LieferantStatistikDto statistik = new LieferantStatistikDto();
        statistik.setArtikelAnzahl((int) artikelpreisRepository.zaehleArtikelVonLieferant(lieferant.getId()));
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
