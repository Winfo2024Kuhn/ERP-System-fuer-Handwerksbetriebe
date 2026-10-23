package org.example.kalkulationsprogramm.service.openfile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Schreibt ein kleines, unkomprimiertes Zip mit Unix-Dateirechten.
 *
 * <p>{@code java.util.zip} kann keine Dateirechte setzen. Eine {@code .command}-Datei braucht
 * aber das Ausführungsrecht, sonst startet sie auf dem Mac nicht per Doppelklick. Der
 * Mac-Entpacker übernimmt das Recht aus den „externen Attributen“ des Eintrags.</p>
 */
final class UnixZip {

    /** Ausführbare Datei: Dateityp „regulär“ (0100000) plus rwxr-xr-x. */
    static final int AUSFUEHRBAR = 0100755;
    /** Normale Datei: rw-r--r--. */
    static final int NORMAL = 0100644;

    private static final int VERSION = 20;
    /** „Erstellt unter Unix“ (3) im oberen Byte, damit die Rechte gelten. */
    private static final int ERSTELLT_UNTER_UNIX = (3 << 8) | VERSION;
    /** Bit 11: Dateinamen in UTF-8. */
    private static final int FLAG_UTF8 = 0x0800;
    /** 01.01.2026, 00:00 – fester Zeitstempel, damit gleiche Inhalte gleiche Bytes ergeben. */
    private static final int DOS_DATUM = ((2026 - 1980) << 9) | (1 << 5) | 1;

    private record Eintrag(byte[] name, byte[] inhalt, int rechte, long crc, int offset) {
    }

    private final ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
    private final List<Eintrag> eintraege = new ArrayList<>();

    UnixZip datei(String name, byte[] inhalt, int rechte) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(inhalt);
        Eintrag eintrag = new Eintrag(nameBytes, inhalt, rechte, crc.getValue(), ausgabe.size());
        int32(0x04034b50);
        int16(VERSION);
        int16(FLAG_UTF8);
        int16(0); // gespeichert, nicht komprimiert
        int16(0);
        int16(DOS_DATUM);
        int32(eintrag.crc());
        int32(inhalt.length);
        int32(inhalt.length);
        int16(nameBytes.length);
        int16(0);
        ausgabe.writeBytes(nameBytes);
        ausgabe.writeBytes(inhalt);
        eintraege.add(eintrag);
        return this;
    }

    byte[] fertig() {
        int verzeichnisStart = ausgabe.size();
        for (Eintrag e : eintraege) {
            int32(0x02014b50);
            int16(ERSTELLT_UNTER_UNIX);
            int16(VERSION);
            int16(FLAG_UTF8);
            int16(0);
            int16(0);
            int16(DOS_DATUM);
            int32(e.crc());
            int32(e.inhalt().length);
            int32(e.inhalt().length);
            int16(e.name().length);
            int16(0); // Extra
            int16(0); // Kommentar
            int16(0); // Datenträger
            int16(0); // interne Attribute
            int32((long) e.rechte() << 16);
            int32(e.offset());
            ausgabe.writeBytes(e.name());
        }
        int verzeichnisGroesse = ausgabe.size() - verzeichnisStart;
        int32(0x06054b50);
        int16(0);
        int16(0);
        int16(eintraege.size());
        int16(eintraege.size());
        int32(verzeichnisGroesse);
        int32(verzeichnisStart);
        int16(0);
        return ausgabe.toByteArray();
    }

    private void int16(int wert) {
        ausgabe.write(wert & 0xff);
        ausgabe.write((wert >>> 8) & 0xff);
    }

    private void int32(long wert) {
        int16((int) (wert & 0xffff));
        int16((int) ((wert >>> 16) & 0xffff));
    }
}
