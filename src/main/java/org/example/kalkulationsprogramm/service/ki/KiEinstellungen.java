package org.example.kalkulationsprogramm.service.ki;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Liest und speichert die KI-Einstellungen in {@code system_setting}: gewählter
 * Anbieter, Ersatz-Schalter, Schlüssel und Modelle je Anbieter. Alles wirkt
 * sofort, ohne Neustart – die Dienste fragen bei jeder Anfrage neu.
 *
 * <p>Schlüssel neuer Anbieter liegen verschlüsselt ({@link MailSecretService});
 * der Gemini-Schlüssel bleibt, wo er immer lag ({@link SystemSettingsService#getGeminiApiKey()}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiEinstellungen {

    static final String ANBIETER = "ai.anbieter";
    static final String ERSATZ_ERLAUBT = "ai.anbieter.ersatz-erlaubt";
    static final KiAnbieterTyp STANDARD_ANBIETER = KiAnbieterTyp.GEMINI;

    /** Schlüssel: Zeichen, wie sie die Anbieter ausgeben (Buchstaben, Ziffern, wenige Sonderzeichen). */
    private static final Pattern GUELTIGER_SCHLUESSEL = Pattern.compile("[A-Za-z0-9._~+/=:-]{8,500}");
    /** Modellnamen wie {@code mistral-large-latest} oder {@code gemini-3.8-flash}. */
    private static final Pattern GUELTIGES_MODELL = Pattern.compile("[A-Za-z0-9._:/-]{1,200}");

    private final SystemSettingsService settings;
    private final MailSecretService secrets;
    private final Environment environment;

    // ==================== Auswahl ====================

    public KiAnbieterTyp gewaehlterAnbieter() {
        return KiAnbieterTyp.lies(settings.get(ANBIETER, null)).orElse(STANDARD_ANBIETER);
    }

    public boolean ersatzErlaubt() {
        String wert = settings.get(ERSATZ_ERLAUBT, "true");
        return wert == null || !"false".equalsIgnoreCase(wert.trim());
    }

    @Transactional
    public void speichereAuswahl(KiAnbieterTyp anbieter, boolean ersatzErlaubt) {
        settings.save(ANBIETER, anbieter.name(), "Gewählter KI-Anbieter");
        settings.save(ERSATZ_ERLAUBT, String.valueOf(ersatzErlaubt),
                "Darf ein anderer KI-Anbieter einspringen, wenn der gewählte etwas nicht kann?");
        log.info("KI-Anbieter gewählt: {} (Ersatz {})", anbieter, ersatzErlaubt ? "erlaubt" : "aus");
    }

    // ==================== Wahl je Funktion ====================

    public Optional<KiFunktionsWahl> funktionsWahl(KiFunktion funktion) {
        Optional<KiAnbieterTyp> anbieter = KiAnbieterTyp.lies(settings.get(funktionEinstellung(funktion, "anbieter"), null));
        if (anbieter.isEmpty()) {
            return Optional.empty();
        }
        String modell = settings.get(funktionEinstellung(funktion, "modell"), null);
        return Optional.of(new KiFunktionsWahl(anbieter.get(),
                modell == null || modell.isBlank() ? null : modell.strip()));
    }

    /**
     * Speichert die eigene Wahl; {@code null} setzt die Funktion zurück auf den
     * Standard-Anbieter. Ob der Anbieter die Funktion kann, prüft {@link KiDienst}.
     */
    @Transactional
    public void speichereFunktionsWahl(KiFunktion funktion, KiFunktionsWahl wahl) {
        String modell = wahl == null || wahl.modell() == null ? "" : wahl.modell().strip();
        if (!modell.isEmpty() && !istGueltigesModell(modell)) {
            throw new IllegalArgumentException(
                    "Der Modellname ist ungültig. Erlaubt sind Buchstaben, Ziffern und . _ : / -");
        }
        settings.save(funktionEinstellung(funktion, "anbieter"), wahl == null ? "" : wahl.anbieter().name(),
                "KI-Anbieter für: " + funktion.bezeichnung());
        settings.save(funktionEinstellung(funktion, "modell"), wahl == null ? "" : modell,
                "KI-Modell für: " + funktion.bezeichnung());
        log.info("KI-Wahl für {}: {}", funktion,
                wahl == null ? "Standard" : wahl.anbieter() + (modell.isEmpty() ? "" : " / " + modell));
    }

    // ==================== Schlüssel ====================

    /** Der Schlüssel des Anbieters, leer ohne Schlüssel. Nie loggen. */
    public String schluessel(KiAnbieter anbieter) {
        // Klartext-Ablage gibt es nur für den Altbestand Gemini, über die bisherigen Methoden.
        if (!anbieter.schluesselVerschluesselt()) {
            String wert = settings.getGeminiApiKey();
            return wert == null ? "" : wert.strip();
        }
        String gespeichert = settings.get(schluesselEinstellung(anbieter.typ()), null);
        if (gespeichert == null) {
            // Rückfall: application-local.properties bzw. app.env (CLAUDE.md, Sicherheitsregel 1)
            return environment.getProperty(schluesselEinstellung(anbieter.typ()), "").strip();
        }
        if (gespeichert.isBlank()) {
            return "";
        }
        try {
            return secrets.decrypt(gespeichert);
        } catch (RuntimeException e) {
            // Falscher Verschlüsselungsschlüssel auf dem Server: wie kein Schlüssel behandeln.
            log.warn("KI-Schlüssel für {} lässt sich nicht entschlüsseln ({})", anbieter.typ(),
                    e.getClass().getSimpleName());
            return "";
        }
    }

    public boolean hatSchluessel(KiAnbieter anbieter) {
        return !schluessel(anbieter).isBlank();
    }

    /** Kann der Server Schlüssel verschlüsselt ablegen? */
    public boolean verschluesselungBereit() {
        return secrets.isConfigured();
    }

    @Transactional
    public void speichereSchluessel(KiAnbieter anbieter, String eingabe) {
        String wert = eingabe == null ? "" : eingabe.strip();
        if (wert.isEmpty()) {
            throw new IllegalArgumentException("Bitte einen Schlüssel eintragen.");
        }
        if (!GUELTIGER_SCHLUESSEL.matcher(wert).matches()) {
            throw new IllegalArgumentException("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.");
        }
        if (!anbieter.schluesselVerschluesselt()) {
            settings.saveGeminiApiKey(wert);
            return;
        }
        if (!secrets.isConfigured()) {
            throw new IllegalArgumentException("Der Server ist noch nicht für verschlüsselte Zugangsdaten "
                    + "eingerichtet. Bitte den Betreuer des Programms fragen.");
        }
        settings.save(schluesselEinstellung(anbieter.typ()), secrets.encrypt(wert),
                anbieter.name() + "-Schlüssel (verschlüsselt)");
        log.info("KI-Schlüssel für {} gespeichert", anbieter.typ());
    }

    /** Leer speichern, damit auch ein Wert aus den Properties nicht mehr greift. */
    @Transactional
    public void entferneSchluessel(KiAnbieter anbieter) {
        if (!anbieter.schluesselVerschluesselt()) {
            settings.entferneGeminiApiKey();
            return;
        }
        settings.save(schluesselEinstellung(anbieter.typ()), "", anbieter.name() + "-Schlüssel (verschlüsselt)");
        log.info("KI-Schlüssel für {} entfernt", anbieter.typ());
    }

    // ==================== Modelle ====================

    /** Das wirksame Modell: eingestellt, sonst der Standard des Anbieters. */
    public String modell(KiAnbieter anbieter, KiModellStufe stufe) {
        String eingestellt = settings.get(modellEinstellung(anbieter.typ(), stufe), "");
        return eingestellt == null || eingestellt.isBlank() ? anbieter.standardModelle().get(stufe) : eingestellt.strip();
    }

    /**
     * Speichert die Modelle. Fehlt eine Stufe oder ist sie leer, gilt wieder der
     * Standard. Erst alles prüfen, dann speichern – sonst bliebe ein halber Stand.
     */
    @Transactional
    public void speichereModelle(KiAnbieter anbieter, Map<String, String> eingabe) {
        Map<String, String> modelle = eingabe == null ? Map.of() : eingabe;
        for (Map.Entry<String, String> eintrag : modelle.entrySet()) {
            KiModellStufe stufe = stufe(eintrag.getKey());
            if (stufe == null) {
                throw new IllegalArgumentException("Diese Modellstufe gibt es nicht.");
            }
            String modell = eintrag.getValue() == null ? "" : eintrag.getValue().strip();
            // Die Eingabe nicht zurückgeben: Meldungen sollen nichts Fremdes wiederholen.
            if (!modell.isEmpty() && !istGueltigesModell(modell)) {
                throw new IllegalArgumentException("Der Modellname für „" + bezeichnung(stufe)
                        + "“ ist ungültig. Erlaubt sind Buchstaben, Ziffern und . _ : / -");
            }
        }
        for (KiModellStufe stufe : KiModellStufe.values()) {
            String modell = modelle.getOrDefault(stufe.name(), "");
            settings.save(modellEinstellung(anbieter.typ(), stufe), modell == null ? "" : modell.strip(),
                    anbieter.name() + "-Modell für Stufe " + stufe.name());
        }
        log.info("KI-Modelle für {} gespeichert", anbieter.typ());
    }

    /** Der Name landet bei Gemini im Adresspfad: keine „..“-Schritte, die woandershin führen. */
    private static boolean istGueltigesModell(String modell) {
        return GUELTIGES_MODELL.matcher(modell).matches() && !modell.contains("..");
    }

    private static KiModellStufe stufe(String name) {
        for (KiModellStufe stufe : KiModellStufe.values()) {
            if (stufe.name().equals(name)) {
                return stufe;
            }
        }
        return null;
    }

    private static String bezeichnung(KiModellStufe stufe) {
        return switch (stufe) {
            case SCHNELL -> "Schnell";
            case STANDARD -> "Standard";
            case GRUENDLICH -> "Gründlich";
        };
    }

    static String funktionEinstellung(KiFunktion funktion, String teil) {
        return "ai.funktion." + funktion.name().toLowerCase(Locale.ROOT) + "." + teil;
    }

    static String schluesselEinstellung(KiAnbieterTyp typ) {
        return "ai." + typ.kuerzel() + ".api-key";
    }

    static String modellEinstellung(KiAnbieterTyp typ, KiModellStufe stufe) {
        return "ai." + typ.kuerzel() + ".modell." + stufe.name().toLowerCase(Locale.ROOT);
    }
}
