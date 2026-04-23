package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.ConfirmGruppeDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.ConfirmRequestDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.ConfirmResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.PreviewResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.ProfilGruppeDto;
import org.example.kalkulationsprogramm.dto.Bestellung.HicadImportDtos.SaegelisteZeileDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestriert den HiCAD-Sägelisten-Import:
 *   1) parse()   → liest Excel, matcht Artikel/Projekt, liefert Preview.
 *   2) confirm() → legt Bestellpositionen an (1:1 oder aggregiert je Gruppe).
 * Der Service ist zustandslos — Preview-Daten reicht das Frontend beim Confirm zurück.
 */
@Service
@AllArgsConstructor
public class HicadImportService {

    private static final Logger log = LoggerFactory.getLogger(HicadImportService.class);

    /** Profil-Präfixe, die wir per Default als „selbst schneiden" vorschlagen (Stangenware, i.d.R. aus Rohr/Winkel). */
    private static final List<String> DEFAULT_AGGREGIEREN_PREFIXES = List.of("FRR", "FRQ", "RR", "RQ", "L ");

    /** Fallback-Stangenlänge in Metern, wenn weder Artikel.verpackungseinheit noch Confirm-Override gesetzt. */
    private static final long FALLBACK_STANGEN_M = 6L;

    private final HicadSaegelisteParser parser;
    private final ArtikelRepository artikelRepository;
    private final ProjektRepository projektRepository;
    private final KategorieRepository kategorieRepository;
    private final ArtikelInProjektRepository artikelInProjektRepository;

    // ========================= PREVIEW =========================

    @Transactional(readOnly = true)
    public PreviewResponseDto preview(MultipartFile file) throws IOException {
        HicadSaegelisteParser.ParseResult parsed = parser.parse(file.getInputStream());
        PreviewResponseDto preview = parsed.header();
        List<SaegelisteZeileDto> zeilen = parsed.zeilen();

        // Projekt per Auftragsnummer erkennen (falls vorhanden)
        if (preview.getAuftragsnummer() != null && !preview.getAuftragsnummer().isBlank()) {
            projektRepository.findByAuftragsnummer(preview.getAuftragsnummer().trim())
                    .ifPresent(p -> {
                        preview.setErkannteProjektId(p.getId());
                        preview.setErkannteProjektName(p.getBauvorhaben());
                    });
        }

        preview.setGruppen(gruppiere(zeilen));
        return preview;
    }

    /**
     * Gruppiert Zeilen nach (Bezeichnung + Werkstoff), matcht Artikel und füllt die Gruppen-Felder.
     */
    private List<ProfilGruppeDto> gruppiere(List<SaegelisteZeileDto> zeilen) {
        Map<String, ProfilGruppeDto> map = new LinkedHashMap<>();

        for (SaegelisteZeileDto z : zeilen) {
            String bez = z.getBezeichnung() != null ? z.getBezeichnung().trim() : "";
            String ws = z.getWerkstoff() != null ? z.getWerkstoff().trim() : "";
            String key = bez.toLowerCase(Locale.GERMANY) + "||" + ws.toLowerCase(Locale.GERMANY);

            ProfilGruppeDto g = map.computeIfAbsent(key, k -> {
                ProfilGruppeDto neu = new ProfilGruppeDto();
                neu.setGroupKey(k);
                neu.setBezeichnung(bez);
                neu.setWerkstoff(ws);
                neu.setZeilen(new ArrayList<>());
                neu.setDefaultAggregieren(istDefaultAggregieren(bez));

                // Artikel-Match: hicadName oder produktname
                Optional<Artikel> match = artikelRepository.findByHicadNameOrProduktname(bez).stream().findFirst();
                match.ifPresent(a -> {
                    neu.setArtikelId(a.getId());
                    neu.setArtikelProduktname(a.getProduktname());
                    neu.setVerpackungseinheitM(a.getVerpackungseinheit());
                });
                return neu;
            });
            g.getZeilen().add(z);
        }

        // Summen + Stangen-Berechnung
        for (ProfilGruppeDto g : map.values()) {
            berechneSummen(g);
        }
        return new ArrayList<>(map.values());
    }

    private boolean istDefaultAggregieren(String bezeichnung) {
        if (bezeichnung == null) return false;
        String b = bezeichnung.toUpperCase(Locale.GERMANY);
        return DEFAULT_AGGREGIEREN_PREFIXES.stream().anyMatch(b::startsWith);
    }

    private void berechneSummen(ProfilGruppeDto g) {
        BigDecimal summeMeter = BigDecimal.ZERO;
        int summeStueck = 0;
        for (SaegelisteZeileDto z : g.getZeilen()) {
            int anzahl = z.getAnzahl() != null ? z.getAnzahl() : 0;
            int laenge = z.getLaengeMm() != null ? z.getLaengeMm() : 0;
            summeStueck += anzahl;
            summeMeter = summeMeter.add(
                    BigDecimal.valueOf((long) anzahl * laenge)
                            .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP));
        }
        g.setSummeMeter(summeMeter.setScale(2, RoundingMode.HALF_UP));
        g.setSummeStueck(summeStueck);

        long stangeM = g.getVerpackungseinheitM() != null && g.getVerpackungseinheitM() > 0
                ? g.getVerpackungseinheitM()
                : FALLBACK_STANGEN_M;
        g.setBerechneteStaebe(optimiereStangen(g.getZeilen(), stangeM).anzahlStangen());
    }

    /**
     * Ergebnis der Zuschnitt-Optimierung.
     */
    private record StangenPlan(int anzahlStangen, long belegtMm, long verschnittMm, int ueberlange) {}

    /**
     * Zuschnitt-Optimierung per First-Fit-Decreasing (FFD).
     * Liefert minimale Stangenzahl, die zur Produktion aller Zuschnitte nötig ist — und den Verschnitt.
     * Zuschnitte, die länger sind als die Stange selbst, zählen als eigene (zu kurze) Stange.
     */
    private StangenPlan optimiereStangen(List<SaegelisteZeileDto> zeilen, long stangenlaengeM) {
        long stangeMm = stangenlaengeM * 1000L;
        List<Integer> alle = new ArrayList<>();
        for (SaegelisteZeileDto z : zeilen) {
            int anzahl = z.getAnzahl() != null ? z.getAnzahl() : 0;
            int laenge = z.getLaengeMm() != null ? z.getLaengeMm() : 0;
            if (anzahl <= 0 || laenge <= 0) continue;
            for (int i = 0; i < anzahl; i++) alle.add(laenge);
        }
        alle.sort(Comparator.reverseOrder());

        List<Long> belegung = new ArrayList<>();
        int ueberlange = 0;
        long belegtMm = 0;
        for (int len : alle) {
            belegtMm += len;
            if (len > stangeMm) {
                ueberlange++;
                belegung.add((long) len);
                continue;
            }
            int idx = -1;
            for (int i = 0; i < belegung.size(); i++) {
                if (belegung.get(i) + len <= stangeMm) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                belegung.set(idx, belegung.get(idx) + len);
            } else {
                belegung.add((long) len);
            }
        }

        long kapazitaetMm = (long) belegung.size() * stangeMm;
        long verschnittMm = Math.max(0, kapazitaetMm - belegtMm);
        return new StangenPlan(belegung.size(), belegtMm, verschnittMm, ueberlange);
    }

    // ========================= CONFIRM =========================

    @Transactional
    public ConfirmResponseDto confirm(ConfirmRequestDto req) {
        if (req.getGruppen() == null || req.getGruppen().isEmpty()) {
            throw new IllegalArgumentException("Keine Gruppen übergeben.");
        }
        if (req.getPreview() == null || req.getPreview().isEmpty()) {
            throw new IllegalArgumentException("Preview-Daten fehlen — Import abgebrochen.");
        }

        Map<String, ProfilGruppeDto> previewByKey = new LinkedHashMap<>();
        for (ProfilGruppeDto g : req.getPreview()) {
            previewByKey.put(g.getGroupKey(), g);
        }

        List<Long> angelegteIds = new ArrayList<>();
        for (ConfirmGruppeDto c : req.getGruppen()) {
            ProfilGruppeDto preview = previewByKey.get(c.getGroupKey());
            if (preview == null) {
                log.warn("Confirm-Gruppe ohne Preview-Match: {}", c.getGroupKey());
                continue;
            }
            Long projektId = c.getProjektId() != null ? c.getProjektId() : req.getProjektId();
            if (projektId == null) {
                throw new IllegalArgumentException(
                        "Kein Projekt gewählt für Gruppe '" + preview.getBezeichnung() + "'");
            }

            if (c.isAggregieren()) {
                Long id = legeAggregiertAn(preview, c, projektId, req.getKommentarPrefix());
                if (id != null) angelegteIds.add(id);
            } else {
                angelegteIds.addAll(legeFixzuschnitteAn(preview, c, projektId, req.getKommentarPrefix()));
            }
        }

        ConfirmResponseDto resp = new ConfirmResponseDto();
        resp.setAngelegtePositionen(angelegteIds.size());
        resp.setPositionIds(angelegteIds);
        return resp;
    }

    /**
     * 1:1-Import: pro Sägeliste-Zeile eine Bestellposition.
     */
    private List<Long> legeFixzuschnitteAn(ProfilGruppeDto preview, ConfirmGruppeDto conf,
                                           Long projektId, String kommentarPrefix) {
        List<Long> ids = new ArrayList<>();
        Projekt projekt = projektRepository.getReferenceById(projektId);
        for (SaegelisteZeileDto z : preview.getZeilen()) {
            ArtikelInProjekt aip = baseEintrag(projekt, conf, preview);
            aip.setStueckzahl(z.getAnzahl());
            // Fixzuschnitt: Länge in mm gehört in fixmassMm — nicht in Kommentar.
            aip.setFixmassMm(z.getLaengeMm());
            aip.setAnschnittWinkelLinks(parseWinkel(z.getAnschnittSteg()));
            aip.setAnschnittWinkelRechts(parseWinkel(z.getAnschnittFlansch()));
            aip.setKilogramm(z.getGesamtGewichtKg());
            aip.setKommentar(kommentar(kommentarPrefix,
                    "Pos " + (z.getPosNr() != null ? z.getPosNr() : "-")));

            if (aip.getArtikel() == null) {
                aip.setFreitextProduktname(preview.getBezeichnung());
                aip.setFreitextProdukttext(preview.getWerkstoff());
                aip.setFreitextMenge(z.getAnzahl() != null ? BigDecimal.valueOf(z.getAnzahl()) : null);
                aip.setFreitextEinheit("Stück");
            }
            ids.add(artikelInProjektRepository.save(aip).getId());
        }
        return ids;
    }

    /**
     * Aggregiert: eine Bestellposition mit Anzahl = berechnete Stäbe.
     */
    private Long legeAggregiertAn(ProfilGruppeDto preview, ConfirmGruppeDto conf,
                                  Long projektId, String kommentarPrefix) {
        long stangenlaengeM = conf.getStangenlaengeM() != null && conf.getStangenlaengeM() > 0
                ? conf.getStangenlaengeM()
                : (preview.getVerpackungseinheitM() != null && preview.getVerpackungseinheitM() > 0
                        ? preview.getVerpackungseinheitM()
                        : FALLBACK_STANGEN_M);

        BigDecimal summeMeter = preview.getSummeMeter() != null ? preview.getSummeMeter() : BigDecimal.ZERO;
        if (summeMeter.signum() <= 0) {
            log.warn("Aggregation ohne Meter-Summe übersprungen: {}", preview.getBezeichnung());
            return null;
        }

        // Zuschnitt-Optimierung (FFD): minimale Stangenzahl + realer Verschnitt
        StangenPlan plan = optimiereStangen(preview.getZeilen(), stangenlaengeM);
        int staebe = Math.max(1, plan.anzahlStangen());

        Projekt projekt = projektRepository.getReferenceById(projektId);
        ArtikelInProjekt aip = baseEintrag(projekt, conf, preview);
        aip.setStueckzahl(staebe);
        // fixmassMm nur bei Fixzuschnitt setzen — bei Stangenware null lassen,
        // damit DB-Queries "fixmass_mm IS NOT NULL" klar Fixzuschnitte identifizieren.
        aip.setFixmassMm(null);
        // Bei Stangenware leiten wir kg/m aus den Schwester-Zuschnitten ab und
        // multiplizieren mit Stangenlänge × Anzahl Stäbe — sonst stünde die Zeile
        // ohne Gewicht in der Bedarfsliste.
        BigDecimal kgProMeter = berechneKgProMeter(preview);
        BigDecimal stangenKilogramm = kgProMeter != null
                ? kgProMeter
                        .multiply(BigDecimal.valueOf(stangenlaengeM))
                        .multiply(BigDecimal.valueOf(staebe))
                        .setScale(2, RoundingMode.HALF_UP)
                : null;
        aip.setKilogramm(stangenKilogramm);

        int anzahlZuschnitte = preview.getSummeStueck() != null ? preview.getSummeStueck() : 0;
        double verschnittM = plan.verschnittMm() / 1000.0;
        String hinweis = String.format(Locale.GERMANY,
                "Stangenware · %d Stk à %d m (%.2f m benötigt, %d Zuschnitte, %.2f m Verschnitt)",
                staebe, stangenlaengeM, summeMeter.doubleValue(), anzahlZuschnitte, verschnittM);
        if (plan.ueberlange() > 0) {
            hinweis += String.format(Locale.GERMANY,
                    " · ⚠ %d Zuschnitt(e) länger als Stange", plan.ueberlange());
        }
        aip.setKommentar(kommentar(kommentarPrefix, hinweis));

        if (aip.getArtikel() == null) {
            aip.setFreitextProduktname(preview.getBezeichnung());
            aip.setFreitextProdukttext(preview.getWerkstoff());
            aip.setFreitextMenge(BigDecimal.valueOf(stangenlaengeM));
            aip.setFreitextEinheit("m");
        }
        return artikelInProjektRepository.save(aip).getId();
    }

    /**
     * Leitet das Profil-Gewicht je Meter aus den HiCAD-Zuschnitten der Gruppe ab.
     * Summiert dafür Gesamtgewicht und Gesamtlänge aller Zeilen mit gültigen Werten
     * und teilt kg / m. Liefert null, wenn die Datenlage nicht reicht.
     */
    private BigDecimal berechneKgProMeter(ProfilGruppeDto preview) {
        if (preview.getZeilen() == null) return null;
        BigDecimal sumKg = BigDecimal.ZERO;
        BigDecimal sumM = BigDecimal.ZERO;
        for (SaegelisteZeileDto z : preview.getZeilen()) {
            if (z.getGesamtGewichtKg() == null || z.getLaengeMm() == null || z.getAnzahl() == null) continue;
            if (z.getAnzahl() <= 0 || z.getLaengeMm() <= 0) continue;
            sumKg = sumKg.add(z.getGesamtGewichtKg());
            BigDecimal meter = BigDecimal.valueOf(z.getLaengeMm())
                    .multiply(BigDecimal.valueOf(z.getAnzahl()))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
            sumM = sumM.add(meter);
        }
        return sumM.signum() > 0
                ? sumKg.divide(sumM, 6, RoundingMode.HALF_UP)
                : null;
    }

    private ArtikelInProjekt baseEintrag(Projekt projekt, ConfirmGruppeDto conf, ProfilGruppeDto preview) {
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setProjekt(projekt);
        aip.setHinzugefuegtAm(LocalDate.now());

        // lieferantId aus dem Confirm-Request wird nach A2 ignoriert — der
        // Lieferant wird erst beim Anlegen der Bestellung (Preisanfrage-Vergabe
        // oder manueller Export) auf der Bestellung selbst gesetzt.
        if (conf.getArtikelId() != null) {
            aip.setArtikel(artikelRepository.getReferenceById(conf.getArtikelId()));
        } else if (preview.getArtikelId() != null) {
            aip.setArtikel(artikelRepository.getReferenceById(preview.getArtikelId()));
        }
        if (conf.getKategorieId() != null) {
            aip.setKategorie(kategorieRepository.getReferenceById(conf.getKategorieId()));
        }
        return aip;
    }

    private String kommentar(String prefix, String body) {
        String p = prefix != null && !prefix.isBlank() ? prefix.trim() + " · " : "";
        return p + body;
    }

    /**
     * Parst einen Winkel aus der Saegeliste (Excel liefert Strings, teils mit Komma).
     * Leere/nicht numerische Eingabe -> null.
     */
    private Double parseWinkel(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
