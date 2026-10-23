package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der feste Hinweistext der KI-Hilfe beschreibt Menü und Seitenlinks. Er lief der
 * Menüleiste davon (Issue #275). Dieser Test vergleicht ihn mit
 * {@code react-pc-frontend/src/components/layout/navigation.ts} (Quelle der Menüleiste)
 * und den Routen in {@code App.tsx}: Kommt ein Menüpunkt dazu, schlägt er fehl, bis der
 * Hinweistext nachgezogen ist.
 */
class KiHilfeMenueAbgleichTest {

    private static final Path NAVIGATION = Path.of("react-pc-frontend", "src", "components", "layout", "navigation.ts");
    private static final Path APP = Path.of("react-pc-frontend", "src", "App.tsx");

    private static final Pattern KATEGORIE = Pattern.compile("category:\\s*+'([^']++)'");
    private static final Pattern UNTERGRUPPE = Pattern.compile("label:\\s*+'([^']++)'");
    private static final Pattern EINTRAG = Pattern.compile("\\{\\s*+name:\\s*+'([^']++)',\\s*+href:\\s*+('([^']++)'|BESTELLBEDARF_PFAD)");
    private static final Pattern LINK = Pattern.compile("\\]\\((/[^)\\s]++)\\)");
    private static final Pattern ROUTE = Pattern.compile("path=\"(/[^\"]*+)\"");

    /** Kategorie → Untergruppe → Menüpunkte (Name → Pfad), in Menü-Reihenfolge. */
    private static final Map<String, Map<String, Map<String, String>>> MENUE = new LinkedHashMap<>();
    private static final Set<String> ROUTEN = new LinkedHashSet<>();
    private static String navigationsAbschnitt;
    private static String linkAbschnitt;

    @BeforeAll
    static void einlesen() throws IOException {
        String kategorie = null;
        String untergruppe = null;
        for (String zeile : Files.readAllLines(finde(NAVIGATION))) {
            Matcher k = KATEGORIE.matcher(zeile);
            Matcher u = UNTERGRUPPE.matcher(zeile);
            Matcher e = EINTRAG.matcher(zeile);
            if (k.find()) {
                kategorie = k.group(1);
                MENUE.put(kategorie, new LinkedHashMap<>());
            } else if (u.find() && kategorie != null) {
                untergruppe = u.group(1);
                MENUE.get(kategorie).put(untergruppe, new LinkedHashMap<>());
            } else if (e.find() && untergruppe != null && e.group(3) != null) {
                // BESTELLBEDARF_PFAD ist noch nicht freigegeben (lib/freigaben.ts) und fehlt bewusst.
                MENUE.get(kategorie).get(untergruppe).put(e.group(1), e.group(3));
            }
        }
        Matcher r = ROUTE.matcher(Files.readString(finde(APP)));
        while (r.find()) {
            ROUTEN.add(r.group(1));
        }

        String prompt = KiHilfeService.BASE_SYSTEM_PROMPT;
        int nav = prompt.indexOf("## Navigationsstruktur");
        int links = prompt.indexOf("## Seitenlinks");
        int tabelle = prompt.indexOf("| Seite | Link |", links);
        assertThat(nav).as("Abschnitt Navigationsstruktur im Hinweistext").isPositive();
        assertThat(links).as("Abschnitt Seitenlinks im Hinweistext").isGreaterThan(nav);
        assertThat(tabelle).as("Link-Tabelle im Hinweistext").isGreaterThan(links);
        navigationsAbschnitt = prompt.substring(nav, links);
        // Nur die Tabelle – davor steht das Format-Beispiel „[Seitenname](/route)“.
        linkAbschnitt = prompt.substring(tabelle);
    }

    @Test
    @DisplayName("Menü wurde gelesen (sonst prüft der Test nichts)")
    void menueGelesen() {
        assertThat(MENUE).hasSizeGreaterThanOrEqualTo(5);
        assertThat(ROUTEN).contains("/projekte", "/kalender");
    }

    @Test
    @DisplayName("Jeder Hauptreiter, jede Gruppe und jeder Menüpunkt steht in der Navigationsstruktur")
    void navigationsstrukturPasstZumMenue() {
        List<String> fehlt = new ArrayList<>();
        MENUE.forEach((kategorie, gruppen) -> {
            String abschnitt = kategorieAbschnitt(kategorie);
            if (abschnitt == null) {
                fehlt.add("Hauptreiter „" + kategorie + "“");
                return;
            }
            gruppen.forEach((gruppe, eintraege) -> {
                String zeile = zeileMit(abschnitt, "- **" + gruppe + ":**");
                if (zeile == null) {
                    fehlt.add(kategorie + " → Gruppe „" + gruppe + "“");
                    return;
                }
                eintraege.keySet().stream()
                        .filter(name -> !zeile.contains(name))
                        .forEach(name -> fehlt.add(kategorie + " → " + gruppe + " → „" + name + "“"));
            });
        });
        assertThat(fehlt).as("Im Hinweistext der KI-Hilfe (KiHilfeService) fehlen Menüpunkte").isEmpty();
    }

    @Test
    @DisplayName("Jeder Menüpunkt hat einen Seitenlink")
    void jederMenuepunktHatLink() {
        List<String> fehlt = MENUE.values().stream()
                .flatMap(g -> g.values().stream())
                .flatMap(e -> e.values().stream())
                .filter(pfad -> !linkAbschnitt.contains("](" + pfad + ")"))
                .toList();
        assertThat(fehlt).as("Seitenlinks ohne Eintrag im Hinweistext der KI-Hilfe").isEmpty();
    }

    @Test
    @DisplayName("Jeder Seitenlink führt auf eine echte Seite")
    void jederLinkFuehrtAufEineSeite() {
        Matcher m = LINK.matcher(linkAbschnitt);
        List<String> tot = new ArrayList<>();
        while (m.find()) {
            if (!istRoute(m.group(1))) {
                tot.add(m.group(1));
            }
        }
        assertThat(tot).as("Seitenlinks im Hinweistext ohne Route in App.tsx").isEmpty();
    }

    private static boolean istRoute(String pfad) {
        String[] teile = pfad.split("/", -1);
        for (String route : ROUTEN) {
            String[] muster = route.split("/", -1);
            if (muster.length != teile.length) {
                continue;
            }
            boolean passt = true;
            for (int i = 0; i < muster.length && passt; i++) {
                passt = muster[i].startsWith(":") || muster[i].equals(teile[i]);
            }
            if (passt) {
                return true;
            }
        }
        return false;
    }

    private static String kategorieAbschnitt(String kategorie) {
        Matcher m = Pattern.compile("### \\d++\\. " + Pattern.quote(kategorie) + "\\n").matcher(navigationsAbschnitt);
        if (!m.find()) {
            return null;
        }
        int naechster = navigationsAbschnitt.indexOf("###", m.end());
        return navigationsAbschnitt.substring(m.end(), naechster < 0 ? navigationsAbschnitt.length() : naechster);
    }

    private static String zeileMit(String abschnitt, String anfang) {
        return abschnitt.lines().map(String::strip).filter(z -> z.startsWith(anfang)).findFirst().orElse(null);
    }

    /** Sucht die Datei vom Arbeitsverzeichnis aus nach oben (Maven: Projektwurzel, IDE: evtl. Unterordner). */
    private static Path finde(Path relativ) {
        for (Path ordner = Path.of(System.getProperty("user.dir")).toAbsolutePath(); ordner != null; ordner = ordner.getParent()) {
            Path kandidat = ordner.resolve(relativ);
            if (Files.isRegularFile(kandidat)) {
                return kandidat;
            }
        }
        throw new AssertionError(relativ + " nicht gefunden (gesucht ab " + System.getProperty("user.dir") + " aufwärts)");
    }
}
