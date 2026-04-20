package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für automatische Email-Zuordnung zu Lieferanten, Projekten und Anfragenn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAutoAssignmentService {

    private final EmailRepository emailRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final EmailKiClassificationService emailKiClassificationService;
    private final PreisanfrageZuordnungService preisanfrageZuordnungService;

    /**
     * Versucht eine Email automatisch zuzuordnen.
     * Priorisierung: Preisanfrage-Antwort > Lieferant > Projekt > Anfrage
     *
     * @return true wenn zugeordnet wurde
     */
    @Transactional
    public boolean tryAutoAssign(Email email) {
        if (email.getZuordnungTyp() != EmailZuordnungTyp.KEINE) {
            return false; // Bereits zugeordnet
        }

        // 1. Preisanfrage-Antwort (via parentEmail.messageId oder Token im Betreff)
        if (tryAssignToPreisanfrage(email)) {
            return true;
        }

        // 2. Lieferant über Domain
        if (tryAssignToLieferant(email)) {
            return true;
        }

        // 3. Projekt/Anfrage über Kunden-Email
        if (tryAssignToKundeEntity(email)) {
            return true;
        }

        return false;
    }

    /**
     * Versucht die Email einer offenen Preisanfrage-Position zuzuordnen. Bei Treffer
     * wird die Email zusätzlich dem Lieferanten zugewiesen, damit sie weiter im
     * Lieferanten-Postfach sichtbar bleibt.
     */
    @Transactional
    public boolean tryAssignToPreisanfrage(Email email) {
        return preisanfrageZuordnungService.tryMatch(email)
                .map(pal -> {
                    email.assignToLieferant(pal.getLieferant());
                    emailRepository.save(email);
                    log.info("[AutoAssign] Email {} -> PreisanfrageLieferant {} (Lieferant: {})",
                            email.getId(), pal.getId(), pal.getLieferant().getLieferantenname());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Versucht Email einem Lieferanten zuzuordnen basierend auf Sender-Domain.
     */
    @Transactional
    public boolean tryAssignToLieferant(Email email) {
        String senderDomain = email.getSenderDomain();
        if (senderDomain == null || senderDomain.isBlank()) {
            return false;
        }

        // Finde Lieferant mit passender Email-Domain via Repository
        // Verwende die optimierte Query-Methode
        List<Lieferanten> matches = lieferantenRepository.findByEmailDomain(senderDomain);
        
        // Nehme den ersten Treffer, falls vorhanden
        if (!matches.isEmpty()) {
            email.assignToLieferant(matches.getFirst());
            emailRepository.save(email);
            log.info("[AutoAssign] Email {} → Lieferant {} (Domain: {})", 
                    email.getId(), matches.getFirst().getLieferantenname(), senderDomain);
            return true;
        }

        return false;
    }

    /**
     * Versucht Email einem Projekt oder Anfrage zuzuordnen basierend auf Kunden-Emails.
     * Wenn genau 1 Match (±1 Monat), direkt zuordnen.
     * Bei mehreren Matches: Schlagwortsuche.
     */
    @Transactional
    public boolean tryAssignToKundeEntity(Email email) {
        String fromAddress = email.getFromAddress();
        if (fromAddress == null || fromAddress.isBlank()) {
            return false;
        }

        String emailLower = fromAddress.toLowerCase().trim();
        LocalDateTime emailDate = email.getSentAt() != null ? email.getSentAt() : LocalDateTime.now();
        LocalDateTime rangeStart = emailDate.minusMonths(1);
        LocalDateTime rangeEnd = emailDate.plusMonths(1);

        // Finde Projekte mit dieser Email (im Zeitfenster)
        // Optimiert: Nutze Repository-Query
        List<Projekt> matchingProjekte = projektRepository.findByKundenEmail(emailLower).stream()
                .filter(p -> isInTimeRange(p.getAnlegedatum(), rangeStart, rangeEnd))
                .collect(Collectors.toList());

        // Finde Anfragen mit dieser Email (im Zeitfenster)
        // Optimiert: Nutze Repository-Query
        List<Anfrage> matchingAnfragen = anfrageRepository.findByKundenEmail(emailLower).stream()
                .filter(a -> isInTimeRange(a.getAnlegedatum(), rangeStart, rangeEnd))
                .collect(Collectors.toList());

        int totalMatches = matchingProjekte.size() + matchingAnfragen.size();

        // 2a. Genau 1 Match im Zeitfenster → direkt zuordnen
        if (totalMatches == 1) {
            return assignToSingleMatch(email, matchingProjekte, matchingAnfragen, "Zeitfenster-Match");
        }

        // 2b. Mehrere Matches im Zeitfenster → Schlagwortsuche, dann KI-Fallback
        if (totalMatches > 1) {
            if (tryAssignByKeywords(email, matchingProjekte, matchingAnfragen)) {
                return true;
            }
            // KI-Fallback: Wenn Schlagwörter nicht reichen, KI fragen
            if (tryAssignByKi(email, matchingProjekte, matchingAnfragen)) {
                return true;
            }
        }
        
        // 3. Fallback: Globale Suche (ohne Zeitfenster) falls noch nichts gefunden
        if (totalMatches == 0) {
            // Optimiert: Nutze Repository-Query ohne Zeitfilter
            List<Projekt> allProjekte = projektRepository.findByKundenEmail(emailLower);
            List<Anfrage> allAnfragen = anfrageRepository.findByKundenEmail(emailLower);
                
            int globalMatches = allProjekte.size() + allAnfragen.size();
            
            if (globalMatches == 1) {
                return assignToSingleMatch(email, allProjekte, allAnfragen, "Globaler-Match");
            }
            
            if (globalMatches > 1) {
                if (tryAssignByKeywords(email, allProjekte, allAnfragen)) {
                    return true;
                }
                // KI-Fallback für globale Matches
                return tryAssignByKi(email, allProjekte, allAnfragen);
            }
        }

        return false;
    }

    private boolean assignToSingleMatch(Email email, List<Projekt> projekte, List<Anfrage> anfragen, String reason) {
        if (!projekte.isEmpty()) {
            email.assignToProjekt(projekte.getFirst());
            emailRepository.save(email);
            log.info("[AutoAssign] Email {} → Projekt {} ({})", 
                    email.getId(), projekte.getFirst().getBauvorhaben(), reason);
            return true;
        } else {
            email.assignToAnfrage(anfragen.getFirst());
            emailRepository.save(email);
            log.info("[AutoAssign] Email {} → Anfrage {} ({})", 
                    email.getId(), anfragen.getFirst().getBauvorhaben(), reason);
            return true;
        }
    }

    /**
     * Versucht Zuordnung über Schlagwörter (Bauvorhaben, Kurzbeschreibung) im Email-Betreff.
     */
    public boolean tryAssignByKeywords(Email email, List<Projekt> projekte, List<Anfrage> anfragen) {
        String subject = email.getSubject();
        String body = email.getBody();
        String searchText = ((subject != null ? subject : "") + " " + (body != null ? body : "")).toLowerCase();

        // Schlagwortsuche in Projekten
        for (Projekt p : projekte) {
            if (matchesKeywords(searchText, p.getBauvorhaben(), p.getKurzbeschreibung())) {
                email.assignToProjekt(p);
                emailRepository.save(email);
                log.info("[AutoAssign] Email {} → Projekt {} (Schlagwort-Match)", 
                        email.getId(), p.getBauvorhaben());
                return true;
            }
        }

        // Schlagwortsuche in Anfragenn
        for (Anfrage a : anfragen) {
            if (matchesKeywords(searchText, a.getBauvorhaben(), a.getKurzbeschreibung())) {
                email.assignToAnfrage(a);
                emailRepository.save(email);
                log.info("[AutoAssign] Email {} → Anfrage {} (Schlagwort-Match)", 
                        email.getId(), a.getBauvorhaben());
                return true;
            }
        }

        return false;
    }

    /**
     * Prüft ob Schlagwörter aus Bauvorhaben/Kurzbeschreibung im Text gefunden werden.
     */
    private boolean matchesKeywords(String searchText, String bauvorhaben, String kurzbeschreibung) {
        List<String> keywords = new ArrayList<>();
        
        if (bauvorhaben != null && !bauvorhaben.isBlank()) {
            // Zerlege Bauvorhaben in Wörter (min. 4 Zeichen)
            Arrays.stream(bauvorhaben.split("\\s+"))
                    .filter(w -> w.length() >= 4)
                    .map(String::toLowerCase)
                    .forEach(keywords::add);
        }

        if (kurzbeschreibung != null && !kurzbeschreibung.isBlank()) {
            Arrays.stream(kurzbeschreibung.split("\\s+"))
                    .filter(w -> w.length() >= 4)
                    .map(String::toLowerCase)
                    .forEach(keywords::add);
        }

        // Mind. 2 Schlagwörter müssen matchen für Zuordnung
        long matchCount = keywords.stream()
                .filter(searchText::contains)
                .count();

        return matchCount >= 2;
    }

    /**
     * Findet mögliche Zuordnungen für eine Email (für Frontend-Dropdown).
     */
    @Transactional(readOnly = true)
    public PossibleAssignments findPossibleAssignments(Email email) {
        String fromAddress = email.getFromAddress();
        PossibleAssignments result = new PossibleAssignments();
        
        if (fromAddress == null || fromAddress.isBlank()) {
            return result;
        }

        String emailLower = fromAddress.toLowerCase().trim();

        // Projekte mit passender Email
        result.projekte = projektRepository.findByKundenEmail(emailLower).stream()
                .map(p -> new EntityOption(p.getId(), p.getBauvorhaben(), "PROJEKT", p.getAuftragsnummer(), null))
                .collect(Collectors.toList());

        // Anfragen mit passender Email
        result.anfragen = anfrageRepository.findByKundenEmail(emailLower).stream()
                .map(a -> new EntityOption(a.getId(), a.getBauvorhaben(), "ANFRAGE", null, "A-" + a.getId()))
                .collect(Collectors.toList());

        return result;
    }

    /**
     * KI-basierte Zuordnung als Fallback wenn Schlagwortsuche nicht greift.
     * Ollama bekommt alle Kandidaten mit Email-Verlauf und entscheidet.
     */
    @Transactional
    public boolean tryAssignByKi(Email email, List<Projekt> projekte, List<Anfrage> anfragen) {
        try {
            EmailKiClassificationService.ClassificationResult result =
                    emailKiClassificationService.classify(email, projekte, anfragen);

            if (!result.isAssigned()) {
                log.info("[AutoAssign] KI konnte Email {} nicht zuordnen: {}", email.getId(), result.reason());
                return false;
            }

            if (result.zuordnungTyp() == EmailZuordnungTyp.PROJEKT) {
                Optional<Projekt> match = projekte.stream()
                        .filter(p -> p.getId().equals(result.entityId()))
                        .findFirst();
                if (match.isPresent()) {
                    email.assignToProjekt(match.get());
                    emailRepository.save(email);
                    log.info("[AutoAssign] Email {} → Projekt {} (KI-Zuordnung, confidence={}, reason={})",
                            email.getId(), match.get().getBauvorhaben(), result.confidence(), result.reason());
                    return true;
                }
            } else if (result.zuordnungTyp() == EmailZuordnungTyp.ANFRAGE) {
                Optional<Anfrage> match = anfragen.stream()
                        .filter(a -> a.getId().equals(result.entityId()))
                        .findFirst();
                if (match.isPresent()) {
                    email.assignToAnfrage(match.get());
                    emailRepository.save(email);
                    log.info("[AutoAssign] Email {} → Anfrage {} (KI-Zuordnung, confidence={}, reason={})",
                            email.getId(), match.get().getBauvorhaben(), result.confidence(), result.reason());
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.warn("[AutoAssign] KI-Zuordnung fehlgeschlagen für Email {}: {}", email.getId(), e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ═══════════════════════════════════════════════════════════════



    private boolean isInTimeRange(java.time.LocalDate date, LocalDateTime start, LocalDateTime end) {
        if (date == null) {
            return true; // Kein Datum = immer match
        }
        LocalDateTime dateTime = date.atStartOfDay();
        return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    // ═══════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════

    public static class PossibleAssignments {
        public List<EntityOption> projekte = new ArrayList<>();
        public List<EntityOption> anfragen = new ArrayList<>();
    }

    public static class EntityOption {
        public Long id;
        public String name;
        public String type;
        public String projektNummer;
        public String anfrageNummer;

        public EntityOption(Long id, String name, String type, String projektNummer, String anfrageNummer) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.projektNummer = projektNummer;
            this.anfrageNummer = anfrageNummer;
        }
    }
}
