# E-Mail-System – Postfächer, Import, Zuordnung, Versand

> **Stand:** Oktober 2026.
> Diese Datei war bis dahin ein Refactoring-Protokoll vom Dezember 2025 (getrennte Tabellen `ProjektEmail`/`AngebotEmail`/`LieferantEmail`, Warteschlange `EmailProcessingJob`, Admin-API `/api/admin/email-queue`). Davon ist nichts mehr im Code. Der Dateiname bleibt, der Inhalt beschreibt den heutigen Stand.

---

## 1. Überblick: Wo finde ich was?

| Ort im Programm | Wer | Wofür |
|---|---|---|
| **Kommunikation › E-Mail › E-Mail Center** (`/emails/inbox`) | alle | Mails lesen, schreiben, beantworten, zuordnen |
| **Kommunikation › E-Mail › E-Mail Vorlagen** (`/email-textvorlagen`, Seite „E-MAIL-TEXTVORLAGEN“) | alle | Texte für Angebote, Rechnungen, Mahnungen, Webseiten-Anfragen |
| **Kommunikation › E-Mail › E-Mail-Einstellungen** (`/email-einstellungen`) | alle | Reiter „Signaturen“ und „Abwesenheitsnotizen“ |
| **Vorlagen & Stammdaten › Administration › Einstellungen** → Reiter **„E-Mail“** → „Postfächer“ | Admin | Postfächer anlegen, Zugangsdaten, Hauptpostfach |
| **Einstellungen** → Reiter **„Abteilungen & Rechte“** → „E-Mail-Postfächer – wer sieht welches Postfach?“ | Admin | Sichtbarkeit je Abteilung und Benutzer |
| Benutzermenü → **„Benutzer verwalten“** → Feld „Eigenes Postfach“ | Admin | Einem Benutzer sein Postfach zuordnen |
| Projekt, Anfrage, Lieferant („E-Mail-Verlauf“), Steuerberater | alle | Alle Mails zu diesem Vorgang |

### So geht's (Kurzfassung)

- **Neue E-Mail:** E-Mail Center → **„Neue E-Mail“**. Unter **„Senden von“** das Postfach wählen. Für eine Mail an viele Empfänger den Schalter **„Einzeln verschicken – jeder sieht nur sich selbst“** setzen (höchstens 50 Empfänger, kein CC).
- **Antworten:** **„Antworten“**, **„Allen antworten“** (nur wenn mehrere beteiligt sind) oder im Menü **„Weiterleiten“**. Der Absender ist dabei fest: Geantwortet wird aus dem Postfach, in dem die Mail ankam.
- **Zuordnen:** Im Menü einer Mail **„Zuordnen“** → Fenster „E-Mail zuordnen“ → Projekt oder Anfrage wählen. Mails von Lieferanten und Steuerberatern ordnet das Programm meist selbst zu.
- **Spam:** „Als Spam markieren“ bzw. „Kein Spam“ trainiert den Filter. **„Absender sperren“** setzt die Adresse auf eine Sperrliste: Künftige Mails von ihr werden gar nicht mehr gespeichert, die vorhandenen werden endgültig gelöscht.
- **Signatur:** E-Mail-Einstellungen → „Signaturen“ → **„Neue Signatur“** gestalten → **„Als Standard“**. Die Standard-Signatur kommt automatisch unter jede neue E-Mail.
- **Abwesenheit:** E-Mail-Einstellungen → „Abwesenheitsnotizen“ → **„Neue Abwesenheit“** → Zeitraum und Text eintragen, unter **„Gilt für diese Postfächer“** die Postfächer wählen.

---

## 2. Datenmodell

Jede Mail wird **einmal** gespeichert (Tabelle `email`, eindeutig über die `messageId`). Zuordnungen und Postfächer hängen als Verweise daran.

| Entity | Wichtige Felder | Zweck |
|---|---|---|
| `Email` | `messageId`, `fromAddress`, `senderDomain`, `recipient`, `cc`, `replyToAddress`, `subject`, `body`/`htmlBody`/`rawBody`, `sentAt`, `direction` (`IN`/`OUT`), `isRead`, `isStarred`, `deletedAt` (= im Papierkorb), `parentEmail`/`replies` (Verlauf) | Eine Nachricht |
| | `zuordnungTyp` (`PROJEKT`, `ANFRAGE`, `LIEFERANT`, `STEUERBERATER`, `KEINE`) + FKs `projekt`, `anfrage`, `lieferant`, `steuerberater` | Zuordnung, genau eine; ein Lieferant kann zusätzlich zu Projekt/Anfrage verknüpft sein |
| | `spamScore`, `isSpam`, `userSpamVerdict`, `bayesScore`, `isNewsletter`, `inquiryScore`, `isPotentialInquiry` | Spam- und Newsletter-Erkennung |
| | `zustellStatus` (`OFFEN`/`UNZUSTELLBAR`), `zustellFehler` | Rückläufer eigener Mails |
| | `processingStatus` (`QUEUED`, `PROCESSING`, `DONE`, `ERROR`) | Verarbeitungsstand |
| `EmailPostfachZuordnung` | `email`, `postfach`, `imapOrdner`, `imapUid` | In welchem Postfach (und Ordner) eine Mail liegt – eine Mail kann in mehreren Postfächern liegen |
| `EmailAttachment` | `originalFilename`, `storedFilename`, `contentId`, `inlineAttachment`, `mimeType`, `sizeBytes`, `aiProcessed`, `lieferantDokument` | Anhänge; bei Lieferanten-Mails Verweis auf das erzeugte Lieferanten-Dokument |
| `EmailAbsender` (= **Postfach**) | `emailAdresse`, `anzeigename`, `aktiv`, `hauptpostfach`, `fuerGeschaeftsdokumente`, `laeuftAus`, `sichtbarFuerAlle`, `benutzername`, `passwortVerschluesselt`, `smtpHost`/`smtpPort`, `imapHost`/`imapPort`, `letzterAbrufAm`, `letzterAbrufFehler` | Ein Postfach mit Zugangsdaten; Freigaben über `email_absender_abteilung` und `email_absender_benutzer` |
| `EmailSignature` (+ `EmailSignatureImage`) | `name`, `html`, `besitzer`, `isSystemDefault`, `firmenVorlage`, `firmenVorlageId`, `vorlageDaten` | Signaturen mit Besitzer und Firmenvorlagen |
| `EmailTextTemplate` | `dokumentTyp`, `kategorie`, `name`, `subjectTemplate`, `htmlBody`, `aktiv` | E-Mail-Vorlagen |
| `EmailDraft` (+ `EmailDraftAttachment`) | Empfänger, Betreff, Text, Postfach, Bezug (Antwort, Projekt, Anfrage, Dokument), Anhänge als Bytes | Entwürfe |
| `EmailBlacklistEntry` | `emailAddress`, `blockedAt`, `blockedBy` | Gesperrte Absender |
| `OutOfOfficeSchedule` (+ `OooReplyLog`) | `title`, `startAt`, `endAt`, `subjectTemplate`, `bodyTemplate`, `postfaecher`, `active` | Abwesenheitsnotizen und wer schon eine Antwort bekam |

Der eigene Postfach-Bezug eines Benutzers steht in `FrontendUserProfile.emailAbsender`, seine Standard-Signatur in `FrontendUserProfile.defaultSignature`. Ein Kunde hat keinen eigenen Mail-Verweis; Kundenmails laufen über Projekt oder Anfrage.

---

## 3. Postfächer

| Art | Regel |
|---|---|
| **Hauptpostfach** (Haken „Hauptpostfach“) | Genau eines. Lässt sich nicht ausschalten, löschen oder auf „Läuft aus“ setzen. Das erste aktive Postfach wird automatisch Hauptpostfach. Alle sehen es; es ist der Rückfall für den Versand. |
| **Postfach für Rechnungen & Mahnungen** (Haken „Für Rechnungen & Mahnungen“) | Höchstens eines. Nur zum Versenden von Geschäftsdokumenten: unter „Senden von“ nicht wählbar, nie für Antworten genutzt, nie jemandes eigenes Postfach (außer es ist zugleich Hauptpostfach). Alle sehen es. |
| **Eigenes Postfach** | Einem Benutzer unter „Benutzer verwalten“ → „Eigenes Postfach“ zugeordnet. Ohne Zuordnung („Kein eigenes Postfach (Hauptpostfach)“) schreibt der Benutzer aus dem Hauptpostfach. |
| **Allgemeines Postfach** | Keinem Benutzer zugeordnet (z. B. eine Info-Adresse). |
| **„Läuft aus“** | Mails kommen weiter an und bleiben sichtbar; Antworten gehen über das Hauptpostfach raus; für neue Mails nicht mehr wählbar. |

**Sichtbarkeit** (`PostfachSichtbarkeitService`): Ein Benutzer sieht ein aktives Postfach, wenn es Haupt- oder Rechnungs-Postfach ist, für „Alle im Betrieb“ freigegeben ist, sein eigenes ist oder ihm bzw. seiner Abteilung freigegeben wurde („Nur bestimmte“). Administratoren sehen alle. Mails ohne Postfach-Verweis gelten als Mails des Hauptpostfachs. Mails, die einem Projekt, einer Anfrage oder einem Lieferanten zugeordnet sind, bleiben dort für alle lesbar.

**Einrichten:** Einstellungen → Reiter „E-Mail“ → „Postfächer“ → **„Neues Postfach“**: Adresse, angezeigter Name, Haken „Hauptpostfach“ / „Für Rechnungen & Mahnungen“ / „Eingeschaltet“ / „Läuft aus“, aufklappbar „Server-Einstellungen“ (IMAP und SMTP, Benutzername, Passwort), **„Verbindung testen“** (optional mit Test-Mail), Speichern. Anmeldename und Adresse müssen zur selben Domain gehören. Ein Postfach, in dem schon Mails liegen, lässt sich nicht löschen – stattdessen ausschalten.

Passwörter werden verschlüsselt gespeichert (AES-GCM, Schlüssel `mail.credentials.encryption-key`, Umgebungsvariable `MAIL_CREDENTIALS_ENCRYPTIONKEY`). Alte Einzel-Einstellungen für SMTP/IMAP übernimmt `PostfachUmzugService` beim Start automatisch in Postfächer.

---

## 4. Abruf (Import)

`EmailImportService.importNewEmails()` läuft **alle 60 Sekunden** (`@Scheduled(fixedDelay = 60_000)`), aber nur wenn `email.features.enabled=true`. In `application.properties` und `application-docker.properties` steht der Schalter auf `false`; im Betrieb wird er über die Umgebungsvariable `EMAIL_FEATURES_ENABLED='true'` eingeschaltet (siehe `deployment/docker-server/README.md`). Derselbe Schalter steuert die stündliche Abschaltung abgelaufener Abwesenheitsnotizen und die nächtliche Aufräumung.

Ablauf je Lauf:

1. **Je Postfach** mit vollständigen Zugangsdaten (Hauptpostfach zuerst), jedes für sich; Fehler landen beim Postfach („letzter Abruf“, z. B. „Anmeldung fehlgeschlagen – bitte Passwort und Anmeldename prüfen.“).
2. IMAP über SSL, Ordner nur lesend: Posteingang und die üblichen Gesendet-Ordner (`INBOX.Sent`, `Sent`, `Gesendet` …).
3. **Doppelte Mails** erkennt das Programm an der Message-ID; liegt eine bekannte Mail in einem weiteren Postfach, wird nur der Postfach-Verweis ergänzt.
4. **Gesperrte Absender** werden verworfen und gar nicht gespeichert.
5. **Verlauf:** zuerst über `In-Reply-To`/`References`, sonst über Betreff und Beteiligte. Eine Antwort erbt die Zuordnung der Ursprungsmail.
6. **Newsletter** erkennt das Programm am `List-Unsubscribe`-Kopf (nicht bei Lieferanten).
7. **Nachbearbeitung** nicht zugeordneter Mails, siehe Abschnitt 5.
8. **Abwesenheitsnotiz** auslösen (Abschnitt 8).
9. **Rückläufer** („unzustellbar“, „nicht zustellbar“ …): Nur dauerhafte Fehler zählen; die eigene Mail wird als `UNZUSTELLBAR` markiert und zeigt im Verlauf „Nicht angekommen“.

**Aufräumen** (`EmailCleanupService`): täglich um 03:00 Uhr werden Mails endgültig gelöscht, die seit mehr als 30 Tagen im Papierkorb liegen, sowie Spam und Newsletter älter als 30 Tage – jeweils zuerst auf dem Server, dann in der Datenbank. Mails mit Projekt-, Anfrage- oder Lieferanten-Zuordnung bleiben. Um 03:10 Uhr lernt der Spamfilter Posteingangs-Mails, die älter als zwei Monate sind, als erwünscht.

---

## 5. Automatische Zuordnung

Für eine noch nicht zugeordnete eingehende Mail prüft das Programm der Reihe nach:

1. **Steuerberater** – Absenderadresse oder Domain passt zu einem Steuerberater (`SteuerberaterEmailProcessingService`). PDFs mit BWA-ähnlichem Namen gehen zur BWA, andere PDFs werden (mit KI) eingeordnet, Lohnabrechnungs-Sammel-PDFs je Mitarbeiter aufgeteilt.
2. **Lieferant** – die Absender-Domain gehört zu einem Lieferanten (`EmailAutoAssignmentService`).
3. **Projekt oder Anfrage** – die Absenderadresse steht als Kundenadresse an einem Projekt oder einer Anfrage aus dem Zeitraum ±1 Monat. Ein Treffer wird direkt zugeordnet; bei mehreren entscheiden Stichworte aus Bauvorhaben/Kurzbeschreibung und, falls eingerichtet, die KI. Ohne Treffer im Zeitraum wird mit denselben Regeln über alle gesucht.
4. **Spamfilter** (`SpamFilterService`, Regeln + Naive Bayes): ab 85 Punkten landet die Mail im Ordner „Spam“. Lieferanten-Mails werden nie als Spam oder Newsletter markiert.
5. Bei **Lieferanten-Mails** werden PDF- und XML-Anhänge zu Lieferanten-Dokumenten ausgelesen – siehe [LIEFERANTEN_DOKUMENTEN.md](LIEFERANTEN_DOKUMENTEN.md).

Ausgehende Mails bekommen den Lieferanten über die Empfängeradresse (`EmailLieferantVerknuepfungService`). Bleibt eine Mail ohne Treffer, steht sie im Ordner „Nicht zugeordnet“ und lässt sich von Hand zuordnen.

**KI ist optional:** Ohne Gemini-Schlüssel überspringt die KI-Klassifikation (`EmailKiClassificationService`) sich selbst; die Regeln oben arbeiten weiter.

---

## 6. Versand

**Welches Postfach sendet?** (`PostfachVersandService`)

| Fall | Absender |
|---|---|
| Neue Mail | gewähltes Postfach („Senden von“), sonst das eigene, sonst das Hauptpostfach |
| Antwort / Weiterleitung auf eine eingegangene Mail | das Postfach, an dessen Adresse die Mail ging (An, dann CC), sonst Hauptpostfach |
| Antwort auf eine eigene gesendete Mail | das Postfach, aus dem sie ging |
| Mail lag nur in einem auslaufenden Postfach | Hauptpostfach |
| Geschäftsdokument (Angebot, Rechnung, Mahnung … im Anhang) | Postfach „Für Rechnungen & Mahnungen“, sonst Hauptpostfach |
| Bestätigungscode der digitalen Freigabe | Hauptpostfach (ohne Kopie im E-Mail-Center) |

Versendet wird per SMTP über SSL. Ein Postfach ohne eigene Zugangsdaten sendet über das Konto des Hauptpostfachs mit seiner eigenen Adresse. Die gesendete Mail wird im Programm gespeichert (`AusgangsmailService`, mit Zuordnung, Postfach und Anhängen) und per IMAP in den „Gesendet“-Ordner des Anbieters kopiert (`SentMailArchiver`, Systemeinstellung `mail.sent-kopie.aktiv`, Standard an).

Grenzen: Anhänge höchstens 15 MB je Datei und 25 MB zusammen. Entwürfe speichert das Programm automatisch und löscht sie beim Senden.

Weitere Versandwege mit derselben Logik: Bestellung an Lieferanten ([BESTELLWESEN.md](BESTELLWESEN.md)), automatische Mahnungen, automatische Auftragsbestätigung nach digitaler Annahme, Bestätigungsmail für Webseiten-Anfragen ([ANFRAGE_FUNNEL_SETUP.md](ANFRAGE_FUNNEL_SETUP.md)).

---

## 7. Signaturen

Ort: **Kommunikation › E-Mail › E-Mail-Einstellungen** → Reiter **„Signaturen“** (Überschrift „E-Mail-Signaturen“).

- Jeder Benutzer sieht und bearbeitet nur **seine eigenen** Signaturen; Administratoren sehen alle und können den Besitzer festlegen.
- **Signatur-Studio:** „Neue Signatur“ öffnet „Neue Signatur gestalten“ mit den Vorlagen „Klassisch mit Foto“, „Modern mit Farbbalken“, „Kompakt“, „Mit Banner“ und „Schlicht“.
- **„Als Standard“** macht eine Signatur zur eigenen Standard-Signatur; sie kommt automatisch unter jede neue Mail. Eine zugewiesene Standard-Signatur sieht der Benutzer auch, wenn er sie nicht selbst angelegt hat (nur lesen).
- **Firmen-Vorlagen** (nur Admin, Karte „Firmen-Vorlagen für Mitarbeiter“, Knopf „Als Firmen-Vorlage“): Sobald es eine gibt, erstellen Mitarbeiter neue Signaturen aus der Firmen-Vorlage. Gestaltung und Firmenangaben sind dann fest, persönliche Angaben bleiben änderbar.
- **System-Signatur** (nur Admin, Karte „System-Signatur für automatische E-Mails“, Knopf „Als System-Signatur“): steht unter automatischen Mails wie Mahnungen oder Anfrage-Bestätigungen; sie lässt sich nicht löschen, nur ersetzen.

---

## 8. Abwesenheitsnotizen

Ort: **E-Mail-Einstellungen** → Reiter **„Abwesenheitsnotizen“** → „Neue Abwesenheit“ bzw. „Abwesenheit planen“.

- **Wer darf was:** Administratoren für jedes aktive Postfach; alle anderen für ihr eigenes Postfach und für allgemeine Postfächer, die sie sehen. Nie für auslaufende Postfächer oder das reine Rechnungs-Postfach.
- **Antwort:** aus dem Postfach, in dem die Mail ankam – **einmal je Absender und Notiz**.
- **Keine Antwort** auf automatische Mails, System- und eigene Adressen, Spam und Newsletter.
- **Platzhalter:** `{{subject}}`, `{{title}}`, `{{start}}`, `{{ende}}`; Betreff-Vorschlag „Automatische Antwort: {{subject}}“.
- Abgelaufene Notizen schaltet das Programm stündlich ab.

---

## 9. E-Mail-Vorlagen

Ort: **Kommunikation › E-Mail › E-Mail Vorlagen**. Je Dokumenttyp eine Vorlage, gruppiert nach den Kategorien „Dokumente“, „Mahnwesen“, „Webseite & Anfragen“ und „System“.

- **Platzhalter:** u. a. `{{ANREDE}}`, `{{KUNDENNAME}}`, `{{ANSPRECHPARTNER}}`, `{{BAUVORHABEN}}`, `{{PROJEKTNUMMER}}`, `{{DOKUMENTNUMMER}}`, `{{RECHNUNGSDATUM}}`, `{{FAELLIGKEITSDATUM}}`, `{{BETRAG}}`, `{{BENUTZER}}`, `{{BANK}}`, `{{IBAN}}`, `{{BIC}}`, `{{REVIEW_LINK}}`, für Webseiten-Anfragen `{{NACHRICHT}}`, `{{ANFRAGE_DATUM}}`, `{{ANFRAGENUMMER}}`. Eingaben werden beim Einsetzen maskiert.
- **Knöpfe mit Link** („Button einfügen“): Ziel „Google-Bewertung“ (`{{REVIEW_URL}}`) oder eine eigene Adresse (nur `http(s)`, `mailto`, `tel`).
- Bei Angeboten und Nachtragsangeboten setzt das Programm beim Versand den Link zur digitalen Annahme ein, wenn die Freigabe-Adresse eingerichtet ist (siehe [freigabe/FREIGABE_FEATURE_PROMPT.md](freigabe/FREIGABE_FEATURE_PROMPT.md)).

---

## 10. E-Mail Center (Oberfläche)

- **Ordner** (linke Leiste „E-Mail Center“): „Posteingang“, „Gesendet“, „Entwürfe“, „Markiert“, „Newsletter“, „Spam“, „Papierkorb“ und aufklappbar „Zugeordnet“ mit „Nicht zugeordnet“, „Projekte“, „Anfragen“, „Lieferanten“, „Steuerberater“.
- **Aktionen an einer Mail:** „Antworten“, „Allen antworten“, Stern (markieren), Menü mit „Weiterleiten“, „Zuordnen“, „Alle Anhänge herunterladen“, „Verschieben nach“, „Wiederherstellen“, „Kein Spam“ / „Als Spam markieren“, „Kein Newsletter“ / „Newsletter bestätigen“, „Absender sperren“, „In Papierkorb“ / „Endgültig löschen“.
- **Ziehen und Ablegen** auf „Posteingang“, „Newsletter“, „Spam“ und „Papierkorb“.
- Jede Mail zeigt, in welchem Postfach sie liegt. Verläufe werden zusammengefasst angezeigt, Zitate eingeklappt.
- In Projekt, Anfrage und Lieferant zeigt der E-Mail-Reiter alle zugeordneten Mails – unabhängig von der Postfach-Sichtbarkeit.

---

## 11. REST-API (Auswahl)

| Basis | Wichtige Endpunkte |
|---|---|
| `/api/emails` | Ordner: `GET /inbox`, `/sent`, `/projects`, `/offers` (Anfragen), `/suppliers`, `/tax-advisors`, `/unassigned`, `/starred`, `/newsletter`, `/spam`, `/trash`, `/search`, `/stats`; je Vorgang `GET /projekt/{id}`, `/anfrage/{id}`, `/lieferant/{id}`; Einzelmail `GET /{id}`, `/{id}/thread`, `/{id}/attachments/{aid}`; `GET /absender-postfaecher`; Senden `POST /send`, `/{id}/reply` (multipart); Zuordnen `POST /{id}/assign/projekt/{x}`, `/assign/anfrage/{x}`, `/assign/lieferant/{x}`, `/{id}/unassign`; Spam `POST /{id}/mark-spam`, `/mark-not-spam`, `/mark-not-newsletter`, `/confirm-newsletter`, `/{id}/block-sender`; `POST /{id}/mark-read`, `/{id}/toggle-star`, `/bulk/move-to-folder`, `/mark-all-read`; `DELETE /{id}` (Papierkorb), `/{id}/permanent`; Wartung `POST /import`, `/scan-*`, `/admin/rebuild-threads` (nur Admin) |
| `/api/emails/drafts` | Entwürfe anlegen, ändern, löschen, `/count`, Anhänge |
| `/api/email` | `GET /from-addresses`, `/dokument-absender`; `POST /beautify` (KI-Formulierung), `/preview`, `/send`, `/send/anfrage` (Geschäftsdokumente) |
| `/api/email/template` | Vorlage für einen Dokumenttyp rendern |
| `/api/email/signatures` | Liste, `/default`, `/firmen-vorlagen`, `/{id}/standard`, `/{id}/firmen-vorlage`, `/system-default` (Admin), Bilder, Vorschau |
| `/api/email-textvorlagen` | Vorlagen-CRUD, `/dokumenttypen`, `/placeholders` |
| `/api/email/outofoffice` | Abwesenheitsnotizen, `/postfaecher` |
| `/api/email-ki` | `GET /status`, `POST /classify/{id}`, `/classify-and-assign/{id}` |
| `/api/postfaecher` (nur Admin) | Postfächer anlegen, ändern, löschen, `/{id}/sichtbarkeit`, `/test` |

---

## 12. Konfiguration

| Schlüssel | Bedeutung |
|---|---|
| `email.features.enabled` / `EMAIL_FEATURES_ENABLED` | Abruf, Aufräumen und Abwesenheits-Job an/aus (in den Properties `false`, im Betrieb `true`) |
| `mail.credentials.encryption-key` / `MAIL_CREDENTIALS_ENCRYPTIONKEY` | Schlüssel für die Postfach-Passwörter (16, 24 oder 32 Byte) |
| `email.threading.subject-fallback-lookback` | Wie viele Mails die Verlaufs-Erkennung über den Betreff zurückschaut (Standard 500) |
| `file.mail-attachment-dir`, `file.supplier-mail-attachment-dir` | Ablage der Anhänge |
| `ai.email.enabled`, `ai.email.model`, `ai.gemini.model.email-classification` | KI für E-Mails |
| Systemeinstellung `mail.sent-kopie.aktiv` | Kopie gesendeter Mails in den „Gesendet“-Ordner des Anbieters |

Server, Benutzernamen und Passwörter der Postfächer werden in der Oberfläche gepflegt, nicht in den Properties. Secrets gehören nur in `application-local.properties` bzw. die Umgebung.

---

## 13. Wichtige Klassen

| Klasse | Aufgabe |
|---|---|
| `EmailImportService` | Abruf je Postfach, Verlauf, Nachbearbeitung, Abwesenheits-Job |
| `EmailAutoAssignmentService`, `SteuerberaterEmailProcessingService` | Automatische Zuordnung |
| `SpamFilterService`, `EmailKiClassificationService` | Spam- und KI-Klassifikation |
| `EmailAttachmentProcessingService` | Lieferanten-Anhänge → Lieferanten-Dokumente |
| `BounceErkennungService` | Rückläufer erkennen |
| `EmailCleanupService` | Nächtliches Aufräumen |
| `PostfachService`, `PostfachSichtbarkeitService`, `PostfachVersandService`, `PostfachUmzugService` | Postfächer, Sichtbarkeit, Absenderwahl, Übernahme alter Einstellungen |
| `AusgangsmailService`, `SentMailArchiver` | Gesendete Mails speichern und in „Gesendet“ ablegen |
| `EmailSignaturVerwaltungService`, `EmailSignatureService` | Signaturen |
| `AbwesenheitsnotizService`, `OutOfOfficeResponder` | Abwesenheitsnotizen |
| `EmailTextTemplateService` | E-Mail-Vorlagen |
| `UnifiedEmailController`, `EmailController`, `EmailDraftController`, `PostfachController` | REST-API |
