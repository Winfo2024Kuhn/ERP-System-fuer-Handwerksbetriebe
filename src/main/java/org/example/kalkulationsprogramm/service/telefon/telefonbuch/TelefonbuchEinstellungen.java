package org.example.kalkulationsprogramm.service.telefon.telefonbuch;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Schalter und Stand des Telefonbuch-Abgleichs in {@code system_setting}.
 * Keine Geheimnisse – die Zugangsdaten zur FRITZ!Box verwaltet weiter
 * {@code TelefonEinstellungenService}.
 */
@Service
@RequiredArgsConstructor
public class TelefonbuchEinstellungen {

    static final String AKTIV = "telefon.telefonbuch.aktiv";
    static final String TELEFONBUCH_ID = "telefon.telefonbuch.id";
    static final String LETZTER_ABGLEICH = "telefon.telefonbuch.letzter-abgleich";
    static final String ANZAHL = "telefon.telefonbuch.anzahl";
    static final String LETZTER_FEHLER = "telefon.telefonbuch.letzter-fehler";
    static final String LOESCHUNG_OFFEN = "telefon.telefonbuch.loeschung-offen";

    private final SystemSettingsService settings;

    public boolean istAktiv() {
        return Boolean.parseBoolean(settings.get(AKTIV, "false"));
    }

    public void setzeAktiv(boolean aktiv) {
        settings.save(AKTIV, String.valueOf(aktiv), "Kontakte ins FRITZ!Box-Telefonbuch „ERP-Kontakte“ übertragen");
    }

    /** Nach dem Ausschalten ist das Telefonbuch noch nicht aus der FRITZ!Box gelöscht. */
    public boolean istLoeschungOffen() {
        return Boolean.parseBoolean(settings.get(LOESCHUNG_OFFEN, "false"));
    }

    public void setzeLoeschungOffen(boolean offen) {
        settings.save(LOESCHUNG_OFFEN, String.valueOf(offen), "Telefonbuch „ERP-Kontakte“ muss noch aus der FRITZ!Box gelöscht werden");
    }

    /** Telefonbuch ist aus der Box gelöscht: ID vergessen, Stand zurücksetzen, Löschung erledigt. */
    public void merkeTelefonbuchGeloescht(LocalDateTime zeitpunkt) {
        merkeTelefonbuchId("");
        merkeAbgleich(zeitpunkt, 0, null);
        setzeLoeschungOffen(false);
    }

    /** ID des Telefonbuchs „ERP-Kontakte“ in der FRITZ!Box oder {@code null}, wenn noch keins angelegt ist. */
    public String telefonbuchId() {
        return leerZuNull(settings.get(TELEFONBUCH_ID, null));
    }

    public void merkeTelefonbuchId(String id) {
        settings.save(TELEFONBUCH_ID, id, "ID des Telefonbuchs „ERP-Kontakte“ in der FRITZ!Box");
    }

    public LocalDateTime letzterAbgleich() {
        String wert = settings.get(LETZTER_ABGLEICH, null);
        if (wert == null || wert.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(wert);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public int anzahlEintraege() {
        try {
            return Math.max(0, Integer.parseInt(settings.get(ANZAHL, "0").trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String letzterFehler() {
        return leerZuNull(settings.get(LETZTER_FEHLER, null));
    }

    /** Merkt Zeitpunkt und Anzahl; {@code fehler} = Hinweis auf einzelne abgelehnte Einträge oder {@code null}. */
    public void merkeAbgleich(LocalDateTime zeitpunkt, int anzahl, String fehler) {
        settings.save(LETZTER_ABGLEICH, zeitpunkt.toString(), "Letzter Abgleich mit dem FRITZ!Box-Telefonbuch");
        settings.save(ANZAHL, String.valueOf(anzahl), "Anzahl Einträge im FRITZ!Box-Telefonbuch „ERP-Kontakte“");
        merkeFehler(fehler);
    }

    public void merkeFehler(String fehler) {
        settings.save(LETZTER_FEHLER, fehler == null ? "" : fehler, "Grund des letzten fehlgeschlagenen Telefonbuch-Abgleichs");
    }

    private static String leerZuNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
