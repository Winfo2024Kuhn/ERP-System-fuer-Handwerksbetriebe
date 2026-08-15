package org.example.kalkulationsprogramm.mapper;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
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
                        BigDecimal menge = ermittleMenge(aip);
                        BigDecimal preisProStueck = aip.getPreisProStueck();
                        if (!aip.isBestellt() && aip.getLieferantenArtikelPreis() != null
                                && aip.getLieferantenArtikelPreis().getPreis() != null) {
                            preisProStueck = aip.getLieferantenArtikelPreis().getPreis().multiply(menge);
                        }
                        aDto.setPreisProStueck(preisProStueck);
                        aDto.setGesamtpreis(ermittleGesamtpreis(aip, menge));
                        aDto.setHinzugefuegtAm(aip.getHinzugefuegtAm());
                        aDto.setBestellt(aip.isBestellt());
                        aDto.setAusLager(aip.isAusLager());
                        aDto.setBestelltAm(aip.getBestelltAm());
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

    /**
     * Die Menge der Position in ihrer Verrechnungseinheit - Stueck, Meter
     * oder Kilogramm, je nachdem, wie der Artikel abgerechnet wird.
     */
    private BigDecimal ermittleMenge(ArtikelInProjekt aip) {
        if (aip.getArtikel() == null || aip.getArtikel().getVerrechnungseinheit() == null) {
            return BigDecimal.ZERO;
        }
        return switch (aip.getArtikel().getVerrechnungseinheit()) {
            case STUECK -> aip.getStueckzahl() != null
                    ? BigDecimal.valueOf(aip.getStueckzahl())
                    : BigDecimal.ZERO;
            case LAUFENDE_METER, QUADRATMETER -> aip.getMeter() != null
                    ? aip.getMeter()
                    : BigDecimal.ZERO;
            case KILOGRAMM -> aip.getKilogramm() != null
                    ? aip.getKilogramm()
                    : BigDecimal.ZERO;
        };
    }

    /**
     * Was die Position insgesamt kostet.
     *
     * <p>Der Knackpunkt ist {@code preisProStueck}: Das Feld traegt je nach
     * Herkunft zwei verschiedene Bedeutungen.
     *
     * <ul>
     *   <li>Hakt der Einkaeufer eine Bestellung ab, rechnet
     *       {@code BestellungService.setBestellt} die Summe aus und schreibt
     *       sie dort hinein - der Wert ist dann bereits der Gesamtpreis und
     *       darf nicht noch einmal mit der Menge multipliziert werden.</li>
     *   <li>Ueber {@code fuegeArtikelMaterialkosten} angelegte Positionen
     *       (Lagerware und offene Bedarfe) tragen dort den Preis je Einheit.</li>
     * </ul>
     *
     * <p>Unterschieden wird an {@code bestellt}/{@code ausLager}, nicht am
     * haengenden Preisstand: V339 hat den Preisbezug nachtraeglich auch an
     * laengst abgehakte Bestellungen zurueckgehaengt, deren
     * {@code preisProStueck} trotzdem die eingefrorene Summe blieb.
     */
    private BigDecimal ermittleGesamtpreis(ArtikelInProjekt aip, BigDecimal menge) {
        // Abgehakte Bestellung - die Summe steht fest. Lagerware laeuft nie
        // ueber diesen Weg, sie ist nur deshalb "bestellt", damit sie aus der
        // offenen Bestellliste faellt.
        if (aip.isBestellt() && !aip.isAusLager()) {
            return aip.getPreisProStueck();
        }

        // Sonst je Einheit: Der aktuelle Preisstand hat Vorrang, weil er auch
        // Preisaenderungen nach dem Anlegen abbildet. Faellt er weg - etwa
        // weil er beim Speichern nicht mehr als aktuell galt - bleibt der
        // beim Anlegen festgehaltene Einzelpreis.
        BigDecimal jeEinheit = aip.getLieferantenArtikelPreis() != null
                && aip.getLieferantenArtikelPreis().getPreis() != null
                ? aip.getLieferantenArtikelPreis().getPreis()
                : aip.getPreisProStueck();
        return jeEinheit != null ? jeEinheit.multiply(menge) : null;
    }

}
