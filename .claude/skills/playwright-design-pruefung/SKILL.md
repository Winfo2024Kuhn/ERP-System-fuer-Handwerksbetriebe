---
name: playwright-design-pruefung
description: Pflicht bei jeder Frontend-Änderung (react-pc-frontend, react-zeiterfassung) - prüft den geänderten Ablauf end-to-end mit Playwright, und zwar zweifach - funktioniert er, und sieht er richtig aus. Feste Bildschirmgrößen (14-Zoll-MacBook und großer Monitor für die PC-App, Handy für die Zeiterfassung), Screenshots je Größe, Checkliste zu Farben, Design-System, Look-and-Feel, UX, Auffindbarkeit und Überschneidungen. Trigger - jeder Task, der .tsx/.ts unter react-pc-frontend/src oder react-zeiterfassung/src ändert, jeder Abschnitts-Review dazu, und "Design prüfen", "sieht das gut aus", "Playwright".
---

# Playwright-Design-Prüfung

Vorgabe des Nutzers vom 04.09.2026, gilt dauerhaft: **Jede Frontend-Änderung wird mit
Playwright end-to-end geprüft — funktional und gestalterisch.** Unit-Tests prüfen
Bausteine. Der Nutzer bedient Abläufe, auf einem echten Bildschirm, in einer echten
Größe. Der Anlass: ein Hook mit 24 grünen Unit-Tests ergab zusammen mit seiner Komponente
einen toten Knopf. Kein Unit-Test konnte das sehen.

## Zwei Ebenen, beide Pflicht

1. **Funktion.** Eine Spec unter `e2e/` für genau den geänderten Ablauf, so wie der
   Nutzer ihn klickt. `/api` wird gestubbt (`e2e/hilfen/api.ts`), kein Backend, keine
   echten Personendaten.
2. **Design und UX.** Dieselbe Spec macht je Bildschirmgröße Screenshots des
   Endzustands (und der wichtigen Zwischenzustände: Laden, Fehler, gesperrt), prüft
   automatisch, was sich automatisch prüfen lässt (Überlauf, Überschneidungen,
   Sichtbarkeit), und der Agent **schaut die Screenshots an** und beantwortet die
   Checkliste unten — schriftlich, im Kontext-Log.

## Bildschirmgrößen — fest, nicht verhandelbar

| App | Projekt in `playwright.config.ts` | Größe | Warum |
| --- | --- | --- | --- |
| `react-pc-frontend` | `pc-14zoll` | 1440 × 900 | 14-Zoll-MacBook. **Das Kleinste, was es geben soll.** Was hier nicht passt, ist ein Fehler. |
| `react-pc-frontend` | `pc-monitor` | 1920 × 1080 | Großer Monitor am Arbeitsplatz. Hier darf nichts verloren oder verwaist wirken. |
| `react-zeiterfassung` | `handy` | iPhone 15 (393 × 852, Touch) | Die Zeiterfassung ist eine Handy-App und wird **immer** auf Handygröße geprüft. |

Die PC-App ist **nicht** für Handy oder Tablet gedacht — dort nicht prüfen, dort nichts
optimieren. Die Zeiterfassung wird **nicht** auf Desktop-Größen geprüft.

Beide Desktop-Projekte laufen für jede PC-Spec automatisch mit. Eine Spec, die nur in
einer Größe grün ist, ist rot.

## Checkliste — die sechs Fragen des Nutzers

Für jeden Screenshot, jede Größe. Antworten gehören ins Kontext-Log, nicht in den Kopf.

**1. Unterscheiden sich die Farben schön?**
Zustände (aktiv / inaktiv / Fehler / gesperrt / Hinweis) müssen sich auf einen Blick
trennen lassen — nicht nur per Text. Text auf Fläche hat mindestens 4,5 : 1 Kontrast
(Fließtext) bzw. 3 : 1 (große Überschriften, Icons). Rose ist Akzent, nicht Tapete: ein
Screen mit mehr als einer rosafarbenen Primäraktion ist falsch.

**2. Wird das Design-System eingehalten?**
Maßstab ist der Skill `handwerkerprogramm-design`, sonst nichts. Farben nur rose/slate
(kein blue/indigo/violet aus Skill- oder MCP-Defaults), Icons nur Lucide, kein Emoji,
Systemschrift, `rounded-lg` als Standard, `shadow-sm` für Karten, PageHeader-Muster
(Eyebrow + Titel + Untertitel + Aktionen), drei Chip-Arten und nicht mehr, Ladezustand
als Skeleton statt leerem Kasten.

**3. Ist es ein gutes Look-and-Feel?**
Ruhig, aufgeräumt, ausgerichtet. Abstände folgen der Skala, nichts klebt am Rand,
nichts schwebt verloren in der Mitte eines 1920er-Bildschirms. Ein Handwerker, der das
zum ersten Mal sieht, soll denken „aha, klar" — nicht „wo bin ich".

**4. Ist die UX gut?**
Gulf of Execution: sieht man sofort, *wie* man ans Ziel kommt — genau eine
Primäraktion, klickbare Dinge sehen klickbar aus, deaktivierte Knöpfe erklären per
Tooltip *warum*. Gulf of Evaluation: sieht man sofort, *was passiert ist* — Ladezustand,
Toast bei Fehler, sichtbare Änderung nach dem Speichern. Handwerker-Sprache, kein
Buchhalter-Deutsch.

**5. Findet man es gut?**
Die Aktion, um die es im Task geht, ist auf 14 Zoll **ohne Scrollen** sichtbar und da,
wo man sie erwartet (Aktionen im PageHeader, Hinweise direkt über dem, worauf sie sich
beziehen, Bearbeiten-Leiste am Kopf des Datensatzes). Nicht in einem Menü versteckt,
nicht unter dem Fold.

**6. Überschneidet sich etwas?**
Kein horizontaler Scrollbalken. Keine zwei interaktiven Elemente, deren Rahmen sich
überlappen. Kein abgeschnittener Text, keine Ellipse an einer Stelle, die auf 14 Zoll
lesbar sein muss. Sticky-Leisten und Modale verdecken nichts, was man gerade braucht.

## So wird es gebaut

Hilfen liegen in `react-pc-frontend/e2e/hilfen/design.ts`:

```ts
import { test, expect } from '@playwright/test';
import { designPruefung } from './hilfen/design';

test('Bearbeiten-Leiste: Fertig gibt frei und Bearbeiten holt neu', async ({ page }, testInfo) => {
    // ... Ablauf klicken, mit gestubbtem /api ...

    // Ebene 2: Screenshot + automatische Checks für diese Größe
    await designPruefung(page, testInfo, 'bearbeiten-leiste-lesen', {
        primaerAktion: page.getByRole('button', { name: 'Bearbeiten' }),
    });
});
```

`designPruefung` macht einen Screenshot nach `test-results/design/<name>--<projekt>.png`,
prüft „kein horizontaler Überlauf", „keine überlappenden interaktiven Elemente" und
„Primäraktion im sichtbaren Bereich" — und schlägt fehl, wenn eines davon nicht stimmt.
Was sie **nicht** prüfen kann (Fragen 1–4), prüfst du: öffne die PNGs mit dem
Read-Tool und geh die Checkliste durch. Ein Screenshot, den niemand angeschaut hat, ist
kein Nachweis.

**Ausführen.** Aus `react-pc-frontend/`:

```bash
E2E_PORT=5174 npm run test:e2e                 # eigener Port, wenn andere Agenten parallel laufen
npx playwright test e2e/meine.spec.ts          # nur eine Spec, beide Desktop-Größen
npx playwright test --project=pc-14zoll        # nur die kleine Größe
```

Die Konfiguration liest `E2E_PORT` (Standard 5173) und startet den Vite-Dev-Server
selbst auf genau diesem Port (`--strictPort`). Zwei Agenten auf demselben Port würden
sich gegenseitig den falschen Code testen — deshalb je Agent ein eigener Port.

**Zeiterfassung.** Hat noch keine Playwright-Konfiguration. Der erste Task, der dort
`.tsx` ändert, legt sie an: `@playwright/test` als devDependency, `playwright.config.ts`
mit dem Projekt `handy` (`devices['iPhone 15']`), `baseURL` auf den Vite-Server (der
läuft dort per `@vitejs/plugin-basic-ssl` über **https** — `ignoreHTTPSErrors: true`
setzen), Script `test:e2e`. Dieselbe Hilfsdatei wie in der PC-App daneben legen.

## Wer prüft was

- **Coding-Agent:** schreibt die Spec für seinen Ablauf und fährt **nur diese
  Spec** auf eigenem Port (`E2E_PORT=<port> npx playwright test e2e/<spec>`).
  Nie die ganze E2E-Suite, nie die ganze Unit-Suite — Vorgabe des Nutzers,
  es dauert zu lang. Ein kurzer Blick auf die eigenen Screenshots schadet
  nicht, die formale Beurteilung ist aber nicht seine.
- **Design-Reviewer** (`loese-problem-design-review`, eigener Agent, nur bei
  Frontend-Änderungen): fährt `npm run test:e2e` komplett (alle Specs, beide
  Größen) in einem **eigenen Worktree** auf dem gemergten Stand, schaut jeden
  Screenshot unter `test-results/design/` mit dem Read-Tool an und beantwortet
  die sechs Fragen je Größe schriftlich im Kontext-Log. Wo ein Screenshot nicht
  reicht, klickt er mit dem Playwright-MCP (`browser_navigate`,
  `browser_resize`, `browser_take_screenshot`) selbst durch die laufende App.
  Ampel: 🔴 bei Frage 5 oder 6 verletzt (nicht auffindbar, Überschneidung,
  Abschneiden auf 14 Zoll) oder Bruch des Design-Systems (fremde Farbe, Emoji,
  handgemaltes SVG); Geschmacksfragen zu 3 sind 🟡.
- **Code-Reviewer** (`loese-problem-review`): läuft parallel, fasst E2E und
  Design nicht an — Code, Korrektheit, Performance, Datenschutz, Sicherheit,
  volle Unit-/Backend-Suiten.
- **Orchestrator:** merged vorher, legt den Review-Worktree an, startet beide
  Reviewer in einer Nachricht.

## Was NICHT hierher gehört

Pixelgenaue Screenshot-Vergleiche (`toHaveScreenshot`) — die brechen bei jeder
Schriftglättung und erzeugen Rauschen statt Befunde. Es geht um die sechs Fragen, nicht
um Byte-Gleichheit.

## Beobachtungen aus echten Läufen

**04.09.2026, Layout-Prüfung 14 Zoll:** Die automatischen Checks in
`e2e/hilfen/design.ts` haben drei echte Abschneide-Fehler nicht gesehen, die
im Browser sofort ins Auge fielen:

- Der Inhaltsbereich `<main>` hat `overflow-x-hidden`. Was rechts übersteht,
  ist weg, ohne dass `document.documentElement.scrollWidth` wächst. Immer
  auch `main.scrollWidth <= main.clientWidth` messen — und jedes Element mit
  `overflow-x: hidden`, dessen `scrollWidth` größer ist als es selbst.
- Zusammengequetschte Kennzahl-Kästen: Text läuft sichtbar über seinen Kasten
  (`scrollWidth > clientWidth` auf einem Blatt-Element mit Text). Elemente mit
  Breite 0 dabei nicht überspringen, genau die sind der Fall.
- `truncate`-Titel mit tatsächlich gekürztem Text („…") sind auf 14 Zoll ein
  Fehler, außer die Kürzung ist ausdrücklich gewollt.

Ein Screenshot allein reicht nicht: Die abgeschnittene rechte Spalte im
Projekt-Editor sah auf 1920 perfekt aus und war bei 1440 zu 170 px außerhalb
des Fensters. Beide Größen sind Pflicht, und die Zahl zählt, nicht der
Eindruck. Spec dazu: `docs/superpowers/specs/2026-09-04-layout-14-zoll.md`
(im Haupt-Checkout).
