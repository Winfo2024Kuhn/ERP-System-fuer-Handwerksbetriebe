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
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
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
     * @throws KiNichtVerfuegbar  wenn für die Funktion kein Anbieter eingerichtet ist
     * @throws KiInhaltNichtLesbar wenn die Funktion läuft, aber kein erlaubter Anbieter ein Dateiformat
     *                             dieser Anfrage liest (dauerhaft, die Meldung darf zum Nutzer)
     * @throws KiAnbieterFehler   wenn der Anbieter nicht oder mit einem Fehler antwortet
     */
    public KiErgebnis sende(KiFunktion funktion, KiModellStufe stufe, KiAnfrage anfrage) {
        // Mit den Dateien der Anfrage: Ein Format, das der gewählte Anbieter nicht liest, zählt wie
        // eine fehlende Fähigkeit (Ersatz, wenn erlaubt – sonst eine klare Meldung).
        KiZuordnung zuordnung = zuordnung(funktion, anfrage.dateien());
        if (!zuordnung.verfuegbar()) {
            if (!anfrage.dateien().isEmpty() && zuordnung(funktion).verfuegbar()) {
                // Die Funktion ist an, nur dieses Format liest kein erlaubter Anbieter: kein „aus“,
                // sondern der Grund – so erfährt der Nutzer, was er an der Datei ändern kann.
                throw new KiInhaltNichtLesbar(zuordnung.hinweis());
            }
            throw new KiNichtVerfuegbar(funktion, zuordnung.hinweis());
        }
        KiAnbieter anbieter = zuordnung.anbieter();
        return anbieter.sende(anfrage, modell(zuordnung, stufe), einstellungen.schluessel(anbieter));
    }

    public boolean verfuegbar(KiFunktion funktion) {
        return zuordnung(funktion).verfuegbar();
    }

    /**
     * Kann der Anbieter, über den die Funktion gerade läuft, auch diese Fähigkeit – etwa die
     * Internet-Suche? {@code false}, wenn die Funktion aus ist.
     */
    public boolean kann(KiFunktion funktion, KiFaehigkeit faehigkeit) {
        KiZuordnung zuordnung = zuordnung(funktion);
        return zuordnung.verfuegbar() && zuordnung.anbieter().faehigkeiten().contains(faehigkeit);
    }

    /**
     * Gemini-Schlüssel, um eine Frage für die Suche im Programm-Wissen einzubetten. Der
     * Wissens-Index besteht aus Gemini-Vektoren, also geht die Frage dafür an Google – aber nur,
     * wenn die Funktion gerade ohnehin über Gemini läuft (gewählt oder als erlaubter Ersatz).
     * Sonst leer: Die Suche nimmt dann nur die Wortsuche, und nichts geht an Google.
     */
    public Optional<String> geminiEinbettungsSchluessel(KiFunktion funktion) {
        KiZuordnung zuordnung = zuordnung(funktion);
        if (!zuordnung.verfuegbar() || zuordnung.anbieter().typ() != KiAnbieterTyp.GEMINI) {
            return Optional.empty();
        }
        String schluessel = einstellungen.schluessel(zuordnung.anbieter());
        return schluessel.isBlank() ? Optional.empty() : Optional.of(schluessel);
    }

    /** Modell, mit dem die Funktion auf dieser Stufe gerade liefe; leer, wenn sie aus ist. */
    public Optional<String> modell(KiFunktion funktion, KiModellStufe stufe) {
        KiZuordnung zuordnung = zuordnung(funktion);
        return zuordnung.verfuegbar() ? Optional.of(modell(zuordnung, stufe)) : Optional.empty();
    }

    /**
     * Ein eigenes Modell der Funktion gilt für jede Stufe; sonst ein Modell, das der Anbieter für
     * diese Fähigkeiten eigens nimmt (z. B. Ton); sonst das Modell der Stufe.
     */
    private String modell(KiZuordnung zuordnung, KiModellStufe stufe) {
        if (zuordnung.modellVorgabe() != null) {
            return zuordnung.modellVorgabe();
        }
        KiAnbieter anbieter = zuordnung.anbieter();
        return anbieter.modellFuer(zuordnung.funktion().braucht())
                .orElseGet(() -> einstellungen.modell(anbieter, stufe));
    }

    /** Hat irgendein Anbieter einen Schlüssel? Dann sind KI-Knöpfe grundsätzlich an. */
    public boolean irgendeinAnbieterEingerichtet() {
        return new Stand().irgendeinAnbieterEingerichtet();
    }

    /**
     * Was die Oberfläche beim Anmelden wissen muss: ob überhaupt ein Anbieter eingerichtet ist und
     * welche Funktionen gerade laufen. Liest die Einstellungen dafür nur einmal (ein {@link Stand}
     * für alle Funktionen) – sonst läse jede Funktion Auswahl, Ersatz-Schalter und Schlüssel neu.
     * Das Ergebnis ist dasselbe wie {@link #irgendeinAnbieterEingerichtet()} und
     * {@link #verfuegbar(KiFunktion)} je Funktion.
     */
    public KiVerfuegbarkeit verfuegbarkeit() {
        Stand stand = new Stand();
        List<KiFunktion> funktionen = Arrays.stream(KiFunktion.values())
                .filter(funktion -> zuordnung(funktion, List.of(), stand).verfuegbar())
                .toList();
        return new KiVerfuegbarkeit(stand.irgendeinAnbieterEingerichtet(), funktionen);
    }

    // ==================== Zuordnung ====================

    /** Die Zuordnung der Funktion im Allgemeinen – so zeigt sie die Einstellungsseite. */
    public KiZuordnung zuordnung(KiFunktion funktion) {
        return zuordnung(funktion, List.of());
    }

    private KiZuordnung zuordnung(KiFunktion funktion, List<KiTeil.Datei> dateien) {
        return zuordnung(funktion, dateien, new Stand());
    }

    /**
     * @param dateien die Dateien einer konkreten Anfrage; ein Anbieter, der eines ihrer Formate
     *                nicht liest, kommt für diese Anfrage nicht in Frage (leer: nur die Fähigkeiten zählen)
     * @param stand   die Einstellungen, auf denen die Entscheidung beruht
     */
    private KiZuordnung zuordnung(KiFunktion funktion, List<KiTeil.Datei> dateien, Stand stand) {
        Optional<KiFunktionsWahl> wahl = stand.funktionsWahl(funktion);
        KiAnbieter gewaehlt = anbieter(wahl.map(KiFunktionsWahl::anbieter)
                .orElseGet(stand::gewaehlterAnbieter));
        Optional<String> hindernis = hindernis(gewaehlt, funktion, dateien);
        if (hindernis.isEmpty() && stand.hatSchluessel(gewaehlt)) {
            return new KiZuordnung(funktion, KiZuordnung.Status.AKTIV, gewaehlt,
                    wahl.map(KiFunktionsWahl::modell).orElse(null), zusatzHinweis(gewaehlt, funktion).orElse(null));
        }
        if (hindernis.isEmpty()
                && stand.schluesselZustand(gewaehlt) == KiEinstellungen.SchluesselZustand.NICHT_LESBAR) {
            // Der Betrieb hat diesen Anbieter eingerichtet, nur der Server kann den Schlüssel gerade nicht
            // lesen. Kein Ersatz: Sonst gingen Daten unbemerkt an einen anderen Anbieter (DSGVO).
            return new KiZuordnung(funktion, KiZuordnung.Status.AUS, null, null,
                    "Der Schlüssel für " + gewaehlt.name() + " lässt sich auf diesem Server nicht entschlüsseln. "
                            + "Bitte unter Einstellungen → KI-Funktionen neu eintragen. "
                            + "Bis dahin springt kein anderer Anbieter ein.");
        }

        String grund = hindernis.orElseGet(() -> "Für " + gewaehlt.name() + " ist noch kein Schlüssel hinterlegt.");

        Optional<KiAnbieter> ersatz = anbieterListe.stream()
                .filter(a -> a != gewaehlt)
                .filter(a -> hindernis(a, funktion, dateien).isEmpty())
                .filter(stand::hatSchluessel)
                .findFirst();

        if (!stand.ersatzErlaubt()) {
            String zusatz = ersatz.isPresent() ? " Ersatz-Anbieter sind ausgeschaltet." : "";
            return new KiZuordnung(funktion, KiZuordnung.Status.AUS, null, null, grund + zusatz);
        }
        if (ersatz.isPresent()) {
            // Das eigene Modell gehört zum gewählten Anbieter, nicht zum Ersatz.
            return new KiZuordnung(funktion, KiZuordnung.Status.ERSATZ, ersatz.get(), null,
                    grund + " Es springt " + ersatz.get().name() + " ein."
                            + zusatzHinweis(ersatz.get(), funktion).map(zusatz -> " " + zusatz).orElse(""));
        }
        return new KiZuordnung(funktion, KiZuordnung.Status.AUS, null, null,
                grund + " Kein anderer eingerichteter Anbieter kann das.");
    }

    /**
     * Ein Blick auf die KI-Einstellungen, der jede Einstellung höchstens einmal liest (jeder Zugriff
     * auf {@link KiEinstellungen} ist ein Datenbank-Read, ein Schlüssel wird dabei auch entschlüsselt).
     * Gelesen wird erst, wenn eine Entscheidung den Wert braucht. Gilt nur für einen Aufruf: Jede
     * einzelne Zuordnung nimmt einen frischen Stand, damit Änderungen sofort wirken.
     */
    private final class Stand {
        private KiAnbieterTyp gewaehlterAnbieter;
        private Boolean ersatzErlaubt;
        private final Map<KiFunktion, Optional<KiFunktionsWahl>> wahlen = new EnumMap<>(KiFunktion.class);
        private final Map<KiAnbieterTyp, KiEinstellungen.SchluesselZustand> schluessel =
                new EnumMap<>(KiAnbieterTyp.class);

        KiAnbieterTyp gewaehlterAnbieter() {
            if (gewaehlterAnbieter == null) {
                gewaehlterAnbieter = einstellungen.gewaehlterAnbieter();
            }
            return gewaehlterAnbieter;
        }

        boolean ersatzErlaubt() {
            if (ersatzErlaubt == null) {
                ersatzErlaubt = einstellungen.ersatzErlaubt();
            }
            return ersatzErlaubt;
        }

        Optional<KiFunktionsWahl> funktionsWahl(KiFunktion funktion) {
            return wahlen.computeIfAbsent(funktion, einstellungen::funktionsWahl);
        }

        KiEinstellungen.SchluesselZustand schluesselZustand(KiAnbieter anbieter) {
            return schluessel.computeIfAbsent(anbieter.typ(), typ -> einstellungen.schluesselZustand(anbieter));
        }

        boolean hatSchluessel(KiAnbieter anbieter) {
            return schluesselZustand(anbieter) == KiEinstellungen.SchluesselZustand.VORHANDEN;
        }

        boolean irgendeinAnbieterEingerichtet() {
            return anbieterListe.stream().anyMatch(this::hatSchluessel);
        }
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

    /** Fehlende Zusätze und bekannte Grenzen des Anbieters, als ein Hinweis. */
    private static Optional<String> zusatzHinweis(KiAnbieter anbieter, KiFunktion funktion) {
        List<String> teile = new ArrayList<>();
        ohneZusatz(anbieter, funktion).ifPresent(teile::add);
        anbieter.einschraenkung(funktion).ifPresent(teile::add);
        return teile.isEmpty() ? Optional.empty() : Optional.of(String.join(" ", teile));
    }

    private static Optional<String> ohneZusatz(KiAnbieter anbieter, KiFunktion funktion) {
        Set<KiFaehigkeit> fehlend = funktion.nutztWennMoeglich();
        fehlend.removeAll(anbieter.faehigkeiten());
        if (fehlend.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Läuft ohne " + inWorten(fehlend) + " – das kann " + anbieter.name()
                + " in diesem Programm nicht.");
    }

    /**
     * Warum der Anbieter die Funktion (mit diesen Dateien) nicht übernehmen kann – als Satz für
     * die Oberfläche; leer, wenn nichts dagegenspricht.
     */
    private static Optional<String> hindernis(KiAnbieter anbieter, KiFunktion funktion, List<KiTeil.Datei> dateien) {
        Set<KiFaehigkeit> fehlend = fehlend(anbieter, funktion);
        if (!fehlend.isEmpty()) {
            return Optional.of(anbieter.name() + " kann das in diesem Programm noch nicht (" + inWorten(fehlend) + ").");
        }
        for (KiTeil.Datei datei : dateien) {
            if (!anbieter.liest(datei)) {
                String art = datei.istAudio() ? "Aufnahmeformat" : datei.istBild() ? "Bildformat" : "Dateiformat";
                return Optional.of(anbieter.name() + " kann dieses " + art + " nicht lesen (" + datei.mimeType() + ").");
            }
        }
        return Optional.empty();
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
                                info.modell(), info.name(), info.bilder(), info.ton()));
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
        Stand stand = new Stand();
        List<KiEinstellungenDto.Anbieter> anbieter = anbieterListe.stream()
                .map(a -> alsDto(a, stand))
                .toList();
        List<KiEinstellungenDto.Funktion> funktionen = Arrays.stream(KiFunktion.values())
                .map(funktion -> alsDto(funktion, stand))
                .toList();
        return new KiEinstellungenDto(
                stand.gewaehlterAnbieter().name(),
                stand.ersatzErlaubt(),
                einstellungen.verschluesselungBereit(),
                anbieter,
                funktionen);
    }

    private KiEinstellungenDto.Anbieter alsDto(KiAnbieter anbieter, Stand stand) {
        List<KiEinstellungenDto.Modell> modelle = List.of(KiModellStufe.values()).stream()
                .map(stufe -> new KiEinstellungenDto.Modell(stufe.name(),
                        einstellungen.modell(anbieter, stufe), anbieter.standardModelle().get(stufe)))
                .toList();
        return new KiEinstellungenDto.Anbieter(
                anbieter.typ().name(),
                anbieter.name(),
                anbieter.beschreibung(),
                anbieter.datenstandort().name(),
                stand.hatSchluessel(anbieter),
                stand.schluesselZustand(anbieter) == KiEinstellungen.SchluesselZustand.NICHT_LESBAR,
                anbieter.schluesselVerschluesselt(),
                namen(anbieter.faehigkeiten()),
                modelle);
    }

    private KiEinstellungenDto.Funktion alsDto(KiFunktion funktion, Stand stand) {
        KiZuordnung zuordnung = zuordnung(funktion, List.of(), stand);
        KiAnbieter anbieter = zuordnung.anbieter();
        KiEinstellungenDto.EigeneWahl eigeneWahl = stand.funktionsWahl(funktion)
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
