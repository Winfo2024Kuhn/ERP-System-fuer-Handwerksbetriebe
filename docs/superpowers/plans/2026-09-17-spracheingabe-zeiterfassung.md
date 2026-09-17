# Plan: Spracheingabe in der mobilen Zeiterfassung

Issue: #163 — https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/issues/163
Feature-Branch: `feature/spracheingabe-zeiterfassung` (existiert bereits als Worktree unter `/Users/marvinkuhn/dev/wt/spracheingabe`)
Spec: `docs/superpowers/specs/2026-09-17-spracheingabe-zeiterfassung.md` (vollständig lesen; die sechs „Offenen Punkte" am Ende sind **entschieden**, siehe unten)
Kontext-Log: `docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung-log.md` (legt der Orchestrator an)

**Status dieses Dokuments:** Grobplan mit 13 Tasks. Abschnitts-/Runden-Einteilung,
Task-Branches und Worktree-Pfade fehlen bewusst — die trägt der Abschnitts-Agent
nach. Für ihn sind die Blöcke „Interfaces → Consumes" und der Abschnitt
**Kopplungs-Hinweise für den Abschnitts-Schnitt** am Ende maßgeblich.

---

## Entschiedene Punkte (verbindlich, nicht neu diskutieren)

Der Orchestrator hat die sechs offenen Punkte der Spec entschieden:

1. **Mikrofon-Stream:** pro Aufnahme neu per `getUserMedia`, danach **sofort** alle
   Tracks stoppen. Ausdrücklich **kein** Cache nach Vorbild `cameraStreamService.ts`.
   Begründung gehört als Kommentar in den Code: Solange ein Mikrofon-Stream offen
   ist, zeigt iOS einen orangen Punkt in der Statusleiste — der bliebe nach dem
   Diktieren stehen und sähe aus, als lausche die App weiter. Der Preis (evtl. ein
   zusätzlicher Berechtigungsdialog auf iOS) ist bewusst in Kauf genommen.
2. **Serverseitige Grenze:** eigene Grenze am Endpunkt. **10 MB** Obergrenze,
   Whitelist der Inhaltstypen (`audio/webm`, `audio/ogg`, `audio/mp4`, `audio/m4a`,
   `audio/aac`, `audio/wav`, `audio/mpeg`). Das globale Multipart-Limit von 15 GB ist
   als Verteidigung wertlos. Die 2-Minuten-Regel im Browser ist Bequemlichkeit, keine
   Sicherheit — der Server darf sich nicht darauf verlassen.
3. **Unklarer Berechtigungsstatus:** Fehlt `navigator.permissions.query`, wirft es
   oder liefert es einen unbrauchbaren Wert → Anzeige **„Noch nicht gefragt"**, Knopf
   trotzdem anbieten. Niemals einen Status behaupten, der nicht ermittelt wurde.
4. **Datenschutz-Hinweis:** einmalig beim **ersten** Antippen des Mikrofons (Merker
   lokal pro Gerät), plus eine dauerhafte Zeile auf der Einstellungsseite. **Nicht**
   als Dauertext an jedem Feld.
5. **Aufnahme unter 1 Sekunde:** kurzer neutraler Hinweis („Zu kurz — Knopf drücken
   und sprechen"), **kein** Fehler-Toast. Ein Fehlgriff ist kein Fehler.
6. **Props-Zuschnitt:** Ermessen des Grobplans → entschieden in Task 7, Begründung
   dort.

---

## Zwei Abweichungen von der Spec (begründet, verbindlich)

### A. Kein Multipart — die Aufnahme kommt als roher Request-Body

Die Spec (Abschnitt 1) schlägt `@RequestParam("audio") MultipartFile audio` vor. Das
steht im direkten Widerspruch zur DSGVO-Vorgabe derselben Spec (Abschnitt 6: „kein
Zwischenspeichern auf Platte, auch nicht temporär"):

Spring Boot 3.2.5 (`pom.xml:8`) hat `spring.servlet.multipart.file-size-threshold`
standardmäßig auf `0B`. Der Schlüssel ist in `src/main/resources/application.properties`
**nicht** gesetzt, und im gesamten `src/main/java` gibt es **kein**
`MultipartConfigElement`-Bean (beides geprüft). Mit Schwelle 0 schreibt Tomcat
**jeden** Multipart-Teil sofort in sein Temp-Verzeichnis auf Platte — die
Sprachaufnahme läge also auf der Festplatte, bevor der Controller sie überhaupt sieht.
Den Schwellwert global hochzudrehen ist keine Option: er gälte dann für alle Uploads
inklusive der 15-GB-Grenze.

Deshalb: Der Client schickt den `Blob` als rohen Body, der Inhaltstyp steht im
`Content-Type`-Header, der Server liest ihn **begrenzt** in den Arbeitsspeicher.
Nebeneffekt: die 10-MB-Grenze lässt sich beim Lesen durchsetzen statt erst danach.

Findet der umsetzende Agent einen Grund, warum das nicht trägt: **melden**, nicht
still auf Multipart zurückfallen.

### B. Das Notification-Bootstrap zieht aus `App.tsx` in ein eigenes Modul

Die Spec beschreibt zwei Funktionen in `App.tsx`. Die Einstellungsseite muss den
prompt-auslösenden Ablauf aber ebenfalls aufrufen können, und eine komponenten-lokale
Funktion in `App.tsx` ist von dort nicht erreichbar. Deshalb wandert die Kette nach
`react-zeiterfassung/src/services/notificationBootstrap.ts` (Task 6). `App.tsx`
(Task 9) und `EinstellungenPage.tsx` (Task 8) rufen sie beide von dort auf. Das
5-Minuten-Intervall zieht als Modul-Zustand mit um; `notificationIntervalRef` in
`App.tsx:91` entfällt.

---

## Global Constraints

Diese Regeln gelten für **jeden** Task. Der Auftragstext eines Coding-Agenten ist nur
eine Zusammenfassung — **maßgeblich ist der `### Task N`-Block hier**. Weicht der
Auftrag vom Plan ab, gilt der Plan; die Abweichung melden.

### Pflichtlektüre vor dem ersten Edit

| Du änderst … | Vorher per `Read` laden |
| --- | --- |
| `*.java` (auch Tests, auch Config) | `docs/agent instructions/docs/BACKEND_ARCH.md` |
| `*.tsx`/`*.ts` unter `react-zeiterfassung/` | `docs/agent instructions/docs/FRONTEND_UI.md` **und** Design-Skill (siehe unten) |
| Testdateien (`*Test.java`, `*.test.tsx`, `*.spec.ts`) | `docs/agent instructions/docs/TESTING_SECURITY.md` |
| alle | `.claude/skills/loese-problem/references/kriterien.md` |

Ein PreToolUse-Hook (`.claude/hooks/check-doc-read.ps1`) blockt Edits mit Exit 2,
solange das passende Doc in der Session nicht gelesen wurde. Nicht umgehen — lesen und
den Edit danach erneut versuchen.

**Design-Skill bei Frontend-Arbeit (hook-erzwungen, `DESIGN-SKILL-GUARD`):**
`handwerkerprogramm-design` aufrufen — **ohne** Namespace-Präfix.

*(Korrigiert am 18.09.2026. Hier stand zuvor, der unscoped Name scheitere mit
„Unknown skill" und nur `wt/<worktree-name>:handwerkerprogramm-design`
funktioniere. Das ist falsch: Alle vier Coding-Agenten aus Abschnitt 1 haben
unabhängig voneinander berichtet, dass der Aufruf ohne Präfix direkt
funktioniert; der Orchestrator hat es in derselben Sitzung ebenso erlebt. Die
alte Angabe hätte jeden Agenten auf einen Umweg geschickt.)*

**Nicht verfügbar:** Der Skill `superpowers:test-driven-development`, den die
Agenten-Rollendefinition nennt, ist in diesem Projekt nicht registriert
(„Unknown skill", auch ohne Präfix). Von allen vier Agenten aus Abschnitt 1
gemeldet. Bis das behoben ist: manuell nach TDD-Disziplin vorgehen — erst den
Test schreiben, den roten Fehlschlag mit der erwarteten Begründung
verifizieren, dann implementieren.

**TypeScript: keine Parameter-Properties, keine Enums, keine Namespaces.**
`erasableSyntaxOnly: true` steht in **allen** tsconfigs **beider** Frontends
(`react-zeiterfassung/tsconfig.app.json:23` und `tsconfig.node.json:21`,
ebenso im PC-Frontend). Damit bricht `tsc -b` bei allem ab, was nicht durch
reines Löschen der Typen verschwindet. Praktisch heißt das:

    // TS1294 — uebersetzt NICHT
    class Fehler extends Error {
        constructor(message: string, readonly status?: number) { super(message) }
    }

    // so stattdessen
    class Fehler extends Error {
        status?: number
        constructor(message: string, status?: number) {
            super(message)
            this.status = status
        }
    }

*(Am 18.09.2026 ergänzt. Der Task-4-Block dieses Plans zeigte die erste,
nicht übersetzbare Form. Die Tests laufen trotzdem grün durch — Vitest
transpiliert ohne Typprüfung —, erst `npm run build` bricht ab. Wer den
Build als Fail-Fast-Prüfung überspringt, merkt es also nicht.)*

**graphify steht in keinem Worktree zur Verfügung.** `graphify`, `graphify.cmd`
und `.graphify-venv/` sind gitignored (`.gitignore:99,102,103`) und existieren
nur im Haupt-Checkout. Ein per `git worktree add` angelegtes Arbeitsverzeichnis
bekommt sie nicht mit. Task-Agenten können `./graphify` also weder zum Suchen
noch für `update .` nutzen — deshalb nennt dieser Plan zu jeder Referenz Datei
und Zeile, damit gezielt gelesen statt gesucht werden kann. `./graphify
update .` läuft am Ende einmal zentral im Haupt-Checkout.

**graphify:** vor der ersten Suche im Code `./graphify query "…"` laufen lassen (Hook
in `.claude/settings.json` blockt sonst den ersten Roh-Read). Dieser Plan liefert die
Recherche bereits mit — graphify nur noch für gezielte Einzelfragen nutzen, nicht für
einen allgemeinen Überblick. `./graphify update .` **einmal am Ende** der Aufgabe, nicht
nach jedem Edit.

### Projektregeln

- **Backend:** Constructor Injection (Lombok `@RequiredArgsConstructor` erlaubt),
  keine Field-Injection. Controller enthalten keine Fachlogik. Keine neue Flyway-
  Migration in diesem Vorhaben — es wird nichts persistiert.
- **Frontend-Farben:** ausschließlich `rose-*`/`slate-*` (plus die im Bestand schon
  verwendeten Semantikfarben `emerald`/`amber`/`red` für Statusabzeichen). **Niemals**
  `blue`/`indigo`/`violet`, auch nicht „nur vorübergehend". Icons ausschließlich
  Lucide React.
- **Toast-Pflicht:** Jede fehlgeschlagene Aktion zeigt `toast.error(...)` aus
  `react-zeiterfassung/src/components/ui/toast.tsx` (`useToast()`). Kein `alert()`,
  kein stiller `catch`, kein Fehler, der nur in der Konsole landet. Einzige Ausnahme in
  diesem Vorhaben: der <1-Sekunden-Fehlgriff (Punkt 5 oben) — das ist keine
  fehlgeschlagene Aktion.
- **Keine nativen Dialoge:** kein `alert`/`confirm`/`prompt`. Betriebssystem-
  Berechtigungsdialoge (`getUserMedia`, `Notification.requestPermission`) sind echte
  Systemfunktionen und ausdrücklich erlaubt.
- **Icon-only-Knöpfe** brauchen immer ein `aria-label`. Deaktivierte Knöpfe erklären
  sichtbar, **warum** sie deaktiviert sind.
- **Sprache:** deutsche Handwerker-Sprache in jeder sichtbaren Zeichenkette. Keine
  buchhalterischen oder technischen Begriffe („Transkription", „Token", „Permission"
  gehören nicht in die UI).
- **DSGVO:** In Tests, Fixtures und E2E-Stubs ausschließlich Dummy-Daten
  („Max Mustermann", „Musterbetrieb GmbH"). Weder Audio-Bytes noch der zurückgegebene
  Text dürfen in Logs landen — Log-Zeilen dieses Vorhabens enthalten nur technische
  Metadaten (Bytes, Inhaltstyp, HTTP-Status).
- **Secrets** niemals in Code oder Commit. Vor dem Commit `git diff --staged` ansehen.

### Test-Gates je Coding-Agent (nicht die volle Suite)

| Bereich | Befehl |
| --- | --- |
| Backend | `./mvnw -B test -Dtest=<MeineTestklasse>` |
| Frontend Unit | `npx vitest run src/<meine-datei>.test.tsx` |
| Frontend Lint | `npm run lint` (im Ordner `react-zeiterfassung`) — wird am häufigsten gerissen |
| Frontend Build | `npm run build` (im Ordner `react-zeiterfassung`) |
| Playwright | `E2E_PORT=<eigener Port> npx playwright test e2e/<meine-spec>.spec.ts` |

Exit-Codes nicht durch Pipes verlieren (`./mvnw test | tail` liefert den Code von
`tail`): `set -o pipefail` oder erst in eine Datei umleiten.

### ⚠️ Build-Artefakte gehören NICHT in den Task-Commit

`npm run build` in `react-zeiterfassung` schreibt nach
`src/main/resources/static/zeiterfassung/` (siehe `vite.config.ts:56-59`,
`outDir: '../src/main/resources/static/zeiterfassung'`, `emptyOutDir: true`) — mit
gehashten Bundle-Namen und umgeschriebener Script-Zeile in `index.html`. Bauen mehrere
Tasks parallel, kollidieren sie beim Abschnitts-Merge genau dort.

**Regel:** `npm run build` ist reine Fail-Fast-Prüfung. Die erzeugten Artefakte vor dem
Commit verwerfen:

```bash
git checkout -- src/main/resources/static/zeiterfassung/ 2>/dev/null || true
git clean -fd src/main/resources/static/zeiterfassung/
```

Der echte Build passiert **einmal koordiniert am Ende** durch den Orchestrator.

### Baseline (unveränderter Feature-Branch, gemessen vom Orchestrator)

| Gate | Ergebnis |
| --- | --- |
| Backend `./mvnw test` | 554 Testklassen, 3126 Tests, 0 Failures, 0 Errors, 17 Skipped, Exit 0 |
| Frontend `npm test` (react-zeiterfassung) | 22 Testdateien, 217 Tests, alle grün, Exit 0 |
| Lint `npm run lint` (react-zeiterfassung) | 0 Fehler, Exit 0 |

**Abnahmeregel:** alles grün. **Jeder** Fehler ist neu und gehört dem Task, der ihn
erzeugt hat. Keine fremden Tests „reparieren".

---

## Der Endpunkt-Vertrag (gilt für Task 2 und Task 4 gemeinsam)

```
POST /api/spracheingabe/transkribieren?token=<mitarbeiter-login-token>
Content-Type: audio/webm;codecs=opus   (oder audio/mp4 u.a. aus der Whitelist)
Body:         rohe Audio-Bytes (kein Multipart, kein JSON)

200 { "text": "Heute Gelaender montiert. ..." }
400 { "success": false, "message": "..." }   ungueltiger/leerer Inhaltstyp, leerer Body
401 (leerer Body)                            Token unbekannt oder Mitarbeiter inaktiv
413 { "success": false, "message": "..." }   Aufnahme groesser als 10 MB
502 { "success": false, "message": "..." }   kein Gemini-Schluessel / Gemini-Fehler
```

---

## Tasks

### Task 1 — Backend: `SpracheingabeService` + Antwort-DTO + Modell-Property

- **Files:**
  - `src/main/java/org/example/kalkulationsprogramm/service/SpracheingabeService.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/dto/SpracheingabeErgebnis.java` (neu)
  - `src/main/resources/application.properties` (geändert)
  - `src/test/java/org/example/kalkulationsprogramm/service/SpracheingabeServiceTest.java` (neu)
- **Vorbild:**
  - Doppel-Konstruktor (Produktion baut `HttpClient` selbst, paketprivater Test-
    Konstruktor nimmt ihn entgegen): `BeitragKiService.java:101-129`.
  - Gemini-Request mit `systemInstruction` + `inlineData` + `generationConfig`
    (`temperature 0`, `responseMimeType "text/plain"`, **kein** `responseSchema`):
    `GeminiScannerService.java:64-97`. **Achtung:** `GeminiScannerService` nutzt
    camelCase (`systemInstruction`, `inlineData`, `mimeType`), `BeitragKiService`
    snake_case (`system_instruction`, `inline_data`, `mime_type`). Gemini akzeptiert
    beides — hier **camelCase**, wie im näher liegenden Vorbild.
  - Senden/Lesen der Antwort inkl. Timeout-Behandlung: `BeitragKiService.sendeUndLies()`
    (`BeitragKiService.java:347-390`) — aber **ohne** das Mitloggen des Antwortkörpers
    aus Zeile 374 (der Körper ist hier das Transkript, siehe Spec Abschnitt 6).
  - Größen- und MIME-Prüfung mit Konstanten-Whitelist:
    `KassenbuchungService.validiereDatei()` (`KassenbuchungService.java:316-329`),
    Konstanten `KassenbuchungService.java:73-77`.
  - API-Schlüssel zur Laufzeit: `systemSettingsService.getGeminiApiKey()`
    (`SystemSettingsService.java:360-361`), verwendet in `BeitragKiService.java:260-264`.
  - HttpClient/HttpResponse mocken: `BelegKiKostenkontoServiceTest.java:198-203`.
  - Regel-Assertions auf die Systemanweisung:
    `BeitragKiServiceTest.stilregelnStehenInDerSystemanweisung()` (`:224-233`).
- **Interfaces:**
  - **Produces:**
    ```java
    // dto/SpracheingabeErgebnis.java
    public record SpracheingabeErgebnis(String text) {}

    // service/SpracheingabeService.java
    @Slf4j @Service
    public class SpracheingabeService {
        static final String SYSTEM_ANWEISUNG = """...""";
        static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;
        static final Set<String> ERLAUBTE_INHALTSTYPEN = Set.of(
                "audio/webm", "audio/ogg", "audio/mp4", "audio/m4a",
                "audio/aac", "audio/wav", "audio/mpeg");

        @Autowired
        public SpracheingabeService(SystemSettingsService systemSettingsService,
                                    ObjectMapper objectMapper,
                                    @Value("${ai.gemini.model.spracheingabe:gemini-flash-latest}") String modell);

        /** Test-Konstruktor mit injizierbarem HttpClient. */
        SpracheingabeService(SystemSettingsService systemSettingsService,
                             ObjectMapper objectMapper,
                             HttpClient httpClient,
                             String modell);

        /** Liest den Strom begrenzt in den Speicher, prueft, ruft Gemini, liefert den Text. */
        public SpracheingabeErgebnis transkribiere(InputStream audioStrom, String inhaltstyp);

        /** Sichtbar fuer den Test: baut den Gemini-Koerper. */
        ObjectNode baueKoerper(byte[] audio, String inhaltstyp);

        /** Sichtbar fuer den Test: "audio/webm;codecs=opus" -> "audio/webm". */
        static String normalisiereInhaltstyp(String rohwert);

        /** 413 statt 400: eigene Unterklasse, damit der Controller sie trennen kann. */
        public static class AufnahmeZuGross extends IllegalArgumentException { ... }
    }
    ```
  - **Consumes:** nichts.
- **Steps:**
  - [ ] `dto/SpracheingabeErgebnis.java` als Record mit dem einen Feld `text` anlegen,
        Javadoc nach Muster von `dto/Beitraege/BeitragKiAnfrage.java:8-14`.
  - [ ] `service/SpracheingabeService.java` anlegen. Klassenkommentar mit
        Datenschutz-Absatz analog `BeitragKiService.java:33-46`, aber mit dem hier
        gültigen Inhalt: Sprachaufnahmen von Mitarbeitern im Arbeitsverhältnis, Audio
        und Transkript leben ausschließlich im Arbeitsspeicher, nichts wird gespeichert,
        nichts davon geht in ein Log.
  - [ ] `SYSTEM_ANWEISUNG` als `static final String` Textblock anlegen — **wörtlich**
        dieser Text (ASCII-Umlaute, Projektkonvention wie
        `BeitragKiService.java:51-86`), ohne redaktionelle Änderungen:
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
  - [ ] `normalisiereInhaltstyp(String)`: `null` → `""`; sonst bis zum ersten `;`
        abschneiden, `trim()`, `toLowerCase(Locale.ROOT)`. **Kein** Regex mit
        verschachtelten Wiederholungen (ReDoS-Regel, `TESTING_SECURITY.md`).
  - [ ] Begrenztes Lesen: private Methode `leseHoechstens(InputStream, int max)`, die
        in einen `ByteArrayOutputStream` liest und abbricht, **sobald** `max + 1` Bytes
        gelesen wurden → `AufnahmeZuGross`. Nicht erst alles puffern und danach die
        Größe prüfen — sonst kann ein manipulierter Client beliebig viel Speicher
        belegen. `IOException` in `IllegalStateException("Die Aufnahme konnte nicht
        gelesen werden.")` wickeln.
  - [ ] `transkribiere(InputStream, String)`: Inhaltstyp normalisieren → nicht in
        `ERLAUBTE_INHALTSTYPEN` → `IllegalArgumentException` mit Text „Dieses
        Audioformat wird nicht unterstuetzt."; Bytes begrenzt lesen; leer →
        `IllegalArgumentException("Die Aufnahme ist leer.")`; API-Schlüssel über
        `systemSettingsService.getGeminiApiKey()` holen, `null`/blank →
        `IllegalStateException("Kein Gemini-Schluessel hinterlegt.")` (**kein**
        Platzhalter-Text, anders als `GeminiScannerService.java:58-60`); dann
        `baueKoerper(...)` senden und den Text zurückgeben.
  - [ ] `baueKoerper(byte[] audio, String inhaltstyp)`: `systemInstruction.parts[0].text`
        = `SYSTEM_ANWEISUNG`; `contents` = **genau eine** Runde `role: "user"` mit
        **genau einem** Part `inlineData` (`mimeType` = normalisierter Typ, `data` =
        `Base64.getEncoder().encodeToString(audio)`) — kein begleitender Text-Part;
        `generationConfig` = `temperature 0.0`, `responseMimeType "text/plain"`, **kein**
        `responseSchema`.
  - [ ] Senden nach `BeitragKiService.java:347-372`: URL
        `https://generativelanguage.googleapis.com/v1beta/models/<modell>:generateContent?key=<key>`,
        `READ_TIMEOUT = Duration.ofSeconds(90)`, `connectTimeout` 15 s. Bei `statusCode >= 400`:
        `log.warn("[Spracheingabe] Gemini HTTP {} ({} Bytes, {})", status, audio.length, inhaltstyp)`
        — **ohne** `antwort.body()` — und `IllegalStateException("Die Spracherkennung hat
        mit einem Fehler geantwortet.")`. Antwort lesen:
        `candidates[0].content.parts[0].text`, `trim()`, in `SpracheingabeErgebnis` packen.
  - [ ] `application.properties`: im Block „GEMINI Scanner & PDF" (nach Zeile 116,
        direkt bei den anderen `ai.gemini.model.*`-Schlüsseln) ergänzen:
        ```
        # Spracheingabe der mobilen Zeiterfassung (Diktat -> Text, ein Aufruf)
        ai.gemini.model.spracheingabe=gemini-flash-latest
        ```
  - [ ] `SpracheingabeServiceTest` schreiben (JUnit 5, Mockito, AssertJ; Aufbau wie
        `BeitragKiServiceTest.java:35-51`), mindestens:
        - `systemanweisungEnthaeltDieKernregeln()` — `contains("[unverstaendlich]")`,
          `contains("Fuellwoerter")`, `contains("reiner Text")`,
          `contains("Erfinde nichts dazu")`, `contains("HEB 200")`.
        - `koerperHatGenauEinenInlineDataPartUndKeinenText()` — `contents` hat Größe 1,
          `parts` hat Größe 1, `parts[0].inlineData.mimeType` == `"audio/webm"`,
          `.data` == erwarteter Base64.
        - `generationConfigHatTemperaturNullUndKeinSchema()` —
          `generationConfig.temperature == 0`, `responseMimeType == "text/plain"`,
          `generationConfig.has("responseSchema")` ist `false`.
        - `codecZusatzWirdAbgeschnitten()` — `normalisiereInhaltstyp("audio/webm;codecs=opus")`
          == `"audio/webm"`, `normalisiereInhaltstyp(null)` == `""`.
        - `fremderInhaltstypWirdAbgewiesen()` — `"image/png"` und `"application/pdf"`
          → `IllegalArgumentException`.
        - `zuGrosseAufnahmeWirdAbgewiesen()` — `MAX_AUDIO_BYTES + 1` Bytes →
          `AufnahmeZuGross`.
        - `ohneApiSchluesselFliegtEinEhrlicherFehler()` — `getGeminiApiKey()` liefert
          `""` → `IllegalStateException`, und der HttpClient wurde **nie** aufgerufen.
        - `geminiFehlerAntwortLandetNichtInDerFehlermeldung()` — HTTP 500 mit Körper
          `{"error":"Geheimes Transkript von Max Mustermann"}` → Exception-Text enthält
          `"Geheimes Transkript"` **nicht**.
        - `antwortTextKommtGetrimmtZurueck()` — Gemini-Antwort-JSON mit
          `" Gelaender montiert.\n"` → Ergebnis `"Gelaender montiert."`.
  - [ ] `./mvnw -B test -Dtest=SpracheingabeServiceTest` grün.

---

### Task 2 — Backend: `SpracheingabeController` + Freischaltung für die PWA

- **Files:**
  - `src/main/java/org/example/kalkulationsprogramm/controller/SpracheingabeController.java` (neu)
  - `src/main/java/org/example/kalkulationsprogramm/config/SecurityConfig.java` (geändert)
  - `src/main/java/org/example/kalkulationsprogramm/config/ZeiterfassungSecurityFilter.java` (geändert)
  - `src/test/java/org/example/kalkulationsprogramm/controller/SpracheingabeControllerTest.java` (neu)
  - `src/test/java/org/example/kalkulationsprogramm/config/ZeiterfassungFilterChainMatcherTest.java` (geändert)
- **Vorbild:**
  - Token-Auth im Mobile-Muster: `PushSubscriptionController.java:23-37`
    (`@RequestParam String token`, `mitarbeiterRepository.findByLoginToken(token)`,
    `401` bei `null` oder `!Boolean.TRUE.equals(getAktiv())`).
  - `@ExceptionHandler`-Aufteilung auf 400/502: `BeitragKiController.java:43-55`.
  - 413-Antwort mit `Map.of("success", false, "message", …)`:
    `AnalyticsSnapshotIngressController.java:63-68`.
  - Controller-Test (`@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)`,
    `@MockBean MitarbeiterRepository`, Dummy „Max Mustermann"):
    `PushSubscriptionControllerTest.java:31-56`.
- **Wichtig (steht so nicht in der Spec, verifiziert):** Ohne Eintrag in
  `SecurityConfig.ZEITERFASSUNG_PATHS` landet der neue Pfad in der `apiFilterChain`
  (`SecurityConfig.java:195ff`, Session-Login + CSRF) und antwortet der PWA mit
  401/403. Zusätzlich filtert `ZeiterfassungSecurityFilter` externe (nicht-lokale,
  nicht-Tailscale) IPs gegen `ALLOWED_PATHS` (`ZeiterfassungSecurityFilter.java:41-57`).
  **Beide** Listen brauchen den Pfad.
- **Interfaces:**
  - **Produces:** der Endpunkt-Vertrag oben.
    ```java
    @RestController
    @RequestMapping("/api/spracheingabe")
    @RequiredArgsConstructor
    public class SpracheingabeController {
        @PostMapping("/transkribieren")
        public ResponseEntity<?> transkribieren(
                @RequestParam String token,
                @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String inhaltstyp,
                HttpServletRequest anfrage) throws IOException;
    }
    ```
  - **Consumes:** `SpracheingabeService.transkribiere(InputStream, String)` und
    `SpracheingabeErgebnis` aus Task 1.
- **Steps:**
  - [ ] `SpracheingabeController` anlegen. Klassen-Javadoc: der Endpunkt wird
        ausschließlich von `react-zeiterfassung` angesprochen (kein PC-Anteil), deshalb
        bewusst das einfache `?token=`-Muster aus `PushSubscriptionController` statt des
        doppelten Web/Mobile-Musters aus `ProjektController.resolveMitarbeiter`. Den
        bekannten Restpunkt aus der Spec (Token landet als Query-Parameter in
        Server-Logs und Browser-Verlauf, gilt heute schon für `/api/push/*`) als
        Kommentar festhalten.
  - [ ] Handler: Mitarbeiter über `mitarbeiterRepository.findByLoginToken(token)`
        auflösen; `null` oder `!Boolean.TRUE.equals(mitarbeiter.getAktiv())` →
        `ResponseEntity.status(401).build()`. Sonst
        `spracheingabeService.transkribiere(anfrage.getInputStream(), inhaltstyp)`
        zurückgeben. **Keine** weitere Logik im Controller.
  - [ ] Drei `@ExceptionHandler` in derselben Klasse:
        `AufnahmeZuGross` → `413` + `{"success":false,"message":"Die Aufnahme ist zu lang. Bitte kuerzer diktieren."}`;
        `IllegalArgumentException` → `400` + `ex.getMessage()`;
        `IllegalStateException` → `502` + `{"success":false,"message":"Die Spracherkennung ist gerade nicht erreichbar."}`,
        dazu `log.warn("[Spracheingabe] Transkription fehlgeschlagen: {}", ex.getMessage())`
        — ohne Inhalt. (Spring wählt `AufnahmeZuGross` vor `IllegalArgumentException`,
        weil spezifischer.)
  - [ ] `SecurityConfig.java`: in `ZEITERFASSUNG_PATHS` (Array beginnt bei Zeile 114,
        letzter Eintrag `"/api/buchhaltung/mobile/**"` in Zeile 134) `"/api/spracheingabe/**"`
        ergänzen, mit Kommentar-Zeile darüber im Stil der Nachbareinträge
        („Diktat der mobilen Zeiterfassung. Auth im Controller ueber Mitarbeiter-Token.").
  - [ ] `ZeiterfassungSecurityFilter.java`: `"/api/spracheingabe"` in `ALLOWED_PATHS`
        (Zeile 41-57) ergänzen, sonst scheitert das Diktat für externe IPs.
  - [ ] `ZeiterfassungFilterChainMatcherTest.java`: in
        `mobileKernpfadeSindErreichbar()` (Zeile 33-43) die Zeile
        `assertThat(ohneLoginErreichbar("/api/spracheingabe/transkribieren")).isTrue();`
        ergänzen.
  - [ ] `SpracheingabeControllerTest` (`@WebMvcTest(SpracheingabeController.class)`,
        `@MockBean SpracheingabeService`, `@MockBean MitarbeiterRepository`), Fälle:
        - gültiger aktiver Mitarbeiter („Max Mustermann", `loginToken` `"valid-token"`)
          → `200`, `jsonPath("$.text")` == erwarteter Text, Service genau einmal
          aufgerufen.
        - unbekannter Token → `401`, `verifyNoInteractions(spracheingabeService)`.
        - inaktiver Mitarbeiter (`setAktiv(false)`) → `401`,
          `verifyNoInteractions(spracheingabeService)`.
        - Service wirft `AufnahmeZuGross` → `413`.
        - Service wirft `IllegalArgumentException` → `400`.
        - Service wirft `IllegalStateException` → `502` und der Antworttext enthält
          keine internen Details.
        - Sicherheits-Pflichtcheckliste: `token` mit `'; DROP TABLE x; --` und mit
          `<script>alert(1)</script>` → `401` (kein 500), Repository wird mit genau
          diesem String befragt (parametrisierte Query, kein Concat).
        - Request ohne `Content-Type`-Header → Service bekommt `null` übergeben und der
          `IllegalArgumentException`-Pfad greift → `400`.
        Requests mit `post("/api/spracheingabe/transkribieren").param("token", …)
        .contentType("audio/webm").content(new byte[]{1,2,3})` bauen.
  - [ ] `./mvnw -B test -Dtest=SpracheingabeControllerTest,ZeiterfassungFilterChainMatcherTest` grün.

---

### Task 3 — Frontend: `audioRecorderService.ts` (Mikrofon + MediaRecorder)

- **Files:**
  - `react-zeiterfassung/src/services/audioRecorderService.ts` (neu)
  - `react-zeiterfassung/src/services/audioRecorderService.test.ts` (neu)
- **Vorbild:** `react-zeiterfassung/src/services/cameraStreamService.ts` — als
  **Gegenbeispiel**. Der dortige Modul-Cache (`cachedStream`, `pendingAcquire`) wird
  hier ausdrücklich **nicht** übernommen (Entscheidung 1). Der Kopfkommentar dort
  (`cameraStreamService.ts:1-14`) ist das Stilvorbild für den erklärenden Kommentar.
  Teststil: `cameraStreamService.test.ts:1-45` (Fake-Stream mit `getTracks()`,
  `vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })`).
- **Interfaces:**
  - **Produces:**
    ```ts
    export interface AufnahmeSitzung {
        /** Der vom Browser tatsaechlich gewaehlte Inhaltstyp, z.B. "audio/webm;codecs=opus". */
        readonly mimeType: string
        /** Date.now() beim Start — Grundlage fuer die 1s- und 2min-Regel. */
        readonly gestartetAm: number
        /** Beendet die Aufnahme, gibt das Mikrofon SOFORT frei, liefert die Aufnahme. */
        stoppe(): Promise<Blob>
        /** Bricht ab, verwirft die Daten, gibt das Mikrofon SOFORT frei. */
        brichAb(): void
    }

    /** Der Nutzer hat den Mikrofonzugriff abgelehnt oder er ist gesperrt. */
    export class MikrofonNichtErlaubtError extends Error {}

    export function mikrofonWirdUnterstuetzt(): boolean
    export async function starteAufnahme(): Promise<AufnahmeSitzung>
    ```
  - **Consumes:** nichts.
- **Steps:**
  - [ ] Datei anlegen mit Kopfkommentar, der die Entscheidung festhält: kein
        Stream-Cache. Wortlaut sinngemäß: *„Anders als `cameraStreamService.ts` wird der
        Mikrofon-Stream bewusst NICHT ueber mehrere Aufnahmen hinweg gehalten. Solange
        ein Mikrofon-Stream offen ist, zeigt iOS einen orangen Punkt in der
        Statusleiste. Der bliebe nach dem Diktieren stehen und saehe aus, als lausche
        die App weiter. Dafuer nehmen wir in Kauf, dass iOS in der installierten PWA je
        Aufnahme erneut nach der Erlaubnis fragen kann."*
  - [ ] `mikrofonWirdUnterstuetzt()`: `true`, wenn `navigator.mediaDevices?.getUserMedia`
        **und** `typeof MediaRecorder !== 'undefined'` vorhanden sind.
  - [ ] Private `waehleMimeType()`: erste unterstützte Variante aus
        `['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/aac']` per
        `MediaRecorder.isTypeSupported` — existiert die Funktion nicht (iOS/Safari),
        `''` zurückgeben, damit der Browser selbst wählt.
  - [ ] `starteAufnahme()`: `navigator.mediaDevices.getUserMedia({ audio: true })`;
        wirft der Aufruf mit `name === 'NotAllowedError'` oder
        `'PermissionDeniedError'` → in `MikrofonNichtErlaubtError` umwandeln und
        werfen, alles andere unverändert weiterwerfen. Danach `new MediaRecorder(stream, mimeType ? { mimeType } : undefined)`,
        `ondataavailable` sammelt die Chunks in einem lokalen Array,
        `recorder.start()`. `mimeType` der Sitzung = `recorder.mimeType || gewaehlterTyp || 'audio/webm'`.
  - [ ] `stoppe()`: Promise, die auf `recorder.onstop` wartet, dann
        `new Blob(chunks, { type: sitzung.mimeType })` liefert. **Vor** dem Auflösen
        der Promise `stream.getTracks().forEach(t => t.stop())` aufrufen (in
        `try/catch`, wie `cameraStreamService.releaseCameraStream()`), und die Chunks
        leeren. Zweiter Aufruf von `stoppe()` liefert dieselbe Promise, startet nichts neu.
  - [ ] `brichAb()`: Recorder stoppen (falls `state !== 'inactive'`), Chunks verwerfen,
        Tracks stoppen. Darf mehrfach aufrufbar sein, ohne zu werfen.
  - [ ] Test `audioRecorderService.test.ts` mit Fake-`MediaRecorder`
        (`vi.stubGlobal('MediaRecorder', FakeRecorder)` inkl. statischem
        `isTypeSupported`) und Fake-Stream (`getTracks()` liefert Tracks mit
        `stop()`, das `readyState` auf `'ended'` setzt):
        - `stoppe()` liefert einen Blob mit den gesammelten Chunks und dem gemeldeten
          `mimeType`.
        - **Kernprüfung:** nach `stoppe()` ist jeder Track `'ended'` — das Mikrofon ist
          frei (iOS-Punkt weg).
        - Zwei Aufnahmen hintereinander rufen `getUserMedia` **zweimal** auf (kein
          Cache — bewusst anders als `cameraStreamService`).
        - `brichAb()` stoppt die Tracks und liefert keinen Blob.
        - `getUserMedia` lehnt mit `NotAllowedError` ab → `MikrofonNichtErlaubtError`.
        - `mikrofonWirdUnterstuetzt()` ist `false`, wenn `MediaRecorder` fehlt.
  - [ ] `npx vitest run src/services/audioRecorderService.test.ts`, `npm run lint`,
        `npm run build` (Artefakte danach verwerfen).

---

### Task 4 — Frontend: `spracheingabeService.ts` (Aufruf des Endpunkts)

- **Files:**
  - `react-zeiterfassung/src/services/spracheingabeService.ts` (neu)
  - `react-zeiterfassung/src/services/spracheingabeService.test.ts` (neu)
- **Vorbild:** Token aus `localStorage.getItem('zeiterfassung_token')` und
  `?token=`-Anhängen wie `LieferantReklamationCreatePage.tsx:109` bzw. — sauberer, mit
  `URL`/`searchParams` — `BelegScannerPage.tsx:241-243`. Fehlerbehandlung/Statusprüfung
  wie `BelegScannerPage.tsx:247-250`.
- **Interfaces:**
  - **Produces:**
    ```ts
    /** Fehler mit einer Meldung, die so im Toast stehen kann. */
    export class SpracheingabeFehler extends Error {
        constructor(message: string, readonly status?: number)
    }

    /** Schickt die Aufnahme an das Backend und liefert den aufgeraeumten Text. */
    export async function transkribiere(
        aufnahme: Blob,
        token: string,
        signal?: AbortSignal,
    ): Promise<string>
    ```
  - **Consumes:** den Endpunkt-Vertrag (Task 2). Kann parallel zu Task 2 geschrieben
    werden, **muss** aber im selben Abschnitt liegen.
- **Steps:**
  - [ ] URL bauen:
        `` const url = `/api/spracheingabe/transkribieren?token=${encodeURIComponent(token)}` ``
        (`encodeURIComponent` ist Pflicht laut `FRONTEND_UI.md`).
  - [ ] `fetch(url, { method: 'POST', headers: { 'Content-Type': aufnahme.type || 'audio/webm' }, body: aufnahme, signal })`.
        Der Blob geht roh in den Body — **kein** `FormData` (Begründung: Abweichung A
        oben, als Kommentar in die Datei).
  - [ ] Statusbehandlung, jeweils `SpracheingabeFehler` mit fertigem deutschem Text:
        - `401` → „Die Anmeldung ist abgelaufen. Bitte den QR-Code neu scannen."
        - `413` → „Die Aufnahme ist zu lang. Bitte kuerzer diktieren."
        - `400` / `415` → „Dieses Aufnahmeformat versteht das Programm nicht."
        - `502`/sonstige `!res.ok` → „Das Diktat konnte nicht umgewandelt werden. Bitte
          noch einmal versuchen." (Server-`message` nur verwenden, wenn vorhanden und
          nicht leer.)
        - `AbortError` (Abbruch/Zeitüberschreitung) → „Das Diktat hat zu lange
          gedauert. Bitte noch einmal versuchen."
        - Netzwerkfehler (`TypeError` aus `fetch`) → „Keine Verbindung. Spracheingabe
          braucht Internet."
  - [ ] Erfolgsfall: `await res.json()`, `text` als String zurückgeben; fehlt `text`
        oder ist leer → `SpracheingabeFehler("Es wurde nichts verstanden. Bitte noch einmal diktieren.")`.
  - [ ] **Kein** `console.log` des Textes und **kein** `console.log` der Bytes (DSGVO).
  - [ ] Test `spracheingabeService.test.ts` mit `vi.stubGlobal('fetch', vi.fn())`:
        - Erfolg → Text kommt zurück; die aufgerufene URL enthält den kodierten Token;
          `body` ist der übergebene Blob; der `Content-Type`-Header entspricht
          `aufnahme.type`.
        - Token mit Sonderzeichen (`'a b&c'`) wird kodiert (`a%20b%26c`).
        - Je ein Test für `401`, `413`, `400`, `502` → passende Meldung und `status`.
        - Antwort `{"text": ""}` → Fehler statt leerem Rückgabewert.
        - `fetch` wirft `TypeError` → Offline-Meldung.
  - [ ] `npx vitest run src/services/spracheingabeService.test.ts`, `npm run lint`,
        `npm run build` (Artefakte verwerfen).

---

### Task 5 — Frontend: `permissionStatusService.ts` (Berechtigungen lesen und anfragen)

- **Files:**
  - `react-zeiterfassung/src/services/permissionStatusService.ts` (neu)
  - `react-zeiterfassung/src/services/permissionStatusService.test.ts` (neu)
- **Vorbild:** Defensives `navigator.permissions.query` in `try/catch` inklusive
  Feature-Prüfung: `NotificationService.registerPeriodicSync()`
  (`NotificationService.ts:187-208`). Stream sofort wieder freigeben:
  `cameraStreamService.releaseCameraStream()` (`cameraStreamService.ts:50-58`).
  Teststil: `NotificationService.test.ts:1-45` (`vi.stubGlobal('navigator', …)`).
- **Interfaces:**
  - **Produces:**
    ```ts
    /** 'nicht-gefragt' ist auch die ehrliche Antwort, wenn der Status NICHT ermittelbar war. */
    export type BerechtigungsStatus = 'erlaubt' | 'nicht-gefragt' | 'blockiert' | 'nicht-verfuegbar'

    export function leseBenachrichtigungsStatus(): BerechtigungsStatus
    export async function leseMikrofonStatus(): Promise<BerechtigungsStatus>
    export async function leseKameraStatus(): Promise<BerechtigungsStatus>
    /** Loest genau einmal den System-Dialog aus und gibt das Mikrofon sofort wieder frei. */
    export async function frageMikrofonAn(): Promise<void>
    /** Dito fuer die Kamera, ueber den bestehenden cameraStreamService. */
    export async function frageKameraAn(): Promise<void>
    ```
  - **Consumes:** `NotificationService` (Bestand), `cameraStreamService` (Bestand).
- **Steps:**
  - [ ] `leseBenachrichtigungsStatus()`: `NotificationService.isSupported()`
        (`NotificationService.ts:38-40`) `=== false` → `'nicht-verfuegbar'`. Sonst
        `NotificationService.getPermissionStatus()` (`:52-55`) abbilden:
        `granted → 'erlaubt'`, `denied → 'blockiert'`, `default → 'nicht-gefragt'`,
        `unsupported → 'nicht-verfuegbar'`.
  - [ ] Private `leseGeraeteStatus(name: 'microphone' | 'camera')`: gibt es
        `navigator.mediaDevices?.getUserMedia` nicht → `'nicht-verfuegbar'`. Gibt es
        `navigator.permissions?.query` nicht → `'nicht-gefragt'`. Sonst
        `await navigator.permissions.query({ name: name as PermissionName })` in
        `try/catch`; `granted → 'erlaubt'`, `denied → 'blockiert'`,
        `prompt → 'nicht-gefragt'`; **jeder** andere Wert, jede Ausnahme und jedes
        `undefined` → `'nicht-gefragt'`. Kommentar dazu (Entscheidung 3): *„Safari/iOS
        beantwortet die Abfrage je nach Version unterschiedlich zuverlaessig. Lieber
        ehrlich 'Noch nicht gefragt' anzeigen und den Knopf trotzdem anbieten, als einen
        Status zu behaupten, den wir nicht ermitteln konnten."*
  - [ ] `frageMikrofonAn()`: `const stream = await navigator.mediaDevices.getUserMedia({ audio: true })`,
        danach **sofort** `stream.getTracks().forEach(t => { try { t.stop() } catch { /* ignore */ } })`.
        Es wird kein Stream gehalten. Fehler unverändert weiterwerfen — die Seite
        toastet.
  - [ ] `frageKameraAn()`: `await acquireCameraStream({ video: true })`, danach
        `releaseCameraStream()` in `finally` — den bestehenden Dienst wiederverwenden,
        keine zweite Kamera-Logik bauen.
  - [ ] Test `permissionStatusService.test.ts`:
        - `permissions.query` liefert `granted`/`denied`/`prompt` → `'erlaubt'`/`'blockiert'`/`'nicht-gefragt'`.
        - `navigator.permissions` fehlt → `'nicht-gefragt'` (nicht `'nicht-verfuegbar'`).
        - `permissions.query` wirft → `'nicht-gefragt'`.
        - `permissions.query` liefert `{ state: 'irgendwas' }` → `'nicht-gefragt'`.
        - `navigator.mediaDevices` fehlt → `'nicht-verfuegbar'`.
        - `frageMikrofonAn()` stoppt alle Tracks (Kernprüfung).
        - Benachrichtigungen: alle vier Abbildungen inkl. `'unsupported'`.
  - [ ] `npx vitest run src/services/permissionStatusService.test.ts`, `npm run lint`,
        `npm run build` (Artefakte verwerfen).

---

### Task 6 — Frontend: `notificationBootstrap.ts` (Kette aus `App.tsx` herauslösen)

- **Files:**
  - `react-zeiterfassung/src/services/notificationBootstrap.ts` (neu)
  - `react-zeiterfassung/src/services/notificationBootstrap.test.ts` (neu)
- **Vorbild:** der heutige Ablauf `initializeNotifications` in
  `react-zeiterfassung/src/App.tsx:183-217` — **1:1 übernehmen**, nur an einer Stelle
  aufgetrennt. Modul-globaler Zustand nach dem Muster von `cameraStreamService.ts:16-17`.
- **Wichtig:** Dieser Task **ändert `App.tsx` nicht**. Das macht Task 9. Solange Task 9
  nicht gelaufen ist, existiert das Modul ungenutzt parallel zur alten Funktion — ein
  lauffähiger Zwischenstand.
- **Interfaces:**
  - **Produces:**
    ```ts
    /**
     * Startet die komplette Benachrichtigungs-Kette NUR, wenn die Erlaubnis
     * bereits erteilt ist. Fragt NIE von sich aus nach. Liefert true, wenn
     * die Kette gelaufen ist.
     */
    export async function starteBenachrichtigungenFallsErlaubt(token: string): Promise<boolean>

    /**
     * Loest den System-Dialog aus (nur vom "Erlauben"-Knopf der Einstellungsseite)
     * und startet bei Zustimmung dieselbe Kette. Liefert true bei Zustimmung.
     */
    export async function frageBenachrichtigungenAn(token: string): Promise<boolean>

    /** Beendet das 5-Minuten-Polling (beim Abmelden). */
    export function stoppeBenachrichtigungsIntervall(): void
    ```
  - **Consumes:** `NotificationService` (Bestand).
- **Steps:**
  - [ ] Private `starteKette(token)` mit exakt dem Rest aus `App.tsx:186-212`, in
        dieser Reihenfolge: `await NotificationService.storeTokenForSW(token)` →
        `await NotificationService.subscribeToPush(token)` →
        `await NotificationService.registerPeriodicSync()` →
        `NotificationService.loadAndCheck(token)` → vorheriges Intervall löschen und
        ein neues `window.setInterval` alle 5 Minuten setzen, das den **aktuellen**
        Token aus `localStorage.getItem('zeiterfassung_token')` liest (wie
        `App.tsx:208-212`, damit ein Token-Wechsel nicht ins Leere läuft).
        Intervall-Id in einer Modul-Variablen `let intervallId: number | null = null`.
  - [ ] `starteBenachrichtigungenFallsErlaubt(token)`: wenn
        `NotificationService.getPermissionStatus() !== 'granted'` → **sofort** `false`
        zurückgeben, **ohne** `requestPermission()`. Sonst `starteKette` und `true`.
        Kommentar: *„Kein ungefragter Prompt mehr beim App-Start und beim QR-Login
        (Issue #163). Erlaubt der Nutzer die Benachrichtigungen spaeter ueber
        /einstellungen, laeuft ab dem naechsten Start wieder alles automatisch."*
  - [ ] `frageBenachrichtigungenAn(token)`: `await NotificationService.requestPermission()`
        (`NotificationService.ts:61-83`); bei `true` `starteKette` und `true`, sonst `false`.
  - [ ] `stoppeBenachrichtigungsIntervall()`: Intervall löschen, Variable auf `null`.
        Mehrfach aufrufbar.
  - [ ] Test `notificationBootstrap.test.ts` mit `vi.mock('./NotificationService')`
        und `vi.useFakeTimers()`:
        - Status `'default'` → `starteBenachrichtigungenFallsErlaubt` liefert `false`,
          und `requestPermission` wurde **nicht** aufgerufen (Kernprüfung der
          Verhaltensänderung).
        - Status `'denied'` → ebenso `false`, kein `requestPermission`.
        - Status `'granted'` → `storeTokenForSW`, `subscribeToPush`,
          `registerPeriodicSync`, `loadAndCheck` je genau einmal aufgerufen, Rückgabe `true`.
        - Nach `vi.advanceTimersByTime(5 * 60 * 1000)` läuft `loadAndCheck` erneut;
          nach `stoppeBenachrichtigungsIntervall()` nicht mehr.
        - `frageBenachrichtigungenAn` mit `requestPermission → false` startet die Kette
          nicht; mit `true` startet sie sie.
        - Zweimaliges Starten hinterlässt nur **ein** Intervall.
  - [ ] `npx vitest run src/services/notificationBootstrap.test.ts`, `npm run lint`,
        `npm run build` (Artefakte verwerfen).

---

### Task 7 — Frontend: Diktier-Komponente `VoiceInputButton.tsx`

- **Files:**
  - `react-zeiterfassung/src/components/VoiceInputButton.tsx` (neu)
  - `react-zeiterfassung/src/components/VoiceInputButton.test.tsx` (neu)
- **Vorbild:** Online-/Offline-Erkennung per `navigator.onLine` + `online`/`offline`-
  Listener: `NetworkStatusBadge.tsx:11-24`. Lade-/Fertig-Zustand am Knopf mit
  `Loader2 … animate-spin` und `disabled`: `ProjektNotizenPage.tsx:524-536`. Formulierung
  des Berechtigungs-Hinweises im Ton von `ScannerModal.tsx:713-715`. Toast-Nutzung:
  `useToast()` aus `components/ui/toast.tsx:15-19`.
- **Props-Entscheidung (offener Punkt 6 der Spec):** eine **einzelne Komponente** mit
  `wert` + `onErgebnis(neuerWert)`, kein separater Hook, kein reiner
  `onTranskript(text)`-Callback.
  *Begründung:* Die wichtigste Anforderung des Brainstormings ist „anhängen, niemals
  ersetzen". Bekämen die Wirtsseiten nur den rohen Text, müsste jede der vier Seiten
  die Anhänge-Regel selbst umsetzen — genau die vierfache Kopie, die dieses Vorhaben
  vermeiden soll, und der schlimmste denkbare Fehlerfall (überschriebener getippter
  Text) wäre viermal möglich. Mit `wert` + `onErgebnis` liegt die Regel an **einer**
  Stelle, und die Wirtsseite schreibt nur `onErgebnis={setNeueNotiz}` — der bereits
  vorhandene Setter, falsch machen kann man dabei nichts. Ein Hook-Split wäre erst
  nötig, wenn eine Seite eine eigene Darstellung bräuchte; das tut keine der vier.
- **Interfaces:**
  - **Produces:**
    ```tsx
    export interface VoiceInputButtonProps {
        /** Aktueller Inhalt des Feldes. Wird nur gelesen, nie ersetzt. */
        wert: string
        /** Bekommt den KOMPLETTEN neuen Feldinhalt (alt + Leerzeile + Diktat). */
        onErgebnis: (neuerWert: string) => void
        /** Fuer die aria-labels, z.B. "Eintrag", "Bemerkung". Default: "Text". */
        feldName?: string
        /** Hart sperren, z.B. waehrend die Wirtsseite speichert. */
        disabled?: boolean
        /** Zusaetzliche Klassen fuer die Positionierung im Wirtsformular. */
        className?: string
    }
    export default function VoiceInputButton(props: VoiceInputButtonProps)
    ```
  - **Consumes:** Task 3 (`starteAufnahme`, `AufnahmeSitzung`,
    `MikrofonNichtErlaubtError`, `mikrofonWirdUnterstuetzt`), Task 4 (`transkribiere`,
    `SpracheingabeFehler`).
- **Steps:**
  - [ ] Zustand `type Zustand = 'bereit' | 'nimmt-auf' | 'verarbeitet'` plus
        `hinweis: string | null` für die Statuszeile.
  - [ ] Render: `<div className={...flex items-center gap-2...}>` mit
        (a) rundem Icon-Knopf und (b) `<p className="text-xs text-slate-500" role="status">`
        für Statuszeile/Hinweis.
        - `bereit`: Lucide `Mic`, `aria-label={\`${feldName} diktieren\`}`,
          Klassen `border-rose-300 text-rose-700 hover:bg-rose-50` (Sekundär-Knopf laut
          `FRONTEND_UI.md`), Mindestgröße `min-h-11 min-w-11` (Touch).
        - `nimmt-auf`: Lucide `Square` (Stopp), gefüllt `bg-rose-600 text-white`,
          `aria-label="Aufnahme beenden"`, dazu ein `motion-safe:animate-pulse` Punkt und
          die laufende Dauer als `mm:ss` in der Statuszeile.
        - `verarbeitet`: Lucide `Loader2 … animate-spin`, Knopf `disabled`,
          Statuszeile „Wird aufgeschrieben…".
  - [ ] Offline-Sperre: `isOnline` wie in `NetworkStatusBadge.tsx:11-24`. Ist die App
        offline oder liefert `mikrofonWirdUnterstuetzt()` `false`, ist der Knopf
        `disabled` **und** die Statuszeile nennt den Grund („Spracheingabe braucht
        Internet." bzw. „Dieses Geraet kann nicht diktieren."). Kein stummes Grau.
  - [ ] Datenschutz-Hinweis (Entscheidung 4): Beim **ersten** Antippen (localStorage-
        Merker `zeiterfassung_spracheingabe_hinweis`, Wert `'1'`) startet **noch keine**
        Aufnahme. Stattdessen erscheint ein komponenteneigenes, modales Hinweisfeld mit
        dem Text „Deine Aufnahme wird nur zum Aufschreiben verschickt und danach sofort
        geloescht. Gespeichert wird nichts." und den Knöpfen „Verstanden" (setzt den
        Merker und startet die Aufnahme) und „Abbrechen".
        **Bewusst nicht** `useConfirm` aus `components/ui/confirm-dialog.tsx`: die
        Komponente wäre damit auf einen `ConfirmProvider` angewiesen, den z.B.
        `UrlaubsantragPage.test.tsx:12` nicht rendert — sie soll in jedem Formular ohne
        Zusatz-Provider funktionieren. Markup und Klassen trotzdem an
        `confirm-dialog.tsx:58-67` anlehnen (gleiche Rundungen, `role="dialog"`,
        `aria-modal="true"`, `min-h-12`-Knöpfe, rose-Primärknopf), damit es sich nicht
        fremd anfühlt. Kein nativer Dialog.
  - [ ] Aufnahme starten: `starteAufnahme()`; bei `MikrofonNichtErlaubtError`
        `toast.error('Kein Zugriff auf das Mikrofon. Unter "Einstellungen" in der App kannst du nachsehen, wie du es erlaubst.')`
        und zurück auf `bereit`. Jeder andere Fehler → `toast.error('Die Aufnahme konnte nicht gestartet werden.')`.
  - [ ] 2-Minuten-Grenze: beim Start ein `window.setTimeout` über 120 000 ms, das
        `stoppeUndSende()` auslöst und `toast.info('Zwei Minuten sind voll. Das Gesagte wird jetzt aufgeschrieben.')`
        zeigt — Abbruch ist es keiner, das bereits Aufgenommene wird normal verarbeitet.
        Timer im Cleanup (`useEffect`-Rückgabe und beim manuellen Stopp) löschen.
  - [ ] `stoppeUndSende()`: `const aufnahme = await sitzung.stoppe()`. Dauer =
        `Date.now() - sitzung.gestartetAm`. Unter 1000 ms (Entscheidung 5): Aufnahme
        verwerfen, **kein** Request, **kein** Toast — stattdessen `hinweis` auf
        „Zu kurz — Knopf druecken und sprechen" setzen und nach ~4 s wieder leeren.
  - [ ] Sonst `zustand = 'verarbeitet'`, Token aus
        `localStorage.getItem('zeiterfassung_token')` (fehlt er →
        `toast.error('Nicht angemeldet. Bitte den QR-Code neu scannen.')`), dann
        `const text = await transkribiere(aufnahme, token)`.
        Bei `SpracheingabeFehler` (und jedem anderen Fehler) `toast.error(fehler.message)` —
        das Feld bleibt **unverändert**, kein Teil-Update, kein Leeren.
  - [ ] Anhängen statt Ersetzen: `const alt = wert.trimEnd(); onErgebnis(alt ? \`${alt}\n\n${text}\` : text)`.
        Leerer/whitespace-Wert → nur das Diktat. Zustand zurück auf `bereit`,
        `hinweis` auf „Fertig." (kurz sichtbar).
  - [ ] Aufräumen beim Unmount: laufende Aufnahme per `sitzung.brichAb()` beenden
        (sonst bliebe das Mikrofon offen), Timer löschen, laufenden `fetch` abbrechen.
  - [ ] Test `VoiceInputButton.test.tsx` mit `vi.mock('../services/audioRecorderService')`
        und `vi.mock('../services/spracheingabeService')`, gerendert in `<ToastProvider>`:
        - Zustandsfolge: Klick → Knopf heißt „Aufnahme beenden"; nach Stopp erscheint
          „Wird aufgeschrieben…"; danach wieder „… diktieren".
        - **Anhängen:** `wert="Alter Text"` → `onErgebnis` bekommt
          `'Alter Text\n\nNeuer Text'`. Mit `wert=""` → nur `'Neuer Text'`.
          Mit `wert="Alter Text\n\n"` → genau **eine** Leerzeile dazwischen.
        - Offline (`navigator.onLine = false`) → Knopf `disabled`, Text „Spracheingabe
          braucht Internet." sichtbar, `starteAufnahme` wurde nie aufgerufen.
        - Erster Klick zeigt den Datenschutz-Hinweis und startet noch keine Aufnahme;
          nach „Verstanden" läuft sie; beim zweiten Mount (Merker gesetzt) erscheint
          kein Hinweis mehr.
        - Unter 1 Sekunde: `transkribiere` wurde **nicht** aufgerufen, es gibt **keinen**
          `role="alert"`-Toast, aber der Text „Zu kurz" ist sichtbar.
        - 2 Minuten mit `vi.useFakeTimers()`: nach 120 000 ms stoppt die Aufnahme von
          selbst und `transkribiere` läuft.
        - `MikrofonNichtErlaubtError` → Toast mit Verweis auf die Einstellungen; das
          Feld bleibt unverändert (`onErgebnis` nie aufgerufen).
        - `transkribiere` wirft → `role="alert"`-Toast, `onErgebnis` nie aufgerufen.
  - [ ] `npx vitest run src/components/VoiceInputButton.test.tsx`, `npm run lint`,
        `npm run build` (Artefakte verwerfen).

---

### Task 8 — Frontend: Einstellungsseite `EinstellungenPage.tsx`

- **Files:**
  - `react-zeiterfassung/src/pages/EinstellungenPage.tsx` (neu)
  - `react-zeiterfassung/src/pages/EinstellungenPage.test.tsx` (neu)
  - `react-zeiterfassung/e2e/einstellungen-berechtigungen.spec.ts` (neu)
- **Vorbild:** Seitenkopf mit Zurück-Pfeil (`ArrowLeft`, `navigate('/')`) und
  `safe-area-top`: `AbwesenheitenPage.tsx:148-163`. Karten-/Zeilenoptik
  (`bg-white rounded-2xl p-4 border border-slate-200 shadow-sm`):
  `LieferantReklamationCreatePage.tsx:216-228`. Status-Abzeichen-Stil (Farbklassen je
  Status als Config-Objekt): `AbwesenheitenPage.tsx:32-37`. E2E-Grundgerüst:
  `e2e/zeitkonto-status.spec.ts:1-36` mit dem Fixture aus `e2e/hilfen/test.ts`.
- **Interfaces:**
  - **Produces:** `export default function EinstellungenPage()` — keine Props, die Seite
    holt den Token selbst aus `localStorage`.
  - **Consumes:** Task 5 (`permissionStatusService`), Task 6
    (`frageBenachrichtigungenAn`).
- **Steps:**
  - [ ] Seite mit Kopfzeile „Einstellungen" und Zurück-Pfeil nach `/` anlegen
        (Muster `AbwesenheitenPage.tsx:148-163`, aber ohne Sync-Knopf).
  - [ ] Drei Zeilen, je als eigene Karte, in dieser Reihenfolge und mit genau diesen
        Texten (aus der Spec, Abschnitt 7):
        | Titel | Erklärung | Icon |
        | --- | --- | --- |
        | Benachrichtigungen | „Damit du Termine und Freigaben aufs Handy bekommst" | `Bell` |
        | Mikrofon | „Damit du Eintraege diktieren kannst statt sie zu tippen" | `Mic` |
        | Kamera | „Fuer Fotos im Bautagebuch und zum Belege scannen" | `Camera` |
  - [ ] Statusabzeichen mit den vier Werten aus `BerechtigungsStatus`:
        `erlaubt` → „Erlaubt" (`bg-emerald-50 text-emerald-700 border-emerald-200`),
        `nicht-gefragt` → „Noch nicht gefragt" (`bg-slate-50 text-slate-600 border-slate-200`),
        `blockiert` → „Blockiert" (`bg-rose-50 text-rose-700 border-rose-200`),
        `nicht-verfuegbar` → „Geraet kann das nicht" (`bg-slate-50 text-slate-500 border-slate-200`).
  - [ ] Status beim Mount über `leseBenachrichtigungsStatus()`, `leseMikrofonStatus()`,
        `leseKameraStatus()` laden; solange sie laufen, ein schlichter Ladezustand
        (`Loader2 animate-spin`) statt eines geratenen Status.
  - [ ] Verhalten je Status:
        - `nicht-gefragt` → Knopf „Erlauben" (`bg-rose-600 text-white`), löst
          `frageBenachrichtigungenAn(token)` / `frageMikrofonAn()` / `frageKameraAn()`
          aus. Danach den Status **neu lesen** und anzeigen. Erfolg →
          `toast.success('Erlaubnis erteilt.')`, Ablehnung/Fehler → `toast.error(...)`
          mit konkretem Text („Das Mikrofon wurde nicht erlaubt.").
        - `blockiert` → **kein** erneuter Anfrage-Knopf (kein Browser erlaubt das
          Zurücksetzen per Skript), stattdessen ein aufklappbarer Hilfetext:
          iOS: „Auf dem iPhone: Einstellungen → Safari → Mikrofon erlauben und die App
          neu starten." (Ton wie `ScannerModal.tsx:713-715`);
          Android: „Auf Android: im Browser oben auf das Schloss-Symbol tippen und die
          Erlaubnis dort umstellen."
        - `nicht-verfuegbar` → Zeile bleibt informativ, kein Knopf.
        - `erlaubt` → nur Abzeichen, kein Knopf.
  - [ ] Dauerhafte Datenschutz-Zeile unter der Mikrofon-Karte (Entscheidung 4):
        „Diktierte Aufnahmen werden nur zum Aufschreiben verschickt und danach sofort
        geloescht. Gespeichert wird weder die Aufnahme noch der Text." — klein
        (`text-xs text-slate-500`), dauerhaft sichtbar.
  - [ ] Test `EinstellungenPage.test.tsx` mit `vi.mock('../services/permissionStatusService')`
        und `vi.mock('../services/notificationBootstrap')`, gerendert in
        `<MemoryRouter><ToastProvider>`:
        - Alle drei Zeilen mit Titel und Erklärung sind sichtbar.
        - Je Status wird das richtige Abzeichen gezeigt; bei `blockiert` gibt es
          **keinen** „Erlauben"-Knopf, aber den Hilfetext; bei `nicht-verfuegbar`
          keinen Knopf.
        - `nicht-gefragt` + Klick auf „Erlauben" ruft genau die passende Funktion auf
          und liest den Status danach neu.
        - Abgelehnte Anfrage (`frageMikrofonAn` wirft) → `role="alert"`-Toast.
        - Die Datenschutz-Zeile ist sichtbar.
  - [ ] E2E `e2e/einstellungen-berechtigungen.spec.ts`: `page.addInitScript` setzt
        `zeiterfassung_token`/`zeiterfassung_mitarbeiter` (Dummy „Max Mustermann") und
        stubbt `navigator.permissions.query`; `page.route('**/api/**', …)` wie in
        `e2e/zeitkonto-status.spec.ts:11-28`; `page.goto('/einstellungen')`; prüfen,
        dass die drei Zeilen sichtbar sind und `body` nicht horizontal scrollt;
        Screenshot über `testInfo.outputPath(...)`.
        Lauf: `E2E_PORT=<eigener Port> npx playwright test e2e/einstellungen-berechtigungen.spec.ts`.
  - [ ] `npx vitest run src/pages/EinstellungenPage.test.tsx`, `npm run lint`,
        `npm run build` (Artefakte verwerfen).

---

### Task 9 — Frontend: `App.tsx` — Route `/einstellungen` und keine ungefragte Erstabfrage mehr

- **Files:**
  - `react-zeiterfassung/src/App.tsx` (geändert)
  - `react-zeiterfassung/e2e/benachrichtigungen-keine-erstabfrage.spec.ts` (neu)
- **Vorbild:** die bestehenden Routen-Zeilen `App.tsx:330-354`; die zu ersetzende
  Funktion `App.tsx:183-217`.
- **Interfaces:**
  - **Produces:** Route `/einstellungen`; `App.tsx` ruft an beiden Stellen nur noch
    `starteBenachrichtigungenFallsErlaubt`.
  - **Consumes:** Task 6 (`notificationBootstrap`), Task 8 (`EinstellungenPage` muss
    existieren, sonst schlägt der Import fehl).
- **Steps:**
  - [ ] Import ergänzen: `import EinstellungenPage from './pages/EinstellungenPage'`
        (zu den übrigen Seiten-Imports, `App.tsx:59-81`) und
        `import { starteBenachrichtigungenFallsErlaubt, stoppeBenachrichtigungsIntervall } from './services/notificationBootstrap'`.
  - [ ] **Beide** Aufrufstellen umstellen — das ist der Kern dieses Tasks:
        - `App.tsx:171` (in `initializeApp`, Session-Wiederherstellung):
          `initializeNotifications(auth.token)` → `starteBenachrichtigungenFallsErlaubt(auth.token)`.
        - `App.tsx:284` (in `validateAndStoreToken`, QR-Login):
          `initializeNotifications(token)` → `starteBenachrichtigungenFallsErlaubt(token)`.
        Fehlt eine der beiden, bleibt der reflexhafte Prompt über die andere Stelle
        bestehen (verifiziert — die Spec nennt beide, das Brainstorming nur eine).
  - [ ] Die komponenten-lokale Funktion `initializeNotifications`
        (`App.tsx:183-217`) **ersatzlos entfernen** — sie lebt jetzt in
        `notificationBootstrap.ts`. Ebenso `const notificationIntervalRef = useRef<number | null>(null)`
        (`App.tsx:91`) entfernen.
  - [ ] `handleLogout` (`App.tsx:301-310`): den Block mit `notificationIntervalRef`
        durch `stoppeBenachrichtigungsIntervall()` ersetzen. Der Rest (`clearAuth()`,
        `setIsAuthenticated(false)`, `setMitarbeiter(null)`) bleibt unverändert.
  - [ ] Route ergänzen, direkt vor der Catch-all-Zeile
        `<Route path="*" element={<Navigate to="/" replace />} />` (`App.tsx:354`):
        `<Route path="/einstellungen" element={<EinstellungenPage />} />`.
  - [ ] Prüfen, dass `NotificationService` in `App.tsx` weiterhin importiert wird,
        solange `syncData` (`App.tsx:248-251`) es nutzt — sonst den Import entfernen,
        sonst reißt Lint (`no-unused-vars`).
  - [ ] E2E `e2e/benachrichtigungen-keine-erstabfrage.spec.ts`: `addInitScript` setzt
        `Notification.permission = 'default'` und zählt Aufrufe von
        `Notification.requestPermission` in `window.__promptAufrufe`; App laden (`/`)
        mit gesetztem Token; erwarten, dass `__promptAufrufe === 0` ist und die
        Kachel-Oberfläche normal erscheint. Zweiter Fall: `permission = 'granted'` →
        die App fragt weiterhin nicht, aber ruft `/api/push/vapid-key` auf (per
        `page.route` beobachtbar) — die Kette läuft also weiterhin automatisch.
  - [ ] `npm run lint`, `npm run build` (Artefakte verwerfen),
        `E2E_PORT=<eigener Port> npx playwright test e2e/benachrichtigungen-keine-erstabfrage.spec.ts`.
        Zusätzlich `npx vitest run src/pages/DashboardPage.test.tsx` gegenprüfen (App-Umbau
        darf nichts reißen).

---

### Task 10 — Frontend: Zahnrad-Knopf im Dashboard-Kopf

- **Files:**
  - `react-zeiterfassung/src/pages/DashboardPage.tsx` (geändert)
  - `react-zeiterfassung/src/pages/DashboardPage.test.tsx` (geändert)
  - `react-zeiterfassung/e2e/dashboard-einstellungen-knopf.spec.ts` (neu)
- **Vorbild:** Icon-Knopf mit `p-2 hover:bg-slate-100 rounded-lg transition-all
  active:scale-95` im Kopfbereich: `AbwesenheitenPage.tsx:157-163`. Navigation per
  `navigate('/…')` ist in `DashboardPage.tsx:70` bereits vorhanden.
- **Interfaces:**
  - **Produces:** ein erreichbarer Weg zur Einstellungsseite.
  - **Consumes:** Task 9 (Route `/einstellungen`), Task 8 (Zielseite).
- **Steps:**
  - [ ] `Settings` zur Lucide-Import-Zeile `DashboardPage.tsx:3` hinzufügen.
  - [ ] In `DashboardPage.tsx:993-995` steht heute:
        ```tsx
        <div className="flex items-center gap-2">
            <NetworkStatusBadge syncStatus={syncStatus} onSync={onSync} />
        </div>
        ```
        Den Zahnrad-Knopf **links vom** `<NetworkStatusBadge>` als erstes Kind
        einfügen:
        ```tsx
        <button
            type="button"
            onClick={() => navigate('/einstellungen')}
            aria-label="Einstellungen"
            className="p-2 -mr-1 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
        >
            <Settings className="w-5 h-5" />
        </button>
        ```
        `aria-label` ist Pflicht (Icon-only, `FRONTEND_UI.md`).
  - [ ] `DashboardPage.test.tsx` um einen Fall ergänzen (Aufbau der bestehenden Tests
        ab Zeile 88 wiederverwenden, inkl. `localStorage.setItem('zeiterfassung_token', 'tok-test')`):
        Knopf mit `getByRole('button', { name: 'Einstellungen' })` finden, klicken und
        prüfen, dass zur Route `/einstellungen` navigiert wird (Test in
        `<MemoryRouter>` mit `<Routes>` und einer Platzhalter-Route, oder über einen
        gemockten `useNavigate` — je nachdem, was in der Datei bereits etabliert ist).
  - [ ] E2E `e2e/dashboard-einstellungen-knopf.spec.ts`: Dashboard laden (Stubs wie in
        `e2e/zeitkonto-status.spec.ts`), Zahnrad über `getByRole('button', { name: 'Einstellungen' })`
        anklicken, erwarten, dass die Einstellungsseite erscheint; Screenshot des
        Kopfbereichs.
  - [ ] `npx vitest run src/pages/DashboardPage.test.tsx`, `npm run lint`,
        `npm run build` (Artefakte verwerfen), eigene Playwright-Spec fahren.

---

### Task 11 — Einbau: Bautagebuch und Anfrage-Tagebuch

- **Files:**
  - `react-zeiterfassung/src/pages/ProjektNotizenPage.tsx` (geändert)
  - `react-zeiterfassung/src/pages/AnfrageNotizenPage.tsx` (geändert)
  - `react-zeiterfassung/src/pages/ProjektNotizenPage.test.tsx` (geändert)
  - `react-zeiterfassung/e2e/spracheingabe-bautagebuch.spec.ts` (neu)
- **Hinweis zum Zuschnitt:** Beide Seiten liegen in **einem** Task, weil
  `ProjektNotizenPage.test.tsx` beide über `describe.each` (`:12`) abdeckt — getrennte
  Tasks würden sich diese Testdatei teilen und beim Merge kollidieren.
- **Vorbild:** der Modal-Aufbau in `ProjektNotizenPage.tsx:480-540` und der
  identische in `AnfrageNotizenPage.tsx:488-548`.
- **Interfaces:**
  - **Produces:** Diktat im Hauptanwendungsfall (Bautagebuch).
  - **Consumes:** Task 7 (`VoiceInputButton`).
- **Steps:**
  - [ ] `ProjektNotizenPage.tsx`: `import VoiceInputButton from '../components/VoiceInputButton'`
        ergänzen. **Direkt nach** dem Ende des `<textarea …/>` (endet in Zeile 507) und
        **vor** `<div className="space-y-3 mt-4">` (Zeile 508) einfügen:
        ```tsx
        <VoiceInputButton className="mt-2" feldName="Eintrag" wert={neueNotiz} onErgebnis={setNeueNotiz} />
        ```
        Am `<textarea>` selbst nichts ändern.
  - [ ] `AnfrageNotizenPage.tsx`: analog, **nach** dem `<textarea …/>` (endet in
        Zeile 514) und vor `<div className="space-y-3 mt-4">` (Zeile 516):
        ```tsx
        <VoiceInputButton className="mt-2" feldName="Notiz" wert={neueNotiz} onErgebnis={setNeueNotiz} />
        ```
  - [ ] `ProjektNotizenPage.test.tsx` ergänzen (bestehende Tests nicht umbauen), mit
        `vi.mock('../components/VoiceInputButton')` **nicht** — stattdessen die beiden
        Service-Module mocken (`audioRecorderService`, `spracheingabeService`), damit die
        echte Komponente mitgetestet wird:
        - Für **beide** Seiten (`it.each` wie in Zeile 30): Modal öffnen, Knopf
          „Eintrag diktieren" bzw. „Notiz diktieren" ist vorhanden.
        - Getippten Text ins Textfeld schreiben, diktieren, prüfen, dass der getippte
          Text **erhalten** bleibt und das Diktat mit Leerzeile dahinter steht
          (Regressionsschutz für die wichtigste Anforderung).
  - [ ] E2E `e2e/spracheingabe-bautagebuch.spec.ts`: `addInitScript` ersetzt
        `navigator.mediaDevices.getUserMedia` durch einen Fake-Stream und `MediaRecorder`
        durch eine Fake-Klasse (Playwright-Chromium hat ohne Extra-Flags kein
        Fake-Mikrofon); `/api/spracheingabe/transkribieren` per `page.route` mit
        `{ text: 'Gelaender montiert.' }` beantworten; Notizen-Seite öffnen, Modal
        öffnen, diktieren, prüfen, dass der Text im Feld steht; Screenshot.
  - [ ] `npx vitest run src/pages/ProjektNotizenPage.test.tsx`, `npm run lint`,
        `npm run build` (Artefakte verwerfen), eigene Playwright-Spec fahren.

---

### Task 12 — Einbau: Urlaubsantrag (Feld „Bemerkung")

- **Files:**
  - `react-zeiterfassung/src/pages/UrlaubsantragPage.tsx` (geändert)
  - `react-zeiterfassung/src/pages/UrlaubsantragPage.test.tsx` (geändert)
  - `react-zeiterfassung/e2e/spracheingabe-urlaub.spec.ts` (neu)
- **Vorbild:** Feldgruppe `UrlaubsantragPage.tsx:448-461`.
- **Interfaces:**
  - **Produces:** Diktat im Bemerkungsfeld.
  - **Consumes:** Task 7 (`VoiceInputButton`).
- **Steps:**
  - [ ] Import `VoiceInputButton` ergänzen (zu den Imports ab Zeile 1).
  - [ ] **Nach** dem `</div>` in Zeile 460, das den `relative`-Wrapper um `<textarea>`
        und das dekorative `FileText`-Icon schließt, und **vor** dem `</div>` in
        Zeile 461 einfügen:
        ```tsx
        <VoiceInputButton className="mt-2" feldName="Bemerkung" wert={bemerkung} onErgebnis={setBemerkung} />
        ```
        Das `FileText`-Icon bleibt unverändert (`pointer-events-none`, es stört nicht).
        Das Label „Bemerkung (Optional)" bleibt ebenfalls unverändert — das Feld heißt
        im Code `bemerkung`, nicht „Grund" (die Benennung im Brainstorming war falsch).
  - [ ] `UrlaubsantragPage.test.tsx` ergänzen (Render-Muster aus Zeile 12
        wiederverwenden, `<MemoryRouter><ToastProvider>` — **kein** `ConfirmProvider`
        nötig, die Komponente kommt ohne aus): Knopf „Bemerkung diktieren" ist
        vorhanden; nach einem Diktat steht der Text im Feld, vorhandene Eingaben bleiben
        erhalten. Services wie in Task 11 mocken.
  - [ ] E2E `e2e/spracheingabe-urlaub.spec.ts` analog zu Task 11, auf `/urlaub`.
  - [ ] `npx vitest run src/pages/UrlaubsantragPage.test.tsx`, `npm run lint`,
        `npm run build` (Artefakte verwerfen), eigene Playwright-Spec fahren.

---

### Task 13 — Einbau: Lieferanten-Reklamation (Feld „Problembeschreibung")

- **Files:**
  - `react-zeiterfassung/src/pages/LieferantReklamationCreatePage.tsx` (geändert)
  - `react-zeiterfassung/src/pages/LieferantReklamationCreatePage.test.tsx` (neu)
  - `react-zeiterfassung/e2e/spracheingabe-reklamation.spec.ts` (neu)
- **Vorbild:** Karte „Problembeschreibung" `LieferantReklamationCreatePage.tsx:216-228`.
  Für die neue Testdatei: `LieferantLieferscheinePage.test.tsx` (gleiche Seitenfamilie,
  gleiche Stub-Art).
- **Interfaces:**
  - **Produces:** Diktat in der Problembeschreibung.
  - **Consumes:** Task 7 (`VoiceInputButton`).
- **Steps:**
  - [ ] Import `VoiceInputButton` ergänzen.
  - [ ] **Nach** dem `<textarea …/>` (endet Zeile 227) und **vor** dem schließenden
        `</div>` der Karte (Zeile 228) einfügen:
        ```tsx
        <VoiceInputButton className="mt-2" feldName="Problembeschreibung" wert={beschreibung} onErgebnis={setBeschreibung} />
        ```
  - [ ] Neue Testdatei `LieferantReklamationCreatePage.test.tsx` anlegen. Die Seite lädt
        beim Mount `/api/lieferanten/:id/dokumente?typ=LIEFERSCHEIN&token=…`
        (`:64-66`) — `fetch` per `vi.stubGlobal` mit einer leeren Liste beantworten,
        `localStorage.setItem('zeiterfassung_token', 'tok-test')`, Render in
        `<MemoryRouter initialEntries={['/lieferanten/1/reklamation/neu']}>` +
        `<Routes>` + `<ToastProvider>`. Fälle: Knopf „Problembeschreibung diktieren"
        vorhanden; Diktat hängt an, ohne vorhandenen Text zu ersetzen; Fehler beim
        Diktat zeigt einen `role="alert"`-Toast und lässt das Feld unverändert.
        Nur Dummy-Daten („Musterbetrieb GmbH").
  - [ ] E2E `e2e/spracheingabe-reklamation.spec.ts` analog Task 11, auf
        `/lieferanten/1/reklamation/neu`.
  - [ ] `npx vitest run src/pages/LieferantReklamationCreatePage.test.tsx`,
        `npm run lint`, `npm run build` (Artefakte verwerfen), eigene Spec fahren.

---

## Kopplungs-Hinweise für den Abschnitts-Schnitt

Der Abschnitts-Agent entscheidet, dies sind die harten Randbedingungen:

1. **Task 2 und Task 4 gehören in denselben Abschnitt.** Ein Client ohne Endpunkt (oder
   umgekehrt) ist kein lauffähiger Stand, und keine Testsuite sieht das. Sie fassen
   keine gemeinsame Datei an, können also parallel laufen.
2. **Task 8, 9 und 10 gehören in denselben Abschnitt.** Task 9 entfernt die automatische
   Berechtigungsabfrage; ohne die Einstellungsseite (Task 8) und den Weg dorthin
   (Task 10) könnte danach niemand mehr Benachrichtigungen aktivieren. Die drei fassen
   `EinstellungenPage.tsx`, `App.tsx` und `DashboardPage.tsx` an — disjunkt.
   Zusätzlich braucht Task 9 den Import von `EinstellungenPage`, sonst bricht der Build.
3. **Task 7 muss vor 11, 12 und 13 fertig sein** (die drei importieren die Komponente).
   Untereinander sind 11/12/13 disjunkt und laufen zusammen.
4. **Task 1 vor Task 2** (Controller ruft den Service auf).
5. **Task 3 und 4 vor Task 7**, **Task 5 und 6 vor Task 8/9**.
6. Alle Frontend-Tasks möglichst in **wenige** Runden legen, damit der Design-Reviewer
   nicht mehrfach läuft.
7. Keine zwei Tasks einer Runde teilen sich eine Datei — programmatisch prüfen. Die
   einzigen Dateien, die überhaupt in mehr als einem Task vorkommen könnten, sind die
   generierten Build-Artefakte unter `src/main/resources/static/zeiterfassung/`; dafür
   gilt die Verwerfen-Regel in den Global Constraints.

Eine grobe topologische Ebene ergibt sich daraus zu: **{1, 3, 5, 6}** → **{2, 4, 7}** →
**{8, 9, 10}** → **{11, 12, 13}**. Vier Runden; der Abschnitts-Agent darf enger
schneiden, solange 1–7 oben eingehalten sind.

## Dateiübersicht (für die Disjunktheitsprüfung)

| Task | Dateien |
| --- | --- |
| 1 | `service/SpracheingabeService.java`, `dto/SpracheingabeErgebnis.java`, `application.properties`, `service/SpracheingabeServiceTest.java` |
| 2 | `controller/SpracheingabeController.java`, `config/SecurityConfig.java`, `config/ZeiterfassungSecurityFilter.java`, `controller/SpracheingabeControllerTest.java`, `config/ZeiterfassungFilterChainMatcherTest.java` |
| 3 | `src/services/audioRecorderService.ts` (+ `.test.ts`) |
| 4 | `src/services/spracheingabeService.ts` (+ `.test.ts`) |
| 5 | `src/services/permissionStatusService.ts` (+ `.test.ts`) |
| 6 | `src/services/notificationBootstrap.ts` (+ `.test.ts`) |
| 7 | `src/components/VoiceInputButton.tsx` (+ `.test.tsx`) |
| 8 | `src/pages/EinstellungenPage.tsx` (+ `.test.tsx`), `e2e/einstellungen-berechtigungen.spec.ts` |
| 9 | `src/App.tsx`, `e2e/benachrichtigungen-keine-erstabfrage.spec.ts` |
| 10 | `src/pages/DashboardPage.tsx`, `src/pages/DashboardPage.test.tsx`, `e2e/dashboard-einstellungen-knopf.spec.ts` |
| 11 | `src/pages/ProjektNotizenPage.tsx`, `src/pages/AnfrageNotizenPage.tsx`, `src/pages/ProjektNotizenPage.test.tsx`, `e2e/spracheingabe-bautagebuch.spec.ts` |
| 12 | `src/pages/UrlaubsantragPage.tsx`, `src/pages/UrlaubsantragPage.test.tsx`, `e2e/spracheingabe-urlaub.spec.ts` |
| 13 | `src/pages/LieferantReklamationCreatePage.tsx`, `src/pages/LieferantReklamationCreatePage.test.tsx`, `e2e/spracheingabe-reklamation.spec.ts` |

(Frontend-Pfade relativ zu `react-zeiterfassung/`, Backend-Pfade relativ zu
`src/main/java/org/example/kalkulationsprogramm/` bzw. `src/test/java/org/example/kalkulationsprogramm/`.)

## Abschnittseinteilung

Vom Orchestrator festgelegt. Datei-Disjunktheit wurde programmatisch geprüft:
keine Datei kommt in mehr als einem Task vor, jeder Abschnitt ist in sich
disjunkt.

**Korrektur am Vorschlag des Grobplaners.** Die grobe Ebene schlug
`{2, 4, 7}` als zweite Runde vor. Das verletzt Randbedingung 5 desselben
Kapitels: Task 7 importiert `spracheingabeService.ts`, das erst Task 4
anlegt. Lägen beide in derselben Runde, fände `tsc -b` im Worktree von
Task 7 das Modul nicht — der Task wäre für sich nicht baubar und sein Agent
könnte seine eigene Arbeit nicht prüfen. Task 7 bekommt deshalb eine eigene
Runde. Kosten: ein zusätzlicher Review-Durchlauf. Nutzen: jeder Task-Branch
bleibt für sich übersetzbar.

| Abschnitt | Tasks | Worktree | Branch | Design-Review |
| --- | --- | --- | --- | --- |
| 1 | 1 — `SpracheingabeService` (Backend) | `/Users/marvinkuhn/dev/wt/sprach-task-1` | `sprach/task-1-backend-service` | nein |
| 1 | 3 — `audioRecorderService.ts` | `/Users/marvinkuhn/dev/wt/sprach-task-3` | `sprach/task-3-audio-recorder` | nein |
| 1 | 5 — `permissionStatusService.ts` | `/Users/marvinkuhn/dev/wt/sprach-task-5` | `sprach/task-5-permission-status` | nein |
| 1 | 6 — `notificationBootstrap.ts` | `/Users/marvinkuhn/dev/wt/sprach-task-6` | `sprach/task-6-notification-bootstrap` | nein |
| 2 | 2 — `SpracheingabeController` + Freischaltung | `/Users/marvinkuhn/dev/wt/sprach-task-2` | `sprach/task-2-backend-controller` | nein |
| 2 | 4 — `spracheingabeService.ts` (Client) | `/Users/marvinkuhn/dev/wt/sprach-task-4` | `sprach/task-4-client-service` | nein |
| 3 | 7 — `VoiceInputButton.tsx` | `/Users/marvinkuhn/dev/wt/sprach-task-7` | `sprach/task-7-voice-button` | nein |
| 4 | 8 — `EinstellungenPage.tsx` | `/Users/marvinkuhn/dev/wt/sprach-task-8` | `sprach/task-8-einstellungen-seite` | **ja** |
| 4 | 9 — `App.tsx` Route + keine Erstabfrage | `/Users/marvinkuhn/dev/wt/sprach-task-9` | `sprach/task-9-app-route` | **ja** |
| 4 | 10 — Zahnrad im Dashboard-Kopf | `/Users/marvinkuhn/dev/wt/sprach-task-10` | `sprach/task-10-zahnrad` | **ja** |
| 5 | 11 — Einbau Bautagebuch + Anfrage-Tagebuch | `/Users/marvinkuhn/dev/wt/sprach-task-11` | `sprach/task-11-tagebuecher` | **ja** |
| 5 | 12 — Einbau Urlaubsantrag | `/Users/marvinkuhn/dev/wt/sprach-task-12` | `sprach/task-12-urlaub` | **ja** |
| 5 | 13 — Einbau Reklamation | `/Users/marvinkuhn/dev/wt/sprach-task-13` | `sprach/task-13-reklamation` | **ja** |

### Warum Abschnitt 1 vier Tasks hat

Der Skill gibt maximal drei Tasks je Abschnitt vor. Abschnitt 1 hat vier.
Begründete Ausnahme: Alle vier legen ausschließlich **neue** Dateien an,
teilen nachweislich keine einzige Datei, und keiner verändert sichtbare
Oberfläche. Die Regel begrenzt Merge-Risiko und Prüffläche — beides ist hier
minimal. Die Alternative wäre ein fünfter Review-Durchlauf für einen Task,
der aus zwei neuen Dateien besteht.

### Design-Review je Abschnitt

Abschnitt 1 bis 3 ändern **nichts Sichtbares**: neue Dienste, eine noch
nirgends eingebundene Komponente. Reine Vorarbeit bekommt laut Skill keinen
Design-Review, sonst prüft er eine Stunde lang, dass sich nichts geändert
hat. Erst ab Abschnitt 4 gibt es eine im Browser ansteuerbare Oberfläche.

### Abnahmeregel (Baseline auf dem unveränderten Feature-Branch)

    Backend  ./mvnw test   554 Testklassen, 3126 Tests, 0 Failures, 0 Errors, 17 Skipped
    Frontend npm test      22 Testdateien, 217 Tests, alle grün
    Lint     npm run lint  0 Fehler

Alles grün. Es gibt **keine** bekannten Vorschäden. Jeder Fehler ist neu.

### Worktree-Einrichtung (macht der Orchestrator, nicht die Agenten)

    git worktree add /Users/marvinkuhn/dev/wt/sprach-task-<n> -b sprach/task-<n>-<name> feature/spracheingabe-zeiterfassung
    ln -s /Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe/react-zeiterfassung/node_modules \
          /Users/marvinkuhn/dev/wt/sprach-task-<n>/react-zeiterfassung/node_modules

Der Symlink entfällt bei reinen Backend-Tasks (1 und 2).

**Beim Aufräumen zwingend:** vor `git worktree remove` erst den
`node_modules`-Symlink lösen (`rm` auf den Link, nicht `rm -rf` auf das
Ziel). Sonst läuft das Entfernen durch den Verweis hindurch und leert das
echte `node_modules` im Haupt-Checkout.

## Nicht Teil dieses Plans

- `react-pc-frontend` — ausdrücklich außen vor.
- Speichern, Verlauf oder Wiedergabe von Sprachaufnahmen; Offline-Warteschlange für
  Aufnahmen; eine Oberfläche zum Pflegen des Fachvokabulars.
- Ein gemeinsames Gemini-Client-Modul (bleibt eine eigene Aufgabe, siehe
  `BeitragKiService.java:43-45`).
- Die Umstellung des projektweiten `?token=`-Musters (bekannter Restpunkt, hier nur
  fortgeführt).
- Der iOS-Safe-Area-Fix (bereits erledigt, Commit `24942cbf`).
- Der Eintrag im Verarbeitungsverzeichnis des Betriebs — organisatorisch, kein Code.

## Log

<wird während der Ausführung nicht hier befüllt — siehe eigene Kontext-Log-Datei.
Dieser Abschnitt bleibt für eine kurze Abschluss-Zusammenfassung pro Abschnitt durch
den Review-Agenten reserviert.>
