package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@AllArgsConstructor
public class BestellungService {

    private final ArtikelInProjektRepository artikelInProjektRepository;

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
        boolean isWerkstoff = dto.getRootKategorieId() != null && dto.getRootKategorieId() == 1;
        if (isWerkstoff) {
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
        dto.setStueckzahl(aip.getStueckzahl() != null ? aip.getStueckzahl() : 0);
        boolean hasMeter = isWerkstoff && aip.getMeter() != null &&
                aip.getMeter().compareTo(java.math.BigDecimal.ZERO) > 0;
        java.math.BigDecimal menge = hasMeter ? aip.getMeter() :
                java.math.BigDecimal.valueOf(dto.getStueckzahl());
        dto.setMenge(menge);
        dto.setEinheit(hasMeter ? "m" : "Stück");
        dto.setKilogramm(aip.getKilogramm());
        dto.setBestellt(aip.isBestellt());
        dto.setBestelltAm(aip.getBestelltAm());
        dto.setSchnittForm(aip.getSchnittForm());
        dto.setAnschnittWinkelLinks(aip.getAnschnittWinkelLinks());
        dto.setAnschnittWinkelRechts(aip.getAnschnittWinkelRechts());
        return dto;
    }
}
