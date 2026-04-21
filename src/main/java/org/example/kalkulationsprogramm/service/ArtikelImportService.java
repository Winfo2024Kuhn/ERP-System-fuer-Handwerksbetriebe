package org.example.kalkulationsprogramm.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ArtikelImportService {

    private final ArtikelRepository artikelRepository;
    private final LieferantenRepository lieferantenRepository;
    private final org.example.kalkulationsprogramm.repository.KategorieRepository kategorieRepository;
    private final WerkstoffRepository werkstoffRepository;
    private final ArtikelPreisHookService preisHookService;

    private static final Logger log = LoggerFactory.getLogger(ArtikelImportService.class);

    private static final Map<String, String> DEFAULT_HEADERS = Map.ofEntries(
            Map.entry("externeArtikelnummer", "materialnummer"),
            Map.entry("preis", "nettopreis"),
            Map.entry("produktlinie", "produktlinie"),
            Map.entry("produktname", "produktname"),
            Map.entry("werkstoff", "werkstoff"),
            Map.entry("produkttext", "produkttext"),
            Map.entry("verpackungseinheit", "packgroesse"),
            Map.entry("preiseinheit", "preiseinheit"),
            Map.entry("waehrung", "waehrung"));

    @Transactional(readOnly = true)
    public java.util.List<String> readHeaders(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            java.nio.charset.Charset detectedCharset = detectCharset(bytes);
            String content = new String(bytes, detectedCharset);
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                String line = reader.readLine();
                if (line == null) {
                    return java.util.Collections.emptyList();
                }
                // Detect delimiter
                String delimiter = line.contains(";") ? ";" : ",";
                return java.util.Arrays.stream(line.split(delimiter))
                        .map(String::trim)
                        .filter(h -> !h.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }
        } catch (IOException e) {
            throw new RuntimeException("Konnte Header nicht lesen", e);
        }
    }

    @Transactional(readOnly = true)
    public org.example.kalkulationsprogramm.dto.ImportAnalysisResult analyzeImport(MultipartFile file,
            String lieferantenName, Map<String, String> spaltenZuordnung) {
        org.example.kalkulationsprogramm.dto.ImportAnalysisResult result = new org.example.kalkulationsprogramm.dto.ImportAnalysisResult();
        result.setNewArticleExamples(new java.util.ArrayList<>());

        try {
            byte[] bytes = file.getBytes();
            java.nio.charset.Charset detectedCharset = detectCharset(bytes);
            String content = new String(bytes, detectedCharset);
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    return result;
                }
                String[] headers = headerLine.split(";");
                Map<String, Integer> headerIndex = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String norm = headers[i].trim().toLowerCase();
                    headerIndex.put(norm, i);
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(";");
                    String externeNr = getValue("externeArtikelnummer", values, headerIndex, spaltenZuordnung);
                    String preisStr = getValue("preis", values, headerIndex, spaltenZuordnung);
                    if (externeNr == null || preisStr == null) {
                        continue;
                    }

                    boolean exists = artikelRepository.findByExterneArtikelnummer(externeNr).isPresent();
                    if (exists) {
                        result.setExistingCount(result.getExistingCount() + 1);
                    } else {
                        result.setNewCount(result.getNewCount() + 1);
                        if (result.getNewArticleExamples().size() < 5) {
                            String name = getValue("produktname", values, headerIndex, spaltenZuordnung);
                            result.getNewArticleExamples().add(name != null ? name : externeNr);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV konnte nicht analysiert werden", e);
        }
        return result;
    }

    @Transactional
    public void importiereCsv(MultipartFile file, String lieferantenName, Map<String, String> spaltenZuordnung,
            Long defaultKategorieId) {
        // Ursprünglichen Lieferantennamen unverändert lassen (Umlaute bleiben erhalten)
        try {
            byte[] bytes = file.getBytes();
            java.nio.charset.Charset detectedCharset = detectCharset(bytes);
            String content = new String(bytes, detectedCharset);
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    return;
                }
                String[] headers = headerLine.split(";");
                Map<String, Integer> headerIndex = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String norm = headers[i].trim().toLowerCase();
                    headerIndex.put(norm, i);
                }

                String finalLieferantenName = lieferantenName;
                Lieferanten lieferant = lieferantenRepository.findByLieferantenname(lieferantenName)
                        .orElseGet(() -> {
                            Lieferanten neu = new Lieferanten();
                            neu.setLieferantenname(finalLieferantenName);
                            neu.setIstAktiv(true);
                            neu.setStartZusammenarbeit(new Date());
                            return lieferantenRepository.save(neu);
                        });

                Kategorie defaultKategorie = null;
                if (defaultKategorieId != null) {
                    defaultKategorie = kategorieRepository.findById(Math.toIntExact(defaultKategorieId)).orElse(null);
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(";");
                    String externeNr = getValue("externeArtikelnummer", values, headerIndex, spaltenZuordnung);
                    String preisStr = getValue("preis", values, headerIndex, spaltenZuordnung);
                    String einheitStr = getValue("preiseinheit", values, headerIndex, spaltenZuordnung);
                    if (externeNr == null || preisStr == null) {
                        continue;
                    }

                    boolean isNew = false;
                    Artikel artikel = artikelRepository.findByExterneArtikelnummer(externeNr).orElse(null);
                    if (artikel == null) {
                        artikel = new Artikel();
                        isNew = true;
                    }

                    setIfPresent(artikel::setProduktlinie, "produktlinie", values, headerIndex, spaltenZuordnung);
                    setIfPresent(artikel::setProduktname, "produktname", values, headerIndex, spaltenZuordnung);
                        String werkstoffName = getValue("werkstoff", values, headerIndex, spaltenZuordnung);
                        if (werkstoffName != null) {
                        artikel.setWerkstoff(resolveWerkstoff(werkstoffName));
                        }
                    setIfPresent(artikel::setProdukttext, "produkttext", values, headerIndex, spaltenZuordnung);
                    setIfPresentLong(artikel::setVerpackungseinheit, "verpackungseinheit", values, headerIndex,
                            spaltenZuordnung);
                    setIfPresent(artikel::setPreiseinheit, "preiseinheit", values, headerIndex, spaltenZuordnung);

                    if (isNew && defaultKategorie != null) {
                        artikel.setKategorie(defaultKategorie);
                    }

                    BigDecimal preis = parseBigDecimal(preisStr);
                    if (preis == null) {
                        continue;
                    }

                    BigDecimal einheit = parseBigDecimal(einheitStr);
                    if (einheit != null && einheit.compareTo(BigDecimal.ZERO) > 0) {
                        preis = preis.divide(einheit, 4, java.math.RoundingMode.HALF_UP);
                    }

                    preis = normalizePreis(preis);
                    if (preis == null) {
                        log.warn("Preis fuer Artikel {} liegt außerhalb des erwarteten Bereichs", externeNr);
                        continue;
                    }

                    final Artikel currentArtikel = artikel;
                    Optional<LieferantenArtikelPreise> existingLapOptional = currentArtikel.getArtikelpreis().stream()
                            .filter(p -> p.getLieferant() != null && p.getLieferant().getId().equals(lieferant.getId()))
                            .findFirst();

                    LieferantenArtikelPreise lap = existingLapOptional.orElseGet(() -> {
                        LieferantenArtikelPreise neu = new LieferantenArtikelPreise();
                        neu.setArtikel(currentArtikel);
                        neu.setLieferant(lieferant);
                        currentArtikel.getArtikelpreis().add(neu);
                        return neu;
                    });

                    lap.setExterneArtikelnummer(externeNr);
                    lap.setPreis(preis);
                    lap.setPreisAenderungsdatum(new Date());

                    artikelRepository.save(currentArtikel);
                    preisHookService.registriere(currentArtikel, lieferant, preis,
                            currentArtikel.getVerrechnungseinheit(),
                            PreisQuelle.KATALOG, externeNr);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV konnte nicht importiert werden", e);
        }
    }

    private String getValue(String feld, String[] values, Map<String, Integer> headerIndex,
            Map<String, String> mapping) {
        String header = mapping.get(feld);
        Integer idx = null;
        if (header != null) {
            idx = headerIndex.get(header.toLowerCase());
        } else {
            String def = DEFAULT_HEADERS.get(feld);
            if (def != null) {
                idx = headerIndex.get(def);
            }
        }
        if (idx == null || idx >= values.length) {
            return null;
        }
        String val = values[idx].trim();
        return val.isEmpty() ? null : val;
    }

    private void setIfPresent(Consumer<String> setter, String feld, String[] values, Map<String, Integer> headerIndex,
            Map<String, String> mapping) {
        String val = getValue(feld, values, headerIndex, mapping);
        if (val != null) {
            setter.accept(val);
        }
    }

    private void setIfPresentLong(Consumer<Long> setter, String feld, String[] values,
            Map<String, Integer> headerIndex, Map<String, String> mapping) {
        String val = getValue(feld, values, headerIndex, mapping);
        if (val != null) {
            String digits = val.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    setter.accept(Long.parseLong(digits));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void setIfPresentCurrency(Consumer<String> setter, String feld, String[] values,
            Map<String, Integer> headerIndex, Map<String, String> mapping) {
        String val = getValue(feld, values, headerIndex, mapping);
        if (val != null) {
            if ("€".equals(val)) {
                val = "EUR";
            }
            setter.accept(val);
        }
    }

    private Werkstoff resolveWerkstoff(String werkstoffName) {
        String normalized = werkstoffName == null ? null : werkstoffName.trim();
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        Optional<Werkstoff> existing = werkstoffRepository.findByNameIgnoreCase(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            Werkstoff werkstoff = new Werkstoff();
            werkstoff.setName(normalized);
            return werkstoffRepository.save(werkstoff);
        } catch (DataIntegrityViolationException e) {
            // Concurrent import created the same Werkstoff — re-fetch
            return werkstoffRepository.findByNameIgnoreCase(normalized)
                    .orElseThrow(() -> e);
        }
    }

    private BigDecimal normalizePreis(BigDecimal preis) {
        BigDecimal min = new BigDecimal("0.50");
        BigDecimal max = new BigDecimal("10.00");

        if (preis.compareTo(max) > 0) {
            BigDecimal durchHundert = preis.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
            if (durchHundert.compareTo(min) >= 0 && durchHundert.compareTo(max) <= 0) {
                return durchHundert;
            }
            BigDecimal durchTausend = preis.divide(BigDecimal.valueOf(1000), 4, java.math.RoundingMode.HALF_UP);
            if (durchTausend.compareTo(min) >= 0 && durchTausend.compareTo(max) <= 0) {
                return durchTausend;
            }
            return null;
        }

        if (preis.compareTo(min) < 0) {
            BigDecimal malHundert = preis.multiply(BigDecimal.valueOf(100));
            if (malHundert.compareTo(min) >= 0 && malHundert.compareTo(max) <= 0) {
                return malHundert;
            }
            BigDecimal malTausend = preis.multiply(BigDecimal.valueOf(1000));
            if (malTausend.compareTo(min) >= 0 && malTausend.compareTo(max) <= 0) {
                return malTausend;
            }
            return null;
        }

        return preis;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Erkennt automatisch die Zeichencodierung einer CSV-Datei.
     * Testet mehrere gängige Codierungen und wählt die beste aus.
     */
    private java.nio.charset.Charset detectCharset(byte[] bytes) {
        // Liste der zu testenden Charsets in Prioritätsreihenfolge
        java.util.List<java.nio.charset.Charset> charsetsToTry = java.util.Arrays.asList(
                StandardCharsets.UTF_8,
                java.nio.charset.Charset.forName("Windows-1252"),
                StandardCharsets.ISO_8859_1,
                java.nio.charset.Charset.forName("Windows-1250"));

        // Prüfe auf UTF-8 BOM (Byte Order Mark)
        if (hasBOM(bytes)) {
            log.debug("UTF-8 BOM erkannt");
            return StandardCharsets.UTF_8;
        }

        // Teste jede Codierung und wähle die erste gültige
        for (java.nio.charset.Charset charset : charsetsToTry) {
            if (isValidEncoding(bytes, charset)) {
                log.debug("Codierung erkannt: {}", charset.name());
                return charset;
            }
        }

        // Fallback zu UTF-8
        log.warn("Keine eindeutige Codierung erkannt, verwende UTF-8 als Fallback");
        return StandardCharsets.UTF_8;
    }

    /**
     * Prüft, ob die Datei mit einem UTF-8 BOM (Byte Order Mark) beginnt.
     */
    private boolean hasBOM(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
    }

    /**
     * Validiert, ob eine bestimmte Codierung für die gegebenen Bytes plausibel ist.
     */
    private boolean isValidEncoding(byte[] bytes, java.nio.charset.Charset charset) {
        try {
            String content = new String(bytes, charset);

            // Prüfe auf Replacement-Zeichen (zeigt ungültige Codierung an)
            if (content.contains("\uFFFD")) {
                return false;
            }

            // Prüfe auf zu viele Steuerzeichen (außer Newline, Carriage Return, Tab)
            long controlChars = content.chars()
                    .filter(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
                    .count();

            // Wenn mehr als 1% Steuerzeichen, wahrscheinlich falsche Codierung
            if (controlChars > content.length() * 0.01) {
                return false;
            }

            // Zusätzliche Plausibilitätsprüfung: CSV sollte Semikolons oder Kommas
            // enthalten
            if (!content.contains(";") && !content.contains(",")) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
