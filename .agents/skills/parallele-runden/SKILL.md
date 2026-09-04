---
name: parallele-runden
description: Use when executing a written implementation plan with many tasks in this repo - runs tasks in parallel rounds with a review gate between rounds, instead of one task at a time. Trigger on "Plan umsetzen", "parallele Runden", "Runde starten", or after writing-plans when the user wants speed.
---

# Umsetzung in parallelen Runden

Ersetzt `superpowers:subagent-driven-development` für dieses Projekt. Der
Unterschied: dort läuft ein Agent je Task nacheinander, hier laufen mehrere
Agenten je Runde gleichzeitig, und geprüft wird einmal pro Runde statt
einmal pro Task.

Das ist schneller, verlangt aber, dass die Runden vorher sauber geschnitten
sind. Der Schnitt steht im Plan.

## Voraussetzung

Es gibt einen geschriebenen Plan unter `docs/superpowers/plans/` mit

- je Task einen Abschnitt `### Task N` mit Steps in `- [ ]`-Form,
- einem Abschnitt `## Global Constraints`,
- einem Abschnitt `## Ausführung in Runden` mit der Rundeneinteilung.

Fehlt die Rundeneinteilung, schneide sie zuerst selbst nach den zwei Regeln
unten und lege sie dem Nutzer vor, bevor du startest.

## Die zwei Regeln für den Rundenschnitt

1. **Keine zwei Agenten einer Runde schreiben in dieselbe Datei.**
   Zwei Agenten, die gleichzeitig dieselbe Datei ändern, überschreiben sich
   gegenseitig. Das merkt oft erst der Testlauf, und dann ist unklar, wessen
   Arbeit fehlt. Geh die `Files`-Blöcke aller Tasks durch und bau eine
   Tabelle Datei zu Tasks. Jede Datei, die mehrfach auftaucht, zwingt ihre
   Tasks in verschiedene Runden.

2. **Ein Task startet erst, wenn alles fertig ist, was er importiert.**
   Steht im `Interfaces`-Block eines Tasks unter `Consumes` etwas aus einem
   anderen Task, muss der vorher fertig sein. Nicht nur geschrieben, sondern
   geprüft.

Im Zweifel die kleinere Runde. Ein Agent zu wenig kostet Zeit, ein
Dateikonflikt kostet mehr.

## Ablauf

Für jede Runde der Reihe nach:

### 1. Starten

Alle Agenten der Runde in **einer einzigen Nachricht** starten, sonst laufen
sie nacheinander statt gleichzeitig. Modell für die Umsetzung: das im Plan
oder vom Nutzer genannte, sonst Sonnet.

Jeder Agent bekommt genau seinen Task-Abschnitt plus die Global Constraints.
Nicht den ganzen Plan: er soll seinen Task bauen, nicht die Nachbarn
mitdenken.

Der Auftrag muss diese sechs Punkte enthalten:

- Welchen Task aus welcher Datei
- Global Constraints zuerst lesen
- Testgetrieben, Schritt für Schritt: Test schreiben, fehlschlagen lassen,
  Grund des Fehlschlags prüfen, umsetzen, bestehen lassen, committen
- Welche Pflichtdokumente vor dem ersten Edit zu lesen sind (Hook blockt sonst)
- **Nur die Dateien anfassen, die unter `Files` stehen**, weil andere Agenten
  gleichzeitig in denselben Ordnern arbeiten
- Anhalten und melden, wenn der Plan von der Wirklichkeit im Code abweicht,
  statt still etwas anderes zu bauen

### 2. Warten

Bis alle Agenten der Runde zurück sind. Nicht früher weitermachen, auch
wenn zwei von drei schon fertig sind.

### 3. Prüfen

**Ein** Prüfagent für die ganze Runde, nicht einer je Task. Nur so sieht er
das Zusammenspiel. Modell: das stärkste verfügbare, sonst Opus.

Der Prüfagent führt die Tests selbst aus. Er glaubt keinem Bericht eines
Umsetzungs-Agenten. Er prüft mindestens:

- Laufen alle Tests, und baut das Projekt?
- Wurden die Tests wirklich zuerst geschrieben? Die Commit-Reihenfolge zeigt es.
- Passen die erzeugten Namen und Typen exakt zu dem, was im Plan unter
  `Produces` steht? Spätere Tasks importieren sie.
- Die Projektregeln aus `.claude/CLAUDE.md` und den Doku-Dateien.

Er meldet eine Ampel: grün weiter, gelb Kleinigkeiten, rot zurück.

### 4. Nachbessern

Bei rot geht der Befund an einen Agenten zurück, mit dem Befund im Auftrag.
Bei gelb entscheidet der Hauptagent, ob er es selbst macht.

Danach **erneut prüfen**, nicht darauf vertrauen, dass die Nachbesserung saß.

### 5. Nächste Runde

Erst bei grün.

## Was schiefgeht, wenn man schludert

| Verlockung | Was passiert |
| --- | --- |
| „Die zwei Tasks passen schon zusammen in eine Runde" | Beide schreiben `App.tsx`, einer verliert seine Änderung |
| „Der Agent sagt, die Tests laufen" | Sie liefen bei ihm, vielleicht ohne den Code des Nachbarn. Selbst ausführen. |
| „Ich prüfe am Ende alles auf einmal" | Ein Fehler aus Runde 1 steckt dann in fünf Runden Arbeit |
| „Der Agent darf auch die Nachbardatei anfassen, ist ja praktisch" | Genau daraus entstehen die Konflikte, die die Runden verhindern sollen |
| „Testgetrieben ist hier Formsache" | Ohne roten Test weiß niemand, ob der grüne Test überhaupt etwas prüft |

## Abschluss

Nach der letzten Runde einmal alles zusammen: Frontend-Tests, Frontend-Build,
Backend-Tests, `./graphify update .`. Danach `.claude/commands/review-and-ship.md`,
wie es `.claude/CLAUDE.md` für jede abgeschlossene Aufgabe vorschreibt.
