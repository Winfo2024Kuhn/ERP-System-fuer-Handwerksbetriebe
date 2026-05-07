package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.example.kalkulationsprogramm.service.AuditChainVerifier;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService;
import org.example.kalkulationsprogramm.service.SteuerpruefungZ3ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Endpunkte für die Steuerprüfung:
 * <ul>
 *   <li>{@code /audit/anzahl}     – Anzahl Audit-Einträge im Zeitraum (UI-Vorschau).</li>
 *   <li>{@code /audit/export}     – Audit-Log als CSV (mit Hash-Kette).</li>
 *   <li>{@code /audit/verify}     – Selbsttest der Hash-Kette mit Bericht.</li>
 *   <li>{@code /audit/z3-paket}   – Komplettes GoBD-Z3-Paket (ZIP) für Datenträgerüberlassung.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ausgangs-dokumente/audit")
@RequiredArgsConstructor
public class AusgangsGeschaeftsDokumentAuditController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;
    @SuppressWarnings("unused")
    private final AusgangsGeschaeftsDokumentAuditService auditService;
    private final AuditChainVerifier auditChainVerifier;
    private final SteuerpruefungZ3ExportService z3ExportService;

    /**
     * Exportiert alle Audit-Einträge in einem Zeitraum als CSV.
     * Inklusive Hash-Kettenspalten (chain_index, previous_hash, entry_hash) — der
     * Prüfer kann die Kette mit jedem SHA-256-Tool nachvollziehen.
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportCsv(
            @RequestParam("von") String von,
            @RequestParam("bis") String bis) {

        LocalDateTime vonDt = LocalDate.parse(von).atStartOfDay();
        LocalDateTime bisDt = LocalDate.parse(bis).atTime(23, 59, 59);

        List<AusgangsGeschaeftsDokumentAudit> eintraege =
                auditRepository.findByGeaendertAmBetweenOrderByChainIndexAsc(vonDt, bisDt);

        StringBuilder csv = new StringBuilder();
        csv.append("ChainIndex;Zeitpunkt;Aktion;DokumentNummer;Typ;BetragNetto;BetragBrutto;")
           .append("Gebucht;Storniert;DigitalAngenommen;BearbeiterId;Begruendung;IpAdresse;")
           .append("InhaltHash;PreviousHash;EntryHash\n");

        for (AusgangsGeschaeftsDokumentAudit a : eintraege) {
            csv.append(a.getChainIndex() != null ? a.getChainIndex().toString() : "").append(';');
            csv.append(a.getGeaendertAm().format(TS)).append(';');
            csv.append(a.getAktion().name()).append(';');
            csv.append(esc(a.getDokumentNummer())).append(';');
            csv.append(a.getTyp().name()).append(';');
            csv.append(a.getBetragNetto() != null ? a.getBetragNetto().toPlainString() : "").append(';');
            csv.append(a.getBetragBrutto() != null ? a.getBetragBrutto().toPlainString() : "").append(';');
            csv.append(a.isGebucht()).append(';');
            csv.append(a.isStorniert()).append(';');
            csv.append(a.isDigitalAngenommen()).append(';');
            csv.append(a.getGeaendertVon() != null ? a.getGeaendertVon().getId() : "").append(';');
            csv.append(esc(a.getAenderungsgrund())).append(';');
            csv.append(esc(a.getIpAdresse())).append(';');
            csv.append(a.getInhaltHash() != null ? a.getInhaltHash() : "").append(';');
            csv.append(a.getPreviousHash() != null ? a.getPreviousHash() : "").append(';');
            csv.append(a.getEntryHash() != null ? a.getEntryHash() : "").append('\n');
        }

        String filename = "audit_ausgangsdokumente_" + von + "_bis_" + bis + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    /**
     * Anzahl Audit-Einträge im Zeitraum (für UI-Vorschau).
     */
    @GetMapping("/anzahl")
    public ResponseEntity<Long> anzahl(
            @RequestParam("von") String von,
            @RequestParam("bis") String bis) {
        LocalDateTime vonDt = LocalDate.parse(von).atStartOfDay();
        LocalDateTime bisDt = LocalDate.parse(bis).atTime(23, 59, 59);
        long n = auditRepository.countByGeaendertAmBetween(vonDt, bisDt);
        return ResponseEntity.ok(n);
    }

    /**
     * Verifiziert die komplette Hash-Kette und liefert einen Bericht.
     * Antwort enthält Status (intakt/gebrochen), Anzahl geprüfter Einträge und
     * ggf. die erste gefundene Bruchstelle.
     */
    @GetMapping("/verify")
    public ResponseEntity<AuditChainVerifier.Bericht> verify() {
        return ResponseEntity.ok(auditChainVerifier.verify());
    }

    /**
     * Liefert das komplette GoBD-Z3-Paket (ZIP) für die Datenträgerüberlassung.
     * Enthält dokumente.csv, audit.csv (mit Hash-Kette), INFO.txt mit Anleitung,
     * manifest.sha256.
     */
    @GetMapping(value = "/z3-paket")
    public ResponseEntity<byte[]> z3Paket(
            @RequestParam("von") String von,
            @RequestParam("bis") String bis) throws IOException {
        LocalDate vonD = LocalDate.parse(von);
        LocalDate bisD = LocalDate.parse(bis);
        byte[] zip = z3ExportService.erzeugeZip(vonD, bisD);
        String filename = "steuerpruefung_" + von + "_bis_" + bis + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    private String esc(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ").replace(";", ",");
        return "\"" + escaped + "\"";
    }
}
