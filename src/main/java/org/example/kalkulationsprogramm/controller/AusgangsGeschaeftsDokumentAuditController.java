package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Endpunkte für die Steuerprüfung: Audit-Log über einen Zeitraum als CSV exportieren.
 * Erfüllt GoBD-Z3-Datenträgerüberlassung — Prüfer kann das Log offline mitnehmen.
 */
@RestController
@RequestMapping("/api/ausgangs-dokumente/audit")
@RequiredArgsConstructor
public class AusgangsGeschaeftsDokumentAuditController {

    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;
    @SuppressWarnings("unused")
    private final AusgangsGeschaeftsDokumentAuditService auditService;

    /**
     * Exportiert alle Audit-Einträge in einem Zeitraum als CSV.
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportCsv(
            @RequestParam("von") String von,
            @RequestParam("bis") String bis) {

        LocalDateTime vonDt = LocalDate.parse(von).atStartOfDay();
        LocalDateTime bisDt = LocalDate.parse(bis).atTime(23, 59, 59);

        List<AusgangsGeschaeftsDokumentAudit> eintraege =
                auditRepository.findByGeaendertAmBetweenOrderByGeaendertAmAsc(vonDt, bisDt);

        DateTimeFormatter ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder csv = new StringBuilder();
        csv.append("Zeitpunkt;Aktion;DokumentNummer;Typ;BetragNetto;BetragBrutto;")
           .append("Gebucht;Storniert;DigitalAngenommen;BearbeiterId;Begruendung;IpAdresse;InhaltHash\n");

        for (AusgangsGeschaeftsDokumentAudit a : eintraege) {
            csv.append(a.getGeaendertAm().format(ts)).append(';');
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
            csv.append(a.getInhaltHash() != null ? a.getInhaltHash() : "").append('\n');
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
        long n = auditRepository.findByGeaendertAmBetweenOrderByGeaendertAmAsc(vonDt, bisDt).size();
        return ResponseEntity.ok(n);
    }

    private String esc(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ").replace(";", ",");
        return "\"" + escaped + "\"";
    }
}
