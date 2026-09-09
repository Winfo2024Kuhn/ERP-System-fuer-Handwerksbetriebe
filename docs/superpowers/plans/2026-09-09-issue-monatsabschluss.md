<!-- Angelegt als Issue #98:
     https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/98
     Diese Datei ist die Quelle des Issue-Texts. Änderungen hier gehen NICHT
     automatisch nach GitHub — bei Bedarf mit 'gh issue edit 98 --body-file' nachziehen. -->

## Problem

Ein einmal berechneter Monat ist heute nie endgültig. Jede spätere Änderung an Stammdaten oder am Rechenkern kann ihn neu berechnen und damit einen längst abgerechneten Saldo verschieben.

`MonatsSaldo` (`src/main/java/org/example/kalkulationsprogramm/domain/MonatsSaldo.java`) ist ein **Cache, kein Abschluss**. Das Feld `gueltig` sagt nur, ob der gespeicherte Wert noch frisch ist — nicht, ob er überhaupt noch geändert werden darf. Es gibt keinen Zustand „dieser Monat ist fertig, Finger weg".

## Wie der Fehler entsteht

Drei Wege, auf denen ein abgeschlossener Monat heute stillschweigend andere Zahlen bekommt:

**1. Zeitkonto-Änderung invalidiert die gesamte Vergangenheit.**

`ZeitverwaltungController.updateZeitkonto()` wirft bei jeder Änderung der Sollstunden alle Monate des Mitarbeiters weg (`src/main/java/org/example/kalkulationsprogramm/controller/ZeitverwaltungController.java:663-664`):

```java
// Sollstunden haben sich geändert → ALLE MonatsSaldo-Caches invalidieren
monatsSaldoService.invalidiereAlle(mitarbeiterId);
```

Danach wird jeder Altmonat gegen das **neue** Wochenmodell neu gerechnet — auch der von vor drei Jahren.

**2. Die Abwesenheitsstunden laufen dabei nicht mit.**

`AbwesenheitService.bucheAbwesenheit()` (`src/main/java/org/example/kalkulationsprogramm/service/AbwesenheitService.java:60-61`) berechnet die Stunden einer Abwesenheit **einmalig beim Buchen** aus dem damals gültigen Zeitkonto und schreibt sie in `abwesenheit.stunden` (`Abwesenheit.java:50-51`). Danach werden sie nie wieder berechnet. `MonatsSaldoService:128` zieht sie als gespeicherte Summe über `abwesenheitRepository.sumStundenByMitarbeiterIdAndDatumBetween`.

Bei einer Neuberechnung wird die Soll-Seite also frisch gerechnet, die Abwesenheits-Seite bleibt der alte Snapshot. Das ergibt Überstunden aus dem Nichts:

> Ein Mitarbeiter mit 8-Stunden-Tagen nimmt 25 Urlaubstage, gebucht mit je 8 Stunden. Der Betrieb stellt ihn auf 6-Stunden-Tage um. `invalidiereAlle` wirft alle Altmonate weg, das Soll sinkt auf 6 Stunden je Tag, die Urlaubsgutschrift bleibt bei 8 — macht 2 Stunden Plus je Urlaubstag, 50 Überstunden aus dem Nichts, rückwirkend über die gesamte Betriebszugehörigkeit.

**3. Jeder künftige Bugfix im Rechenkern verschiebt Altmonate.**

Wer einen Fehler in `TagesSollService` oder `MonatsSaldoService` behebt, ändert damit das Ergebnis jedes Monats, der danach neu berechnet wird. Das lässt sich durch keine Historisierung der Stammdaten abfangen — nur durch einen Riegel, der die Neuberechnung selbst unterbindet.

## Vorschlag

Die Infrastruktur ist weitgehend vorhanden. `MonatsSaldo` hat bereits `jahr`, `monat`, `gueltig`, `berechnetAm`, `istStunden`, `sollStunden`, `abwesenheitsStunden`, `feiertagsStunden` und `korrekturStunden`. Es fehlen:

1. **Eine Spalte `festgeschrieben`**, samt Angabe wer wann abgeschlossen hat — die Nachvollziehbarkeit ist der halbe Zweck. Dazu eine Flyway-Migration (nächste freie Nummer: V368, V367 ist belegt).

2. **Alle `invalidiere*`-Methoden überspringen festgeschriebene Monate.** Betrifft `invalidiereMonat`, `invalidiereJahr`, `invalidiereAlle`, `invalidiereFuerDatum` und `invalidiereFuerDateTime` in `MonatsSaldoService` (Zeilen 205–247). `getOrBerechne` gibt einen festgeschriebenen Saldo unverändert zurück, statt ihn neu zu rechnen.

3. **`MonatsSaldoWarmupService` überspringt sie ebenfalls.** Er läuft beim Anwendungsstart über alle aktiven Mitarbeiter (`MonatsSaldoWarmupService.java:44`) und würde sonst beim nächsten Neustart genau das tun, was der Abschluss verhindern soll.

4. **Oberfläche zum Abschließen und zum Wieder-Öffnen.** Das Öffnen muss möglich bleiben — es gibt echte Korrekturfälle — und sichtbar protokolliert werden.

### Wording

Projektregel Handwerker-Sprache: **„Monat abschließen"** und **„Monat wieder öffnen"**. Nicht „festschreiben", nicht „Periodenabschluss", nicht „Sperrvermerk". Ein abgeschlossener Monat ist in der Oberfläche als solcher erkennbar.

### Wichtig: der Abschluss riegelt Änderungen ab, nicht die Anzeige

Naheliegende Fehlumsetzung, die ausdrücklich ausgeschlossen ist: Der Abschluss darf **keine** Voraussetzung für die Anzeige des Saldos werden.

- Stundensaldo und Urlaubsanspruch bleiben in der Handy-App und in der PC-App jederzeit live sichtbar, auch für den laufenden und jeden noch nicht abgeschlossenen Monat.
- Der angezeigte Saldo besteht aus den abgeschlossenen Monaten (fester Wert) plus allen offenen Monaten (live gerechnet).
- Der vorläufige Anteil wird gekennzeichnet, etwa: „Geprüft bis März 2026 — die Stunden seitdem sind noch vorläufig."
- Kein Bildschirm zeigt „Saldo erst nach Monatsabschluss verfügbar" oder blendet Werte aus, weil ein Monat noch offen ist.

### Erinnerung: Hinweis in der Benachrichtigungs-Glocke

Ein Riegel, an den niemand erinnert wird, wird vergessen — und ein nie abgeschlossener Monat ist genauso ungeschützt wie gar kein Abschluss. Die Erinnerung ist deshalb kein Komfort, sondern das, was den Abschluss überhaupt wirksam macht.

Dafür wird die **vorhandene** Glocke genutzt, kein eigener Benachrichtigungsweg: `NotificationController` (`src/main/java/org/example/kalkulationsprogramm/controller/NotificationController.java`) aggregiert unter `GET /api/notifications/summary` bereits Zähler und Einträge aus sieben Quellen; im Frontend hängt daran `react-pc-frontend/src/components/layout/NotificationBell.tsx`. Der Monatsabschluss wird dort eine weitere Kategorie.

- Der Hinweis erscheint, sobald ein Monat vorbei ist und noch keinen Abschluss hat. Der laufende Monat erzeugt keinen Hinweis.
- Wording: Kategorie **„Monat abzuschließen"**, Eintrag darunter etwa „März 2026 — noch nicht abgeschlossen". Nicht „Periodenabschluss fällig".
- **Performance-Auflage:** Der Controller rechnet bei jedem Glocken-Abruf live über alle Kategorien. Die neue Kategorie muss über eine einzelne, indexgestützte Abfrage laufen — keine Schleife über Mitarbeiter mal Monate, sonst wird die Glocke langsam.
- Den Hinweis bekommen die Mitarbeiter der Abteilungen mit dem neuen Recht `darfMonatAbschliessen` (siehe unten), nicht alle.

### Berechtigung: wer abschließen darf

Gesteuert über die **vorhandenen** Abteilungs-Berechtigungen, kein neuer Mechanismus. `Abteilung` (`src/main/java/org/example/kalkulationsprogramm/domain/Abteilung.java`) trägt heute `darfRechnungenGenehmigen`, `darfRechnungenSehen`, `darfFreigabeAnnahmePushen` und `darfWebseitenAnfragenPushen`; gepflegt werden sie über `react-pc-frontend/src/pages/AbteilungBerechtigungenEditor.tsx` unter der Route `/abteilung-berechtigungen` (in `App.tsx:113` mit `RequireAdmin` geschützt).

Dazu kommt **ein** neues Flag: **`darfMonatAbschliessen`**. Wer es hat, schließt Monate ab, öffnet sie wieder und bekommt den Glocken-Hinweis.

Bewusst nur ein Flag: Für Rechnungen gibt es zwei (`darfRechnungenGenehmigen` = sehen und genehmigen, `darfRechnungenSehen` = nur sehen, dokumentiert in `OffenePostenController.java:53-54`), weil es dort zwei echte Rollen gibt — Büro genehmigt, Buchhaltung schaut zu. Beim Monatsabschluss gibt es diese zweite Rolle nicht. Ob ein Monat abgeschlossen ist, wird auf der Zeitkonten-Seite ohnehin mit angezeigt; wer die Seite sehen darf, sieht auch das Kennzeichen. Ein zweites Flag dafür wäre eine Einstellung, die niemand je bewusst setzt.

- Neue Spalte in derselben Migration, `NOT NULL DEFAULT FALSE`. Bewusst `false`: nach der Migration darf zunächst niemand abschließen, der Betrieb schaltet es gezielt frei — anders als bei den beiden Push-Flags, die auf `true` stehen. Ein versehentlich abgeschlossener Monat ist teurer als ein zu spät abgeschlossener.
- `AbteilungBerechtigungDto` (beide Records) und `AbteilungBerechtigungController` ziehen nach.
- Der Editor bekommt den Schalter, Wording: **„Monate abschließen"**, Hilfetext „Aus, wenn diese Abteilung Monate weder abschließen noch wieder öffnen soll."
- Die Endpoints prüfen das Flag **serverseitig**. Ein ausgeblendeter Knopf ist keine Berechtigung.

## Offene Frage

Ob der Abschluss automatisch an die vorhandene `Lohnabrechnung`-Entity gekoppelt wird oder ein bewusster eigener Schritt bleibt.

**Empfehlung: eigener bewusster Schritt.** Eine automatische Kopplung macht den Monat zu, bevor jemand ihn geprüft hat — und der Abschluss soll ja gerade die Aussage „das hat jemand angesehen und für richtig befunden" tragen.

## Betroffene Stellen

- `MonatsSaldo`, neue Flyway-Migration (V368)
- `MonatsSaldoService` — alle `invalidiere*`-Methoden, `getOrBerechne`
- `MonatsSaldoRepository` — die `invalidiere*`-Queries
- `MonatsSaldoWarmupService`
- `ZeitverwaltungController` — Endpoints zum Abschließen und Wieder-Öffnen
- `NotificationController` — neue Kategorie „Monat abzuschließen"
- `Abteilung`, `AbteilungBerechtigungDto`, `AbteilungBerechtigungController` — neues Flag `darfMonatAbschliessen`
- Frontend PC: `AbteilungBerechtigungenEditor.tsx` — neuer Schalter
- Frontend PC: Zeiterfassungs-Seiten (Abschluss auslösen, Zustand anzeigen), `NotificationBell.tsx` samt Tests
- Frontend Mobile (`react-zeiterfassung`): Kennzeichnung des vorläufigen Anteils, **ohne** die Live-Anzeige einzuschränken

## Akzeptanzkriterien

- [ ] Ein abgeschlossener Monat ändert seinen Saldo nicht mehr — nicht durch eine Zeitkonto-Änderung, nicht durch einen Vorlagenwechsel, nicht durch den Warmup beim Anwendungsstart, nicht durch eine nachgetragene Zeitbuchung.
- [ ] Ein abgeschlossener Monat lässt sich wieder öffnen; wer das wann getan hat, ist nachvollziehbar.
- [ ] Der Abschluss ist in der Oberfläche erkennbar.
- [ ] In der Handy-App sind Stundensaldo und Urlaubsanspruch auch dann vollständig sichtbar, wenn für den laufenden Monat und beliebig viele davor noch kein Abschluss erfolgt ist; der vorläufige Anteil ist gekennzeichnet.
- [ ] Ist ein vergangener Monat nicht abgeschlossen, erscheint in der Glocke ein Hinweis „Monat abzuschließen" mit dem betroffenen Monat; nach dem Abschluss verschwindet er. Der laufende Monat löst keinen Hinweis aus.
- [ ] Ein Mitarbeiter ohne `darfMonatAbschliessen` kann einen Monat weder abschließen noch wieder öffnen — auch nicht durch direkten Aufruf des Endpoints — und bekommt keinen Glocken-Hinweis; mit dem Flag kann er beides.
- [ ] Bestehende Monatssalden ändern sich durch die Migration nicht.
- [ ] Test mit Dummy-Daten (`Max Mustermann`) für einen abgeschlossenen und einen wieder geöffneten Monat.

## Warum jetzt

Vorbedingung für #93 und #96. Dort wird das Wochenmodell historisiert und Mitarbeiter bekommen Arbeitszeit-Vorlagen zugewiesen — jede dieser Umstellungen läuft ohne Abschluss ohne Sicherheitsnetz.

Die Historisierung aus #93 schützt nur gegen die eine Ursache, die sie historisiert (das Wochenmodell). Der Abschluss schützt gegen alle, auch gegen die beiden anderen oben beschriebenen Wege. Beide werden gebraucht, aber der Abschluss kommt zuerst: er ist der Riegel, unter dem die übrigen Umstellungen gefahrlos passieren können.

Verwandt: #93, #94, #95, #96.

Die zugehörige Spezifikation liegt unter `docs/superpowers/specs/2026-09-09-zeitkonto-vorlagen-und-historisierung.md`.
