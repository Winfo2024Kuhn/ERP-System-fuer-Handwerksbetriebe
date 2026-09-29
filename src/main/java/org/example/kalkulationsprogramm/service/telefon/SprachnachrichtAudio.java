package org.example.kalkulationsprogramm.service.telefon;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Bereitet Aufnahmen des Anrufbeantworters so auf, dass jeder Browser sie
 * abspielen kann: Ergebnis ist immer WAV mit 16-bit-PCM.
 * <ul>
 *   <li>WAV mit PCM → unverändert</li>
 *   <li>WAV mit G.711 A-law/µ-law → nach 16-bit-PCM gewandelt (Safari spielt G.711 nicht ab)</li>
 *   <li>kein WAV-Kopf → als rohes G.711 A-law, 8 kHz mono gelesen (Telefonie-Standard in Europa)</li>
 * </ul>
 */
public final class SprachnachrichtAudio {

    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_ALAW = 6;
    private static final int FORMAT_MULAW = 7;
    private static final int ROH_ABTASTRATE = 8000;

    private SprachnachrichtAudio() {
    }

    /** Aufbereitete Aufnahme samt Dauer. */
    public record Ergebnis(byte[] wav, int dauerSekunden, boolean warRoh) {
    }

    public static Ergebnis aufbereiten(byte[] daten) {
        if (daten == null || daten.length == 0) {
            throw new IllegalArgumentException("Leere Aufnahme");
        }
        Wav wav = leseWav(daten);
        if (wav == null) {
            byte[] pcm = g711ZuPcm(daten, 0, daten.length, true);
            return new Ergebnis(schreibePcmWav(pcm, ROH_ABTASTRATE, 1), dauer(pcm.length, ROH_ABTASTRATE * 2), true);
        }
        if (wav.format == FORMAT_PCM) {
            return new Ergebnis(daten, dauer(wav.datenLaenge, wav.byteRate), false);
        }
        if (wav.format == FORMAT_ALAW || wav.format == FORMAT_MULAW) {
            byte[] pcm = g711ZuPcm(daten, wav.datenStart, wav.datenLaenge, wav.format == FORMAT_ALAW);
            return new Ergebnis(schreibePcmWav(pcm, wav.abtastrate, wav.kanaele),
                    dauer(pcm.length, wav.abtastrate * wav.kanaele * 2), false);
        }
        throw new IllegalArgumentException("Nicht unterstütztes Audioformat " + wav.format);
    }

    private record Wav(int format, int kanaele, int abtastrate, int byteRate, int datenStart, int datenLaenge) {
    }

    /** Liest den RIFF/WAVE-Kopf; null, wenn es kein WAV ist. */
    private static Wav leseWav(byte[] d) {
        if (d.length < 12 || !text(d, 0, "RIFF") || !text(d, 8, "WAVE")) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int format = -1;
        int kanaele = 1;
        int abtastrate = ROH_ABTASTRATE;
        int byteRate = ROH_ABTASTRATE;
        while (pos + 8 <= d.length) {
            String id = new String(d, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long groesse = Integer.toUnsignedLong(b.getInt(pos + 4));
            int inhalt = pos + 8;
            if ("fmt ".equals(id) && inhalt + 16 <= d.length) {
                format = Short.toUnsignedInt(b.getShort(inhalt));
                kanaele = Math.max(1, Short.toUnsignedInt(b.getShort(inhalt + 2)));
                abtastrate = b.getInt(inhalt + 4);
                byteRate = b.getInt(inhalt + 8);
            } else if ("data".equals(id)) {
                int laenge = (int) Math.min(groesse, (long) d.length - inhalt);
                if (format < 0 || abtastrate <= 0 || byteRate <= 0) {
                    throw new IllegalArgumentException("WAV ohne gültigen fmt-Block");
                }
                return new Wav(format, kanaele, abtastrate, byteRate, inhalt, laenge);
            }
            long naechste = inhalt + groesse + (groesse % 2);
            if (naechste > d.length || naechste <= pos) {
                break;
            }
            pos = (int) naechste;
        }
        throw new IllegalArgumentException("WAV ohne data-Block");
    }

    private static int dauer(long bytes, long bytesProSekunde) {
        if (bytesProSekunde <= 0) {
            return 0;
        }
        return (int) Math.round((double) bytes / bytesProSekunde);
    }

    static byte[] g711ZuPcm(byte[] d, int start, int laenge, boolean alaw) {
        byte[] pcm = new byte[laenge * 2];
        for (int i = 0; i < laenge; i++) {
            int wert = alaw ? alaw(d[start + i]) : mulaw(d[start + i]);
            pcm[2 * i] = (byte) (wert & 0xFF);
            pcm[2 * i + 1] = (byte) ((wert >> 8) & 0xFF);
        }
        return pcm;
    }

    /** ITU-T G.711 A-law → linear 16 bit. */
    static int alaw(byte b) {
        int a = (b ^ 0x55) & 0xFF;
        int t = (a & 0x0F) << 4;
        int seg = (a & 0x70) >> 4;
        if (seg == 0) {
            t += 8;
        } else if (seg == 1) {
            t += 0x108;
        } else {
            t += 0x108;
            t <<= seg - 1;
        }
        return (a & 0x80) != 0 ? t : -t;
    }

    /** ITU-T G.711 µ-law → linear 16 bit. */
    static int mulaw(byte b) {
        int u = ~b & 0xFF;
        int t = ((u & 0x0F) << 3) + 0x84;
        t <<= (u & 0x70) >> 4;
        return (u & 0x80) != 0 ? 0x84 - t : t - 0x84;
    }

    static byte[] schreibePcmWav(byte[] pcm, int abtastrate, int kanaele) {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        h.putInt(36 + pcm.length);
        h.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        h.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        h.putInt(16);
        h.putShort((short) FORMAT_PCM);
        h.putShort((short) kanaele);
        h.putInt(abtastrate);
        h.putInt(abtastrate * kanaele * 2);
        h.putShort((short) (kanaele * 2));
        h.putShort((short) 16);
        h.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        h.putInt(pcm.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        out.writeBytes(h.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }

    private static boolean text(byte[] d, int pos, String erwartet) {
        for (int i = 0; i < erwartet.length(); i++) {
            if (d[pos + i] != erwartet.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
