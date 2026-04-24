package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.ManuelleBestellpositionDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class BestellungService {

    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final ProjektRepository projektRepository;
    private final KategorieRepository kategorieRepository;
    private final ArtikelRepository artikelRepository;
    private final SchnittbilderRepository schnittbilderRepository;
    private final ZeugnisService zeugnisService;

    /**
     * Offene Bedarfspositionen über alle Projekte, sortiert nach Bauvorhaben.
     * Nach Stufe A2 hat die AiP keinen Lieferanten mehr — eine
     * Gruppierung nach Lieferant ist hier nicht mehr möglich. Lieferant-
     * und Zeugnis-Daten leben auf {@link Bestellung}/{@link Bestellposition}.
     */
    public List<BestellungResponseDto> findeOffeneBestellungen() {
        List<BestellungResponseDto> dtos = artikelInProjektRepository
                .findByQuelleOrderByProjekt_BauvorhabenAsc(BestellQuelle.OFFEN)
                .stream()
                .map(this::toDto)
                .toList();
        java.math.BigDecimal sum = dtos.stream()
                .map(BestellungResponseDto::getKilogramm)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal total = sum.compareTo(java.math.BigDecimal.ZERO) > 0 ? sum : null;
        dtos.forEach(d -> d.setGesamtKilogramm(total));
        return dtos;
    }

    /**
     * Setzt den Workflow-Zustand einer Bedarfsposition manuell auf BESTELLT/OFFEN.
     * Kein Preis-Write: der Kalkulationspreis kommt später aus der Rechnung
     * (über die interne Bestellnummer) bzw. beim AUS_LAGER-Haken aus dem
     * gleitenden Durchschnittspreis des Artikels.
     */
    @Transactional
    public void setBestellt(Long id, boolean bestellt) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Artikel nicht gefunden"));
        aip.setQuelle(bestellt ? BestellQuelle.BESTELLT : BestellQuelle.OFFEN);
        artikelInProjektRepository.save(aip);
    }

    private BestellungResponseDto toDto(ArtikelInProjekt aip) {
        BestellungResponseDto dto = new BestellungResponseDto();
        dto.setId(aip.getId());
        Kategorie kat = null;
        if (aip.getArtikel() != null) {
            dto.setArtikelId(aip.getArtikel().getId());
            dto.setExterneArtikelnummer(aip.getArtikel().getExterneArtikelnummer());
            dto.setProduktname(aip.getArtikel().getProduktname());
            dto.setProdukttext(aip.getArtikel().getProdukttext());
            dto.setKommentar(aip.getKommentar());
            if (aip.getArtikel().getWerkstoff() != null) {
                dto.setWerkstoffName(aip.getArtikel().getWerkstoff().getName());
            }
            kat = aip.getArtikel().getKategorie();
            if (kat != null) {
                dto.setKategorieName(kat.getBeschreibung());
                Kategorie root = kat;
                while (root.getParentKategorie() != null) {
                    root = root.getParentKategorie();
                }
                dto.setRootKategorieId(root.getId());
                dto.setRootKategorieName(root.getBeschreibung());
            }
        }
        // Freitext-Felder für manuelle Positionen (kein Stammartikel)
        if (aip.getArtikel() == null) {
            dto.setProduktname(aip.getFreitextProduktname());
            dto.setProdukttext(aip.getFreitextProdukttext());
            dto.setKommentar(aip.getKommentar());
            if (aip.getKategorie() != null) {
                dto.setKategorieName(aip.getKategorie().getBeschreibung());
                Kategorie root = aip.getKategorie();
                while (root.getParentKategorie() != null) root = root.getParentKategorie();
                dto.setRootKategorieId(root.getId());
                dto.setRootKategorieName(root.getBeschreibung());
            }
        }
        // Lieferant lebt nach A2 auf der Bestellung, nicht mehr auf AiP.
        // Die Platzhalter-Belegung "Werkstoffe" bleibt als UI-Hinweis für Werkstoff-Positionen.
        boolean isWerkstoff = Integer.valueOf(1).equals(dto.getRootKategorieId());
        if (isWerkstoff && aip.getArtikel() != null) {
            dto.setLieferantName("Werkstoffe");
            dto.setLieferantId(null);
        } else if (!isWerkstoff && aip.getArtikel() != null) {
            // Bei Nicht-Werkstoffen den Lieferanten aus der ersten
            // LieferantenArtikelPreise-Verknuepfung ziehen — der Mitarbeiter
            // sieht so im Bedarf/PDF, wer das Teil normalerweise liefert.
            aip.getArtikel().getArtikelpreis().stream()
                    .filter(lap -> lap.getExterneArtikelnummer() != null
                            && !lap.getExterneArtikelnummer().isBlank())
                    .findFirst()
                    .ifPresent(lap -> {
                        if (lap.getLieferant() != null) {
                            dto.setLieferantId(lap.getLieferant().getId());
                            dto.setLieferantName(lap.getLieferant().getLieferantenname());
                        }
                    });
        }
        if (aip.getProjekt() != null) {
            dto.setProjektId(aip.getProjekt().getId());
            dto.setProjektName(aip.getProjekt().getBauvorhaben());
            dto.setProjektNummer(aip.getProjekt().getAuftragsnummer());
            dto.setKundenName(aip.getProjekt().getKunde());
        }
        // Menge: Freitext-Position hat eigene Felder, Stammartikel nutzt meter/stueckzahl
        if (aip.getArtikel() == null) {
            dto.setMenge(aip.getFreitextMenge());
            dto.setEinheit(aip.getFreitextEinheit());
            dto.setStueckzahl(0);
        } else {
            dto.setStueckzahl(aip.getStueckzahl() != null ? aip.getStueckzahl() : 0);
            boolean hasMeter = isWerkstoff && aip.getMeter() != null &&
                    aip.getMeter().compareTo(java.math.BigDecimal.ZERO) > 0;
            java.math.BigDecimal menge = hasMeter ? aip.getMeter() :
                    java.math.BigDecimal.valueOf(dto.getStueckzahl());
            dto.setMenge(menge);
            dto.setEinheit(hasMeter ? "m" : "Stück");
        }
        dto.setKilogramm(aip.getKilogramm());
        dto.setFixmassMm(aip.getFixmassMm());
        boolean bestellt = aip.getQuelle() == BestellQuelle.BESTELLT;
        dto.setBestellt(bestellt);
        // bestelltAm/exportiertAm liegen nach A2 auf der Bestellung; das DTO
        // liefert hier nur die Info "Zeile ist im Bestellvorgang".
        dto.setBestelltAm(null);
        dto.setExportiertAm(null);
        if (aip.getSchnittbild() != null) {
            dto.setSchnittbildId(aip.getSchnittbild().getId());
            dto.setSchnittbildBildUrl(aip.getSchnittbild().getBildUrlSchnittbild());
            if (aip.getSchnittbild().getSchnittAchse() != null) {
                dto.setSchnittAchseBildUrl(aip.getSchnittbild().getSchnittAchse().getBildUrl());
            }
        }
        dto.setAnschnittbildStegUrl(aip.getAnschnittbildStegUrl());
        dto.setAnschnittbildFlanschUrl(aip.getAnschnittbildFlanschUrl());
        dto.setAnschnittStegText(aip.getAnschnittStegText());
        dto.setAnschnittFlanschText(aip.getAnschnittFlanschText());
        dto.setAnschnittWinkelLinks(aip.getAnschnittWinkelLinks());
        dto.setAnschnittWinkelRechts(aip.getAnschnittWinkelRechts());
        // EN 1090: zeugnisAnforderung bleibt am Bedarf und wird in Anfrage/Bestellung kopiert.
        if (aip.getZeugnisAnforderung() != null) {
            dto.setZeugnisAnforderung(aip.getZeugnisAnforderung().name());
        }
        if (aip.getProjekt() != null) dto.setExcKlasse(aip.getProjekt().getExcKlasse());
        dto.setFreiePosition(aip.getArtikel() == null);
        // Kategorie-ID für freie Positionen direkt, sonst über Artikel
        if (aip.getKategorie() != null) {
            dto.setKategorieId(aip.getKategorie().getId());
        } else if (kat != null) {
            dto.setKategorieId(kat.getId());
        }
        return dto;
    }

    /**
     * Manuelle Freitext-Bedarfsposition anlegen (ohne Stammartikel).
     * lieferantId aus dem Request wird nach A2 ignoriert — Lieferant wird
     * erst beim Anlegen der Bestellung über die Preisanfrage-Vergabe erfasst.
     * zeugnisAnforderung bleibt am Bedarf (wird in Anfrage/Bestellung kopiert).
     */
    @Transactional
    public BestellungResponseDto manuellePosition(ManuelleBestellpositionDto req) {
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setHinzugefuegtAm(LocalDate.now());

        if (req.getProjektId() != null) {
            aip.setProjekt(projektRepository.getReferenceById(req.getProjektId()));
        }
        if (req.getKategorieId() != null) {
            aip.setKategorie(kategorieRepository.getReferenceById(req.getKategorieId()));
        }

        // Stammartikel oder Freitext — Artikel hat Vorrang, wenn gesetzt
        if (req.getArtikelId() != null) {
            Artikel artikel = artikelRepository.findById(req.getArtikelId())
                    .orElseThrow(() -> new RuntimeException("Artikel nicht gefunden: " + req.getArtikelId()));
            aip.setArtikel(artikel);
            if (req.getMenge() != null) {
                aip.setStueckzahl(req.getMenge().intValue());
            }
        } else {
            aip.setFreitextProduktname(req.getProduktname());
            aip.setFreitextProdukttext(req.getProdukttext());
            aip.setFreitextMenge(req.getMenge());
            aip.setFreitextEinheit(req.getEinheit());
        }
        aip.setKommentar(req.getKommentar());
        aip.setFixmassMm(req.getFixmassMm());

        if (req.getZeugnisAnforderung() != null && !req.getZeugnisAnforderung().isBlank()) {
            aip.setZeugnisAnforderung(ZeugnisTyp.valueOf(req.getZeugnisAnforderung()));
        }

        applySchnittDaten(aip, req);

        return toDto(artikelInProjektRepository.save(aip));
    }

    public Optional<ZeugnisTyp> zeugnisDefault(Integer kategorieId, String excKlasse) {
        return zeugnisService.bestimmeDefault(kategorieId, excKlasse);
    }

    @Transactional
    public void loeschePosition(Long id) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Position nicht gefunden"));
        if (aip.getQuelle() == BestellQuelle.BESTELLT) {
            throw new IllegalStateException("Bereits bestellte Positionen können nicht gelöscht werden");
        }
        if (aip.getQuelle() == BestellQuelle.AUS_LAGER) {
            throw new IllegalStateException("Aus dem Lager entnommene Positionen können nicht gelöscht werden");
        }
        if (aip.getQuelle() == BestellQuelle.IN_ANFRAGE) {
            throw new IllegalStateException("Positionen in einer laufenden Anfrage können nicht gelöscht werden");
        }
        artikelInProjektRepository.delete(aip);
    }

    /**
     * Aktualisiert eine Bedarfsposition. Schlägt fehl, wenn die Position
     * bereits bestellt oder aus Lager entnommen ist. lieferantId aus dem
     * Request wird nach A2 ignoriert; zeugnisAnforderung bleibt am Bedarf.
     */
    @Transactional
    public BestellungResponseDto aktualisierePosition(Long id, ManuelleBestellpositionDto req) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Position nicht gefunden"));
        if (aip.getQuelle() == BestellQuelle.BESTELLT) {
            throw new IllegalStateException("Bereits bestellte Positionen können nicht bearbeitet werden");
        }
        if (aip.getQuelle() == BestellQuelle.AUS_LAGER) {
            throw new IllegalStateException("Aus dem Lager entnommene Positionen können nicht bearbeitet werden");
        }

        aip.setProjekt(req.getProjektId() != null ? projektRepository.getReferenceById(req.getProjektId()) : null);
        aip.setKategorie(req.getKategorieId() != null ? kategorieRepository.getReferenceById(req.getKategorieId()) : null);

        if (req.getArtikelId() != null) {
            Artikel artikel = artikelRepository.findById(req.getArtikelId())
                    .orElseThrow(() -> new RuntimeException("Artikel nicht gefunden: " + req.getArtikelId()));
            aip.setArtikel(artikel);
            if (req.getMenge() != null) {
                aip.setStueckzahl(req.getMenge().intValue());
            }
            // Freitext-Felder leeren, da jetzt Stammartikel
            aip.setFreitextProduktname(null);
            aip.setFreitextProdukttext(null);
            aip.setFreitextMenge(null);
            aip.setFreitextEinheit(null);
        } else {
            aip.setArtikel(null);
            aip.setFreitextProduktname(req.getProduktname());
            aip.setFreitextProdukttext(req.getProdukttext());
            aip.setFreitextMenge(req.getMenge());
            aip.setFreitextEinheit(req.getEinheit());
        }
        aip.setKommentar(req.getKommentar());
        aip.setFixmassMm(req.getFixmassMm());

        if (req.getZeugnisAnforderung() != null && !req.getZeugnisAnforderung().isBlank()) {
            aip.setZeugnisAnforderung(ZeugnisTyp.valueOf(req.getZeugnisAnforderung()));
        } else {
            aip.setZeugnisAnforderung(null);
        }

        applySchnittDaten(aip, req);

        return toDto(artikelInProjektRepository.save(aip));
    }

    /**
     * Uebernimmt Schnittbild-FK und die beiden Anschnittwinkel vom Request.
     * Kein Schnittbild im Request = normaler 90°-Zuschnitt → FK und Winkel NULL.
     * Leere Winkel bei gesetztem Schnittbild werden als 90° interpretiert.
     */
    private void applySchnittDaten(ArtikelInProjekt aip, ManuelleBestellpositionDto req) {
        if (req.getSchnittbildId() != null) {
            aip.setSchnittbild(schnittbilderRepository.findById(req.getSchnittbildId()).orElse(null));
            aip.setAnschnittWinkelLinks(req.getAnschnittWinkelLinks() != null ? req.getAnschnittWinkelLinks() : 90.0);
            aip.setAnschnittWinkelRechts(req.getAnschnittWinkelRechts() != null ? req.getAnschnittWinkelRechts() : 90.0);
        } else {
            aip.setSchnittbild(null);
            aip.setAnschnittWinkelLinks(null);
            aip.setAnschnittWinkelRechts(null);
        }
    }
}
