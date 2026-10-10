# E-Mail-Postfächer: eigene Postfächer pro Benutzer, Hauptpostfach, Sichtbarkeit

Stand: 10.10.2026 · Status: freigegeben (Brainstorming), Etappe 1 umgesetzt (siehe „Umsetzungsnotizen Etappe 1“)

## Ziel

Der Betrieb hat bei Hetzner unter der eigenen Domain mehrere echte Postfächer
(info@, rechnungen@, max@ …), jedes mit eigenem Login. Das ERP soll:

- alle diese Postfächer abrufen und daraus versenden,
- ein **Hauptpostfach** (z. B. info@) kennen,
- jedem **Frontend-Benutzer** (wer sich im ERP anmeldet, nicht jeder Mitarbeiter)
  ein eigenes Postfach zuordnen,
- pro Postfach festlegen, **wer es sehen darf** (alle / bestimmte Abteilungen /
  bestimmte Benutzer) – Admin sieht immer alles,
- beim Schreiben das **Absender-Postfach** wählen lassen (nur bei neuen Mails),
  bei Antworten/Weiterleitungen fest das Postfach, in dem die Mail ankam,
- **Sammel-Mails** einzeln verschicken (jeder Empfänger bekommt eine eigene Mail,
  sieht keine anderen Empfänger).

## Ausgangslage (Code-Stand origin/main 54f6be60)

- Ein IMAP-/SMTP-Konto in den System-Einstellungen (`SystemSettingsService`,
  `smtp.*`, `imap.*`), Abruf in `EmailImportService` (fester Ordnerliste).
- Zweites Konto „Postfach für Rechnungen und Mahnungen“ (`smtp.dokumente.*`,
  `mail.dokumente.*`), nur für Geschäftsdokumente.
- Beide Konten als Bausteine `SystemSettingsService.MailKonto` (Versand) und
  `ImapZugang` (Abruf/Gesendet-Kopie), genutzt u. a. von Mahnlauf,
  Auto-Auftragsbestätigung, Anfrage-Bestätigung, `SentMailArchiver`,
  `ImapAppendService`, `EmailController`, `UnifiedEmailController`.
- Tabelle `email_absender` (Entity `EmailAbsender`): reine Absender-Adressen
  ohne Zugang; `FrontendUserProfile.emailAbsender` ordnet einem Benutzer eine
  Adresse zu. Gepflegt im FirmaEditor (`/api/firma/email-absender`).
- `MailSecretService` verschlüsselt Zugangsdaten (AES-GCM, Schlüssel
  `mail.credentials.encryption-key`).
- Jeder sieht im E-Mail-Center alles. Kein BCC, kein Einzelversand.

## Entscheidungen

| Frage | Entscheidung |
| --- | --- |
| Wie kommen Mails ins Postfach? | Eigener IMAP/SMTP-Zugang je Postfach (echte Hetzner-Postfächer). |
| Was sind „Rechnungs-Mails“? | Das Postfach rechnungen@ – heute das Dokument-Mailkonto, künftig ein normales Postfach mit Haken „Für Rechnungen & Mahnungen“. |
| Neue Tabelle oder Ausbau? | **Ausbau** von `email_absender` zum Postfach (Zuordnung Benutzer → Postfach existiert schon). Klasse heißt weiter `EmailAbsender`, in der UI „Postfach“. |
| Sichtbarkeit außerhalb E-Mail-Center? | Nein. Projekt-/Anfrage-/Lieferanten-Reiter zeigen weiter alle zugeordneten Mails (Rechnungs-Anhangsrecht der Abteilung greift dort wie heute). |
| Admin-Ansicht | Alle Postfächer in **einer** Liste, keine Postfach-Auswahl, nur ein Postfach-Schild an jeder Mail. Gilt auch für „Gesendet“. |
| Absender bei neuer Mail | Auswahl „Senden von“, vorbelegt mit eigenem Postfach, sonst Hauptpostfach. |
| Absender bei Antwort / Allen antworten / Weiterleiten | **Fest** = Postfach, in dem die Mail ankam (bei eigener gesendeter Mail: von dem sie rausging). Nur Anzeige, keine Auswahl; Backend ignoriert eine abweichende Angabe. |
| Geschäftsdokumente (Rechnung, Mahnung, Angebot, AB) | Über das Postfach mit „Für Rechnungen & Mahnungen“; fehlt eins → Hauptpostfach. |
| Automatische Versände (Mahnlauf, Anfrage-Bestätigung, Auto-AB) | Unverändert über `MailKonto`-Bausteine, die jetzt aus den Postfächern lesen. |
| Gelesen/ungelesen | Pro Mail für alle (geteiltes Postfach). |
| Sammel-Mail | Schalter „Einzeln verschicken“, max. 50 Empfänger, keine CC, je Empfänger eine eigene gespeicherte Mail, Teilfehler werden gemeldet. Keine persönliche Anrede (eigener späterer Schritt). |
| Etappen | **1 = Postfächer** (inkl. Versand + Einzelversand), **2 = Sichtbarkeit**. |

---

## Etappe 1 – Postfächer

### 1.1 Datenmodell (Flyway V407, idempotent)

`email_absender` bekommt (alle nullable bzw. mit Default):

| Spalte | Typ | Bedeutung |
| --- | --- | --- |
| `benutzername` | VARCHAR(255) | Login (meist = Adresse) |
| `passwort_verschluesselt` | TEXT | via `MailSecretService` |
| `smtp_host` / `smtp_port` | VARCHAR(255) / INT | Versand (Port 465) |
| `imap_host` / `imap_port` | VARCHAR(255) / INT | Abruf + Gesendet-Kopie (Port 993) |
| `hauptpostfach` | BOOLEAN NOT NULL DEFAULT FALSE | genau eins |
| `fuer_geschaeftsdokumente` | BOOLEAN NOT NULL DEFAULT FALSE | höchstens eins |
| `letzter_abruf_am` | DATETIME | |
| `letzter_abruf_fehler` | VARCHAR(500) | Klartext für Handwerker, `NULL` = ok |

Neue Tabelle `email_postfach_zuordnung`:
`email_id` (FK email, ON DELETE CASCADE), `postfach_id` (FK email_absender),
`imap_ordner` VARCHAR(255), `imap_uid` BIGINT, PK (`email_id`, `postfach_id`).
Eine Mail kann so in mehreren Postfächern liegen (info@ + max@ gleichzeitig
adressiert) und wird trotzdem nur einmal gespeichert (Dedupe über `message_id`).
Ausgehende Mails bekommen eine Zeile für das Postfach, aus dem sie versendet
wurden. Die Alt-Spalten `email.imap_folder`/`imap_uid` bleiben unverändert.

Ein Postfach **ohne** vollständigen Zugang (Benutzername + Passwort + Server)
bleibt eine reine Absender-Adresse wie heute: kein Abruf, Versand über das
Hauptpostfach.

### 1.2 Umzug beim ersten Start (`PostfachUmzugRunner`, Java, idempotent)

Passwörter müssen verschlüsselt werden → kein reines SQL möglich.

1. Gibt es noch kein Hauptpostfach und sind `imap.*`/`smtp.*` gesetzt: Postfach
   mit der Adresse des Kontos suchen oder anlegen, Zugang verschlüsselt
   übernehmen, `hauptpostfach = true`. Absender-Adresse/-Name aus
   `mail.from-address`/`mail.absender-name`.
2. Ist das Dokument-Mailkonto aktiv: Postfach (Absender- bzw. Login-Adresse)
   suchen oder anlegen, Zugang übernehmen, `fuer_geschaeftsdokumente = true`.
3. Alle Mails ohne Zeile in `email_postfach_zuordnung` → Hauptpostfach
   (Batch `INSERT … SELECT`, übernimmt `imap_folder`/`imap_uid`).
4. Ist `mail.credentials.encryption-key` nicht eingerichtet: nichts umziehen,
   Warnung loggen; die Alt-Einstellungen bleiben als Rückfall aktiv.

Die Alt-Einstellungen werden **nicht gelöscht** (Rückfall). `getStandardMailKonto()`,
`getStandardImapZugang()`, `getDokumentMailKonto()`, `getDokumentImapZugang()`,
`nutztDokumentMailKonto()` lesen zuerst die Postfächer, sonst wie bisher.

### 1.3 Abruf (`EmailImportService`)

- Jede Minute: alle aktiven Postfächer mit Zugang, nacheinander, jedes in
  eigenem try/catch. Fehler eines Postfachs → `letzter_abruf_fehler`
  (z. B. „Anmeldung fehlgeschlagen – Passwort prüfen“), die anderen laufen weiter.
- Ordner: Hauptpostfach behält die heutige Liste (INBOX + Archiv-Unterordner +
  Sent); alle anderen INBOX + `INBOX.Sent` / `INBOX.Sent Items` / `Sent`
  (sofern vorhanden).
- Bereits bekannte `message_id` → nur Zuordnungszeile ergänzen.
- Endgültiges Löschen vom Server löscht in allen zugeordneten Postfächern.

### 1.4 Versand

Neuer Service `PostfachVersandService` (Auslagerung aus `UnifiedEmailController`,
siehe „Freigaben“):

- `bestimmePostfach(dto, frontendUserId)`:
  1. Antwort/Weiterleitung → `antwortPostfach` der Original-Mail (fest).
  2. Geschäftsdokument → Rechnungs-Postfach, sonst Hauptpostfach.
  3. `postfachId` gesetzt → dieses (muss aktiv sein, sonst 400).
  4. Eigenes Postfach des Benutzers, sonst Hauptpostfach.
- `antwortPostfach(email)`: OUT → Versand-Postfach; IN → das zugeordnete
  Postfach, dessen Adresse im „An“ steht, sonst eins aus „CC“, sonst
  Hauptpostfach, sonst das erste zugeordnete.
- SMTP-Zugang des Postfachs; reine Absender-Adresse → Hauptpostfach-Zugang
  mit dieser From-Adresse (heutiges Verhalten).
- Gesendet-Kopie in den IMAP des versendenden Postfachs.
- Gespeicherte OUT-Mail bekommt Zuordnungszeile zum Postfach.

**Einzelversand:** eine SMTP-Verbindung, je Empfänger eine eigene `MimeMessage`
mit genau diesem Empfänger im „An“; gleicher Betreff/Text/Anhänge; je Empfänger
eine gespeicherte `Email` (Zustellstatus/Rückläufer pro Empfänger). Teilfehler
brechen nicht ab.

### 1.5 API-Vertrag Etappe 1

Alle Fehler: `{ "message": "<Handwerker-Sprache>" }`. CSRF wie überall.

#### Postfächer verwalten (nur ADMIN; sonst 403, ohne Login 401)

`GET /api/postfaecher` → `200 PostfachDto[]` (sortiert `sortierung`, `id`)

```json
{
  "id": 3,
  "emailAdresse": "info@bauschlosserei-kuhn.de",
  "anzeigename": "Bauschlosserei Kuhn",
  "aktiv": true,
  "sortierung": 0,
  "hauptpostfach": true,
  "fuerGeschaeftsdokumente": false,
  "benutzername": "info@bauschlosserei-kuhn.de",
  "passwortGesetzt": true,
  "smtpHost": "mail.your-server.de",
  "smtpPort": 465,
  "imapHost": "mail.your-server.de",
  "imapPort": 993,
  "abrufAktiv": true,
  "letzterAbrufAm": "2026-10-10T09:41:00",
  "letzterAbrufFehler": null,
  "zugewieseneBenutzer": [{ "id": 7, "displayName": "Max Mustermann" }]
}
```

`abrufAktiv` = Zugang vollständig und `aktiv`. Das Passwort wird **nie** ausgeliefert.

`POST /api/postfaecher` → `201 PostfachDto` ·
`PUT /api/postfaecher/{id}` → `200 PostfachDto` · Body `PostfachSpeichernRequest`:

```json
{
  "emailAdresse": "max@bauschlosserei-kuhn.de",
  "anzeigename": "Max Mustermann – Bauschlosserei Kuhn",
  "aktiv": true,
  "sortierung": 10,
  "hauptpostfach": false,
  "fuerGeschaeftsdokumente": false,
  "benutzername": "max@bauschlosserei-kuhn.de",
  "passwort": "",
  "smtpHost": "mail.your-server.de",
  "smtpPort": 465,
  "imapHost": "mail.your-server.de",
  "imapPort": 993
}
```

Regeln (400 mit Meldung):
- `emailAdresse` Pflicht, gültig, eindeutig (ohne Groß/Klein).
- `passwort` leer/`null` = unverändert.
- Felder ≤ 255 Zeichen, Ports 1–65535.
- `benutzername` und `emailAdresse` gleiche Domain (sonst Fälschungs-Hinweis wie heute).
- `hauptpostfach: true` → alle anderen verlieren den Haken. Das einzige Hauptpostfach
  abwählen → 400 „Es muss ein Hauptpostfach geben – bitte ein anderes als Hauptpostfach markieren.“
- `fuerGeschaeftsdokumente: true` → alle anderen verlieren den Haken (abwählen erlaubt).
- Hauptpostfach deaktivieren → 400.
- Zugang teilweise ausgefüllt ist erlaubt (Entwurf), dann `abrufAktiv = false`.
- Unbekannte `id` → 404.

`DELETE /api/postfaecher/{id}` → `204`; 400 wenn Hauptpostfach („Das Hauptpostfach kann nicht
gelöscht werden.“) oder wenn schon Mails zugeordnet sind („Dieses Postfach hat schon E-Mails –
bitte stattdessen ausschalten.“); 404 unbekannt. Benutzer-Zuordnungen werden dabei gelöst.

`POST /api/postfaecher/test` → `200 { "versandOk": bool, "abrufOk": bool, "message": "…" }`
Body: `{ "id": 3|null, "benutzername", "passwort" (leer = gespeichertes von id), "smtpHost",
"smtpPort", "imapHost", "imapPort", "testEmpfaenger": "…"|null }`. Prüft SMTP-Login
(+ Testmail, wenn `testEmpfaenger`) und IMAP-Login.

#### Absender-Auswahl im E-Mail-Center (jeder angemeldete Benutzer)

`GET /api/emails/absender-postfaecher` → `200`

```json
[{ "id": 7, "emailAdresse": "max@bauschlosserei-kuhn.de", "anzeigename": "Max Mustermann", "eigenes": true, "hauptpostfach": false }]
```

Alle aktiven Postfächer; eigenes zuerst, dann Hauptpostfach, dann `sortierung`.
(Etappe 2: nur sichtbare.) Ersetzt `GET /api/emails/from-addresses` (wird entfernt).

#### Mail-Listen und Detail (`/api/emails/**`, `UnifiedEmailDto`)

Neue Felder in **jedem** `UnifiedEmailDto` (Listen, Detail, Verlauf, Send-Antwort):

```json
"postfaecher": [{ "id": 3, "emailAdresse": "info@bauschlosserei-kuhn.de", "anzeigename": "Bauschlosserei Kuhn" }],
"antwortPostfach": { "id": 3, "emailAdresse": "info@bauschlosserei-kuhn.de", "anzeigename": "Bauschlosserei Kuhn" }
```

`postfaecher` nie `null` (ggf. `[]`), `antwortPostfach` `null` nur, wenn es gar kein
Postfach gibt.

#### Senden `POST /api/emails/send` (multipart, Teil `dto`)

Neue Felder in `ProjektEmailDto` (Teil `dto`):

| Feld | Typ | Bedeutung |
| --- | --- | --- |
| `postfachId` | Long, optional | Absender-Postfach für **neue** Mails |
| `weitergeleitetVonEmailId` | Long, optional | Weiterleitung: Postfach = `antwortPostfach` dieser Mail, `postfachId` wird ignoriert; unbekannt → 404 |
| `einzelversand` | boolean, default false | Sammel-Mail einzeln verschicken |

`sender` wird ignoriert (Absender = Adresse des bestimmten Postfachs).

Antwort:
- `einzelversand = false`: wie heute `200 UnifiedEmailDto`.
- `einzelversand = true`: `200`, wenn mind. eine Mail rausging:

```json
{ "verschickt": 28, "fehlgeschlagen": [{ "adresse": "kaputt@example.org", "grund": "Empfänger unbekannt" }], "emails": [ /* UnifiedEmailDto je verschickter Mail */ ] }
```

  Gingen alle schief: `502` mit `{ "message": "…", "fehlgeschlagen": [...] }`.
- 400: `postfachId` unbekannt/inaktiv; Einzelversand mit CC („Beim einzelnen Verschicken
  bitte keine Kopie-Empfänger eintragen.“); Einzelversand mit mehr als 50 Empfängern
  („Höchstens 50 Empfänger pro Sammel-Mail.“); keine Empfänger.

#### Antworten `POST /api/emails/{emailId}/reply`

Postfach = `antwortPostfach` der Mail `emailId`; `postfachId`/`sender`/`einzelversand`
werden ignoriert. Antwort unverändert `200 UnifiedEmailDto`.

#### Entwürfe

`EmailDraft` speichert zusätzlich `postfachId` und `einzelversand`; die Draft-DTOs bekommen
dieselben Felder (`null`/`false`, wenn nicht gesetzt).

#### Benutzer

`FrontendUser`-Speichern nimmt weiter `emailAbsenderId` (UI: „Eigenes Postfach“).
Benutzer-Editor lädt die Liste aus `GET /api/postfaecher`.

#### Entfallende Endpoints (Frontend zieht mit)

`/api/settings/smtp`, `/smtp/test`, `/imap`, `/imap/test`, `/email-account`,
`/mail-from`, `/dokument-mail`, `/dokument-mail/test`, `/api/firma/email-absender*`,
`/api/emails/from-addresses`. Vorher per Grep sicherstellen, dass kein anderer
Aufrufer (auch `react-zeiterfassung`) sie nutzt; sonst bleiben sie bestehen.

### 1.6 Oberfläche Etappe 1

- **Einstellungen → E-Mail → Postfächer**: ersetzt die bisherigen Blöcke „Postfach“
  und „Postfach für Rechnungen und Mahnungen“. Liste mit Adresse, Anzeigename,
  Schildern „Hauptpostfach“ / „Rechnungen & Mahnungen“, Abruf-Status
  („Abruf ok · vor 2 Min.“ / roter Fehlertext), zugewiesene Benutzer.
  Dialog zum Anlegen/Bearbeiten: Server-Felder werden bei neuen Postfächern aus
  dem Hauptpostfach vorbelegt und sind unter „Server-Einstellungen“ eingeklappt –
  bei Hetzner genügen Adresse und Passwort. Knopf „Verbindung testen“.
  Hinweistext zum Spam-Vorteil der eigenen Domain bleibt beim Haken
  „Für Rechnungen & Mahnungen“.
- **FirmaEditor**: Block „E-Mail-Absender“ entfällt, stattdessen ein Satz mit Link
  zu Einstellungen → E-Mail.
- **Benutzer-Editor**: Feld heißt „Eigenes Postfach“.
- **E-Mail-Center**: kleines Postfach-Schild je Mail in Liste und Detail
  (z. B. „info@“ – lokaler Teil der Adresse, voller Wert im Tooltip).
- **Schreiben-Fenster**:
  - Neue Mail: Auswahl „Senden von“ (aus `absender-postfaecher`), vorbelegt.
  - Antworten / Allen antworten / Weiterleiten: Zeile „Von: info@…“ als Text,
    keine Auswahl.
  - Schalter „Einzeln verschicken – jeder sieht nur sich selbst“; Hinweis ab
    4 Empfängern; bei „an“ wird CC ausgeblendet und geleert; bei mehr als 50
    Empfängern Senden gesperrt mit Hinweis.
  - Ergebnis Einzelversand: „28 verschickt, 2 nicht: …“ mit Adressen.

---

## Etappe 2 – Sichtbarkeit (Überblick, eigene Spec-Ergänzung vor dem Bau)

- `email_absender.sichtbar_fuer_alle` (Default TRUE nach Umzug), Tabellen
  `email_absender_abteilung`, `email_absender_benutzer`.
- Sichtbar für Benutzer U: eigenes Postfach ∪ `sichtbar_fuer_alle` ∪ Abteilung von
  U (über `Mitarbeiter.abteilungen`) ∪ U direkt. Admin: alles.
- Durchgesetzt im Backend für alle E-Mail-Center-Endpoints (Listen, Suche,
  Zähler/`stats`, Detail, Verlauf, Anhänge, Massenaktionen) → fremde Mail 404.
  Projekt-/Anfrage-/Lieferanten-Reiter **nicht** gefiltert.
- `absender-postfaecher` und Versand nur aus sichtbaren Postfächern (403 sonst).
- UI: im Postfach-Dialog „Wer darf es sehen?“ – Alle / Nur bestimmte (Häkchen
  Abteilungen + Benutzer).

---

## Tests (verbindlich, beide Etappen)

**Branch-Coverage ≥ 80 %** für jede neue und jede wesentlich geänderte Klasse
bzw. Datei (Backend: JaCoCo, Frontend: `vitest --coverage`). Gemessen und im
Abschlussbericht je Datei ausgewiesen.

- **Backend Unit (JUnit 5 + Mockito):** `EmailAbsenderService`/Postfach-Regeln,
  `PostfachVersandService` (alle Zweige von `bestimmePostfach`/`antwortPostfach`,
  Einzelversand inkl. Teil- und Totalfehler), `PostfachUmzugRunner`
  (idempotent, ohne Schlüssel, ohne Altkonto), `SystemSettingsService`-Rückfall,
  Import-Schleife (ein Postfach kaputt, Dedupe → Zuordnung ergänzt).
- **Backend Integration:**
  - MockMvc + Spring Security: `/api/postfaecher` 401 / 403 (USER) / CSRF 403 /
    404 / 400-Fälle; XSS- und SQL-Injection-Strings in Adresse/Anzeigename → 400
    bzw. unschädlich gespeichert.
  - **GreenMail** (neue Test-Dependency `com.icegreen:greenmail-junit5`):
    echter IMAP/SMTP in-memory – zwei Postfächer abrufen, gleiche Mail an beide →
    eine `Email`, zwei Zuordnungen; Versand aus gewähltem Postfach landet mit
    richtigem From und Gesendet-Kopie im richtigen Postfach; Antwort auf
    info@-Mail geht von info@; Einzelversand → N Mails mit je genau einem
    Empfänger, keiner sieht die anderen.
  - `@DataJpaTest` für neue Repository-Queries.
  - Flyway V407 + Umzug gegen den lokalen MySQL-Klon (Docker, Flyway an) einmal
    manuell geprüft (H2-Tests laufen ohne Flyway).
- **Frontend Unit (Vitest + Testing Library):** Postfach-Liste/Dialog
  (Vorbelegung, Passwort leer = unverändert, Fehlertexte), „Senden von“
  (Vorbelegung, fest bei Antwort/Weiterleiten), Einzelversand-Schalter (CC weg,
  Grenze 50, Ergebnisanzeige), Postfach-Schild.
- **E2E (Playwright, `react-pc-frontend/e2e/`, `page.route`-Mocks):**
  `email-postfaecher-einstellungen.spec.ts` (anlegen, testen, Hauptpostfach
  wechseln), `email-absender-postfach.spec.ts` (neue Mail mit Auswahl, Antwort mit
  festem Absender, Weiterleiten), `email-einzelversand.spec.ts` (30 Empfänger,
  Teilfehler-Meldung). Plus `playwright-design-pruefung` (MacBook 14" + großer
  Monitor).
- JaCoCo-Plugin wird im `pom.xml` ergänzt (Report bei `mvn verify`), ohne
  projektweite Schwelle – die 80 % gelten für die betroffenen Klassen.

## Umsetzungsnotizen Etappe 1 (Abweichungen vom Entwurf)

- `email_postfach_zuordnung` hat eine eigene `id` (AUTO_INCREMENT) plus Unique-Key
  (`email_id`, `postfach_id`) statt eines zusammengesetzten Primärschlüssels – einfacher in JPA.
- Einzelversand: Jede Mail geht über eine eigene SMTP-Anmeldung desselben Postfachs
  (vorhandener `EmailService`), nacheinander. Eine gemeinsame Verbindung hätte einen Umbau
  des Mail-Kerns bedeutet; bei höchstens 50 Empfängern unkritisch. Schlägt die Anmeldung
  fehl, wird abgebrochen und alle restlichen Empfänger als fehlgeschlagen gemeldet.
- Die Konto-Getter des `SystemSettingsService` (`getSmtpHost`, `getImapUsername`,
  `getStandardMailKonto`, `getDokumentMailKonto` …) lesen zuerst das Hauptpostfach bzw. das
  Rechnungs-Postfach und fallen sonst auf die Einstellungen zurück. Dadurch laufen
  Mahnlauf, Anfrage-Bestätigung, Auto-AB, Abwesenheitsnotiz usw. ohne Einzelumbau über die
  Postfächer.
- Bleiben bestehen (Ersteinrichtung `FirstLoginSetupPage`/`SystemSetupConfigurator` und
  Bestellungs-Editor nutzen sie): `/api/settings/smtp`, `/smtp/test`, `/imap`, `/imap/test`,
  `/email-account`, `/api/email/from-addresses`. Speichern in der Ersteinrichtung gleicht
  genau den gespeicherten Teil (Versand / Abruf / Anmeldung) ins Hauptpostfach ab.
- Entfernt: `/api/settings/mail-from`, `/api/settings/dokument-mail*`,
  `/api/firma/email-absender*`, `/api/emails/from-addresses`.
- `GET /api/emails/absender-postfaecher` ermittelt das eigene Postfach aus der Anmeldung,
  nicht aus einem Request-Parameter.
- Weiterleitung einer unbekannten Mail (`weitergeleitetVonEmailId`) → 404.
- Gibt es noch kein Hauptpostfach, wird das erste aktive gespeicherte Postfach automatisch
  Hauptpostfach.

- Nach Review (2 Runden):
  - Das gespeicherte Passwort wird nur an den gespeicherten Server und Anmeldenamen geschickt
    (Verbindungstest und Speichern). Wer Server oder Anmeldenamen ändert, gibt es neu ein (400).
    Die Ersteinrichtung verwirft das Hauptpostfach-Passwort, wenn sie ohne neues Passwort den
    Server ändert.
  - `GET /api/settings` zeigt Passwörter nur noch als „gesetzt“.
  - Der Nachtrag beim Start legt Ausgangsmails ohne Postfach zuerst in das Postfach ihrer
    Absender-Adresse, erst den Rest ins Hauptpostfach.
  - `antwortPostfach` steht nur in Detail-, Verlauf- und Send-Antworten (in Listen fehlt das Feld,
    sonst eine Abfrage pro Zeile). Ausgeschaltetes Postfach → Antwort übers Hauptpostfach.
  - Neuer `AusgangsmailService` (freigegeben): speichert jede gesendete Mail in eigener
    Transaktion. Einzelversand: verschickt, aber nicht gespeichert → `nichtGespeichert` in der
    Antwort; das Frontend warnt „bitte NICHT erneut senden“.

## Etappe 2 – offene Punkte aus Etappe 1 (Review)

- **Sicherheit vor Sichtbarkeit:** Dedupe ergänzt die Postfach-Zuordnung nur über die
  Message-ID. Ein Externer könnte eine fremde Message-ID an max@ schicken und so die
  Original-Mail „in max@“ legen. Bevor Sichtbarkeit an der Zuordnung hängt: zusätzlich Absender,
  Datum, Betreff vergleichen oder nur für eigene Gesendet-Ordner ergänzen.
- Sichtbarkeitsprüfung für `postfachId`, `weitergeleitetVonEmailId` und `/absender-postfaecher`.
- `weitergeleitetVonEmailId` im Entwurf speichern (Weiterleitungs-Entwurf behält festen Absender).
- `angemeldeterBenutzer` nicht auf die vom Client geschickte `frontendUserId` zurückfallen lassen.
- Bean-Validation (`@Valid`, `@Size`, `@Email`) für Postfach-Requests; eigene Exception statt
  `IllegalArgumentException` im `PostfachController`.
- DB-Absicherung „genau ein Hauptpostfach“.
- Konto-Getter im `SystemSettingsService`: Zugang einmal holen statt je Getter (Abfrage +
  Entschlüsselung); Test-Konstruktoren dort durch gemockten `ObjectProvider` ersetzen.
- `sendEmail` hält eine äußere Transaktion, der `AusgangsmailService` eine zweite (zwei
  Verbindungen je Versand). Bei Bedarf äußere Transaktion auf das Laden der Dokumente beschränken.
- Waisen-Dateien, wenn das Speichern nach dem Kopieren eines Anhangs scheitert
  (Muster: `ProjektEmailArchivService.registriereRollbackBereinigung`).
- `ordneAusgangsmailsNachAbsenderZu` erkennt nur nackte Absender-Adressen, nicht `Name <adresse>`.
- JaCoCo auf 0.8.13 (offizielle Java-23-Unterstützung).

## Freigaben laut CLAUDE.md (Auslagerung)

1. Versandlogik aus `UnifiedEmailController` (`/send`, `/reply`) in
   `PostfachVersandService` auslagern – sonst wächst der 2 260-Zeilen-Controller
   weiter und Einzelversand/Postfach-Wahl wären nicht sauber testbar.
2. Frontend: eigene Komponenten `PostfachSettings` (Liste + Dialog) und
   `AbsenderPostfachAuswahl` statt Ausbau der 950-/1 700-Zeilen-Dateien.
3. (Review Runde 1, freigegeben) Speichern gesendeter Mails in `AusgangsmailService`.

## Nicht Teil dieses Vorhabens

Persönliche Anrede je Empfänger beim Einzelversand, Ordnerverwaltung pro
Postfach, Postfach-Auswahl/Filter für Admin, Gelesen-Status pro Benutzer,
SMTP-Port 587/STARTTLS.
