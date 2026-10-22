package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Ein Eintrag, wie er im Telefonbuch der Telefonanlage stehen soll: Anzeigename
 * und alle Nummern des Kontakts (ohne Doppelte, die erste ist die Hauptnummer).
 * Unabhängig von der FRITZ!Box – welche Nummernart dort welchen Typ bekommt,
 * entscheidet die Anlagen-Umsetzung.
 */
public record TelefonbuchEintrag(String name, List<Nummer> nummern) {

    /** Version des Inhalts-Hashs. Hochzählen, wenn sich ändert, was an die Box geht – dann wird alles neu gesendet. */
    static final String HASH_VERSION = "v1";

    public TelefonbuchEintrag {
        nummern = List.copyOf(nummern);
    }

    /** Art einer Nummer – für das Symbol am Telefon (Büro, Handy, privat). */
    public enum NummerArt {
        GESCHAEFTLICH,
        MOBIL,
        PRIVAT
    }

    public record Nummer(String nummer, NummerArt art) {
    }

    /** SHA-256 über Name und Nummern – ändert sich genau dann, wenn der Eintrag neu gesendet werden muss. */
    public String inhaltHash() {
        StringBuilder inhalt = new StringBuilder(HASH_VERSION).append('\n').append(name);
        for (Nummer n : nummern) {
            inhalt.append('\n').append(n.art()).append(':').append(n.nummer());
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(inhalt.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
