package org.example.kalkulationsprogramm.service.dokumentstand;

import static org.example.kalkulationsprogramm.service.dokumentstand.StandTextFormat.betrag;
import static org.example.kalkulationsprogramm.service.dokumentstand.StandTextFormat.prozent;
import static org.example.kalkulationsprogramm.service.dokumentstand.StandTextFormat.zahl;
import static org.example.kalkulationsprogramm.service.dokumentstand.StandTextFormat.zitat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.example.kalkulationsprogramm.service.dokumentstand.DokumentPositionen.Abschnitt;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentPositionen.Leistung;
import org.example.kalkulationsprogramm.service.dokumentstand.DokumentPositionen.Text;

/**
 * Vergleicht die Blöcke zweier Stände. Zugeordnet wird über die Block-ID (auch für Leistungen
 * in Bauabschnitten); Blöcke ohne ID bekommen ihre Lage als Ersatzschlüssel.
 */
final class PositionenVergleich {

    private PositionenVergleich() {
    }

    static void vergleiche(DokumentPositionen alt, DokumentPositionen neu, List<String> saetze) {
        abschnitte(alt.abschnitte(), neu.abschnitte(), saetze);
        leistungen(alt.leistungen(), neu.leistungen(), saetze);
        texte(alt.texte(), neu.texte(), saetze);
        reihenfolge(alt.leistungen(), neu.leistungen(), saetze);
        gesamtRabatt(alt.gesamtRabatt(), neu.gesamtRabatt(), saetze);
    }

    private static void abschnitte(List<Abschnitt> alt, List<Abschnitt> neu, List<String> saetze) {
        Map<String, Abschnitt> altNachId = nachId(alt, Abschnitt::id);
        Map<String, Abschnitt> neuNachId = nachId(neu, Abschnitt::id);
        neuNachId.forEach((id, n) -> {
            Abschnitt a = altNachId.get(id);
            if (a != null && !a.name().equals(n.name())) {
                saetze.add("Bauabschnitt umbenannt: " + zitat(a.name()) + " → " + zitat(n.name()));
            }
        });
        altNachId.forEach((id, a) -> {
            if (!neuNachId.containsKey(id)) {
                saetze.add("Bauabschnitt " + zitat(a.name()) + " gelöscht");
            }
        });
        neuNachId.forEach((id, n) -> {
            if (!altNachId.containsKey(id)) {
                saetze.add("Bauabschnitt " + zitat(n.name()) + " hinzugefügt");
            }
        });
    }

    private static void leistungen(List<Leistung> alt, List<Leistung> neu, List<String> saetze) {
        Map<String, Leistung> altNachId = nachId(alt, Leistung::id);
        Map<String, Leistung> neuNachId = nachId(neu, Leistung::id);
        altNachId.forEach((id, a) -> {
            if (!neuNachId.containsKey(id)) {
                saetze.add("Pos. " + a.pos() + " " + zitat(a.titel()) + " gelöscht");
            }
        });
        neuNachId.forEach((id, n) -> {
            Leistung a = altNachId.get(id);
            if (a == null) {
                saetze.add("Pos. " + n.pos() + " " + zitat(n.titel()) + " hinzugefügt");
            } else {
                felder(a, n, saetze);
            }
        });
    }

    private static void felder(Leistung a, Leistung n, List<String> saetze) {
        String pos = "Pos. " + n.pos();
        if (!a.titel().equals(n.titel())) {
            saetze.add("Bezeichnung " + pos + ": " + zitat(a.titel()) + " → " + zitat(n.titel()));
        }
        if (Double.compare(a.menge(), n.menge()) != 0) {
            String einheit = n.einheit().isEmpty() ? "" : " " + n.einheit();
            saetze.add("Menge " + pos + ": " + zahl(a.menge()) + " → " + zahl(n.menge()) + einheit);
        }
        if (!a.einheit().equals(n.einheit())) {
            saetze.add("Einheit " + pos + ": " + zitat(a.einheit()) + " → " + zitat(n.einheit()));
        }
        if (Double.compare(a.preis(), n.preis()) != 0) {
            saetze.add("Preis " + pos + ": " + betrag(a.preis()) + " → " + betrag(n.preis()));
        }
        if (Double.compare(a.rabatt(), n.rabatt()) != 0) {
            saetze.add("Rabatt " + pos + ": " + prozent(a.rabatt()) + " → " + prozent(n.rabatt()));
        }
        if (!a.beschreibung().equals(n.beschreibung())) {
            saetze.add("Beschreibung " + pos + " geändert");
        }
        if (a.wahl() != n.wahl()) {
            saetze.add(pos + (n.wahl() ? " ist jetzt Wahlposition" : " ist keine Wahlposition mehr"));
        }
    }

    private static void texte(List<Text> alt, List<Text> neu, List<String> saetze) {
        Map<String, Text> altNachId = nachId(alt, Text::id);
        Map<String, Text> neuNachId = nachId(neu, Text::id);
        boolean hinzugefuegt = neuNachId.keySet().stream().anyMatch(id -> !altNachId.containsKey(id));
        boolean geaendert = neuNachId.entrySet().stream().anyMatch(e -> altNachId.containsKey(e.getKey())
                && !altNachId.get(e.getKey()).inhalt().equals(e.getValue().inhalt()));
        boolean geloescht = altNachId.keySet().stream().anyMatch(id -> !neuNachId.containsKey(id));
        if (hinzugefuegt) {
            saetze.add("Text hinzugefügt");
        }
        if (geaendert) {
            saetze.add("Text geändert");
        }
        if (geloescht) {
            saetze.add("Text gelöscht");
        }
    }

    /** Nur wenn dieselben (gemeinsamen) Positionen jetzt in anderer Ordnung stehen. */
    private static void reihenfolge(List<Leistung> alt, List<Leistung> neu, List<String> saetze) {
        List<String> altIds = new ArrayList<>(nachId(alt, Leistung::id).keySet());
        List<String> neuIds = new ArrayList<>(nachId(neu, Leistung::id).keySet());
        List<String> gemeinsamAlt = altIds.stream().filter(neuIds::contains).toList();
        List<String> gemeinsamNeu = neuIds.stream().filter(altIds::contains).toList();
        if (!gemeinsamAlt.equals(gemeinsamNeu)) {
            saetze.add("Reihenfolge der Positionen geändert");
        }
    }

    private static void gesamtRabatt(double alt, double neu, List<String> saetze) {
        if (Double.compare(alt, neu) == 0) {
            return;
        }
        if (alt <= 0) {
            saetze.add("Rabatt " + prozent(neu) + " gegeben");
        } else if (neu <= 0) {
            saetze.add("Rabatt " + prozent(alt) + " entfernt");
        } else {
            saetze.add("Rabatt " + prozent(alt) + " → " + prozent(neu));
        }
    }

    /** Reihenfolge bleibt erhalten; Blöcke ohne ID bekommen „#Lage“ als Schlüssel. */
    private static <T> Map<String, T> nachId(List<T> bloecke, Function<T, String> id) {
        Map<String, T> ergebnis = new LinkedHashMap<>();
        for (int i = 0; i < bloecke.size(); i++) {
            T block = bloecke.get(i);
            String schluessel = id.apply(block);
            if (schluessel == null || schluessel.isBlank() || ergebnis.containsKey(schluessel)) {
                schluessel = "#" + i;
            }
            ergebnis.put(schluessel, block);
        }
        return ergebnis;
    }
}
