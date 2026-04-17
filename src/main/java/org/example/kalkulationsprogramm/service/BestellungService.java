package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.*;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.dto.Bestellung.ManuelleBestellpositionDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class BestellungService {

    private final ArtikelInProjektRepository artikelInProjektRepository;
    private final ProjektRepository projektRepository;
    private final LieferantenRepository lieferantenRepository;
    private final KategorieRepository kategorieRepository;
    private final ArtikelRepository artikelRepository;
    private final ZeugnisService zeugnisService;

    public List<BestellungResponseDto> findeOffeneBestellungen() {
        List<BestellungResponseDto> dtos = artikelInProjektRepository
                .findByBestelltFalseOrderByLieferant_LieferantennameAscProjekt_BauvorhabenAsc()
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

    @Transactional
    public void setBestellt(Long id, boolean bestellt) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Artikel nicht gefunden"));
        aip.setBestellt(bestellt);
        aip.setBestelltAm(bestellt ? LocalDate.now() : null);
        if (bestellt && aip.getLieferantenArtikelPreis() != null) {
            BigDecimal preis = aip.getLieferantenArtikelPreis().getPreis();
            if (preis != null) {
                if (aip.getArtikel() != null && aip.getArtikel().getVerrechnungseinheit() != null) {
                    Verrechnungseinheit ve = aip.getArtikel().getVerrechnungseinheit();
                    switch (ve) {
                        case KILOGRAMM -> {
                            if (aip.getKilogramm() != null) {
                                preis = preis.multiply(aip.getKilogramm());
                            }
                        }
                        case LAUFENDE_METER, QUADRATMETER -> {
                            if (aip.getMeter() != null) {
                                preis = preis.multiply(aip.getMeter());
                            }
                        }
                        case STUECK -> {
                            if (aip.getStueckzahl() != null) {
                                preis = preis.multiply(BigDecimal.valueOf(aip.getStueckzahl()));
                            }
                        }
                    }
                }
                aip.setPreisProStueck(preis);
            } else {
                aip.setPreisProStueck(null);
            }
            aip.setLieferantenArtikelPreis(null);
        }
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
        boolean isWerkstoff = Integer.valueOf(1).equals(dto.getRootKategorieId());
        if (isWerkstoff && aip.getArtikel() != null) {
            dto.setLieferantName("Werkstoffe");
            dto.setLieferantId(null);
        } else if (aip.getLieferant() != null) {
            dto.setLieferantName(aip.getLieferant().getLieferantenname());
            dto.setLieferantId(aip.getLieferant().getId());
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
        dto.setBestellt(aip.isBestellt());
        dto.setBestelltAm(aip.getBestelltAm());
        dto.setSchnittForm(aip.getSchnittForm());
        dto.setAnschnittWinkelLinks(aip.getAnschnittWinkelLinks());
        dto.setAnschnittWinkelRechts(aip.getAnschnittWinkelRechts());
        // EN 1090
        dto.setZeugnisAnforderung(aip.getZeugnisAnforderung() != null ? aip.getZeugnisAnforderung().name() : null);
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

    @Transactional
    public BestellungResponseDto manuellePosition(ManuelleBestellpositionDto req) {
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setHinzugefuegtAm(LocalDate.now());
        aip.setBestellt(false);

        if (req.getProjektId() != null) {
            aip.setProjekt(projektRepository.getReferenceById(req.getProjektId()));
        }
        if (req.getLieferantId() != null) {
            aip.setLieferant(lieferantenRepository.getReferenceById(req.getLieferantId()));
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

        return toDto(artikelInProjektRepository.save(aip));
    }

    public Optional<ZeugnisTyp> zeugnisDefault(Integer kategorieId, String excKlasse) {
        return zeugnisService.bestimmeDefault(kategorieId, excKlasse);
    }

    @Transactional
    public void loeschePosition(Long id) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Position nicht gefunden"));
        if (aip.getArtikel() != null) {
            throw new IllegalStateException("Nur freie Positionen können hier gelöscht werden");
        }
        artikelInProjektRepository.delete(aip);
    }
}
