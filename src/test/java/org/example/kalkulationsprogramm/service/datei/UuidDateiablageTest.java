package org.example.kalkulationsprogramm.service.datei;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UuidDateiablageTest {

    @TempDir
    Path tmp;

    private UuidDateiablage ablage() {
        return new UuidDateiablage(tmp.toString(), "probe", ".pdf");
    }

    @Test
    @DisplayName("Datenstrom speichern, finden, löschen – im eigenen Unterordner mit UUID-Namen")
    void datenstrom() throws Exception {
        UuidDateiablage ablage = ablage();
        String name = ablage.speichere(new ByteArrayInputStream(new byte[] { 37, 80, 68, 70 }));

        assertThat(UuidDateiablage.istGueltigerName(name, ".pdf")).isTrue();
        Path pfad = ablage.pfad(name);
        assertThat(pfad).startsWith(tmp.resolve("probe").toAbsolutePath());
        assertThat(Files.readAllBytes(pfad)).containsExactly(37, 80, 68, 70);

        ablage.loesche(name);
        assertThat(pfad).doesNotExist();
    }

    @Test
    @DisplayName("Fremde Endung oder Traversal-Namen werden abgelehnt, Löschen ignoriert sie still")
    void fremdeNamen() {
        UuidDateiablage ablage = ablage();
        String wav = "0a4f113b-0000-0000-0000-000000000000.wav";
        assertThat(UuidDateiablage.istGueltigerName(wav, ".pdf")).isFalse();
        assertThatThrownBy(() -> ablage.pfad("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        ablage.loesche("../../etc/passwd");
        ablage.loesche(null);
    }

    @Test
    @DisplayName("Ablage mit Alt-Endung: neue Dateien bekommen die neue Endung, alte bleiben lesbar und löschbar")
    void altEndung() throws Exception {
        UuidDateiablage ablage = new UuidDateiablage(tmp.toString(), "probe", ".bin", ".pdf");
        String neu = ablage.speichere(new byte[] { 1, 2 });
        assertThat(neu).endsWith(".bin");
        assertThat(UuidDateiablage.istGueltigerName(neu, ".bin")).isTrue();

        String alt = "0a4f113b-0000-0000-0000-000000000000.pdf";
        Path altPfad = Files.write(tmp.resolve("probe").resolve(alt), new byte[] { 3 });
        assertThat(ablage.pfad(alt)).isEqualTo(altPfad.toAbsolutePath().normalize());
        ablage.loesche(alt);
        assertThat(altPfad).doesNotExist();

        String fremd = "0a4f113b-0000-0000-0000-000000000000.wav";
        assertThatThrownBy(() -> ablage.pfad(fremd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ablage.pfad("../0a4f113b-0000-0000-0000-000000000000.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        ablage.loesche(fremd);
    }

    @Test
    @DisplayName("Lesefehler beim Speichern wird als UncheckedIOException weitergereicht")
    void lesefehler() {
        InputStream kaputt = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("kaputt");
            }
        };
        assertThatThrownBy(() -> ablage().speichere(kaputt)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("Kann der Unterordner nicht angelegt werden, scheitert auch das Speichern von Bytes verständlich")
    void ordnerNichtAnlegbar() throws Exception {
        Files.writeString(tmp.resolve("probe"), "ich bin eine Datei, kein Ordner");
        assertThatThrownBy(() -> ablage().speichere(new byte[] { 1 })).isInstanceOf(UncheckedIOException.class);
    }
}
