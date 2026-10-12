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
import java.math.RoundingMode;
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

    /**
     * Hakt eine Bestellung ab oder nimmt das zurueck.
     *
     * <p>{@code preisProStueck} wechselt dabei seine Bedeutung (siehe
     * {@link org.example.kalkulationsprogramm.mapper.ProjektMapper}): Beim
     * Abhaken wird die Summe eingefroren, beim Zuruecknehmen wieder auf den
     * Preis je Einheit zurueckgerechnet - sonst multipliziert die Anzeige die
     * eingefrorene Summe ein zweites Mal mit der Menge. Lagerware traegt immer
     * den Preis je Einheit und wird nicht umgerechnet.
     */
    @Transactional
    public void setBestellt(Long id, boolean bestellt) {
        ArtikelInProjekt aip = artikelInProjektRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Artikel nicht gefunden"));
        if (aip.isBestellt() == bestellt) {
            // Doppelklick oder zweiter Tab: nichts umzuschalten, und der Preis
            // darf nicht ein zweites Mal umgerechnet werden.
            return;
        }
        aip.setBestellt(bestellt);
        aip.setBestelltAm(bestellt ? LocalDate.now() : null);
        if (!aip.isAusLager()) {
            if (bestellt) {
                friereGesamtpreisEin(aip);
            } else {
                rechneAufEinzelpreisZurueck(aip);
            }
        }
        artikelInProjektRepository.save(aip);
    }

    private void friereGesamtpreisEin(ArtikelInProjekt aip) {
        // Wie im ProjektMapper: Der haengende Preisstand hat Vorrang, sonst
        // gilt der festgehaltene Preis je Einheit.
        BigDecimal jeEinheit = aip.getLieferantenArtikelPreis() != null
                && aip.getLieferantenArtikelPreis().getPreis() != null
                ? aip.getLieferantenArtikelPreis().getPreis()
                : aip.getPreisProStueck();
        aip.setPreisProStueck(jeEinheit != null ? jeEinheit.multiply(mengeFuerPreis(aip)) : null);
        aip.setLieferantenArtikelPreis(null);
    }

    private void rechneAufEinzelpreisZurueck(ArtikelInProjekt aip) {
        BigDecimal summe = aip.getPreisProStueck();
        BigDecimal menge = mengeFuerPreis(aip);
        // Bei Menge 0 ist die Summe 0 - daraus laesst sich kein Einzelpreis
        // zurueckgewinnen, 0 x 0 bleibt aber richtig.
        if (summe == null || menge.signum() == 0) {
            return;
        }
        aip.setPreisProStueck(summe.divide(menge, 4, RoundingMode.HALF_UP));
    }

    /**
     * Die Menge, mit der der Preis je Einheit zur Summe wird. Fehlt die
     * Verrechnungseinheit oder die passende Menge, wurde schon immer nicht
     * multipliziert - daher 1.
     */
    private BigDecimal mengeFuerPreis(ArtikelInProjekt aip) {
        if (aip.getArtikel() == null || aip.getArtikel().getVerrechnungseinheit() == null) {
            return BigDecimal.ONE;
        }
        Verrechnungseinheit ve = aip.getArtikel().getVerrechnungseinheit();
        BigDecimal menge = switch (ve) {
            case KILOGRAMM -> aip.getKilogramm();
            case LAUFENDE_METER, QUADRATMETER -> aip.getMeter();
            case STUECK -> aip.getStueckzahl() != null ? BigDecimal.valueOf(aip.getStueckzahl()) : null;
        };
        return menge != null ? menge : BigDecimal.ONE;
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
