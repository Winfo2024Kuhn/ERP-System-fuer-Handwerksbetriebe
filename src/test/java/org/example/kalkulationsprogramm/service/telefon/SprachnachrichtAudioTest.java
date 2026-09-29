package org.example.kalkulationsprogramm.service.telefon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SprachnachrichtAudioTest {

    /** Baut ein WAV mit beliebigem Format, optional mit Zusatz-Chunk vor "data". */
    static byte[] wav(int format, int kanaele, int rate, int bitsProSample, byte[] daten, boolean mitListChunk) {
        int byteRate = rate * kanaele * bitsProSample / 8;
        byte[] liste = mitListChunk ? "LIST\u0003\u0000\u0000\u0000abc\u0000".getBytes(StandardCharsets.ISO_8859_1) : new byte[0];
        ByteBuffer b = ByteBuffer.allocate(12 + 24 + liste.length + 8 + daten.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(b.capacity() - 8).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) format).putShort((short) kanaele)
                .putInt(rate).putInt(byteRate).putShort((short) (kanaele * bitsProSample / 8)).putShort((short) bitsProSample);
        b.put(liste);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(daten.length).put(daten);
        return b.array();
    }

    @Test
    @DisplayName("PCM-WAV bleibt unverändert, Dauer aus der Datei")
    void pcmUnveraendert() {
        byte[] original = wav(1, 1, 8000, 16, new byte[16000 * 3], false);
        SprachnachrichtAudio.Ergebnis e = SprachnachrichtAudio.aufbereiten(original);
        assertThat(e.wav()).isSameAs(original);
        assertThat(e.dauerSekunden()).isEqualTo(3);
        assertThat(e.warRoh()).isFalse();
    }

    @Test
    @DisplayName("A-law-WAV wird nach 16-bit-PCM gewandelt, Zusatz-Chunks werden übersprungen")
    void alawZuPcm() {
        byte[] alaw = new byte[8000 * 2];
        java.util.Arrays.fill(alaw, (byte) 0xD5);
        SprachnachrichtAudio.Ergebnis e = SprachnachrichtAudio.aufbereiten(wav(6, 1, 8000, 8, alaw, true));
        assertThat(new String(e.wav(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        ByteBuffer b = ByteBuffer.wrap(e.wav()).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(b.getShort(20)).isEqualTo((short) 1);
        assertThat(b.getShort(34)).isEqualTo((short) 16);
        assertThat(b.getInt(40)).isEqualTo(alaw.length * 2);
        assertThat(e.dauerSekunden()).isEqualTo(2);
        assertThat(e.warRoh()).isFalse();
    }

    @Test
    @DisplayName("µ-law-WAV wird ebenfalls gewandelt")
    void mulawZuPcm() {
        SprachnachrichtAudio.Ergebnis e = SprachnachrichtAudio.aufbereiten(wav(7, 1, 8000, 8, new byte[8000], false));
        assertThat(e.dauerSekunden()).isEqualTo(1);
        assertThat(ByteBuffer.wrap(e.wav()).order(ByteOrder.LITTLE_ENDIAN).getShort(20)).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("Rohdaten ohne WAV-Kopf werden als A-law 8 kHz gelesen")
    void rohdaten() {
        SprachnachrichtAudio.Ergebnis e = SprachnachrichtAudio.aufbereiten(new byte[8000 * 4]);
        assertThat(e.warRoh()).isTrue();
        assertThat(e.dauerSekunden()).isEqualTo(4);
        assertThat(new String(e.wav(), 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WAVE");
    }

    @Test
    @DisplayName("G.711-Dekodierung nach ITU-Referenzwerten")
    void g711Werte() {
        assertThat(SprachnachrichtAudio.alaw((byte) 0xD5)).isEqualTo(8);
        assertThat(SprachnachrichtAudio.alaw((byte) 0x55)).isEqualTo(-8);
        assertThat(SprachnachrichtAudio.alaw((byte) 0xAA)).isEqualTo(32256);
        assertThat(SprachnachrichtAudio.alaw((byte) 0x2A)).isEqualTo(-32256);
        assertThat(SprachnachrichtAudio.mulaw((byte) 0xFF)).isZero();
        assertThat(SprachnachrichtAudio.mulaw((byte) 0x80)).isEqualTo(32124);
        assertThat(SprachnachrichtAudio.mulaw((byte) 0x00)).isEqualTo(-32124);
    }

    @Test
    @DisplayName("Leere, kaputte und unbekannte Formate werden abgelehnt")
    void fehler() {
        assertThatThrownBy(() -> SprachnachrichtAudio.aufbereiten(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SprachnachrichtAudio.aufbereiten(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SprachnachrichtAudio.aufbereiten(wav(85, 1, 8000, 8, new byte[10], false)))
                .hasMessageContaining("85");
        byte[] ohneData = "RIFF\u0004\u0000\u0000\u0000WAVE".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> SprachnachrichtAudio.aufbereiten(ohneData)).hasMessageContaining("data");
        byte[] nurData = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(12).put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(0).array();
        assertThatThrownBy(() -> SprachnachrichtAudio.aufbereiten(nurData)).hasMessageContaining("fmt");
    }
}
