package org.example.kalkulationsprogramm.service.dokumentstand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.AusgangsDokumentStand;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.dto.AusgangsDokumentStand.StandEintragDto;
import org.example.kalkulationsprogramm.repository.AusgangsDokumentStandZeile;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Macht aus Ständen bzw. Listenzeilen {@link StandEintragDto}s: Benutzernamen aller Einträge in
 * einer Abfrage, gespeicherte JSON-Spalten als Listen, „PDF vorhanden“ über die {@link DokumentStandPdfAblage}.
 */
@Component
@RequiredArgsConstructor
public class DokumentStandEintragMapper {

    private final FrontendUserProfileRepository benutzerRepository;
    private final DokumentStandPdfAblage pdfAblage;

    /** Die Ablage, über die auch das PDF eines Stands ausgeliefert wird. */
    DokumentStandPdfAblage pdfAblage() {
        return pdfAblage;
    }

    /** Eine Seite der Liste; {@code aktuellerAbdruck} ist der Fingerabdruck des aktuellen Dokumentinhalts. */
    public List<StandEintragDto> eintraege(Iterable<AusgangsDokumentStandZeile> zeilen, String aktuellerAbdruck) {
        Set<Long> ids = new LinkedHashSet<>();
        zeilen.forEach(z -> sammle(ids, z.festgehaltenVonId(), z.bearbeitetVonIds()));
        Map<Long, String> namen = namen(ids);
        List<StandEintragDto> liste = new ArrayList<>();
        for (AusgangsDokumentStandZeile z : zeilen) {
            liste.add(new StandEintragDto(z.id(), z.nummer(), z.art(), z.festgehaltenAm(),
                    namen.get(z.festgehaltenVonId()), namenListe(z.bearbeitetVonIds(), namen), z.betragNetto(),
                    DokumentStandJson.leseSaetze(z.aenderungen()), z.wiederhergestelltAusNummer(),
                    aktuellerAbdruck.equals(z.inhaltFingerabdruck()), pdfAblage.finde(z.pdfDateiname()).isPresent()));
        }
        return liste;
    }

    /** Ein einzelner Stand; ob er dem aktuellen Inhalt gleicht, entscheidet der Aufrufer. */
    public StandEintragDto eintrag(AusgangsDokumentStand s, boolean aktuell) {
        Set<Long> ids = new LinkedHashSet<>();
        sammle(ids, s.getFestgehaltenVonId(), s.getBearbeitetVonIds());
        Map<Long, String> namen = namen(ids);
        return new StandEintragDto(s.getId(), s.getNummer(), s.getArt(), s.getFestgehaltenAm(),
                namen.get(s.getFestgehaltenVonId()), namenListe(s.getBearbeitetVonIds(), namen), s.getBetragNetto(),
                DokumentStandJson.leseSaetze(s.getAenderungen()), s.getWiederhergestelltAusNummer(), aktuell,
                pdfAblage.finde(s.getPdfDateiname()).isPresent());
    }

    private static void sammle(Set<Long> ids, Long festgehaltenVon, String bearbeitetVonIds) {
        if (festgehaltenVon != null) {
            ids.add(festgehaltenVon);
        }
        ids.addAll(DokumentStandJson.leseIds(bearbeitetVonIds));
    }

    private Map<Long, String> namen(Set<Long> ids) {
        Map<Long, String> namen = new HashMap<>();
        if (ids.isEmpty()) {
            return namen;
        }
        for (FrontendUserProfile p : benutzerRepository.findAllById(ids)) {
            namen.put(p.getId(), p.getDisplayName());
        }
        return namen;
    }

    private static List<String> namenListe(String idsJson, Map<Long, String> namen) {
        List<String> liste = new ArrayList<>();
        for (Long id : DokumentStandJson.leseIds(idsJson)) {
            String name = namen.get(id);
            if (name != null && !name.isBlank()) {
                liste.add(name);
            }
        }
        return liste;
    }
}
