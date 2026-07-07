package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsSmbShareRunnerTest {

    @Test
    void shareScript_enthaeltPfadUndShareNameInEinfachenQuotes() {
        String script = WindowsSmbShareRunner.baueShareScript(Path.of("C:\\Test Ordner\\Zeichnungen"), "ERP-Dateien");
        assertThat(script).contains("New-SmbShare");
        assertThat(script).contains("'C:\\Test Ordner\\Zeichnungen'");
        assertThat(script).contains("'ERP-Dateien'");
        // Authentifizierte Benutzer sprachneutral über SID S-1-5-11
        assertThat(script).contains("S-1-5-11");
    }

    @Test
    void shareScript_lehntEinfacheAnfuehrungszeichenImPfadAb() {
        // Single-Quote im Pfad könnte aus dem PS-String ausbrechen -> ablehnen
        assertThatThrownBy(() ->
                WindowsSmbShareRunner.baueShareScript(Path.of("C:\\Böse'Injection"), "ERP-Dateien"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
