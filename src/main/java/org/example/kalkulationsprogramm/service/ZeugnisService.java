package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.ZeugnisTyp;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class ZeugnisService {

    private final KategorieRepository kategorieRepository;

    /**
     * Bestimmt den Zeugnis-Default für eine Kombination aus Kategorie + EXC-Klasse.
     * Läuft die Kategorie-Hierarchie hoch, bis ein Wert gefunden wird.
     */
    public Optional<ZeugnisTyp> bestimmeDefault(Integer kategorieId, String excKlasse) {
        if (kategorieId == null || excKlasse == null) return Optional.empty();
        Kategorie kat = kategorieRepository.findById(kategorieId).orElse(null);
        while (kat != null) {
            ZeugnisTyp z = getZeugnisForExc(kat, excKlasse);
            if (z != null) return Optional.of(z);
            kat = kat.getParentKategorie();
        }
        return Optional.empty();
    }

    private ZeugnisTyp getZeugnisForExc(Kategorie kat, String excKlasse) {
        return switch (excKlasse) {
            case "EXC_1" -> kat.getZeugnisExc1();
            case "EXC_2" -> kat.getZeugnisExc2();
            case "EXC_3" -> kat.getZeugnisExc3();
            case "EXC_4" -> kat.getZeugnisExc4();
            default -> null;
        };
    }

    public String beschreibung(ZeugnisTyp typ) {
        if (typ == null) return null;
        return switch (typ) {
            case WZ_2_1 -> "Werkszeugnis 2.1 nach DIN EN 10204";
            case WZ_2_2 -> "Werkszeugnis 2.2 nach DIN EN 10204";
            case APZ_3_1 -> "Abnahmeprüfzeugnis 3.1 nach DIN EN 10204";
            case APZ_3_2 -> "Abnahmeprüfzeugnis 3.2 nach DIN EN 10204";
            case CE_KONFORMITAET -> "CE-Kennzeichnung / Konformitätserklärung";
        };
    }
}
