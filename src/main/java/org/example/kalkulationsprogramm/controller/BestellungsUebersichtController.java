package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller für die Bestellungs-Übersicht mit Dokumenten-Ketten.
 * Gruppiert Lieferanten-Dokumente nach Status:
 * - Offene Anfragen (nur Anfrage, keine Folgedokumente)
 * - Laufende Bestellungen (AB vorhanden, keine Rechnung)
 * - Abgeschlossen (Rechnung vorhanden, noch nicht zugeordnet)
 * - Zugeordnet (Rechnung vorhanden und Projekten zugeordnet)
 */
@RestController
@RequestMapping("/api/bestellungen-uebersicht")
@RequiredArgsConstructor
public class BestellungsUebersichtController {

    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final ProjektRepository projektRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    private final KostenstelleRepository kostenstelleRepository;
    private final FrontendUserProfileRepository frontendUserProfileRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.mail-attachment-dir}")
    private String attachmentDir;

    /**
     * Gibt alle Dokumenten-Ketten gruppiert nach Status zurück.
     */
    @GetMapping
    public ResponseEntity<BestellungsUebersichtDto> getUebersicht() {
        // Alle Dokumente laden
        List<LieferantDokument> alleDokumente = dokumentRepository.findAll();

        // IDs aller bereits zugeordneten Dokumente
        Set<Long> zugeordneteDokumentIds = projektAnteilRepository.findAll().stream()
                .map(a -> a.getDokument().getId())
                .collect(Collectors.toSet());

        // IDs aller Lagerbestellungen (Bestellungen ohne Projekt-Zuordnung)
        Set<Long> lagerbestellungIds = geschaeftsdokumentRepository.findAll().stream()
                .filter(gd -> Boolean.TRUE.equals(gd.getLagerbestellung()))
                .map(gd -> gd.getDokument() != null ? gd.getDokument().getId() : -1L)
                .collect(Collectors.toSet());

        // Ketten bilden
        List<DokumentenKette> alleKetten = buildKetten(alleDokumente);

        // Nach Status gruppieren
        List<DokumentenKette> offeneAnfragen = new ArrayList<>();
        List<DokumentenKette> laufendeBestellungen = new ArrayList<>();
        List<DokumentenKette> abgeschlossen = new ArrayList<>();
        List<DokumentenKette> zugeordnet = new ArrayList<>();

        for (DokumentenKette kette : alleKetten) {
            boolean hatRechnung = kette.dokumente.stream()
                    .anyMatch(d -> d.typ == LieferantDokumentTyp.RECHNUNG);
            boolean hatAB = kette.dokumente.stream()
                    .anyMatch(d -> d.typ == LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
            boolean hatNurAnfrage = kette.dokumente.stream()
                    .allMatch(d -> d.typ == LieferantDokumentTyp.ANGEBOT);

            if (hatRechnung) {
                // Prüfe ob die Rechnung bereits zugeordnet oder als Lagerbestellung markiert ist
                boolean istZugeordnet = kette.dokumente.stream()
                        .filter(d -> d.typ == LieferantDokumentTyp.RECHNUNG)
                        .anyMatch(d -> zugeordneteDokumentIds.contains(d.id) || lagerbestellungIds.contains(d.id));

                if (istZugeordnet) {
                    zugeordnet.add(kette);
                } else {
                    abgeschlossen.add(kette);
                }
            } else if (hatAB) {
                laufendeBestellungen.add(kette);
            } else if (hatNurAnfrage) {
                offeneAnfragen.add(kette);
            }
        }

        // Nach Datum sortieren (neueste zuerst)
        Comparator<DokumentenKette> byDate = (a, b) -> {
            LocalDate dateA = a.dokumente.stream()
                    .map(d -> d.dokumentDatum)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.MIN);
            LocalDate dateB = b.dokumente.stream()
                    .map(d -> d.dokumentDatum)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.MIN);
            return dateB.compareTo(dateA);
        };

        offeneAnfragen.sort(byDate);
        laufendeBestellungen.sort(byDate);
        abgeschlossen.sort(byDate);
        zugeordnet.sort(byDate);

        return ResponseEntity.ok(new BestellungsUebersichtDto(
                offeneAnfragen, laufendeBestellungen, abgeschlossen, zugeordnet));
    }

    /**
     * Gibt die Geschäftsdaten eines Dokuments für die Bearbeitung zurück.
     */
    @GetMapping("/geschaeftsdaten/{dokId}")
    public ResponseEntity<GeschaeftsdatenDto> getGeschaeftsdaten(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        GeschaeftsdatenDto dto = new GeschaeftsdatenDto();
        dto.id = gd.getId();
        dto.dokumentNummer = gd.getDokumentNummer();
        dto.dokumentDatum = gd.getDokumentDatum();
        dto.betragNetto = gd.getBetragNetto();
        dto.betragBrutto = gd.getBetragBrutto();
        
        // Lagerbestellung Flag
        dto.istLagerbestellung = Boolean.TRUE.equals(gd.getLagerbestellung());
        dto.mwstSatz = gd.getMwstSatz();
        dto.liefertermin = gd.getLiefertermin();
        dto.bestellnummer = gd.getBestellnummer();

        // Lieferant-Info
        if (gd.getDokument() != null && gd.getDokument().getLieferant() != null) {
            dto.lieferantId = gd.getDokument().getLieferant().getId();
            dto.lieferantName = gd.getDokument().getLieferant().getLieferantenname();
        }

        return ResponseEntity.ok(dto);
    }

    /**
     * Aktualisiert die Geschäftsdaten eines Dokuments.
     */
    @PutMapping("/geschaeftsdaten/{dokId}")
    @Transactional
    public ResponseEntity<GeschaeftsdatenDto> updateGeschaeftsdaten(
            @PathVariable Long dokId,
            @RequestBody GeschaeftsdatenDto dto) {

        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.notFound().build();
        }

        if (dto.dokumentNummer != null)
            gd.setDokumentNummer(dto.dokumentNummer);
        if (dto.dokumentDatum != null)
            gd.setDokumentDatum(dto.dokumentDatum);
        if (dto.betragNetto != null)
            gd.setBetragNetto(dto.betragNetto);
        if (dto.betragBrutto != null)
            gd.setBetragBrutto(dto.betragBrutto);
        if (dto.mwstSatz != null)
            gd.setMwstSatz(dto.mwstSatz);
        if (dto.liefertermin != null)
            gd.setLiefertermin(dto.liefertermin);
        if (dto.bestellnummer != null)
            gd.setBestellnummer(dto.bestellnummer);

        geschaeftsdokumentRepository.save(gd);

        return getGeschaeftsdaten(dokId);
    }

    /**
     * Gibt alle aktiven Kostenstellen zurück.
     */
    @GetMapping("/kostenstellen")
    public ResponseEntity<List<KostenstelleDto>> getKostenstellen() {
        var list = kostenstelleRepository.findByAktivTrueOrderBySortierungAsc().stream()
                .map(k -> new KostenstelleDto(k.getId(), k.getBezeichnung(), k.getTyp().name(), k.getBeschreibung()))
                .toList();
        return ResponseEntity.ok(list);
    }

    /**
     * Ordnet eine Rechnung anteilig Projekten zu.
     * - Erstellt BestellungProjektZuordnung für die Bestellungsübersicht
     * - Erstellt LieferantDokumentProjektAnteil für Materialkosten/Nachkalkulation
     * - Kopiert das PDF auch als ProjektDokument in die Gruppe EINGANGSRECHNUNGEN
     */
    @PostMapping("/zuordnen")
    @Transactional
    public ResponseEntity<?> zuordnenZuProjekten(@RequestBody ZuordnungRequest request) {
        // Geschäftsdokument laden
        var gd = geschaeftsdokumentRepository.findById(request.geschaeftsdokumentId).orElse(null);
        if (gd == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geschäftsdokument nicht gefunden"));
        }

        // LieferantDokument für die Materialkosten-Zuordnung
        LieferantDokument lieferantDokument = gd.getDokument();
        if (lieferantDokument == null) {
             return ResponseEntity.badRequest().body(Map.of("error", "Kein Basis-Dokument vorhanden"));
        }

        // Frontend-User laden (wer ordnet zu?)
        FrontendUserProfile zugeordnetVon = null;
        if (request.frontendUserProfileId != null) {
            zugeordnetVon = frontendUserProfileRepository.findById(request.frontendUserProfileId).orElse(null);
        }

        // Lösche alte LieferantDokumentProjektAnteil für dieses Dokument
        List<LieferantDokumentProjektAnteil> alteAnteile = projektAnteilRepository.findByDokumentId(lieferantDokument.getId());
        projektAnteilRepository.deleteAll(alteAnteile);

        // Neue Zuordnungen erstellen
        List<LieferantDokumentProjektAnteil> neueProjektAnteile = new ArrayList<>();
        
        for (ProjektAnteil anteil : request.projektAnteile) {
            // Validierung: Entweder Projekt oder Kostenstelle
            Projekt projekt = null;
            Kostenstelle kostenstelle = null;

            if (anteil.projektId != null) {
                projekt = projektRepository.findById(anteil.projektId).orElse(null);
                if (projekt == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Projekt nicht gefunden: " + anteil.projektId));
                }
            } else if (anteil.kostenstelleId != null) {
                kostenstelle = kostenstelleRepository.findById(anteil.kostenstelleId).orElse(null);
                if (kostenstelle == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Kostenstelle nicht gefunden: " + anteil.kostenstelleId));
                }
            } else {
                // Skip invalid entries
                continue;
            }

            // LieferantDokumentProjektAnteil für Materialkosten/Nachkalkulation und Anzeige
            LieferantDokumentProjektAnteil projektAnteil = new LieferantDokumentProjektAnteil();
            projektAnteil.setDokument(lieferantDokument);
            projektAnteil.setProjekt(projekt);
            projektAnteil.setKostenstelle(kostenstelle);
            // Speichere Prozent als BigDecimal im "prozent"-Integer Feld? 
            // Nein, Anteil-Entity hat Integer für Prozent und BigDecimal für Absolut.
            // Der Request hat BigDecimal für beides.
            projektAnteil.setProzent(anteil.prozentanteil != null ? anteil.prozentanteil.intValue() : 100);
            projektAnteil.setBeschreibung(anteil.beschreibung);
            
            // Betrag berechnen
            if (gd.getBetragBrutto() != null) {
                projektAnteil.berechneAnteil(gd.getBetragBrutto());
            } else if (anteil.betrag != null) {
                projektAnteil.setBerechneterBetrag(anteil.betrag);
            }
            neueProjektAnteile.add(projektAnteil);
            projektAnteil.setZugeordnetVon(zugeordnetVon);
            
            // PDF als ProjektDokument in EINGANGSRECHNUNGEN-Gruppe speichern (Nur bei Projektzuordnung)
            if (projekt != null) {
                copyPdfToProject(gd, projekt);
            }
        }

        projektAnteilRepository.saveAll(neueProjektAnteile);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Erfolgreich " + neueProjektAnteile.size() + " Zuordnung(en) gespeichert",
                "zuordnungen", neueProjektAnteile.size()));
    }

    /**
     * Kopiert das PDF des Geschäftsdokuments als ProjektDokument in die EINGANGSRECHNUNGEN-Gruppe.
     */
    private void copyPdfToProject(LieferantGeschaeftsdokument gd, Projekt projekt) {
        if (gd.getDokument() == null || gd.getDokument().getAttachment() == null) {
            return;
        }
        

        var attachment = gd.getDokument().getAttachment();
        var email = attachment.getEmail();
        if (email == null || email.getLieferant() == null) {
            return;
        }
        
        Long lieferantId = email.getLieferant().getId();

        String storedFilename = attachment.getStoredFilename();
        String originalFilename = attachment.getOriginalFilename();
        
        // Quellpfad (Mail-Attachment)
        Path sourcePath = Path.of(attachmentDir).toAbsolutePath().normalize()
                .resolve("email")
                .resolve(String.valueOf(lieferantId))
                .resolve(storedFilename);
        
        if (!Files.exists(sourcePath)) {
            return;
        }
        
        // Erzeuge eindeutigen gespeicherten Dateinamen für Projekt
        String lieferantName = email.getLieferant().getLieferantenname();
        String dokumentNummer = gd.getDokumentNummer() != null ? gd.getDokumentNummer() : "unbekannt";
        String gespeicherterName = "ER_%s_%s_%d.pdf".formatted(
                sanitizeFilename(lieferantName != null ? lieferantName : ""),
                sanitizeFilename(dokumentNummer),
                System.currentTimeMillis());
        
        // Zielpfad (Projekt-Uploads)
        Path projektDir = Path.of(uploadDir).toAbsolutePath().normalize()
                .resolve(String.valueOf(projekt.getId()));
        Path targetPath = projektDir.resolve(gespeicherterName);
        
        try {
            Files.createDirectories(projektDir);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            // ProjektDokument erstellen
            ProjektDokument dok = new ProjektDokument();
            dok.setProjekt(projekt);
            dok.setOriginalDateiname(originalFilename != null ? originalFilename : storedFilename);
            dok.setGespeicherterDateiname(gespeicherterName);
            dok.setDateityp("application/pdf");
            dok.setDateigroesse(Files.size(targetPath));
            dok.setUploadDatum(LocalDate.now());
            dok.setDokumentGruppe(DokumentGruppe.EINGANGSRECHNUNGEN);
            dok.setLieferant(email.getLieferant());
            
            projektDokumentRepository.save(dok);
        } catch (IOException e) {
            // Fehler beim Kopieren protokollieren, aber nicht abbrechen
            System.err.println("Fehler beim Kopieren des PDFs: " + e.getMessage());
        }
    }
    
    private String sanitizeFilename(String name) {
        if (name == null) return "";
        return name.replaceAll("[^a-zA-Z0-9äöüÄÖÜß_-]", "_").replaceAll("_{2,}", "_");
    }


    /**
     * Markiert eine Rechnung als Lagerbestellung (keine Projektzuordnung nötig).
     */
    @PostMapping("/lagerbestellung/{dokId}")
    @Transactional
    public ResponseEntity<?> markiereAlsLagerbestellung(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geschäftsdokument nicht gefunden"));
        }

        gd.setLagerbestellung(true);
        geschaeftsdokumentRepository.save(gd);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Als Lagerbestellung markiert"));
    }

    /**
     * Hebt die Zuordnung einer Rechnung zu allen Projekten auf.
     * Die Rechnung wird wieder als "Abgeschlossen" angezeigt.
     */
    @DeleteMapping("/zuordnung/{dokId}")
    @Transactional
    public ResponseEntity<?> hebeZuordnungAuf(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Geschäftsdokument nicht gefunden"));
        }

        // Lösche alle LieferantDokumentProjektAnteil
        if (gd.getDokument() != null) {
            List<LieferantDokumentProjektAnteil> anteile = projektAnteilRepository.findByDokumentId(gd.getDokument().getId());
            projektAnteilRepository.deleteAll(anteile);
        }

        // Optional: Lagerbestellung-Flag zurücksetzen
        gd.setLagerbestellung(false);
        geschaeftsdokumentRepository.save(gd);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Zuordnung aufgehoben - Rechnung wieder in 'Abgeschlossen'"));
    }

    /**
     * Gibt die Zuordnungen für ein Geschäftsdokument zurück.
     */
    @GetMapping("/zuordnungen/{dokId}")
    public ResponseEntity<List<ZuordnungDto>> getZuordnungen(@PathVariable Long dokId) {
        var gd = geschaeftsdokumentRepository.findById(dokId).orElse(null);
        if (gd == null || gd.getDokument() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<LieferantDokumentProjektAnteil> anteile = projektAnteilRepository.findByDokumentId(gd.getDokument().getId());

        List<ZuordnungDto> dtos = anteile.stream().map(a -> {
            ZuordnungDto dto = new ZuordnungDto();
            dto.id = a.getId();
            if (a.getProjekt() != null) {
                dto.projektId = a.getProjekt().getId();
                dto.projektName = a.getProjekt().getBauvorhaben();
            } else if (a.getKostenstelle() != null) {
                dto.kostenstelleId = a.getKostenstelle().getId();
                dto.kostenstelleName = a.getKostenstelle().getBezeichnung();
            }
            dto.betrag = a.getBerechneterBetrag();
            dto.prozentanteil = a.getProzent() != null ? BigDecimal.valueOf(a.getProzent()) : null;
            dto.beschreibung = a.getBeschreibung();
            dto.zugeordnetAm = a.getZugeordnetAm();
            if (a.getZugeordnetVon() != null) {
                dto.zugeordnetVonName = a.getZugeordnetVon().getDisplayName();
            }
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Gibt alle Zuordnungen für eine Kostenstelle zurück (aus beiden Quellen).
     */
    @GetMapping("/zuordnungen/kostenstelle/{kostenstelleId}")
    public ResponseEntity<List<ZuordnungDto>> getZuordnungenForKostenstelle(@PathVariable Long kostenstelleId) {
        
        // Quelle: LieferantDokumentProjektAnteil (Dokumentenverwaltung)
        var dokumentAnteile = projektAnteilRepository.findByKostenstelleId(kostenstelleId);

        List<ZuordnungDto> dtos = dokumentAnteile.stream().map(a -> {
            ZuordnungDto dto = new ZuordnungDto();
            dto.id = a.getId();
            dto.kostenstelleId = a.getKostenstelle().getId();
            dto.kostenstelleName = a.getKostenstelle().getBezeichnung();
            dto.betrag = a.getBerechneterBetrag();
            dto.prozentanteil = a.getProzent() != null ? BigDecimal.valueOf(a.getProzent()) : null;
            dto.beschreibung = a.getBeschreibung();
            
            // Datum kann vom UploadDatum kommen, wenn nicht anders gesetzt
            if (a.getZugeordnetAm() != null) {
                dto.zugeordnetAm = a.getZugeordnetAm();
            } else if (a.getDokument().getUploadDatum() != null) {
                dto.zugeordnetAm = a.getDokument().getUploadDatum();
            }

            if (a.getDokument().getLieferant() != null) {
                dto.lieferantName = a.getDokument().getLieferant().getLieferantenname();
            }
            
            if (a.getDokument().getGeschaeftsdaten() != null) {
                dto.geschaeftsdokumentId = a.getDokument().getGeschaeftsdaten().getId();
                dto.bestellnummer = a.getDokument().getGeschaeftsdaten().getBestellnummer();
                dto.dokumentDatum = a.getDokument().getGeschaeftsdaten().getDokumentDatum();
            }
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Bildet Dokumenten-Ketten basierend auf Verknüpfungen.
     */
    private List<DokumentenKette> buildKetten(List<LieferantDokument> dokumente) {
        // Map für schnellen Zugriff
        Map<Long, LieferantDokument> dokMap = dokumente.stream()
                .collect(Collectors.toMap(LieferantDokument::getId, d -> d));

        // Set für bereits verarbeitete Dokumente
        Set<Long> verarbeitet = new HashSet<>();
        List<DokumentenKette> ketten = new ArrayList<>();

        for (LieferantDokument dok : dokumente) {
            if (verarbeitet.contains(dok.getId()))
                continue;

            // Sammle alle verknüpften Dokumente
            Set<Long> kettenIds = new HashSet<>();
            collectKettenIds(dok, dokMap, kettenIds);

            if (!kettenIds.isEmpty()) {
                List<DokumentRef> refs = new ArrayList<>();
                for (Long id : kettenIds) {
                    LieferantDokument d = dokMap.get(id);
                    if (d != null) {
                        refs.add(toDokumentRef(d));
                        verarbeitet.add(id);
                    }
                }

                // Sortiere nach Typ-Reihenfolge
                // Sortieren: Erst nach Typ-Rang, dann nach Datum
                refs.sort(Comparator.comparingInt((DokumentRef r) -> getTypReihenfolge(r.typ))
                        .thenComparing(r -> r.dokumentDatum, Comparator.nullsLast(Comparator.naturalOrder())));

                // Lieferant aus erstem Dokument
                LieferantDokument first = dokMap.get(kettenIds.iterator().next());
                String lieferantName = first.getLieferant() != null
                        ? first.getLieferant().getLieferantenname()
                        : null;
                Long lieferantId = first.getLieferant() != null
                        ? first.getLieferant().getId()
                        : null;

                ketten.add(new DokumentenKette(
                        UUID.randomUUID().toString(),
                        lieferantId,
                        lieferantName,
                        refs));
            }
        }

        return ketten;
    }

    private void collectKettenIds(LieferantDokument dok, Map<Long, LieferantDokument> dokMap, Set<Long> collected) {
        if (dok == null || collected.contains(dok.getId()))
            return;
        collected.add(dok.getId());

        // Verknüpfte Dokumente durchlaufen
        if (dok.getVerknuepfteDokumente() != null) {
            for (LieferantDokument verknuepft : dok.getVerknuepfteDokumente()) {
                collectKettenIds(verknuepft, dokMap, collected);
            }
        }
    }

    private int getTypReihenfolge(LieferantDokumentTyp typ) {
        return switch (typ) {
            case ANGEBOT -> 0;
            case AUFTRAGSBESTAETIGUNG -> 1;
            case LIEFERSCHEIN -> 2;
            case RECHNUNG -> 3;
            case GUTSCHRIFT -> 4;
            case SONSTIG -> 99;
        };
    }

    private DokumentRef toDokumentRef(LieferantDokument d) {
        DokumentRef ref = new DokumentRef();
        ref.id = d.getId();
        ref.typ = d.getTyp();
        ref.dateiname = d.getEffektiverDateiname();

        if (d.getGeschaeftsdaten() != null) {
            var gd = d.getGeschaeftsdaten();
            ref.dokumentNummer = gd.getDokumentNummer();
            ref.dokumentDatum = gd.getDokumentDatum();
            ref.betragBrutto = gd.getBetragBrutto() != null ? gd.getBetragBrutto().doubleValue() : null;
            ref.betragNetto = gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : null;
            ref.liefertermin = gd.getLiefertermin();
        }

        // PDF-URL: Mehrere Quellen prüfen
        // 1. E-Mail Attachment
        if (d.getAttachment() != null && d.getAttachment().getEmail() != null) {
            var att = d.getAttachment();
            ref.pdfUrl = "/api/emails/" + att.getEmail().getId() +
                    "/attachments/" + att.getId();
        } 
        // 2. Gespeicherte Datei über Lieferant
        else if (d.getLieferant() != null && d.getGespeicherterDateiname() != null) {
            ref.pdfUrl = "/api/lieferanten/" + d.getLieferant().getId() +
                    "/dokumente/" + d.getId() + "/download";
        }
        // 3. Fallback: Generischer Dokument-Endpoint
        else if (d.getId() != null) {
            ref.pdfUrl = "/api/lieferant-dokumente/" + d.getId() + "/download";
        }

        return ref;
    }

    // ========== DTOs ==========

    public record BestellungsUebersichtDto(
            List<DokumentenKette> offeneAnfragen,
            List<DokumentenKette> laufendeBestellungen,
            List<DokumentenKette> abgeschlossen,
            List<DokumentenKette> zugeordnet) {
    }

    public record DokumentenKette(
            String id,
            Long lieferantId,
            String lieferantName,
            List<DokumentRef> dokumente) {
    }

    public static class DokumentRef {
        public Long id;
        public LieferantDokumentTyp typ;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public Double betragBrutto;
        public Double betragNetto;
        public LocalDate liefertermin;
        public String dateiname;
        public String pdfUrl;
    }

    public static class GeschaeftsdatenDto {
        public Long id;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public BigDecimal betragNetto;
        public BigDecimal betragBrutto;
        public BigDecimal mwstSatz;
        public LocalDate liefertermin;
        public String bestellnummer;
        public Long lieferantId;
        public String lieferantName;
        public Boolean istLagerbestellung;
    }

    public static class ZuordnungRequest {
        public Long geschaeftsdokumentId;
        public Long frontendUserProfileId;
        public List<ProjektAnteil> projektAnteile;
    }

    public static class ProjektAnteil {
        public Long projektId;
        public Long kostenstelleId; // Optional
        public BigDecimal betrag;
        public BigDecimal prozentanteil;
        public String beschreibung;
    }

    public static class ZuordnungDto {
        public Long id;
        public Long projektId;
        public String projektName;
        public Long kostenstelleId;
        public String kostenstelleName;
        public BigDecimal betrag;
        public BigDecimal prozentanteil;
        public String beschreibung;
        public LocalDateTime zugeordnetAm;
        public String zugeordnetVonName;
        
        // Extra Info
        public String lieferantName;
        public String bestellnummer;
        public LocalDate dokumentDatum;
        public Long geschaeftsdokumentId;
    }

    public record KostenstelleDto(Long id, String bezeichnung, String typ, String beschreibung) {}
}
