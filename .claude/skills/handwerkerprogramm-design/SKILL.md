---
name: handwerkerprogramm-design
description: Der EINE Frontend-Skill für Handwerkerprogramm (react-pc-frontend, react-zeiterfassung). Pflicht vor jeder Frontend-Arbeit, vom Hook erzwungen. Enthält Design-System (Rose/Slate, Lucide, Wording), Bedienbarkeit (Gulf of Execution/Evaluation, Nielsen-Heuristiken), Performance statt Animation, Typografie, Farbwahl, Nutzerführung und Lösch-UX, Frontend-Sicherheit und die Playwright-Design-Prüfung. Bringt UI-Kits für Desktop und Handy, Design-Tokens und echte Screenshots mit.
user-invocable: true
---

# Frontend-Standard für Handwerkerprogramm

Das ist der einzige Frontend-Skill. Der Frontend-Agent liest ihn vollständig, bevor er etwas baut. Er ersetzt die früheren Skills `ui-ux-pro-max`, `playwright-design-pruefung` sowie die Kernideen aus `emil-design-eng` und `design-taste-frontend`.

Vorrang bei Widersprüchen: **`docs/agent instructions/docs/FRONTEND_UI.md`** (Pflicht-Komponenten, Regeln) > dieser Skill > Geschmack. Die Landingpage-Regeln aus `design-taste-frontend` (keine Schatten, keine Rundungen, große Hero-Typo) gelten hier **nicht**: Das ERP ist eine dichte Arbeitsoberfläche, keine Werbeseite.

Dateien in diesem Ordner:
- `README.md`: Produktkontext, Wording, visuelle Grundlagen, Navigation, Icons
- `colors_and_type.css`: Design-Tokens (Farben, Schrift, Abstände, Schatten, Radien, Bewegung, Chips, PDF-Maße)
- `assets/`: echte Logos, Datei-Icons, Screenshots (wiederverwenden, nicht nachzeichnen)
- `ui_kits/desktop-erp/`, `ui_kits/mobile-zeiterfassung/`: React-Nachbauten zum Abgucken
- `preview/`: kleine Muster-Karten
- `PRUEFUNG.md`: Playwright-Design-Prüfung (Pflicht nach jeder Frontend-Änderung)

## 1. Design-System (nicht verhandelbar)

1. **Nur Deutsch.** Alle UI-Texte in Handwerker-Sprache („Kundenrechnungen“, nicht „Debitoren“). Etablierte Begriffe nie übersetzen (Angebot, Rechnung, Nachkalkulation, KI-Hilfe, Zeiterfassung).
2. **Markenfarbe `rose-600` (#e11d48)**, sparsam: Primärknopf, aktive Navigation, Seiten-Eyebrow, KI-Momente. Alles andere Slate. Statusfarben nur für Zustände (emerald Erfolg, amber Warnung, red-600 Gefahr).
3. **Icons nur Lucide.** Nie SVGs von Hand. `Gem` kennzeichnet KI-Momente.
4. **Seitenkopf** immer als PageHeader: Eyebrow + Titel + Untertitel + Aktionen.
5. **Kein Emoji** in der Produkt-UI.
6. **Systemschrift**, keine Webfonts.
7. **`rounded-lg`** ist Standard, `rounded-2xl` für Dialoge, `rounded-3xl` für Handy-Sheets.
8. **`shadow-sm`** für Karten, `shadow-lg` bei Hover, Glühen nur bei Hero/Kennzahl.
9. **Drei Chip-Arten:** indigo Platzhalter, gelb Vorschauwert, rose *anklickbares* Zahlungsziel. Nur das letzte ist klickbar.
10. **Nie eine leere Box beim Laden.** Skeleton (`motion-safe:animate-pulse`) → einblenden → `ImageOff`-Kachel bei Fehler.
11. **Keine Zierbewegung; Rest hinter `motion-safe:`**; Icon-Knöpfe mit `aria-label` (+ `aria-expanded` beim Auf-/Zuklappen).
12. **PDF-Maße des Dokumenteditors nicht nach Gefühl ändern** (10pt / 1.3 / 2pt spiegelt den PDF-Dienst).
13. **Info-Button an erklärungsbedürftigen Stellen** (Nutzerregel 10.10.2026): wo eine Einstellung anderswo wirkt, im Hintergrund etwas auslöst, nicht rückgängig zu machen ist oder einen Fachbegriff braucht. Gemeinsamer `<InfoButton>` aus `components/ui/info-button.tsx`, zwei kurze Teile: „Was passiert im Hintergrund?“ und „Was bewirkt das?“. Nie nur Hover, nie gesammelt am Seitenende, keiner an Selbstverständlichem.

Immer zuerst Bestehendes nutzen: Pflicht-Komponenten aus `FRONTEND_UI.md` (`Select`, `DatePicker`, `Button`, `DetailLayout`, `useConfirm`, `useToast` …) und die MCP-Server `shadcn` für fehlende Bausteine.

## 2. Bedienbarkeit: jede UI-Entscheidung gegen diese Fragen

Beide Kluften müssen mit **Ja** beantwortbar sein, sonst ist die Umsetzung nicht fertig. Die ausführlichen Regeln stehen in `FRONTEND_UI.md`.

- **Gulf of Execution:** Sieht der Nutzer sofort, *wie* er ans Ziel kommt? Eine Primäraktion pro Screen, Klickbares sieht klickbar aus, Icon-Knöpfe haben Tooltip, deaktivierte Knöpfe erklären *warum*, Löschen fragt nach, Pflichtfelder und Formate stehen *vorher* da.
- **Gulf of Evaluation:** Sieht der Nutzer sofort, *was passiert ist*? Ladezustand, danach Ergebnis; Erfolg und **jeder** Fehler als Toast; Daten aktualisieren sich sichtbar; Laden, leer und Fehler sehen verschieden aus.

Die zehn Nielsen-Heuristiken als Prüfliste (Handwerker-Fassung):

1. **Sichtbarkeit des Systemstatus:** Der Nutzer weiß jederzeit, was gerade läuft (Spinner, Fortschritt, „Gespeichert“).
2. **Sprache der echten Welt:** Wörter des Handwerkers, keine Buchhalter- oder SAP-Begriffe („Kunde“ statt „Debitor“).
3. **Nutzerkontrolle und Freiheit:** Jeder Dialog hat „Abbrechen“, Aktionen lassen sich rückgängig machen (Toast „Rückgängig“), Esc schließt.
4. **Konsistenz und Standards:** Gleiche Sache, gleiche Komponente und gleiches Wort überall. Nicht pro Seite eine Sonderlösung.
5. **Fehlervermeidung:** Besser verhindern als melden: Knopf gesperrt, bis Pflichtfelder gefüllt sind; Format automatisch; Bestätigung bei Unwiderruflichem.
6. **Wiedererkennen statt Erinnern:** Namen in Auswahllisten statt IDs zum Abtippen; zuletzt Benutztes anbieten; nichts, was man sich merken muss.
7. **Flexibilität und Effizienz:** Häufiges ist schnell (Tastatur, Vorschläge, Vorbelegung); Seltenes darf einen Klick tiefer liegen.
8. **Ästhetisches, minimalistisches Design:** Nur, was jetzt gebraucht wird. Eine Primäraktion, ruhige Flächen, kein Schmuck.
9. **Fehler erkennen, verstehen, beheben:** Fehlermeldung sagt in einfachen Worten, *was* schiefging und *was* man tun kann („Rechnung konnte nicht gespeichert werden. Kunde fehlt.“).
10. **Hilfe und Dokumentation:** Hilfe an der Stelle, nicht im Handbuch: `InfoButton` neben dem Feld, Platzhalter und Beispiele im Feld.

## 3. Performance statt Animation

Das ERP ist ein Arbeitswerkzeug, kein Schaufenster. **Keine Animationen zum Schmuck** (keine Einblend-Effekte, Staffelungen, Federn, Hero-Bewegung). Schnelle Bedienung zählt mehr als Effekte.

- Erlaubt ist nur, was Rückmeldung gibt oder ohnehin Standard ist: Hover-/Fokus-Farbwechsel (`transition-colors`), Skeleton beim Laden (`motion-safe:animate-pulse`), Spinner auf Knöpfen. Dialoge, Dropdowns und Toasts dürfen mit den Bordmitteln der Komponenten (shadcn/Radix) ein-/ausblenden, aber nichts Eigenes bauen.
- Nie `transition-all`, nie Animationen auf Tastatur-Aktionen, nie auf Listen- oder Tabellenzeilen.
- Alles hinter `motion-safe:`; keine neue Animations-Bibliothek (kein Framer Motion o. Ä.).
- **Performance ist Pflicht:** große Listen virtualisieren oder paginieren; teure Berechnungen mit `useMemo`, Handler stabil halten, wo Kinder davon abhängen; Suchfelder entprellen; Daten nicht doppelt laden; Bilder und PDFs erst bei Bedarf; keine unnötigen Re-Renders ganzer Seiten; Bundle klein halten (kein schweres Paket für eine Kleinigkeit, `React.lazy` für selten genutzte große Seiten). Spürbar langsame Stellen auf 14 Zoll mit realistischer Datenmenge prüfen (500+ Zeilen).

## 3b. Große Listen: begrenzt laden

Typischer Fehler: eine Liste lädt alles auf einmal. Das ist hier **verboten** für alles, was wachsen kann (Kunden, Artikel, Projekte, Rechnungen, E-Mails, Dokumente, Zeitbuchungen, Verläufe).

- Immer **eine Seite** abfragen (`page`/`size`, Standard 25–50; Auswahllisten 20) und den Rest nachladen: **Tabelle mit Seiten** (Stammdaten, Seite in der URL), **„Mehr laden“-Knopf** mit Zähler „25 von 140“ (Verläufe, Übersichten) oder bei Auswahlfeldern **Suche mit den ersten ~20 Treffern** (entprellt, serverseitig).
- Filtern, Suchen und Sortieren macht das Backend, nie der Browser auf einer vollen Liste. Filteränderung → zurück auf Seite 1; späte Antworten alter Abfragen verwerfen.
- Gesamtzahl bzw. „es gibt mehr“ immer anzeigen. Badge-Zahlen kommen vom Backend, nicht aus `liste.length`.
- Gibt es für den Fall noch keinen paginierten Endpoint, wird er **mit angelegt** (Backend: `Page`/`Slice`, Obergrenze serverseitig) statt `findAll` im Frontend zu verwenden. Muster und Regeln: Abschnitt „Große Listen“ in `FRONTEND_UI.md`; Vorbilder `ArtikelSuche.tsx` (Seiten) und `bestellungenNachladen.ts` (Mehr laden).

## 3c. Zuständigkeiten und Größe

Das Programm wächst, also bleibt alles klein, wiederverwendbar und testbar. Volle Regeln: „Zuständigkeiten und Größe“ in `FRONTEND_UI.md`.

- Komponentendatei bis ca. 300 Zeilen, ab 500 zu groß; Hook bis ca. 150; Seiten setzen nur zusammen. Logik in Hooks und reine Funktionen (mit Test), wiederkehrende Oberfläche in gemeinsame Komponenten.
- **Vor dem Bauen suchen**, ob es das schon gibt. Zweites Vorkommen = auslagern und an beiden Stellen nutzen, nicht kopieren.
- Neues kommt nie in eine bereits zu große Datei, sondern in eine neue Komponente/einen neuen Hook.
- **Fällt eine bestehende große Datei auf, die du anfasst: fragen bzw. im Bericht vorschlagen**, was mit welchem Schnitt ausgelagert werden könnte.

## 4. Typografie, Farbe, Nutzerführung

**Typografie** (Systemschrift, Hierarchie über Größe und Gewicht, nicht über Schmuck):
- Wenige feste Stufen: Seitentitel, Abschnittstitel, Text, Hinweis. Pro Screen höchstens diese vier; gleiche Rolle = gleiche Klasse überall.
- Fließtext und Tabelleninhalt in `slate-900`/`slate-700` und mindestens `text-sm`; Grau (`slate-500`) nur für Nebensächliches. Hinweise nie kleiner als `text-xs`, nie hellgrau auf weiß unter 4,5 : 1.
- Zahlen, Beträge, Mengen: `tabular-nums`, rechtsbündig, Einheit immer dabei (`€`, `Std.`, `m²`), deutsches Format (`1.234,50 €`).
- Titel kurz, Knopf-Beschriftung ein Verb mit Objekt („Rechnung erstellen“, nicht „OK“). Keine Großschreibung ganzer Sätze, keine Abkürzungen, die nur Buchhalter kennen.

**Farbwahl:**
- Rose = die eine Handlung, die jetzt dran ist. Pro Screen eine Rose-Fläche; alles andere Slate. Rose ist Akzent, nicht Tapete.
- Zustände eindeutig und nie nur über Farbe: Erfolg (emerald), Warnung (amber), Gefahr (red-600) immer mit Icon oder Text dazu.
- Flächen trennen über kleine Tonschritte (`slate-50` auf Weiß) statt über mehr Linien. Kontrast Text/Fläche mindestens 4,5 : 1 (große Titel und Icons 3 : 1).
- Keine neuen Farben und keine Verläufe. Fremde Vorschläge aus Skills oder MCP-Bausteinen (blue/indigo/violet) werden auf Rose/Slate umgestellt.

**Löschen und Unwiderrufliches:**
- Rückgängig vor Nachfragen: wo es geht, sofort löschen mit Toast „Rückgängig“ und erst danach das echte DELETE senden (Muster `useVerzoegertesLoeschen`). Wo nicht (hängt etwas daran, Versand, Storno), fragt `useConfirm` mit `variant: 'danger'`.
- Der Dialog sagt **was** gelöscht wird (Name/Nummer), **was daran hängt** („3 Rechnungen betroffen“) und **ob es rückgängig geht**. Knopf trägt das Verb („Rechnung löschen“), nicht „Ja“. Der sichere Weg („Abbrechen“) ist vorbelegt, der Gefahrenknopf rot und nie der Standardfokus.
- Gesperrtes Löschen erklärt den Grund und was der Nutzer stattdessen tun kann (z. B. „Erst die Rechnung stornieren“).
- Nach dem Löschen: Fokus und Liste springen sinnvoll weiter, kein leerer Bildschirm ohne Hinweis.

**Nutzerführung (intuitiv, ohne Handbuch):**
- Der nächste Schritt ist immer erkennbar: eine Primäraktion; leere Listen zeigen einen Leer-Zustand mit dem Knopf, der etwas anlegt („Noch keine Angebote. Angebot erstellen“).
- Reihenfolge wie im Arbeitsablauf des Handwerkers (Anfrage → Angebot → Auftrag → Rechnung), Felder in der Reihenfolge, in der man sie ausfüllt; Wichtigstes oben links.
- Vorbelegen statt Abfragen (Datum heute, letzter Kunde, Standardsätze); Auswahl statt Freitext, wenn die Menge bekannt ist.
- Fehler direkt am Feld erklären und sagen, wie es geht; Formulare verlieren beim Fehler nie Eingaben.
- Lange Abläufe in sichtbaren Schritten; der Nutzer sieht, wo er ist und was noch fehlt.
- Handy: Daumenzone, mindestens 44 × 44 px Tippfläche, nur oben Safe-Area, unten nichts; die Seite ist eine eigene Komposition, nicht die verkleinerte PC-Seite.
- Zustände vollständig bauen (Laden, leer, Fehler, gesperrt, Fokus, deaktiviert), sichtbarer Fokus-Ring, Alt-Texte, mit Worst-Case-Daten prüfen (langer Firmenname, 0 und 500 Einträge, Umlaute, große Beträge).

## 4b. Texte in der Oberfläche (nach humanizer-de)

Alle sichtbaren Texte (Knöpfe, Toasts, Fehlermeldungen, Leer-Zustände, Platzhalter, Info-Buttons, Tooltips) klingen wie ein Kollege im Betrieb, nicht wie eine KI und nicht wie ein Formular von der Behörde. Modus **Sachlich**.

- **Kurz und konkret.** Sagen, was passiert ist oder was zu tun ist, mit dem Namen der Sache: „Rechnung RE-2026-014 gespeichert.“ statt „Der Vorgang wurde erfolgreich abgeschlossen.“ Fehler: *was* ging schief und *was* kann der Nutzer tun.
- **Anrede einheitlich:** neutral im Imperativ/Infinitiv („Kunde auswählen“, „Bitte Datum eintragen“) oder „Sie“. Kein „du“ in der App-Oberfläche, nie innerhalb einer Seite mischen. Wörter wie „Wir“ und „Ich“ nur, wenn wirklich jemand spricht.
- **Keine KI-Floskeln:** nicht „nahtlos“, „intuitiv“, „leistungsstark“, „umfassend“, „Lösung“, „entdecken“, „eintauchen“, „Los geht’s“, „Ups!“, „Großartig!“. Keine Chatbot-Höflichkeit („Gerne helfe ich“, „Hoffentlich hilft das“), keine Ausrufezeichen-Ketten, kein Emoji.
- **Keine Muster-Stapel:** keine Dreierlisten aus Gewohnheit („schnell, einfach und sicher“), kein „nicht nur … sondern auch“, keine Gegenüberstellung „Nicht X, sondern Y“, kein Doppelpunkt-Titel nach Schema, keine Gedankenstriche als Satzzeichen (Punkt, Komma oder Doppelpunkt). Konnektoren („außerdem“, „zudem“, „darüber hinaus“) weglassen.
- **Handwerker-Wörter statt Abstrakta und Anglizismen:** „Rechnung“ statt „Dokument“, „speichern“ statt „persistieren“, „Eintrag“ statt „Entität“; kein Denglisch („Feedback submitten“, „Item“, „Workflow ausrollen“). Etablierte Begriffe bleiben (Angebot, Nachkalkulation, KI-Hilfe).
- **Typografie im Text:** deutsche Anführungszeichen „…“, Auslassung `…`, geschütztes Leerzeichen zwischen Zahl und Einheit (`12 Std.`), Beträge `1.234,50 €`, Datum `31.10.2026`.
- **Nichts erfinden:** keine Zahlen, Fristen oder Rechtsaussagen in Texten, die nicht aus dem Auftrag oder dem Code stammen. Im Zweifel den Nutzer fragen.
- **Kurz gegenlesen:** Bevor ein Text bleibt: Würde ein Meister das so am Telefon sagen? Wenn nicht, kürzer und direkter. Bei größeren Textmengen (Hilfetexte, Info-Buttons, Onboarding) kann der Skill `humanizer-de` auf die Texte angewendet werden.

## 5. Frontend-Sicherheit (nur Frontend)

Details und Begründung: Abschnitt „Sicherheit im Frontend“ in `FRONTEND_UI.md`. Kurzfassung:

- HTML anzeigen: nur über `sanitizeHtml()` (`src/lib/htmlSanitizer.ts`), auch bei serverseitig bereinigten Daten. Gilt auch für `innerHTML` und `insertAdjacentHTML`.
- Tags entfernen: `stripHtmlTags()`. Entities entpacken: `unescapeHtmlEntities()`. URLs für `<iframe>`/`<img>`: `toSafeResourceUrl()`. HTML nur auslesen: `parseHtmlInert()`/`htmlToText()`.
- URL-Parameter mit `encodeURIComponent()`. Keine Geheimnisse im Frontend-Code, keine echten Personendaten in Tests (`Max Mustermann`).

## 6. Prüfen und abgeben

Nach jeder Frontend-Änderung: `PRUEFUNG.md` lesen und befolgen (Spec für den geänderten Ablauf, Screenshots in 1440 und 1920 bzw. Handy, sechs Fragen schriftlich beantworten). Danach `npm run lint`, `npm run build`, gezielte Tests. Nicht committen: das erledigt der Hauptagent im gemeinsamen `review-and-ship`.

Ohne weitere Vorgabe fragt dieser Skill bei Prototypen und Mockups zuerst, was gebaut werden soll, und liefert dann HTML-Artefakte oder Produktionscode.
