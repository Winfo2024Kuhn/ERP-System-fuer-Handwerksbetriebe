## Worum es geht

Auf einem 14-Zoll-MacBook (1440 × 900) war in der PC-Oberfläche auf mehreren Seiten rechts etwas abgeschnitten, ohne dass ein Scrollbalken erschien. Am schlimmsten im Projekt-Editor: Die rechte Spalte „Projektdaten" ragte 170 px aus dem Fenster und war nur noch als schmaler Streifen zu sehen. Dazu abgeschnittene Beschriftungen in der Menüleiste, gequetschte Kennzahlen in den Kopfzeilen und abgehackte Titel auf den Übersichtskarten.

Ursache war kein Einzelfehler, sondern eine Fehlerklasse, die an 60 Stellen steckte: Elemente, die ihre automatische Mindestbreite behalten und dadurch ihren Nachbarn oder ihre Karte sprengen.

## Was sich ändert

- **Zwei-Spalten-Raster der Detailseiten** lässt seine Spalten wieder schrumpfen. Das ist die Zeile, die den Projekt-Editor rettet, und sie wirkt für alle fünf Detailseiten.
- **Kopfzeilen** brechen um, statt Kennzahlen und Knöpfe zu verdrängen. Die Knöpfe bleiben rechts, auch nach einem Umbruch.
- **Reiterleisten** brechen um statt versteckt zu scrollen; im Projekt-Editor heißen drei Reiter kürzer („Geschäftsdokumente", „Material", „Tagebuch").
- **Menüleiste** zeigt alle fünf Kategorien und die vollen Menü-Beschriftungen; der Nutzername wird nur dort gekürzt, wo es nötig ist.
- **Übersichtskarten** zeigen zweizeilige Titel und bei 1440 drei statt vier Karten je Reihe.
- **Rund 60 Wertfelder** in den fünf Detailseiten brechen jetzt um, statt still über ihren Kasten zu laufen.
- **Dialog-Komponente** setzt `role="dialog"` und `aria-modal` — nebenbei eine Lücke für Screenreader.

Auf 1920 × 1080 sieht alles aus wie vorher, bis auf die umbenannten Reiter und die vollständigen Menü-Beschriftungen.

## Wie es geprüft ist

Die Design-Prüfung mit Playwright wurde erweitert und läuft jetzt scharf für jede Spec: Überlauf der Seite, Text über seinen Kasten, echte Kürzungen, Überschneidungen mit Rücksicht auf scrollende Bereiche und Sticky-Leisten.

| Messung | vorher | nachher |
| --- | --- | --- |
| End-to-End-Tests | 110 | 390 |
| Geprüfte Bildschirmgrößen | 2 | 3 (1440, 1536, 1920) |
| Typprüfung der Testdateien | keine | `npm run typecheck:e2e` |
| Netzwerkzugriff aus Tests | ungebremst | für alle 17 Specs abgeriegelt |

Zehn Abschnitte, jeder von einem Code-Reviewer und einem Design-Reviewer geprüft, der die Screenshots wirklich angeschaut und im Browser nachgemessen hat.

## Was bewusst offen bleibt

- Vorbestehende Lücken derselben Art in Dialogen, die keine Spec öffnet.
- Fremde Farben aus dem Bestand, zwei Kennzahlen-Bauweisen nebeneinander, der Mitarbeiter-Editor sieht anders aus als die anderen vier. Alles Umgestaltung, nicht Robustheit.
- Bei 1536 px sind die Karten schmaler als bei 1440 — geprüft und in Ordnung, aber die Annahme „1440 ist der härteste Fall" gilt für Kartenraster nicht.

Spec, Plan und ein Kontext-Log mit allen Messwerten liegen unter `docs/superpowers/`.
