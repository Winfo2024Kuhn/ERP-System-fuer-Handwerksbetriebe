package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.junit.jupiter.api.Test;

/** Abgleich der eingestellten Modellnamen mit der Liste des Anbieters. */
class KiModellPruefungTest {

    @Test
    void namenEnthaltenModellnamenUndAliasse() {
        List<KiModellInfo> modelle = List.of(
                new KiModellInfo("mistral-medium-latest", "Mistral Medium", true, false, true, List.of("mistral-medium-2508")),
                new KiModellInfo("voxtral-small-latest", null, null, true, false, null));

        assertThat(KiModellPruefung.namen(modelle))
                .containsExactlyInAnyOrder("mistral-medium-latest", "mistral-medium-2508", "voxtral-small-latest");
    }

    @Test
    void alleEingestelltenModelleBekanntErgibtErfolg() {
        Set<String> bekannt = Set.of("a-schnell", "a-standard");

        TestResult ergebnis = KiModellPruefung.ergebnis("Mistral", bekannt, List.of("a-schnell", "a-standard"));

        assertThat(ergebnis.success()).isTrue();
        assertThat(ergebnis.message()).contains("Mistral").contains("alle eingestellten Modelle sind verfügbar");
    }

    @Test
    void leereUndFehlendeEinstellungenZaehlenNichtAlsUnbekannt() {
        TestResult ergebnis = KiModellPruefung.ergebnis("Google Gemini", Set.of("g"), Arrays.asList("g", "", "  ", null));

        assertThat(ergebnis.success()).isTrue();
    }

    @Test
    void unbekannteModelleWerdenInReihenfolgeOhneDoppelteGenannt() {
        Set<String> bekannt = Set.of("mistral-small-latest");

        TestResult ergebnis = KiModellPruefung.ergebnis("Mistral", bekannt,
                List.of("mistral-small-latest", "mistral-gross", "mistral-medium-xy", "mistral-gross"));

        assertThat(ergebnis.success()).isFalse();
        assertThat(ergebnis.message())
                .contains("Der Schlüssel ist gültig")
                .contains("mistral-gross, mistral-medium-xy")
                .contains("Schreibweise prüfen");
    }
}
