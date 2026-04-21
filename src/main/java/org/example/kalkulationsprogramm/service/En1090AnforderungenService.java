package org.example.kalkulationsprogramm.service;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.En1090Anforderungen;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Single Source of Truth fuer die Frage: Faellt ein Projekt unter den vollen
 * EN-1090-2-Ablauf? Alle EN-1090-Folgemodule (Wareneingang, WPS, SAP-Freigabe,
 * Schweisser-Qualifikations-Check, ZfP-Pruefplan, WPK-Dashboard) fragen hier
 * ab — keine verstreuten {@code "EXC_2".equals(...)}-Checks mehr im Code.
 *
 * <p>Die Unterscheidung zwischen EXC 1 und EXC 2 bleibt in der DB als Info fuer
 * interne Auswertungen erhalten, triggert aber denselben Workflow: ist ein
 * Projekt EN-1090-pflichtig, gilt der komplette Ablauf nach DIN EN 1090-2.
 * EXC 3 und EXC 4 sind in diesem ERP entfernt worden und werden wie
 * {@code null} behandelt.
 */
@Service
@RequiredArgsConstructor
public class En1090AnforderungenService {

    private static final Set<String> EN1090_KLASSEN = Set.of("EXC_1", "EXC_2");

    private final ProjektRepository projektRepository;

    /**
     * Liefert die Anforderungen fuer ein konkretes Projekt.
     *
     * @throws IllegalArgumentException wenn kein Projekt mit der ID existiert.
     */
    public En1090Anforderungen fuerProjekt(Long projektId) {
        if (projektId == null) {
            return En1090Anforderungen.KEINE;
        }
        Projekt projekt = projektRepository.findById(projektId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Projekt mit ID " + projektId + " nicht gefunden."));
        return fuerExcKlasse(projekt.getExcKlasse());
    }

    /**
     * Liefert die Anforderungen fuer eine gegebene EXC-Klasse als String.
     * Null, leer, unbekannt, EXC_3 und EXC_4 ergeben {@link En1090Anforderungen#KEINE}.
     */
    public En1090Anforderungen fuerExcKlasse(String excKlasse) {
        if (excKlasse == null) {
            return En1090Anforderungen.KEINE;
        }
        String normiert = excKlasse.trim();
        if (EN1090_KLASSEN.contains(normiert)) {
            return En1090Anforderungen.PFLICHTIG;
        }
        return En1090Anforderungen.KEINE;
    }
}
