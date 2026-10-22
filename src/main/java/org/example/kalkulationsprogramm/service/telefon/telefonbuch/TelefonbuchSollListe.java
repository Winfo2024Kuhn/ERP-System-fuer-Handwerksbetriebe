package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.TelefonbuchQuelle;
import org.example.kalkulationsprogramm.repository.KontaktRufnummerRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.example.kalkulationsprogramm.service.telefon.RufnummerNormalisierer;
import org.example.kalkulationsprogramm.service.telefon.TelefonEinstellungenService;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchEintrag.NummerArt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Berechnet aus der Datenbank, was im FRITZ!Box-Telefonbuch „ERP-Kontakte“ stehen soll.
 * <p>
 * Dabei sind: Kunden (ohne gesperrte und anonymisierte), aktive Lieferanten, aktive
 * Mitarbeiter, aktive und nicht abgelaufene Steuerberater samt Ansprechpartnern
 * (eigener Eintrag je Person). Gemerkte Zusatznummern hängen am jeweiligen Kunden,
 * Lieferanten oder Steuerberater. Ein Eintrag je Kontakt mit allen Nummern, ohne
 * Doppelte; Kontakte ohne gültige Nummer fehlen.
 * <p>
 * Die Nummern gehen in der Schreibweise an die Box, in der sie auch Anrufer meldet
 * (national mit 0, Ausland mit +), damit die Box bei Anrufen den Namen findet.
 */
@Service
@RequiredArgsConstructor
public class TelefonbuchSollListe {

    /** Längster Name im Telefonbuch – Mobilteile zeigen ohnehin nur den Anfang. */
    static final int MAX_NAME = 64;
    /** Mindestens so viel vom eigentlichen Namen bleibt stehen, bevor die Art gekürzt wird. */
    private static final int MIN_BASISNAME = 24;
    static final int MAX_NUMMERN = 9;
    static final int MIN_ZIFFERN = 3;
    static final int MAX_NUMMER = 32;

    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final SteuerberaterKontaktRepository steuerberaterRepository;
    private final KontaktRufnummerRepository kontaktRufnummerRepository;
    private final TelefonEinstellungenService telefonEinstellungen;
    private final Clock clock;

    /** Eindeutige Herkunft eines Eintrags. */
    public record Schluessel(TelefonbuchQuelle quelle, long id) {
    }

    /** Soll-Einträge in fester Reihenfolge (Kunden, Lieferanten, Mitarbeiter, Steuerberater). */
    @Transactional(readOnly = true)
    public Map<Schluessel, TelefonbuchEintrag> berechne() {
        Sammler s = new Sammler(telefonEinstellungen.landesvorwahl(), telefonEinstellungen.ortsvorwahl());
        LocalDate heute = LocalDate.now(clock);

        for (Object[] z : kundeRepository.findeFuerTelefonbuch()) {
            NummerArt festnetz = festnetzArtKunde((Anrede) z[2]);
            Schluessel k = new Schluessel(TelefonbuchQuelle.KUNDE, (Long) z[0]);
            s.nummer(k, (String) z[1], "Kunde", (String) z[3], festnetz);
            s.nummer(k, (String) z[1], "Kunde", (String) z[4], NummerArt.MOBIL);
        }
        for (Object[] z : kontaktRufnummerRepository.findeKundenNummernFuerTelefonbuch()) {
            s.nummer(new Schluessel(TelefonbuchQuelle.KUNDE, (Long) z[0]), (String) z[1], "Kunde",
                    (String) z[3], festnetzArtKunde((Anrede) z[2]));
        }
        for (Object[] z : lieferantenRepository.findeFuerTelefonbuch()) {
            Schluessel l = new Schluessel(TelefonbuchQuelle.LIEFERANT, (Long) z[0]);
            s.nummer(l, (String) z[1], "Lieferant", (String) z[2], NummerArt.GESCHAEFTLICH);
            s.nummer(l, (String) z[1], "Lieferant", (String) z[3], NummerArt.MOBIL);
        }
        for (Object[] z : kontaktRufnummerRepository.findeLieferantenNummernFuerTelefonbuch()) {
            s.nummer(new Schluessel(TelefonbuchQuelle.LIEFERANT, (Long) z[0]), (String) z[1], "Lieferant",
                    (String) z[2], NummerArt.GESCHAEFTLICH);
        }
        for (Object[] z : mitarbeiterRepository.findeFuerTelefonbuch()) {
            Schluessel m = new Schluessel(TelefonbuchQuelle.MITARBEITER, (Long) z[0]);
            String name = vollerName((String) z[1], (String) z[2]);
            s.nummer(m, name, "Mitarbeiter", (String) z[3], NummerArt.MOBIL);
            s.nummer(m, name, "Mitarbeiter", (String) z[4], NummerArt.PRIVAT);
        }
        for (Object[] z : steuerberaterRepository.findeFuerTelefonbuch(heute)) {
            s.nummer(new Schluessel(TelefonbuchQuelle.STEUERBERATER, (Long) z[0]), (String) z[1], "Steuerberater",
                    (String) z[2], NummerArt.GESCHAEFTLICH);
        }
        for (Object[] z : kontaktRufnummerRepository.findeSteuerberaterNummernFuerTelefonbuch(heute)) {
            s.nummer(new Schluessel(TelefonbuchQuelle.STEUERBERATER, (Long) z[0]), (String) z[1], "Steuerberater",
                    (String) z[2], NummerArt.GESCHAEFTLICH);
        }
        for (Object[] z : steuerberaterRepository.findeAnsprechpartnerFuerTelefonbuch(heute)) {
            String kanzlei = bereinige((String) z[4]);
            s.nummer(new Schluessel(TelefonbuchQuelle.STEUERBERATER_ANSPRECHPARTNER, (Long) z[0]),
                    vollerName((String) z[1], (String) z[2]),
                    kanzlei.isEmpty() ? "Steuerberater" : "Steuerberater " + kanzlei,
                    (String) z[3], NummerArt.GESCHAEFTLICH);
        }
        return s.ergebnis();
    }

    /** Firmen und „Damen und Herren“ sind geschäftlich, Personen und Familien privat. */
    static NummerArt festnetzArtKunde(Anrede anrede) {
        if (anrede == null) {
            return NummerArt.GESCHAEFTLICH;
        }
        return switch (anrede) {
            case HERR, FRAU, FAMILIE -> NummerArt.PRIVAT;
            case FIRMA, DAMEN_HERREN -> NummerArt.GESCHAEFTLICH;
        };
    }

    /**
     * Nummer in der Schreibweise, wie die FRITZ!Box sie wählt und Anrufer meldet:
     * inländisch "0931123456", Ausland "+43…". Ohne erkennbare Vorwahl (Ortsnetz,
     * Ortsvorwahl unbekannt) nur die Ziffern. {@code null}, wenn keine brauchbare Nummer.
     */
    static String waehlbar(String roh, String landesvorwahl, String ortsvorwahl) {
        if (roh == null || roh.isBlank()) {
            return null;
        }
        String e164 = RufnummerNormalisierer.normalisiere(roh, landesvorwahl, ortsvorwahl);
        String nummer;
        if (e164 != null) {
            String land = "+" + ohneFuehrendeNullen(nurZiffern(landesvorwahl == null || landesvorwahl.isBlank() ? "49" : landesvorwahl));
            nummer = e164.startsWith(land) ? "0" + e164.substring(land.length()) : e164;
        } else {
            nummer = nurZiffern(roh);
        }
        int ziffern = nummer.startsWith("+") ? nummer.length() - 1 : nummer.length();
        if (ziffern < MIN_ZIFFERN || nummer.length() > MAX_NUMMER) {
            return null;
        }
        return nummer;
    }

    /** "Name (Art)", höchstens {@link #MAX_NAME} Zeichen; zu lang → erst der Name, dann die Art gekürzt. */
    static String anzeigename(String basis, String art) {
        String b = bereinige(basis);
        String a = bereinige(art);
        String voll = b + " (" + a + ")";
        if (voll.length() <= MAX_NAME) {
            return voll;
        }
        int platzFuerBasis = MAX_NAME - a.length() - 3;
        if (platzFuerBasis >= MIN_BASISNAME) {
            return kuerze(b, platzFuerBasis) + " (" + a + ")";
        }
        String kurzBasis = kuerze(b, MIN_BASISNAME);
        return kurzBasis + " (" + kuerze(a, MAX_NAME - kurzBasis.length() - 3) + ")";
    }

    /** Steuerzeichen raus, Leerraum zusammenfassen – Steuerzeichen sind in XML gar nicht erlaubt. */
    static String bereinige(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean leer = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean erlaubt = c >= 0x20 && c != 0x7F && !Character.isSurrogate(c) && c != '\uFFFE' && c != '\uFFFF';
            if (Character.isWhitespace(c)) {
                leer = !sb.isEmpty();
                continue;
            }
            if (!erlaubt) {
                continue;
            }
            if (leer) {
                sb.append(' ');
                leer = false;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String kuerze(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)).stripTrailing() + "…";
    }

    private static String vollerName(String vorname, String nachname) {
        return (bereinige(vorname) + " " + bereinige(nachname)).trim();
    }

    private static String nurZiffern(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String ohneFuehrendeNullen(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '0') {
            i++;
        }
        return s.substring(i);
    }

    /** Sammelt Nummern je Kontakt, ohne Doppelte; Handynummern erkennt er auch in Festnetz-Feldern. */
    private static final class Sammler {
        private final String land;
        private final String ort;
        private final Map<Schluessel, String> namen = new LinkedHashMap<>();
        private final Map<Schluessel, Map<String, NummerArt>> nummern = new LinkedHashMap<>();

        Sammler(String land, String ort) {
            this.land = land;
            this.ort = ort;
        }

        void nummer(Schluessel schluessel, String name, String art, String roh, NummerArt nummerArt) {
            String basis = bereinige(name);
            if (basis.isEmpty()) {
                return;
            }
            String nummer = waehlbar(roh, land, ort);
            if (nummer == null) {
                return;
            }
            namen.putIfAbsent(schluessel, anzeigename(basis, art));
            Map<String, NummerArt> liste = nummern.computeIfAbsent(schluessel, k -> new LinkedHashMap<>());
            if (liste.size() < MAX_NUMMERN) {
                boolean handy = RufnummerNormalisierer.istDeutscherMobilfunk(
                        RufnummerNormalisierer.normalisiere(nummer, land, ort));
                liste.putIfAbsent(nummer, handy ? NummerArt.MOBIL : nummerArt);
            }
        }

        Map<Schluessel, TelefonbuchEintrag> ergebnis() {
            Map<Schluessel, TelefonbuchEintrag> ergebnis = new LinkedHashMap<>();
            nummern.forEach((schluessel, liste) -> {
                List<TelefonbuchEintrag.Nummer> n = new ArrayList<>(liste.size());
                liste.forEach((nummer, art) -> n.add(new TelefonbuchEintrag.Nummer(nummer, art)));
                ergebnis.put(schluessel, new TelefonbuchEintrag(namen.get(schluessel), n));
            });
            return ergebnis;
        }
    }
}
