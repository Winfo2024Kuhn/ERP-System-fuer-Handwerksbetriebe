package org.example.kalkulationsprogramm.service.openfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

class UnixZipTest {

    @Test
    void zipIstLesbarUndEnthaeltDieDateien() throws IOException {
        byte[] zip = new UnixZip()
                .datei("ERP-Dateien-oeffnen.command", "#!/bin/bash\necho hallo\n".getBytes(StandardCharsets.UTF_8),
                        UnixZip.AUSFUEHRBAR)
                .datei("liesmich.txt", "Max Mustermann".getBytes(StandardCharsets.UTF_8), UnixZip.NORMAL)
                .fertig();

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry erste = in.getNextEntry();
            assertThat(erste.getName()).isEqualTo("ERP-Dateien-oeffnen.command");
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("#!/bin/bash\necho hallo\n");
            ZipEntry zweite = in.getNextEntry();
            assertThat(zweite.getName()).isEqualTo("liesmich.txt");
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("Max Mustermann");
            assertThat(in.getNextEntry()).isNull();
        }
    }

    @Test
    void zentralverzeichnisTraegtUnixRechte() {
        byte[] zip = new UnixZip()
                .datei("a.command", new byte[] {1, 2, 3}, UnixZip.AUSFUEHRBAR)
                .datei("b.txt", new byte[0], UnixZip.NORMAL)
                .fertig();
        ByteBuffer puffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);

        int ende = zip.length - 22;
        assertThat(puffer.getInt(ende)).isEqualTo(0x06054b50);
        assertThat(puffer.getShort(ende + 10)).isEqualTo((short) 2);
        int verzeichnis = puffer.getInt(ende + 16);

        assertThat(puffer.getInt(verzeichnis)).isEqualTo(0x02014b50);
        assertThat(puffer.getShort(verzeichnis + 4) & 0xffff).isEqualTo(0x0314); // erstellt unter Unix
        assertThat(puffer.getInt(verzeichnis + 38) >>> 16).isEqualTo(UnixZip.AUSFUEHRBAR);

        int zweiter = verzeichnis + 46 + "a.command".length();
        assertThat(puffer.getInt(zweiter)).isEqualTo(0x02014b50);
        assertThat(puffer.getInt(zweiter + 38) >>> 16).isEqualTo(UnixZip.NORMAL);
    }

    @Test
    void gleicheInhalteErgebenGleicheBytes() {
        byte[] eins = new UnixZip().datei("x", "y".getBytes(StandardCharsets.UTF_8), UnixZip.NORMAL).fertig();
        byte[] zwei = new UnixZip().datei("x", "y".getBytes(StandardCharsets.UTF_8), UnixZip.NORMAL).fertig();
        assertThat(eins).isEqualTo(zwei);
    }
}
