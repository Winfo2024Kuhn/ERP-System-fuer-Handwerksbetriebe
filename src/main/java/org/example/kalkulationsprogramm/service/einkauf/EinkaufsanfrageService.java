package org.example.kalkulationsprogramm.service.einkauf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufsanfrageDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufKontaktDto.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.example.kalkulationsprogramm.service.DokumentnummerService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufAnfrageMengenProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EinkaufsanfrageService implements EinkaufAnfrageMengenProvider {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$");
    private final EinkaufsanfrageRepository anfragen;
    private final AnfrageRevisionRepository revisionen;
    private final AnfrageLieferantRepository lieferanten;
    private final EinkaufBedarfRepository bedarfe;
    private final DokumentnummerService nummern;
    private final EinkaufPositionService positionService;
    private final EinkaufDateiService dateien;
    private final ObjectMapper objectMapper;
    private final EinkaufAuditService audit;
    private final LieferantEinkaufKontaktService kontakte;

    public EinkaufsanfrageService(EinkaufsanfrageRepository anfragen, AnfrageRevisionRepository revisionen,
            AnfrageLieferantRepository lieferanten, EinkaufBedarfRepository bedarfe,
            DokumentnummerService nummern, EinkaufPositionService positionService, EinkaufDateiService dateien,
            ObjectMapper objectMapper, EinkaufAuditService audit, LieferantEinkaufKontaktService kontakte) {
        this.anfragen = anfragen; this.revisionen = revisionen; this.lieferanten = lieferanten;
        this.bedarfe = bedarfe; this.nummern = nummern; this.positionService = positionService;
        this.dateien = dateien; this.objectMapper = objectMapper; this.audit = audit; this.kontakte = kontakte;
    }

    @Transactional
    public Detail anlegen(Create request, Long akteurId) {
        validiereAkteur(akteurId);
        validiere(request);
        String hash = hash(request);
        Optional<Einkaufsanfrage> wiederholung = anfragen.findByIdempotenzKey(request.idempotenzKey());
        if (wiederholung.isPresent()) {
            if (!hash.equals(wiederholung.get().getPayloadHash())) throw conflict("Der Idempotenzschlüssel wurde bereits mit anderen Anfragedaten verwendet.");
            return toDetail(wiederholung.get(), wiederholung.get().getAktuelleRevision());
        }
        List<EinkaufBedarf> gesperrt = bedarfe.findeAlleFuerUpdate(request.positionen().stream()
                .map(Herkunft::bedarfId).distinct().sorted().toList());
        if (gesperrt.size() != request.positionen().stream().map(Herkunft::bedarfId).distinct().count())
            throw new org.example.kalkulationsprogramm.exception.NotFoundException("Mindestens ein Einkaufsbedarf wurde nicht gefunden.");
        Map<Long, EinkaufBedarf> nachId = gesperrt.stream().collect(Collectors.toMap(EinkaufBedarf::getId, b -> b));
        List<ValidiertePosition> snapshots = new ArrayList<>();
        for (Herkunft herkunft : request.positionen()) snapshots.add(validiereHerkunft(herkunft, nachId));
        List<Snapshot> empfaenger = validiereEmpfaenger(request.empfaenger());
        LocalDate heute = LocalDate.now();
        Einkaufsanfrage kopf = anfragen.saveAndFlush(new Einkaufsanfrage(
                nummern.naechsteEinkaufsnummer("PA", heute), request.zustaendigId(), request.idempotenzKey(), hash));
        AnfrageRevision revision = revisionen.saveAndFlush(new AnfrageRevision(kopf, 1, request.antwortfrist(), request.liefertermin(), request.idempotenzKey(), hash));
        persistiereInhalt(revision, snapshots, empfaenger);
        kopf.setAktuelleRevision(revision);
        kopf = anfragen.saveAndFlush(kopf);
        audit.protokolliere("EINKAUFSANFRAGE", kopf.getId(), "ANFRAGE_ANGELEGT", akteurId, null,
                objectMapper.valueToTree(new Kopf(kopf.getId(), kopf.getVersion() == null ? 0 : kopf.getVersion(), kopf.getPaNummer(), kopf.getZustaendigId(), revision.getId(), 1, revision.getStatus(), revision.getAntwortfrist(), revision.getLiefertermin())), null);
        return toDetail(kopf, revision);
    }

    @Transactional
    public Detail revidieren(Long id, RevisionRequest request, Long akteurId) {
        validiereAkteur(akteurId);
        if (id == null || id <= 0 || request == null || request.inhalt() == null || request.version() < 0)
            throw new IllegalArgumentException("Die Anfragefassung ist ungültig.");
        validiere(request.inhalt());
        String hash = hash(request.inhalt());
        Optional<AnfrageRevision> wiederholung = revisionen.findByIdempotenzKey(request.inhalt().idempotenzKey());
        if (wiederholung.isPresent()) {
            if (!hash.equals(wiederholung.get().getPayloadHash())) throw conflict("Der Idempotenzschlüssel wurde bereits mit anderen Anfragedaten verwendet.");
            if (!Objects.equals(wiederholung.get().getAnfrage().getId(), id)) throw conflict("Dieser Idempotenzschlüssel gehört zu einer anderen Anfrage.");
            return laden(id);
        }
        Einkaufsanfrage kopf = anfragen.findById(id).orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Die Anfrage wurde nicht gefunden."));
        if (!Objects.equals(kopf.getVersion(), request.version())) throw conflict("Die Anfrage wurde zwischenzeitlich geändert. Bitte neu laden.");
        List<EinkaufBedarf> gesperrt = bedarfe.findeAlleFuerUpdate(request.inhalt().positionen().stream()
                .map(Herkunft::bedarfId).distinct().sorted().toList());
        if (gesperrt.size() != request.inhalt().positionen().stream().map(Herkunft::bedarfId).distinct().count())
            throw new org.example.kalkulationsprogramm.exception.NotFoundException("Mindestens ein Einkaufsbedarf wurde nicht gefunden.");
        Map<Long, EinkaufBedarf> nachId = gesperrt.stream().collect(Collectors.toMap(EinkaufBedarf::getId, b -> b));
        List<ValidiertePosition> snapshots = request.inhalt().positionen().stream().map(h -> validiereHerkunft(h, nachId)).toList();
        List<Snapshot> empfaenger = validiereEmpfaenger(request.inhalt().empfaenger());
        kopf = anfragen.findByIdForUpdate(id).orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Die Anfrage wurde nicht gefunden."));
        if (!Objects.equals(kopf.getVersion(), request.version())) throw conflict("Die Anfrage wurde zwischenzeitlich geändert. Bitte neu laden.");
        AnfrageRevision alt = kopf.getAktuelleRevision();
        Long alterZustaendiger = kopf.getZustaendigId();
        AnfrageRevision neu = revisionen.saveAndFlush(new AnfrageRevision(kopf,
                (alt == null ? 0 : alt.getNummer()) + 1, request.inhalt().antwortfrist(), request.inhalt().liefertermin(),
                request.inhalt().idempotenzKey(), hash));
        persistiereInhalt(neu, snapshots, empfaenger);
        kopf.setZustaendigId(request.inhalt().zustaendigId());
        kopf.setAktuelleRevision(neu);
        kopf = anfragen.saveAndFlush(kopf);
        audit.protokolliere("EINKAUFSANFRAGE", id, "ANFRAGE_REVISION_ANGELEGT", akteurId,
                objectMapper.valueToTree(alt == null ? null : new Kopf(id, request.version(), kopf.getPaNummer(), alterZustaendiger, alt.getId(), alt.getNummer(), alt.getStatus(), alt.getAntwortfrist(), alt.getLiefertermin())),
                objectMapper.valueToTree(new Kopf(id, kopf.getVersion(), kopf.getPaNummer(), kopf.getZustaendigId(), neu.getId(), neu.getNummer(), neu.getStatus(), neu.getAntwortfrist(), neu.getLiefertermin())), null);
        return toDetail(kopf, neu);
    }

    public Detail laden(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("Die Anfrage-ID ist ungültig.");
        Einkaufsanfrage kopf = anfragen.findById(id).orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException("Die Anfrage wurde nicht gefunden."));
        return kopf.getAktuelleRevision() == null ? new Detail(toKopf(kopf, null), List.of(), List.of()) : toDetail(kopf, kopf.getAktuelleRevision());
    }

    public Page<Kopf> suchen(Pageable pageable) {
        if (pageable == null) throw new IllegalArgumentException("Die Seitenauswahl fehlt.");
        return anfragen.findAllByOrderByAngelegtAmDesc(pageable).map(k -> toKopf(k, k.getAktuelleRevision()));
    }

    @Override
    public Map<Long, BigDecimal> angefragtFuerBedarfe(Collection<Long> bedarfIds) {
        if (bedarfIds == null || bedarfIds.isEmpty()) return Map.of();
        List<Long> ids = bedarfIds.stream().filter(Objects::nonNull).filter(i -> i > 0).distinct().sorted().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : revisionen.summenAktuelleAnfragen(ids)) result.put((Long) row[0], (BigDecimal) row[1]);
        return Map.copyOf(result);
    }

    private ValidiertePosition validiereHerkunft(Herkunft h, Map<Long, EinkaufBedarf> nachId) {
        if (h == null || h.bedarfId() == null || h.bedarfId() <= 0 || h.version() < 0 || h.menge() == null || h.menge().signum() <= 0 || h.menge().scale() > 6)
            throw new IllegalArgumentException("Bitte geben Sie gültige Bedarfsanteile und positive Mengen an.");
        EinkaufBedarf b = nachId.get(h.bedarfId());
        if (b == null) throw new org.example.kalkulationsprogramm.exception.NotFoundException("Der Einkaufsbedarf wurde nicht gefunden.");
        if (b.getVersion() == null || b.getVersion() != h.version()) throw conflict("Der Einkaufsbedarf wurde zwischenzeitlich geändert.");
        if (b.isNachpflegeErforderlich() || b.getBedarfMenge() == null) throw conflict("Dieser Bedarf muss zuerst nachgepflegt werden.");
        if (h.menge().compareTo(b.disponierbar()) > 0) throw conflict("Die angefragte Teilmenge überschreitet den aktuell disponierbaren Bedarf.");
        PositionSnapshot source = b.getPosition();
        Long projectId = b.getLiefergruppe() == null ? null : b.getLiefergruppe().projektId();
        if (source == null || source.art() == Positionsart.ZEICHNUNGSTEIL
                && (projectId == null || !Objects.equals(projectId, b.getProjektId())))
            throw conflict("Die Projektzuordnung des Zeichnungsteils konnte nicht bestätigt werden.");
        PositionSnapshot checked = positionService.validiere(source, projectId);
        if (checked.art() == Positionsart.ZEICHNUNGSTEIL) dateien.pruefeFreigegebeneBedarfsanlagen(b.getId(), checked.anlageVersionIds());
        PositionSnapshot partial = positionService.mitTeilmenge(checked, h.menge());
        return new ValidiertePosition(b, h, partial);
    }

    private List<Snapshot> validiereEmpfaenger(List<Snapshot> inputs) {
        if (inputs == null) return List.of();
        Set<Long> seen = new HashSet<>();
        List<Snapshot> results = new ArrayList<>();
        for (Snapshot input : inputs) {
            if (input == null || input.lieferantId() == null || input.lieferantId() <= 0 || input.kontaktId() == null || input.kontaktId() <= 0
                    || !seen.add(input.lieferantId())) throw new IllegalArgumentException("Jeder Lieferant darf nur einmal als gültiger Einkaufskontakt ausgewählt werden.");
            Snapshot verified = kontakte.snapshot(input.lieferantId(), input.kontaktId(), input.email(), KontaktZweck.ANFRAGE);
            if (verified.email() == null || verified.email().length() > 254 || !EMAIL.matcher(verified.email()).matches())
                throw new IllegalArgumentException("Bitte wählen Sie einen gültigen Einkaufskontakt.");
            results.add(verified);
        }
        return List.copyOf(results);
    }

    private void persistiereInhalt(AnfrageRevision revision, List<ValidiertePosition> positions, List<Snapshot> recipients) {
        for (ValidiertePosition p : positions) {
            AnfragePosition zeile = new AnfragePosition(revision, p.snapshot(), p.herkunft().menge());
            zeile.addHerkunft(new AnfrageHerkunft(zeile, p.bedarf(), p.herkunft().version(), p.herkunft().menge()));
            revision.addPosition(zeile);
        }
        for (Snapshot recipient : recipients) revision.addLieferant(new AnfrageLieferant(revision, recipient));
        revisionen.saveAndFlush(revision);
    }

    private Detail toDetail(Einkaufsanfrage head, AnfrageRevision revision) {
        List<Positionszeile> positions = revision.getPositionen().stream().map(p -> new Positionszeile(p.getId(), p.getSnapshot(),
                p.getHerkuenfte().stream().map(h -> new Herkunft(h.getBedarf().getId(), h.getBedarfVersion(), h.getMenge())).toList())).toList();
        List<Lieferantenbeteiligung> participations = revision.getLieferanten().stream()
                .map(l -> new Lieferantenbeteiligung(l.getId(), l.getKontakt().lieferantId(), l.getKontakt().lieferantenname(), l.getStatus())).toList();
        return new Detail(toKopf(head, revision), positions, participations);
    }
    private Kopf toKopf(Einkaufsanfrage h, AnfrageRevision r) {
        return new Kopf(h.getId(), h.getVersion() == null ? 0 : h.getVersion(), h.getPaNummer(), h.getZustaendigId(),
                r == null ? null : r.getId(), r == null ? 0 : r.getNummer(), r == null ? "ENTWURF" : r.getStatus(),
                r == null ? null : r.getAntwortfrist(), r == null ? null : r.getLiefertermin());
    }
    private static void validiere(Create r) {
        if (r == null || r.idempotenzKey() == null || r.positionen() == null || r.positionen().isEmpty()) throw new IllegalArgumentException("Bitte wählen Sie mindestens einen Bedarf und einen Idempotenzschlüssel.");
        if (r.zustaendigId() != null && r.zustaendigId() <= 0) throw new IllegalArgumentException("Die zuständige Person ist ungültig.");
        if (r.positionen().stream().anyMatch(Objects::isNull) || r.positionen().stream().map(Herkunft::bedarfId).distinct().count() != r.positionen().size()) throw new IllegalArgumentException("Jeder Bedarf darf nur einmal enthalten sein.");
        if (r.empfaenger() == null) throw new IllegalArgumentException("Die Lieferantenauswahl ist ungültig.");
        if (r.antwortfrist() != null && r.liefertermin() != null && r.antwortfrist().isAfter(r.liefertermin())) throw new IllegalArgumentException("Die Antwortfrist darf nicht nach dem Liefertermin liegen.");
    }
    private static void validiereAkteur(Long id) { if (id == null || id <= 0) throw new IllegalArgumentException("Der handelnde Benutzer fehlt."); }
    private String hash(Create r) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(r))); }
        catch (NoSuchAlgorithmException | JsonProcessingException e) { throw new IllegalStateException("Die Anfragedaten konnten nicht geprüft werden.", e); }
    }
    private static ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }
    private record ValidiertePosition(EinkaufBedarf bedarf, Herkunft herkunft, PositionSnapshot snapshot) {}
}
