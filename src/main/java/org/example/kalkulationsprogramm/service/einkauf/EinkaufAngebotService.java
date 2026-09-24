package org.example.kalkulationsprogramm.service.einkauf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.einkauf.*;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufAngebotDto.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EinkaufAngebotService {
    private static final Set<String> ARTen = Set.of("MATERIAL", "FRACHT", "ZUSCHNITT", "VERPACKUNG", "MINDERMENGE", "ZEUGNIS", "LEGIERUNG", "SCHROTT", "ENERGIE", "MENGE", "GUETE", "BEARBEITUNG", "OBERFLAECHE", "RABATT");
    private static final Set<String> BASEN = Set.of("STUECK", "100STUECK", "M", "KG", "100KG", "T", "PROZENT", "PAUSCHAL");
    private final EinkaufAngebotRepository angebote;
    private final AngebotVersionRepository versionen;
    private final AnfrageLieferantRepository beteiligungen;
    private final AnfrageRevisionRepository revisionen;
    private final EmailRepository emails;
    private final EinkaufDateiRepository dateien;
    private final org.example.kalkulationsprogramm.repository.EmailAttachmentRepository emailAttachments;
    private final org.example.kalkulationsprogramm.repository.LieferantDokumentRepository lieferantDokumente;
    private final EinkaufMailZuordnungRepository mailZuordnungen;

    public EinkaufAngebotService(EinkaufAngebotRepository angebote, AngebotVersionRepository versionen,
            AnfrageLieferantRepository beteiligungen, AnfrageRevisionRepository revisionen,
            EmailRepository emails, EinkaufDateiRepository dateien,
            org.example.kalkulationsprogramm.repository.EmailAttachmentRepository emailAttachments,
            org.example.kalkulationsprogramm.repository.LieferantDokumentRepository lieferantDokumente,
            EinkaufMailZuordnungRepository mailZuordnungen) {
        this.angebote = angebote; this.versionen = versionen; this.beteiligungen = beteiligungen;
        this.revisionen = revisionen; this.emails = emails; this.dateien = dateien;
        this.emailAttachments = emailAttachments; this.lieferantDokumente = lieferantDokumente;
        this.mailZuordnungen = mailZuordnungen;
    }

    @Transactional
    public VersionDto erfassen(Long beteiligungId, Erfassung request, Long akteurId) {
        if (beteiligungId == null || beteiligungId <= 0) throw new IllegalArgumentException("Die Lieferantenbeteiligung ist ungültig.");
        AnfrageLieferant beteiligung = beteiligungen.findById(beteiligungId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Lieferantenbeteiligung nicht gefunden."));
        EinkaufAngebot angebot = angebote.findByBeteiligungId(beteiligungId).orElseGet(() -> angebote.saveAndFlush(new EinkaufAngebot(beteiligung)));
        if (!angebot.getVersionen().isEmpty()) throw new IllegalStateException("Für diesen Lieferanten liegt bereits ein Angebot vor. Bitte eine neue Version erfassen.");
        return legeVersionAn(angebot, request, akteurId);
    }

    @Transactional
    public VersionDto neueVersion(Long angebotId, Erfassung request, Long akteurId) {
        if (angebotId == null || angebotId <= 0) throw new IllegalArgumentException("Das Angebot ist ungültig.");
        EinkaufAngebot angebot = angebote.findById(angebotId).orElseThrow(() -> new java.util.NoSuchElementException("Angebot nicht gefunden."));
        AngebotVersion latest = versionen.findFirstByAngebotIdOrderByNummerDesc(angebotId).orElse(null);
        if (latest != null) latest.abloesen();
        return legeVersionAn(angebot, request, akteurId);
    }

    @Transactional
    public VersionDto bestaetigen(Long versionId, long erwarteteVersion, Long akteurId) {
        if (versionId == null || versionId <= 0 || erwarteteVersion < 0 || akteurId == null || akteurId <= 0)
            throw new IllegalArgumentException("Angebot, Version und Benutzer sind erforderlich.");
        AngebotVersion version = versionen.findById(versionId).orElseThrow(() -> new java.util.NoSuchElementException("Angebotsversion nicht gefunden."));
        if (version.getVersion() == null || version.getVersion() != erwarteteVersion) throw new IllegalStateException("Das Angebot wurde zwischenzeitlich geändert.");
        if (!"ERFASST".equals(version.getStatus())) throw new IllegalStateException("Diese Angebotsversion ist bereits bestätigt oder abgelöst.");
        version.bestaetigen(akteurId);
        version.getAngebot().setStatus("GEPRUEFT");
        versionen.flush();
        return dto(version);
    }

    @Transactional
    public VersionDto abweichungBestaetigen(Long angebotId, long erwarteteVersion, String begruendung, Long akteurId) {
        if (angebotId == null || angebotId <= 0 || erwarteteVersion < 0 || akteurId == null || akteurId <= 0
                || begruendung == null || begruendung.isBlank() || begruendung.length() > 1000)
            throw new IllegalArgumentException("Bitte geben Sie eine begründete technische Abweichungsfreigabe an.");
        AngebotVersion latest = versionen.findFirstByAngebotIdOrderByNummerDesc(angebotId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Angebotsversion nicht gefunden."));
        if (latest.getVersion() == null || latest.getVersion() != erwarteteVersion)
            throw new IllegalStateException("Das Angebot wurde zwischenzeitlich geändert.");
        latest.bestaetigeAbweichung(akteurId, begruendung.trim());
        versionen.flush();
        return dto(latest);
    }

    @Transactional
    public VersionDto bestaetigenAngebot(Long angebotId, long erwarteteVersion, Long akteurId) {
        if (angebotId == null || angebotId <= 0) throw new IllegalArgumentException("Das Angebot ist ungültig.");
        AngebotVersion latest = versionen.findFirstByAngebotIdOrderByNummerDesc(angebotId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Angebotsversion nicht gefunden."));
        return bestaetigen(latest.getId(), erwarteteVersion, akteurId);
    }

    public List<Uebersicht> auflisten(Long anfrageId) {
        if (anfrageId == null || anfrageId <= 0) throw new IllegalArgumentException("Die Anfrage ist ungültig.");
        if (revisionen.findFirstByAnfrageIdOrderByNummerDesc(anfrageId).isEmpty())
            throw new java.util.NoSuchElementException("Anfrage nicht gefunden.");
        var alle = versionen.leseAnfrageversionen(anfrageId);
        if (alle.isEmpty()) return List.of();
        versionen.ladeVergleichskosten(alle.stream().map(AngebotVersion::getId).toList());
        Map<Long, List<AngebotVersion>> gruppen = alle.stream().collect(java.util.stream.Collectors.groupingBy(
                v -> v.getAngebot().getId(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return gruppen.values().stream().map(v -> {
            var angebot = v.getFirst().getAngebot();
            var kontakt = angebot.getBeteiligung().getKontakt();
            return new Uebersicht(kontakt.lieferantId(), kontakt.lieferantenname(), new Angebot(angebot.getId(),
                    angebot.getBeteiligung().getId(), angebot.getStatus(), v.stream().map(this::dto).toList()));
        }).toList();
    }

    public Angebot laden(Long angebotId) {
        EinkaufAngebot angebot = angebote.findById(angebotId).orElseThrow(() -> new java.util.NoSuchElementException("Angebot nicht gefunden."));
        return new Angebot(angebot.getId(), angebot.getBeteiligung().getId(), angebot.getStatus(),
                versionen.findByAngebotIdOrderByNummerAsc(angebotId).stream().map(this::dto).toList());
    }

    private VersionDto legeVersionAn(EinkaufAngebot angebot, Erfassung r, Long akteurId) {
        if (akteurId == null || akteurId <= 0 || r == null || r.anfrageRevisionId() == null || r.positionen().isEmpty())
            throw new IllegalArgumentException("Angebot und handelnder Benutzer müssen vollständig sein.");
        if (r.waehrung() == null || !r.waehrung().matches("[A-Z]{3}") || r.angebotsnummer() != null && r.angebotsnummer().length() > 120
                || r.zahlungsbedingungen() != null && r.zahlungsbedingungen().length() > 2000
                || r.skontoProzent() != null && (r.skontoProzent().signum() < 0 || r.skontoProzent().compareTo(BigDecimal.valueOf(100)) > 0)
                || r.skontoTage() != null && (r.skontoTage() < 0 || r.skontoTage() > 365))
            throw new IllegalArgumentException("Bitte prüfen Sie Währung, Zahlungsbedingungen und Skonto.");
        AnfrageRevision revision = revisionen.findById(r.anfrageRevisionId()).orElseThrow(() -> new java.util.NoSuchElementException("Anfragefassung nicht gefunden."));
        if (!revision.getAnfrage().getId().equals(angebot.getBeteiligung().getRevision().getAnfrage().getId()))
            throw new IllegalArgumentException("Die Angebotsfassung gehört zu einer anderen Anfrage.");
        validiereQuellen(angebot, r);
        Set<Long> erlaubtePositionen = revision.getPositionen().stream().map(AnfragePosition::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> genutzt = new HashSet<>();
        for (Position p : r.positionen()) {
            if (p == null || p.anfragePositionId() == null || !erlaubtePositionen.contains(p.anfragePositionId()) || !genutzt.add(p.anfragePositionId()))
                throw new IllegalArgumentException("Eine Angebotsposition gehört nicht zur gewählten Anfragefassung.");
            if (p.originalNummer() != null && p.originalNummer().length() > 120 || p.originalText() != null && p.originalText().length() > 2000)
                throw new IllegalArgumentException("Die Lieferanten-Positionsangabe ist zu lang.");
            validiereKosten(p.kosten());
            if (p.zeugnisse().stream().anyMatch(z -> z == null || z.art() == null || z.status() == null
                    || !Set.of("ENTHALTEN", "AUFPREIS", "NICHT_LIEFERBAR", "OFFEN").contains(z.status())))
                throw new IllegalArgumentException("Der Zeugnisstatus ist ungültig.");
        }
        validiereKosten(r.kosten());
        int number = versionen.findFirstByAngebotIdOrderByNummerDesc(angebot.getId()).map(v -> v.getNummer() + 1).orElse(1);
        AngebotVersion version = new AngebotVersion(angebot, revision, number, sauber(r.angebotsnummer()), r.datum(), r.gueltigBis(),
                r.waehrung(), sauber(r.zahlungsbedingungen()), r.skontoProzent(), r.skontoTage(), r.emailId(), r.originalDateiId());
        for (Position p : r.positionen()) {
            AnfragePosition requestPosition = revision.getPositionen().stream().filter(x -> x.getId().equals(p.anfragePositionId())).findFirst().orElseThrow();
            AngebotPosition saved = new AngebotPosition(version, requestPosition, sauber(p.originalNummer()), sauber(p.originalText()), p.angeboten(),
                    p.mindestmenge(), p.verpackungseinheit(), p.liefertermin(), p.abweichungen(), p.zeugnisse());
            version.addPosition(saved);
            p.kosten().forEach(k -> version.addKosten(kosten(version, saved, k)));
        }
        r.kosten().forEach(k -> version.addKosten(kosten(version, null, k)));
        angebot.addVersion(version);
        angebot.setStatus("ERFASST");
        angebote.saveAndFlush(angebot);
        return dto(version);
    }

    private void validiereQuellen(EinkaufAngebot angebot, Erfassung request) {
        if (request.emailId() != null) {
            Email email = emails.findById(request.emailId()).orElseThrow(() -> new java.util.NoSuchElementException("Quell-E-Mail nicht gefunden."));
            if (!"EINKAUF".equals(email.getKontoId()) || email.getFromAddress() == null
                    || !email.getFromAddress().equalsIgnoreCase(angebot.getBeteiligung().getKontakt().email()))
                throw new IllegalArgumentException("Die Quell-E-Mail gehört nicht zu diesem Lieferantenangebot.");
        }
        if (request.emailId() != null) {
            var beteiligung = angebot.getBeteiligung();
            boolean passend = mailZuordnungen.findByEmailId(request.emailId())
                    .filter(EinkaufMailZuordnung::isBestaetigt)
                    .filter(link -> "ANFRAGE".equals(link.getTyp()))
                    .filter(link -> java.util.Objects.equals(link.getVorgangId(), beteiligung.getRevision().getAnfrage().getId()))
                    .filter(link -> java.util.Objects.equals(link.getBeteiligungId(), beteiligung.getId()))
                    .filter(link -> java.util.Objects.equals(link.getRevisionId(), request.anfrageRevisionId()))
                    .isPresent();
            if (!passend) throw new IllegalArgumentException(
                    "Die Quell-E-Mail braucht eine bestätigte Zuordnung zu dieser Anfrage, Lieferantenbeteiligung und Fassung.");
        }
        if (request.originalDateiId() != null) {
            var file = dateien.findById(request.originalDateiId()).orElseThrow(() -> new java.util.NoSuchElementException("Die Originaldatei wurde nicht gefunden."));
            boolean fromMatchingEmail = request.emailId() != null && file.getEmailAttachmentId() != null
                    && emailAttachments.findById(file.getEmailAttachmentId()).filter(a -> a.getEmail().getId().equals(request.emailId())).isPresent();
            boolean fromSupplierDocument = file.getLieferantDokumentId() != null && lieferantDokumente.findById(file.getLieferantDokumentId())
                    .filter(d -> d.getLieferant() != null && d.getLieferant().getId().equals(angebot.getBeteiligung().getKontakt().lieferantId())).isPresent();
            if (!fromMatchingEmail && !fromSupplierDocument)
                throw new IllegalArgumentException("Die Originaldatei gehört nicht zu dieser Lieferantenkommunikation.");
        }
    }

    private static void validiereKosten(List<Kosten> costs) {
        if (costs == null) return;
        Set<String> keys = new HashSet<>();
        for (Kosten k : costs) {
            if (k == null || k.schluessel() == null || !k.schluessel().matches("[A-Za-z0-9_-]{1,80}") || !keys.add(k.schluessel())
                    || k.art() == null || !ARTen.contains(k.art()) || k.basis() == null || !BASEN.contains(k.basis())
                    || k.betrag() != null && k.betrag().signum() < 0 || k.betrag() != null && k.betrag().scale() > 6
                    || k.basisMenge() != null && k.basisMenge().signum() <= 0 || k.basisMenge() != null && k.basisMenge().scale() > 6
                    || k.quelle() != null && k.quelle().length() > 500)
                throw new IllegalArgumentException("Ein Kostenbestandteil ist ungültig.");
            if ("PROZENT".equals(k.basis()) && (k.prozentBasisSchluessel() == null || k.betrag() == null))
                throw new IllegalArgumentException("Prozentkosten brauchen einen Betrag und eine eindeutige Bezugsposition.");
        }
        pruefeProzentbasiszyklen(costs);
    }

    public static void pruefeProzentbasiszyklen(List<Kosten> costs) {
        Map<String, String> dependencies = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (Kosten k : costs) {
            if (k == null || k.schluessel() == null || !keys.add(k.schluessel())) throw new IllegalArgumentException("Kosten-Schlüssel müssen eindeutig sein.");
            if ("PROZENT".equals(k.basis())) dependencies.put(k.schluessel(), k.prozentBasisSchluessel());
        }
        for (var dependency : dependencies.entrySet()) {
            if (dependency.getValue() == null || !keys.contains(dependency.getValue())) throw new IllegalArgumentException("Die Prozentbasis verweist auf keinen Kostenbestandteil.");
            Set<String> seen = new HashSet<>();
            String current = dependency.getKey();
            while (dependencies.containsKey(current)) {
                if (!seen.add(current)) throw new IllegalArgumentException("Die Prozentbasen enthalten einen Rechenkreis.");
                current = dependencies.get(current);
            }
        }
    }
    private static AngebotKostenbestandteil kosten(AngebotVersion version, AngebotPosition position, Kosten k) {
        return new AngebotKostenbestandteil(version, position, k.schluessel(), k.art(), k.betrag(), k.basis(), k.basisMenge(),
                k.prozentBasisSchluessel(), k.enthalten(), k.variabel(), sauber(k.quelle()));
    }
    VersionDto dto(AngebotVersion v) {
        Map<Long, List<Kosten>> byPosition = new HashMap<>();
        List<Kosten> heads = new ArrayList<>();
        for (AngebotKostenbestandteil cost : v.getKosten()) {
            Kosten dto = new Kosten(cost.getSchluessel(), cost.getArt(), cost.getBetrag(), cost.getBasis(), cost.getBasisMenge(),
                    cost.getProzentBasisSchluessel(), cost.isEnthalten(), cost.isVariabel(), cost.getQuelle());
            if (cost.getPositionId() == null) heads.add(dto); else byPosition.computeIfAbsent(cost.getPositionId(), ignored -> new ArrayList<>()).add(dto);
        }
        List<Position> positions = v.getPositionen().stream().map(p -> new Position(p.getAnfragePositionId(), p.getOriginalNummer(),
                p.getOriginalText(), p.getAngeboten(), p.getMindestmenge(), p.getVerpackungseinheit(), p.getLiefertermin(),
                p.getAbweichungen(), p.getZeugnisse(), byPosition.getOrDefault(p.getId(), List.of()), p.getId())).toList();
        return new VersionDto(v.getId(), v.getAngebot().getId(), v.getNummer(), v.getVersion() == null ? 0 : v.getVersion(),
                v.getAnfrageRevision().getId(), v.getStatus(), v.getAngebotsnummer(), v.getDatum(), v.getGueltigBis(), v.getWaehrung(),
                positions, heads, v.getZahlungsbedingungen(), v.getSkontoProzent(), v.getSkontoTage(), v.getEmailId(), v.getOriginalDateiId(),
                v.getBestaetigtVon(), v.getAbweichungBestaetigtVon(), v.getAbweichungBestaetigtAm(), v.getAbweichungBestaetigung());
    }
    private static String sauber(String value) { return value == null ? null : value.trim(); }
}
