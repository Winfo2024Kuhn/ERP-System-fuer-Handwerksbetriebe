package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailZuordnung;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufMailantwortVorschau;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageLieferant;
import org.example.kalkulationsprogramm.domain.einkauf.AnfrageRevision;
import org.example.kalkulationsprogramm.domain.einkauf.Einkaufsanfrage;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufKommunikationVorschau;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKommunikationDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPdfDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandSnapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.Gerendert;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVorlagenDto.VorlagenKontext;
import org.example.kalkulationsprogramm.repository.AnfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRevisionRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMailZuordnungRepository;
import org.example.kalkulationsprogramm.repository.EinkaufMailantwortVorschauRepository;
import org.example.kalkulationsprogramm.repository.EinkaufKommunikationVorschauRepository;
import org.example.kalkulationsprogramm.repository.EinkaufsanfrageRepository;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufOutboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EinkaufKommunikationService {
    private static final SecureRandom TOKEN_GENERATOR = new SecureRandom();
    private final EinkaufsanfrageRepository anfragen;
    private final AnfrageRevisionRepository revisionen;
    private final AnfrageLieferantRepository beteiligungen;
    private final EmailRepository emails;
    private final EinkaufMailZuordnungRepository zuordnungen;
    private final EinkaufKommunikationVorschauRepository vorschauen;
    private final EinkaufMailantwortVorschauRepository antwortVorschauen;
    private final org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository versandAuftraege;
    private final EinkaufVorlagenService vorlagen;
    private final EinkaufPdfService pdf;
    private final EinkaufDateiService dateien;
    private final EinkaufOutboxService outbox;
    private final EinkaufVersandWorker worker;
    private final EinkaufMailantwortVersandListener mailantwortListener;
    private final ObjectMapper objectMapper;

    public EinkaufKommunikationService(EinkaufsanfrageRepository anfragen, AnfrageRevisionRepository revisionen,
            AnfrageLieferantRepository beteiligungen, EmailRepository emails, EinkaufMailZuordnungRepository zuordnungen,
            EinkaufKommunikationVorschauRepository vorschauen, EinkaufMailantwortVorschauRepository antwortVorschauen,
            org.example.kalkulationsprogramm.repository.EinkaufVersandauftragRepository versandAuftraege, EinkaufVorlagenService vorlagen,
            EinkaufPdfService pdf, EinkaufDateiService dateien,
            EinkaufOutboxService outbox, EinkaufVersandWorker worker, EinkaufMailantwortVersandListener mailantwortListener, ObjectMapper objectMapper) {
        this.anfragen = anfragen; this.revisionen = revisionen; this.beteiligungen = beteiligungen;
        this.emails = emails; this.zuordnungen = zuordnungen; this.vorschauen = vorschauen;
        this.antwortVorschauen = antwortVorschauen;
        this.versandAuftraege = versandAuftraege;
        this.vorlagen = vorlagen; this.pdf = pdf;
        this.dateien = dateien; this.outbox = outbox; this.worker = worker; this.mailantwortListener = mailantwortListener; this.objectMapper = objectMapper;
    }

    @Transactional
    public Vorschau vorschau(Long anfrageId, Long beteiligungId, Long templateId) {
        return vorschauDaten(anfrageId, beteiligungId, templateId).vorschau();
    }

    private VorschauDaten vorschauDaten(Long anfrageId, Long beteiligungId, Long templateId) {
        VersandBasis basis = ladeBasis(anfrageId, beteiligungId);
        if (templateId == null || templateId <= 0) throw new IllegalArgumentException("Bitte wählen Sie eine Einkaufsvorlage.");
        var kontakt = basis.beteiligung().getKontakt();
        var positionen = basis.revision().getPositionen().stream().map(p -> p.getSnapshot()).toList();
        String lieferadresse = basis.revision().getPositionen().stream().flatMap(p -> p.getHerkuenfte().stream())
                .map(h -> h.getBedarf().getLiefergruppe()).filter(Objects::nonNull)
                .map(group -> group.lieferadresse())
                .filter(Objects::nonNull).findFirst().orElse("");
        Gerendert gerendert = vorlagen.rendern(templateId, new VorlagenKontext("EINKAUF_ANFRAGE",
                Map.of("LIEFERANTENNAME", safe(kontakt.lieferantenname()), "ANSPRECHPARTNER", safe(kontakt.name()),
                        "ANREDE", safe(kontakt.anrede()), "LIEFERADRESSE", lieferadresse,
                        "EIGENE_KUNDENNUMMER_BEIM_LIEFERANTEN", safe(kontakt.eigeneKundennummer()),
                        "ANFRAGENUMMER", basis.anfrage().getPaNummer(), "ANTWORTFRIST", value(basis.revision().getAntwortfrist()),
                        "LIEFERTERMIN", value(basis.revision().getLiefertermin())), positionen, basis.beteiligung().getRueckmeldecode()));
        Beleg pdfBeleg = new Beleg("ANFRAGE", basis.anfrage().getPaNummer(), basis.revision().getNummer(),
                kontakt, basis.revision().getPositionen().stream().map(p -> new PdfPosition(String.valueOf(p.getId()),
                        p.getSnapshot(), p.getHerkuenfte().stream().map(h -> new PdfHerkunft(h.getBedarf().getId(),
                                h.getBedarf().getLiefergruppe().projektId() == null ? null : String.valueOf(h.getBedarf().getLiefergruppe().projektId()),
                                h.getMenge(), p.getSnapshot().basis().einheit())).toList(), List.of(), null)).toList(),
                List.of(), basis.revision().getPositionen().stream().flatMap(p -> p.getHerkuenfte().stream())
                        .map(h -> h.getBedarf().getLiefergruppe()).distinct().toList(), basis.revision().getAntwortfrist(),
                basis.revision().getLiefertermin(), null, null, true);
        byte[] renderedPdf = pdf.erzeugen(pdfBeleg);
        byte[] pdfBytes = renderedPdf;
        List<Long> attachmentIds = basis.revision().getPositionen().stream().flatMap(p -> p.getSnapshot().anlageVersionIds().stream()).distinct().sorted().toList();
        dateien.pruefePaketgroesse(attachmentIds, pdfBytes.length);
        var attachments = new java.util.ArrayList<>(dateien.ladeVersandanlagen(attachmentIds));
        var pdfSnapshot = dateien.speicherePdfSnapshot(renderedPdf,
                basis.anfrage().getPaNummer() + "-Anfrage-" + basis.revision().getNummer() + ".pdf");
        Long frozenPdfId = pdfSnapshot.dateiId();
        attachments.add(new EmailService.Attachment(pdfBytes, basis.anfrage().getPaNummer() + "-Anfrage.pdf", "application/pdf"));
        String empfaenger = kontakt.email();
        String token = token();
        String inhaltHash = inhaltHash(basis.revision().getId(), templateId, gerendert.version(), gerendert.subject(),
                gerendert.htmlBody(), empfaenger, attachmentIds, frozenPdfId, sha256(pdfBytes));
        Instant erstelltAm = Instant.now();
        vorschauen.saveAndFlush(new EinkaufKommunikationVorschau(token, basis.anfrage().getId(), beteiligungId,
                basis.revision().getId(), templateId, gerendert.version(), gerendert.subject(), gerendert.htmlBody(),
                empfaenger, attachmentIds, frozenPdfId, sha256(pdfBytes), inhaltHash, erstelltAm,
                erstelltAm.plus(Duration.ofDays(1))));
        Vorschau preview = new Vorschau(basis.revision().getId(), token, gerendert.subject(), gerendert.htmlBody(), empfaenger,
                frozenPdfId, attachmentIds, basis.revision().getNummer(), kontakt.eigeneKundennummer(),
                basis.revision().getAntwortfrist(), basis.revision().getLiefertermin(), dateien.metadaten(attachmentIds));
        return new VorschauDaten(basis, preview, List.copyOf(attachments));
    }

    @Transactional
    public VersandErgebnis senden(Long anfrageId, Long beteiligungId, Freigabe freigabe, Long akteurId) {
        if (freigabe == null || freigabe.idempotenzKey() == null || akteurId == null || akteurId <= 0)
            throw new IllegalArgumentException("Die Versandfreigabe ist unvollständig.");
        var prior = outbox.findeWiederholungsauftrag(freigabe.idempotenzKey(), freigabe.vorschauHash(), anfrageId, beteiligungId);
        if (prior.isPresent()) {
            var versand = prior.get();
            return new VersandErgebnis(beteiligungId, versand.status(), versand.fehlerCode(), versand.messageId());
        }
        EinkaufKommunikationVorschau vorschauSnapshot = vorschauen
                .findByFreigabeTokenAndAnfrageIdAndBeteiligungId(freigabe.vorschauHash(), anfrageId, beteiligungId)
                .orElseThrow(() -> new IllegalArgumentException("Die Vorschau ist abgelaufen. Bitte neu erstellen."));
        if (!Instant.now().isBefore(vorschauSnapshot.getGueltigBis()))
            throw new IllegalArgumentException("Die Vorschau ist abgelaufen. Bitte neu erstellen.");
        // Serialize approvals for the same participation, not just requests sharing an idempotency key.
        var gesperrt = beteiligungen.sperreVersandbeteiligung(beteiligungId, anfrageId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Lieferantenbeteiligung nicht gefunden."));
        var bereitsBeauftragt = outbox.pruefeBeteiligungsversand(anfrageId, gesperrt.getRevision().getId(),
                beteiligungId, freigabe.idempotenzKey(), freigabe.vorschauHash());
        if (bereitsBeauftragt.isPresent()) {
            var versand = bereitsBeauftragt.get();
            return new VersandErgebnis(beteiligungId, versand.status(), versand.fehlerCode(), versand.messageId());
        }
        VersandBasis basis = ladeBasis(anfrageId, beteiligungId);
        if (!basis.revision().getId().equals(vorschauSnapshot.getRevisionId())
                || !basis.revision().getId().equals(freigabe.version()))
            throw new IllegalStateException("Die Anfrage oder Vorschau wurde geändert. Bitte neu prüfen.");
        String verifiedHash = inhaltHash(vorschauSnapshot.getRevisionId(), vorschauSnapshot.getVorlageId(), vorschauSnapshot.getVorlageVersion(),
                vorschauSnapshot.getSubject(), vorschauSnapshot.getHtmlBody(), vorschauSnapshot.getEmpfaenger(), vorschauSnapshot.getAnlageVersionIds(),
                vorschauSnapshot.getPdfDateiId(), vorschauSnapshot.getPdfSha256());
        if (!verifiedHash.equals(vorschauSnapshot.getInhaltSha256()))
            throw new IllegalStateException("Der gespeicherte Vorschauinhalt ist verändert und kann nicht versendet werden.");
        byte[] pdfBytes = dateien.ladePdfSnapshotBytes(vorschauSnapshot.getPdfDateiId());
        if (!sha256(pdfBytes).equals(vorschauSnapshot.getPdfSha256()))
            throw new IllegalStateException("Die freigegebene PDF-Datei stimmt nicht mit der Vorschau überein.");
        dateien.pruefePaketgroesse(vorschauSnapshot.getAnlageVersionIds(), pdfBytes.length);
        var attachments = new java.util.ArrayList<>(dateien.ladeVersandanlagen(vorschauSnapshot.getAnlageVersionIds()));
        attachments.add(new EmailService.Attachment(pdfBytes, basis.anfrage().getPaNummer() + "-Anfrage.pdf", "application/pdf"));
        Vorschau aktuell = new Vorschau(vorschauSnapshot.getRevisionId(), vorschauSnapshot.getFreigabeToken(), vorschauSnapshot.getSubject(),
                vorschauSnapshot.getHtmlBody(), vorschauSnapshot.getEmpfaenger(), vorschauSnapshot.getPdfDateiId(), vorschauSnapshot.getAnlageVersionIds());
        var message = new Nachricht(null, aktuell.empfaenger(), aktuell.subject(), aktuell.htmlBody(), null, List.of(), attachments);
        VersandSnapshot versandSnapshot = new VersandSnapshot("ANFRAGE", anfrageId, basis.revision().getId(), beteiligungId,
                new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.KontoZugangReferenz("EINKAUF"), message, aktuell.vorschauHash());
        var result = outbox.einreihen(versandSnapshot, freigabe.idempotenzKey(), akteurId);
        worker.dispatchNachCommit(result.id());
        return new VersandErgebnis(beteiligungId, result.status(), result.fehlerCode(), result.messageId());
    }

    @Transactional
    public AntwortVorschau antwortVorschau(Long emailId, Antwort request) {
        AntwortBasis basis = ladeAntwortBasis(emailId);
        EingabeAntwort antwort = pruefeAntwort(request);
        var attachments = dateien.ladeVersandanlagen(antwort.anlageIds());
        var anlagen = new java.util.ArrayList<EinkaufMailantwortVorschau.AnlageSnapshot>();
        for (int index = 0; index < attachments.size(); index++) {
            anlagen.add(new EinkaufMailantwortVorschau.AnlageSnapshot(antwort.anlageIds().get(index), sha256(attachments.get(index).data())));
        }
        dateien.pruefePaketgroesse(antwort.anlageIds(), 0);
        String token = token();
        String hash = antwortInhaltHash(basis, antwort, anlagen);
        Instant erstelltAm = Instant.now();
        antwortVorschauen.saveAndFlush(new EinkaufMailantwortVorschau(token, basis.email().getId(), basis.email().getKontoId(),
                basis.zuordnung().getTyp(), basis.zuordnung().getVorgangId(), basis.zuordnung().getBeteiligungId(),
                basis.zuordnung().getRevisionId(), basis.email().getMessageId(), basis.inReplyTo(), basis.references(),
                basis.empfaenger(), antwort.subject(), antwort.htmlBody(), anlagen, hash,
                erstelltAm, erstelltAm.plus(Duration.ofDays(1))));
        return new AntwortVorschau(token, antwort.subject(), antwort.htmlBody(), basis.empfaenger(), antwort.anlageIds(),
                basis.inReplyTo(), basis.references());
    }

    @Transactional
    public org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto antworten(Long emailId, Antwort request, Long akteurId) {
        if (akteurId == null || akteurId <= 0 || request == null || request.idempotenzKey() == null
                || request.vorschauHash() == null || request.vorschauHash().isBlank())
            throw new IllegalArgumentException("Die freigegebene Einkaufsantwort ist unvollständig.");
        AntwortBasis basis = ladeAntwortBasis(emailId, true);
        var snapshot = antwortVorschauen.findByFreigabeTokenAndEmailId(request.vorschauHash(), emailId)
                .orElseThrow(() -> new IllegalArgumentException("Die Antwortvorschau ist abgelaufen. Bitte neu erstellen."));
        if (!Instant.now().isBefore(snapshot.getGueltigBis()))
            throw new IllegalArgumentException("Die Antwortvorschau ist abgelaufen. Bitte neu erstellen.");
        EingabeAntwort antwort = pruefeAntwort(request);
        if (!snapshot.getSubject().equals(antwort.subject()) || !snapshot.getHtmlBody().equals(antwort.htmlBody())
                || !snapshot.getAnlagen().stream().map(EinkaufMailantwortVorschau.AnlageSnapshot::anlageId).toList().equals(antwort.anlageIds())
                || !snapshot.getKontoId().equals(basis.email().getKontoId())
                || !snapshot.getEinkaufTyp().equals(basis.zuordnung().getTyp())
                || !snapshot.getVorgangId().equals(basis.zuordnung().getVorgangId())
                || !Objects.equals(snapshot.getBeteiligungId(), basis.zuordnung().getBeteiligungId())
                || !Objects.equals(snapshot.getRevisionId(), basis.zuordnung().getRevisionId())
                || !snapshot.getSourceMessageId().equals(basis.email().getMessageId())
                || !snapshot.getInReplyTo().equals(basis.inReplyTo())
                || !snapshot.getReferences().equals(basis.references())
                || !snapshot.getEmpfaenger().equals(basis.empfaenger()))
            throw new IllegalStateException("Die E-Mail oder Antwort wurde seit der Vorschau geändert. Bitte neu prüfen.");
        if (!snapshot.getInhaltSha256().equals(antwortInhaltHash(basis, antwort, snapshot.getAnlagen())))
            throw new IllegalStateException("Der freigegebene Antwortinhalt wurde verändert. Bitte neu prüfen.");

        if (snapshot.getVersandId() != null) {
            var prior = versandAuftraege.findById(snapshot.getVersandId())
                    .orElseThrow(() -> new IllegalStateException("Der gespeicherte Antwortversand fehlt."));
            worker.dispatchNachCommit(prior.getId());
            return new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto(prior.getId(), prior.getVersion(),
                    prior.getTyp(), prior.getVorgangId(), prior.getRevisionId(), prior.getStatus().name(), prior.getFehlerCode(),
                    prior.getErstelltAm(), prior.getAngenommenAm(), prior.getArchiviertAm() != null, prior.getMessageId());
        }
        var attachments = dateien.ladeVersandanlagen(antwort.anlageIds());
        if (attachments.size() != snapshot.getAnlagen().size())
            throw new IllegalStateException("Die freigegebenen Anlagen haben sich geändert. Bitte neu prüfen.");
        for (int index = 0; index < attachments.size(); index++) {
            if (!snapshot.getAnlagen().get(index).sha256().equals(sha256(attachments.get(index).data())))
                throw new IllegalStateException("Eine freigegebene Anlage hat sich geändert. Bitte neu prüfen.");
        }
        dateien.pruefePaketgroesse(antwort.anlageIds(), 0);
        var nachricht = new Nachricht(null, basis.empfaenger(), antwort.subject(), antwort.htmlBody(), basis.inReplyTo(),
                basis.references(), attachments);
        var versandSnapshot = new VersandSnapshot("ANTWORT", emailId, basis.zuordnung().getRevisionId(),
                basis.zuordnung().getBeteiligungId(), new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.KontoZugangReferenz("EINKAUF"),
                nachricht, snapshot.getFreigabeToken());
        var versand = outbox.einreihen(versandSnapshot, request.idempotenzKey(), akteurId);
        snapshot.setVersandId(versand.id());
        antwortVorschauen.saveAndFlush(snapshot);
        worker.dispatchNachCommit(versand.id());
        return versand;
    }

    @Transactional
    public org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto antwortErneutSenden(
            Long emailId, Long versandId, VersandWiederholung request, Long akteurId) {
        if (akteurId == null || akteurId <= 0 || request == null || versandId == null || versandId <= 0)
            throw new IllegalArgumentException("Die Versandangaben sind ungültig.");
        AntwortBasis basis = ladeAntwortBasis(emailId, true);
        var snapshot = antwortVorschauen.findByVersandIdAndEmailIdForUpdate(versandId, emailId)
                .orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Der Einkaufsantwortversand wurde nicht gefunden."));
        pruefeAntwortBindung(snapshot, basis);
        var versand = outbox.kommunikationErneutVersuchen(versandId, request.version(), akteurId, "ANTWORT", emailId,
                basis.zuordnung().getRevisionId(), basis.zuordnung().getBeteiligungId());
        worker.dispatchNachCommit(versand.id());
        return versand;
    }

    @Transactional
    public void antwortKlaeren(Long emailId, Long versandId,
            org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.Klaerung request, Long akteurId) {
        if (akteurId == null || akteurId <= 0 || request == null || versandId == null || versandId <= 0)
            throw new IllegalArgumentException("Die Versandklärung ist ungültig.");
        AntwortBasis basis = ladeAntwortBasis(emailId, true);
        var snapshot = antwortVorschauen.findByVersandIdAndEmailIdForUpdate(versandId, emailId)
                .orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Der Einkaufsantwortversand wurde nicht gefunden."));
        pruefeAntwortBindung(snapshot, basis);
        outbox.kommunikationKlaeren(versandId, request, akteurId, "ANTWORT", emailId,
                basis.zuordnung().getRevisionId(), basis.zuordnung().getBeteiligungId(), mailantwortListener::verarbeite);
    }

    @Transactional(readOnly = true)
    public org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto antwortVersandstatus(Long emailId) {
        if (emailId == null || emailId <= 0) throw new IllegalArgumentException("Die E-Mail-ID ist ungültig.");
        var snapshot = antwortVorschauen.findFirstByEmailIdOrderByErstelltAmDesc(emailId).orElse(null);
        if (snapshot == null || snapshot.getVersandId() == null) return null;
        if (!"EINKAUF".equals(snapshot.getKontoId())) throw new org.example.kalkulationsprogramm.exception.NotFoundException("Die Antwort wurde nicht gefunden.");
        var versand = versandAuftraege.findById(snapshot.getVersandId())
                .orElseThrow(() -> new IllegalStateException("Der Antwortversand fehlt."));
        return new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto(versand.getId(), versand.getVersion(),
                versand.getTyp(), versand.getVorgangId(), versand.getRevisionId(), versand.getStatus().name(), versand.getFehlerCode(),
                versand.getErstelltAm(), versand.getAngenommenAm(), versand.getArchiviertAm() != null, versand.getMessageId());
    }

    private void pruefeAntwortBindung(EinkaufMailantwortVorschau snapshot, AntwortBasis basis) {
        if (!"EINKAUF".equals(snapshot.getKontoId()) || !snapshot.getKontoId().equals(basis.email().getKontoId())
                || !snapshot.getEinkaufTyp().equals(basis.zuordnung().getTyp())
                || !snapshot.getVorgangId().equals(basis.zuordnung().getVorgangId())
                || !Objects.equals(snapshot.getBeteiligungId(), basis.zuordnung().getBeteiligungId())
                || !Objects.equals(snapshot.getRevisionId(), basis.zuordnung().getRevisionId())
                || !snapshot.getSourceMessageId().equals(basis.email().getMessageId())
                || !snapshot.getInReplyTo().equals(basis.inReplyTo())
                || !snapshot.getReferences().equals(basis.references())
                || !snapshot.getEmpfaenger().equals(basis.empfaenger()))
            throw new IllegalStateException("Der Versand gehört nicht mehr zur aktuellen Einkaufszuordnung.");
        var input = new EingabeAntwort(snapshot.getSubject(), snapshot.getHtmlBody(), snapshot.getAnlagen().stream()
                .map(EinkaufMailantwortVorschau.AnlageSnapshot::anlageId).toList());
        if (!snapshot.getInhaltSha256().equals(antwortInhaltHash(basis, input, snapshot.getAnlagen())))
            throw new IllegalStateException("Der gespeicherte Antwortinhalt passt nicht mehr zum Einkaufsversand.");
    }

    private AntwortBasis ladeAntwortBasis(Long emailId) {
        return ladeAntwortBasis(emailId, false);
    }

    private AntwortBasis ladeAntwortBasis(Long emailId, boolean lockZuordnung) {
        if (emailId == null || emailId <= 0) throw new IllegalArgumentException("Die E-Mail-ID ist ungültig.");
        Email email = emails.findById(emailId).orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Die E-Mail wurde nicht gefunden."));
        if (!"EINKAUF".equals(email.getKontoId())) throw new org.example.kalkulationsprogramm.exception.NotFoundException("Die E-Mail wurde nicht gefunden.");
        EinkaufMailZuordnung zuordnung = (lockZuordnung ? zuordnungen.findByEmailIdForUpdate(emailId) : zuordnungen.findByEmailId(emailId))
                .filter(z -> z.getTyp() != null && z.getVorgangId() != null && !"PRUEFEN".equals(z.getStatus()))
                .orElseThrow(() -> new IllegalStateException("Diese Einkaufsnachricht muss zuerst eindeutig zugeordnet werden."));
        String recipient = email.getDirection() == EmailDirection.OUT ? email.getRecipient()
                : (email.getReplyToAddress() == null || email.getReplyToAddress().isBlank() ? email.getFromAddress() : email.getReplyToAddress());
        recipient = emailAddress(recipient);
        if (recipient == null || email.getMessageId() == null || !validMessageId(email.getMessageId()))
            throw new IllegalStateException("Absender oder Originalheader der Einkaufsnachricht sind ungültig.");
        List<String> references = parseReferences(email.getReferences());
        if (!references.contains(email.getMessageId())) references = java.util.stream.Stream.concat(references.stream(), java.util.stream.Stream.of(email.getMessageId())).toList();
        return new AntwortBasis(email, zuordnung, recipient, email.getMessageId(), references);
    }

    private EingabeAntwort pruefeAntwort(Antwort request) {
        if (request == null || request.subject() == null || request.subject().isBlank() || request.subject().length() > 998
                || request.subject().contains("\r") || request.subject().contains("\n")
                || request.htmlBody() == null || request.htmlBody().length() > 10000
                || request.anlageIds().size() > 50 || request.anlageIds().stream().anyMatch(id -> id == null || id <= 0)
                || request.anlageIds().stream().distinct().count() != request.anlageIds().size())
            throw new IllegalArgumentException("Betreff, Nachricht oder Anlagen der Einkaufsantwort sind ungültig.");
        String cleanHtml = org.example.kalkulationsprogramm.util.EmailHtmlSanitizer.sanitizeDetailHtml(request.htmlBody());
        return new EingabeAntwort(request.subject().trim(), cleanHtml, request.anlageIds());
    }

    private String antwortInhaltHash(AntwortBasis basis, EingabeAntwort antwort,
            List<EinkaufMailantwortVorschau.AnlageSnapshot> anlagen) {
        try {
            var payload = new AntwortInhalt(basis.email().getId(), basis.email().getKontoId(), basis.zuordnung().getTyp(),
                    basis.zuordnung().getVorgangId(), basis.zuordnung().getBeteiligungId(), basis.zuordnung().getRevisionId(),
                    basis.email().getMessageId(), basis.inReplyTo(), basis.references(), basis.empfaenger(), antwort.subject(),
                    antwort.htmlBody(), anlagen);
            return sha256(objectMapper.writeValueAsBytes(payload));
        } catch (Exception ex) { throw new IllegalStateException("Die Antwortvorschau konnte nicht gebunden werden.", ex); }
    }

    private static String emailAddress(String raw) {
        if (raw == null) return null;
        try {
            var addresses = jakarta.mail.internet.InternetAddress.parse(raw, true);
            if (addresses.length != 1 || addresses[0].getAddress() == null || !addresses[0].getAddress().contains("@")) return null;
            return addresses[0].getAddress();
        } catch (Exception ex) { return null; }
    }
    private static boolean validMessageId(String value) { return value.length() <= 512 && value.matches("<[^<>\\s]{1,500}>"); }
    private static List<String> parseReferences(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.replace('\r', ' ').replace('\n', ' ').trim().split("\\s+"))
                .filter(EinkaufKommunikationService::validMessageId).distinct().toList();
    }
    private record AntwortBasis(Email email, EinkaufMailZuordnung zuordnung, String empfaenger, String inReplyTo, List<String> references) {}
    private record EingabeAntwort(String subject, String htmlBody, List<Long> anlageIds) {}
    private record AntwortInhalt(Long emailId, String kontoId, String typ, Long vorgangId, Long beteiligungId, Long revisionId,
            String sourceMessageId, String inReplyTo, List<String> references, String empfaenger, String subject, String htmlBody,
            List<EinkaufMailantwortVorschau.AnlageSnapshot> anlagen) {}

    @Transactional(readOnly = true)
    public List<BeteiligungsVersand> versandstatus(Long anfrageId, Long revisionId) {
        if (anfrageId == null || anfrageId <= 0 || revisionId == null || revisionId <= 0)
            throw new IllegalArgumentException("Anfrage und Revision sind ungültig.");
        var revision = revisionen.findByIdAndAnfrageId(revisionId, anfrageId)
                .orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Die Anfragefassung wurde nicht gefunden."));
        if (revision.getAnfrage().isGeloescht()) throw new org.example.kalkulationsprogramm.exception.NotFoundException("Die Anfrage wurde nicht gefunden.");
        return outbox.anfrageStatus(anfrageId, revisionId);
    }

    @Transactional
    public org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.VersandDto erneutSenden(
            Long anfrageId, Long beteiligungId, Long versandId, VersandWiederholung request, Long akteurId) {
        if (anfrageId == null || anfrageId <= 0 || request == null || request.version() < 0)
            throw new IllegalArgumentException("Die Versandwiederholung ist ungültig.");
        anfragen.findByIdForUpdate(anfrageId)
                .orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Die Anfrage wurde nicht gefunden."));
        var basis = ladeBasis(anfrageId, beteiligungId);
        var result = outbox.anfrageErneutVersuchen(versandId, request.version(), akteurId,
                anfrageId, basis.revision().getId(), beteiligungId);
        worker.dispatchNachCommit(result.id());
        return result;
    }

    @Transactional(readOnly = true)
    public Page<NachrichtDto> verlauf(String typ, Long vorgangId, Pageable pageable) {
        if (typ == null || !List.of("ANFRAGE", "BESTELLUNG").contains(typ) || vorgangId == null || vorgangId <= 0 || pageable == null)
            throw new IllegalArgumentException("Vorgang oder Seitenauswahl ist ungültig.");
        var seite = zuordnungen.findAllByTypAndVorgangId(typ, vorgangId, pageable);
        var nachrichten = emails.findAllById(seite.stream().map(link -> link.getEmailId()).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Email::getId, java.util.function.Function.identity()));
        return seite.map(link -> {
            var e = nachrichten.get(link.getEmailId());
            return e == null ? null : new NachrichtDto(e.getId(), e.getMessageId(), e.getSubject(), e.getFromAddress(), e.getSentAt(),
                    link.getTyp(), link.getVorgangId(), link.getBeteiligungId(), link.getRevisionId(), link.getStatus(), link.getQuelle());
        });
    }

    /** Bestellverlauf bleibt an dieselbe paginierte Zuordnungsquelle gebunden wie der Anfrageverlauf. */
    @Transactional(readOnly = true)
    public Page<NachrichtDto> bestellverlauf(Long bestellungId, Pageable pageable) {
        return verlauf("BESTELLUNG", bestellungId, pageable);
    }

    private VersandBasis ladeBasis(Long anfrageId, Long beteiligungId) {
        if (anfrageId == null || anfrageId <= 0 || beteiligungId == null || beteiligungId <= 0)
            throw new IllegalArgumentException("Anfrage und Lieferantenbeteiligung sind ungültig.");
        Einkaufsanfrage anfrage = anfragen.findById(anfrageId).orElseThrow(() -> new java.util.NoSuchElementException("Anfrage nicht gefunden."));
        AnfrageRevision revision = anfrage.getAktuelleRevision();
        if (anfrage.isGeloescht() || revision == null) throw new IllegalStateException("Die Anfrage kann nicht versendet werden.");
        AnfrageLieferant beteiligung = beteiligungen.findByIdAndRevisionAnfrageId(beteiligungId, anfrageId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Lieferantenbeteiligung nicht gefunden."));
        if (!revision.getId().equals(beteiligung.getRevision().getId())) throw new IllegalStateException("Nur Lieferanten der aktuellen Anfragefassung können versendet werden.");
        if (!List.of("AUSSTEHEND").contains(beteiligung.getStatus())) throw new IllegalStateException("Diese Lieferantenanfrage wurde bereits beantwortet oder versendet.");
        return new VersandBasis(anfrage, revision, beteiligung);
    }
    private record VersandBasis(Einkaufsanfrage anfrage, AnfrageRevision revision, AnfrageLieferant beteiligung) {}
    private record VorschauDaten(VersandBasis basis, Vorschau vorschau, List<EmailService.Attachment> anlagen) {}
    private record VorschauInhalt(Long revisionId, Long templateId, Long templateVersion, String subject, String html,
            String recipient, List<Long> attachmentIds, Long pdfId, String pdfHash) {}
    private static String safe(String value) { return value == null ? "" : value; }
    private static String value(java.time.LocalDate value) { return value == null ? "" : value.toString(); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 ist nicht verfügbar.", ex); }
    }
    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String token() {
        byte[] bytes = new byte[32];
        TOKEN_GENERATOR.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
    private String inhaltHash(Long revisionId, Long templateId, Long templateVersion, String subject, String html,
            String recipient, List<Long> attachmentIds, Long pdfId, String pdfHash) {
        try {
            return sha256(objectMapper.writeValueAsBytes(new VorschauInhalt(revisionId, templateId, templateVersion,
                    subject, html, recipient, List.copyOf(attachmentIds), pdfId, pdfHash)));
        } catch (Exception ex) {
            throw new IllegalStateException("Der Vorschauinhalt konnte nicht gebunden werden.", ex);
        }
    }
}
