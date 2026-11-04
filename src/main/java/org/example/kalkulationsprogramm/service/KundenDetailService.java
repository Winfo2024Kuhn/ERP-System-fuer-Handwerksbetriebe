package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Kunde.*;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Kundenakte ({@code GET /api/kunden/{id}}): Stammdaten, Projekte, Anfragen, Dokumente,
 * Notizen und die Zahl der E-Mail-Verläufe. Die Mails selbst lädt der E-Mail-Reiter seitenweise
 * ({@code GET /api/emails/reiter/kunde/{id}}).
 */
@Service
@RequiredArgsConstructor
public class KundenDetailService {

    private static final String QUELLE_STAMMDATEN = "STAMMDATEN";
    private static final String QUELLE_PROJEKT = "PROJEKT";
    private static final String QUELLE_ANFRAGE = "ANFRAGE";

    private final KundeRepository kundeRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final EmailReiterRepository emailReiterRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;
    private final KundeNotizRepository kundeNotizRepository;

    @Transactional(readOnly = true)
    public Optional<KundeDetailDto> loadDetails(Long kundenId) {
        return kundeRepository.findById(kundenId)
                .map(kunde -> {
                    List<Projekt> projekte = projektRepository.findByKundenId_Id(kunde.getId());
                    List<Anfrage> anfragen = loadAnfragenForKunde(kunde, projekte);
                    return buildDetailDto(kunde, projekte, anfragen);
                });
    }

    private KundeDetailDto buildDetailDto(Kunde kunde, List<Projekt> projekte, List<Anfrage> anfragen) {
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
        dto.setZahlungsziel(kunde.getZahlungsziel());
        dto.setKundenEmails(new ArrayList<>(Objects.requireNonNullElse(kunde.getKundenEmails(), List.of())));
        dto.setGesperrt(kunde.istGesperrt());
        dto.setGesperrtAm(kunde.getGesperrtAm());
        dto.setAufbewahrenBis(kunde.getAufbewahrenBis());
        dto.setAnonymisiertAm(kunde.getAnonymisiertAm());
        dto.setProjekte(mapProjekte(projekte));
        dto.setAnfragen(mapAnfragen(anfragen));
        dto.setGeschaeftsdokumente(mapGeschaeftsdokumente(kunde, projekte, anfragen));
        dto.setNotizen(mapNotizen(kunde.getId()));
        List<KundeAggregierteEmailDto> emailAdressen = aggregateEmails(kunde, projekte, anfragen);
        dto.setEmailAnzahl(emailReiterRepository.anzahlVerlaeufe(EmailBezug.KUNDE, kunde.getId()));
        dto.setStatistik(buildStatistik(projekte, anfragen, emailAdressen));
        return dto;
    }

    /**
     * Anfragen der Projekte des Kunden, Anfragen direkt an ihm und Anfragen mit seiner Kundennummer –
     * dieselbe Menge, deren Mails der E-Mail-Reiter des Kunden zeigt ({@code EmailReiterSql}). Jede nur einmal.
     */
    private List<Anfrage> loadAnfragenForKunde(Kunde kunde, List<Projekt> projekte) {
        Map<Long, Anfrage> result = new LinkedHashMap<>();
        List<Long> projektIds = Objects.requireNonNullElse(projekte, List.<Projekt>of()).stream()
                .map(Projekt::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!projektIds.isEmpty()) {
            sammle(result, anfrageRepository.findByProjektIdIn(projektIds));
        }
        sammle(result, anfrageRepository.findByKundeId(kunde.getId()));
        if (StringUtils.hasText(kunde.getKundennummer())) {
            sammle(result, anfrageRepository.findByKunde_KundennummerIgnoreCase(kunde.getKundennummer()));
        }
        return new ArrayList<>(result.values());
    }

    private static void sammle(Map<Long, Anfrage> result, List<Anfrage> anfragen) {
        for (Anfrage anfrage : Objects.requireNonNullElse(anfragen, List.<Anfrage>of())) {
            if (anfrage.getId() != null) {
                result.putIfAbsent(anfrage.getId(), anfrage);
            }
        }
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

    /**
     * Alle Geschäftsdokumente (Angebote, Rechnungen, Mahnungen ...) der Projekte
     * UND Anfragen dieses Kunden – aggregiert über die bestehenden Projekt-/Anfrage-Abfragen –
     * plus Dokumente, die direkt am Kunden hängen (ohne Projekt/Anfrage). Sonst zeigt die Akte
     * „0 Dokumente“, während die Datenschutz-Löschung sie zählt. Neueste zuerst (nach Dokumentdatum).
     */
    private List<AusgangsGeschaeftsDokumentResponseDto> mapGeschaeftsdokumente(Kunde kunde, List<Projekt> projekte,
            List<Anfrage> anfragen) {
        List<AusgangsGeschaeftsDokumentResponseDto> result = new ArrayList<>();
        for (Projekt projekt : Objects.requireNonNullElse(projekte, List.<Projekt>of())) {
            if (projekt.getId() != null) {
                result.addAll(ausgangsGeschaeftsDokumentService.findByProjekt(projekt.getId()));
            }
        }
        for (Anfrage anfrage : Objects.requireNonNullElse(anfragen, List.<Anfrage>of())) {
            if (anfrage.getId() != null) {
                result.addAll(ausgangsGeschaeftsDokumentService.findByAnfrage(anfrage.getId()));
            }
        }
        Set<Long> bekannt = result.stream().map(AusgangsGeschaeftsDokumentResponseDto::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (AusgangsGeschaeftsDokumentResponseDto d : ausgangsGeschaeftsDokumentService.findByKunde(kunde.getId())) {
            if (d.getId() == null || bekannt.add(d.getId())) {
                result.add(d);
            }
        }
        result.sort(Comparator.comparing(
                (AusgangsGeschaeftsDokumentResponseDto d) -> Optional.ofNullable(d.getDatum()).orElse(LocalDate.MIN))
                .reversed());
        return result;
    }

    private List<KundeNotizDto> mapNotizen(Long kundenId) {
        return kundeNotizRepository.findByKundeIdOrderByErstelltAmDesc(kundenId).stream()
                .map(notiz -> {
                    KundeNotizDto dto = new KundeNotizDto();
                    dto.setId(notiz.getId());
                    dto.setText(notiz.getText());
                    dto.setErstelltAm(notiz.getErstelltAm());
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
