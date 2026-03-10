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
 * Service für automatische Email-Zuordnung zu Lieferanten, Projekten und Angeboten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAutoAssignmentService {

    private final EmailRepository emailRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ProjektRepository projektRepository;
    private final AngebotRepository angebotRepository;

    /**
     * Versucht eine Email automatisch zuzuordnen.
     * Priorisierung: Lieferant > Projekt > Angebot
     * 
     * @return true wenn zugeordnet wurde
     */
    @Transactional
    public boolean tryAutoAssign(Email email) {
        if (email.getZuordnungTyp() != EmailZuordnungTyp.KEINE) {
            return false; // Bereits zugeordnet
        }

        // 1. Lieferant über Domain
        if (tryAssignToLieferant(email)) {
            return true;
        }

        // 2. Projekt/Angebot über Kunden-Email
        if (tryAssignToKundeEntity(email)) {
            return true;
        }

        return false;
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
     * Versucht Email einem Projekt oder Angebot zuzuordnen basierend auf Kunden-Emails.
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

        // Finde Angebote mit dieser Email (im Zeitfenster)
        // Optimiert: Nutze Repository-Query
        List<Angebot> matchingAngebote = angebotRepository.findByKundenEmail(emailLower).stream()
                .filter(a -> isInTimeRange(a.getAnlegedatum(), rangeStart, rangeEnd))
                .collect(Collectors.toList());

        int totalMatches = matchingProjekte.size() + matchingAngebote.size();

        // 2a. Genau 1 Match im Zeitfenster → direkt zuordnen
        if (totalMatches == 1) {
            return assignToSingleMatch(email, matchingProjekte, matchingAngebote, "Zeitfenster-Match");
        }

        // 2b. Mehrere Matches im Zeitfenster → Schlagwortsuche
        if (totalMatches > 1) {
            if (tryAssignByKeywords(email, matchingProjekte, matchingAngebote)) {
                return true;
            }
        }
        
        // 3. Fallback: Globale Suche (ohne Zeitfenster) falls noch nichts gefunden
        if (totalMatches == 0) {
            // Optimiert: Nutze Repository-Query ohne Zeitfilter
            List<Projekt> allProjekte = projektRepository.findByKundenEmail(emailLower);
            List<Angebot> allAngebote = angebotRepository.findByKundenEmail(emailLower);
                
            int globalMatches = allProjekte.size() + allAngebote.size();
            
            if (globalMatches == 1) {
                return assignToSingleMatch(email, allProjekte, allAngebote, "Globaler-Match");
            }
            
            if (globalMatches > 1) {
                return tryAssignByKeywords(email, allProjekte, allAngebote);
            }
        }

        return false;
    }

    private boolean assignToSingleMatch(Email email, List<Projekt> projekte, List<Angebot> angebote, String reason) {
        if (!projekte.isEmpty()) {
            email.assignToProjekt(projekte.getFirst());
            emailRepository.save(email);
            log.info("[AutoAssign] Email {} → Projekt {} ({})", 
                    email.getId(), projekte.getFirst().getBauvorhaben(), reason);
            return true;
        } else {
            email.assignToAngebot(angebote.getFirst());
            emailRepository.save(email);
            log.info("[AutoAssign] Email {} → Angebot {} ({})", 
                    email.getId(), angebote.getFirst().getBauvorhaben(), reason);
            return true;
        }
    }

    /**
     * Versucht Zuordnung über Schlagwörter (Bauvorhaben, Kurzbeschreibung) im Email-Betreff.
     */
    public boolean tryAssignByKeywords(Email email, List<Projekt> projekte, List<Angebot> angebote) {
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

        // Schlagwortsuche in Angeboten
        for (Angebot a : angebote) {
            if (matchesKeywords(searchText, a.getBauvorhaben(), a.getKurzbeschreibung())) {
                email.assignToAngebot(a);
                emailRepository.save(email);
                log.info("[AutoAssign] Email {} → Angebot {} (Schlagwort-Match)", 
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

        // Angebote mit passender Email
        result.angebote = angebotRepository.findByKundenEmail(emailLower).stream()
                .map(a -> new EntityOption(a.getId(), a.getBauvorhaben(), "ANGEBOT", null, "A-" + a.getId()))
                .collect(Collectors.toList());

        return result;
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
        public List<EntityOption> angebote = new ArrayList<>();
    }

    public static class EntityOption {
        public Long id;
        public String name;
        public String type;
        public String projektNummer;
        public String angebotNummer;

        public EntityOption(Long id, String name, String type, String projektNummer, String angebotNummer) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.projektNummer = projektNummer;
            this.angebotNummer = angebotNummer;
        }
    }
}
