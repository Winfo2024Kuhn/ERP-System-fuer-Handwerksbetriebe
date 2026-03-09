package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Kunde.*;
import org.example.kalkulationsprogramm.repository.AngebotRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class KundenDetailService {

    private static final Logger LOG = LoggerFactory.getLogger(KundenDetailService.class);
    private static final String QUELLE_STAMMDATEN = "STAMMDATEN";
    private static final String QUELLE_PROJEKT = "PROJEKT";
    private static final String QUELLE_ANGEBOT = "ANGEBOT";
    private static final int MAX_KOMMUNIKATION_EINTRAEGE = 250;

    private final KundeRepository kundeRepository;
    private final ProjektRepository projektRepository;
    private final AngebotRepository angebotRepository;
    private final org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    @Transactional(readOnly = true)
    public Optional<KundeDetailDto> loadDetails(Long kundenId) {
        return kundeRepository.findById(kundenId)
                .map(kunde -> {
                    List<Projekt> projekte = projektRepository.findByKundenId_Id(kunde.getId());
                    List<Angebot> angebote = loadAngeboteForKunde(kunde, projekte);
                    
                    // Lade Emails effizient über Repository
                    List<Email> projektEmails = projekte.isEmpty() ? List.of() : emailRepository.findByProjektInOrderBySentAtDesc(projekte);
                    List<Email> angebotEmails = angebote.isEmpty() ? List.of() : emailRepository.findByAngebotInOrderBySentAtDesc(angebote);
                    
                    return buildDetailDto(kunde, projekte, angebote, projektEmails, angebotEmails);
                });
    }

    private KundeDetailDto buildDetailDto(Kunde kunde, List<Projekt> projekte, List<Angebot> angebote,
                                          List<Email> projektEmails, List<Email> angebotEmails) {
        KundeDetailDto dto = new KundeDetailDto();
        dto.setId(kunde.getId());
        dto.setKundennummer(kunde.getKundennummer());
        dto.setName(kunde.getName());
        dto.setAnrede(kunde.getAnrede() != null ? kunde.getAnrede().name() : null);
        dto.setAnsprechspartner(kunde.getAnsprechspartner());
        dto.setStrasse(kunde.getStrasse());
        dto.setPlz(kunde.getPlz());
        dto.setOrt(kunde.getOrt());
        dto.setTelefon(kunde.getTelefon());
        dto.setMobiltelefon(kunde.getMobiltelefon());
        dto.setKundenEmails(new ArrayList<>(Objects.requireNonNullElse(kunde.getKundenEmails(), List.of())));
        dto.setProjekte(mapProjekte(projekte));
        dto.setAngebote(mapAngebote(angebote));
        dto.setAggregierteEmails(aggregateEmails(kunde, projekte, angebote));
        try {
            dto.setKommunikation(buildKommunikationsHistorie(projektEmails, angebotEmails));
        } catch (Exception ex) {
            LOG.warn("Kommunikationsverlauf für Kunden {} konnte nicht erzeugt werden: {}", kunde.getId(),
                    ex.getMessage(), ex);
            dto.setKommunikation(List.of());
        }
        dto.setStatistik(buildStatistik(projekte, angebote, dto.getAggregierteEmails()));
        return dto;
    }

    private List<Angebot> loadAngeboteForKunde(Kunde kunde, List<Projekt> projekte) {
        Set<Long> seenIds = new LinkedHashSet<>();
        List<Angebot> result = new ArrayList<>();
        List<Long> projektIds = Objects.requireNonNullElse(projekte, List.<Projekt>of()).stream()
                .map(Projekt::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!projektIds.isEmpty()) {
            angebotRepository.findByProjektIdIn(projektIds).forEach(angebot -> {
                if (angebot.getId() != null && seenIds.add(angebot.getId())) {
                    result.add(angebot);
                }
            });
        }
        if (StringUtils.hasText(kunde.getKundennummer())) {
            angebotRepository.findByKunde_KundennummerIgnoreCase(kunde.getKundennummer())
                    .forEach(angebot -> {
                        if (angebot.getId() != null && seenIds.add(angebot.getId())) {
                            result.add(angebot);
                        }
                    });
        }
        return result;
    }

    private List<KundeProjektKurzDto> mapProjekte(List<Projekt> projekte) {
        if (projekte == null) {
            return List.of();
        }
        return projekte.stream()
                .sorted(Comparator.comparing(
                        (Projekt p) -> p.getAbschlussdatum() != null ? p.getAbschlussdatum() : p.getAnlegedatum(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(projekt -> {
                    KundeProjektKurzDto dto = new KundeProjektKurzDto();
                    dto.setId(projekt.getId());
                    dto.setBauvorhaben(projekt.getBauvorhaben());
                    dto.setAuftragsnummer(projekt.getAuftragsnummer());
                    dto.setAnlegedatum(projekt.getAnlegedatum());
                    dto.setAbschlussdatum(projekt.getAbschlussdatum());
                    dto.setBezahlt(projekt.isBezahlt());
                    dto.setBruttoPreis(projekt.getBruttoPreis());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<KundeAngebotKurzDto> mapAngebote(List<Angebot> angebote) {
        if (angebote == null) {
            return List.of();
        }
        return angebote.stream()
                .sorted(Comparator.comparing(Angebot::getAnlegedatum, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(angebot -> {
                    KundeAngebotKurzDto dto = new KundeAngebotKurzDto();
                    dto.setId(angebot.getId());
                    dto.setBauvorhaben(angebot.getBauvorhaben());
                    dto.setAngebotsnummer(resolveAngebotsnummer(angebot));
                    dto.setAnlegedatum(angebot.getAnlegedatum());
                    dto.setBetrag(angebot.getBetrag());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String resolveAngebotsnummer(Angebot angebot) {
        // Priorität: AusgangsGeschaeftsDokument (neues System)
        String nummerNeu = ausgangsGeschaeftsDokumentService.resolveAngebotsnummer(angebot.getId());
        if (nummerNeu != null) {
            return nummerNeu;
        }
        // Fallback: altes System (AngebotGeschaeftsdokument)
        if (angebot.getDokumente() == null) {
            return null;
        }
        return angebot.getDokumente().stream()
                .filter(AngebotGeschaeftsdokument.class::isInstance)
                .map(AngebotGeschaeftsdokument.class::cast)
                .filter(doc -> doc.getGeschaeftsdokumentart() != null &&
                        doc.getGeschaeftsdokumentart().toLowerCase(Locale.GERMAN).contains("angebot"))
                .map(AngebotGeschaeftsdokument::getDokumentid)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<KundeAggregierteEmailDto> aggregateEmails(Kunde kunde, List<Projekt> projekte,
            List<Angebot> angebote) {
        Map<String, KundeAggregierteEmailDto> map = new LinkedHashMap<>();
        addEmails(map, Objects.requireNonNullElse(kunde.getKundenEmails(), List.of()),
                QUELLE_STAMMDATEN, kunde.getId(), "Stammdaten", true);
        for (Projekt projekt : Objects.requireNonNullElse(projekte, List.<Projekt>of())) {
            List<String> emails = Objects.requireNonNullElse(projekt.getKundenEmails(), List.of());
            String beschreibung = buildProjektBeschreibung(projekt);
            addEmails(map, emails, QUELLE_PROJEKT, projekt.getId(), beschreibung, false);
        }
        for (Angebot angebot : Objects.requireNonNullElse(angebote, List.<Angebot>of())) {
            List<String> emails = new ArrayList<>();
            if (angebot.getKunde() != null && angebot.getKunde().getKundenEmails() != null) {
                emails.addAll(angebot.getKunde().getKundenEmails());
            }
            if (angebot.getKundenEmails() != null) {
                emails.addAll(angebot.getKundenEmails());
            }
            String beschreibung = buildAngebotBeschreibung(angebot);
            addEmails(map, emails, QUELLE_ANGEBOT, angebot.getId(), beschreibung, false);
        }
        return new ArrayList<>(map.values());
    }

    private void addEmails(Map<String, KundeAggregierteEmailDto> map,
            List<String> emails,
            String typ,
            Long referenzId,
            String beschreibung,
            boolean markAsMaster) {
        for (String email : emails) {
            if (!StringUtils.hasText(email)) {
                continue;
            }
            String normalized = email.trim().toLowerCase(Locale.GERMAN);
            KundeAggregierteEmailDto aggregate = map.computeIfAbsent(normalized, k -> {
                KundeAggregierteEmailDto dto = new KundeAggregierteEmailDto();
                dto.setEmail(email.trim());
                dto.setQuellen(new ArrayList<>());
                return dto;
            });
            KundeEmailQuelleDto quelle = new KundeEmailQuelleDto();
            quelle.setTyp(typ);
            quelle.setReferenzId(referenzId);
            quelle.setBeschreibung(beschreibung);
            aggregate.getQuellen().add(quelle);
            if (markAsMaster) {
                aggregate.setAusStammdaten(true);
            }
        }
    }

    private String buildProjektBeschreibung(Projekt projekt) {
        if (projekt == null) {
            return "Projekt";
        }
        if (StringUtils.hasText(projekt.getAuftragsnummer())) {
            return "Projekt " + projekt.getAuftragsnummer();
        }
        if (StringUtils.hasText(projekt.getBauvorhaben())) {
            return projekt.getBauvorhaben();
        }
        return "Projekt #" + projekt.getId();
    }

    private String buildAngebotBeschreibung(Angebot angebot) {
        if (angebot == null) {
            return "Angebot";
        }
        String nummer = resolveAngebotsnummer(angebot);
        if (StringUtils.hasText(nummer)) {
            return "Angebot " + nummer;
        }
        if (StringUtils.hasText(angebot.getBauvorhaben())) {
            return angebot.getBauvorhaben();
        }
        return "Angebot #" + angebot.getId();
    }

    private List<KundeKommunikationDto> buildKommunikationsHistorie(List<Email> projektEmails, List<Email> angebotEmails) {
        List<KundeKommunikationDto> timeline = new ArrayList<>();
        
        for (Email email : projektEmails) {
             String referenzName = buildProjektBeschreibung(email.getProjekt());
             timeline.add(createKommunikationDto(email, QUELLE_PROJEKT, email.getProjekt().getId(), referenzName));
        }
        
        for (Email email : angebotEmails) {
             String referenzName = buildAngebotBeschreibung(email.getAngebot());
             timeline.add(createKommunikationDto(email, QUELLE_ANGEBOT, email.getAngebot().getId(), referenzName));
        }

        timeline.sort(Comparator.comparing(
                (KundeKommunikationDto dto) -> Optional.ofNullable(dto.getZeitpunkt()).orElse(LocalDateTime.MIN))
                .reversed());
        if (timeline.size() > MAX_KOMMUNIKATION_EINTRAEGE) {
            return new ArrayList<>(timeline.subList(0, MAX_KOMMUNIKATION_EINTRAEGE));
        }
        return timeline;
    }

    private KundeKommunikationDto createKommunikationDto(Email email, String referenzTyp, Long referenzId, String referenzName) {
        KundeKommunikationDto dto = new KundeKommunikationDto();
        dto.setId(email.getId());
        dto.setReferenzId(referenzId);
        dto.setReferenzTyp(referenzTyp);
        dto.setReferenzName(referenzName);
        dto.setSubject(email.getSubject());
        dto.setAbsender(email.getFromAddress());
        dto.setEmpfaenger(combineRecipients(email.getRecipient(), email.getCc()));
        dto.setZeitpunkt(email.getSentAt());
        dto.setDirection(email.getDirection());
        dto.setSnippet(buildSnippet(email.getHtmlBody(), email.getBody()));
        dto.setBody(extractPlainText(email.getHtmlBody(), email.getBody()));
        dto.setAttachments(mapAttachments(email.getAttachments(), referenzTyp, referenzId, email.getId()));
        return dto;
    }

    private String combineRecipients(String recipient, String cc) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(recipient)) {
            parts.add(recipient.trim());
        }
        if (StringUtils.hasText(cc)) {
            parts.add("CC: " + cc.trim());
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" | ", parts);
    }

    private String buildSnippet(String html, String fallbackText) {
        String text = null;
        if (StringUtils.hasText(html)) {
            text = Jsoup.parse(html).text();
        }
        if (!StringUtils.hasText(text) && StringUtils.hasText(fallbackText)) {
            text = fallbackText;
        }
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String condensed = text.trim().replaceAll("\\s+", " ");
        if (condensed.length() > 220) {
            return condensed.substring(0, 217).trim() + "…";
        }
        return condensed;
    }

    private List<KundeEmailAttachmentDto> mapAttachments(List<EmailAttachment> attachments, String referenzTyp, Long referenzId, Long emailId) {
        if (attachments == null) return List.of();
        return attachments.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getInlineAttachment()))
                .map(f -> {
                    KundeEmailAttachmentDto dto = new KundeEmailAttachmentDto();
                    dto.setId(f.getId());
                    dto.setFilename(f.getOriginalFilename());
                    // Construct URL dependent on type, or generic
                    String pathSegment = referenzTyp.equals(QUELLE_PROJEKT) ? "projekte" : "angebote";
                    // Using unified API would be better: /api/emails/{id}/attachments/{attId}
                    // But sticking to legacy structure if needed, or using Unified Controller:
                    // Let's use unified:
                    dto.setUrl("/api/emails/" + emailId + "/attachments/" + f.getId());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String extractPlainText(String html, String fallbackText) {
        String text = null;
        if (StringUtils.hasText(html)) {
            text = Jsoup.parse(html).text();
        }
        if (!StringUtils.hasText(text) && StringUtils.hasText(fallbackText)) {
            text = fallbackText;
        }
        return text;
    }

    private KundeStatistikDto buildStatistik(List<Projekt> projekte,
            List<Angebot> angebote,
            List<KundeAggregierteEmailDto> aggregierteEmails) {
        KundeStatistikDto statistik = new KundeStatistikDto();
        statistik.setProjektAnzahl(Objects.requireNonNullElse(projekte, List.of()).size());
        statistik.setAngebotAnzahl(Objects.requireNonNullElse(angebote, List.of()).size());
        statistik.setEmailAdresseAnzahl(Objects.requireNonNullElse(aggregierteEmails, List.of()).size());
        statistik.setLetzteAktivitaet(resolveLetzteAktivitaet(projekte, angebote));
        statistik.setGesamtUmsatz(berechneGesamtUmsatz(projekte));
        statistik.setGesamtGewinn(berechneGesamtGewinn(projekte));
        return statistik;
    }

    private BigDecimal berechneGesamtGewinn(List<Projekt> projekte) {
        BigDecimal umsatz = berechneGesamtUmsatz(projekte);
        double kosten = Objects.requireNonNullElse(projekte, List.<Projekt>of()).stream()
                .mapToDouble(dateiSpeicherService::berechneProjektKosten)
                .sum();
        return umsatz.subtract(BigDecimal.valueOf(kosten));
    }

    private BigDecimal berechneGesamtUmsatz(List<Projekt> projekte) {
        return Objects.requireNonNullElse(projekte, List.<Projekt>of()).stream()
                .map(Projekt::getBruttoPreis)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate resolveLetzteAktivitaet(List<Projekt> projekte, List<Angebot> angebote) {
        LocalDate letzteProjektAktivitaet = Objects
                .requireNonNullElse(projekte, List.<Projekt>of())
                .stream()
                .filter(Objects::nonNull)
                .flatMap(p -> Stream.of(p.getAbschlussdatum(), p.getAnlegedatum()))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        LocalDate letztesAngebot = Objects
                .requireNonNullElse(angebote, List.<Angebot>of())
                .stream()
                .filter(Objects::nonNull)
                .map(Angebot::getAnlegedatum)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return Stream.of(letzteProjektAktivitaet, letztesAngebot)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
