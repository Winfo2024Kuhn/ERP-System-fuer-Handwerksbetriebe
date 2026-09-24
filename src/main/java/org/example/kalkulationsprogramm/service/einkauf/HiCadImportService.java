package org.example.kalkulationsprogramm.service.einkauf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.hssf.record.SupBookRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.kalkulationsprogramm.domain.einkauf.Einheit;
import org.example.kalkulationsprogramm.domain.einkauf.HiCadImport;
import org.example.kalkulationsprogramm.domain.einkauf.HiCadImportZeile;
import org.example.kalkulationsprogramm.domain.einkauf.Positionsart;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufBedarfDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufDateiDto.AnlageDto;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Liefergruppe;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.Mengenbasis;
import org.example.kalkulationsprogramm.dto.Einkauf.EinkaufPositionDto.PositionSnapshot;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto.Zeile;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto.ZeilenAuswahl;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto.BildVorschlag;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto.ImportFortschritt;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto.ZeilenFortschritt;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto.Vorschau;
import org.example.kalkulationsprogramm.dto.Einkauf.HiCadImportDto.Uebernahme;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.repository.HiCadImportRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Shape;

@Service
public class HiCadImportService {
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_COLUMNS = 20;
    private static final int MAX_CELL_TEXT = 4_000;
    private static final int MAX_HEADER_SEARCH_ROWS = 30;
    private static final Set<String> SAEGELISTE_BLATTNAMEN = Set.of("sägeliste", "saegeliste");
    private static final Set<String> ZEICHNUNGSNUMMER_LABELS = Set.of("zeichnungsnr.", "zeichnungsnr", "zeichnungsnummer");
    private static final Set<String> KOPFBLOCK_LABELS = Set.of("titel", "zeichnungsnr.", "zeichnungsnr", "zeichnungsnummer",
            "kunde", "auftragsnr.", "auftragsnr", "auftragsnummer", "auftragstext", "ersteller", "erstelltam", "benennung");
    /** Winkelangabe wie „45°“ oder „22,5°“; beschränkte, possessive Quantifizierer (ReDoS-sicher). */
    private static final Pattern WINKEL = Pattern.compile("\\d{1,3}+(?:[.,]\\d{1,2}+)?+°?+");
    /** Spaltenüberschrift (klein, ohne Leerzeichen) → logisches Feld. */
    private static final Map<String, String> HEADER_ALIASES = headerAliases();
    private final HiCadImportRepository imports;
    private final EinkaufBedarfService bedarfe;
    private final EinkaufDateiService dateien;
    private final ArtikelRepository artikelRepository;
    private final ObjectMapper json = new ObjectMapper();

    public HiCadImportService(HiCadImportRepository imports, EinkaufBedarfService bedarfe, EinkaufDateiService dateien) {
        this(imports, bedarfe, dateien, null);
    }

    @Autowired
    public HiCadImportService(HiCadImportRepository imports, EinkaufBedarfService bedarfe,
            EinkaufDateiService dateien, ArtikelRepository artikelRepository) {
        this.imports = imports; this.bedarfe = bedarfe; this.dateien = dateien;
        this.artikelRepository = artikelRepository;
    }

    @Transactional
    public Vorschau vorschau(Long projektId, MultipartFile file, SpaltenMapping mapping, Long akteurId) {
        if (projektId == null || projektId <= 0 || akteurId == null || akteurId <= 0)
            throw new IllegalArgumentException("Projekt und handelnder Benutzer müssen gültig sein.");
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES)
            throw new IllegalArgumentException("Die Excel-Datei fehlt oder überschreitet das Limit von 10 MiB.");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))
            throw new IllegalArgumentException("Bitte eine XLS- oder XLSX-Datei auswählen.");
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(MAX_FILE_BYTES + 1); }
        catch (IOException e) { throw new IllegalArgumentException("Die Excel-Datei konnte nicht gelesen werden.", e); }
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("Die Excel-Datei überschreitet das Limit von 10 MiB.");
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(MAX_FILE_BYTES);
        ZipSecureFile.setMaxFileCount(2000);
        rejectExternalContent(bytes, filename);
        String hash = sha256(bytes);
        boolean duplicate = imports.findFirstByProjektIdAndDateiHashOrderByIdDesc(projektId, hash).isPresent();
        Parsed parsed = parse(bytes, filename.endsWith(".xlsx"), mapping);
        HiCadImport entity = new HiCadImport(projektId, hash, akteurId, duplicate);
        List<PendingPreview> pendingRows = new ArrayList<>();
        for (ParsedRow row : parsed.rows()) {
            String snapshot = serialize(row.snapshot());
            HiCadImportZeile importRow = new HiCadImportZeile(row.rowNumber(), row.raw(), snapshot);
            importRow.setBildDateiIdsJson(serialize(row.images().stream().map(EinkaufDateiService.ImportBildDto::id).toList()));
            entity.addZeile(importRow);
            List<String> hints = new ArrayList<>(row.hints());
            if (row.snapshot().werkstoff() != null && row.snapshot().werkstoff().matches("(?i).*S235.*S355.*|.*S355.*S235.*"))
                hints.add("Werkstoffgüte ist uneindeutig und muss geprüft werden.");
            List<Long> candidates = parsed.katalogAbgleich() ? artikelKandidaten(row.snapshot()) : List.of();
            if (parsed.katalogAbgleich() && row.snapshot().interneReferenz() != null && !row.snapshot().interneReferenz().isBlank()
                    && candidates.isEmpty()) hints.add("Kein technisch eindeutiger Katalogartikel gefunden; bitte Position prüfen.");
            pendingRows.add(new PendingPreview(row, candidates, List.copyOf(hints)));
        }
        HiCadImport persisted = imports.save(entity);
        List<Zeile> previewRows = pendingRows.stream().map(p -> new Zeile(p.row().rowNumber(), p.row().raw(),
                p.row().snapshot(), p.candidates(), false, p.hints(), p.row().images().stream()
                        .map(image -> new BildVorschlag(image.id(), image.dateiname(), image.mimeTyp(), image.byteAnzahl(),
                                "/api/einkauf/hicad/" + persisted.getId() + "/bilder/" + image.id())).toList())).toList();
        return new Vorschau(persisted.getId(), hash, duplicate, List.copyOf(previewRows));
    }

    @Transactional
    public BildVorschlag anlageErgänzen(Long importId, int zeilennummer, MultipartFile datei, Long akteurId) {
        if (importId == null || importId <= 0 || zeilennummer <= 0 || akteurId == null || akteurId <= 0)
            throw new IllegalArgumentException("Import, Zeile und Benutzer müssen gültig sein.");
        HiCadImport vorgang = imports.findByIdForUpdate(importId)
                .orElseThrow(() -> new NotFoundException("Der Import wurde nicht gefunden."));
        if (!akteurId.equals(vorgang.getAkteurId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        HiCadImportZeile zeile = vorgang.getZeilen().stream().filter(eintrag -> eintrag.getZeilennummer() == zeilennummer)
                .findFirst().orElseThrow(() -> new NotFoundException("Die Zeile gehört nicht zu diesem Import."));
        if (zeile.isUebernommen()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Zeile wurde bereits vollständig übernommen.");
        List<Long> anlagen = new ArrayList<>(parseBildIds(zeile.getBildDateiIdsJson()));
        if (anlagen.size() >= 50) throw new IllegalArgumentException("Pro Zeile sind höchstens 50 Anlagen erlaubt.");
        var gespeichert = dateien.speichereImportAnlage(datei);
        if (!anlagen.contains(gespeichert.id())) anlagen.add(gespeichert.id());
        zeile.setBildDateiIdsJson(serialize(anlagen));
        imports.save(vorgang);
        return new BildVorschlag(gespeichert.id(), gespeichert.dateiname(), gespeichert.mimeTyp(), gespeichert.byteAnzahl(),
                "/api/einkauf/hicad/" + importId + "/bilder/" + gespeichert.id());
    }

    @Transactional
    public List<EinkaufBedarfDto.Response> uebernehmen(Long importId, Uebernahme request, Long akteurId) {
        if (importId == null || importId <= 0 || request == null || request.idempotenzKey() == null)
            throw new IllegalArgumentException("Import und Idempotenzschlüssel müssen angegeben werden.");
        if (akteurId == null || akteurId <= 0) throw new IllegalArgumentException("Der handelnde Benutzer fehlt.");
        HiCadImport imp = imports.findByIdForUpdate(importId).orElseThrow(() -> new NotFoundException("Der Import wurde nicht gefunden."));
        String payloadHash = sha256("duplikatBewusst=" + request.duplikatBewusst() + "&zeilen="
                + (request.zeilen() == null ? "" : request.zeilen().toString()));
        List<StoredTransferResult> priorResults = parseTransferResults(imp.getIdempotenzErgebnisseJson());
        for (StoredTransferResult prior : priorResults) {
            if (!request.idempotenzKey().toString().equals(prior.idempotenzKey())) continue;
            if (!payloadHash.equals(prior.payloadHash())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Idempotenzschlüssel wurde mit anderen Zeilen verwendet.");
            return parseResult(prior.resultJson());
        }
        if (request.idempotenzKey().toString().equals(imp.getIdempotenzKey())) {
            if (!payloadHash.equals(imp.getPayloadHash())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Idempotenzschlüssel wurde mit anderen Zeilen verwendet.");
            return parseResult(imp.getResultJson());
        }
        if (imp.getVersion() != null && imp.getVersion() != request.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Importvorschau wurde zwischenzeitlich geändert.");
        if (imp.isDuplikat() && !request.duplikatBewusst()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Datei wurde bereits importiert. Bitte den erneuten Import ausdrücklich bestätigen.");
        if (request.version() < 0 || request.zeilen() == null || request.zeilen().isEmpty())
            throw new IllegalArgumentException("Bitte mindestens eine Zeile auswählen.");
        Map<Integer, HiCadImportZeile> byRow = imp.getZeilen().stream().collect(java.util.stream.Collectors.toMap(HiCadImportZeile::getZeilennummer, row -> row));
        List<EinkaufBedarfDto.Response> created = new ArrayList<>();
        java.util.Set<Integer> selectedRows = new java.util.HashSet<>();
        for (ZeilenAuswahl selection : request.zeilen()) {
            if (!selectedRows.add(selection.zeilennummer())) throw new IllegalArgumentException("Eine Excel-Zeile wurde doppelt ausgewählt.");
            HiCadImportZeile row = byRow.get(selection.zeilennummer());
            if (row == null) throw new IllegalArgumentException("Die gewählte Excel-Zeile gehört nicht zu diesem Import.");
            if (row.isUebernommen()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Zeile wurde bereits vollständig übernommen.");
            PositionSnapshot original = parseSnapshot(row.getSnapshotJson());
            if (original.basis() == null || original.basis().menge() == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Für diese Importzeile fehlt die Gesamtmenge.");
            BigDecimal totalQuantity = original.basis().menge().setScale(6, java.math.RoundingMode.HALF_UP);
            BigDecimal alreadyTransferred = row.getUebernommeneMenge();
            BigDecimal remaining = totalQuantity.subtract(alreadyTransferred);
            if (remaining.signum() <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Diese Zeile wurde bereits vollständig übernommen.");
            PositionSnapshot snapshot = selection.korrigiert() == null ? original : selection.korrigiert();
            BigDecimal selectedQuantity = selection.menge() == null
                    ? (snapshot.basis() == null ? null : snapshot.basis().menge()) : selection.menge();
            if (snapshot.basis() == null || snapshot.basis().einheit() != original.basis().einheit())
                throw new IllegalArgumentException("Die Einheit einer Importzeile darf bei einer Teilübernahme nicht geändert werden.");
            if (selectedQuantity != null) {
                if (selectedQuantity.stripTrailingZeros().scale() > 6)
                    throw new IllegalArgumentException("Die Teilmenge darf höchstens sechs Nachkommastellen haben.");
                selectedQuantity = selectedQuantity.setScale(6);
            }
            snapshot = withQuantity(snapshot, selectedQuantity, remaining);
            List<Long> imageIds = parseBildIds(row.getBildDateiIdsJson());
            List<Long> confirmedIds = selection.bestaetigteBildDateiIds() == null ? List.of() : selection.bestaetigteBildDateiIds();
            if (!new java.util.HashSet<>(confirmedIds).equals(new java.util.HashSet<>(imageIds)) || confirmedIds.size() != imageIds.size())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bitte bestätigen Sie alle eingebetteten Bilder dieser Zeile.");
            if (snapshot.art() == Positionsart.ZEICHNUNGSTEIL && imageIds.isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bitte fügen Sie vor der Übernahme eine technische Anlage hinzu.");
            Liefergruppe group = new Liefergruppe(null, null, imp.getProjektId(), null);
            PositionSnapshot staging = withAttachments(snapshot, List.of(Long.MAX_VALUE));
            EinkaufBedarfDto.Response newNeed = bedarfe.anlegen(new EinkaufBedarfDto.Create(staging, group, null), akteurId);
            List<Long> versionIds = new ArrayList<>();
            int imageIndex = 1;
            for (Long imageId : imageIds) {
                var version = dateien.anhaengenImportBild(newNeed.id(), imageId,
                        "HiCAD-" + imp.getImportInstanz().substring(0, 8) + "-Z" + selection.zeilennummer()
                                + "-B" + imageIndex++ + "-" + request.idempotenzKey().toString().substring(0, 8), akteurId);
                dateien.freigeben(version.id(), akteurId);
                versionIds.add(version.id());
            }
            PositionSnapshot finalSnapshot = withAttachments(snapshot, versionIds);
            created.add(bedarfe.aktualisieren(newNeed.id(), new EinkaufBedarfDto.Update(newNeed.version(), finalSnapshot, group), akteurId));
            BigDecimal transferredTotal = alreadyTransferred.add(selectedQuantity);
            row.setUebernommeneMenge(transferredTotal);
            row.setUebernommen(transferredTotal.compareTo(totalQuantity) >= 0);
        }
        priorResults.add(new StoredTransferResult(request.idempotenzKey().toString(), payloadHash, serialize(created)));
        imp.setIdempotenzErgebnisseJson(serialize(priorResults));
        imp.setIdempotenzKey(request.idempotenzKey().toString());
        imp.setPayloadHash(payloadHash);
        imp.setResultJson(serialize(created));
        imports.save(imp);
        return List.copyOf(created);
    }

    @Transactional(readOnly = true)
    public ImportFortschritt fortschritt(Long importId, Long akteurId) {
        if (importId == null || importId <= 0 || akteurId == null || akteurId <= 0)
            throw new IllegalArgumentException("Import und handelnder Benutzer müssen gültig sein.");
        HiCadImport imp = imports.findById(importId)
                .orElseThrow(() -> new NotFoundException("Der Import wurde nicht gefunden."));
        if (!akteurId.equals(imp.getAkteurId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        List<ZeilenFortschritt> rows = imp.getZeilen().stream().map(row -> {
            PositionSnapshot source = parseSnapshot(row.getSnapshotJson());
            BigDecimal total = source.basis().menge().setScale(6, java.math.RoundingMode.HALF_UP);
            BigDecimal transferred = row.getUebernommeneMenge();
            BigDecimal remaining = total.subtract(transferred).max(BigDecimal.ZERO);
            return new ZeilenFortschritt(row.getZeilennummer(), total, transferred, remaining, row.isUebernommen());
        }).toList();
        return new ImportFortschritt(imp.getId(), imp.getVersion() == null ? 0 : imp.getVersion(), imp.isDuplikat(), rows);
    }

    private Parsed parse(byte[] bytes, boolean xlsx, SpaltenMapping requested) {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(MAX_FILE_BYTES);
        ZipSecureFile.setMaxFileCount(2000);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("Die Excel-Datei enthält kein Tabellenblatt.");
            int sheetIndex = tabellenblattIndex(workbook);
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("Die Tabelle enthält mehr als 10.000 Zeilen.");
            DataFormatter formatter = new DataFormatter(Locale.GERMANY);
            Row header = findeUeberschriftenzeile(sheet, formatter);
            if (header == null || header.getLastCellNum() > MAX_COLUMNS) throw new IllegalArgumentException("Die Tabelle darf höchstens 20 Spalten enthalten.");
            Map<String, Integer> detected = detectMapping(header, formatter);
            Map<String, Integer> columns = requested == null ? detected : mitPositionsspalte(validateMapping(requested), detected);
            if (!columns.containsKey("menge")) throw new IllegalArgumentException("Bitte ordnen Sie die Mengenspalte zu.");
            boolean positionsliste = columns.containsKey("position");
            String kopfZeichnungsnummer = columns.containsKey("zeichnungsnummer") ? null
                    : kopfwert(sheet, header.getRowNum(), formatter, ZEICHNUNGSNUMMER_LABELS);
            List<ParsedRow> rows = new ArrayList<>();
            for (int number = header.getRowNum() + 1; number <= sheet.getLastRowNum(); number++) {
                Row row = sheet.getRow(number);
                if (row == null || empty(row)) continue;
                // HiCAD-Positionslisten enden mit einer Summenzeile ohne Positionsnummer (teils mit Formeln).
                if (positionsliste && ohnePositionOderBezeichnung(row, columns)) continue;
                if (row.getLastCellNum() > MAX_COLUMNS) throw new IllegalArgumentException("Die Tabelle darf höchstens 20 Spalten enthalten.");
                String raw = raw(row, formatter);
                if (raw.length() > MAX_CELL_TEXT) throw new IllegalArgumentException("Eine Zeile enthält zu viele Zeichen.");
                try { rows.add(new ParsedRow(number + 1, raw, toSnapshot(row, columns, formatter, kopfZeichnungsnummer), List.of(), List.of())); }
                catch (IllegalArgumentException error) { rows.add(new ParsedRow(number + 1, raw, emptySnapshot(), List.of(error.getMessage()), List.of())); }
            }
            attachEmbeddedPictures(workbook, sheetIndex, rows);
            return new Parsed(rows, !positionsliste || columns.containsKey("interneReferenz"));
        } catch (IOException e) {
            throw new IllegalArgumentException("Die Excel-Datei ist beschädigt oder das Format wird nicht unterstützt.", e);
        }
    }

    /** HiCAD-Stahlbau-Exporte haben viele Blätter; die Positionsliste steht im Blatt „Sägeliste“. */
    private static int tabellenblattIndex(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = Normalizer.normalize(workbook.getSheetName(i), Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT);
            if (SAEGELISTE_BLATTNAMEN.contains(name)) return i;
        }
        return 0;
    }

    /**
     * Sucht in den ersten Zeilen die Überschriftenzeile: zuerst eine Zeile mit Mengenspalte und mindestens
     * einer weiteren bekannten Spalte, sonst eine Zeile mit mindestens drei bekannten Spalten, sonst Zeile 1.
     */
    private static Row findeUeberschriftenzeile(Sheet sheet, DataFormatter formatter) {
        int first = sheet.getFirstRowNum();
        if (first < 0) return null;
        int last = Math.min(sheet.getLastRowNum(), first + MAX_HEADER_SEARCH_ROWS - 1);
        Row ohneMenge = null;
        for (int number = first; number <= last; number++) {
            Row row = sheet.getRow(number);
            if (row == null || row.getLastCellNum() > MAX_COLUMNS) continue;
            Map<String, Integer> mapping = detectMapping(row, formatter);
            int bekannteSpalten = new java.util.HashSet<>(mapping.values()).size();
            if (mapping.containsKey("menge") && bekannteSpalten >= 2) return row;
            if (ohneMenge == null && bekannteSpalten >= 3) ohneMenge = row;
        }
        return ohneMenge != null ? ohneMenge : sheet.getRow(first);
    }

    private static Map<String, Integer> detectMapping(Row header, DataFormatter formatter) {
        Map<String, Integer> found = new java.util.HashMap<>();
        for (Cell cell : header) {
            if (cell.getColumnIndex() >= MAX_COLUMNS) continue;
            String key = HEADER_ALIASES.get(normalizeHeader(headerText(cell, formatter)));
            if (key != null) found.putIfAbsent(key, cell.getColumnIndex());
        }
        return withQuantityFallback(found);
    }

    private static Map<String, Integer> validateMapping(SpaltenMapping mapping) {
        if (mapping == null || mapping.spalten() == null) throw new IllegalArgumentException("Das Spaltenmapping fehlt.");
        if (mapping.spalten().values().stream().anyMatch(index -> index == null || index < 0 || index >= MAX_COLUMNS))
            throw new IllegalArgumentException("Eine Spaltenzuordnung ist ungültig.");
        return withQuantityFallback(new java.util.HashMap<>(mapping.spalten()));
    }

    /** Bei Stücklisten ohne eigene Mengenspalte (HiCAD: „Anzahl“) ist die Stückzahl die Menge. */
    private static Map<String, Integer> withQuantityFallback(Map<String, Integer> mapping) {
        if (!mapping.containsKey("menge") && mapping.containsKey("stueckzahl")) mapping.put("menge", mapping.get("stueckzahl"));
        return Map.copyOf(mapping);
    }

    /** Die manuelle Zuordnung kennt keine Pos.-Spalte; sie wird aus der erkannten Überschrift ergänzt. */
    private static Map<String, Integer> mitPositionsspalte(Map<String, Integer> requested, Map<String, Integer> detected) {
        if (requested.containsKey("position") || !detected.containsKey("position")) return requested;
        Map<String, Integer> merged = new java.util.HashMap<>(requested);
        merged.put("position", detected.get("position"));
        return Map.copyOf(merged);
    }

    private static boolean ohnePositionOderBezeichnung(Row row, Map<String, Integer> columns) {
        if (blankCell(row, columns.get("position"))) return true;
        return blankCell(row, columns.get("bezeichnung")) && blankCell(row, columns.get("abmessung"))
                && blankCell(row, columns.get("benennung"));
    }

    /** Liest einen Wert aus dem Kopfblock oberhalb der Überschriftenzeile (z. B. „Zeichnungsnr.“). */
    private static String kopfwert(Sheet sheet, int headerRow, DataFormatter formatter, java.util.Set<String> labels) {
        for (int number = Math.max(0, sheet.getFirstRowNum()); number < headerRow; number++) {
            Row row = sheet.getRow(number);
            if (row == null) continue;
            int last = Math.min(MAX_COLUMNS, Math.max(0, row.getLastCellNum()));
            for (int column = 0; column < last; column++) {
                if (!labels.contains(normalizeHeader(headerText(row.getCell(column), formatter)))) continue;
                for (int candidate = column + 1; candidate < last && candidate <= column + 2; candidate++) {
                    String text = headerText(row.getCell(candidate), formatter);
                    if (KOPFBLOCK_LABELS.contains(normalizeHeader(text))) break;
                    if (!text.isBlank()) return text.length() > 128 ? null : text;
                }
            }
        }
        return null;
    }

    private static PositionSnapshot toSnapshot(Row row, Map<String, Integer> map, DataFormatter formatter,
            String kopfZeichnungsnummer) {
        boolean positionsliste = map.containsKey("position");
        String quantityRaw = value(row, map, "menge", formatter);
        BigDecimal quantity = parseDecimal(quantityRaw, "Menge");
        String unitRaw = value(row, map, "einheit", formatter);
        Einheit unit = parseUnit(unitRaw);
        BigDecimal pieces = decimalOrNull(value(row, map, "stueckzahl", formatter));
        BigDecimal length = rundeLaenge(decimalOrNull(value(row, map, "einzelLaengeMm", formatter)));
        if (unit == Einheit.METER && pieces != null && length != null) quantity = pieces.multiply(length).divide(new BigDecimal("1000"), 6, java.math.RoundingMode.HALF_UP);
        BigDecimal kgJeMeter = kgJeMeter(decimalOrNull(value(row, map, "gewichtKg", formatter)), length);
        Mengenbasis basis = new Mengenbasis(quantity, unit, pieces, length, kgJeMeter, kgJeMeter == null ? null : "HiCAD-Stückgewicht");
        // In HiCAD-Positionslisten steht in „Bezeichnung“ das Profil (z. B. „HEB 220“), der Teilname in „Benennung“.
        String abmessung = value(row, map, "abmessung", formatter);
        if (abmessung == null && positionsliste) abmessung = value(row, map, "bezeichnung", formatter);
        String bezeichnung = firstNonBlank(value(row, map, "benennung", formatter), value(row, map, "bezeichnung", formatter));
        Anschnitt steg = anschnitt(untrimmedValue(row, map, "anschnittSteg", formatter));
        Anschnitt flansch = anschnitt(untrimmedValue(row, map, "anschnittFlansch", formatter));
        String schnittForm = steg.vorhanden() && flansch.vorhanden() ? "Anschnitt Steg und Flansch"
                : steg.vorhanden() ? "Anschnitt Steg" : flansch.vorhanden() ? "Anschnitt Flansch" : null;
        return new PositionSnapshot(Positionsart.ZEICHNUNGSTEIL, null,
                firstNonBlank(value(row, map, "interneReferenz", formatter), value(row, map, "position", formatter)),
                firstNonBlank(value(row, map, "zeichnungsnummer", formatter), kopfZeichnungsnummer),
                defaultValue(value(row, map, "zeichnungsrevision", formatter), "Ungeprüft"),
                defaultValue(bezeichnung, "Importierte HiCAD-Position"),
                value(row, map, "werkstoff", formatter), abmessung, basis, schnittForm,
                firstNonBlank(value(row, map, "winkelLinks", formatter), steg.links(), flansch.links()),
                firstNonBlank(value(row, map, "winkelRechts", formatter), steg.rechts(), flansch.rechts()),
                null, value(row, map, "oberflaeche", formatter), List.of(), List.of());
    }

    /** HiCAD liefert Längen mit bis zu 12 Nachkommastellen; für den Einkauf reicht eine Nachkommastelle. */
    private static BigDecimal rundeLaenge(BigDecimal length) {
        return length == null || length.scale() <= 1 ? length : length.setScale(1, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal kgJeMeter(BigDecimal stueckgewichtKg, BigDecimal laengeMm) {
        if (stueckgewichtKg == null || laengeMm == null || stueckgewichtKg.signum() <= 0 || laengeMm.signum() <= 0) return null;
        BigDecimal result = stueckgewichtKg.multiply(new BigDecimal("1000")).divide(laengeMm, 6, java.math.RoundingMode.HALF_UP);
        return result.signum() > 0 ? result : null;
    }

    /**
     * HiCAD schreibt Anschnittwinkel links- bzw. rechtsbündig in eine Zelle („45°      45°“, „      45°“).
     * Zwei Winkel = links und rechts; ein einzelner Winkel wird nach seiner Lage in der Zelle zugeordnet.
     */
    private static Anschnitt anschnitt(String text) {
        if (text == null || text.isBlank()) return new Anschnitt(null, null);
        Matcher matcher = WINKEL.matcher(text);
        List<String> winkel = new ArrayList<>();
        int start = -1;
        int end = -1;
        while (winkel.size() < 2 && matcher.find()) {
            if (winkel.isEmpty()) { start = matcher.start(); end = matcher.end(); }
            winkel.add(matcher.group());
        }
        if (winkel.isEmpty()) return new Anschnitt(null, null);
        if (winkel.size() == 2) return new Anschnitt(winkel.get(0), winkel.get(1));
        return start <= text.length() - end ? new Anschnitt(winkel.get(0), null) : new Anschnitt(null, winkel.get(0));
    }

    private void attachEmbeddedPictures(Workbook workbook, int sheetIndex, List<ParsedRow> rows) {
        if (!(workbook instanceof XSSFWorkbook xssf) || rows.isEmpty()) return;
        Drawing<?> drawing = xssf.getSheetAt(sheetIndex).getDrawingPatriarch();
        if (drawing == null) return;
        for (Shape shape : drawing) if (shape instanceof XSSFPicture picture && picture.getClientAnchor() != null) {
            int row = picture.getClientAnchor().getRow1() + 1;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).rowNumber() == row) {
                var old = rows.get(i);
                List<String> hints = new ArrayList<>(old.hints());
                List<EinkaufDateiService.ImportBildDto> images = new ArrayList<>(old.images());
                try {
                    var pictureData = picture.getPictureData();
                    String partName = pictureData.getPackagePart().getPartName().getName();
                    String filename = partName.substring(partName.lastIndexOf('/') + 1);
                    if (filename == null || filename.isBlank()) filename = "HiCAD-Bild-" + row + ".png";
                    images.add(dateien.speichereImportBild(filename, pictureData.getMimeType(), pictureData.getData()));
                    hints.add("Eingebettetes Bild gespeichert; bitte vor der Übernahme als ungeprüfte Anlage bestätigen.");
                } catch (IllegalArgumentException error) {
                    hints.add("Eingebettetes Bild konnte nicht übernommen werden: " + error.getMessage());
                }
                rows.set(i, new ParsedRow(old.rowNumber(), old.raw(), old.snapshot(), List.copyOf(hints), List.copyOf(images)));
            }
        }
    }

    @Transactional(readOnly = true)
    public ImportBildRessource ladeBild(Long importId, Long dateiId, Authentication auth) {
        HiCadImport imp = imports.findById(importId).orElseThrow(() -> new NotFoundException("Der Import wurde nicht gefunden."));
        boolean attached = imp.getZeilen().stream().anyMatch(row -> parseBildIds(row.getBildDateiIdsJson()).contains(dateiId));
        if (!attached) throw new NotFoundException("Das Bild gehört nicht zu diesem Import.");
        var metadata = dateien.findImportBild(dateiId);
        return new ImportBildRessource(dateien.ladeImportBild(dateiId, auth), metadata.mimeTyp());
    }

    private List<Long> artikelKandidaten(PositionSnapshot position) {
        if (artikelRepository == null || position.interneReferenz() == null || position.interneReferenz().isBlank()) return List.of();
        Artikel article = artikelRepository.findHiCadByArtikelnummer(position.interneReferenz().trim()).orElse(null);
        if (article == null || !sameTechnicalValue(position.werkstoff(), articleWerkstoff(article))) return List.of();
        String dimension = position.abmessung();
        if (dimension != null && !dimension.isBlank()
                && !sameTechnicalValue(dimension, article.getProduktname())
                && !sameTechnicalValue(dimension, article.getHicadName())) return List.of();
        return article.getId() == null ? List.of() : List.of(article.getId());
    }

    private static String articleWerkstoff(Artikel article) {
        if (article.getWerkstoffnorm() != null && !article.getWerkstoffnorm().isBlank()) return article.getWerkstoffnorm();
        if (article.getWerkstoff() == null) return null;
        if (article.getWerkstoff().getWerkstoffnorm() != null && !article.getWerkstoff().getWerkstoffnorm().isBlank())
            return article.getWerkstoff().getWerkstoffnorm();
        return article.getWerkstoff().getName();
    }

    private static boolean sameTechnicalValue(String source, String catalog) {
        return source != null && catalog != null && source.trim().replaceAll("\\s+", "")
                .equalsIgnoreCase(catalog.trim().replaceAll("\\s+", ""));
    }

    private static void rejectExternalContent(byte[] bytes, String filename) {
        try {
            if (filename.endsWith(".xlsx")) {
                try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
                    if (pkg.getParts().size() > 2000 || hasExternalRelationship(pkg.getRelationships()))
                        throw forbiddenExternalContent();
                    for (var part : pkg.getParts()) {
                        String name = part.getPartName().getName().toLowerCase(Locale.ROOT);
                        String contentType = part.getContentType().toLowerCase(Locale.ROOT);
                        if (name.contains("vbaproject") || name.startsWith("/xl/externallinks/")
                                || name.endsWith("/connections.xml") || contentType.contains("vbaproject")
                                || !part.isRelationshipPart() && hasExternalRelationship(part.getRelationships()))
                            throw forbiddenExternalContent();
                    }
                }
                try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    if (workbook.isMacroEnabled() || !workbook.getExternalLinksTable().isEmpty())
                        throw forbiddenExternalContent();
                }
                return;
            }
            try (POIFSFileSystem compound = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
                if (containsVbaProject(compound.getRoot())) throw forbiddenExternalContent();
                try (HSSFWorkbook workbook = new HSSFWorkbook(compound)) {
                    boolean external = workbook.getInternalWorkbook().getWorkbookRecordList().getRecords().stream()
                            .filter(SupBookRecord.class::isInstance).map(SupBookRecord.class::cast)
                            .anyMatch(SupBookRecord::isExternalReferences);
                    if (external) throw forbiddenExternalContent();
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Die Excel-Datei ist beschädigt oder nicht sicher lesbar.", e);
        }
    }

    private static boolean hasExternalRelationship(Iterable<PackageRelationship> relationships) {
        for (PackageRelationship relationship : relationships)
            if (relationship.getTargetMode() == TargetMode.EXTERNAL) return true;
        return false;
    }

    private static boolean containsVbaProject(DirectoryEntry directory) {
        for (Entry entry : directory) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (name.contains("vba") || entry instanceof DirectoryEntry child && containsVbaProject(child)) return true;
        }
        return false;
    }

    private static IllegalArgumentException forbiddenExternalContent() {
        return new IllegalArgumentException("Makros und externe Verknüpfungen sind nicht erlaubt.");
    }

    private static String raw(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_COLUMNS, Math.max(0, row.getLastCellNum())); i++) values.add(cellText(row.getCell(i), formatter));
        return String.join(" | ", values);
    }
    private static boolean empty(Row row) { for (Cell cell : row) if (cell.getCellType() != CellType.BLANK) return false; return true; }
    private static String value(Row row, Map<String, Integer> map, String field, DataFormatter formatter) {
        Integer index = map.get(field); return index == null ? null : nullIfBlank(cellText(row.getCell(index), formatter));
    }
    private static String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA) throw new IllegalArgumentException("Formeln werden aus Sicherheitsgründen nicht ausgewertet.");
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell))
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        String value = formatter.formatCellValue(cell);
        if (value.length() > MAX_CELL_TEXT) throw new IllegalArgumentException("Ein Tabellenwert ist zu lang.");
        return value.trim();
    }
    private static BigDecimal parseDecimal(String value, String field) {
        BigDecimal result = decimalOrNull(value);
        if (result == null || result.signum() <= 0) throw new IllegalArgumentException(field + " muss größer als 0 sein.");
        return result;
    }
    private static BigDecimal decimalOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replace(" ", "").replace("'", "");
        if (normalized.contains(",") && normalized.contains(".")) normalized = normalized.replace(".", "").replace(',', '.');
        else normalized = normalized.replace(',', '.');
        try { return new BigDecimal(normalized); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Bitte prüfen Sie die Zahl „" + value + "“."); }
    }
    private static Einheit parseUnit(String value) {
        if (value == null || value.isBlank()) return Einheit.STUECK;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "st", "stück", "stueck", "pcs", "piece" -> Einheit.STUECK;
            case "m", "meter", "lfm", "laufmeter" -> Einheit.METER;
            case "kg", "kilogramm" -> Einheit.KILOGRAMM;
            case "t", "tonne", "to" -> Einheit.TONNE;
            case "m²", "m2", "qm" -> Einheit.QUADRATMETER;
            default -> throw new IllegalArgumentException("Die Einheit „" + value + "“ wird nicht unterstützt.");
        };
    }
    private static PositionSnapshot withQuantity(PositionSnapshot position, BigDecimal selected, BigDecimal available) {
        if (selected == null || selected.signum() <= 0) throw new IllegalArgumentException("Die Teilmenge muss größer als 0 sein.");
        Mengenbasis old = position.basis();
        if (old == null || old.menge() == null || selected.compareTo(available) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Teilmenge überschreitet die noch offene Importmenge.");
        BigDecimal pieces = old.stueckzahl();
        if (old.einheit() == Einheit.STUECK && selected.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("Die Stückzahl muss ganzzahlig sein.");
        if (old.einheit() == Einheit.STUECK) pieces = selected;
        Mengenbasis basis = new Mengenbasis(selected.setScale(6), old.einheit(), pieces,
                old.einzelLaengeMm(), old.kgJeMeter(), old.faktorQuelle());
        return new PositionSnapshot(position.art(), position.artikelId(), position.interneReferenz(), position.zeichnungsnummer(),
                position.zeichnungsrevision(), position.bezeichnung(), position.werkstoff(), position.abmessung(), basis,
                position.schnittForm(), position.winkelLinks(), position.winkelRechts(), position.bearbeitung(),
                position.oberflaeche(), position.dokumente(), position.anlageVersionIds(), position.beschaffungsdetails());
    }
    private static PositionSnapshot emptySnapshot() {
        return new PositionSnapshot(Positionsart.ZEICHNUNGSTEIL, null, null, null, null, null, null, null, null,
                null, null, null, null, null, List.of(), List.of());
    }
    private static PositionSnapshot withAttachments(PositionSnapshot position, List<Long> ids) {
        return new PositionSnapshot(position.art(), position.artikelId(), position.interneReferenz(), position.zeichnungsnummer(),
                position.zeichnungsrevision(), position.bezeichnung(), position.werkstoff(), position.abmessung(), position.basis(),
                position.schnittForm(), position.winkelLinks(), position.winkelRechts(), position.bearbeitung(), position.oberflaeche(),
                position.dokumente(), ids, position.beschaffungsdetails());
    }
    private List<Long> parseBildIds(String jsonValue) {
        if (jsonValue == null || jsonValue.isBlank()) return List.of();
        try { return json.readerForListOf(Long.class).readValue(jsonValue); }
        catch (IOException e) { throw new IllegalStateException("Die Bildreferenzen des Imports sind beschädigt.", e); }
    }
    private PositionSnapshot parseSnapshot(String value) {
        try { return json.readValue(value, PositionSnapshot.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Die gespeicherte Importzeile ist beschädigt.", e); }
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Der Importstand konnte nicht gespeichert werden.", e); }
    }
    private List<EinkaufBedarfDto.Response> parseResult(String value) {
        if (value == null) return List.of();
        try { return json.readerForListOf(EinkaufBedarfDto.Response.class).readValue(value); }
        catch (IOException e) { throw new IllegalStateException("Das Übernahmeergebnis konnte nicht gelesen werden.", e); }
    }
    private List<StoredTransferResult> parseTransferResults(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        try { return json.readerForListOf(StoredTransferResult.class).readValue(value); }
        catch (IOException e) { throw new IllegalStateException("Die Idempotenzhistorie des Imports ist beschädigt.", e); }
    }
    private static Map<String, String> headerAliases() {
        Map<String, String> aliases = new java.util.HashMap<>();
        alias(aliases, "interneReferenz", "internenummer", "interneartikelnummer", "artikelnummer", "teilnummer", "nummer");
        alias(aliases, "position", "pos", "pos.", "position", "posnr", "posnr.", "positionsnummer");
        alias(aliases, "zeichnungsnummer", "zeichnung", "zeichnungsnummer", "zeichnungsnr", "zeichnungsnr.", "drawingnumber");
        alias(aliases, "zeichnungsrevision", "revision", "zeichnungsrevision", "rev");
        alias(aliases, "bezeichnung", "bezeichnung", "beschreibung", "name", "description");
        alias(aliases, "benennung", "benennung");
        alias(aliases, "werkstoff", "werkstoff", "material", "güte", "guete");
        alias(aliases, "abmessung", "abmessung", "profil", "dimension");
        alias(aliases, "menge", "menge", "quantity", "qty");
        alias(aliases, "einheit", "einheit", "unit");
        alias(aliases, "stueckzahl", "stückzahl", "stueckzahl", "anzahl", "pieces", "stk", "stk.");
        alias(aliases, "einzelLaengeMm", "einzellängemm", "einzellaengemm", "längemm", "laengemm", "länge(mm)",
                "laenge(mm)", "länge[mm]", "laenge[mm]", "einzellänge(mm)", "einzellaenge(mm)");
        alias(aliases, "winkelLinks", "winkellinks", "leftangle");
        alias(aliases, "winkelRechts", "winkelrechts", "rightangle");
        alias(aliases, "anschnittSteg", "anschnitt(steg)", "anschnittsteg");
        alias(aliases, "anschnittFlansch", "anschnitt(flansch)", "anschnittflansch");
        alias(aliases, "gewichtKg", "gew.(kg)", "gew(kg)", "gewicht(kg)", "stückgewicht(kg)", "stueckgewicht(kg)");
        alias(aliases, "oberflaeche", "beschichtung", "oberfläche", "oberflaeche");
        return Map.copyOf(aliases);
    }
    private static void alias(Map<String, String> aliases, String field, String... headers) {
        for (String header : headers) aliases.put(header, field);
    }
    private static String normalizeHeader(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).replaceAll("\\s++", "");
    }
    /** Überschriften und Kopfblock: Formeln werden nicht ausgewertet, sondern als leer behandelt. */
    private static String headerText(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.FORMULA) return "";
        String text = cellText(cell, formatter);
        return text.length() > 255 ? "" : text;
    }
    private static boolean blankCell(Row row, Integer index) {
        if (index == null) return true;
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) return true;
        return cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank();
    }
    /** Anschnittzellen tragen die Lage des Winkels über führende/folgende Leerzeichen – daher ungetrimmt. */
    private static String untrimmedValue(Row row, Map<String, Integer> map, String field, DataFormatter formatter) {
        Integer index = map.get(field);
        if (index == null) return null;
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() != CellType.STRING) return nullIfBlank(cellText(cell, formatter));
        String text = cell.getStringCellValue();
        if (text.length() > MAX_CELL_TEXT) throw new IllegalArgumentException("Ein Tabellenwert ist zu lang.");
        return nullIfBlank(text);
    }
    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
    private static String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String nullIfBlank(String value) { return value == null || value.isBlank() ? null : value; }
    private static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest(bytes)); }
    private static String sha256(String value) { return HexFormat.of().formatHex(digest(value.getBytes(StandardCharsets.UTF_8))); }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private record Parsed(List<ParsedRow> rows, boolean katalogAbgleich) {}
    private record Anschnitt(String links, String rechts) {
        boolean vorhanden() { return links != null || rechts != null; }
    }
    private record ParsedRow(int rowNumber, String raw, PositionSnapshot snapshot, List<String> hints,
            List<EinkaufDateiService.ImportBildDto> images) {}
    private record PendingPreview(ParsedRow row, List<Long> candidates, List<String> hints) {}
    private record StoredTransferResult(String idempotenzKey, String payloadHash, String resultJson) {}
    public record ImportBildRessource(Resource resource, String mimeTyp) {}
    public record SpaltenMapping(Map<String, Integer> spalten) {}
}
