package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.Rechnungsuebersicht.AusgangsrechnungDto;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class RechnungsuebersichtService {
    private static final EnumSet<AusgangsGeschaeftsDokumentTyp> RECHNUNGSTYPEN = EnumSet.of(
            AusgangsGeschaeftsDokumentTyp.RECHNUNG, AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG,
            AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT, AusgangsGeschaeftsDokumentTyp.STORNO);

    private final AusgangsGeschaeftsDokumentRepository ausgangsRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final Path uploadPath;

    public RechnungsuebersichtService(AusgangsGeschaeftsDokumentRepository ausgangsRepository,
            ProjektDokumentRepository projektDokumentRepository, @Value("${file.upload-dir}") String uploadPath) {
        this.ausgangsRepository = ausgangsRepository;
        this.projektDokumentRepository = projektDokumentRepository;
        this.uploadPath = Path.of(uploadPath).toAbsolutePath().normalize();
    }

    public List<AusgangsrechnungDto> getAusgangsrechnungen(Integer year, Integer month, String search) {
        LocalDate start = null;
        LocalDate end = null;
        if (year != null) {
            start = month == null ? LocalDate.of(year, 1, 1) : YearMonth.of(year, month).atDay(1);
            end = month == null ? LocalDate.of(year, 12, 31) : YearMonth.of(year, month).atEndOfMonth();
        }
        var dokumente = ausgangsRepository.findRechnungenFuerUebersicht(RECHNUNGSTYPEN, start, end);
        var rechnungen = dokumente.stream().filter(this::istRechnung).toList();
        if (rechnungen.isEmpty()) return List.of();

        // Nur Zahlungsstatus und archivierte PDF-Dateien liegen noch in der alten Domäne.
        // Sie bestimmen weder Umfang noch Identität, Datum oder Betrag der Übersicht.
        Map<String, ProjektGeschaeftsdokument> details = new java.util.HashMap<>();
        var nummern = rechnungen.stream().map(AusgangsGeschaeftsDokument::getDokumentNummer).toList();
        for (int offset = 0; offset < nummern.size(); offset += 500) {
            for (var detail : projektDokumentRepository.findGeschaeftsdokumenteByDokumentidIn(
                    nummern.subList(offset, Math.min(offset + 500, nummern.size())))) {
                details.putIfAbsent(detail.getDokumentid(), detail);
            }
        }
        String query = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return rechnungen.stream().map(d -> toDto(d, details.get(d.getDokumentNummer())))
                .filter(d -> query.isEmpty() || matchesSearch(d, query))
                .sorted(Comparator.comparing((AusgangsrechnungDto d) -> d.rechnungsdatum,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean istRechnung(AusgangsGeschaeftsDokument d) {
        return RECHNUNGSTYPEN.contains(d.getTyp())
                && (d.isGebucht() || d.getVersandDatum() != null || d.isStorniert());
    }

    private AusgangsrechnungDto toDto(AusgangsGeschaeftsDokument d, ProjektGeschaeftsdokument detail) {
        var dto = new AusgangsrechnungDto();
        dto.id = d.getId();
        dto.dokumentid = d.getDokumentNummer();
        dto.geschaeftsdokumentart = switch (d.getTyp()) {
            case RECHNUNG -> "Rechnung";
            case TEILRECHNUNG -> "Teilrechnung";
            case ABSCHLAGSRECHNUNG -> "Abschlagsrechnung";
            case SCHLUSSRECHNUNG -> "Schlussrechnung";
            case GUTSCHRIFT -> "Gutschrift";
            case STORNO -> "Stornorechnung";
            default -> throw new IllegalArgumentException("Kein Rechnungstyp");
        };
        dto.rechnungsdatum = d.getDatum();
        dto.bruttoBetrag = d.getBetragBrutto() == null ? null : d.getBetragBrutto().doubleValue();
        dto.storniert = d.isStorniert();
        dto.storno = d.getTyp() == AusgangsGeschaeftsDokumentTyp.STORNO;
        dto.bezahlt = detail == null ? null : !dto.storniert && !dto.storno && detail.isBezahlt();
        Integer tage = d.getZahlungszielTage();
        if (tage == null && d.getKunde() != null) tage = d.getKunde().getZahlungsziel();
        dto.faelligkeitsdatum = tage == null || d.getDatum() == null ? null : d.getDatum().plusDays(tage);
        dto.originalDateiname = d.getDokumentNummer() + ".pdf";
        dto.editorUrl = "/dokument-editor?dokumentId=" + d.getId() + "&dokumentTyp=" + d.getTyp().name();
        String pdfName = d.getPdfDateiname() != null ? d.getPdfDateiname()
                : detail == null ? null : detail.getGespeicherterDateiname();
        if (pdfPath(pdfName) != null) dto.pdfUrl = "/api/dokumente/" + pdfName;
        var projekt = d.getProjekt() != null ? d.getProjekt()
                : d.getAnfrage() == null ? null : d.getAnfrage().getProjekt();
        if (projekt != null) {
            dto.projektId = projekt.getId();
            dto.projektAuftragsnummer = projekt.getAuftragsnummer();
            dto.projektKunde = projekt.getKunde();
        }
        if (d.getKunde() != null) dto.projektKunde = d.getKunde().getName();
        return dto;
    }

    private boolean matchesSearch(AusgangsrechnungDto d, String query) {
        return contains(d.dokumentid, query) || contains(d.geschaeftsdokumentart, query)
                || contains(d.rechnungsdatum, query) || contains(d.bruttoBetrag, query)
                || contains(d.projektAuftragsnummer, query) || contains(d.projektKunde, query);
    }

    private boolean contains(Object value, String query) {
        return value != null && value.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    public byte[] readAusgangsPdf(Long id) throws IOException {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var dokument = ausgangsRepository.findById(id)
                .filter(this::istRechnung)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (dokument.getPdfDateiname() != null) {
            Path path = pdfPath(dokument.getPdfDateiname());
            if (path != null) return Files.readAllBytes(path);
            throw fehlendesPdf(dokument.getDokumentNummer());
        }
        for (var detail : projektDokumentRepository.findGeschaeftsdokumenteByDokumentid(dokument.getDokumentNummer())) {
            Path path = pdfPath(detail.getGespeicherterDateiname());
            if (path != null) return Files.readAllBytes(path);
        }
        throw fehlendesPdf(dokument.getDokumentNummer());
    }

    private ResponseStatusException fehlendesPdf(String nummer) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "PDF für " + nummer + " fehlt. Bitte das Dokument im Dokumenteditor exportieren.");
    }

    private Path pdfPath(String name) {
        if (name == null || name.contains("/") || name.contains("\\") || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) return null;
        for (Path base : List.of(uploadPath, uploadPath.resolve("attachments"))) {
            Path path = base.resolve(name).normalize();
            if (path.startsWith(base) && Files.isRegularFile(path)) return path;
        }
        return null;
    }
}
