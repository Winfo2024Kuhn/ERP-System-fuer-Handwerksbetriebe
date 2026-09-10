# Zusatzplan: systemeigene Eingaben und Meldungen

Hauptplan: [Monatsabschluss und DATEV](2026-09-09-monatsabschluss-datev.md). Issue #102, Featurebranch `codex/monatsabschluss-datev`, Kontext-Log `docs/superpowers/plans/2026-09-09-monatsabschluss-datev-log.md`. Nutzerscope bereits ausdrücklich freigegeben: alle sichtbaren Standardpicker/-dialoge durch systemeigene UI ersetzen, alle Mengen-/Dezimalfelder deutsch, Nullwerte bei Klick-/Tab-Fokus leeren. Kein weiterer Designfreigabepunkt.

## Grenzen und Ablauf

- Pflichtlektüre und Reviewregeln des Hauptplans gelten. Frontenddesign-Skill und Browserprüfung für beide Apps; keine personenbezogenen Echtdaten in Tests. Nutzerfreigabe vom 09.09.2026: Wiederkehrende UI und Logik auslagern und wiederverwenden; dafür ist keine erneute Rückfrage nötig. Vorhandene Bausteine bevorzugen, keine neue Komponentenbibliothek. Geteilte Änderungen zwischen parallelen Tasks über den Orchestrator koordinieren, damit Dateibesitz und Review-Gates erhalten bleiben.
- Bestehende gestaltete Textfelder, Checkboxen und Radiobuttons nicht allein wegen ihrer HTML-Grundlage umbauen. Ziel sind sichtbare Browserstandard-Picker, Spinner, Meldungen und fehlerhafte Zahleneingabe. Betriebssystem-Dateiauswahl, Kamera-/Push-Berechtigungen bleiben echte Browser-/Betriebssystemfunktionen.
- Kennnummern (Personalnummern, Lohnarten etc.) bleiben Ziffernstrings einschließlich führender Nullen, ohne Mengenformatierung/Nullwertlöschung. Ganzzahlige Mengen, Jahre, Ports mit passenden Grenzen und Ganzzahlprüfung behandeln; keine Dezimalstellen dort erfinden.
- Kein kosmetischer globaler Austausch `type=number` gegen `text`: bisherige `Number(value)||0`, `parseFloat` und `parseInt` zerstören Leerzustände und Kommazahlen. Jeder Aufrufer führt einen String-Draft, validiert vollständig vor Mutation/Berechnung und wandelt erst dann in den fachlichen Payload um. Leere Pflichtwerte sind keine 0; unvollständige Werte weder still zurücksetzen noch speichern.
- Alle API-/Validierungsfehler systemeigen toasten, konkrete Feldhinweise zusätzlich. Bestehende `useConfirm()`-Aufrufe sind bereits systemeigene Dialoge. Native `prompt` durch Eingabedialog mit Pflichtvalidierung ersetzen, nicht durch Toast.
- Jeder Task besitzt nur unten aufgelistete Dateien plus explizit benannte Tests. Abhängige Abschnitte erst nach Merge und Review starten. Keine fremden Änderungen zurücksetzen; taskeigene Worktrees jeweils vom geprüften Featurebranch erstellen. Gemeinsam genutzte Testkonfiguration nicht parallel ändern.
- Die Kategorien A/B/C beschreiben Abhängigkeiten, keine zusätzliche Wartepflicht nach dem gesamten Hauptfeature. Verbindliche gemeinsame Ausführungsreihenfolge: Abschnitt 1 [1,3], Abschnitt 2 [2,4,7], Abschnitt 3 [5,8,9], Abschnitt 4 [6,10,11], Abschnitt 5 [12,13,14]. So bleibt jeder Abschnitt dateidisjunkt, maximal drei Tasks, und jeder Consumer startet auf geprüften Produzenten. Je Abschnitt Code-Reviewer und bei Frontend-Änderungen separater Designreview nach loese-problem; maximal zwei Nachbesserungen. Abschluss: beide Frontendtests/-builds, passende Backend-Gesamttests des Hauptumfangs, Browserprüfung, Diff und einmal Graphify durch Orchestrator.

| Abschnitt | Parallel | Voraussetzung | Ergebnis |
| --- | --- | --- | --- |
| A | 7 in Runde 2, 8 in Runde 3 | bestehende Basis; unabhängig vom Hauptfeature | feste PC-/Mobile-Bausteinverträge |
| B | 9 in Runde 3, 10/11 in Runde 4 | jeweilige Grundlagen geprüft | Personal/Zeit, Finanzen/Miete, Mobile migriert |
| C | 12/13/14 in Runde 5 | PC-Grundlagen geprüft | übrige PC-Eingaben vollständig migriert |

Branches immer `codex/systemeingaben-task-N`, Worktrees `.Codex/worktrees/systemeingaben-task-N` für N=7–14. Kontext-Log nur nach gemeinsamem append-only/Lock-Protokoll des Hauptplans. Code-/Designreview-Berichte schreibt ausschließlich jeweiliger Reviewer in dessen Worktree.

## Audit-Baseline und verbindliche Baustein-API

Graphify-Abfrage vor Audit ausgeführt; anschließend Source-Literals und vorhandene UI gelesen. PC `components/ui/input.tsx` ist bislang gestyltes HTML ohne Zahlenlogik. `select-custom.tsx` und `datepicker.tsx` haben eigene Popups, aber ihre div-Trigger keine ausreichende Tastaturbedienung; DatePicker bislang ohne min/max/required/id. `toast.tsx`, `confirm-dialog.tsx`, `dialog.tsx` existieren. Kein universelles Dezimal-/Zeitfeld vorhanden. Mobile besitzt `components/MobileDatePicker.tsx`, aber keine eigenen Toast-/Confirm-/Select-Provider. Dessen verstecktes readOnly-required-Feld garantiert keine Pflichtvalidierung. Keine fremde Toastbibliothek gefunden; abweichender lokaler Toast in PC `components/kasse/KasseShortcuts.tsx`.

PC-Neuverträge, in Abschnitt A vollständig implementieren und testen:

- `components/ui/decimal-input.tsx`: `DecimalInput` mit `value:string`, `onChange:(draft:string)=>void`, `id`, `name`, `label?`, `aria-label`, `required?`, `min?`, `max?`, `integer?`, `disabled?`, `readOnly?`, `className?`, `error?:string`. Gestyltes Textinput mit `inputMode=decimal` bzw. numeric für Ganzzahlen; nullwertigen Draft (`0`, `0,00`) bei Fokus leeren, Nichtnullwerte erhalten. In Bearbeitung keinen automatischen numerischen Commit oder stillen Fallback. Externe neue Werte sauber übernehmen.
- `lib/numberInput.ts`: `formatDecimalInput(value:number):string`, `validateDecimalInput(draft:string, rules:{label:string;required?:boolean;min?:number;max?:number;integer?:boolean}): {valid:true;value:number|null}|{valid:false;message:string}`. Vollständiges deutsches Zahlenformat, finite Werte/Grenzen/Ganzzahligkeit; optional leer liefert null, erforderlich leer Fehler. Kein `parseFloat`-Teilpräfix als gültige Zahl. Gleiche Funktion unmittelbar vor jeder Aktion aufrufen, ausdrücklich auch Buttonhandler ohne `<form>`. Alle Werte vor erstem Request validieren. API-Zahlen intern numerisch, Anzeigen de-DE, DATEV folgt separater Spezifikation.
- `components/ui/time-input.tsx`: `TimeInput` mit `value:string`, `onChange:(draft:string)=>void`, `label?`, `id`, `name`, `aria-label`, `required?`, `disabled?`, `className?`, `error?:string`. Gestyltes Textfeld, sichtbarer Hinweis `HH:mm`, kein nativer time-Picker; Eingaben während Bearbeitung als String halten. Export `validateTimeInput(draft, {label,required?})` als entsprechendes discriminated result mit gültigem HH:mm-String oder optional null. Erlaubte Zeiten 00:00–23:59, keine unbemerkte Normalisierung ungültiger Werte; Start/Ende-Regel im Aufrufer prüfen.
- Bestehendes `Select` behält `options/value/onChange`; additive `id`, `aria-label`, `name`, `required`, `error`, Options-`disabled?`, Tastatur/Fokus/Escape/ARIA. Bestehender DatePicker behält API, additive `id`, `aria-label`, `name`, `required`, `min`, `max`, `error`. Picker ersetzen native Kontrolle ohne Validierungsverlust; außerhalb Form explizit prüfen. Portal-/Dialogebenen und Viewportränder beachten.
- `components/ui/color-input.tsx`: `ColorInput` (`value:string`, `onChange`, `aria-label`, `className?`, `disabled?`) als eigene Palette plus validierte Hex-Eingabe. Bestehende kundenspezifische Hexwerte erhalten, keine Beschränkung nur auf Rose. Kein nativer Farbdialog.
- Mobile lokale `components/ui/toast.tsx`: `ToastProvider/useToast` mit `success/error/warning/info(message)`; `confirm-dialog.tsx`: `ConfirmProvider/useConfirm` entsprechend PC mit Promise<boolean>, deutsche Labels, Cancel/Escape/Fokus, modal sichtbaren Toasts. `select-custom.tsx` mit PC-kompatibler Kern-API und Touchzielen. `decimal-input.tsx` und `lib/numberInput.ts` mit demselben numerischen Vertrag wie PC, app-lokal ohne Monorepo-Umbau. Bestehenden MobileDatePicker erweitern, keinen zweiten Kalender bauen.

## Abschnitt A

### Task 7: PC-Grundlagen

- Branch `codex/systemeingaben-task-7`; Worktree `.Codex/worktrees/systemeingaben-task-7`.
- Exklusive Dateien unter `react-pc-frontend/`: `src/components/ui/decimal-input.tsx`, `src/components/ui/time-input.tsx`, `src/components/ui/color-input.tsx`, `src/components/ui/select-custom.tsx`, `src/components/ui/datepicker.tsx`, `src/lib/numberInput.ts`; Tests jeweils gleichnamig `.test.tsx` bzw. `.test.ts`; `e2e/systemeingaben-task-7.spec.ts`.
- Produces: alle obigen PC-Verträge. Consumes: vorhandenes Input/Design/Toast unverändert; keine Seitenintegration.
- Steps: Bausteine samt vollständiger Validierung implementieren; additive Picker-API rückwärtskompatibel; semantische Trigger, beschriftete Optionen, Pfeile/Enter/Escape/Tab und Datumslimits. Fokusnull und Nichtnullwerte, unvollständiges Komma, min/max/integer, optional/required testen. Eigene Farbpalette/Hex unter Erhalt freier Bestandsfarben.
- E2E: nach Integration durch B/C dieselben Primitives auf realer Arbeitszeit-/Farbseite prüfen; keine künstliche Produktionsroute für Tests anlegen. Bis dahin interaktive Komponententests. Geöffnete Popups vor Modal, Viewportränder, sichtbarer Rosefokus, keine nativen Dialogereignisse.

### Task 8: Mobile-Grundlagen

- Branch `codex/systemeingaben-task-8`; Worktree `.Codex/worktrees/systemeingaben-task-8`.
- Exklusive Dateien unter `react-zeiterfassung/`: `src/components/ui/toast.tsx`, `src/components/ui/confirm-dialog.tsx`, `src/components/ui/select-custom.tsx`, `src/components/ui/decimal-input.tsx`, `src/lib/numberInput.ts`, `src/components/MobileDatePicker.tsx`, `src/main.tsx`; gleichnamige `.test.tsx`/`.test.ts` für die Bausteine; `e2e/systemeingaben-task-8.spec.ts`.
- Produces: Mobile-Verträge oben, Provider am App-Einstieg. Consumes: vorhandenes Mobile-Design. Kein Export/Import aus dem PC-Sourcebaum.
- Steps: Provider appweit einbinden, nicht pro Seite; Toasts zugänglich und auch vor offenem Dialog sichtbar, Confirm mit Cancel/Escape und Fokuswiederherstellung. Kalender min/max/Heute-Grenze und Pflichtwert korrekt behandeln. Select Touch-/Tastaturbedienung.
- Tests: gestapelte Fehler, Dismiss/Unmount, Confirm-Abbruch/Bestätigung genau einmal, Datumslimits/Heute, Zahlennullfocus. E2E-Verifikation nach Task11 auf echten Seiten, feste kleine Viewports und sicherer Abstand zur Bottom-Navigation.

## Abschnitt B

### Task 9: PC-Zeit und Personal

- Branch `codex/systemeingaben-task-9`; Worktree `.Codex/worktrees/systemeingaben-task-9`.
- Exklusive Dateien unter `react-pc-frontend/src/`: `pages/MitarbeiterEditor.tsx`, `pages/ZeiterfassungZeitkonten.tsx`, `pages/ZeiterfassungKalender.tsx`, `pages/ZeiterfassungSteuerberater.tsx`, `pages/TerminKalender.tsx`, `pages/ArbeitszeitartEditor.tsx`, `pages/FirmaEditor.tsx`, `components/ZeitkontoKorrekturenModal.tsx`, `components/VerrechnungslohnRechnerDialog.tsx`, `components/firma/LohnStammdatenPanel.tsx`, `components/mitarbeiter/StundenlohnHistorieList.tsx`, `components/langzeitkrankmeldung/StufenplanTabelle.tsx`, `components/StundensatzEditModal.tsx`. Tests: gleichnamige `.test.tsx` zu diesen Dateien; `react-pc-frontend/e2e/systemeingaben-task-9.spec.ts`.
- Consumes Task7. Produces vollständig validierte Zeit-/Personalformulare mit unveränderten fachlichen Payloads.
- Native Zeitstellen: MitarbeiterEditor 1512–1513 (Screenshot), Zeitkonten 19, Kalender 1338/1347, TerminKalender 1286/1297. Native Selects MitarbeiterEditor 1196, Steuerberater 199/211; nativer Prompt Korrekturen 134. Firma Farbauswahl 768. Alle übrigen Dateien enthalten Zahlenfelder.
- Steps: Drafts/Submitvalidierung für jede Zahlen-/Zeitbearbeitung; Arbeitszeit-Vorschau darf ungültige Pflichtwerte nicht an API senden. Korrekturstorno bekommt echten Pflichtgrunddialog. Vorhandenes NumberCell im Verrechnungslohnrechner ergänzen bzw. Baustein integrieren, stilles Zurücksetzen ungültiger Werte entfernen; keinen fachlichen Rechenalgorithmus ändern. Range bei Gewinn nur verändern falls noch ungestaltete Track/Thumb-Optik.
- Tests/E2E: Screenshotdialog Montag 7,7 erhalten, Samstag 0 per Klick/Tab leer, 8,5 übernehmen; leere Pflichtstunde und 24,1 blockieren. Zeit 09:05 gültig, 25:99 blockiert, optional leer möglich. Negative erlaubte Korrektur und Pflichtgrund/Abbrechen; Mitarbeiterdaten/Nummern unverändert. Select Tastatur und geöffneter Uhrzeitbereich ohne nativen Picker.
- Zusätzlicher Testbesitz aus Abschnitt-2-Review: `pages/MitarbeiterEditor.task8.test.tsx` und `pages/ZeiterfassungZeitkontenTask10.test.tsx` gehören ebenfalls zu Task9. Die zugänglichen vollständigen Datum-Labels sind bereits angepasst; bei der Zahlenmigration deren bisherige Spinbutton-/Punkt-Eingaben auf deutsche Texteingaben umstellen, fachliche Vorschau-/Versionsprüfungen erhalten. Im Arbeitszeitdialog „Vorschau anzeigen“ als klare Primäraktion gestalten.

### Task 10: PC-Finanzen und Miete

- Branch `codex/systemeingaben-task-10`; Worktree `.Codex/worktrees/systemeingaben-task-10`.
- Exklusive Dateien unter `react-pc-frontend/src/`: `pages/BelegeKasseEditor.tsx`, `pages/RechnungsuebersichtEditor.tsx`, `pages/MietabrechnungEditor.tsx`, `components/kasse/KassenbuchAbschlussLeiste.tsx`, `components/kasse/KasseShortcuts.tsx`, `components/kasse/KostenstellenSplitsEditor.tsx`, `components/mietabrechnung/ParteienView.tsx`, `components/mietabrechnung/KostenpositionenView.tsx`, `components/mietabrechnung/RaeumeView.tsx`. Tests gleichnamig `.test.tsx`; `react-pc-frontend/e2e/systemeingaben-task-10.spec.ts`.
- Consumes Task7, bestehende Toast/Confirm/Dialog. Produces deutsche Beträge/Flächen/Zählerwerte und eigene Datums-/Meldungsbedienung.
- Audit: native Belegdatum-/Datumsfilter in BelegeKasseEditor 1120/1123/1522/2044/2047; Rechnungsübersicht 893. Alerts in BelegeKasseEditor 430/436/1383/1386/1414. Mietobjektprompt 41; lokaler KasseShortcuts-Toast 59–207.
- Steps: alle numerischen Felder einschließlich bereits textbasierter Kassenabschlusswerte migrieren; Zahlengrenzen, optionale Verbrauchsberechnung, Jahre/Ganzzahlen erhalten. Prompt echter Eingabedialog; Alerts und lokaler Toast durch vorhandenen Toast. Keine bisherigen Buchungs-/Steuerregeln ändern.
- Tests/E2E: 12,50 ergibt Payload 12.5; Leer/12, blockiert ohne Mutation; min/max/Splitprozente; Datepickerfilter und Belegdatum; Kassenaktion Fehler bei offenem Dialog sichtbar, Mietobjekt Abbrechen sendet nichts. Negative Werte nur nach vorhandener Fachregel.

### Task 11: Mobile-Integration

- Branch `codex/systemeingaben-task-11`; Worktree `.Codex/worktrees/systemeingaben-task-11`.
- Exklusive Dateien unter `react-zeiterfassung/src/`: `pages/UrlaubsantragPage.tsx`, `pages/AbwesenheitenPage.tsx`, `pages/AnfrageNotizenPage.tsx`, `pages/ProjektNotizenPage.tsx`, `pages/LieferantLieferscheinePage.tsx`, `pages/LieferantReklamationCreatePage.tsx`, `pages/LieferantReklamationDetailPage.tsx`, `pages/ZeiterfassungPage.tsx`, `components/ScannerModal.tsx`, `pages/MwstRechnerPage.tsx`. Tests gleichnamig `.test.tsx`; `react-zeiterfassung/e2e/systemeingaben-task-11.spec.ts`.
- Consumes Task8. Audit: Urlaub zwei native Daten/ein Select, Abwesenheiten zwei Selects; Notizseiten je acht Alerts/zwei Confirms, Lieferscheine fünf Alerts, ReklamationCreate drei, Detail einer, Zeiterfassung zwei, Scanner zwei. MwstRechner bereits gestyltes Dezimalfeld ohne Nullfokus.
- Steps: native Controls/Meldungen durch Mobile-Bausteine; asynchrone Confirmauflösung vor Löschrequest, Abbruch ohne Mutation. Vorhandene aussagekräftige Inlinefehler behalten, Fehler zusätzlich toasten. Keine Kamera-/Offline-/Pushfachlogik ändern.
- Tests/E2E: Urlaub Datum/Filter per Touch und Tastatur, Pflicht/Min-Grenze; Notiz/Bild löschen Abbrechen/Bestätigen; Scan-/Upload-/Buchungsfehler als systemeigener Toast, kein Browserdialog; Mwst 0-Fokus/Komma und Berechnung. Offlinefehler und 390px-Viewport, Modals/Bottom-Navigation überdecken Toasts nicht.

## Abschnitt C

### Task 12: PC-Einkauf und Artikel

- Branch `codex/systemeingaben-task-12`; Worktree `.Codex/worktrees/systemeingaben-task-12`.
- Exklusive Dateien unter `react-pc-frontend/src/`: `pages/BestellungenUebersicht.tsx`, `pages/ArtikelDetail.tsx`, `components/CreateArticleModal.tsx`, `components/artikel/ArtikelAuswahlDialog.tsx`, `components/LieferantDokumentModal.tsx`, `components/LieferantDokumentImportModal.tsx`, `components/LieferantDokumenteTab.tsx`, `components/ZuordnungModal.tsx`. Tests gleichnamig `.test.tsx`; `react-pc-frontend/e2e/systemeingaben-task-12.spec.ts`.
- Consumes Task7. Native Confirm Bestellungen 825, LieferantDokumenteTab 77; Alerts dort 83/85; übrige Fälle Mengen/Preise/Steuern einschließlich textbasierter Artikelwerte.
- Steps: String-Drafts und validierte Umwandlung vor Aktion; alle Mengen/Preis-/Prozentfelder; Confirm/Toast wiederverwenden. Bestehende Einkaufs-/Import-/Zuordnungsregeln behalten, keine Änderungen am Dokumentinhalt.
- Tests/E2E: 0-Fokus Artikelmenge, 12,50 Preis exakt im Payload, ungültige/fehlende Pflichtzahl verhindert Import/Zuordnung, Bestellungen ausblenden/Beleg löschen Abbruch bzw. einmalige Mutation, Serverfehler Toast.

### Task 13: PC-Projekte, Kontakte und Dokumente

- Branch `codex/systemeingaben-task-13`; Worktree `.Codex/worktrees/systemeingaben-task-13`.
- Exklusive Dateien unter `react-pc-frontend/src/`: `pages/ProjektEditor.tsx`, `pages/AnfrageEditor.tsx`, `pages/Kundeneditor.tsx`, `pages/Leistungseditor.tsx`, `pages/DokumentUebersichtEditor.tsx`, `components/ProjektErstellenModal.tsx`, `components/KundeAnlegenForm.tsx`, `components/AusgangsrechnungUploadModal.tsx`, `components/DocumentManager.tsx`, `components/DokumentHierarchie.tsx`, `components/KategorieAnalyseModal.tsx`. Tests gleichnamig `.test.tsx`; `react-pc-frontend/e2e/systemeingaben-task-13.spec.ts`.
- Consumes Task7. Native Selects ProjektErstellenModal 778, AusgangsrechnungUploadModal 424, DocumentManager 383, KategorieAnalyseModal 268; sonst numerische Felder.
- Steps: eigene Selects mit Labels/Keyboard, sämtliche Mengen-/Geld-/Prozentfelder mit validierten Drafts. Zahlungsziel Ganzzahl, leere Eingabe nicht still auf 8 zurücksetzen; sonstige Text-/Kontaktfelder erhalten.
- Tests/E2E: Projekt/Anfrage Betrag Komma, Zahlungsziel 0 löschen/neue Ganzzahl/leere Pflicht ablehnen, Rechnungsupload/Zuordnung Select per Tastatur, kein nativer Popup, API-Payloads exakt und keine Requests bei ungültigen Werten.

### Task 14: PC-Editoren und Einstellungen

- Branch `codex/systemeingaben-task-14`; Worktree `.Codex/worktrees/systemeingaben-task-14`.
- Exklusive Dateien unter `react-pc-frontend/src/`: `pages/DocumentBuilder.tsx`, `pages/EmailTextvorlagenEditor.tsx`, `components/document-editor/index.tsx`, `components/document-editor/ServiceBlock.tsx`, `components/document-editor/SummenFooter.tsx`, `components/document-editor/RabattDialog.tsx`, `components/formularwesen/PropertiesSidebar.tsx`, `components/TiptapEditor.tsx`, `components/settings/sections/EmailSettingsSection.tsx`, `components/website/BildEditorModal.tsx`. Tests gleichnamig `.test.tsx`; `react-pc-frontend/e2e/systemeingaben-task-14.spec.ts`.
- Consumes Task7. Native Select DocumentBuilder 944 und Tiptap 430/778; Farbpicker PropertiesSidebar 129 und Tiptap 506/523/854/871; Confirm Emailvorlagen 733; Zahlen in übrigen Dateien. BildEditor range 244 nur migrieren falls Track/Thumb tatsächlich ungestaltet.
- Steps: deutsche Zahlen und Pflichtvalidierung einschließlich Editoraktionen ohne Form; eigene Schrift-/Dropdown-/Farbauswahl, existing Editorformatierung und Auswahl nicht verlieren. Email-Ports ganzzahlig mit Grenzen, fachliche Prozentgrenzen. Keine PDF-Metriken oder Drucklayoutregeln verändern, keine native Farbpalette.
- Tests/E2E: Dokumentmenge/Preis/Rabatt 0-Fokus und Komma, unvollständige Zahl blockiert Übernahme; Schrift-/Farbwahl bei erhaltener Textselektion, kundenspezifischer Hexwert bleibt; Email-Port ungültig blockiert, Vorlagenlöschung Cancel/Confirm. Zoomslider nur falls geändert per Tastatur/Touch prüfen.

## Gesamtabnahme der Ergänzung

Statische Suche nach verbleibenden sichtbaren `select`, `type=number/date/time/datetime-local/month/week/color`, `alert/confirm/prompt` in Produktionsquellen beider Frontends; Treffer klassifizieren, keine Tests/XSS-Strings oder systemeigene Confirm-Hooks als native Fehler zählen. Zusätzlich bereits textbasierte numeric/decimal-Felder auf Fokusnull/Validierung prüfen. Keine Behauptung, alle Controls migriert zu haben, solange bekannte sichtbare Treffer offen sind. Alle fachlichen numerischen Payloads bleiben korrekt; serverseitige bestehende Grenzen dürfen nicht durch UI-Umstellung umgangen werden. Falls relevante serverseitige Validierung fehlt, konkret an Orchestrator melden und zusätzlichen disjunkten Backendtask planen, nicht still als erledigt markieren.

Nach Abnahme von Task7 wird Task9 bereits in Abschnitt3 parallel zu5/8 ausgeführt: er konsumiert ausschließlich die geprüften PC-Bausteine und besitzt disjunkte Dateien. Task11 folgt auf den abgenommenen Mobile-Bausteinen in Abschnitt4. Dadurch bleiben maximal drei Tasks parallel und alle Review-Gates erhalten, bei fünf statt sechs Abschnitten.
