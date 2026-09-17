# Spec: Spracheingabe in der mobilen Zeiterfassung

Datum: 17.09.2026

Issue: [#163](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/163)

Branch: `feature/spracheingabe-zeiterfassung` (Git-Worktree, abgezweigt von `main`)

Status: Grundlage ist ein mit dem Nutzer abgeschlossenes Brainstorming zur
Diktierfunktion und zur Berechtigungs-Einstellungsseite in
`react-zeiterfassung`. Diese Spec überführt die dort getroffenen
Entscheidungen in eine Umsetzungsgrundlage für den Grobplan und erfindet
keine neuen Design-Entscheidungen. Die Auslagerung der Diktierfunktion in
ein wiederverwendbares Frontend-Bauteil (statt vier Kopien) ist vom Nutzer
im Brainstorming ausdrücklich freigegeben und fester Bestandteil dieses
Umfangs, kein optionaler Vorschlag, der erst separat abgenickt werden
müsste.

## Ziel

Handwerker, die die mobile PWA `react-zeiterfassung` auf der Baustelle
nutzen, sollen lange Freitexte diktieren können statt sie eintippen zu
müssen — vor allem im Bautagebuch, aber auch in drei weiteren mehrzeiligen
Freitextfeldern. Vorbild ist "Wispr Flow": Mikrofon-Knopf drücken,
sprechen, ein sauber aufgeräumter, aber inhaltlich unveränderter Text
erscheint im Feld.

Dazu kommt ein zweiter, technisch vorausgesetzter Baustein: eine neue
Einstellungsseite, auf der Mitarbeiter den Status ihrer Berechtigungen
(Benachrichtigungen, Mikrofon, Kamera) sehen und bewusst erteilen können.
Die Diktierfunktion braucht Mikrofonzugriff; ohne eine Stelle, an der eine
einmal abgelehnte Berechtigung erklärt und der Weg zur Korrektur gezeigt
wird, liefe die Funktion für jeden, der versehentlich "Nicht erlauben"
tippt, dauerhaft ins Leere. Im Zuge dessen entfällt die bisherige
ungefragte Abfrage der Benachrichtigungs-Erlaubnis beim Login.

Zielgruppe: Mitarbeiter im Feld, die die mobile App nutzen. Das
PC-Frontend und das Büro sind nicht Adressat dieser Änderung.

## Nicht-Ziele

- `react-pc-frontend` — ausdrücklich nicht Teil dieser Aufgabe, auch nicht
  die spätere Übernahme der Diktierfunktion dorthin.
- Speichern, Verlauf oder Wiedergabe von Sprachaufnahmen.
- Eine Offline-Warteschlange für Aufnahmen. Ohne Netz wird der Knopf
  ausgegraut, es wird nichts zwischengespeichert.
- Eine Oberfläche zum Pflegen des Metallbau-Fachvokabulars — die Begriffe
  stehen fest in der Systemanweisung (Abschnitt 2).
- Die Umstellung des projektweiten `?token=`-Auth-Musters. Es ist bekannt
  suboptimal (Tokens landen in Server-Logs und im Browser-Verlauf), aber
  der neue Endpunkt folgt bewusst dem etablierten Haus-Muster (siehe
  Architektur, Abschnitt 1). Diese Spec dokumentiert das als Restpunkt,
  ohne ihn zu beheben.
- Der iOS-Safe-Area-Layoutfix. Der ist bereits erledigt (separater Branch
  `fix/ios-safe-area-zeiterfassung`, Commit `24942cbf`) und wird hier nicht
  wiederholt oder angefasst.
- Ein neues, gemeinsames Gemini-Client-Modul. Der neue Dienst bleibt
  bewusst eigenständig, wie die bestehenden unabhängigen Gemini-Aufrufe im
  Projekt (u.a. `BeitragKiService`, `GeminiScannerService`,
  `KiHilfeService`, `EmailAiService` — ein gemeinsamer Client ist laut
  Klassenkommentar in `BeitragKiService.java:43-45` eine eigene, bislang
  nicht angegangene Aufgabe).
- Ein Eintrag im Verarbeitungsverzeichnis des Betriebs für Sprachaufnahmen.
  Das ist eine organisatorische Aufgabe außerhalb dieses Codes — siehe
  Abschnitt 6 (Datenschutz).

## Architektur/Ablauf

### 0. Warum Audio direkt an Gemini (Abwägung)

Die Aufnahme entsteht im Browser und geht unverändert an einen neuen,
eigenen Backend-Endpunkt, der sie an Gemini weiterreicht. Ein einziger
Gemini-Aufruf übernimmt Transkription und Aufräumen zugleich. Verworfen
wurden zwei Alternativen: Gerätediktat der Tastatur mit nachgelagertem
KI-Aufräumschritt (das Gerätediktat schreibt wörtlich mit — inklusive
Selbstkorrekturen wie "ähm, nee, warte" — und verliert Fachbegriffe im
Baustellenlärm unwiederbringlich, bevor eine KI überhaupt ansetzen könnte)
und die Web Speech API im Browser (laut caniuse nur "partial support",
nicht standardisiert, in installierten iOS-PWAs unzuverlässig, und schickt
Audio ohnehin an Google — der vermeintliche Datenschutzvorteil entfällt
damit).

Gemini akzeptiert WebM/Opus und M4A direkt — genau das, was
`MediaRecorder` auf Android bzw. iOS erzeugt. Das Backend kodiert nichts
um, sondern reicht die vom Browser gelieferten Bytes unverändert weiter.
Kostenrahmen laut Nutzerangabe: Gemini rechnet Audio mit 32 Tokens/Sekunde
ab, eine Minute Aufnahme sind damit 1.920 Tokens — weniger als allein die
Systemanweisung des bestehenden `BeitragKiService`. Eine Opus-Aufnahme
braucht rund 200 KB pro Minute, ein Bruchteil eines Baustellenfotos.

### 1. Backend: neuer Dienst und Endpunkt

Neuer Service `SpracheingabeService` (Package `service`, keine Logik im
Controller — siehe Package-Regel in `BACKEND_ARCH.md`) nach dem Vorbild
von `BeitragKiService.java`:

- Zwei Konstruktoren wie in `BeitragKiService.java:101-129`: ein
  öffentlicher Produktionskonstruktor, der den `HttpClient` selbst baut,
  und ein paketprivater Testkonstruktor, der ihn injizierbar entgegennimmt.
  Das macht den Gemini-Aufruf mockbar, ohne echte HTTP-Requests in Tests
  (siehe `BeitragKiServiceTest.java:48-51`).
- Die Systemanweisung als eigener `static final String`-Textblock (siehe
  Abschnitt 2), ASCII-Umlaute im Prompt-Text selbst (`"fuer"`, `"aeh"`,
  …) — Projektkonvention, siehe `BeitragKiService.SYSTEM_ANWEISUNG`
  (`BeitragKiService.java:51-86`).
- Klassenkommentar mit Datenschutz-Hinweis, analog zu
  `BeitragKiService.java:33-46`: hier geht es nicht um einen öffentlichen
  Text, sondern um Sprachaufnahmen von Mitarbeitern im Arbeitsverhältnis,
  deshalb der Hinweis, dass Audio ausschließlich im Arbeitsspeicher lebt
  und nach dem Aufruf verworfen wird (siehe Abschnitt 6).

Aufbau des Gemini-Requests nach dem konkreten Vorbild aus
`GeminiScannerService.java:60-130` (der bestehende Dienst, der am
nächsten an "reine Binärdaten plus Anweisung, keine Bildvorverarbeitung"
liegt):

- `systemInstruction` mit einem Text-Part (die Systemanweisung).
- `contents`: genau eine `user`-Runde. Deren einziger Part ist
  `inlineData` (`mimeType`, `data` als Base64 der Aufnahme) — kein
  begleitender Text-Part, die Aufnahme ist der gesamte fachliche Inhalt.
- `generationConfig`: `temperature 0`, `responseMimeType "text/plain"`.
  Kein `responseSchema` — anders als bei `BeitragKiService`, weil die
  Antwort selbst der gesuchte Text ist, kein JSON-Objekt zum Parsen.
- URL-Muster wie in beiden Vorbildern:
  `https://generativelanguage.googleapis.com/v1beta/models/<modell>:generateContent?key=<key>`.

Property-Schlüssel: neuer, eigener Schlüssel `ai.gemini.model.spracheingabe`,
Standardwert `gemini-flash-latest` (ausdrückliche Nutzervorgabe). Das folgt
der bestehenden Namenskonvention `ai.gemini.model.<verwendungszweck>` in
`application.properties:112-125` (`scanner`, `pro`, `dokument-analyse`,
`pdf-extractor`, `email-classification`). Anmerkung zur Einordnung: Der
Kommentar über `ai.ki-hilfe.model` (`application.properties:119`)
beschreibt ebenfalls `gemini-flash-latest`, während der tatsächlich
konfigurierte Wert dort `gemini-3.5-flash-lite` ist
(`application.properties:120`) — die Nutzervorgabe für den neuen Schlüssel
bezieht sich auf die im Kommentar dokumentierte Absicht, nicht auf den
aktuell abweichenden Live-Wert. Beide Schlüssel bleiben unabhängig
konfigurierbar.

API-Schlüssel: `systemSettingsService.getGeminiApiKey()`
(`SystemSettingsService.java:360-361`), wie bei `BeitragKiService`. Fehlt
der Schlüssel, wirft der Dienst einen ehrlichen Fehler statt eines stillen
Ersatzwerts — anders als `GeminiScannerService.generateFilename`, das bei
fehlendem Schlüssel klaglos einen Platzhalter-Dateinamen liefert
(`GeminiScannerService.java:58-60`). Ein erratener Platzhalter-Text im
Bautagebuch wäre fachlich falsch, siehe Fehlerfälle (Abschnitt 5).

Rückgabe: ein schlankes Antwort-Record (z.B.
`dto/SpracheingabeErgebnis.java` mit einem Feld `text`), nach dem Muster
der Records in `dto/Beitraege/` (siehe `BeitragKiAnfrage.java`).

Neuer Controller `SpracheingabeController`, Route-Vorschlag
`POST /api/spracheingabe/transkribieren`. Auth nach dem einfachen
Mobile-Muster aus `PushSubscriptionController.java:23-28`:
`@RequestParam String token` (Pflichtparameter, nicht optional) plus
`mitarbeiterRepository.findByLoginToken(token)`, `401` bei unbekanntem
oder inaktivem Mitarbeiter. Bewusst **nicht** das doppelte Web/Mobile-Muster
aus `ProjektController.java` (`resolveMitarbeiter` mit optionalem Token für
Endpunkte, die sowohl vom PC- als auch vom Mobile-Frontend angesprochen
werden, z.B. der Notiz-Bilder-Upload `ProjektController.java:1550-1556`) —
dieser Endpunkt hat keinen PC-Anteil, er wird ausschließlich von
`react-zeiterfassung` angesprochen.

Die Aufnahme kommt als Multipart (`@RequestParam("audio") MultipartFile
audio`), wie die bestehenden Bild-Upload-Endpunkte. Das bestehende globale
Limit (`spring.servlet.multipart.max-file-size=15GB`,
`application.properties:60-61`) deckt die maximal ~400 KB einer
2-Minuten-Opus-Aufnahme bei weitem ab, hier ist keine
Konfigurationsänderung nötig.

Bekannter Restpunkt (siehe Nicht-Ziele): Wie alle mobilen Endpunkte dieses
Musters landet der Token als Query-Parameter in Server-Logs und
Browser-Verlauf. Das gilt heute schon für `/api/push/*` und die
Notizen-Endpunkte und wird hier nicht neu gelöst, nur fortgeführt.

### 2. Die Systemanweisung

Übernommen und redaktionell unverändert aus dem vom Nutzer gebilligten
Entwurf, als `SYSTEM_ANWEISUNG`-Textblock (ASCII-Umlaute, wie im übrigen
Projekt):

```
Du bekommst eine Sprachaufnahme von einem Handwerker einer Bauschlosserei.
Er diktiert einen Eintrag, oft auf einer lauten Baustelle.

Deine einzige Aufgabe ist, das Gesagte sauber aufzuschreiben.

Gib ausschliesslich den Text zurueck, ohne Vorwort und ohne Kommentar.
Schreibe nur, was gesagt wurde. Erfinde nichts dazu.
Lass Fuellwoerter weg, also aeh, aehm, halt, sozusagen.
Korrigiert sich der Sprecher selbst, schreibe nur die korrigierte Fassung.
Setze Satzzeichen und Gross- und Kleinschreibung nach Sinn.
Ordne den Inhalt nicht um und fasse ihn nicht zusammen.

Das Ergebnis ist reiner Text.
Keine Sternchen, keine Rauten, keine HTML-Tags, keine Markdown-Syntax.
Absaetze trennst du durch eine Leerzeile.
Aufzaehlungen schreibst du mit einem fuehrenden Bindestrich.

Der Sprecher arbeitet im Metallbau. Rechne mit Begriffen wie
Feuerverzinkung, Pulverbeschichtung, VSG, ESG, Schwerlastanker,
Klebeduebel, Konterlattung, Absturzsicherung.
Profile schreibst du als HEB 200, IPE 160, Rohrprofil 40x40x3.
Normen als DIN EN 1090, DIN 18008.

Verstehst du eine Stelle akustisch nicht, schreibe dort [unverstaendlich].
Rate nicht.
```

Kernregeln, die bei einer Verfeinerung während der Umsetzung nicht
verwässert werden dürfen:

- Inhaltlich bleibt exakt das Gesagte stehen. Die KI räumt auf
  (Füllwörter, Selbstkorrekturen, Groß-/Kleinschreibung, Satzzeichen,
  Fachbegriffe), sie strukturiert nicht um, fasst nicht zusammen und
  dichtet nichts hinzu.
- Ausgabe ist reiner Text: kein Markdown, kein HTML, keine
  Sternchen/Rauten. Die Zielfelder sind schlichte `textarea`-Elemente,
  Formatierungssyntax wäre dort nur Zeichensalat. Gleiches Muster wie
  `BeitragKiService.SYSTEM_ANWEISUNG` (Absätze durch Leerzeile,
  Aufzählungen mit führendem Bindestrich).
- `[unverstaendlich]` statt Raten: Ein Bautagebuch kann im Streitfall vor
  Gericht landen — eine ehrliche Lücke ist dort einer erfundenen,
  plausibel klingenden Formulierung vorzuziehen. Diese Regel ist dem
  Nutzer ausdrücklich wichtig und darf nicht zugunsten "hilfreicherer"
  Ausgaben abgeschwächt werden.

Tests für diesen Dienst sollten dem Muster aus `BeitragKiServiceTest.java`
folgen: nicht nur prüfen, dass irgendein Text zurückkommt, sondern gezielt,
welche Regeln in `SYSTEM_ANWEISUNG` stehen (z.B.
`assertThat(SYSTEM_ANWEISUNG).contains("[unverstaendlich]")`,
`.contains("Fuellwoerter")`, `.contains("reiner Text")`), analog zu
`stilregelnStehenInDerSystemanweisung()`
(`BeitragKiServiceTest.java:224-233`).

### 3. Frontend: wiederverwendbare Diktier-Komponente

Eine gemeinsame Komponente in `react-zeiterfassung/src/components/`
(Namensvorschlag `VoiceInputButton.tsx`; genaue Aufteilung
Komponente/Hook siehe Offene Punkte), die an vier Stellen eingesetzt wird
statt viermal kopiert zu werden.

Verantwortlichkeiten der Komponente:

- Für den Nutzer sichtbare Zustandsmaschine: bereit → nimmt auf →
  verarbeitet → fertig/Fehler (Gulf of Evaluation, `FRONTEND_UI.md:29-33`:
  sichtbarer Ladezustand, sichtbares Ergebnis, kein "Klick ins Leere").
- Start/Stop der Aufnahme über `MediaRecorder`; welches `mimeType` der
  Browser tatsächlich liefert (`audio/webm;codecs=opus` auf
  Android/Chrome, `audio/mp4` auf iOS/Safari — beides von Gemini direkt
  akzeptiert, siehe Abschnitt 0), wird unverändert an das Backend
  mitgegeben, damit dieses denselben Wert als `inlineData.mimeType` an
  Gemini weiterreicht.
- Automatischer Stopp nach 2 Minuten mit Hinweis (harte Obergrenze); die
  bis dahin aufgenommenen Sekunden werden trotzdem normal verarbeitet, es
  ist ein Zeitlimit, kein Abbruch.
- Aufnahmen unter 1 Sekunde werden verworfen (Fehlgriff, z.B. versehentliches
  Antippen), ohne einen Request auszulösen — es liegt keine fehlgeschlagene
  Aktion vor, daher greift hier nicht die Toast-Pflicht aus
  `FRONTEND_UI.md:35-55` (siehe auch Offene Punkte zu einer möglichen
  stillen Rückmeldung).
- Kein Netz: Knopf deaktiviert mit erklärendem Hinweis "Spracheingabe
  braucht Internet" (`disabled`-Button mit Begründung statt stummem Grau,
  siehe `FRONTEND_UI.md:25`), Zustand über `navigator.onLine`/das bereits
  vorhandene Online-Signal (`NetworkStatusBadge.tsx`) ermittelt.
- Anhängeverhalten: liefert das Ergebnis immer als Ergänzung, niemals als
  Ersatz. Steht bereits Text im Feld, wird das Diktat mit einer Leerzeile
  angehängt. Das ist die Vorgabe mit der höchsten Priorität aus dem
  Brainstorming (Überschreiben von getipptem Text ist der schlimmste
  denkbare Fehlerfall) und muss unabhängig von der genauen
  Props-Signatur gelten.
- Alle Fehlerfälle (Abschnitt 5) über `useToast()`
  (`components/ui/toast.tsx`), das in allen vier Zielseiten bereits
  eingebunden ist (`ToastProvider` umschließt die App global,
  `main.tsx:54`) — kein `alert()`, kein stiller `catch`.
- Icon-only-Bedienelemente (Mikrofon-Knopf im Ruhezustand, Stopp-Knopf
  während der Aufnahme) bekommen `aria-label` (z.B. "Diktieren",
  "Aufnahme beenden"), Farben/Icons nach Design-System (rose/slate, Lucide
  `Mic`/`MicOff`/`Loader2`).

Als technisches Vorbild für die Mikrofon-Zugriffsschicht dient
`react-zeiterfassung/src/services/cameraStreamService.ts`: ein
modul-globaler `MediaStream`-Cache mit Race-Guard gegen parallele
`acquire`-Aufrufe und explizitem `release`. Der Kommentar dort
(`cameraStreamService.ts:1-14`) hält eine wichtige, für die
Diktierfunktion ebenso relevante iOS-Eigenheit fest: In der als
Homescreen-App installierten PWA speichert iOS Kamera-Berechtigungen
nicht dauerhaft pro Origin, jeder neue `getUserMedia`-Aufruf kann erneut
prompten. Ob und wie die Diktier-Komponente einen vergleichbaren
Cache-Ansatz für das Mikrofon übernimmt, klärt der Grobplan (siehe Offene
Punkte).

### 4. Einsatzorte

Die Komponente ergänzt an folgenden vier Stellen das jeweils vorhandene
Textfeld, ohne dessen bestehende Logik zu ersetzen:

| Seite | Zustand/Setter | Label im UI | Textarea (verifiziert) |
| --- | --- | --- | --- |
| `pages/ProjektNotizenPage.tsx` | `neueNotiz` / `setNeueNotiz` | "Eintrag eingeben..." (Bautagebuch, Hauptfall) | Zeile 500-502 |
| `pages/AnfrageNotizenPage.tsx` | `neueNotiz` / `setNeueNotiz` | "Notiz eingeben..." (Anfrage-Tagebuch, identischer Modal-Aufbau wie oben) | Zeile 508-512 |
| `pages/UrlaubsantragPage.tsx` | `bemerkung` / `setBemerkung` | "Bemerkung (Optional)" | Zeile 452-454 |
| `pages/LieferantReklamationCreatePage.tsx` | `beschreibung` / `setBeschreibung` | "Problembeschreibung" | Zeile 222-224 |

Anmerkung zur Benennung: Das Brainstorming bezeichnet das Urlaubsfeld als
"Grund" — im Code und im UI heißt es tatsächlich `bemerkung` / "Bemerkung
(Optional)". Fachlich dasselbe Feld, nur zur Klarstellung für den
Grobplan verifiziert.

Die Systemanweisung ist bewusst generisch (Metallbau-Diktat, kein
Kontext, welches der vier Felder gerade befüllt wird) — der Endpunkt
bekommt keinen Hinweis, für welches Feld diktiert wird, und die
Prompt-Gestaltung sieht dafür auch keine Fallunterscheidung vor.

### 5. Fehlerfälle

Bewusst aus dem Brainstorming übernommen, jeweils mit `toast.error(...)`
außer beim Fehlgriff-Fall:

- Mikrofon nicht erlaubt → Hinweis mit Verweis auf die neue
  Einstellungsseite (`/einstellungen`).
- Kein Netz → Knopf deaktiviert, kein Request, erklärender Hinweis am
  Button (siehe Abschnitt 3).
- Gemini-Schlüssel nicht hinterlegt → ehrliche Fehlermeldung per Toast,
  kein stiller Fehlschlag und kein Platzhaltertext im Feld.
- Zeitüberschreitung oder Gemini-Fehler → Toast, Aufnahme wird verworfen,
  der bereits getippte Text im Feld bleibt unangetastet (kein
  Teil-Update, kein Leeren des Felds).
- Aufnahme unter 1 Sekunde → verworfen, kein Request, keine
  Fehlermeldung (kein Fehlerfall im eigentlichen Sinn, siehe Abschnitt 3).
- 2-Minuten-Obergrenze erreicht → automatischer Stopp, Hinweis, danach
  normale Verarbeitung der bis dahin aufgenommenen Sekunden.

### 6. Datenschutz

Sprachaufnahmen sind personenbezogene Daten von Mitarbeitern im
Arbeitsverhältnis — eine andere Kategorie als z.B. Belegfotos. Konkrete
Anforderungen an die Umsetzung:

- Die Aufnahme lebt ausschließlich im Arbeitsspeicher: kein
  Zwischenspeichern auf Platte (auch nicht temporär — die Bytes aus
  `MultipartFile` direkt im Speicher verarbeiten, nicht per `transferTo()`
  auf ein Dateisystem schreiben), keine Datenbank-Spalte für Audio oder
  Transkript. Nach dem Gemini-Aufruf werden beide verworfen.
- Weder die Audio-Bytes noch der zurückgegebene Text dürfen in Logs
  landen. Log-Zeilen zu diesem Dienst dürfen nur technische Metadaten
  enthalten (z.B. Dauer, Dateigröße, HTTP-Status), keinen Inhalt — anders
  als z.B. `log.warn("[BeitragKi] Gemini HTTP {} - {}", statusCode,
  antwort.body())` in `BeitragKiService.java:374`, das den vollen
  Antwortkörper mitloggt. Für Sprachaufnahmen ist das nicht akzeptabel,
  weil der Antwortkörper hier das Transkript selbst enthält.
- Ein kurzer Hinweis in der App gehört dazu (z.B. beim ersten Antippen des
  Mikrofon-Knopfs oder dauerhaft klein am Feld), dass die Aufnahme nur zur
  Verarbeitung genutzt und danach nicht gespeichert wird. Exakte
  Platzierung/Formulierung siehe Offene Punkte.
- Außerhalb des Codes (siehe Nicht-Ziele): Die Verarbeitung gehört in die
  Verarbeitungsübersicht des Betriebs. Das ist ein Hinweis an den
  Betreiber dieser Software, keine Implementierungsaufgabe.

### 7. Einstellungsseite `/einstellungen`

Neue Route `/einstellungen` in `react-zeiterfassung/src/App.tsx` (freier
Pfad, verifiziert gegen die bestehende Routenliste `App.tsx:330-354`),
neue Seite (Namensvorschlag `EinstellungenPage.tsx`).

Erreichbar über ein Zahnrad-Symbol (Lucide `Settings`) links neben
`<NetworkStatusBadge>` im Kopfbereich der `DashboardPage`. Verifizierte
Einfügestelle `DashboardPage.tsx:993-994`, dort steht heute:

```tsx
<div className="flex items-center gap-2">
    <NetworkStatusBadge syncStatus={syncStatus} onSync={onSync} />
</div>
```

Inhalt: eine Zeile pro Berechtigung, jeweils Name in Alltagssprache, kurze
Erklärung, Status-Abzeichen, Knopf. Erklärtexte wörtlich aus dem
Brainstorming:

| Berechtigung | Erklärung | Statuswerte |
| --- | --- | --- |
| Benachrichtigungen | "Damit du Termine und Freigaben aufs Handy bekommst" | Erlaubt / Noch nicht gefragt / Blockiert / Gerät kann das nicht |
| Mikrofon | "Damit du Einträge diktieren kannst statt sie zu tippen" | Erlaubt / Noch nicht gefragt / Blockiert / Gerät kann das nicht |
| Kamera | "Für Fotos im Bautagebuch und zum Belege scannen" | Erlaubt / Noch nicht gefragt / Blockiert / Gerät kann das nicht |

Statusermittlung:

- Benachrichtigungen: vollständig vorhandene Logik wiederverwenden, keine
  neue Push-Logik. `NotificationService.getPermissionStatus()`
  (`NotificationService.ts:52-55`, liefert `granted`/`denied`/`default`/`unsupported`)
  plus `NotificationService.isSupported()` (`NotificationService.ts:38-40`)
  für "Gerät kann das nicht".
- Mikrofon/Kamera: `navigator.permissions.query({ name: 'microphone' |
  'camera' })`, wo unterstützt (liefert `granted`/`denied`/`prompt`).
  Zuverlässigkeit je Plattform ist unterschiedlich, siehe Offene Punkte.

Verhalten je Status:

- "Noch nicht gefragt" → Knopf "Erlauben". Bei Benachrichtigungen ruft er
  dieselbe Kette auf wie das bisherige `initializeNotifications` (siehe
  Abschnitt 8). Bei Mikrofon/Kamera löst er einmalig
  `getUserMedia({audio: true})` bzw. die Wiederverwendung von
  `acquireCameraStream`/`releaseCameraStream` aus
  `services/cameraStreamService.ts` aus, nur um den Browser-Prompt zu
  zeigen; der Stream wird danach sofort wieder freigegeben, es wird
  keiner gehalten.
- "Blockiert" → keine erneute Anfrage (kein Browser erlaubt das
  Zurücksetzen einer abgelehnten Berechtigung per Skript), sondern eine
  geräte-/browserspezifische Anleitung. iOS: Weg über Einstellungen →
  Safari, Formulierung im Ton des bestehenden Kamera-Hinweises in
  `ScannerModal.tsx:710-717` ("Kein Zugriff auf die Kamera. Bitte in den
  iOS-Einstellungen unter Safari → Kamera erlauben und die App neu
  starten."). Android: Hinweis auf das Schloss-Symbol in der
  Adressleiste.
- "Gerät kann das nicht" → Zeile bleibt informativ, ohne Knopf.
- "Erlaubt" → Status-Abzeichen, kein Knopf nötig.

### 8. Verhaltensänderung: `initializeNotifications` nicht mehr ungefragt

Verifizierter Ist-Zustand in `react-zeiterfassung/src/App.tsx`:
`initializeNotifications(token)` (Definition `App.tsx:183-217`, Ablauf:
`NotificationService.requestPermission()` → bei Erfolg `storeTokenForSW`,
`subscribeToPush`, `registerPeriodicSync`, `loadAndCheck`, danach ein
5-Minuten-`setInterval` als Fallback-Polling) hat **zwei** Aufrufstellen,
nicht nur die eine, die das Brainstorming beschreibt:

1. `initializeApp` (Aufruf in `App.tsx:171`) — läuft bei jedem App-Start
   mit bereits vorhandener, gespeicherter Anmeldung (Session-
   Wiederherstellung, nicht nur beim allerersten Login).
2. `validateAndStoreToken` (Funktion ab `App.tsx:261`, Aufruf in
   `App.tsx:284`) — läuft beim eigentlichen Login per QR-Code-Scan,
   kommentiert als "Initialize notifications after login". Das ist die
   Stelle, die das Brainstorming mit "aufgerufen direkt nach dem Login"
   meint.

Beide Stellen lösen heute `NotificationService.requestPermission()` aus,
was nur dann tatsächlich einen Browser-Prompt zeigt, wenn der Status noch
`default` ist (bei `granted`/`denied` liefert `requestPermission()`
sofort ohne Prompt zurück, siehe `NotificationService.ts:61-83`). Damit
die Vorgabe "keine ungefragte Erstabfrage mehr" tatsächlich greift, muss
die Umstellung an **beiden** Stellen erfolgen — sonst bliebe der
reflexhafte Prompt über die jeweils andere Stelle weiter bestehen.

Vorgesehene Aufteilung:

- Eine neue, schlanke Variante (z.B.
  `starteNotificationsFallsBereitsErlaubt(token)`) prüft nur
  `NotificationService.getPermissionStatus() === 'granted'` und führt dann
  den bestehenden Rest der Kette aus (`storeTokenForSW`, `subscribeToPush`,
  `registerPeriodicSync`, `loadAndCheck`, 5-Minuten-Intervall) — ruft aber
  **nicht** `requestPermission()` auf, wenn der Status `default` ist.
  Diese Variante ersetzt an beiden bisherigen Aufrufstellen (`App.tsx:171`
  und `App.tsx:284`) den heutigen `initializeNotifications`-Aufruf.
- Der bestehende, prompt-auslösende Ablauf (inklusive
  `requestPermission()`) bleibt als Funktion erhalten, wird aber nur noch
  vom "Erlauben"-Knopf der neuen Einstellungsseite aufgerufen.
- Ist die Berechtigung bereits erteilt, läuft nach Login/App-Start
  weiterhin automatisch die komplette bestehende Kette — das ist eine
  ausdrückliche Nutzervorgabe, kein Nebeneffekt, der verloren gehen darf.

### 9. Tests

Backend: `SpracheingabeServiceTest` nach dem Muster von
`BeitragKiServiceTest.java` — Aufbau des Request-Körpers
(`inlineData`/`mimeType`, `generationConfig`, kein `responseSchema`),
Inhalt der Systemanweisung (siehe Abschnitt 2), Verhalten bei fehlendem
API-Schlüssel, Verhalten bei Gemini-Fehlerstatus. Controller-Test für die
Auth (gültiger/unbekannter/inaktiver Mitarbeiter, analog zu den
Push-Endpunkten). Ausschließlich Dummy-Daten (z.B. `Max Mustermann`) in
Tests, keine echten Mitarbeiterdaten.

Frontend: Vitest für die neue Komponente (Zustandsübergänge,
Anhängen-statt-Ersetzen-Verhalten, Offline-Deaktivierung,
2-Minuten-Grenze, Verwurf unter 1 Sekunde, Fehler-Toasts) und für
`EinstellungenPage` (Statusanzeige je Berechtigung, Knopf-Verhalten je
Status). Bestehende Tests der vier Wirtsseiten (`ProjektNotizenPage.test.tsx`,
`UrlaubsantragPage.test.tsx`, …) dürfen durch die Integration nicht
brechen.

## Betroffene Bereiche

**Backend** (`src/main/java/org/example/kalkulationsprogramm/`):

- Neu: `service/SpracheingabeService.java`, `controller/SpracheingabeController.java`,
  Antwort-DTO (z.B. `dto/SpracheingabeErgebnis.java`).
- Geändert: `src/main/resources/application.properties` (neuer Schlüssel
  `ai.gemini.model.spracheingabe`).
- Keine Migration erwartet (kein neues Datenmodell, nichts wird
  persistiert).

**Frontend Mobile** (`react-zeiterfassung/`, ausschließlich —
`react-pc-frontend` ist nicht betroffen):

- Neu: gemeinsame Diktier-Komponente (Vorschlag
  `src/components/VoiceInputButton.tsx`), neue Seite
  `src/pages/EinstellungenPage.tsx`.
- Geändert: `src/pages/ProjektNotizenPage.tsx`, `src/pages/AnfrageNotizenPage.tsx`,
  `src/pages/UrlaubsantragPage.tsx`, `src/pages/LieferantReklamationCreatePage.tsx`
  (jeweils Einbau der Komponente am bestehenden Textfeld),
  `src/pages/DashboardPage.tsx` (Zahnrad-Icon im Header), `src/App.tsx`
  (neue Route `/einstellungen`, Entkopplung von `initializeNotifications`,
  siehe Abschnitt 8).

## Offene Punkte für den Grobplan

- **Statusermittlung für Mikrofon/Kamera je Plattform**:
  `navigator.permissions.query({name:'microphone'|'camera'})` ist in
  Chrome/Edge/Android verlässlich, die Unterstützung in Safari/iOS ist
  neuer und je Version/Modus unterschiedlich zuverlässig. Verifiziert
  liegt im Projekt bereits eine dokumentierte Eigenheit für die Kamera vor
  (`cameraStreamService.ts:1-14`: iOS speichert Berechtigungen in der
  installierten PWA nicht dauerhaft pro Origin). Der Grobplan muss
  festlegen, wie die Einstellungsseite auf Geräten/Browsern ohne
  verlässliche Permissions-API-Antwort verfährt.
- **Lebensdauer des Mikrofon-Streams**: Soll die Diktier-Komponente den
  Mikrofon-Stream nach dem Vorbild von `cameraStreamService.ts` über
  mehrere Aufnahmen hinweg cachen (um wiederholte iOS-Prompts zu
  vermeiden), oder je Aufnahme neu per `getUserMedia` anfordern und
  danach sofort freigeben? Das Brainstorming trifft dazu keine Aussage.
- **Serverseitige Begrenzung von Dauer/Größe**: Die 2-Minuten-Grenze ist im
  Brainstorming eine clientseitige Verhaltensregel. Ob der neue Endpunkt
  zusätzlich serverseitig eine eigene, deutlich kleinere Obergrenze
  durchsetzt (Verteidigung gegen einen manipulierten Client — das
  bestehende globale Multipart-Limit von 15 GB wäre dafür viel zu
  großzügig), legt der Grobplan fest.
- **Exakte Props-API der Diktier-Komponente**: Ob sie als einzelne
  Komponente mit `value`/`onAppend`-Callback, als Hook plus
  Präsentationskomponente oder anders geschnitten wird, ist
  Implementierungsdetail des Grobplans — die in Abschnitt 3 beschriebenen
  Verhaltensanforderungen (Zustandsmaschine, Anhängen statt Ersetzen,
  Offline-Sperre) gelten unabhängig von der genauen Aufteilung.
- **Platzierung/Formulierung des Datenschutz-Hinweises** in der App
  (Abschnitt 6) — dass er da sein muss, ist entschieden; ob als einmaliger
  Hinweis beim ersten Antippen oder dauerhaft klein am Feld, entscheidet
  der Grobplan zusammen mit dem Design-Skill.
- **Rückmeldung bei Aufnahmen unter 1 Sekunde**: Das Brainstorming legt den
  Verwurf fest, sagt aber nicht, ob der Nutzer dafür irgendeine
  (nicht-fehlerhafte) Rückmeldung sieht oder der Knopf kommentarlos
  zurückspringt.
