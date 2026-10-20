package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Bundesland;
import org.example.kalkulationsprogramm.dto.Feiertag.FeiertageVorschauDto;
import org.example.kalkulationsprogramm.util.PlzBundesland;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hilft beim Einrichten der Feiertage: Aus der Postleitzahl der Firmenadresse wird das
 * Bundesland vorgeschlagen, dazu die Feiertage, die dann gelten, und Hinweise zu
 * Feiertagen, die nur in einem Teil des Landes gelten (Issue #222).
 */
@Service
@RequiredArgsConstructor
public class FeiertageVorschauService {

    static final String HINWEIS_PLZ = "Der Vorschlag richtet sich nach der Postleitzahl. "
            + "Liegt euer Betrieb nahe einer Landesgrenze, bitte kurz prüfen.";
    static final String HINWEIS_MARIAE_HIMMELFAHRT = "Mariä Himmelfahrt (15. August) gilt in Bayern nur in Gemeinden "
            + "mit überwiegend katholischer Bevölkerung. Gilt der Tag bei euch nicht, schaltet ihr ihn auf der "
            + "Feiertage-Seite ab.";
    static final String HINWEIS_AUGSBURG = "In Augsburg ist am 8. August zusätzlich das Friedensfest. "
            + "Ihr legt es auf der Feiertage-Seite als eigenen Feiertag an.";
    static final String HINWEIS_FRONLEICHNAM_SN = "Fronleichnam gilt in Sachsen nur in einigen Gemeinden um Bautzen. "
            + "Gehört ihr dazu, legt ihr den Tag auf der Feiertage-Seite als eigenen Feiertag an.";
    static final String HINWEIS_FRONLEICHNAM_TH = "Fronleichnam gilt in Thüringen nur in einigen Gemeinden im Eichsfeld "
            + "und um Dingelstädt. Gehört ihr dazu, legt ihr den Tag auf der Feiertage-Seite als eigenen Feiertag an.";

    private final FeiertagService feiertagService;

    /**
     * @param plz        Postleitzahl der Firmenadresse (darf leer sein)
     * @param bundesland gewünschtes Bundesland; leer = Vorschlag aus der PLZ, sonst das eingestellte
     * @param jahr       Jahr der Feiertage; null = laufendes Jahr
     */
    public FeiertageVorschauDto vorschau(String plz, String bundesland, Integer jahr) {
        int zielJahr = jahr != null ? jahr : LocalDate.now().getYear();
        if (zielJahr < 2000 || zielJahr > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein gültiges Jahr angeben.");
        }
        Optional<Bundesland> vorgeschlagen = PlzBundesland.vorschlag(plz);
        Bundesland land;
        if (bundesland != null && !bundesland.isBlank()) {
            land = Bundesland.ausKuerzel(bundesland).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Bitte ein gültiges Bundesland wählen."));
        } else {
            land = vorgeschlagen.orElseGet(() -> Bundesland.ausKuerzel(feiertagService.aktuellesBundesland())
                    .orElse(Bundesland.STANDARD));
        }

        List<FeiertageVorschauDto.Eintrag> feiertage = feiertagService.vorschau(zielJahr, land).stream()
                .map(f -> new FeiertageVorschauDto.Eintrag(f.getDatum(), f.getBezeichnung()))
                .toList();

        return new FeiertageVorschauDto(vorgeschlagen.map(Enum::name).orElse(null), land.name(),
                land.getAnzeigename(), zielJahr, feiertage, hinweise(plz, land, vorgeschlagen.isPresent()));
    }

    private static List<String> hinweise(String plz, Bundesland land, boolean ausPlz) {
        List<String> hinweise = new ArrayList<>();
        if (ausPlz) {
            hinweise.add(HINWEIS_PLZ);
        }
        switch (land) {
            case BY -> {
                hinweise.add(HINWEIS_MARIAE_HIMMELFAHRT);
                if (PlzBundesland.istAugsburg(plz)) {
                    hinweise.add(HINWEIS_AUGSBURG);
                }
            }
            case SN -> hinweise.add(HINWEIS_FRONLEICHNAM_SN);
            case TH -> hinweise.add(HINWEIS_FRONLEICHNAM_TH);
            default -> {
                // keine regionalen Besonderheiten
            }
        }
        return hinweise;
    }
}
