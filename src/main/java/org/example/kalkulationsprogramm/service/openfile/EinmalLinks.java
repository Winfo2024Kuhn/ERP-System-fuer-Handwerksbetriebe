package org.example.kalkulationsprogramm.service.openfile;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Kurzlebige Einmal-Links für die Mac-Einrichtung per Terminal-Befehl.
 *
 * <p>Der Admin erzeugt den Link angemeldet im ERP; abgeholt wird er ohne Anmeldung von
 * {@code curl} im Terminal. Der Schutz steckt in 256 Bit Zufall, der Gültigkeit {@link #GUELTIG}
 * und darin, dass jeder Link nur einmal einlösbar ist. Dass er nur über denselben Rechnernamen
 * abholbar ist, schützt vor Fehlbedienung, nicht vor Angriffen (den Host-Header setzt der
 * Aufrufer). Die Links liegen nur im Speicher – nach einem Neustart erstellt man einfach einen
 * neuen Befehl.</p>
 */
@Component
class EinmalLinks {

    static final Duration GUELTIG = Duration.ofMinutes(10);
    /** Obergrenze offener Links; der älteste fällt weg, wenn sie erreicht ist. */
    static final int MAX_OFFEN = 20;

    /** 32 Byte Base64-URL ohne Auffüllung = 43 Zeichen. */
    private static final Pattern SCHLUESSEL = Pattern.compile("[A-Za-z0-9_-]{43}+");

    private record Eintrag(String inhalt, String host, long ablauf) {
    }

    private final Map<String, Eintrag> offen = new ConcurrentHashMap<>();
    private final SecureRandom zufall = new SecureRandom();
    /** Uhr; in Tests ersetzbar. */
    LongSupplier jetzt = System::currentTimeMillis;

    /** Legt einen Link für {@code inhalt} an, abholbar nur über {@code host}; liefert den Schlüssel. */
    synchronized String neu(String inhalt, String host) {
        long now = jetzt.getAsLong();
        offen.values().removeIf(e -> e.ablauf() <= now);
        while (offen.size() >= MAX_OFFEN) {
            offen.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().ablauf()))
                    .ifPresent(aeltester -> offen.remove(aeltester.getKey()));
        }
        byte[] roh = new byte[32];
        zufall.nextBytes(roh);
        String schluessel = Base64.getUrlEncoder().withoutPadding().encodeToString(roh);
        offen.put(schluessel, new Eintrag(inhalt, host.toLowerCase(Locale.ROOT), now + GUELTIG.toMillis()));
        return schluessel;
    }

    /** Inhalt zum Schlüssel – genau einmal, solange gültig und über denselben Rechnernamen. */
    Optional<String> einloesen(String schluessel, String host) {
        if (schluessel == null || host == null || !SCHLUESSEL.matcher(schluessel).matches()) {
            return Optional.empty();
        }
        Eintrag eintrag = offen.get(schluessel);
        if (eintrag == null) {
            return Optional.empty();
        }
        if (eintrag.ablauf() <= jetzt.getAsLong()) {
            offen.remove(schluessel, eintrag);
            return Optional.empty();
        }
        if (!eintrag.host().equalsIgnoreCase(host)) {
            return Optional.empty();
        }
        // remove(key, value) ist atomar: Zwei gleichzeitige Abrufe bekommen den Inhalt nur einmal
        return offen.remove(schluessel, eintrag) ? Optional.of(eintrag.inhalt()) : Optional.empty();
    }
}
