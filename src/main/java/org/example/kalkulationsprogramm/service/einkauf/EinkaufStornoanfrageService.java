package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufStornoanfrageDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufVersandDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.util.HtmlUtils;

/** Immutable communication snapshots; a cancellation request never changes committed quantities. */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class EinkaufStornoanfrageService {
    private static final String TYP = "STORNO_ANFRAGE";
    private static final String VORSCHAU = "STORNOANFRAGE_VORSCHAU";
    private final EinkaufBestellungRepository bestellungen;
    private final BestellungRevisionRepository revisionen;
    private final EinkaufBedarfRepository bedarfe;
    private final EinkaufMengenService mengen;
    private final EinkaufAuditRepository audits;
    private final EinkaufVersandauftragRepository auftraege;
    private final EinkaufOutboxService outbox;
    private final EinkaufVersandWorker worker;
    private final ObjectMapper json;
    private final EinkaufStornoanfrageAnnahmeListener annahmen;

    public Vorschau vorschau(Long id, Entwurf entwurf, Long akteur) {
        var bestellung = sperre(id, akteur);
        var revision = pruefe(bestellung, entwurf);
        String subject = "Bitte um Stornierung zu Bestellung " + bestellung.getNummer();
        StringBuilder html = new StringBuilder("<p>Bitte bestätigen Sie die Stornierung folgender noch offener Mengen:</p><ul>");
        for (var anteil : entwurf.anteile()) {
            var position = revision.getPositionen().stream().filter(p -> p.getHerkuenfte().stream()
                    .anyMatch(h -> h.getBedarfId().equals(anteil.bedarfId()))).findFirst().orElseThrow();
            String bezeichnung = position.getPosition().bezeichnung();
            html.append("<li>").append(HtmlUtils.htmlEscape(bezeichnung == null ? "Material" : bezeichnung)).append(": ")
                    .append(HtmlUtils.htmlEscape(anteil.menge().stripTrailingZeros().toPlainString().replace('.', ',')))
                    .append(" ").append(HtmlUtils.htmlEscape(position.getPosition().basis().einheit().name())).append("</li>");
        }
        html.append("</ul><p>").append(HtmlUtils.htmlEscape(entwurf.grund())).append("</p>");
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        ObjectNode snapshot = json.createObjectNode();
        snapshot.set("entwurf", json.valueToTree(entwurf)); snapshot.put("revisionId", revision.getId());
        snapshot.put("token", token); snapshot.put("subject", subject); snapshot.put("htmlBody", html.toString());
        snapshot.put("empfaenger", bestellung.getEmpfaenger().email());
        snapshot.put("gueltigBis", Instant.now().plus(Duration.ofHours(24)).toString());
        var audit = audits.save(new EinkaufAudit("BESTELLUNG", id, VORSCHAU, akteur, null, snapshot, "Stornoanfrage zur Prüfung vorbereitet"));
        return new Vorschau(entwurf.version(), audit.getId(), token, subject, html.toString(), bestellung.getEmpfaenger().email(), revision.getId());
    }

    public VersandDto freigeben(Long id, Freigabe freigabe, Long akteur) {
        if (freigabe == null || freigabe.vorschauId() == null || freigabe.vorschauId() <= 0 || freigabe.idempotenzKey() == null
                || freigabe.vorschauHash() == null || freigabe.vorschauHash().length() > 128)
            throw new IllegalArgumentException("Bitte die Stornoanfrage zuerst prüfen.");
        var bestellung = sperre(id, akteur);
        var preview = audits.findById(freigabe.vorschauId()).orElseThrow(() -> new NotFoundException("Vorschau nicht gefunden."));
        if (!"BESTELLUNG".equals(preview.getVorgangTyp()) || !id.equals(preview.getVorgangId())
                || !VORSCHAU.equals(preview.getAktion()) || !akteur.equals(preview.getAkteurId()))
            throw new NotFoundException("Vorschau gehört nicht zu dieser Stornoanfrage.");
        var snapshot = preview.getNachherSnapshot();
        if (!freigabe.vorschauHash().equals(snapshot.path("token").asText())) throw new IllegalStateException("Die Vorschau stimmt nicht überein.");
        var vorhandener = auftraege.findByTypAndVorgangIdAndFreigabeHash(TYP, id, freigabe.vorschauHash());
        if (vorhandener.isPresent()) {
            if (!freigabe.idempotenzKey().equals(vorhandener.get().getIdempotenzKey()))
                throw new IllegalStateException("Diese Stornoanfrage hat bereits einen Versandauftrag. Bitte dessen Status prüfen.");
            return dto(vorhandener.get());
        }
        if (!Instant.now().isBefore(Instant.parse(snapshot.path("gueltigBis").asText())))
            throw new IllegalStateException("Die Vorschau ist abgelaufen. Bitte neu prüfen.");
        var entwurf = json.convertValue(snapshot.get("entwurf"), Entwurf.class);
        var revision = pruefe(bestellung, entwurf);
        if (freigabe.version() != entwurf.version() || revision.getId() != snapshot.path("revisionId").asLong()
                || !Objects.equals(bestellung.getEmpfaenger().email(), snapshot.path("empfaenger").asText()))
            throw new IllegalStateException("Die Bestellung wurde geändert. Bitte erneut prüfen.");
        var nachricht = new Nachricht(null, snapshot.path("empfaenger").asText(), snapshot.path("subject").asText(),
                snapshot.path("htmlBody").asText(), null, List.of(), List.of());
        var result = outbox.einreihen(new VersandSnapshot(TYP, id, revision.getId(), null,
                new KontoZugangReferenz("EINKAUF"), nachricht, freigabe.vorschauHash()), freigabe.idempotenzKey(), akteur);
        audits.save(new EinkaufAudit("BESTELLUNG", id, "STORNOANFRAGE_FREIGEGEBEN", akteur, null, json.valueToTree(result), entwurf.grund()));
        worker.dispatchNachCommit(result.id());
        return result;
    }

    @Transactional(readOnly = true)
    public List<VersandDto> status(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Die Bestellung ist ungültig.");
        if (!bestellungen.existsById(id)) throw new NotFoundException("Bestellung nicht gefunden.");
        return auftraege.leseVorgangStatus(TYP, id).stream().map(a -> new VersandDto(a.getId(), a.getVersion(), a.getTyp(),
                a.getVorgangId(), a.getRevisionId(), a.getStatus().name(), a.getFehlerCode(), a.getErstelltAm(),
                a.getAngenommenAm(), a.getArchiviertAm() != null, a.getMessageId())).toList();
    }

    public VersandDto erneutSenden(Long id, Long versandId, long version, Long akteur) {
        sperre(id, akteur);
        var auftrag = eigenerAuftrag(id, versandId);
        if (!revisionen.findFirstByBestellung_IdOrderByNummerDesc(id).orElseThrow().getId().equals(auftrag.getRevisionId()))
            throw new IllegalStateException("Die Bestellfassung wurde geändert. Bitte die Stornoanfrage neu prüfen.");
        var result = outbox.kommunikationErneutVersuchen(versandId, version, akteur, TYP, id, auftrag.getRevisionId(), null);
        worker.dispatchNachCommit(result.id());
        return result;
    }
    public void klaeren(Long id, Long versandId, Klaerung klaerung, Long akteur) {
        sperre(id, akteur);
        var auftrag = eigenerAuftrag(id, versandId);
        outbox.kommunikationKlaeren(versandId, klaerung, akteur, TYP, id, auftrag.getRevisionId(), null, annahmen::verarbeite);
    }
    private EinkaufVersandauftrag eigenerAuftrag(Long id, Long versandId) {
        if (versandId == null || versandId <= 0) throw new IllegalArgumentException("Der Versandauftrag ist ungültig.");
        var auftrag = auftraege.findById(versandId).orElseThrow(() -> new NotFoundException("Versandauftrag nicht gefunden."));
        if (!TYP.equals(auftrag.getTyp()) || !id.equals(auftrag.getVorgangId()))
            throw new NotFoundException("Der Versandauftrag gehört nicht zu dieser Stornoanfrage.");
        return auftrag;
    }

    private EinkaufBestellung sperre(Long id, Long akteur) {
        if (id == null || id <= 0 || akteur == null || akteur <= 0) throw new IllegalArgumentException("Bestellung und Benutzer sind erforderlich.");
        return EinkaufBestellSperren.sperre(id, List.of(), bestellungen, revisionen, bedarfe);
    }
    private BestellungRevision pruefe(EinkaufBestellung bestellung, Entwurf entwurf) {
        if (entwurf == null || entwurf.grund() == null || entwurf.grund().isBlank() || entwurf.grund().length() > 1000
                || entwurf.anteile() == null || entwurf.anteile().isEmpty() || entwurf.anteile().size() > 1000)
            throw new IllegalArgumentException("Bitte offene Mengen und eine Begründung angeben.");
        if (bestellung.getVersion() == null || bestellung.getVersion() != entwurf.version())
            throw new IllegalStateException("Die Bestellung wurde geändert. Bitte neu prüfen.");
        var revision = revisionen.findFirstByBestellung_IdOrderByNummerDesc(bestellung.getId()).orElseThrow();
        if (!revision.istAngenommen()) throw new IllegalStateException("Bitte die ausstehende Bestellfassung zuerst klären.");
        var stand = mengen.standFuerVorgang("BESTELLUNG:" + bestellung.getId());
        Set<Long> ids = new HashSet<>();
        for (var anteil : entwurf.anteile()) {
            if (anteil == null || anteil.bedarfId() == null || !ids.add(anteil.bedarfId()) || anteil.menge() == null
                    || anteil.menge().signum() <= 0 || !stand.containsKey(anteil.bedarfId()))
                throw new IllegalArgumentException("Die Stornomengen sind ungültig.");
            if (anteil.menge().compareTo(stand.get(anteil.bedarfId()).offen()) > 0)
                throw new IllegalStateException("Die angefragte Stornomenge ist nicht mehr offen.");
        }
        return revision;
    }
    private VersandDto dto(EinkaufVersandauftrag a) {
        return new VersandDto(a.getId(), a.getVersion(), a.getTyp(), a.getVorgangId(), a.getRevisionId(), a.getStatus().name(),
                a.getFehlerCode(), a.getErstelltAm(), a.getAngenommenAm(), a.getArchiviertAm() != null, a.getMessageId());
    }
}
