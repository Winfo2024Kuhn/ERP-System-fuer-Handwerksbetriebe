package org.example.kalkulationsprogramm.service.ki;

import org.example.kalkulationsprogramm.dto.Ki.KiEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Ki.KiModellListeDto;
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Der eine Weg zur KI für alle Dienste. Entscheidet je {@link KiFunktion}, über
 * welchen Anbieter und welches Modell sie läuft, und schickt die Anfrage dorthin.
 *
 * <p>Regel: Der gewählte Anbieter – die eigene Wahl der Funktion, sonst der
 * Standard-Anbieter –, wenn er die Funktion kann und einen Schlüssel hat. Sonst
 * (nur wenn der Betrieb Ersatz erlaubt) der erste andere Anbieter, der beides
 * hat. Sonst ist die Funktion aus. Genau diese Entscheidung zeigt die
 * Einstellungsseite je Funktion an ({@link #uebersicht()}), damit nichts im
 * Verborgenen an einen Anbieter geht.
 */
@Service
public class KiDienst {

    /** So lange gilt eine geladene Modellliste, bevor sie neu geholt wird. */
    static final Duration MODELLLISTE_GUELTIG = Duration.ofMinutes(10);

    private final List<KiAnbieter> anbieterListe;
    private final KiEinstellungen einstellungen;
    private final Clock uhr;
    private final Map<KiAnbieterTyp, GemerkteModelle> modellListen = new ConcurrentHashMap<>();

    @Autowired
    public KiDienst(List<KiAnbieter> anbieterListe, KiEinstellungen einstellungen) {
        this(anbieterListe, einstellungen, Clock.systemUTC());
    }

    KiDienst(List<KiAnbieter> anbieterListe, KiEinstellungen einstellungen, Clock uhr) {
        this.anbieterListe = anbieterListe.stream()
                .sorted(Comparator.comparing(KiAnbieter::typ))
                .toList();
        this.einstellungen = einstellungen;
        this.uhr = uhr;
    }

    // ==================== Anfragen ====================

    /** Schickt die Anfrage mit der üblichen Modellstufe der Funktion. */
    public KiErgebnis sende(KiFunktion funktion, KiAnfrage anfrage) {
        return sende(funktion, funktion.stufe(), anfrage);
    }

    /**
     * @throws KiNichtVerfuegbar wenn für die Funktion kein Anbieter eingerichtet ist
     * @throws KiAnbieterFehler  wenn der Anbieter nicht oder mit einem Fehler antwortet
     */
    public KiErgebnis sende(KiFunktion funktion, KiModellStufe stufe, KiAnfrage anfrage) {
        KiZuordnung zuordnung = zuordnung(funktion);
        if (!zuordnung.verfuegbar()) {
            throw new KiNichtVerfuegbar(funktion, zuordnung.hinweis());
        }
        KiAnbieter anbieter = zuordnung.anbieter();
        return anbieter.sende(anfrage, modell(zuordnung, stufe), einstellungen.schluessel(anbieter));
    }

    public boolean verfuegbar(KiFunktion funktion) {
        return zuordnung(funktion).verfuegbar();
    }

    /**
     * Schlüssel und Modell für Dienste, die selbst mit Gemini sprechen. Leer,
     * wenn die Funktion gerade nicht über Gemini laufen darf – dann darf der
     * Dienst auch nichts an Google schicken.
     */
    public Optional<KiZugang> geminiZugang(KiFunktion funktion) {
        return geminiZugang(funktion, funktion.stufe());
    }

    public Optional<KiZugang> geminiZugang(KiFunktion funktion, KiModellStufe stufe) {
        KiZuordnung zuordnung = zuordnung(funktion);
        if (!zuordnung.verfuegbar() || zuordnung.anbieter().typ() != KiAnbieterTyp.GEMINI) {
            return Optional.empty();
        }
        return Optional.of(new KiZugang(einstellungen.schluessel(zuordnung.anbieter()), modell(zuordnung, stufe)));
    }

    /** Modell, mit dem die Funktion auf dieser Stufe gerade liefe; leer, wenn sie aus ist. */
    public Optional<String> modell(KiFunktion funktion, KiModellStufe stufe) {
        KiZuordnung zuordnung = zuordnung(funktion);
        return zuordnung.verfuegbar() ? Optional.of(modell(zuordnung, stufe)) : Optional.empty();
    }

    /** Ein eigenes Modell der Funktion gilt für jede Stufe; sonst das Modell der Stufe. */
    private String modell(KiZuordnung zuordnung, KiModellStufe stufe) {
        return zuordnung.modellVorgabe() != null
                ? zuordnung.modellVorgabe()
                : einstellungen.modell(zuordnung.anbieter(), stufe);
    }

    /** Hat irgendein Anbieter einen Schlüssel? Dann sind KI-Knöpfe grundsätzlich an. */
    public boolean irgendeinAnbieterEingerichtet() {
        return anbieterListe.stream().anyMatch(einstellungen::hatSchluessel);
    }

    // ==================== Zuordnung ====================

    public KiZuordnung zuordnung(KiFunktion funktion) {
        Optional<KiFunktionsWahl> wahl = einstellungen.funktionsWahl(funktion);
        KiAnbieter gewaehlt = anbieter(wahl.map(KiFunktionsWahl::anbieter)
                .orElseGet(einstellungen::gewaehlterAnbieter));
        Set<KiFaehigkeit> fehlend = fehlend(gewaehlt, funktion);
        if (fehlend.isEmpty() && einstellungen.hatSchluessel(gewaehlt)) {
            return new KiZuordnung(funktion, KiZuordnung.Status.AKTIV, gewaehlt,
                    wahl.map(KiFunktionsWahl::modell).orElse(null), null);
        }

        String grund = fehlend.isEmpty()
                ? "Für " + gewaehlt.name() + " ist noch kein Schlüssel hinterlegt."
                : gewaehlt.name() + " kann das in diesem Programm noch nicht (" + inWorten(fehlend) + ").";

        Optional<KiAnbieter> ersatz = anbieterListe.stream()
                .filter(a -> a != gewaehlt)
                .filter(a -> fehlend(a, funktion).isEmpty())
                .filter(einstellungen::hatSchluessel)
                .findFirst();

        if (!einstellungen.ersatzErlaubt()) {
            String zusatz = ersatz.isPresent() ? " Ersatz-Anbieter sind ausgeschaltet." : "";
            return new KiZuordnung(funktion, KiZuordnung.Status.AUS, null, null, grund + zusatz);
        }
        if (ersatz.isPresent()) {
            // Das eigene Modell gehört zum gewählten Anbieter, nicht zum Ersatz.
            return new KiZuordnung(funktion, KiZuordnung.Status.ERSATZ, ersatz.get(), null,
                    grund + " Es springt " + ersatz.get().name() + " ein.");
        }
        return new KiZuordnung(funktion, KiZuordnung.Status.AUS, null, null,
                grund + " Kein anderer eingerichteter Anbieter kann das.");
    }

    /**
     * Speichert die eigene Wahl einer Funktion; {@code null} heißt „Standard-Anbieter“.
     *
     * @throws IllegalArgumentException wenn der Anbieter die Funktion nicht kann
     */
    public void waehleFuerFunktion(KiFunktion funktion, KiFunktionsWahl wahl) {
        if (wahl != null) {
            KiAnbieter anbieter = anbieter(wahl.anbieter());
            Set<KiFaehigkeit> fehlend = fehlend(anbieter, funktion);
            if (!fehlend.isEmpty()) {
                throw new IllegalArgumentException(
                        anbieter.name() + " kann das in diesem Programm noch nicht (" + inWorten(fehlend) + ").");
            }
        }
        einstellungen.speichereFunktionsWahl(funktion, wahl);
    }

    private static Set<KiFaehigkeit> fehlend(KiAnbieter anbieter, KiFunktion funktion) {
        Set<KiFaehigkeit> fehlend = EnumSet.copyOf(funktion.braucht());
        fehlend.removeAll(anbieter.faehigkeiten());
        return fehlend;
    }

    private static String inWorten(Set<KiFaehigkeit> faehigkeiten) {
        return faehigkeiten.stream().map(KiFaehigkeit::bezeichnung).collect(Collectors.joining(", "));
    }

    // ==================== Anbieter ====================

    /** @throws IllegalArgumentException wenn es den Anbieter nicht gibt */
    public KiAnbieter anbieter(KiAnbieterTyp typ) {
        return anbieterListe.stream()
                .filter(a -> a.typ() == typ)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Diesen KI-Anbieter gibt es nicht."));
    }

    /** @throws IllegalArgumentException bei unbekanntem Namen */
    public KiAnbieter anbieter(String typ) {
        return anbieter(KiAnbieterTyp.lies(typ)
                .orElseThrow(() -> new IllegalArgumentException("Diesen KI-Anbieter gibt es nicht.")));
    }

    /**
     * Prüft Schlüssel und eingestellte Modelle. Ohne Eingabe gilt der gespeicherte
     * Schlüssel – so lässt sich ein gespeicherter Zugang testen, ohne ihn anzuzeigen.
     */
    public TestResult teste(KiAnbieter anbieter, String eingabe) {
        String schluessel = eingabe == null || eingabe.isBlank() ? einstellungen.schluessel(anbieter) : eingabe;
        List<String> modelle = new ArrayList<>();
        for (KiModellStufe stufe : KiModellStufe.values()) {
            modelle.add(einstellungen.modell(anbieter, stufe));
        }
        for (KiFunktion funktion : KiFunktion.values()) {
            einstellungen.funktionsWahl(funktion)
                    .filter(wahl -> wahl.anbieter() == anbieter.typ() && wahl.modell() != null)
                    .ifPresent(wahl -> modelle.add(wahl.modell()));
        }
        return anbieter.teste(schluessel, modelle.stream().distinct().toList());
    }

    // ==================== Modellliste ====================

    /**
     * Chat-Modelle aller Anbieter mit Schlüssel, für die Auswahl in den
     * Einstellungen. Je Anbieter für {@link #MODELLLISTE_GUELTIG} gemerkt.
     */
    public KiModellListeDto modellListe() {
        List<KiModellListeDto.Modell> modelle = new ArrayList<>();
        List<String> hinweise = new ArrayList<>();
        for (KiAnbieter anbieter : anbieterListe) {
            String schluessel = einstellungen.schluessel(anbieter);
            if (schluessel.isBlank()) {
                hinweise.add(anbieter.name() + ": Für diesen Anbieter ist noch kein Schlüssel hinterlegt – "
                        + "seine Modelle erscheinen erst danach.");
                continue;
            }
            try {
                for (KiModellInfo info : modelleVon(anbieter, schluessel)) {
                    if (info.chat()) {
                        modelle.add(new KiModellListeDto.Modell(anbieter.typ().name(), anbieter.name(),
                                info.modell(), info.name(), info.bilder()));
                    }
                }
            } catch (KiAnbieterFehler e) {
                hinweise.add(anbieter.name() + ": Die Modelle konnten nicht geladen werden. " + e.getMessage());
            }
        }
        modelle.sort(Comparator.comparing(KiModellListeDto.Modell::anbieterName)
                .thenComparing(KiModellListeDto.Modell::modell));
        return new KiModellListeDto(modelle, hinweise);
    }

    private List<KiModellInfo> modelleVon(KiAnbieter anbieter, String schluessel) {
        Instant jetzt = uhr.instant();
        // Nur ein Fingerabdruck des Schlüssels: ein neuer Schlüssel lädt neu, der Schlüssel selbst bleibt draußen.
        int fingerabdruck = schluessel.hashCode();
        GemerkteModelle gemerkt = modellListen.get(anbieter.typ());
        if (gemerkt != null && gemerkt.fingerabdruck() == fingerabdruck
                && gemerkt.geladen().plus(MODELLLISTE_GUELTIG).isAfter(jetzt)) {
            return gemerkt.modelle();
        }
        List<KiModellInfo> modelle = List.copyOf(anbieter.ladeModelle(schluessel));
        modellListen.put(anbieter.typ(), new GemerkteModelle(fingerabdruck, jetzt, modelle));
        return modelle;
    }

    private record GemerkteModelle(int fingerabdruck, Instant geladen, List<KiModellInfo> modelle) {
    }

    // ==================== Übersicht ====================

    public KiEinstellungenDto uebersicht() {
        List<KiEinstellungenDto.Anbieter> anbieter = anbieterListe.stream()
                .map(this::alsDto)
                .toList();
        List<KiEinstellungenDto.Funktion> funktionen = List.of(KiFunktion.values()).stream()
                .map(this::alsDto)
                .toList();
        return new KiEinstellungenDto(
                einstellungen.gewaehlterAnbieter().name(),
                einstellungen.ersatzErlaubt(),
                einstellungen.verschluesselungBereit(),
                anbieter,
                funktionen);
    }

    private KiEinstellungenDto.Anbieter alsDto(KiAnbieter anbieter) {
        List<KiEinstellungenDto.Modell> modelle = List.of(KiModellStufe.values()).stream()
                .map(stufe -> new KiEinstellungenDto.Modell(stufe.name(),
                        einstellungen.modell(anbieter, stufe), anbieter.standardModelle().get(stufe)))
                .toList();
        return new KiEinstellungenDto.Anbieter(
                anbieter.typ().name(),
                anbieter.name(),
                anbieter.beschreibung(),
                anbieter.datenstandort().name(),
                einstellungen.hatSchluessel(anbieter),
                anbieter.schluesselVerschluesselt(),
                namen(anbieter.faehigkeiten()),
                modelle);
    }

    private KiEinstellungenDto.Funktion alsDto(KiFunktion funktion) {
        KiZuordnung zuordnung = zuordnung(funktion);
        KiAnbieter anbieter = zuordnung.anbieter();
        KiEinstellungenDto.EigeneWahl eigeneWahl = einstellungen.funktionsWahl(funktion)
                .map(wahl -> new KiEinstellungenDto.EigeneWahl(wahl.anbieter().name(), wahl.modell()))
                .orElse(null);
        return new KiEinstellungenDto.Funktion(
                funktion.name(),
                funktion.bezeichnung(),
                namen(funktion.braucht()),
                zuordnung.status().name(),
                anbieter == null ? null : anbieter.typ().name(),
                anbieter == null ? null : anbieter.name(),
                anbieter == null ? null : modell(zuordnung, funktion.stufe()),
                zuordnung.hinweis(),
                eigeneWahl);
    }

    private static List<String> namen(Set<KiFaehigkeit> faehigkeiten) {
        return faehigkeiten.stream().sorted().map(Enum::name).toList();
    }
}
