package org.example.kalkulationsprogramm.service.telefon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SprachnachrichtDateiablageTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Speichern, finden, löschen – Dateiname vom ERP erzeugt")
    void lebenszyklus() throws Exception {
        SprachnachrichtDateiablage ablage = new SprachnachrichtDateiablage(tmp.toString());
        String name = ablage.speichere(new byte[]{1, 2, 3});

        assertThat(SprachnachrichtDateiablage.istGueltigerName(name)).isTrue();
        Path pfad = ablage.pfad(name);
        assertThat(pfad).startsWith(tmp.resolve("sprachnachrichten").toAbsolutePath());
        assertThat(Files.readAllBytes(pfad)).containsExactly(1, 2, 3);

        ablage.loesche(name);
        assertThat(pfad).doesNotExist();
        ablage.loesche(name);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../../etc/passwd", "../x.wav", "0a4f113b-0000-0000-0000-000000000000.exe",
            "0A4F113B-0000-0000-0000-000000000000.wav", "0a4f113b00000000000000000000000000000.wav",
            "0a4f113b-0000-0000-0000-00000000000/.wav", "virus.bat"})
    @DisplayName("Fremde oder bösartige Dateinamen werden abgelehnt (Path Traversal)")
    void ungueltigeNamen(String name) {
        SprachnachrichtDateiablage ablage = new SprachnachrichtDateiablage(tmp.toString());
        assertThat(SprachnachrichtDateiablage.istGueltigerName(name)).isFalse();
        assertThatThrownBy(() -> ablage.pfad(name)).isInstanceOf(IllegalArgumentException.class);
        ablage.loesche(name);
    }
}
