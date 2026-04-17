package org.example.kalkulationsprogramm.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.PreviewResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.SaegelisteZeileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser für HiCAD-Sägelisten (Excel-Export "HiCAD_Stahlbau", Mengenliste).
 * Liest das Sheet "Sägeliste" (oder das erste Sheet als Fallback) und extrahiert
 * Header-Metadaten + Positionsliste.
 */
@Component
public class HicadSaegelisteParser {

    private static final Logger log = LoggerFactory.getLogger(HicadSaegelisteParser.class);

    private static final String SHEET_NAME = "Sägeliste";

    /**
     * Bekannte Spaltenüberschriften → logischer Feldname.
     * Kleingeschrieben + ohne Sonderzeichen für den Vergleich.
     */
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("pos", "pos"),
            Map.entry("pos.", "pos"),
            Map.entry("position", "pos"),
            Map.entry("anzahl", "anzahl"),
            Map.entry("menge", "anzahl"),
            Map.entry("stk", "anzahl"),
            Map.entry("bezeichnung", "bezeichnung"),
            Map.entry("profil", "bezeichnung"),
            Map.entry("länge", "laenge"),
            Map.entry("laenge", "laenge"),
            Map.entry("länge(mm)", "laenge"),
            Map.entry("laenge(mm)", "laenge"),
            Map.entry("länge[mm]", "laenge"),
            Map.entry("anschnitt(steg)", "anschnittSteg"),
            Map.entry("anschnittsteg", "anschnittSteg"),
            Map.entry("anschnitt(flansch)", "anschnittFlansch"),
            Map.entry("anschnittflansch", "anschnittFlansch"),
            Map.entry("material", "material"),
            Map.entry("werkstoff", "material"),
            Map.entry("gew.(kg)", "gewichtProStueck"),
            Map.entry("gew(kg)", "gewichtProStueck"),
            Map.entry("gewicht(kg)", "gewichtProStueck"),
            Map.entry("gew.", "gewichtProStueck"),
            Map.entry("ges.gew.", "gesamtGewicht"),
            Map.entry("gesgew", "gesamtGewicht"),
            Map.entry("gesamtgewicht", "gesamtGewicht"),
            Map.entry("gesamtgew.", "gesamtGewicht"));

    /**
     * Parsed eine HiCAD-Sägeliste-Datei und füllt den Header + die rohen Zeilen.
     * Gruppierung/Matching geschieht im Service, nicht hier.
     */
    public ParseResult parse(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = findSaegelisteSheet(wb);
            if (sheet == null) {
                throw new IllegalArgumentException("Kein Arbeitsblatt 'Sägeliste' gefunden.");
            }

            PreviewResponseDto preview = new PreviewResponseDto();
            List<SaegelisteZeileDto> zeilen = new ArrayList<>();

            int headerRowIdx = findHeaderRow(sheet);
            if (headerRowIdx < 0) {
                throw new IllegalArgumentException(
                        "Tabellen-Header (Pos/Anzahl/Bezeichnung/...) nicht gefunden.");
            }

            // Header-Block: alle Zeilen vor der Tabelle nach Key: Value-Paaren durchsuchen
            extractHeaderBlock(sheet, headerRowIdx, preview);

            // Spalten-Mapping
            Map<String, Integer> colIdx = mapColumns(sheet.getRow(headerRowIdx));

            DataFormatter df = new DataFormatter(Locale.GERMANY);
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                SaegelisteZeileDto z = parseZeile(row, colIdx, df);
                if (z != null) {
                    zeilen.add(z);
                }
            }

            return new ParseResult(preview, zeilen);
        }
    }

    private Sheet findSaegelisteSheet(Workbook wb) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (SHEET_NAME.equalsIgnoreCase(wb.getSheetName(i).trim())) {
                return wb.getSheetAt(i);
            }
        }
        log.warn("Sheet '{}' nicht gefunden — verwende erstes Sheet als Fallback", SHEET_NAME);
        return wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
    }

    private int findHeaderRow(Sheet sheet) {
        DataFormatter df = new DataFormatter(Locale.GERMANY);
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 40); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            boolean hasPos = false;
            boolean hasAnzahl = false;
            boolean hasBezeichnung = false;
            for (Cell c : row) {
                String norm = normalize(df.formatCellValue(c));
                if ("pos".equals(HEADER_ALIASES.get(norm))) hasPos = true;
                if ("anzahl".equals(HEADER_ALIASES.get(norm))) hasAnzahl = true;
                if ("bezeichnung".equals(HEADER_ALIASES.get(norm))) hasBezeichnung = true;
            }
            if (hasPos && hasAnzahl && hasBezeichnung) {
                return r;
            }
        }
        return -1;
    }

    private Map<String, Integer> mapColumns(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        DataFormatter df = new DataFormatter(Locale.GERMANY);
        for (Cell c : headerRow) {
            String logical = HEADER_ALIASES.get(normalize(df.formatCellValue(c)));
            if (logical != null && !map.containsKey(logical)) {
                map.put(logical, c.getColumnIndex());
            }
        }
        return map;
    }

    private void extractHeaderBlock(Sheet sheet, int tableHeaderRow, PreviewResponseDto preview) {
        DataFormatter df = new DataFormatter(Locale.GERMANY);
        for (int r = 0; r < tableHeaderRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int ci = 0; ci < row.getLastCellNum(); ci++) {
                Cell keyCell = row.getCell(ci);
                if (keyCell == null) continue;
                String key = normalize(df.formatCellValue(keyCell));
                if (key.isBlank()) continue;

                String value = findNextValue(row, ci, df);
                if (value == null || value.isBlank()) continue;

                switch (key) {
                    case "zeichnungsnr.", "zeichnungsnr", "zeichnungsnummer" ->
                            preview.setZeichnungsnr(value);
                    case "auftragsnr.", "auftragsnr", "auftragsnummer" ->
                            preview.setAuftragsnummer(value);
                    case "auftragstext" -> preview.setAuftragstext(value);
                    case "kunde" -> preview.setKunde(value);
                    case "ersteller" -> preview.setErsteller(value);
                    case "erstelltam", "erstellt" -> preview.setErstelltAm(value);
                    default -> { /* ignorieren */ }
                }
            }
        }
    }

    private String findNextValue(Row row, int keyCol, DataFormatter df) {
        for (int ci = keyCol + 1; ci < row.getLastCellNum() && ci <= keyCol + 6; ci++) {
            Cell c = row.getCell(ci);
            if (c == null) continue;
            String v = df.formatCellValue(c).trim();
            if (!v.isBlank()) return v;
        }
        return null;
    }

    private SaegelisteZeileDto parseZeile(Row row, Map<String, Integer> cols, DataFormatter df) {
        Integer pos = readInt(row, cols.get("pos"));
        Integer anzahl = readInt(row, cols.get("anzahl"));
        String bezeichnung = readString(row, cols.get("bezeichnung"), df);
        Integer laenge = readInt(row, cols.get("laenge"));

        // Zeile ohne Kernfelder → ignorieren (leer/Footer)
        if (bezeichnung == null || bezeichnung.isBlank() || anzahl == null || anzahl <= 0) {
            return null;
        }

        SaegelisteZeileDto z = new SaegelisteZeileDto();
        z.setPosNr(pos);
        z.setAnzahl(anzahl);
        z.setBezeichnung(bezeichnung.trim());
        z.setLaengeMm(laenge);
        z.setWerkstoff(readString(row, cols.get("material"), df));
        z.setAnschnittSteg(readString(row, cols.get("anschnittSteg"), df));
        z.setAnschnittFlansch(readString(row, cols.get("anschnittFlansch"), df));
        z.setGewichtProStueckKg(readBigDecimal(row, cols.get("gewichtProStueck")));
        z.setGesamtGewichtKg(readBigDecimal(row, cols.get("gesamtGewicht")));
        return z;
    }

    private Integer readInt(Row row, Integer colIdx) {
        if (colIdx == null) return null;
        Cell c = row.getCell(colIdx);
        if (c == null) return null;
        try {
            return switch (c.getCellType()) {
                case NUMERIC -> (int) Math.round(c.getNumericCellValue());
                case STRING -> {
                    String s = c.getStringCellValue().trim().replace(".", "").replace(",", ".");
                    yield s.isEmpty() ? null : (int) Math.round(Double.parseDouble(s));
                }
                case FORMULA -> (int) Math.round(c.getNumericCellValue());
                default -> null;
            };
        } catch (Exception ignore) {
            return null;
        }
    }

    private String readString(Row row, Integer colIdx, DataFormatter df) {
        if (colIdx == null) return null;
        Cell c = row.getCell(colIdx);
        if (c == null) return null;
        String v = df.formatCellValue(c).trim();
        return v.isBlank() ? null : v;
    }

    private BigDecimal readBigDecimal(Row row, Integer colIdx) {
        if (colIdx == null) return null;
        Cell c = row.getCell(colIdx);
        if (c == null) return null;
        try {
            return switch (c.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(c.getNumericCellValue())
                        .setScale(2, RoundingMode.HALF_UP);
                case STRING -> {
                    String s = c.getStringCellValue().trim().replace(".", "").replace(",", ".");
                    yield s.isEmpty() ? null : new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
                }
                case FORMULA -> BigDecimal.valueOf(c.getNumericCellValue())
                        .setScale(2, RoundingMode.HALF_UP);
                default -> null;
            };
        } catch (Exception ignore) {
            return null;
        }
    }

    /** Kleinbuchstaben, alle Whitespace entfernt. */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.GERMANY).replaceAll("\\s+", "").trim();
    }

    public record ParseResult(PreviewResponseDto header, List<SaegelisteZeileDto> zeilen) {}
}
