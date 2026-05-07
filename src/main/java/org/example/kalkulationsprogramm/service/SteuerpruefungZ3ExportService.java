package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentAudit;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentAuditRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Erzeugt ein Steuerprüfungs-Paket nach GoBD-Z3-Datenträgerüberlassung.
 *
 * <p>Inhalt der ZIP:
 * <ul>
 *   <li>{@code dokumente.csv} – alle Ausgangsdokumente im Zeitraum.</li>
 *   <li>{@code audit.csv} – kompletter Audit-Trail (mit Hash-Kette) im Zeitraum.</li>
 *   <li>{@code INFO.txt} – Erklärung für den Prüfer (was ist drin, wie verifiziert man).</li>
 *   <li>{@code manifest.sha256} – SHA-256 jeder Datei und der ZIP-Inhalte.</li>
 * </ul>
 *
 * <p>Der Prüfer kann die Hash-Kette in {@code audit.csv} mit jedem SHA-256-fähigen
 * Tool nachvollziehen — siehe Anleitung in {@code INFO.txt}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteuerpruefungZ3ExportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private final AusgangsGeschaeftsDokumentAuditRepository auditRepository;
    private final AuditChainVerifier verifier;

    @Transactional(readOnly = true)
    public byte[] erzeugeZip(LocalDate von, LocalDate bis) throws IOException {
        LocalDateTime vonDt = von.atStartOfDay();
        LocalDateTime bisDt = bis.atTime(23, 59, 59);

        List<AusgangsGeschaeftsDokument> dokumente =
                dokumentRepository.findByDatumBetweenOrderByDatumDesc(von, bis);
        List<AusgangsGeschaeftsDokumentAudit> audits =
                auditRepository.findByGeaendertAmBetweenOrderByChainIndexAsc(vonDt, bisDt);

        AuditChainVerifier.Bericht verifyBericht = verifier.verify();

        String dokumenteCsv = buildDokumenteCsv(dokumente);
        String auditCsv = buildAuditCsv(audits);
        String info = buildInfoText(von, bis, dokumente.size(), audits.size(), verifyBericht);
        String manifest = buildManifest(dokumenteCsv, auditCsv, info);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            writeEntry(zip, "INFO.txt", info);
            writeEntry(zip, "dokumente.csv", dokumenteCsv);
            writeEntry(zip, "audit.csv", auditCsv);
            writeEntry(zip, "manifest.sha256", manifest);
        }
        return out.toByteArray();
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String buildDokumenteCsv(List<AusgangsGeschaeftsDokument> dokumente) {
        StringBuilder csv = new StringBuilder();
        csv.append("DokumentNummer;Typ;Datum;Betreff;BetragNetto;BetragBrutto;MwStSatz;")
           .append("Gebucht;GebuchtAm;Storniert;StorniertAm;DigitalAngenommen;")
           .append("VersandDatum;KundeId;ProjektId;VorgaengerNummer;ErstelltAm\n");

        for (AusgangsGeschaeftsDokument d : dokumente) {
            csv.append(esc(d.getDokumentNummer())).append(';');
            csv.append(d.getTyp() != null ? d.getTyp().name() : "").append(';');
            csv.append(d.getDatum() != null ? d.getDatum().toString() : "").append(';');
            csv.append(esc(d.getBetreff())).append(';');
            csv.append(d.getBetragNetto() != null ? d.getBetragNetto().toPlainString() : "").append(';');
            csv.append(d.getBetragBrutto() != null ? d.getBetragBrutto().toPlainString() : "").append(';');
            csv.append(d.getMwstSatz() != null ? d.getMwstSatz().toPlainString() : "").append(';');
            csv.append(d.isGebucht()).append(';');
            csv.append(d.getGebuchtAm() != null ? d.getGebuchtAm().toString() : "").append(';');
            csv.append(d.isStorniert()).append(';');
            csv.append(d.getStorniertAm() != null ? d.getStorniertAm().toString() : "").append(';');
            csv.append(d.isDigitalAngenommen()).append(';');
            csv.append(d.getVersandDatum() != null ? d.getVersandDatum().toString() : "").append(';');
            csv.append(d.getKunde() != null ? d.getKunde().getId() : "").append(';');
            csv.append(d.getProjekt() != null ? d.getProjekt().getId() : "").append(';');
            csv.append(d.getVorgaenger() != null ? esc(d.getVorgaenger().getDokumentNummer()) : "").append(';');
            csv.append(d.getErstelltAm() != null ? d.getErstelltAm().format(TS) : "").append('\n');
        }
        return csv.toString();
    }

    private String buildAuditCsv(List<AusgangsGeschaeftsDokumentAudit> audits) {
        StringBuilder csv = new StringBuilder();
        csv.append("ChainIndex;Zeitpunkt;Aktion;DokumentNummer;Typ;BetragNetto;BetragBrutto;")
           .append("Gebucht;Storniert;DigitalAngenommen;BearbeiterId;Begruendung;IpAdresse;")
           .append("InhaltHash;PreviousHash;EntryHash\n");

        for (AusgangsGeschaeftsDokumentAudit a : audits) {
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
        return csv.toString();
    }

    private String buildInfoText(LocalDate von, LocalDate bis, int anzahlDokumente, int anzahlAudits,
                                 AuditChainVerifier.Bericht bericht) {
        StringBuilder s = new StringBuilder();
        s.append("Steuerprüfungs-Paket nach GoBD (Z3-Datenträgerüberlassung)\n");
        s.append("===========================================================\n\n");
        s.append("Zeitraum: ").append(von).append(" bis ").append(bis).append('\n');
        s.append("Erstellt am: ").append(LocalDateTime.now().format(TS)).append("\n\n");

        s.append("Inhalt\n");
        s.append("------\n");
        s.append("dokumente.csv     : ").append(anzahlDokumente).append(" Ausgangsdokumente (Rechnungen, Angebote, Storno usw.)\n");
        s.append("audit.csv         : ").append(anzahlAudits).append(" Audit-Einträge mit Hash-Kette\n");
        s.append("manifest.sha256   : SHA-256 jeder Datei dieses Pakets\n\n");

        s.append("Hash-Kette (Manipulationsschutz)\n");
        s.append("--------------------------------\n");
        s.append("Jeder Audit-Eintrag enthält einen entry_hash. Dieser ist der SHA-256\n");
        s.append("über die kanonische Form aller Felder + den entry_hash des Vorgängers\n");
        s.append("(Spalte previous_hash). Wer einen einzigen Eintrag manipuliert,\n");
        s.append("bricht ALLE nachfolgenden Hashes — eine nachträgliche Änderung der\n");
        s.append("Buchhaltung ist daher mathematisch nachweisbar.\n\n");

        s.append("Verifikation der Kette (Stand zum Export)\n");
        s.append("-----------------------------------------\n");
        s.append("Status: ").append(bericht.isIntakt() ? "INTAKT" : "GEBROCHEN").append('\n');
        s.append("Geprüfte Einträge: ").append(bericht.getGesamtAnzahl()).append('\n');
        if (bericht.getLetzterChainIndex() != null) {
            s.append("Letzter chain_index: ").append(bericht.getLetzterChainIndex()).append('\n');
            s.append("Letzter entry_hash : ").append(bericht.getLetzterEntryHash()).append('\n');
        }
        if (!bericht.isIntakt()) {
            s.append("\nFEHLER:\n");
            for (AuditChainVerifier.Fehler f : bericht.getFehler()) {
                s.append("  - chain_index ").append(f.chainIndex())
                 .append(" / Dokument ").append(f.dokumentNummer())
                 .append(" : ").append(f.beschreibung()).append('\n');
            }
        }
        s.append("\n");

        s.append("Anleitung für den Prüfer\n");
        s.append("------------------------\n");
        s.append("1. dokumente.csv und audit.csv sind UTF-8, Trennzeichen Semikolon.\n");
        s.append("   Direkt in IDEA, Excel oder eine SQL-Datenbank importierbar.\n");
        s.append("2. Lückenlose Nummerierung prüfen: in dokumente.csv die DokumentNummer\n");
        s.append("   nach Monat sortieren — innerhalb eines Monats darf keine Nummer fehlen.\n");
        s.append("3. Hash-Kette prüfen (optional):\n");
        s.append("   a) audit.csv nach ChainIndex aufsteigend sortieren.\n");
        s.append("   b) Erste Zeile: previous_hash muss leer sein.\n");
        s.append("   c) Jede weitere Zeile: previous_hash == entry_hash der vorigen Zeile.\n");
        s.append("4. manifest.sha256 prüft, dass die Dateien selbst nicht verändert wurden.\n\n");

        s.append("Datenschutzhinweis\n");
        s.append("------------------\n");
        s.append("Diese Datei enthält personenbezogene Daten (Bearbeiter-IDs, IP-Adressen,\n");
        s.append("Beträge, Kundenbezüge). Sie ist ausschließlich an autorisierte Prüfer\n");
        s.append("auszuhändigen und nach Abschluss der Prüfung sicher zu löschen.\n");

        return s.toString();
    }

    private String buildManifest(String dokumenteCsv, String auditCsv, String info) {
        StringBuilder m = new StringBuilder();
        m.append("# SHA-256 Manifest des Steuerprüfungs-Pakets\n");
        m.append("# Format: <hex>  <dateiname>\n");
        m.append(sha256(info)).append("  INFO.txt\n");
        m.append(sha256(dokumenteCsv)).append("  dokumente.csv\n");
        m.append(sha256(auditCsv)).append("  audit.csv\n");
        return m.toString();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ").replace(";", ",");
        return "\"" + escaped + "\"";
    }
}
