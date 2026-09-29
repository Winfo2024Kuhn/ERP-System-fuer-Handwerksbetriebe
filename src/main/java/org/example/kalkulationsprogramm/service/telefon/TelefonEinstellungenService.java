package org.example.kalkulationsprogramm.service.telefon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Telefon.AnrufbeantworterDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonEinstellungenSpeichernDto;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.example.kalkulationsprogramm.service.telefon.fritzbox.FritzBoxHost;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Telefon-Einstellungen in {@code system_setting}. Das FRITZ!Box-Passwort
 * liegt verschlüsselt ({@link MailSecretService}) und verlässt den Server nie.
 */
@Service
@RequiredArgsConstructor
public class TelefonEinstellungenService {

    static final String AKTIV = "telefon.aktiv";
    static final String HOST = "telefon.fritzbox.host";
    static final String BENUTZER = "telefon.fritzbox.benutzer";
    static final String PASSWORT = "telefon.fritzbox.passwort";
    static final String GESCHAEFTSNUMMERN = "telefon.geschaeftsnummern";
    static final String ANRUFBEANTWORTER = "telefon.anrufbeantworter";
    static final String AUFBEWAHRUNG_ANRUFE = "telefon.aufbewahrung.anrufe.monate";
    static final String AUFBEWAHRUNG_NACHRICHTEN = "telefon.aufbewahrung.sprachnachrichten.monate";
    static final String LANDESVORWAHL = "telefon.landesvorwahl";
    static final String ORTSVORWAHL = "telefon.ortsvorwahl";
    static final String LETZTE_ABHOLUNG = "telefon.abholung.letzter-erfolg";
    static final String LETZTER_FEHLER = "telefon.abholung.letzter-fehler";

    static final String STANDARD_HOST = "fritz.box";
    static final int STANDARD_AUFBEWAHRUNG = 12;
    static final int MIN_MONATE = 1;
    static final int MAX_MONATE = 120;
    private static final int MAX_FELD = 200;
    private static final int MAX_EINTRAEGE = 20;

    private final SystemSettingsService settings;
    private final MailSecretService secrets;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TelefonEinstellungenDto lade(boolean anrufmonitorVerbunden) {
        return new TelefonEinstellungenDto(
                istAktiv(),
                host(),
                settings.get(BENUTZER, ""),
                !settings.get(PASSWORT, "").isBlank(),
                secrets.isConfigured(),
                geschaeftsnummern(),
                anrufbeantworter(),
                aufbewahrungAnrufeMonate(),
                aufbewahrungSprachnachrichtenMonate(),
                settings.get(LANDESVORWAHL, null),
                settings.get(ORTSVORWAHL, null),
                letzteAbholung(),
                blankZuNull(settings.get(LETZTER_FEHLER, null)),
                anrufmonitorVerbunden);
    }

    @Transactional
    public void speichere(TelefonEinstellungenSpeichernDto dto) {
        if (dto.host() != null) {
            String host = dto.host().trim();
            if (!FritzBoxHost.istGueltig(host)) {
                throw new IllegalArgumentException("Die FRITZ!Box-Adresse ist ungültig (z.B. fritz.box oder 192.168.178.1).");
            }
            settings.save(HOST, host, "Adresse der FRITZ!Box im Büro-Netz");
        }
        if (dto.benutzer() != null) {
            settings.save(BENUTZER, begrenze(dto.benutzer().trim(), "Benutzername"), "FRITZ!Box-Benutzer für das ERP");
        }
        if (dto.passwort() != null && !dto.passwort().isEmpty()) {
            if (!secrets.isConfigured()) {
                throw new IllegalArgumentException(
                        "Das Passwort kann nicht geschützt gespeichert werden: mail.credentials.encryption-key ist nicht eingerichtet.");
            }
            settings.save(PASSWORT, secrets.encrypt(begrenze(dto.passwort(), "Passwort")), "FRITZ!Box-Passwort (verschlüsselt)");
        }
        if (dto.geschaeftsnummern() != null) {
            Set<String> nummern = new LinkedHashSet<>();
            for (String n : dto.geschaeftsnummern()) {
                if (n != null && !n.isBlank()) {
                    nummern.add(begrenze(n.trim(), "Rufnummer"));
                }
            }
            pruefeAnzahl(nummern.size());
            settings.save(GESCHAEFTSNUMMERN, String.join(",", nummern),
                    "Eigene Rufnummern, deren Anrufe ins ERP übernommen werden");
        }
        if (dto.anrufbeantworter() != null) {
            List<AnrufbeantworterDto> abs = new ArrayList<>();
            for (AnrufbeantworterDto ab : dto.anrufbeantworter()) {
                if (ab != null && ab.index() >= 0 && ab.index() < 10
                        && abs.stream().noneMatch(a -> a.index() == ab.index())) {
                    String name = ab.name() == null || ab.name().isBlank()
                            ? "AB " + (ab.index() + 1) : begrenze(ab.name().trim(), "Name");
                    abs.add(new AnrufbeantworterDto(ab.index(), name));
                }
            }
            pruefeAnzahl(abs.size());
            settings.save(ANRUFBEANTWORTER, json(abs), "Anrufbeantworter, deren Nachrichten ins ERP übernommen werden");
        }
        if (dto.aufbewahrungAnrufeMonate() != null) {
            settings.save(AUFBEWAHRUNG_ANRUFE, String.valueOf(pruefeMonate(dto.aufbewahrungAnrufeMonate())),
                    "Anrufe werden nach so vielen Monaten gelöscht");
        }
        if (dto.aufbewahrungSprachnachrichtenMonate() != null) {
            settings.save(AUFBEWAHRUNG_NACHRICHTEN, String.valueOf(pruefeMonate(dto.aufbewahrungSprachnachrichtenMonate())),
                    "Sprachnachrichten werden nach so vielen Monaten gelöscht");
        }
        if (dto.aktiv() != null) {
            settings.save(AKTIV, String.valueOf(dto.aktiv()), "Telefon-Anbindung an die FRITZ!Box aktiv");
        }
    }

    /**
     * Zugang aus den gespeicherten Einstellungen – leer, wenn Host, Benutzer oder
     * Passwort fehlen oder das Passwort nicht entschlüsselt werden kann.
     */
    public Optional<TelefonZugang> zugang() {
        String passwortVerschluesselt = settings.get(PASSWORT, "");
        String benutzer = settings.get(BENUTZER, "");
        if (passwortVerschluesselt.isBlank() || benutzer.isBlank() || !secrets.isConfigured()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TelefonZugang(host(), benutzer, secrets.decrypt(passwortVerschluesselt)));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * Zugang für "Verbindung testen": ungespeicherte Eingaben haben Vorrang,
     * fehlende Werte kommen aus den gespeicherten Einstellungen. Das gespeicherte
     * Passwort geht nur an die gespeicherte Adresse – bei einer anderen Adresse
     * muss es neu eingegeben werden, sonst könnte man es an einen fremden Host schicken.
     */
    public Optional<TelefonZugang> zugangFuerTest(String host, String benutzer, String passwort) {
        Optional<TelefonZugang> gespeichert = zugang();
        String h = host != null && !host.isBlank() ? host.trim() : host();
        String b = benutzer != null && !benutzer.isBlank() ? benutzer.trim()
                : gespeichert.map(TelefonZugang::benutzer).orElse(settings.get(BENUTZER, ""));
        boolean gespeicherteAdresse = h.equalsIgnoreCase(host());
        String p = passwort != null && !passwort.isEmpty() ? passwort
                : gespeichert.filter(z -> gespeicherteAdresse).map(TelefonZugang::passwort).orElse(null);
        if (!FritzBoxHost.istGueltig(h) || b.isBlank() || p == null) {
            return Optional.empty();
        }
        return Optional.of(new TelefonZugang(h, b, p));
    }

    public boolean istAktiv() {
        return Boolean.parseBoolean(settings.get(AKTIV, "false"));
    }

    public String host() {
        String h = settings.get(HOST, STANDARD_HOST);
        return h == null || h.isBlank() ? STANDARD_HOST : h;
    }

    public List<String> geschaeftsnummern() {
        String wert = settings.get(GESCHAEFTSNUMMERN, "");
        List<String> liste = new ArrayList<>();
        for (String n : wert.split(",", -1)) {
            if (!n.isBlank()) {
                liste.add(n.trim());
            }
        }
        return liste;
    }

    public List<AnrufbeantworterDto> anrufbeantworter() {
        String wert = settings.get(ANRUFBEANTWORTER, "");
        if (wert == null || wert.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(wert, new TypeReference<List<AnrufbeantworterDto>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public int aufbewahrungAnrufeMonate() {
        return monate(AUFBEWAHRUNG_ANRUFE);
    }

    public int aufbewahrungSprachnachrichtenMonate() {
        return monate(AUFBEWAHRUNG_NACHRICHTEN);
    }

    public String landesvorwahl() {
        return settings.get(LANDESVORWAHL, "49");
    }

    public String ortsvorwahl() {
        return settings.get(ORTSVORWAHL, null);
    }

    @Transactional
    public void speichereVorwahlen(String landesvorwahl, String ortsvorwahl) {
        if (landesvorwahl != null && !landesvorwahl.isBlank()) {
            settings.save(LANDESVORWAHL, begrenze(landesvorwahl.trim(), "Landesvorwahl"), "Landesvorwahl laut FRITZ!Box");
        }
        if (ortsvorwahl != null && !ortsvorwahl.isBlank()) {
            settings.save(ORTSVORWAHL, begrenze(ortsvorwahl.trim(), "Ortsvorwahl"), "Ortsvorwahl laut FRITZ!Box");
        }
    }

    public LocalDateTime letzteAbholung() {
        String wert = settings.get(LETZTE_ABHOLUNG, null);
        if (wert == null || wert.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(wert);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String letzterFehler() {
        return blankZuNull(settings.get(LETZTER_FEHLER, null));
    }

    @Transactional
    public void merkeErfolg(LocalDateTime zeitpunkt) {
        settings.save(LETZTE_ABHOLUNG, zeitpunkt.toString(), "Letzte erfolgreiche Abholung von der FRITZ!Box");
        settings.save(LETZTER_FEHLER, "", "Grund der letzten fehlgeschlagenen Abholung");
    }

    @Transactional
    public void merkeFehler(String grund) {
        settings.save(LETZTER_FEHLER, grund == null ? "" : grund, "Grund der letzten fehlgeschlagenen Abholung");
    }

    private int monate(String schluessel) {
        try {
            int m = Integer.parseInt(settings.get(schluessel, String.valueOf(STANDARD_AUFBEWAHRUNG)).trim());
            return Math.clamp(m, MIN_MONATE, MAX_MONATE);
        } catch (NumberFormatException e) {
            return STANDARD_AUFBEWAHRUNG;
        }
    }

    private static int pruefeMonate(int monate) {
        if (monate < MIN_MONATE || monate > MAX_MONATE) {
            throw new IllegalArgumentException("Die Aufbewahrung muss zwischen 1 und 120 Monaten liegen.");
        }
        return monate;
    }

    private static void pruefeAnzahl(int anzahl) {
        if (anzahl > MAX_EINTRAEGE) {
            throw new IllegalArgumentException("Zu viele Einträge.");
        }
    }

    private static String begrenze(String wert, String feld) {
        if (wert.length() > MAX_FELD) {
            throw new IllegalArgumentException(feld + " ist zu lang.");
        }
        return wert;
    }

    private String json(List<AnrufbeantworterDto> abs) {
        try {
            return objectMapper.writeValueAsString(abs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Anrufbeantworter konnten nicht gespeichert werden", e);
        }
    }

    private static String blankZuNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
