package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Kunde.*;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
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
    private static final String QUELLE_ANFRAGE = "ANFRAGE";
    private static final int MAX_KOMMUNIKATION_EINTRAEGE = 250;

    private final KundeRepository kundeRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    @Transactional(readOnly = true)
    public Optional<KundeDetailDto> loadDetails(Long kundenId) {
        return kundeRepository.findById(kundenId)
                .map(kunde -> {
                    List<Projekt> projekte = projektRepository.findByKundenId_Id(kunde.getId());
                    List<Anfrage> anfragen = loadAnfragenForKunde(kunde, projekte);
                    
                    // Lade Emails effizient über Repository
                    List<Email> projektEmails = projekte.isEmpty() ? List.of() : emailRepository.findByProjektInOrderBySentAtDesc(projekte);
                    List<Email> anfrageEmails = anfragen.isEmpty() ? List.of() : emailRepository.findByAnfrageInOrderBySentAtDesc(anfragen);
                    
                    return buildDetailDto(kunde, projekte, anfragen, projektEmails, anfrageEmails);
                });
    }

    private KundeDetailDto buildDetailDto(Kunde kunde, List<Projekt> projekte, List<Anfrage> anfragen,
                                          List<Email> projektEmails, List<Email> anfrageEmails) {
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
        dto.setAnfragen(mapAnfragen(anfragen));
        dto.setAggregierteEmails(aggregateEmails(kunde, projekte, anfragen));
        try {
            dto.setKommunikation(buildKommunikationsHistorie(projektEmails, anfrageEmails));
        } catch (Exception ex) {
            LOG.warn("Kommunikationsverlauf für Kunden {} konnte nicht erzeugt werden: {}", kunde.getId(),
                    ex.getMessage(), ex);
            dto.setKommunikation(List.of());
        }
        dto.setStatistik(buildStatistik(projekte, anfragen, dto.getAggregierteEmails()));
        return dto;
    }

    private List<Anfrage> loadAnfragenForKunde(Kunde kunde, List<Projekt> projekte) {
        Set<Long> seenIds = new LinkedHashSet<>();
        List<Anfrage> result = new ArrayList<>();
        List<Long> projektIds = Objects.requireNonNullElse(projekte, List.<Projekt>of()).stream()
                .map(Projekt::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!projektIds.isEmpty()) {
            anfrageRepository.findByProjektIdIn(projektIds).forEach(anfrage -> {
                if (anfrage.getId() != null && seenIds.add(anfrage.getId())) {
                    result.add(anfrage);
                }
            });
        }
        if (StringUtils.hasText(kunde.getKundennummer())) {
            anfrageRepository.findByKunde_KundennummerIgnoreCase(kunde.getKundennummer())
                    .forEach(anfrage -> {
                        if (anfrage.getId() != null && seenIds.add(anfrage.getId())) {
                            result.add(anfrage);
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

    private List<KundeAnfrageKurzDto> mapAnfragen(List<Anfrage> anfragen) {
        if (anfragen == null) {
            return List.of();
        }
        return anfragen.stream()
                .sorted(Comparator.comparing(Anfrage::getAnlegedatum, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(anfrage -> {
                    KundeAnfrageKurzDto dto = new KundeAnfrageKurzDto();
                    dto.setId(anfrage.getId());
                    dto.setBauvorhaben(anfrage.getBauvorhaben());
                    dto.setAnfragesnummer(resolveAnfragesnummer(anfrage));
                    dto.setAnlegedatum(anfrage.getAnlegedatum());
                    dto.setBetrag(anfrage.getBetrag());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private String resolveAnfragesnummer(Anfrage anfrage) {
        // Priorität: AusgangsGeschaeftsDokument (neues System)
        String nummerNeu = ausgangsGeschaeftsDokumentService.resolveAnfragesnummer(anfrage.getId());
        if (nummerNeu != null) {
            return nummerNeu;
        }
        // Fallback: altes System (AnfrageGeschaeftsdokument)
        if (anfrage.getDokumente() == null) {
            return null;
        }
        return anfrage.getDokumente().stream()
                .filter(AnfrageGeschaeftsdokument.class::isInstance)
                .map(AnfrageGeschaeftsdokument.class::cast)
                .filter(doc -> doc.getGeschaeftsdokumentart() != null &&
                        doc.getGeschaeftsdokumentart().toLowerCase(Locale.GERMAN).contains("angebot"))
                .map(AnfrageGeschaeftsdokument::getDokumentid)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<KundeAggregierteEmailDto> aggregateEmails(Kunde kunde, List<Projekt> projekte,
            List<Anfrage> anfragen) {
        Map<String, KundeAggregierteEmailDto> map = new LinkedHashMap<>();
        addEmails(map, Objects.requireNonNullElse(kunde.getKundenEmails(), List.of()),
                QUELLE_STAMMDATEN, kunde.getId(), "Stammdaten", true);
        for (Projekt projekt : Objects.requireNonNullElse(projekte, List.<Projekt>of())) {
            List<String> emails = Objects.requireNonNullElse(projekt.getKundenEmails(), List.of());
            String beschreibung = buildProjektBeschreibung(projekt);
            addEmails(map, emails, QUELLE_PROJEKT, projekt.getId(), beschreibung, false);
        }
        for (Anfrage anfrage : Objects.requireNonNullElse(anfragen, List.<Anfrage>of())) {
            List<String> emails = new ArrayList<>();
            if (anfrage.getKunde() != null && anfrage.getKunde().getKundenEmails() != null) {
                emails.addAll(anfrage.getKunde().getKundenEmails());
            }
            if (anfrage.getKundenEmails() != null) {
                emails.addAll(anfrage.getKundenEmails());
            }
            String beschreibung = buildAnfrageBeschreibung(anfrage);
            addEmails(map, emails, QUELLE_ANFRAGE, anfrage.getId(), beschreibung, false);
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

    private String buildAnfrageBeschreibung(Anfrage anfrage) {
        if (anfrage == null) {
            return "Anfrage";
        }
        String nummer = resolveAnfragesnummer(anfrage);
        if (StringUtils.hasText(nummer)) {
            return "Anfrage " + nummer;
        }
        if (StringUtils.hasText(anfrage.getBauvorhaben())) {
            return anfrage.getBauvorhaben();
        }
        return "Anfrage #" + anfrage.getId();
    }

    private List<KundeKommunikationDto> buildKommunikationsHistorie(List<Email> projektEmails, List<Email> anfrageEmails) {
        List<KundeKommunikationDto> timeline = new ArrayList<>();
        
        for (Email email : projektEmails) {
             String referenzName = buildProjektBeschreibung(email.getProjekt());
             timeline.add(createKommunikationDto(email, QUELLE_PROJEKT, email.getProjekt().getId(), referenzName));
        }
        
        for (Email email : anfrageEmails) {
             String referenzName = buildAnfrageBeschreibung(email.getAnfrage());
             timeline.add(createKommunikationDto(email, QUELLE_ANFRAGE, email.getAnfrage().getId(), referenzName));
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
                    String pathSegment = referenzTyp.equals(QUELLE_PROJEKT) ? "projekte" : "anfragen";
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
            List<Anfrage> anfragen,
            List<KundeAggregierteEmailDto> aggregierteEmails) {
        KundeStatistikDto statistik = new KundeStatistikDto();
        statistik.setProjektAnzahl(Objects.requireNonNullElse(projekte, List.of()).size());
        statistik.setAnfrageAnzahl(Objects.requireNonNullElse(anfragen, List.of()).size());
        statistik.setEmailAdresseAnzahl(Objects.requireNonNullElse(aggregierteEmails, List.of()).size());
        statistik.setLetzteAktivitaet(resolveLetzteAktivitaet(projekte, anfragen));
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

    private LocalDate resolveLetzteAktivitaet(List<Projekt> projekte, List<Anfrage> anfragen) {
        LocalDate letzteProjektAktivitaet = Objects
                .requireNonNullElse(projekte, List.<Projekt>of())
                .stream()
                .filter(Objects::nonNull)
                .flatMap(p -> Stream.of(p.getAbschlussdatum(), p.getAnlegedatum()))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        LocalDate letztesAnfrage = Objects
                .requireNonNullElse(anfragen, List.<Anfrage>of())
                .stream()
                .filter(Objects::nonNull)
                .map(Anfrage::getAnlegedatum)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return Stream.of(letzteProjektAktivitaet, letztesAnfrage)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
