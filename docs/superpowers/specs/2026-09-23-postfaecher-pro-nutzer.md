# Spec: E-Mail-Postfächer pro Nutzer

Issue: #168

Datum: 23.09.2026

Status: Grundlage ist ein mit dem Nutzer abgeschlossenes Brainstorming.
Diese Spec überführt die dort getroffenen Entscheidungen in eine
Umsetzungsgrundlage für den Grobplan und erfindet keine neuen
Design-Entscheidungen.

**Verbindliche Voraussetzung:** Diese Spec setzt voraus, dass der Branch
`origin/codex/beschaffung-konzept` (Einkaufs-Feature) vorher auf `main`
gemergt ist. Dort werden `MailSecretService` (AES-GCM-Verschlüsselung von
Mailzugangsdaten, Schlüssel `mail.credentials.encryption-key`),
`KontoMailTransport` (konto-bewusster SMTP-Versand und IMAP-Verbindungstest),
`MailkontoService` (löst eine `kontoId` in Zugangsdaten auf) sowie die
Migration `V383__einkauf_mailkonto.sql` (Tabelle `einkauf_mailkonto`)
eingeführt. Das Postfach-Feature baut auf diesen Bausteinen auf und ersetzt
sie nicht neu, sondern generalisiert sie. Ebenfalls aus diesem Branch bereits
vorhanden und für diese Spec zentral: `email.konto_id` (Spalte, Werte bisher
`HAUPT`/`DOKUMENTE`/`EINKAUF`) mit Unique-Index `(konto_id, message_id)`
(Migration `V384__email_kontobezug_importidentitaet.sql`) sowie
`EmailImportService.doImport(String kontoId)`, das bereits pro Konto-ID über
`MailkontoService.resolve(kontoId)` importiert. Dieses Konto-Modell mit drei
festen String-IDs wird durch das echte Postfach-Modell dieser Spec abgelöst.

## Ziel

Die Firma stellt auf ihre eigene Domain um; die Postfächer liegen bereits
bei Hetzner (`mail.your-server.de`, IMAP 993, SMTP 465). Heute nutzt jeder
Nutzer im ERP ein einziges, fest verdrahtetes IMAP/SMTP-Konto
(`imap.*`/`smtp.*` in `system_setting`, aktuell t-online). Ziel dieser Spec
ist ein Postfach-Modell, das mehrere echte E-Mail-Adressen abbildet:

- **Persönliche Postfächer** (z.B. `max@bauschlosserei-kuhn.de`), die genau
  einem Nutzer gehören und nur dieser sieht.
- **Allgemeine (General-)Postfächer** (z.B. `info@…`, `einkauf@…`), auf die
  der Admin ausgewählten Nutzern Zugriff gibt — Verhalten wie eine
  Outlook-Shared-Mailbox (gemeinsamer Posteingang, kein Gelesen-Status pro
  Nutzer).

Jeder Nutzer soll im E-Mail-Center nur die Postfächer sehen (und daraus
senden), auf die er Zugriff hat. Der Admin verwaltet Zugänge zentral, ohne
selbst automatisch fremde persönliche Post lesen zu können (DSGVO). Die
bestehenden Sonderzwecke (Website-Anfrage-Bestätigung, Geschäftsdokumente,
Einkauf) werden über einen neuen, einstellbaren Zweck-Mechanismus an
konkrete Postfächer gebunden, statt wie heute an fest verdrahtete Konten
(`smtp.dokumente.*`, `einkauf_mailkonto`) bzw. an `getMailFromAddress()`.

Zielgruppe: alle Büro-Nutzer des PC-Frontends (`react-pc-frontend`), die im
E-Mail-Center lesen und schreiben, sowie der Admin, der Postfächer anlegt
und Zugriffe vergibt.

## Nicht-Ziele

- Mobile App (`react-zeiterfassung`) — nicht betroffen, hat kein
  E-Mail-Center.
- Serverseitige Abwesenheit per Sieve — Abwesenheit bleibt eine
  Anwendungsfunktion des ERP (Import-Zeitpunkt-basiert, wie heute), kein
  Mailserver-Feature.
- Gelesen-Status pro Nutzer in Generalpostfächern. Verhalten wie eine
  Outlook-Shared-Mailbox: Gelesen/Ungelesen ist Eigenschaft der Mail, nicht
  des lesenden Nutzers.
- Eine Signaturbibliothek pro Nutzer. Signaturen (`EmailSignature`) bleiben
  ein globales Verzeichnis wie heute; ein Postfach verweist nur auf eine
  davon als Standard-Signatur.
- Das Anlegen der eigentlichen Hetzner-Postfächer (Adresse, Passwort auf
  Serverseite) — das ist eine externe Administrationsaufgabe. Diese Spec
  bildet nur die Anbindung im ERP ab; nach der t-online-Migration legt der
  Admin die Hetzner-Postfächer selbst im neuen Admin-UI an.
- Eine neue serverseitige Sieve-/Weiterleitungsregel für die alte
  t-online-Adresse. Ihr Fortbestand (weil sie noch auf Flyern steht) wird
  ausschließlich über "Abruf aktiv" im ERP gesteuert, nicht über
  Mailserver-Konfiguration.

## Architektur/Ablauf

### 1. Datenmodell

Neue Tabelle `postfach` (löst die drei festen String-IDs `HAUPT`/
`DOKUMENTE`/`EINKAUF` aus dem Einkaufs-Branch ab):

- `id` (PK, z.B. `BIGINT AUTO_INCREMENT` — die bisherigen kurzen
  String-IDs wie `"EINKAUF"` waren nur für drei feste Konten tragbar, bei
  beliebig vielen Postfächern braucht es einen echten Surrogatschlüssel).
- `adresse` (E-Mail-Adresse, unique).
- `anzeigename`.
- `art` (`PERSOENLICH` | `ALLGEMEIN`).
- `besitzer_id` (FK auf `FrontendUserProfile`, NULL bei `ALLGEMEIN`,
  Pflicht bei `PERSOENLICH`). Fachliche Regel, in der Spec vom Nutzer
  festgelegt: **maximal ein persönliches Postfach pro Nutzer** — im
  Grobplan als DB-Constraint (partial unique index auf `besitzer_id` wo
  `art = PERSOENLICH`, sofern die Ziel-DB das unterstützt) oder als
  Service-Prüfung umzusetzen.
- IMAP: `imap_host`, `imap_port`, `imap_username`,
  `imap_password_ciphertext`, `imap_tls` — **alle optional/nullable**,
  denn IMAP-Abruf ist laut Entscheidung 1 ausdrücklich abschaltbar (Postfach
  nur zum Senden, ohne Abruf).
- SMTP: `smtp_host`, `smtp_port`, `smtp_username`,
  `smtp_password_ciphertext`, `smtp_tls` — Pflicht, jedes Postfach kann
  senden.
- Ordner: `ordner_posteingang`, `ordner_gesendet`,
  plus zusätzliche Import-Ordner (eigene Tabelle oder JSON-Liste — analog
  zur heutigen `INCOMING_FOLDERS`-Liste in `EmailImportService`, die für
  das Postfach "Altes Postfach (t-online)" 1:1 übernommen wird, siehe
  Abschnitt 5).
- `abruf_aktiv` (boolean) — steuert, ob `EmailImportService` dieses
  Postfach überhaupt anfasst; unabhängig von IMAP-Konfiguration vorhanden,
  damit der Admin den Abruf später abschalten kann, ohne die
  IMAP-Zugangsdaten zu löschen.
- `standard_signatur_id` (FK auf `EmailSignature`, optional).
- `letzter_abruf`, `letzter_fehler`.
- `version` (optimistisches Locking, wie im bestehenden Muster
  `EinkaufMailkonto`/`MailkontoService.speichern()`).

Neue Tabelle `postfach_zugriff` (`postfach_id`, `frontend_user_id`) — nur
für `ALLGEMEIN`-Postfächer relevant; bei `PERSOENLICH` ist der Besitzer
implizit der einzige Zugriffsberechtigte, es entsteht kein Eintrag.

Passwörter werden wie im Einkaufs-Branch über `MailSecretService`
(AES-GCM, Schlüssel `mail.credentials.encryption-key`) verschlüsselt
gespeichert und nie im Klartext zurückgeliefert — die Antwort-DTOs liefern
nur ein `gesetzt`-Flag (Muster aus `MailkontoService.Response`, Felder
`smtpPasswordGesetzt`/`imapPasswordGesetzt`).

Änderung an `email`: `konto_id` (VARCHAR(16)) wird durch `postfach_id`
(FK auf `postfach`, `BIGINT`) ersetzt. Der bestehende Unique-Index
`idx_email_konto_message_id (konto_id, message_id)` wird analog zu
`(postfach_id, message_id)`. Das setzt exakt die bereits im Einkaufs-Branch
gebaute Logik fort (Migration `V384`), nur mit echtem FK statt fixer
String-Enum. `EmailImportIdentitaet` (Tabelle `email_import_identitaet`,
Unique `(konto_id, folder, uidvalidity, uid)`) wird ebenso auf
`postfach_id` umgestellt.

Änderung an `out_of_office_schedule`: neue Spalte `postfach_id` (FK,
Pflicht). Ersetzt die bisherige firmenweite Gültigkeit — jeder Plan gilt
für genau ein Postfach.

Neue Tabelle `postfach_versandzweck` (`zweck` als Enum-Spalte, unique;
`postfach_id`, `signatur_id`) für die in Entscheidung 9 beschriebenen
Versandzwecke `WEBSITE_ANFRAGE`, `GESCHAEFTSDOKUMENTE`, `EINKAUF`.

`EmailAbsender` (Tabelle/Entity, `EmailAbsenderRepository`,
`EmailAbsenderService`, `FrontendUserProfile.emailAbsender`, DTO
`EmailAbsenderDto`) entfällt vollständig — ersetzt durch das persönliche
Postfach des Nutzers (Entscheidung 10). Migrationsschritt und betroffene
Stellen siehe Abschnitt 6.

### 2. Sichtbarkeit und Berechtigungen

- Persönliches Postfach: sichtbar und nutzbar ausschließlich für
  `besitzer_id`.
- Generalpostfach: sichtbar und nutzbar für alle Nutzer mit Eintrag in
  `postfach_zugriff`.
- Admin verwaltet **alle** Postfächer (anlegen, Zugangsdaten ändern,
  Zugriffsrechte anderer Nutzer pflegen), liest aber **keine** fremden
  persönlichen Postfächer automatisch — er hat auf ein fremdes
  `PERSOENLICH`-Postfach keinen impliziten Lesezugriff, nur auf
  `ALLGEMEIN`-Postfächer, sofern er sich selbst in `postfach_zugriff`
  einträgt. Diese Berechtigungsprüfung ist rein fachlicher Natur und muss
  im Service (nicht nur im Frontend) durchgesetzt werden — analog zum
  bestehenden Muster `EinkaufBerechtigungService.verlangeAktivenAdmin(...)`
  in `MailkontoService`.
- Fremde Mail im Detail-Aufruf → 404 (kein 403, um keine Existenz
  preiszugeben — Muster an bestehenden Projekt-/Dokument-Endpunkten prüfen,
  die dieselbe 404-statt-403-Konvention verwenden).

### 3. E-Mail-Center: Ansicht und Filter

Die Ordnerstruktur im E-Mail-Center bleibt exakt wie heute (keine
UI-Umstrukturierung). Neu:

- Mails **aller sichtbaren Postfächer** erscheinen gemischt in der Liste,
  jede mit einem Postfach-Etikett (Adresse/Anzeigename als Badge).
- Filterleiste oben: "Alle Postfächer" / "Mein Postfach" / je ein Eintrag
  pro sichtbarem Generalpostfach (z.B. "info@…").
- Zähler, Suche, Ungelesen-Badge und Benachrichtigungsglocke filtern nach
  demselben Sichtbarkeits- und Filterprinzip.
- **Wichtige Abgrenzung (Entscheidung 5):** Mails, die einem
  Projekt/einer Anfrage/einem Lieferanten/dem Steuerberater zugeordnet
  sind, bleiben in **diesen** fachlichen Ansichten für jeden mit Zugriff
  auf das jeweilige Projekt sichtbar — unabhängig vom Postfach-Zugriff.
  Nur das E-Mail-Center selbst (die generische Postfach-Ansicht) filtert
  nach Postfach-Berechtigung. Diese Trennung betrifft
  `UnifiedEmailController` und die zugehörigen Repository-Queries
  (`EmailRepository`) — welche Endpunkte/Queries bereits projektbezogen
  filtern und welche zusätzlich die neue Postfach-Sicht brauchen, ist im
  Grobplan anhand von `UnifiedEmailController.java` konkret zu verifizieren.

### 4. Deduplizierung bei Mehrfachzustellung

Da dieselbe Nachricht an `info@…` **und** per CC an `max@…` in zwei
verschiedenen Postfächern landet (jedes Postfach importiert unabhängig),
entstehen zwei `email`-Zeilen mit derselben `message_id`, aber
unterschiedlicher `postfach_id` — das ist durch den Unique-Index
`(postfach_id, message_id)` explizit erlaubt und gewollt (Entscheidung 6).

Für die generische Postfach-Ansicht im E-Mail-Center ist das korrekt (zwei
Kopien, zwei Postfach-Etiketten). Für Projekt-/Anfrage-/Lieferanten-/
Steuerberater-Ansichten würde das Duplikate erzeugen — dort muss nach
`message_id` dedupliziert werden, bevor die Liste angezeigt wird. Diese
Dedup-Logik ist neu zu bauen bzw. an den entsprechenden
Repository-Queries/Service-Methoden zu ergänzen; welche das konkret sind
(vermutlich zusätzlich zu `UnifiedEmailController`), ist im Grobplan gegen
`EmailRepository` zu verifizieren.

### 5. Import (IMAP-Abruf)

`EmailImportService.importNewEmails()` iteriert heute über eine feste
Liste dreier Konto-IDs (`List.of("EINKAUF", "HAUPT", "DOKUMENTE")`,
`EmailImportService.java:146`). Das wird durch eine Iteration über **alle**
Postfächer mit `abruf_aktiv = true` ersetzt (Query auf die neue
`postfach`-Tabelle statt fester Liste).

`doImport(String kontoId)` wird zu `doImport(Long postfachId)`
(bzw. äquivalent) und löst die Zugangsdaten über eine generalisierte
Variante von `MailkontoService.resolve(...)` auf, die nicht mehr zwischen
drei Spezialfällen (`HAUPT`/`DOKUMENTE`/`EINKAUF`) unterscheidet, sondern
generisch aus der `postfach`-Tabelle liest (inkl. Entschlüsselung über
`MailSecretService`, wie im Einkaufs-Branch für `"EINKAUF"` bereits
vorgemacht in `MailkontoService.ladeEinkaufZugang()`).

Fehlerisolation bleibt wie heute: Ein Fehler bei einem Postfach (Connect,
Auth, IMAP-Fehler) wird geloggt und **blockiert die anderen Postfächer
nicht** — dieses Verhalten ist in `importNewEmails()` bereits per
try/catch pro Konto umgesetzt (`EmailImportService.java:147-160`) und wird
für die dynamische Postfach-Liste 1:1 fortgeführt. Zusätzlich wird
`letzter_fehler`/`letzter_abruf` je Postfach in der `postfach`-Zeile
aktualisiert (analog zu den bereits vorhandenen Feldern in
`EinkaufMailkonto`), damit der Admin den Status im neuen Admin-UI sieht.

Ordnerzuordnung: Bei den drei heutigen Sonderfällen ist die Ordnerliste
für `HAUPT` hart codiert (`INCOMING_FOLDERS`/`OUTGOING_FOLDERS`,
`EmailImportService.java:106-117`), bei `EINKAUF`/`DOKUMENTE` kommt genau
ein Posteingangs- und ein Gesendet-Ordner aus der Konfiguration. Im neuen
Modell hat jedes Postfach eine konfigurierbare Ordnerzuordnung
(Posteingang, Gesendet, zusätzliche Import-Ordner) — die heutige feste
`INCOMING_FOLDERS`-Liste wird beim Migrationsschritt für das "Alte
Postfach (t-online)" 1:1 als dessen Import-Ordner-Liste übernommen (siehe
Abschnitt 6), bleibt aber für neu angelegte Hetzner-Postfächer frei
konfigurierbar (Standard vermutlich nur Posteingang + Gesendet).

### 6. Migration der Altlast t-online

Ein Java-Migrator (`ApplicationRunner`/`CommandLineRunner`, analog zum
bestehenden Muster `EmailThreadBackfillRunner`), der beim Start läuft und
**idempotent** ist (muss beim zweiten Start erkennen, dass die Migration
bereits gelaufen ist und nichts doppelt anlegen — wichtig, weil Passwörter
beim ersten Lauf verschlüsselt und aus `system_setting` entfernt werden,
ein zweiter Lauf also keine Klartext-Quelle mehr vorfindet):

1. Legt aus `imap.*`/`smtp.*` (`SystemSettingsService`) ein
   Generalpostfach **"Altes Postfach (t-online)"** an (`art = ALLGEMEIN`).
2. Gewährt **allen bestehenden Nutzern** Zugriff (`postfach_zugriff`-Zeile
   je `FrontendUserProfile`).
3. Übernimmt die bisherige feste Ordnerliste (`INCOMING_FOLDERS`,
   `OUTGOING_FOLDERS`) als dessen Import-Ordner.
4. **`abruf_aktiv = true` bleibt bestehen** — ausdrückliche Vorgabe, weil
   die t-online-Adresse noch auf Flyern steht und nur schrittweise
   ausläuft. Der Admin schaltet den Abruf später selbst über den Schalter
   "Abruf aktiv" im neuen Admin-UI ab.
5. Leert danach die Klartext-Passwörter in `system_setting`
   (`smtp.password`, `imap.password` u.ä.) — die Zugangsdaten leben ab
   sofort ausschließlich verschlüsselt in `postfach`.
6. Alle bestehenden `email`-Zeilen und `out_of_office_schedule`-Zeilen
   erhalten die `postfach_id` des neuen "Alten Postfachs".
7. Falls `smtp.dokumente.*` aktiv war
   (`settings.nutztDokumentMailKonto()`): legt ein weiteres Postfach ohne
   Abruf (`abruf_aktiv = false`, IMAP optional/leer) mit Zweck
   `GESCHAEFTSDOKUMENTE` an — ersetzt den heutigen Sonderfall
   `SystemSettingsService.getDokumentMailKonto()`.
8. `einkauf_mailkonto` (aus dem vorausgesetzten Einkaufs-Branch, falls zu
   diesem Zeitpunkt bereits befüllt) wird zu einem Postfach mit Zweck
   `EINKAUF` überführt — der Nutzer möchte hierfür ein eigenes
   Generalpostfach `einkauf@…`, das nach der t-online-Migration ggf. neu
   auf die Hetzner-Adresse umgestellt wird.

Hinweis (kein Extra-Feature, nur Nutzungshinweis für die Doku/Schulung):
Der Admin kann auf dem "Alten Postfach" ganz regulär über die neue
Abwesenheits-Einstellung (Abschnitt 8) einen Hinweistext wie "Wir haben
eine neue Adresse …" schalten, um Absender auf die neue Adresse
hinzuweisen — das ist keine gesonderte Funktion, sondern folgt derselben
Postfach-Abwesenheit wie jedes andere Generalpostfach.

`EmailAbsender`/`FrontendUserProfile.emailAbsender` entfallen im gleichen
Zug: Der Migrator legt für Nutzer, die bereits einen `EmailAbsender`
hatten, **kein** automatisches persönliches Postfach an (dafür fehlen
IMAP/SMTP-Zugangsdaten je Nutzer) — persönliche Hetzner-Postfächer legt
laut Entscheidung 10 der Admin danach selbst im neuen Admin-UI an. Der
Endpunkt `/api/email/from-addresses` (liefert heute `EmailAbsender`-Werte,
Fundstelle in `UnifiedEmailController`/`EmailController`) wird durch einen
neuen Endpunkt ersetzt, der die für den aktuellen Nutzer sendefähigen
Postfächer liefert.

### 7. Verfassen und Senden

- Verfassen einer neuen Mail schickt `postfachId` statt einer freien
  Absenderadresse. Der Server prüft Besitz (persönlich) bzw.
  `postfach_zugriff` (allgemein), bevor gesendet wird.
- Versand läuft über `KontoMailTransport` (aus dem Einkaufs-Branch) mit dem
  `KontoZugang`/Äquivalent des gewählten Postfachs — dieselbe
  SMTP-Sende-Logik, nur mit generischem Postfach statt der drei
  Spezialfälle.
- Gesendet-Kopie wird per IMAP-Append (bestehender `ImapAppendService`,
  siehe `OutOfOfficeResponder`-Import) in den Gesendet-Ordner **desselben**
  Postfachs geschrieben.
- "Von"-Dropdown-Verhalten:
  - Neue Mail: persönliches Postfach des angemeldeten Nutzers ist
    vorausgewählt.
  - Antwort/Weiterleitung: Postfach der Ursprungsmail ist vorausgewählt,
    **sofern** der Nutzer darauf Zugriff hat — sonst Fallback auf sein
    persönliches Postfach.
- Beim Wechsel des Absenders im Dropdown wird die Standard-Signatur des
  neu gewählten Postfachs automatisch gesetzt (ersetzt den bisherigen
  Signaturtext im Editor).

### 8. Abwesenheit pro Postfach

`out_of_office_schedule` erhält `postfach_id` (Pflicht). Der bestehende
`OutOfOfficeResponder` antwortet nur noch für das Postfach, in dem die
eingehende Mail tatsächlich ankam (nicht mehr firmenweit), und versendet
die Antwort über dessen SMTP (`KontoMailTransport`/generalisierter
`MailkontoService`-Nachfolger). Loop-Schutz und "einmal pro Absender pro
Plan" (heutige Logik über `OooReplyLogRepository`,
`OutOfOfficeResponder.handleIncomingEmail`) bleiben unverändert bestehen,
nur zusätzlich auf `postfach_id` skopiert.

Bedienung:

- Jeder Nutzer verwaltet seine **eigene** Abwesenheit für sein
  persönliches Postfach unter "Einstellungen → Meine Abwesenheit"
  (Zeitraum, Betreff, Text, Signatur — gleiche Felder wie heute in
  `OutOfOfficeSchedule`: `startAt`, `endAt`, `subjectTemplate`,
  `bodyTemplate`, `signature`).
- Abwesenheit für Generalpostfächer (z.B. Betriebsurlaub auf `info@…`)
  darf **nur der Admin** anlegen/ändern.

Diese neue Rechteprüfung (Eigentümer bei `PERSOENLICH`, Admin bei
`ALLGEMEIN`) ist im bislang firmenweiten `OutOfOfficeController` neu zu
verankern.

### 9. Versandzwecke (ersetzt fest verdrahtete Sonderkonten)

Neue Tabelle `postfach_versandzweck` (Zweck → Postfach + Signatur), alles
einstellbar im Admin-Bereich "Automatische Mails":

| Zweck | Ersetzt heute | Vorgabe |
| --- | --- | --- |
| `WEBSITE_ANFRAGE` | `AnfrageBestaetigungVersandService` nutzt `systemSettingsService.getMailFromAddress()` + Systemsignatur | Nutzerwunsch: standardmäßig von `info@…`, Signatur im Admin-Bereich wählbar |
| `GESCHAEFTSDOKUMENTE` | `smtp.dokumente.*` (V352, `SystemSettingsService.getDokumentMailKonto()`) — Rechnungen, Mahnungen, Auftrags-/Anfragebestätigungen | Ersetzt vollständig, kein Parallelbetrieb |
| `EINKAUF` | `einkauf_mailkonto` (Einkaufs-Branch) — Einkaufsanfragen an Lieferanten | Nutzer will eigenes Generalpostfach `einkauf@…` |

Fehlerfall (Entscheidung 9, ausdrücklich keine stille Ausweichlösung): Ist
für einen Zweck kein gültiges (aktives, sendefähiges) Postfach hinterlegt,
erscheint eine **sichtbare Warnung** in den Einstellungen ("Automatische
Mails") **und** ein Log-Eintrag — kein automatischer Rückfall auf ein
fremdes Konto, keine stillschweigend fehlschlagende Mail.

Betroffene bestehende Stelle: `SystemSettingsController.java:180` liefert
`settingsService.getMailFromAddress()` an einen Frontend-Konsumenten — im
Grobplan zu klären, ob dieser Aufrufer künftig den `WEBSITE_ANFRAGE`-Zweck
abfragt oder komplett entfällt (siehe Offene Punkte).

### 10. Admin-UI "Einstellungen → E-Mail-Postfächer"

- Liste aller Postfächer mit Art, Adresse, Anzeigename, Abruf-Status,
  letztem Fehler.
- Anlegen/Bearbeiten-Formular: Adresse, Anzeigename, Art
  (persönlich/allgemein), bei persönlich Besitzer-Auswahl, bei allgemein
  Berechtigten-Häkchenliste (alle `FrontendUserProfile`), IMAP- und
  SMTP-Zugangsdaten (Host/Port/Benutzer/Passwort/TLS), Ordnerzuordnung,
  Standard-Signatur, Schalter "Abruf aktiv".
- Knopf **"Hetzner-Vorlage"**: befüllt Host/Port/TLS-Felder vorab mit
  `mail.your-server.de`, IMAP-Port 993, SMTP-Port 465, TLS — spart dem
  Admin das wiederholte manuelle Eintippen für jedes neue Postfach.
- Knopf **"Verbindung testen"**: prüft SMTP- und IMAP-Verbindung ohne
  Versand/Import, analog zu `KontoMailTransport.pruefeVerbindung(...)` im
  Einkaufs-Branch (dort bereits für das Einkaufskonto gebaut, hier
  generisch für jedes Postfach wiederzuverwenden).
- Passwort wird nach dem Speichern **nie zurückgeliefert**, nur als
  "gesetzt"-Flag angezeigt (bestehendes Muster aus
  `MailkontoService.Response`/`Verschluesselung`-Feldern).
- Fehlt der Verschlüsselungsschlüssel (`mail.credentials.encryption-key`),
  zeigt das UI einen klaren Hinweis, Speichern ist gesperrt — analog zu
  `MailSecretService.ensureConfigured()`, das bereits eine
  `IllegalStateException` mit verständlichem Text wirft.

## Betroffene Bereiche

**Backend** (`src/main/java/org/example/kalkulationsprogramm/`):

- Neu: Entity `Postfach`, `PostfachZugriff`, `PostfachVersandzweck`,
  zugehörige Repositories, `PostfachService` (Nachfolger von
  `MailkontoService`, generisch statt drei Spezialfälle), Migrator
  (Nachfolger-Baustein von `EmailThreadBackfillRunner`), Migrationen
  (`postfach`, `postfach_zugriff`, `postfach_versandzweck`, Umbau `email`,
  `email_import_identitaet`, `out_of_office_schedule`).
- Geändert: `EmailImportService` (dynamische Postfach-Liste statt fester
  drei IDs, Abschnitt 5), `OutOfOfficeResponder` + `OutOfOfficeController`
  + `OutOfOfficeSchedule` (Postfach-Bezug, Rechteprüfung, Abschnitt 8),
  `AnfrageBestaetigungVersandService` (Versandzweck `WEBSITE_ANFRAGE`
  statt `getMailFromAddress()`), `SystemSettingsService` (Wegfall der
  `imap.*`/`smtp.*`/`smtp.dokumente.*`-Felder nach Migration, siehe
  Abschnitt 6 und Offene Punkte zu `SystemSettingsController.java:180`),
  `UnifiedEmailController`/`EmailController` (Postfach-Filter, Wegfall
  `/api/email/from-addresses`, Ersatz-Endpunkt sendefähiger Postfächer),
  `EmailRepository` (Queries auf `postfach_id`, Dedup nach `message_id` in
  fachlichen Ansichten, Abschnitt 4).
- Entfernt: `EmailAbsender` (Entity), `EmailAbsenderRepository`,
  `EmailAbsenderService`, `EmailAbsenderDto`,
  `FrontendUserProfile.emailAbsender` sowie deren Aufrufstellen in
  `FrontendUserProfileService`, `FirmaController`, `EmailController`,
  `FrontendUserController`.
- `MailkontoService`/`KontoMailTransport`/`MailSecretService` aus dem
  Einkaufs-Branch werden wiederverwendet bzw. zum generischen
  `PostfachService` weiterentwickelt, nicht dupliziert.

**Frontend** (`react-pc-frontend/`, PC-Frontend — Mobile nicht betroffen):

- Neu: Admin-Seite "Einstellungen → E-Mail-Postfächer" (Liste,
  Anlegen/Bearbeiten, Hetzner-Vorlage, Verbindung testen), Seite/Abschnitt
  "Einstellungen → Meine Abwesenheit" (persönlich) und Erweiterung der
  Admin-Abwesenheitsverwaltung um Generalpostfach-Auswahl, Admin-Bereich
  "Automatische Mails" (Versandzweck-Zuordnung).
- Geändert: E-Mail-Center (Filterleiste "Alle Postfächer/Mein
  Postfach/…", Postfach-Etikett je Mail, Ungelesen-Zähler/Glocke
  postfach-gefiltert), Verfassen-Dialog (Von-Dropdown mit `postfachId`
  statt freier Adresse, automatisches Signatur-Setzen bei Absenderwechsel).

## Offene Punkte für den Grobplan

- **`SystemSettingsController.java:180`** liefert
  `settingsService.getMailFromAddress()` an einen Frontend-Konsumenten,
  der im Brainstorming nicht benannt wurde. Der Grobplan muss klären, ob
  dieser Aufrufer künftig den `WEBSITE_ANFRAGE`-Versandzweck abfragt oder
  ersatzlos entfällt, sowie den genauen Frontend-Aufrufer identifizieren.
- **Exakte Tabellenform der zusätzlichen Import-Ordner** je Postfach
  (eigene Zeilen-Tabelle `postfach_ordner` vs. JSON-Spalte) — das
  Brainstorming legt nur die fachliche Anforderung fest (mehrere
  Import-Ordner pro Postfach), nicht die technische Modellierung.
- **DB-technische Durchsetzung "max. ein persönliches Postfach pro
  Nutzer"**: ob als partial unique index oder Service-Prüfung, hängt von
  der Ziel-DB (MariaDB/MySQL laut vorhandenen Migrationen) ab und ist im
  Grobplan zu verifizieren.
- **Migrationsnummer**: Die zuletzt vergebene Flyway-Version im
  Haupt-Checkout ist `V396`, im Einkaufs-Branch zusätzlich `V383`–`V387`
  für die dort neu eingeführten Tabellen. Die konkreten Versionsnummern
  für die Postfach-Migrationen sind erst nach dem Merge des
  Einkaufs-Branches auf `main` final festzulegen (Kollisionsgefahr bei
  parallelen Branches).
