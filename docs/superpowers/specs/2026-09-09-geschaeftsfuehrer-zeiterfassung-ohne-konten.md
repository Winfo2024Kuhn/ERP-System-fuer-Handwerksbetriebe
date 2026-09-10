# Geschäftsführer: Zeiterfassung ohne Stunden- und Urlaubskonto

Datum: 2026-09-09

Branch: `codex/geschaeftsfuehrer-zeiterfassung-ohne-konten`

## Zugehörige Issues

- [#103 Backend und fachliche Kontenregeln](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/103)
- [#104 PC-Monatsansicht und Urlaub](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/104), abhängig von #103
- [#105 Mobile Auswertung und Urlaub](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/105), abhängig von #103

## Ziel

Geschäftsführer sollen ihre tatsächliche Arbeit weiterhin auf Projekte stempeln können. Diese Zeiten bleiben für die produktive Projektabrechnung nutzbar. In der Monatsansicht der PC-Zeiterfassung und in der mobilen Auswertung sehen sie weiterhin, wie viel sie gearbeitet haben.

Für Geschäftsführer werden kein Stundenkonto mit Plus- und Minusstunden und kein Urlaubskonto geführt. Urlaub bleibt eine erfasste Abwesenheit: Der Geschäftsführer kann ihn eintragen, das Team sieht seine Abwesenheit und vergangene Urlaube bleiben später nachvollziehbar.

Der erste Auftrag umfasste die Vorbereitung durch Spec, Issues und einen Branch. Mit den unter "Getroffene Entscheidungen" festgehaltenen Klärungen vom 2026-09-10 ist die Umsetzung der drei Pakete freigegeben.

## Nicht-Ziele

- Keine Entfernung der projektbezogenen Zeiterfassung oder ihrer Abrechenbarkeit.
- Keine Abschaffung von Urlaubseinträgen, Team-Abwesenheitsansichten oder Urlaubshistorie.
- Keine Änderung des fachlichen Kontenmodells für andere Mitarbeiter.
- Keine neuen Berechtigungsrollen oder allgemeine Neugestaltung der Zeiterfassung.
- Keine Löschung vorhandener Zeiten, Abwesenheiten oder Abschlüsse.
- Keine stillschweigende Entscheidung über Stichtage, historische Kontenwerte oder das Genehmigungsverfahren für Geschäftsführerurlaub.

## Architektur und Ablauf

### Vorhandene Anknüpfungspunkte

Die bestehende Mitarbeiterkonfiguration enthält `Mitarbeiter.istGeschaeftsfuehrer` und `fuehrtZeitkonto`. Die Stundenkontenlogik liegt im `ZeitkontoService` und berücksichtigt Versionen und Pausen. Diese vorhandenen Bereiche bilden die Anknüpfungspunkte der Planung.

Im `UrlaubsantragService` prüft `createAntrag` derzeit das Restkontingent. `approveAntrag` verlangt eine `ZeitkontoVersion` je Buchungstag und erzeugt Abwesenheiten nur bei positiven Sollstunden. Für Geschäftsführer muss die Urlaubserfassung und die Entstehung sichtbarer Abwesenheiten von diesen Kontenvoraussetzungen entkoppelt werden: Fehlendes Urlaubsguthaben, eine fehlende Zeitkontoversion oder nicht vorhandene Sollstunden dürfen ihre Urlaubsdokumentation nicht verhindern. Die konkrete Umsetzung bleibt dem späteren Grobplan vorbehalten.

Als Umsetzungsvorschlag soll das bestehende Geschäftsführerkennzeichen für die fachliche Regel verwendet werden. Die konkrete Wechselwirkung mit `fuehrtZeitkonto` und zeitabhängigen Konfigurationen ist im Grobplan festzulegen; ein zusätzliches Berechtigungsmodell ist nicht vorgesehen.

### Arbeit erfassen und anzeigen

1. Ein Geschäftsführer erfasst Arbeitszeiten weiterhin über die bestehenden Abläufe, insbesondere durch Stempeln auf Projekte.
2. Die tatsächlichen Arbeitszeiten bleiben gespeichert, zugeordnet und abrechenbar. Bestehende Regeln zur Berechnung tatsächlicher Arbeitszeit einschließlich Pausen bleiben zu berücksichtigen.
3. PC-Monatsansicht und mobile Auswertung zeigen weiterhin die tatsächliche Arbeitszeit im gewählten Monat.
   Urlaub und andere Abwesenheitsgutschriften zählen dabei nicht als tatsächlich geleistete Arbeit.
4. Für Geschäftsführer entfallen Sollzeitvergleiche, Plus- und Minusstunden, Überträge und sonstige Stundenkontosalden. Das gilt für Berechnung und betroffene Ausgaben sowie für ihre Darstellung; ein ausgeblendetes, im Hintergrund weitergeführtes Konto erfüllt die Anforderung nicht.
5. Nicht anwendbare Kontenwerte dürfen nicht als scheinbar gültiger Nullsaldo dargestellt werden. Wie dies im bestehenden API-Vertrag abgebildet wird, entscheidet der Grobplan.

### Urlaub erfassen und nachvollziehen

1. Der Geschäftsführer kann Urlaub weiterhin als Abwesenheit eintragen.
2. Der Urlaub bleibt in den bestehenden Teamansichten sichtbar, sodass Kollegen seine Abwesenheit erkennen.
3. Vergangene Urlaubseinträge bleiben über die bestehenden historischen Ansichten nachvollziehbar.
4. Für Geschäftsführer werden kein Urlaubsanspruch, kein Resturlaub und kein Urlaubskonto geführt oder als Konto angezeigt. Urlaubseinträge dienen weiterhin der Abwesenheitsdokumentation; sie dürfen kein erforderliches Urlaubsguthaben voraussetzen.
5. Ob Geschäftsführerurlaub das bestehende Genehmigungsverfahren durchläuft, wird vor Umsetzung entschieden. Diese Spec legt keine automatische Genehmigung fest.

### Bestehende Daten und Statuswechsel

Vorhandene Zeitbuchungen, Urlaubs- und andere Abwesenheitseinträge sowie Abschlüsse bleiben erhalten. Der Wechsel in den Geschäftsführerstatus und zurück muss im Grobplan ausdrücklich mit der vorhandenen Versionierung abgestimmt werden. Bis zur Entscheidung über historische Konten ist weder eine rückwirkende Neuberechnung noch eine Entfernung bestehender Kontenwerte freigegeben.

## Betroffene Bereiche und Umsetzungspakete

### Paket 1: Backend und fachliche Kontenregeln

Betroffen sind Mitarbeiterkonfiguration, `ZeitkontoService`, die zugehörigen Auswertungs-/DTO-Verträge sowie Urlaubskonten- und Abwesenheitslogik. Die exakten weiteren Klassen und Endpunkte werden im Grobplan zugeordnet.

**Akzeptanzkriterien:**

- Projektzeiten von Geschäftsführern können weiterhin erfasst, gespeichert und für die Projektabrechnung verwendet werden.
- Tatsächliche Monatsarbeitszeit bleibt abrufbar und berücksichtigt die bestehenden Pausenregeln.
- Für Geschäftsführer werden im festzulegenden Geltungszeitraum keine Plus-/Minusstunden, Sollvergleiche oder Stundenkontosalden geführt.
- Für Geschäftsführer werden kein Urlaubsanspruch und kein Resturlaub geführt; ein Urlaubseintrag scheitert nicht an fehlendem Urlaubsguthaben.
- Urlaubseinträge bleiben in Teamansichten und Historie verfügbar.
- Urlaub wird auch ohne Zeitkontoversion und positive Sollstunden als sichtbare Abwesenheit dokumentiert; die bestehenden Kopplungen in `createAntrag` und `approveAntrag` verhindern den Geschäftsführerablauf nicht.
- Bestehende Daten und Abschlüsse werden erhalten; andere Mitarbeiter behalten ihr bisheriges Kontenverhalten.
- Backend und Frontends erhalten eine eindeutige Grundlage dafür, wann Kontenwerte nicht anwendbar sind.

**Prüfungen:** Fachliche Tests für Geschäftsführer und reguläre Mitarbeiter, Projektzeiterfassung und Monatsaggregation einschließlich Pausen, Urlaub ohne Guthaben sowie Erhalt von Abwesenheitshistorie und vorhandenen Abschlüssen. Nach Klärung der offenen Fragen kommen Fälle für Statuswechsel, Versionierung und Genehmigung hinzu. Personenbezogene Testdaten sind ausschließlich Dummy-Daten.

### Paket 2: PC-Zeiterfassung und Urlaubsansichten

Betroffen ist `react-pc-frontend`, insbesondere die Monatsansicht der Zeiterfassung sowie die von Geschäftsführern verwendeten Urlaubs- und Abwesenheitsansichten.

**Akzeptanzkriterien:**

- Die Monatsansicht zeigt die tatsächliche Arbeitszeit eines Geschäftsführers weiterhin verständlich an.
- Sollzeiten, Plus-/Minusstunden und Stundenkontosalden einschließlich zugehöriger Auswertungen werden für Geschäftsführer nicht angezeigt.
- Urlaubsanspruch, Resturlaub und Urlaubskontosalden werden für Geschäftsführer nicht angezeigt.
- Urlaub kann weiterhin eingetragen werden; Team-Sichtbarkeit und Rückblick auf frühere Urlaubseinträge bleiben verfügbar.
- Reguläre Mitarbeiter behalten ihre zutreffenden Kontenanzeigen.
- Sichtbare Texte verwenden verständliche Handwerkersprache und die vorhandenen UI-Komponenten.

**Prüfungen:** Geeignete UI-Tests für die Anzeige tatsächlicher Monatsarbeitszeit und das Fehlen der Kontenanzeigen bei Geschäftsführern; Vergleich mit regulären Mitarbeitern. Browserprüfung der Abläufe Zeiterfassung, Monatswechsel, Urlaubseintrag, Teamansicht und Urlaubshistorie. Build und passende Frontend-Tests nach Umsetzung.

### Paket 3: Mobile Zeiterfassung und Auswertung

Betroffen ist `react-zeiterfassung`, insbesondere die mobile Auswertung sowie die dort vorhandenen Zeiterfassungs- und Urlaubsabläufe.

**Akzeptanzkriterien:**

- Geschäftsführer können auf dem Handy weiterhin projektbezogen stempeln.
- Die mobile Auswertung zeigt die tatsächliche Arbeitszeit im gewählten Monat.
- Sollvergleiche, Plus-/Minusstunden und Stundenkontosalden werden für Geschäftsführer nicht angezeigt.
- Vorhandene mobile Urlaubsansichten zeigen für Geschäftsführer keinen Anspruch, Resturlaub oder Urlaubskontosaldo.
- Bestehende mobile Möglichkeiten zur Urlaubserfassung und zum Rückblick bleiben erhalten.
- Andere Mitarbeiter behalten die für sie geltenden Auswertungen.

**Prüfungen:** Geeignete UI-Tests mit Geschäftsführer- und regulären Mitarbeiterdaten; Browserprüfung von Projektstempeln, Monatsauswertung und vorhandenen Urlaubsabläufen in mobilen Bildschirmgrößen. Build und passende Frontend-Tests nach Umsetzung.

## Getroffene Entscheidungen (2026-09-10)

Die zuvor offenen Punkte sind mit dem Auftraggeber geklärt. Sie sind für
Grobplan und Umsetzung verbindlich.

1. **Zeitliche Gültigkeit und Statuswechsel:** Kein Stichtag. Das Kennzeichen
   `Mitarbeiter.istGeschaeftsfuehrer` wirkt als aktueller Zustand auf alle
   Zeiträume, auch rückwirkend: Solange es gesetzt ist, werden für diese Person
   nirgends Plus-/Minusstunden, Sollvergleiche, Stundenkontosalden,
   Urlaubsanspruch oder Resturlaub berechnet oder angezeigt. Es werden keine
   Daten gelöscht und keine Werte rückwirkend überschrieben; wird das
   Kennzeichen entfernt, greift wieder das bisherige Kontenverhalten mit den
   vorhandenen Daten. Bestehende Monatsabschlüsse bleiben unverändert
   gespeichert.
2. **Zusammenspiel bestehender Felder:** `fuehrtZeitkonto` bleibt der Schalter
   "Zeiterfassung an/aus" und bleibt für Geschäftsführer eingeschaltet — sie
   stempeln weiterhin auf Projekte. `istGeschaeftsfuehrer` ist die neue,
   davon unabhängige Regel "führt kein Stundenkonto". Beide Felder bleiben
   getrennt; es entsteht kein neues Berechtigungsmodell.
3. **Arbeitszeit-Voraussetzung:** Für Geschäftsführer ist keine hinterlegte
   Wochenarbeitszeit (`ZeitkontoVersion`) mehr Voraussetzung. Sie können ohne
   Einrichtungsschritt stempeln, und ihr Urlaub wird auch ohne Arbeitszeit und
   ohne positive Sollstunden als sichtbare Abwesenheit dokumentiert.
4. **Urlaubsgenehmigung:** Geschäftsführerurlaub durchläuft kein
   Genehmigungsverfahren. Der Eintrag ist sofort gültig und sichtbar. Ein
   Urlaubsguthaben wird nicht vorausgesetzt und nicht geführt.
5. **API-Abbildung:** Nicht anwendbare Kontenwerte werden nicht als Nullsaldo
   ausgeliefert. Die bestehenden Zeitkonto-Status-Nutzlasten erhalten ein
   zusätzliches Kennzeichen, an dem PC- und Mobile-Frontend erkennen, dass
   für diese Person kein Stunden- und Urlaubskonto existiert; die betroffenen
   Kontenfelder entfallen dann aus der Antwort. Die genaue Feldbenennung und
   Modulzuordnung legt der Grobplan fest.

## Ursprünglich offene Entscheidungen (durch Abschnitt oben ersetzt)

1. **Zeitliche Gültigkeit und Statuswechsel:** Ab welchem Stichtag wirkt das Geschäftsführerkennzeichen auf Konten? Wie werden Wechsel zum Geschäftsführer und zurück mit vorhandenen Versionen und Abschlüssen behandelt? Welche historischen Kontenanzeigen bleiben für frühere Zeiträume zugänglich?
2. **Zusammenspiel bestehender Felder:** Wie wird `istGeschaeftsfuehrer` gegenüber `fuehrtZeitkonto` fachlich ausgewertet und in der Mitarbeiterverwaltung dargestellt, sodass keine widersprüchliche Konfiguration entsteht?
3. **Urlaubsgenehmigung:** Gilt der bestehende Genehmigungsablauf auch für Geschäftsführerurlaub oder ist eine andere Behandlung erforderlich?
4. **API-Abbildung und genaue Modulzuordnung:** Welche bestehenden DTOs und Endpunkte transportieren die Anwendbarkeit der Konten, und welche konkreten Komponenten, Services und Tests müssen angepasst werden?

Diese Entscheidungen sind vor der Umsetzung der jeweils abhängigen Arbeiten zu treffen. Die bereits beschriebenen Anforderungen an tatsächliche Arbeitszeit, fehlende Konten und erhaltene Urlaubsdokumentation gelten unabhängig davon.
