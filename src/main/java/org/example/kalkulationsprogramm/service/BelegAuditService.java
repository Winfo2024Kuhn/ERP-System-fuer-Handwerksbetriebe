package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.BelegAudit;
import org.example.kalkulationsprogramm.domain.BelegAuditAktion;
import org.example.kalkulationsprogramm.domain.BelegAuditChainState;
import org.example.kalkulationsprogramm.domain.KassenbuchMonatsabschluss;
import org.example.kalkulationsprogramm.domain.Kassenzaehlung;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.BelegAuditChainStateRepository;
import org.example.kalkulationsprogramm.repository.BelegAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuehrt das unveraenderliche Protokoll des Kassenbuchs.
 *
 * <p>Jede Aktion an einem Beleg landet als Eintrag in einer fortlaufenden
 * Hash-Kette. Der Kettenkopf liegt in {@code beleg_audit_chain_state} und
 * wird beim Anhaengen pessimistisch gelockt, damit zwei gleichzeitige
 * Buchungen nie denselben Vorgaenger-Hash bekommen -- sonst waere die Kette
 * eine Verzweigung und nicht mehr eindeutig nachrechenbar.</p>
 *
 * <p>Derselbe Lock vergibt auch die laufenden Belegnummern
 * ({@link #zieheNaechsteLaufendeNummer}), weil beides beim Monatsabschluss
 * zusammen passiert und beides lueckenlos sein muss.</p>
 *
 * <p>Baugleich zu {@link AusgangsGeschaeftsDokumentAuditService}, das
 * dasselbe fuer die Ausgangsrechnungen leistet.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegAuditService {

    public static final String BEZUG_KASSENZAEHLUNG = "KASSENZAEHLUNG";
    public static final String BEZUG_MONATSABSCHLUSS = "MONATSABSCHLUSS";

    private final BelegAuditRepository auditRepository;
    private final BelegAuditChainStateRepository chainStateRepository;
    private final EntityManager entityManager;

    // ===================== Beleg-Aktionen =====================

    @Transactional
    public BelegAudit protokolliereErfassung(Beleg beleg, Mitarbeiter bearbeiter, String ipAdresse) {
        return save(beleg, BelegAuditAktion.ERFASST, bearbeiter, "Beleg erfasst", ipAdresse);
    }

    @Transactional
    public BelegAudit protokolliereAenderung(Beleg beleg, Mitarbeiter bearbeiter, String grund, String ipAdresse) {
        return save(beleg, BelegAuditAktion.GEAENDERT, bearbeiter,
                grund == null || grund.isBlank() ? "Beleg geaendert" : grund, ipAdresse);
    }

    @Transactional
    public BelegAudit protokolliereValidierung(Beleg beleg, Mitarbeiter bearbeiter, String ipAdresse) {
        return save(beleg, BelegAuditAktion.VALIDIERT, bearbeiter, "Beleg geprueft", ipAdresse);
    }

    /**
     * Verwerfen ist buchhalterisch ein Loeschvorgang -- ohne Begruendung
     * waere hinterher nicht mehr nachvollziehbar, warum ein Betrag aus dem
     * Kassenbuch verschwunden ist.
     */
    @Transactional
    public BelegAudit protokolliereVerwerfen(Beleg beleg, Mitarbeiter bearbeiter, String grund, String ipAdresse) {
        if (grund == null || grund.isBlank()) {
            throw new IllegalArgumentException("Begruendung fuers Verwerfen ist Pflicht");
        }
        return save(beleg, BelegAuditAktion.VERWORFEN, bearbeiter, grund, ipAdresse);
    }

    @Transactional
    public BelegAudit protokolliereFestschreibung(Beleg beleg, Mitarbeiter bearbeiter, String grund, String ipAdresse) {
        return save(beleg, BelegAuditAktion.FESTGESCHRIEBEN, bearbeiter, grund, ipAdresse);
    }

    @Transactional
    public BelegAudit protokolliereStornierung(Beleg original, Mitarbeiter bearbeiter, String grund, String ipAdresse) {
        if (grund == null || grund.isBlank()) {
            throw new IllegalArgumentException("Stornogrund ist Pflicht");
        }
        return save(original, BelegAuditAktion.STORNIERT, bearbeiter, grund, ipAdresse);
    }

    @Transactional
    public BelegAudit protokolliereStornoErfassung(Beleg storno, Mitarbeiter bearbeiter, String grund, String ipAdresse) {
        return save(storno, BelegAuditAktion.STORNO_ERFASST, bearbeiter, grund, ipAdresse);
    }

    // ===================== Kassenbuch-Aktionen ohne einzelnen Beleg =====================

    @Transactional
    public BelegAudit protokolliereKassenzaehlung(Kassenzaehlung z, Mitarbeiter bearbeiter, String ipAdresse) {
        BelegAudit a = BelegAudit.neutral(BelegAuditAktion.KASSE_GEZAEHLT, bearbeiter,
                z.getBemerkung(), ipAdresse);
        a.setBezugTyp(BEZUG_KASSENZAEHLUNG);
        a.setBezugId(z.getId());
        a.setBelegDatum(z.getStichtag());
        a.setZusatz("gezaehlt=" + z.getGezaehlterBestand()
                + "; rechnerisch=" + z.getRechnerischerBestand()
                + "; differenz=" + z.getDifferenz()
                + "; ausgleichBelegId=" + (z.getAusgleichBelegId() == null ? "-" : z.getAusgleichBelegId())
                + "; stueckelung=" + (z.getStueckelungJson() == null ? "-" : z.getStueckelungJson()));
        return appendToChain(a);
    }

    @Transactional
    public BelegAudit protokolliereMonatsabschluss(KassenbuchMonatsabschluss m, Mitarbeiter bearbeiter, String ipAdresse) {
        BelegAudit a = BelegAudit.neutral(BelegAuditAktion.MONAT_ABGESCHLOSSEN, bearbeiter,
                m.getBemerkung(), ipAdresse);
        a.setBezugTyp(BEZUG_MONATSABSCHLUSS);
        a.setBezugId(m.getId());
        a.setZusatz("monat=" + m.getJahr() + "-" + String.format("%02d", m.getMonat())
                + "; anfang=" + m.getAnfangsbestand()
                + "; ende=" + m.getEndbestand()
                + "; einnahmen=" + m.getSummeEinnahmen()
                + "; ausgaben=" + m.getSummeAusgaben()
                + "; belege=" + m.getAnzahlBelege()
                + "; nummern=" + (m.getErsteLaufendeNummer() == null ? "-" : m.getErsteLaufendeNummer())
                + ".." + (m.getLetzteLaufendeNummer() == null ? "-" : m.getLetzteLaufendeNummer()));
        return appendToChain(a);
    }

    // ===================== Lesen =====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistorie(Long belegId) {
        return auditRepository.findByBelegIdOrderByGeaendertAmDesc(belegId).stream()
                .map(this::toMap)
                .toList();
    }

    // ===================== Laufende Nummer =====================

    /**
     * Zieht die naechste laufende Belegnummer unter demselben Lock, der auch
     * die Hash-Kette schuetzt. Beginnt bei 1.
     *
     * <p>Bewusst kein DB-AUTO_INCREMENT: das reisst bei jedem
     * zurueckgerollten Insert eine Luecke, und genau Lueckenlosigkeit ist
     * hier der Punkt. Ein zurueckgerollter Monatsabschluss gibt die Nummern
     * mit der Transaktion wieder frei.</p>
     */
    /**
     * Nimmt den Ketten-Lock, ohne etwas zu schreiben.
     *
     * <p>Fuer Vorgaenge, die erst spaeter an die Kette schreiben, aber schon
     * vorher gegen Parallellaeufe geschuetzt sein muessen -- allen voran der
     * Monatsabschluss. Ohne diesen fruehen Lock kaemen zwei gleichzeitige
     * Abschlussversuche beide an der "schon abgeschlossen?"-Pruefung vorbei
     * und einer liefe erst am Ende in eine Constraint-Verletzung.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sperreKette() {
        ladeOderInitialisiereState();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long zieheNaechsteLaufendeNummer() {
        BelegAuditChainState state = ladeOderInitialisiereState();
        long naechste = state.getLastLaufendeNummer() + 1;
        state.setLastLaufendeNummer(naechste);
        state.setUpdatedAt(LocalDateTime.now());
        chainStateRepository.saveAndFlush(state);
        return naechste;
    }

    // ===================== Kette =====================

    private BelegAudit save(Beleg beleg, BelegAuditAktion aktion, Mitarbeiter bearbeiter,
                            String grund, String ipAdresse) {
        BelegAudit audit = BelegAudit.fromBeleg(beleg, aktion, bearbeiter, grund, ipAdresse);
        BelegAudit gespeichert = appendToChain(audit);
        log.info("Kassenbuch-Protokoll #{} (Kette {}): {} fuer Beleg {} durch {} -- {}",
                gespeichert.getId(), gespeichert.getChainIndex(), aktion, beleg.getId(),
                bearbeiter != null ? bearbeiter.getId() : "System", grund);
        return gespeichert;
    }

    /**
     * Haengt einen Eintrag atomar an die Kette an.
     *
     * <ol>
     *   <li>Kettenkopf locken (SELECT ... FOR UPDATE).</li>
     *   <li>chainIndex = last + 1, previousHash = lastEntryHash.</li>
     *   <li>Eintrag speichern und aus der Datenbank zurueckladen.</li>
     *   <li>entryHash ueber die <strong>zurueckgeladene</strong> Form rechnen
     *       und als neuen Kettenkopf hinterlegen.</li>
     * </ol>
     *
     * <p>Der Umweg ueber {@code refresh} ist der Kern: gehasht wird genau die
     * Form, die nach dem Insert in der Datenbank steht -- also derselbe Wert,
     * den der {@link BelegAuditChainVerifier} spaeter beim Nachrechnen liest.
     * Wuerde stattdessen der Zustand im Arbeitsspeicher gehasht, wichen die
     * beiden auseinander, sobald die Datenbank etwas normalisiert
     * (Mikrosekunden bei DATETIME(6), Nachkommastellen bei DECIMAL,
     * gekuerzte Strings) -- und der Verifier meldete eine Manipulation, die
     * es nie gab.</p>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public BelegAudit appendToChain(BelegAudit audit) {
        BelegAuditChainState state = ladeOderInitialisiereState();

        long nextIndex = state.getLastChainIndex() + 1;
        audit.setChainIndex(nextIndex);
        audit.setPreviousHash(state.getLastEntryHash());

        BelegAudit saved = auditRepository.saveAndFlush(audit);
        entityManager.refresh(saved);
        saved.setEntryHash(saved.computeEntryHash());
        auditRepository.saveAndFlush(saved);

        state.setLastChainIndex(nextIndex);
        state.setLastEntryHash(saved.getEntryHash());
        state.setUpdatedAt(LocalDateTime.now());
        chainStateRepository.saveAndFlush(state);

        return saved;
    }

    /**
     * Kettenkopf unter Lock. Fehlt die Zeile (sollte die V351-Migration
     * angelegt haben), wird sie hier nachgezogen, damit ein frisch
     * aufgesetztes System nicht beim ersten Beleg stehenbleibt.
     */
    private BelegAuditChainState ladeOderInitialisiereState() {
        BelegAuditChainState state = chainStateRepository.lockState();
        if (state == null) {
            state = new BelegAuditChainState();
            state.setId(1);
            state.setLastChainIndex(-1L);
            state.setLastEntryHash(null);
            state.setLastLaufendeNummer(0L);
            state.setUpdatedAt(LocalDateTime.now());
            state = chainStateRepository.saveAndFlush(state);
        }
        return state;
    }

    /** "Vorname Nachname" fuer die Protokoll-Anzeige; null, wenn das System gehandelt hat. */
    static String anzeigeName(Mitarbeiter m) {
        if (m == null) return null;
        String name = ((m.getVorname() == null ? "" : m.getVorname()) + " "
                + (m.getNachname() == null ? "" : m.getNachname())).trim();
        return name.isEmpty() ? ("Mitarbeiter " + m.getId()) : name;
    }

    private Map<String, Object> toMap(BelegAudit a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("chainIndex", a.getChainIndex());
        map.put("aktion", a.getAktion() != null ? a.getAktion().name() : null);
        map.put("belegId", a.getBelegId());
        map.put("laufendeNummer", a.getLaufendeNummer());
        map.put("belegDatum", a.getBelegDatum() != null ? a.getBelegDatum().toString() : null);
        map.put("kategorie", a.getBelegKategorie() != null ? a.getBelegKategorie().name() : null);
        map.put("status", a.getBelegStatus() != null ? a.getBelegStatus().name() : null);
        map.put("beschreibung", a.getBeschreibung());
        map.put("betragBrutto", a.getBetragBrutto());
        map.put("betragNetto", a.getBetragNetto());
        map.put("mwstSatz", a.getMwstSatz());
        map.put("zahlungsart", a.getZahlungsart());
        map.put("sachkontoNummer", a.getSachkontoNummer());
        map.put("festgeschrieben", a.isFestgeschrieben());
        map.put("stornoFuerBelegId", a.getStornoFuerBelegId());
        map.put("storniertDurchBelegId", a.getStorniertDurchBelegId());
        map.put("dateiHash", a.getDateiHash());
        map.put("previousHash", a.getPreviousHash());
        map.put("entryHash", a.getEntryHash());
        map.put("geaendertVon", anzeigeName(a.getGeaendertVon()));
        map.put("geaendertVonId", a.getGeaendertVon() != null ? a.getGeaendertVon().getId() : null);
        map.put("geaendertAm", a.getGeaendertAm() != null ? a.getGeaendertAm().toString() : null);
        map.put("aenderungsgrund", a.getAenderungsgrund());
        map.put("zusatz", a.getZusatz());
        return map;
    }
}
