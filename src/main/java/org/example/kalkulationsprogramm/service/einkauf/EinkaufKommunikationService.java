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
    private final EinkaufVorlagenService vorlagen;
    private final EinkaufPdfService pdf;
    private final EinkaufDateiService dateien;
    private final EinkaufOutboxService outbox;
    private final EinkaufVersandWorker worker;
    private final ObjectMapper objectMapper;

    public EinkaufKommunikationService(EinkaufsanfrageRepository anfragen, AnfrageRevisionRepository revisionen,
            AnfrageLieferantRepository beteiligungen, EmailRepository emails, EinkaufMailZuordnungRepository zuordnungen,
            EinkaufKommunikationVorschauRepository vorschauen, EinkaufVorlagenService vorlagen,
            EinkaufPdfService pdf, EinkaufDateiService dateien,
            EinkaufOutboxService outbox, EinkaufVersandWorker worker, ObjectMapper objectMapper) {
        this.anfragen = anfragen; this.revisionen = revisionen; this.beteiligungen = beteiligungen;
        this.emails = emails; this.zuordnungen = zuordnungen; this.vorschauen = vorschauen;
        this.vorlagen = vorlagen; this.pdf = pdf;
        this.dateien = dateien; this.outbox = outbox; this.worker = worker; this.objectMapper = objectMapper;
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
