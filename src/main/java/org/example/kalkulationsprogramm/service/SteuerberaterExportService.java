package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.SteuerberaterPaketDto;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteuerberaterExportService {
    private final BelegRepository belegRepository;
    private final BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    private final LieferantDokumentRepository lieferantDokumentRepository;
    private final KasseEinstellungRepository kasseEinstellungRepository;
    private final FirmeninformationRepository firmeninformationRepository;
    private final BelegeKasseExportPdfService belegeKasseExportPdfService;
    private final KasseDatevExportService datevExportService;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    @Transactional(readOnly = true)
    public SteuerberaterPaketDto.Vorpruefung pruefe(int jahr, int monat) {
        List<Beleg> belege = belege(jahr, monat);
        KasseEinstellung einstellung = einstellung();
        List<SteuerberaterPaketDto.OffenerPunkt> offen = belege.stream()
                .map(this::offenerPunkt).flatMap(Optional::stream).toList();
        return SteuerberaterPaketDto.Vorpruefung.builder().anzahlBelege(belege.size()).offenePunkte(offen)
                .beraternummerFehlt(fehlend(einstellung.getDatevBeraternummer()))
                .mandantennummerFehlt(fehlend(einstellung.getDatevMandantennummer())).build();
    }

    @Transactional(readOnly = true)
    public byte[] erzeugeZip(int jahr, int monat, Mitarbeiter ersteller, boolean trotzdem) {
        SteuerberaterPaketDto.Vorpruefung pruefung = pruefe(jahr, monat);
        if (!trotzdem && !pruefung.getOffenePunkte().isEmpty()) throw new IllegalStateException("Export hat offene Punkte");
        List<Beleg> belege = belege(jahr, monat);
        KasseEinstellung einstellung = einstellung();
        String firma = firmeninformationRepository.findFirmeninformation().map(Firmeninformation::getFirmenname).orElse("Betrieb");
        String prefix = String.format("%04d-%02d_Kasse_%s/", jahr, monat, dateiname(firma, "Betrieb", 120));
        Map<Long, List<BelegKostenstellenAnteil>> splits = belegKostenstellenAnteilRepository.findByBelegIds(ids(belege)).stream()
                .collect(Collectors.groupingBy(a -> a.getBeleg().getId()));
        Map<Long, LieferantDokument> dokumente = lieferantDokumentRepository.findByBelegIds(ids(belege)).stream()
                .filter(d -> d.getBeleg() != null).collect(Collectors.toMap(d -> d.getBeleg().getId(), d -> d, (a, b) -> a));
        Path pdf = null;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            pdf = belegeKasseExportPdfService.generatePdf(jahr, monat, ersteller, true);
            write(zip, prefix + "01_Kassenbuch_" + jahr + "-" + monat + ".pdf", Files.readAllBytes(pdf));
            KasseDatevExportService.Parameter parameter = new KasseDatevExportService.Parameter(YearMonth.of(jahr, monat), belege, splits,
                    einstellung, "Export", firma, java.time.LocalDateTime.now(), trotzdem);
            write(zip, prefix + "02_Buchungen_DATEV_" + jahr + "-" + monat + ".csv", datevExportService.erzeugeCsvBytes(parameter));
            write(zip, prefix + "03_Eingangsrechnungen_" + jahr + "-" + monat + ".csv", eingangsrechnungen(belege, dokumente).getBytes(StandardCharsets.UTF_8));
            List<String> fehlende = new ArrayList<>();
            for (Beleg beleg : belege) kopiereBeleg(zip, prefix, beleg, fehlende);
            write(zip, prefix + "LIESMICH.txt", liesmich(einstellung, pruefung, trotzdem, fehlende).getBytes(StandardCharsets.UTF_8));
            zip.finish(); return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Steuerberater-Paket konnte nicht erstellt werden", e);
        } finally {
            if (pdf != null) try { Files.deleteIfExists(pdf); } catch (IOException e) { log.warn("Temporäre Export-PDF konnte nicht gelöscht werden", e); }
        }
    }

    private List<Beleg> belege(int jahr, int monat) { YearMonth ym = YearMonth.of(jahr, monat); return belegRepository.findGeprueftImZeitraumNachNummer(ym.atDay(1), ym.atEndOfMonth()); }
    private KasseEinstellung einstellung() { return kasseEinstellungRepository.findSingleton().orElseGet(KasseEinstellung::new); }
    private Optional<SteuerberaterPaketDto.OffenerPunkt> offenerPunkt(Beleg b) {
        boolean konto = b.getSachkonto() == null, zahlungsart = fehlend(b.getZahlungsart());
        if (!konto && !zahlungsart) return Optional.empty();
        String fehlt = konto && zahlungsart ? "Konto und Zahlungsart fehlen" : konto ? "Konto fehlt" : "Zahlungsart fehlt";
        String bezeichnung = b.getLieferant() == null ? b.getBeschreibung() : b.getLieferant().getLieferantenname();
        return Optional.of(SteuerberaterPaketDto.OffenerPunkt.builder().belegId(b.getId()).belegDatum(b.getBelegDatum()).bezeichnung(bezeichnung).wasFehlt(fehlt).build());
    }
    private static boolean fehlend(String value) { return value == null || value.isBlank(); }
    private static List<Long> ids(List<Beleg> belege) { return belege.stream().map(Beleg::getId).filter(Objects::nonNull).toList(); }
    private String eingangsrechnungen(List<Beleg> belege, Map<Long, LieferantDokument> dokumente) {
        StringBuilder csv = new StringBuilder("Nr;Datum;Lieferant;Netto;MwSt;Brutto;Konto;Kostenstelle;Zahlungsart;Bezahlt am\r\n");
        for (Beleg b : belege) {
            LieferantGeschaeftsdokument gd = Optional.ofNullable(dokumente.get(b.getId())).map(LieferantDokument::getGeschaeftsdaten).orElse(null);
            String bezahlt = gd != null && Boolean.TRUE.equals(gd.getBezahlt()) && gd.getBezahltAm() != null ? gd.getBezahltAm().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "offen";
            csv.append(esc(String.valueOf(b.getLaufendeNummer()))).append(';').append(esc(String.valueOf(b.getBelegDatum()))).append(';')
                    .append(esc(b.getLieferant() == null ? b.getBeschreibung() : b.getLieferant().getLieferantenname())).append(';')
                    .append(geld(b.getBuchungsbetragNetto())).append(';').append(geld(b.getMwstSatz())).append(';').append(geld(b.getBuchungsbetragBrutto())).append(';')
                    .append(esc(b.getSachkonto() == null ? "" : b.getSachkonto().getNummer())).append(';')
                    .append(esc(b.getKostenstelle() == null ? "" : b.getKostenstelle().getBezeichnung())).append(';').append(esc(b.getZahlungsart())).append(';').append(esc(bezahlt)).append("\r\n");
        } return csv.toString();
    }
    private void kopiereBeleg(ZipOutputStream zip, String prefix, Beleg b, List<String> fehlende) throws IOException {
        String gespeichert = b.getGespeicherterDateiname(); Path basis = Path.of(uploadPath, "belege").toAbsolutePath().normalize();
        if (gespeichert == null || gespeichert.isBlank() || gespeichert.contains("/") || gespeichert.contains("\\")) { fehlende.add(String.valueOf(gespeichert)); return; }
        Path quelle = basis.resolve(gespeichert).normalize();
        if (!quelle.startsWith(basis) || !Files.isRegularFile(quelle)) { fehlende.add(gespeichert); return; }
        String original = b.getOriginalDateiname(); String endung = original != null && original.lastIndexOf('.') >= 0 ? original.substring(original.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "").toLowerCase(Locale.ROOT) : ".pdf";
        if (!Set.of(".pdf", ".jpg", ".png").contains(endung)) { fehlende.add("ungültige Datei: " + gespeichert); return; }
        String nummer = b.getLaufendeNummer() == null ? "0000" + (b.getId() == null ? "" : b.getId()) : String.format("%04d", b.getLaufendeNummer());
        String lieferant = b.getLieferant() == null ? "Beleg" : b.getLieferant().getLieferantenname();
        write(zip, prefix + "04_Belege/" + nummer + "_" + b.getBelegDatum() + "_" + dateiname(lieferant, "Beleg", 40) + endung, Files.readAllBytes(quelle));
    }
    private static String liesmich(KasseEinstellung e, SteuerberaterPaketDto.Vorpruefung p, boolean trotzdem, List<String> fehlende) {
        StringBuilder s = new StringBuilder("Steuerberater-Paket\n\n01 enthält das Kassenbuch als PDF.\n02 enthält die Buchungen für den Steuerberater (DATEV-Datei, SKR03).\n03 enthält Eingangsrechnungen samt Zahlstatus.\n04 enthält die Originalbelege.\n\nKassenkonto: ").append(e.getKassenkontoNummer()).append("\nBankkonto: ").append(e.getBankkontoNummer()).append("\n\nBank- und Kreditkartenbuchungen sind bewusst nicht im DATEV-Stapel — das Programm kennt keine Bankumsätze. Beleg und Zahlstatus dazu stehen in Datei 03.\nKostenstellen erscheinen mit ihrem Namen, weil das Programm keine Kostenstellen-Nummern führt.\nVerfahrensdokumentation: GET /api/buchhaltung/kassenbuch/verfahrensdokumentation\n");
        if (trotzdem && !p.getOffenePunkte().isEmpty()) { s.append("\nOffene Punkte:\n"); p.getOffenePunkte().forEach(o -> s.append("PRÜFEN: Beleg ").append(o.getBelegId()).append(" - ").append(o.getWasFehlt()).append('\n')); }
        if (!fehlende.isEmpty()) { s.append("\nFehlende Dateien:\n"); fehlende.forEach(f -> s.append("- ").append(f).append('\n')); } return s.toString();
    }
    private static void write(ZipOutputStream zip, String name, byte[] content) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry(); }
    private static String dateiname(String value, String fallback, int max) { String sauber = (value == null ? "" : value).replaceAll("[^A-Za-z0-9-_]", "_"); return (sauber.isBlank() ? fallback : sauber).substring(0, Math.min(max, (sauber.isBlank() ? fallback : sauber).length())); }
    private static String esc(String value) { return "\"" + (value == null ? "" : value.replace("\"", "\"\"").replace('\r', ' ').replace('\n', ' ').replace(';', ',')) + "\""; }
    private static String geld(BigDecimal value) { return value == null ? "" : value.toPlainString().replace('.', ','); }
}
