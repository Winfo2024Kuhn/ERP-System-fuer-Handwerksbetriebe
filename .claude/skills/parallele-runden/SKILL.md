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

Erst bei grün. Und **erst aufräumen, dann starten:** die Worktrees und
Task-Branches der abgenommenen Runde löschen, bevor die nächste Runde
anläuft.

```powershell
# 1. ZUERST die node_modules-Junction lösen — ohne -Recurse, sonst löscht
#    es durch die Junction hindurch das gemeinsame Ziel (siehe Beobachtungen)
(Get-Item "<worktree-pfad>\react-pc-frontend\node_modules").Delete()
```
```bash
# 2. dann erst den Worktree und den Branch
git worktree remove --force <worktree-pfad>
git branch -d <task-branch>        # -d, nicht -D: verweigert, wenn nicht gemerged
```

Warum vor dem Start und nicht irgendwann später: Jede Runde hinterlässt drei
Ordner mit komplettem Repo-Inhalt. Nach drei Runden sind das neun, und im
Editor sieht man nicht mehr, welcher davon gerade lebt. Das `-d` beim
Branch-Löschen ist die Sicherung — verweigert Git, ist etwas nicht
gemerged, und das will man wissen.

## Was schiefgeht, wenn man schludert

| Verlockung | Was passiert |
| --- | --- |
| „Die zwei Tasks passen schon zusammen in eine Runde" | Beide schreiben `App.tsx`, einer verliert seine Änderung |
| „Der Agent sagt, die Tests laufen" | Sie liefen bei ihm, vielleicht ohne den Code des Nachbarn. Selbst ausführen. |
| „Ich prüfe am Ende alles auf einmal" | Ein Fehler aus Runde 1 steckt dann in fünf Runden Arbeit |
| „Der Agent darf auch die Nachbardatei anfassen, ist ja praktisch" | Genau daraus entstehen die Konflikte, die die Runden verhindern sollen |
| „Testgetrieben ist hier Formsache" | Ohne roten Test weiß niemand, ob der grüne Test überhaupt etwas prüft |
| „Der Agent lässt den langen Testlauf im Hintergrund laufen" | Er wartet auf eine Meldung, die ihn nie erreicht, und endet mit fertigem, aber nicht committetem Code |
| „Das Merkwürdige von eben schreibe ich am Ende auf" | Am Ende ist der Grund vergessen. Sofort rein, siehe unten. |

## Diesen Skill im Lauf verbessern

Dieser Skill ist nie fertig. Er wird **während** der Läufe geschärft, nicht
danach.

Die Regel: Sobald im Lauf etwas auffällt — ein Agent hängt, ein Gate reißt,
eine Anweisung wird von mehreren Agenten gleich missverstanden — schreibst du
es sofort hier rein, bevor du weitermachst. Nicht merken und am Ende
nachtragen. Am Ende ist der Grund vergessen und nur noch das Symptom da.

Was reingehört:

- Was passiert ist, konkret genug zum Wiedererkennen.
- Warum es passiert ist, soweit du es weißt.
- Was der Auftrag künftig enthalten muss, damit es nicht wieder passiert.

Was nicht reingehört: einmalige Zufälle, Geschmacksfragen, und alles, was
schon dasteht. Lieber einen bestehenden Punkt schärfen als einen zweiten
danebenstellen.

Zweimal dasselbe erlebt und nicht aufgeschrieben heißt: beim dritten Mal
kostet es wieder eine Nachbesserungsrunde.

## Beobachtungen aus echten Läufen

### Agenten schicken Testläufe in den Hintergrund und bleiben stehen (04.09.2026)

Alle drei Agenten eines Abschnitts starteten ihren Testlauf im Hintergrund und
beendeten ihren Zug mit „ich warte auf die Benachrichtigung". Die kommt bei
einem Subagenten aber nicht an. Ergebnis: Code fertig geschrieben, aber nichts
committet und nichts im Kontext-Log — von außen sah es aus wie drei
abgeschlossene Tasks.

Konsequenz für den Auftrag: **Testläufe im Vordergrund, mit hohem Timeout**
(600000 ms). Kein Hintergrund, kein Monitor. Ein Backend-Testlauf braucht
Minuten, das ist in Ordnung — der Agent soll ihn abwarten.

Konsequenz für den Hauptagenten: Meldet ein Agent „fertig", ohne
Commit-Hashes zu nennen, ist er nicht fertig. Erst `git log` und
`git status` im Worktree ansehen, dann glauben.

### Zeitabhängige Tests werden flaky, wenn mehrere Agenten gleichzeitig testen (04.09.2026)

Ein Agent meldete einen Failure in `UnifiedEmailControllerExtractEmailTest` —
ein Test mit 500-ms-Zeitschranke, in einer Datei, die er nie angefasst hatte.
Ursache war nicht sein Code, sondern die Last: drei Agenten fuhren gleichzeitig
Testsuiten auf derselben Maschine.

Das ist der Preis der Parallelität und kein Grund, sie aufzugeben. Aber:

- Der Auftrag muss sagen, dass ein Failure in einer **nicht angefassten** Datei
  mit Zeitschranke erst wiederholt wird, bevor er gemeldet wird.
- Der Prüfagent fährt die volle Suite, wenn die Umsetzungs-Agenten **fertig**
  sind, nicht währenddessen. Sonst misst er die Last der Nachbarn mit.
- Ein Failure, der beim zweiten Lauf allein weg ist, gehört als Beobachtung
  ins Kontext-Log — nicht als Befund in die Ampel.

### `git worktree remove` löscht durch eine Junction hindurch (04.09.2026)

Damit nicht jeder Worktree ein eigenes `npm ci` braucht, lag `node_modules`
in jedem Worktree als Windows-Junction auf das eine `node_modules` im
Haupt-Repo. Beim Aufräumen der ersten Runde hat `git worktree remove --force`
die Junction nicht als Verweis behandelt, sondern als Ordner — und das
**gemeinsame Ziel** mit ausgeräumt. Der Prüfagent der nächsten Runde fand
ein leeres `node_modules` vor und musste erst `npm ci` fahren, bevor ein
einziges Gate lief.

Deshalb steht in „Nächste Runde" jetzt der Zwei-Schritt: erst die Junction
lösen (`(Get-Item …).Delete()`, ohne `-Recurse`), dann den Worktree
entfernen. Wer die Reihenfolge umdreht, löscht die Abhängigkeiten aller
anderen Worktrees mit — auch der, die gerade laufen.

Gilt genauso für jeden anderen geteilten Ordner, den man per Junction oder
Symlink in einen Worktree hängt.

### Auftrag als nachweisbares Ergebnis formulieren, nicht als Lösungsweg (04.09.2026)

Ein Befund war zweimal von Reviews weitergereicht und zweimal nicht behoben
worden. Beim dritten Anlauf stand im Auftrag kein Lösungsweg, sondern das
prüfbare Ergebnis: „Versionskonflikt ⇒ 409 mit dieser Meldung, kein
Klassenname im Body, alle übrigen Fehlerfälle weiterhin 400." Dazu ein Satz
zu der Falle, in die der naive Weg läuft („den catch verengen, nicht
streichen — sonst kippen die anderen Fälle auf 500"). Der Agent hat es beim
ersten Versuch sauber gelöst, inklusive zweitem Test als Regressionsschutz.

Also: **Was muss hinterher nachweisbar wahr sein** gehört in den Auftrag.
**Wie** der Agent dahin kommt, gehört ihm. Die eine bekannte Falle benennen
lohnt sich trotzdem — sie kostet einen Satz und spart eine Runde.

## Abschluss

Nach der letzten Runde einmal alles zusammen: Frontend-Tests, Frontend-Build,
Backend-Tests, `./graphify update .`. Danach `.claude/commands/review-and-ship.md`,
wie es `.claude/CLAUDE.md` für jede abgeschlossene Aufgabe vorschreibt.
