package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturAuditRepository;
import org.example.kalkulationsprogramm.repository.ZeitkontoKorrekturRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service für Zeitkonto-Korrekturen mit GoBD-konformer Audit-Protokollierung.
 * 
 * Alle Änderungen werden unveränderlich protokolliert:
 * - Erstellung → AuditAktion.ERSTELLT
 * - Änderung → AuditAktion.GEAENDERT (mit Pflicht-Begründung)
 * - Stornierung → AuditAktion.STORNIERT (keine physische Löschung!)
 */
@Service
@RequiredArgsConstructor
public class ZeitkontoKorrekturService {

    private final ZeitkontoKorrekturRepository korrekturRepository;
    private final ZeitkontoKorrekturAuditRepository auditRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final MonatsSaldoService monatsSaldoService;

    /**
     * Erstellt eine neue Zeitkonto-Korrektur mit Audit-Protokollierung.
     *
     * @param mitarbeiterId ID des Mitarbeiters
     * @param stunden       Korrekturstunden (positiv = Gutschrift, negativ = Abzug)
     * @param datum         Datum der Korrektur
     * @param grund         Pflicht-Begründung für die Korrektur (GoBD)
     * @param erstelltVonId ID des Bearbeiters
     * @param quelle        Erfassungsquelle (DESKTOP, MOBILE_APP, etc.)
     * @param typ           Typ der Korrektur (STUNDEN oder URLAUB)
     * @return Die erstellte Korrektur
     */
    @Transactional
    public ZeitkontoKorrektur erstelleKorrektur(Long mitarbeiterId, BigDecimal stunden, LocalDate datum,
            String grund, Long erstelltVonId, ErfassungsQuelle quelle, KorrekturTyp typ) {
        // Validierung
        if (grund == null || grund.isBlank()) {
            throw new IllegalArgumentException("Begründung ist ein Pflichtfeld für GoBD-Konformität");
        }
        if (stunden == null || stunden.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Korrekturstunden dürfen nicht 0 sein");
        }

        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(mitarbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Mitarbeiter nicht gefunden: " + mitarbeiterId));

        Mitarbeiter erstelltVon = mitarbeiterRepository.findById(erstelltVonId)
                .orElseThrow(() -> new IllegalArgumentException("Bearbeiter nicht gefunden: " + erstelltVonId));

        // Korrektur erstellen
        ZeitkontoKorrektur korrektur = new ZeitkontoKorrektur();
        korrektur.setMitarbeiter(mitarbeiter);
        korrektur.setStunden(stunden);
        korrektur.setDatum(datum);
        korrektur.setGrund(grund);
        korrektur.setErstelltVon(erstelltVon);
        korrektur.setVersion(1);
        korrektur.setTyp(typ != null ? typ : KorrekturTyp.STUNDEN);

        ZeitkontoKorrektur gespeichert = korrekturRepository.save(korrektur);

        // AUDIT: Initiale Erstellung protokollieren
        ZeitkontoKorrekturAudit audit = ZeitkontoKorrekturAudit.fromKorrektur(
                gespeichert, AuditAktion.ERSTELLT, erstelltVon, quelle, "Initiale Erfassung");
        auditRepository.save(audit);

        // MonatsSaldo-Cache invalidieren (Korrektur betrifft den Monat des Datums)
        monatsSaldoService.invalidiereFuerDatum(mitarbeiterId, datum);

        return gespeichert;
    }

    // Overload for backward compatibility if needed, using STUNDEN as default
    @Transactional
    public ZeitkontoKorrektur erstelleKorrektur(Long mitarbeiterId, BigDecimal stunden, LocalDate datum,
            String grund, Long erstelltVonId, ErfassungsQuelle quelle) {
        return erstelleKorrektur(mitarbeiterId, stunden, datum, grund, erstelltVonId, quelle, KorrekturTyp.STUNDEN);
    }

    /**
     * Ändert eine bestehende Korrektur mit Audit-Protokollierung.
     * WICHTIG: Bei GoBD-konformer Buchführung sollten Korrekturen nur
     * in Ausnahmefällen geändert werden!
     *
     * @param korrekturId     ID der zu ändernden Korrektur
     * @param stunden         Neue Stundenzahl
     * @param grund           Neue Begründung
     * @param bearbeiterId    Wer führt die Änderung durch
     * @param aenderungsgrund Pflicht-Begründung für die Änderung
     * @param quelle          Erfassungsquelle
     */
    @Transactional
    public ZeitkontoKorrektur aendereKorrektur(Long korrekturId, BigDecimal stunden, String grund,
            Long bearbeiterId, String aenderungsgrund, ErfassungsQuelle quelle) {
        if (aenderungsgrund == null || aenderungsgrund.isBlank()) {
            throw new IllegalArgumentException("Änderungsgrund ist ein Pflichtfeld für GoBD-Konformität");
        }

        ZeitkontoKorrektur korrektur = korrekturRepository.findById(korrekturId)
                .orElseThrow(() -> new IllegalArgumentException("Korrektur nicht gefunden: " + korrekturId));

        if (korrektur.getStorniert()) {
            throw new IllegalStateException("Stornierte Korrekturen können nicht mehr geändert werden");
        }

        Mitarbeiter bearbeiter = mitarbeiterRepository.findById(bearbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Bearbeiter nicht gefunden: " + bearbeiterId));

        // Version erhöhen vor dem Audit
        korrektur.erhoeheVersion();

        // Daten aktualisieren
        if (stunden != null) {
            korrektur.setStunden(stunden);
        }
        if (grund != null && !grund.isBlank()) {
            korrektur.setGrund(grund);
        }

        // AUDIT: Änderung protokollieren
        ZeitkontoKorrekturAudit audit = ZeitkontoKorrekturAudit.fromKorrektur(
                korrektur, AuditAktion.GEAENDERT, bearbeiter, quelle, aenderungsgrund);
        auditRepository.save(audit);

        ZeitkontoKorrektur gespeicherteKorrektur = korrekturRepository.save(korrektur);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDatum(
                korrektur.getMitarbeiter().getId(), korrektur.getDatum());

        return gespeicherteKorrektur;
    }

    /**
     * Storniert eine Korrektur (GoBD-konform: keine physische Löschung!).
     *
     * @param korrekturId       ID der zu stornierenden Korrektur
     * @param bearbeiterId      Wer storniert
     * @param stornierungsgrund Pflicht-Begründung für die Stornierung
     * @param quelle            Erfassungsquelle
     */
    @Transactional
    public void storniereKorrektur(Long korrekturId, Long bearbeiterId, String stornierungsgrund,
            ErfassungsQuelle quelle) {
        if (stornierungsgrund == null || stornierungsgrund.isBlank()) {
            throw new IllegalArgumentException("Stornierungsgrund ist ein Pflichtfeld für GoBD-Konformität");
        }

        ZeitkontoKorrektur korrektur = korrekturRepository.findById(korrekturId)
                .orElseThrow(() -> new IllegalArgumentException("Korrektur nicht gefunden: " + korrekturId));

        if (korrektur.getStorniert()) {
            throw new IllegalStateException("Korrektur ist bereits storniert");
        }

        Mitarbeiter bearbeiter = mitarbeiterRepository.findById(bearbeiterId)
                .orElseThrow(() -> new IllegalArgumentException("Bearbeiter nicht gefunden: " + bearbeiterId));

        // Version erhöhen
        korrektur.erhoeheVersion();

        // Stornierung setzen
        korrektur.setStorniert(true);
        korrektur.setStorniertAm(LocalDateTime.now());
        korrektur.setStorniertVon(bearbeiter);
        korrektur.setStornierungsgrund(stornierungsgrund);

        // AUDIT: Stornierung protokollieren
        ZeitkontoKorrekturAudit audit = ZeitkontoKorrekturAudit.fromKorrektur(
                korrektur, AuditAktion.STORNIERT, bearbeiter, quelle, stornierungsgrund);
        auditRepository.save(audit);

        korrekturRepository.save(korrektur);

        // MonatsSaldo-Cache invalidieren
        monatsSaldoService.invalidiereFuerDatum(
                korrektur.getMitarbeiter().getId(), korrektur.getDatum());
    }

    /**
     * Gibt alle aktiven (nicht-stornierten) Korrekturen eines Mitarbeiters zurück.
     */
    public List<ZeitkontoKorrektur> getAktiveKorrekturenByMitarbeiter(Long mitarbeiterId) {
        return korrekturRepository.findByMitarbeiterIdOrderByDatumDesc(mitarbeiterId).stream()
                .filter(k -> !k.getStorniert())
                .collect(Collectors.toList());
    }

    /**
     * Gibt ALLE Korrekturen eines Mitarbeiters zurück (inkl. stornierter).
     * Für Audit-Ansicht.
     */
    public List<ZeitkontoKorrektur> getAlleKorrekturenByMitarbeiter(Long mitarbeiterId) {
        return korrekturRepository.findByMitarbeiterIdOrderByDatumDesc(mitarbeiterId);
    }

    /**
     * Summiert alle aktiven Korrekturstunden eines Mitarbeiters für ein Jahr.
     * Nur Typ STUNDEN wird berücksichtigt.
     */
    public BigDecimal summiereAktiveKorrekturen(Long mitarbeiterId, int jahr) {
        LocalDate von = LocalDate.of(jahr, 1, 1);
        LocalDate bis = LocalDate.of(jahr, 12, 31);

        return korrekturRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, von, bis).stream()
                .filter(k -> !Boolean.TRUE.equals(k.getStorniert()))
                .filter(k -> k.getTyp() == KorrekturTyp.STUNDEN)
                .map(k -> k.getStunden() != null ? k.getStunden() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Summiert alle aktiven Korrekturstunden (Typ STUNDEN) eines Mitarbeiters in einem beliebigen Zeitraum.
     */
    public BigDecimal summiereAktiveKorrekturenImZeitraum(Long mitarbeiterId, LocalDate von, LocalDate bis) {
        return korrekturRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, von, bis).stream()
                .filter(k -> !Boolean.TRUE.equals(k.getStorniert()))
                .filter(k -> k.getTyp() == KorrekturTyp.STUNDEN)
                .map(k -> k.getStunden() != null ? k.getStunden() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Summiert alle aktiven Urlaubskorrekturen eines Mitarbeiters für ein Jahr.
     * Nur Typ URLAUB wird berücksichtigt.
     */
    public BigDecimal summiereAktiveUrlaubsKorrekturen(Long mitarbeiterId, int jahr) {
        LocalDate von = LocalDate.of(jahr, 1, 1);
        LocalDate bis = LocalDate.of(jahr, 12, 31);

        return korrekturRepository.findByMitarbeiterIdAndDatumBetween(mitarbeiterId, von, bis).stream()
                .filter(k -> !Boolean.TRUE.equals(k.getStorniert()))
                .filter(k -> k.getTyp() == KorrekturTyp.URLAUB)
                .map(k -> k.getStunden() != null ? k.getStunden() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gibt die vollständige Audit-Historie einer Korrektur zurück.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAuditHistorie(Long korrekturId) {
        return auditRepository.findByZeitkontoKorrekturIdOrderByVersionDesc(korrekturId).stream()
                .map(this::auditToMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> auditToMap(ZeitkontoKorrekturAudit audit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", audit.getId());
        map.put("version", audit.getVersion());
        map.put("aktion", audit.getAktion().name());
        map.put("datum", audit.getDatum().toString());
        map.put("stunden", audit.getStunden());
        map.put("grund", audit.getGrund());
        map.put("geaendertVon", audit.getGeaendertVon() != null
                ? audit.getGeaendertVon().getVorname() + " " + audit.getGeaendertVon().getNachname()
                : null);
        map.put("geaendertAm", audit.getGeaendertAm() != null ? audit.getGeaendertAm().toString() : null);
        map.put("geaendertVia", audit.getGeaendertVia() != null ? audit.getGeaendertVia().name() : null);
        map.put("aenderungsgrund", audit.getAenderungsgrund());
        return map;
    }
}
