package org.example.kalkulationsprogramm.service.ki;

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Vergleicht die eingestellten Modellnamen mit der Modellliste eines Anbieters. */
final class KiModellPruefung {

    private KiModellPruefung() {
    }

    /** Alle Namen und Aliasse der Modelle. */
    static Set<String> namen(List<KiModellInfo> modelle) {
        Set<String> namen = new HashSet<>();
        for (KiModellInfo modell : modelle) {
            namen.add(modell.modell());
            namen.addAll(modell.aliase());
        }
        return namen;
    }

    /**
     * @param anbieterName für die Meldung
     * @param bekannt      Modellnamen (und Aliasse), die der Anbieter meldet
     * @param eingestellt  Modellnamen, die das Programm nutzen wird
     */
    static TestResult ergebnis(String anbieterName, Set<String> bekannt, Collection<String> eingestellt) {
        Set<String> fehlend = new LinkedHashSet<>();
        for (String modell : eingestellt) {
            if (modell != null && !modell.isBlank() && !bekannt.contains(modell)) {
                fehlend.add(modell);
            }
        }
        if (fehlend.isEmpty()) {
            return TestResult.success("Schlüssel gültig. Die Verbindung zu " + anbieterName
                    + " steht, alle eingestellten Modelle sind verfügbar.");
        }
        return TestResult.failure("Der Schlüssel ist gültig, aber diese Modelle kennt " + anbieterName
                + " nicht: " + String.join(", ", fehlend) + ". Bitte die Schreibweise prüfen.");
    }
}
