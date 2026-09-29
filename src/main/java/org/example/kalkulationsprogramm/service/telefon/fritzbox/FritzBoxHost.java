package org.example.kalkulationsprogramm.service.telefon.fritzbox;

import org.example.kalkulationsprogramm.service.telefon.TelefonAnlageException;

/**
 * Prüft die eingestellte FRITZ!Box-Adresse: nur Hostname oder IPv4 –
 * kein Schema, kein Pfad, kein Port, keine Zugangsdaten in der Adresse.
 */
public final class FritzBoxHost {

    private static final int MAX_LAENGE = 253;

    private FritzBoxHost() {
    }

    public static boolean istGueltig(String host) {
        if (host == null || host.isEmpty() || host.length() > MAX_LAENGE) {
            return false;
        }
        if (host.startsWith(".") || host.startsWith("-") || host.endsWith("-")) {
            return false;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean erlaubt = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-';
            if (!erlaubt) {
                return false;
            }
        }
        return !host.contains("..");
    }

    static String pruefe(String host) {
        String h = host == null ? "" : host.trim();
        if (!istGueltig(h)) {
            throw new TelefonAnlageException(TelefonAnlageException.Grund.NICHT_EINGERICHTET);
        }
        return h;
    }
}
