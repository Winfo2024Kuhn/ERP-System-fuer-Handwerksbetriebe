package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsSmbShareRunnerTest {

    @Test
    void shareScript_uebergibtPfadUndShareNameNurBase64Kodiert() {
        String pfad = "C:\\Test Ordner\\Zeichnungen";
        String script = WindowsSmbShareRunner.baueShareScript(Path.of(pfad), "ERP-Dateien");
        assertThat(script).contains("New-SmbShare");
        // Werte gelangen NUR Base64-kodiert ins Skript – Klartext-Interpolation
        // in doppelt gequotete PS-Strings wäre eine Injection-Fläche ($(), Backticks)
        assertThat(script).doesNotContain(pfad);
        assertThat(script).contains(java.util.Base64.getEncoder()
                .encodeToString(pfad.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(script).contains(java.util.Base64.getEncoder()
                .encodeToString("ERP-Dateien".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Authentifizierte Benutzer sprachneutral über SID S-1-5-11
        assertThat(script).contains("S-1-5-11");
    }

    @Test
    void shareScript_interpoliertKeinePowershellMetazeichenAusDemPfad() {
        // $() und Backticks im Ordnernamen dürfen NIE als Klartext im Skript landen
        String boese = "C:\\Zeichnungen$(Start-Process calc)";
        String script = WindowsSmbShareRunner.baueShareScript(Path.of(boese), "ERP-Dateien");
        assertThat(script).doesNotContain("$(Start-Process");
    }

    @Test
    void shareScript_lehntEinfacheAnfuehrungszeichenImPfadAb() {
        // Single-Quote im Pfad könnte aus dem PS-String ausbrechen -> ablehnen
        assertThatThrownBy(() ->
                WindowsSmbShareRunner.baueShareScript(Path.of("C:\\Böse'Injection"), "ERP-Dateien"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
