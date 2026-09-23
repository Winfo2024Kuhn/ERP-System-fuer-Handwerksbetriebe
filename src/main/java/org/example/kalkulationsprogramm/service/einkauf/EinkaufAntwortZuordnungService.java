package org.example.kalkulationsprogramm.service.einkauf;

import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.EinkaufVersandAngenommen;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.Zuordnungsergebnis;
import org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMailZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAuditService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EinkaufAntwortZuordnungService implements EinkaufAnnahmeereignisConsumer, EinkaufImportZuordnungsService {
    private static final Pattern CODE = Pattern.compile("(?i)(?:zuordnungscode\\s*:?\\s*)?([a-f0-9]{32})");
    private final EmailRepository emails;
    private final AnfrageLieferantRepository beteiligungen;
    private final EinkaufMailZuordnungRepository zuordnungen;
    private final EinkaufsanfrageRepository anfragen;
    private final AnfrageRevisionRepository revisionen;
    private final EinkaufAuditService audit;
    private final ObjectMapper objectMapper;
    private final org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository versandauftraege;
    private final org.example.kalkulationsprogramm.repository.EinkaufBestellungRepository bestellungen;
    private final org.example.kalkulationsprogramm.repository.BestellungRevisionRepository bestellrevisionen;
    private final org.example.kalkulationsprogramm.repository.AngebotVersionRepository angebotsversionen;
    private final PlatformTransactionManager transactionManager;
    @Value("${app.background-jobs.enabled:true}")
    private boolean backgroundJobsEnabled;
    private static final Pattern PA_NUMMER = Pattern.compile("(?i)\\bPA-[0-9]{4}-[A-Z0-9-]{3,32}\\b");

    @org.springframework.beans.factory.annotation.Autowired
    public EinkaufAntwortZuordnungService(EmailRepository emails, AnfrageLieferantRepository beteiligungen,
            EinkaufMailZuordnungRepository zuordnungen, EinkaufsanfrageRepository anfragen,
            AnfrageRevisionRepository revisionen, EinkaufAuditService audit, ObjectMapper objectMapper,
            org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository versandauftraege,
            org.example.kalkulationsprogramm.repository.EinkaufBestellungRepository bestellungen,
            org.example.kalkulationsprogramm.repository.BestellungRevisionRepository bestellrevisionen,
            org.example.kalkulationsprogramm.repository.AngebotVersionRepository angebotsversionen,
            PlatformTransactionManager transactionManager) {
        this.emails = emails;
        this.beteiligungen = beteiligungen;
        this.zuordnungen = zuordnungen;
        this.anfragen = anfragen;
        this.revisionen = revisionen;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.versandauftraege = versandauftraege;
        this.bestellungen = bestellungen;
        this.bestellrevisionen = bestellrevisionen;
        this.angebotsversionen = angebotsversionen;
        this.transactionManager = transactionManager;
    }

    /** Compatibility constructor for isolated import-recovery fixtures predating bestellungs validation. */
    public EinkaufAntwortZuordnungService(EmailRepository emails, AnfrageLieferantRepository beteiligungen,
            EinkaufMailZuordnungRepository zuordnungen, EinkaufsanfrageRepository anfragen,
            AnfrageRevisionRepository revisionen, EinkaufAuditService audit, ObjectMapper objectMapper,
            org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository versandauftraege,
            PlatformTransactionManager transactionManager) {
        this(emails, beteiligungen, zuordnungen, anfragen, revisionen, audit, objectMapper, versandauftraege,
                null, null, null, transactionManager);
    }

    public static String klassifiziere(String autoSubmitted, String subject, String body) {
        String auto = autoSubmitted == null ? "" : autoSubmitted.toLowerCase(Locale.ROOT);
        if (!auto.isBlank() && !auto.equals("no")) return "AUTOMATISCHE_ANTWORT";
        String text = ((subject == null ? "" : subject) + " " + (body == null ? "" : body)).toLowerCase(Locale.ROOT);
        if (text.contains("undelivered") || text.contains("delivery status notification") || text.contains("unzustellbar")) return "UNZUSTELLBAR";
        if (text.contains("absage") || text.contains("kein angebot") || text.contains("können wir nicht anbieten")) return "ABSAGE";
        if ((subject == null ? "" : subject).toLowerCase(Locale.ROOT).contains("angebot")) return "ANGEBOT";
        return "PRUEFEN";
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void verarbeiteImportZuordnung(Long emailId) {
        if (emailId == null) return;
        emails.findById(emailId).filter(email -> "EINKAUF".equals(email.getKontoId()) && email.getDirection() == EmailDirection.IN)
                .ifPresent(this::ordneZu);
    }

    /** Durable recovery source: committed incoming EINKAUF emails without a mapping remain discoverable. */
    public int verarbeiteOffeneImportZuordnungen(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("Das Recovery-Limit muss zwischen 1 und 500 liegen.");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        int completed = 0;
        for (Long emailId : zuordnungen.findeOffeneImportZuordnungen(limit)) {
            try {
                Boolean processed = transaction.execute(status -> {
                    if (zuordnungen.findByEmailId(emailId).isPresent()) return false;
                    Email email = emails.findById(emailId).orElse(null);
                    if (email == null || !"EINKAUF".equals(email.getKontoId()) || email.getDirection() != EmailDirection.IN) return false;
                    ordneZu(email);
                    return true;
                });
                if (Boolean.TRUE.equals(processed)) completed++;
            } catch (RuntimeException ex) {
                log.error("[EinkaufMailZuordnung] Recovery für E-Mail {} fehlgeschlagen; bleibt offen", emailId, ex);
            }
        }
        return completed;
    }

    @Scheduled(fixedDelayString = "${einkauf.importzuordnung.recovery-delay:30000}")
    public void wiederholeOffeneImportZuordnungen() {
        if (!backgroundJobsEnabled) return;
        try {
            verarbeiteOffeneImportZuordnungen(100);
        } catch (RuntimeException ex) {
            log.error("[EinkaufMailZuordnung] Recovery offener Importzuordnungen fehlgeschlagen", ex);
        }
    }

    @Transactional
    public Zuordnungsergebnis zuordnen(Long emailId) {
        if (emailId == null || emailId <= 0) throw new IllegalArgumentException("Die E-Mail-ID ist ungültig.");
        Email email = emails.findById(emailId).orElseThrow(() -> new java.util.NoSuchElementException("E-Mail nicht gefunden."));
        if (!"EINKAUF".equals(email.getKontoId())) throw new IllegalArgumentException("Nur E-Mails aus dem Einkaufspostfach können zugeordnet werden.");
        return ordneZu(email);
    }

    @Transactional
    public Zuordnungsergebnis bestaetigen(Long emailId, String typ, Long vorgangId,
            Long beteiligungId, Long revisionId, String begruendung, Long akteurId) {
        if (emailId == null || emailId <= 0 || vorgangId == null || vorgangId <= 0 || akteurId == null || akteurId <= 0
                || typ == null || !Set.of("ANFRAGE", "BESTELLUNG").contains(typ)
                || begruendung == null || begruendung.isBlank() || begruendung.length() > 1000)
            throw new IllegalArgumentException("Bitte geben Sie eine gültige, begründete Zuordnung an.");
        Email email = emails.findById(emailId).orElseThrow(() -> new java.util.NoSuchElementException("E-Mail nicht gefunden."));
        if (!"EINKAUF".equals(email.getKontoId())) throw new IllegalArgumentException("Nur E-Mails aus dem Einkaufspostfach können zugeordnet werden.");
        if ("BESTELLUNG".equals(typ)) {
            validiereBestellung(email, vorgangId, beteiligungId, revisionId);
        } else if ("ANFRAGE".equals(typ)) {
            var anfrage = anfragen.findById(vorgangId).orElseThrow(() -> new java.util.NoSuchElementException("Anfrage nicht gefunden."));
            var revision = revisionId == null ? null : revisionen.findByIdAndAnfrageId(revisionId, vorgangId).orElse(null);
            if (revision == null || anfrage.getAktuelleRevision() == null
                    || !revision.getId().equals(anfrage.getAktuelleRevision().getId()))
                throw new IllegalArgumentException("Die Anfragefassung ist ungültig oder nicht mehr aktuell.");
            if (beteiligungId != null) {
                var beteiligung = beteiligungen.findByIdAndRevisionAnfrageId(beteiligungId, vorgangId)
                        .orElseThrow(() -> new java.util.NoSuchElementException("Lieferantenbeteiligung nicht gefunden."));
                if (!revisionId.equals(beteiligung.getRevision().getId())
                        || email.getFromAddress() == null || !email.getFromAddress().equalsIgnoreCase(beteiligung.getKontakt().email()))
                    throw new IllegalArgumentException("Absender und Lieferantenbeteiligung passen nicht zusammen.");
            }
        }
        var existing = zuordnungen.findByEmailId(emailId).orElse(null);
        var vorher = existing == null ? null : new Zuordnungsergebnis(existing.getEmailId(), existing.getTyp(),
                existing.getVorgangId(), existing.getBeteiligungId(), existing.getRevisionId(), existing.getStatus(), existing.getQuelle(), existing.isBestaetigt());
        var link = existing == null ? new EinkaufMailZuordnung(emailId) : existing;
        link.bestaetigen(typ, vorgangId, beteiligungId, revisionId, begruendung, akteurId, klassifiziere(email.getAutoSubmitted(), email.getSubject(), email.getBody()));
        link = zuordnungen.saveAndFlush(link);
        var ergebnis = toResult(link);
        audit.protokolliere("EINKAUF_EMAIL", emailId, "EMAIL_ZUORDNUNG_BESTAETIGT", akteurId,
                objectMapper.valueToTree(vorher), objectMapper.valueToTree(ergebnis), begruendung);
        return ergebnis;
    }

    private void validiereBestellung(Email email, Long bestellungId, Long beteiligungId, Long revisionId) {
        if (bestellungen == null || bestellrevisionen == null || angebotsversionen == null)
            throw new IllegalArgumentException("Bestellzuordnungen benötigen den aktuellen Bestellvalidator.");
        var bestellung = bestellungen.findById(bestellungId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Bestellung nicht gefunden."));
        if (bestellung.getStatus() == org.example.kalkulationsprogramm.domain.einkauf.BestellungStatus.ENTWURF
                || bestellung.getStatus() == org.example.kalkulationsprogramm.domain.einkauf.BestellungStatus.STORNIERT)
            throw new IllegalArgumentException("Die Bestellung wurde noch nicht versandt oder ist storniert.");
        var revision = bestellrevisionen.findFirstByBestellung_IdOrderByNummerDesc(bestellungId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Bestellfassung nicht gefunden."));
        if (revisionId == null || !revisionId.equals(revision.getId()) || revision.getVersandId() == null)
            throw new IllegalArgumentException("Die Bestellfassung wurde nicht nachweislich versandt oder ist nicht mehr aktuell.");
        String expectedEmail = bestellung.getEmpfaenger() == null ? null : bestellung.getEmpfaenger().email();
        if (expectedEmail == null || email.getFromAddress() == null || !expectedEmail.equalsIgnoreCase(email.getFromAddress()))
            throw new IllegalArgumentException("Absender und gespeicherter Bestellempfänger passen nicht zusammen.");
        if (bestellung.getAngebotsversionId() == null) {
            if (beteiligungId != null) throw new IllegalArgumentException("Eine Direktbestellung hat keine Angebotsbeteiligung.");
            return;
        }
        var offer = angebotsversionen.findById(bestellung.getAngebotsversionId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Angebotsfassung nicht gefunden."));
        var participation = offer.getAngebot().getBeteiligung();
        if (beteiligungId == null || !beteiligungId.equals(participation.getId())
                || !Objects.equals(bestellung.getAnfrageRevisionId(), participation.getRevision().getId())
                || !Objects.equals(expectedEmail, participation.getKontakt().email()))
            throw new IllegalArgumentException("Bestellung, Lieferantenbeteiligung, Anfragefassung und Absender passen nicht zusammen.");
    }

    private Zuordnungsergebnis ordneZu(Email email) {
        var previous = zuordnungen.findByEmailId(email.getId()).orElse(null);
        if (previous != null && previous.isBestaetigt()) return toResult(previous);
        String status = klassifiziere(email.getAutoSubmitted(), email.getSubject(), email.getBody());
        if (email.getDirection() != EmailDirection.IN || !"EINKAUF".equals(email.getKontoId())) status = "PRUEFEN";
        AnfrageLieferant hit = null;
        String content = (email.getSubject() == null ? "" : email.getSubject()) + "\n"
                + (email.getBody() == null ? "" : email.getBody()) + "\n"
                + (email.getHtmlBody() == null ? "" : email.getHtmlBody());
        var matcher = CODE.matcher(content);
        if (matcher.find()) {
            hit = beteiligungen.findFirstByRueckmeldecode(matcher.group(1)).orElse(null);
        }
        AnfrageLieferant threadHit = threadBeteiligung(email);
        if (hit != null && threadHit != null && !hit.getId().equals(threadHit.getId())) {
            var link = previous == null ? new EinkaufMailZuordnung(email.getId()) : previous;
            link.automatisch(null, null, null, null, "PRUEFEN", "HEADER_CODE_WIDERSPRUCH");
            return toResult(zuordnungen.save(link));
        }
        if (hit == null) hit = threadHit;
        boolean senderMatches = hit != null && email.getFromAddress() != null
                && hit.getKontakt() != null && hit.getKontakt().email() != null
                && hit.getKontakt().email().equalsIgnoreCase(email.getFromAddress());
        if (hit != null && senderMatches && !"AUTOMATISCHE_ANTWORT".equals(status) && !"UNZUSTELLBAR".equals(status)) {
            var revision = hit.getRevision();
            var link = previous == null ? new EinkaufMailZuordnung(email.getId()) : previous;
            link.automatisch("ANFRAGE", revision.getAnfrage().getId(), hit.getId(), revision.getId(), status,
                    threadHit == hit ? "THREAD_ABSENDER" : "CODE_ABSENDER");
            return toResult(zuordnungen.save(link));
        }
        if (hit == null) {
            AnfrageLieferant paHit = paUndKontaktTreffer(content, email.getFromAddress());
            if (paHit != null && !"AUTOMATISCHE_ANTWORT".equals(status) && !"UNZUSTELLBAR".equals(status)) {
                var revision = paHit.getRevision();
                var link = previous == null ? new EinkaufMailZuordnung(email.getId()) : previous;
                link.automatisch("ANFRAGE", revision.getAnfrage().getId(), paHit.getId(), revision.getId(), status, "PA_KONTAKT_EINDEUTIG");
                return toResult(zuordnungen.save(link));
            }
        }
        status = hit == null ? status : "PRUEFEN";
        var link = previous == null ? new EinkaufMailZuordnung(email.getId()) : previous;
        link.automatisch(null, null, null, null, status, hit == null ? "KEIN_EINDEUTIGER_TREFFER" : "CODE_ABSENDER_WIDERSPRUCH");
        return toResult(zuordnungen.save(link));
    }

    private Zuordnungsergebnis toResult(EinkaufMailZuordnung link) {
        return new Zuordnungsergebnis(link.getEmailId(), link.getTyp(), link.getVorgangId(),
                link.getBeteiligungId(), link.getRevisionId(), link.getStatus(), link.getQuelle(), link.isBestaetigt());
    }

    private AnfrageLieferant threadBeteiligung(Email email) {
        if (email.getParentEmail() == null) return null;
        return zuordnungen.findByEmailId(email.getParentEmail().getId())
                .filter(link -> "ANFRAGE".equals(link.getTyp()) && link.getBeteiligungId() != null)
                .flatMap(link -> beteiligungen.findById(link.getBeteiligungId())).orElse(null);
    }

    private AnfrageLieferant paUndKontaktTreffer(String content, String sender) {
        if (content == null || sender == null) return null;
        var matcher = PA_NUMMER.matcher(content);
        if (!matcher.find()) return null;
        var anfrage = anfragen.findByPaNummer(matcher.group().toUpperCase(Locale.ROOT)).orElse(null);
        if (anfrage == null || anfrage.getAktuelleRevision() == null) return null;
        List<AnfrageLieferant> matches = beteiligungen.findByRevisionIdOrderByIdAsc(anfrage.getAktuelleRevision().getId()).stream()
                .filter(b -> b.getKontakt() != null && b.getKontakt().email() != null && b.getKontakt().email().equalsIgnoreCase(sender)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    @Override public Set<String> unterstuetzteVorgangstypen() { return Set.of("ANFRAGE"); }

    @Override
    @Transactional
    public void verarbeite(EinkaufVersandAngenommen event) {
        if (event == null || event.ereignisSchluessel() == null || !"ANFRAGE".equals(event.typ())
                || event.beteiligungId() == null || event.revisionId() == null) return;
        AnfrageLieferant beteiligung = beteiligungen.findById(event.beteiligungId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Lieferantenbeteiligung nicht gefunden."));
        if (!beteiligung.getRevision().getId().equals(event.revisionId())
                || !beteiligung.getRevision().getAnfrage().getId().equals(event.vorgangId()))
            throw new IllegalStateException("Versandannahme passt nicht zur Anfragefassung.");
        if (event.ereignisSchluessel().equals(beteiligung.getVersandAnnahmeereignis())) return;
        persistiereAusgangsmail(event);
        beteiligung.versandAngenommen(event.ereignisSchluessel());
    }

    private void persistiereAusgangsmail(EinkaufVersandAngenommen event) {
        var auftrag = versandauftraege.findById(event.versandId()).orElseThrow(() -> new java.util.NoSuchElementException("Versandauftrag nicht gefunden."));
        org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandSnapshot snapshot;
        try { snapshot = objectMapper.readValue(auftrag.getSnapshotJson(), org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandSnapshot.class); }
        catch (Exception ex) { throw new IllegalStateException("Der eingefrorene Versand-Snapshot ist ungültig.", ex); }
        String messageId = snapshot.nachricht().messageId();
        Email outgoing = emails.findByKontoIdAndMessageId("EINKAUF", messageId).orElseGet(() -> {
            Email created = new Email();
            created.setMessageId(messageId); created.setKontoId("EINKAUF"); created.setDirection(org.example.kalkulationsprogramm.domain.EmailDirection.OUT);
            created.setSubject(snapshot.nachricht().subject()); created.setBody(snapshot.nachricht().html());
            created.setHtmlBody(snapshot.nachricht().html()); created.setRecipient(snapshot.nachricht().to());
            created.setSentAt(java.time.LocalDateTime.ofInstant(event.zeit(), java.time.ZoneId.systemDefault()));
            return emails.saveAndFlush(created);
        });
        var linked = zuordnungen.findByEmailId(outgoing.getId()).orElseGet(() -> new EinkaufMailZuordnung(outgoing.getId()));
        linked.automatisch("ANFRAGE", event.vorgangId(), event.beteiligungId(), event.revisionId(), "VERSENDET", "OUTBOX_ANNAHME");
        zuordnungen.save(linked);
    }
}
