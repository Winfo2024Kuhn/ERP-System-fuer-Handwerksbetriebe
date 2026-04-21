package org.example.kalkulationsprogramm.mapper;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.BestellQuelle;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelInProjektResponseDto;
import org.example.kalkulationsprogramm.dto.Materialkosten.MaterialkostenResponseDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto;

import org.example.kalkulationsprogramm.dto.ProjektProduktkategorie.ProjektProduktkategorieResponseDto;
import org.example.kalkulationsprogramm.dto.ProjektZeit.ZeitResponseDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@AllArgsConstructor
public class ProjektMapper {
    private final ProduktkategorieMapper produktkategorieMapper;
    private final AnfrageMapper anfrageMapper;
    private final KundeMapper kundeMapper;

    public ProjektResponseDto toProjektResponseDto(Projekt projekt) {
        if (projekt == null) {
            return null;
        }
        ProjektResponseDto dto = new ProjektResponseDto();
        dto.setId(projekt.getId());
        dto.setBauvorhaben(projekt.getBauvorhaben());
        dto.setStrasse(projekt.getStrasse());
        dto.setPlz(projekt.getPlz());
        dto.setOrt(projekt.getOrt());
        dto.setKunde(projekt.getKunde());
        if (projekt.getKundenId() != null) {
            dto.setKundeDto(kundeMapper.toResponseDto(projekt.getKundenId()));
        }
        dto.setKundenId(projekt.getKundenId() != null ? projekt.getKundenId().getId() : null);
        dto.setKurzbeschreibung(projekt.getKurzbeschreibung());
        dto.setAnlegedatum(projekt.getAnlegedatum());
        dto.setAbschlussdatum(projekt.getAbschlussdatum());

        String bildUrl = projekt.getBildUrl();
        if (bildUrl != null && !bildUrl.isBlank() && !bildUrl.startsWith("/")) {
            bildUrl = "/api/images/" + bildUrl;
        }
        dto.setBildUrl(bildUrl);

        dto.setKundennummer(projekt.getKundennummer());
        dto.setAuftragsnummer(projekt.getAuftragsnummer());
        dto.setKundenEmails(projekt.getKundenEmails());
        dto.setBruttoPreis(projekt.getBruttoPreis());
        dto.setBezahlt(projekt.isBezahlt());
        dto.setAbgeschlossen(projekt.isAbgeschlossen());
        
        // Projektart-Mapping
        if (projekt.getProjektArt() != null) {
            dto.setProjektArt(projekt.getProjektArt().name());
            dto.setProduktiv(projekt.getProjektArt().isProduktiv());
        } else {
            dto.setProjektArt("PAUSCHAL"); // Default
            dto.setProduktiv(true);
        }

        // EN 1090 Ausführungsklasse
        dto.setExcKlasse(projekt.getExcKlasse());

        if (projekt.getProjektProduktkategorien() != null) {
            List<ProjektProduktkategorieResponseDto> pkDtos = projekt.getProjektProduktkategorien().stream()
                    .map(ppk -> {
                        ProjektProduktkategorieResponseDto pkDto = new ProjektProduktkategorieResponseDto();
                        pkDto.setId(ppk.getId());
                        if (ppk.getProduktkategorie() != null) {
                            pkDto.setProduktkategorie(
                                    produktkategorieMapper.toProduktkategorieResponseDto(ppk.getProduktkategorie()));
                        }
                        pkDto.setMenge(ppk.getMenge());
                        return pkDto;
                    }).toList();
            dto.setProduktkategorien(pkDtos);
        }

        if (projekt.getMaterialkosten() != null) {
            List<MaterialkostenResponseDto> mkDtos = projekt.getMaterialkosten().stream()
                    .map(mk -> {
                        MaterialkostenResponseDto mDto = new MaterialkostenResponseDto();
                        mDto.setId(mk.getId());
                        mDto.setBeschreibung(mk.getBeschreibung());
                        mDto.setExterneArtikelnummer(mk.getExterneArtikelnummer());
                        mDto.setMonat(mk.getMonat());
                        mDto.setBetrag(mk.getBetrag());
                        return mDto;
                    }).toList();
            dto.setMaterialkosten(mkDtos);
        }

        if (projekt.getArtikelInProjekt() != null) {
            List<ArtikelInProjektResponseDto> artikelDtos = projekt.getArtikelInProjekt().stream()
                    .map(aip -> {
                        ArtikelInProjektResponseDto aDto = new ArtikelInProjektResponseDto();
                        aDto.setId(aip.getId());
                        if (aip.getArtikel() != null) {
                            aDto.setArtikelId(aip.getArtikel().getId());
                            aDto.setExterneArtikelnummer(aip.getArtikel().getExterneArtikelnummer());
                            aDto.setProduktname(aip.getArtikel().getProduktname());
                            aDto.setProdukttext(aip.getArtikel().getProdukttext());
                        }
                        aDto.setStueckzahl(aip.getStueckzahl());
                        aDto.setMeter(aip.getMeter());
                        aDto.setKilogramm(aip.getKilogramm());
                        if (aip.getArtikel() != null && aip.getArtikel().getWerkstoff() != null) {
                            aDto.setWerkstoffName(aip.getArtikel().getWerkstoff().getName());
                        }
                        BigDecimal preisProStueck = aip.getPreisProStueck();
                        boolean bestellt = aip.getQuelle() == BestellQuelle.BESTELLT;
                        if (!bestellt && aip.getLieferantenArtikelPreis() != null
                                && aip.getLieferantenArtikelPreis().getPreis() != null) {
                            BigDecimal lieferantenPreis = aip.getLieferantenArtikelPreis().getPreis();
                            BigDecimal menge = BigDecimal.ZERO;
                            if (aip.getArtikel() != null && aip.getArtikel().getVerrechnungseinheit() != null) {
                                switch (aip.getArtikel().getVerrechnungseinheit()) {
                                    case STUECK -> menge = aip.getStueckzahl() != null
                                            ? BigDecimal.valueOf(aip.getStueckzahl())
                                            : BigDecimal.ZERO;
                                    case LAUFENDE_METER, QUADRATMETER -> menge = aip.getMeter() != null
                                            ? aip.getMeter()
                                            : BigDecimal.ZERO;
                                    case KILOGRAMM -> menge = aip.getKilogramm() != null
                                            ? aip.getKilogramm()
                                            : BigDecimal.ZERO;
                                }
                            }
                            preisProStueck = lieferantenPreis.multiply(menge);
                        }
                        aDto.setPreisProStueck(preisProStueck);
                        aDto.setHinzugefuegtAm(aip.getHinzugefuegtAm());
                        aDto.setBestellt(bestellt);
                        aDto.setBestelltAm(null);
                        aDto.setKommentar(aip.getKommentar());
                        aDto.setSchnittForm(aip.getSchnittForm());
                        aDto.setAnschnittWinkelLinks(aip.getAnschnittWinkelLinks());
                        aDto.setAnschnittWinkelRechts(aip.getAnschnittWinkelRechts());
                        if (aip.getLieferant() != null) {
                            aDto.setLieferantName(aip.getLieferant().getLieferantenname());
                        }
                        return aDto;
                    }).toList();
            dto.setArtikel(artikelDtos);
        }

        if (projekt.getZeitbuchungen() != null) {
            List<ZeitResponseDto> zeitDtos = projekt.getZeitbuchungen().stream()
                    .map(zeitEntity -> {
                        ZeitResponseDto zeitDto = new ZeitResponseDto();
                        zeitDto.setId(zeitEntity.getId());
                        zeitDto.setAnzahlInStunden(zeitEntity.getAnzahlInStunden());
                        if (zeitEntity.getArbeitsgangStundensatz() != null) {
                            zeitDto.setStundensatz(zeitEntity.getArbeitsgangStundensatz().getSatz());
                        }
                        if (zeitEntity.getArbeitsgang() != null) {
                            zeitDto.setArbeitsgangBeschreibung(zeitEntity.getArbeitsgang().getBeschreibung());
                        }
                        if (zeitEntity.getProjektProduktkategorie() != null
                                && zeitEntity.getProjektProduktkategorie().getProduktkategorie() != null) {
                            zeitDto.setProduktkategorie(
                                    produktkategorieMapper.toProduktkategorieResponseDto(
                                            zeitEntity.getProjektProduktkategorie().getProduktkategorie()));
                        }
                        if (zeitEntity.getMitarbeiter() != null) {
                            zeitDto.setMitarbeiterVorname(zeitEntity.getMitarbeiter().getVorname());
                            zeitDto.setMitarbeiterNachname(zeitEntity.getMitarbeiter().getNachname());
                        }
                        return zeitDto;
                    }).toList();
            dto.setZeiten(zeitDtos);
        }

        if (projekt.getAnfragen() != null) {
            List<AnfrageResponseDto> anfrageDtos = projekt.getAnfragen().stream()
                    .map(anfrageMapper::toAnfrageResponseDto)
                    .toList();
            dto.setAnfragen(anfrageDtos);
        }
        // Emails are now handled via separate API / Endpoint
        // if (projekt.getEmails() != null) { ... }
        return dto;
    }

    public ProjektResponseDto toProjektListeDto(Projekt projekt) {
        if (projekt == null) {
            return null;
        }
        ProjektResponseDto dto = new ProjektResponseDto();
        dto.setId(projekt.getId());
        dto.setBauvorhaben(projekt.getBauvorhaben());
        dto.setStrasse(projekt.getStrasse());
        dto.setPlz(projekt.getPlz());
        dto.setOrt(projekt.getOrt());
        dto.setKunde(projekt.getKunde());
        if (projekt.getKundenId() != null) {
            dto.setKundeDto(kundeMapper.toResponseDto(projekt.getKundenId()));
        }
        dto.setKundenId(projekt.getKundenId() != null ? projekt.getKundenId().getId() : null);
        dto.setKurzbeschreibung(projekt.getKurzbeschreibung());
        dto.setAnlegedatum(projekt.getAnlegedatum());
        dto.setAbschlussdatum(projekt.getAbschlussdatum());

        String bildUrl = projekt.getBildUrl();
        if (bildUrl != null && !bildUrl.isBlank() && !bildUrl.startsWith("/")) {
            bildUrl = "/api/images/" + bildUrl;
        }
        dto.setBildUrl(bildUrl);

        dto.setKundennummer(projekt.getKundennummer());
        dto.setAuftragsnummer(projekt.getAuftragsnummer());
        dto.setBruttoPreis(projekt.getBruttoPreis());
        dto.setBezahlt(projekt.isBezahlt());
        dto.setAbgeschlossen(projekt.isAbgeschlossen());

        return dto;
    }

}
