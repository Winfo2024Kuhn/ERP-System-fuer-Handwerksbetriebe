# API-Referenz – REST-Endpunkte

Stand: 12.10.2026. Abgeglichen mit den Controllern unter
`src/main/java/org/example/kalkulationsprogramm/controller` (einschließlich
Unterpaket `miete`) und der `SecurityConfig`.

## Übersicht

- **Port:** `8080` (`server.port` in `application.properties`, im Docker-Image
  ebenso). Lokal also `http://localhost:8080`.
- **Umfang:** 98 Controller mit zusammen 737 Endpunkt-Methoden
  (733 verschiedene Kombinationen aus HTTP-Methode und Pfad; einige
  Pfade gibt es zweimal, getrennt nach JSON oder multipart bzw. nach
  Query-Parameter). Dazu kommt `RestExceptionHandler` (`@ControllerAdvice`,
  wandelt Fehler in Antworten um, keine eigenen Endpunkte).
- `ZeiterfassungController` und `SpaErrorController` sind `@Controller` und
  liefern Seiten aus, alle anderen sind `@RestController`.

### Legende

- Pfade in den Tabellen sind relativ zur **Basis-URL** (das `@RequestMapping`
  der Klasse). `/` heißt: genau die Basis-URL, ohne Schrägstrich am Ende.
- Hat ein Controller kein eigenes Klassen-Mapping, stehen volle Pfade da.
- `{…}` ist ein Platzhalter im Pfad. Wichtige Query-Parameter stehen in der
  Beschreibung in Backticks, z. B. `jahr`.
- *(nur Admin)*: in der `SecurityConfig` auf die Rolle `ADMIN` beschränkt,
  andere bekommen `403`.
- „multipart“ heißt Upload als `multipart/form-data`.
- Wartungs-Endpunkte ohne Knopf im Frontend und wie man sie aufruft:
  [`ADMIN_ENDPUNKTE.md`](ADMIN_ENDPUNKTE.md).

### Absicherung (Kurzfassung)

Die `SecurityConfig` hat fünf Filterketten, geprüft in dieser Reihenfolge:

1. **`/api/internal/**`** – nur Server-zu-Server für die Firmen-Website
   (Abschnitt 20). Kein Login; jeder Aufruf braucht den Header
   `X-ERP-Website-Key` (ohne eingetragenen Schlüssel wird alles abgelehnt).
   Mit `cloudflare.access.enabled=true` ersetzt ein gültiges
   Cloudflare-Access-Token den Schlüssel. CORS ist hier aus.
2. **Pfade der Handy-App** (`ZEITERFASSUNG_PATHS`): `/zeiterfassung/**`,
   `/api/zeiterfassung/**`, `/api/mitarbeiter/by-token/**`, `/api/urlaub/**`,
   `/api/kalender/mobile/**`, `/api/push/**`, `/api/dokumente/**`,
   `/api/images/**`, `/api/projekte/**`, `/api/anfragen/**`,
   `/api/kunden/**`, `/api/lieferanten/**`, `/api/produktkategorien/**`,
   `/api/arbeitsgaenge/**`, `/api/abwesenheit/**`, die Reklamationspfade
   `/api/reklamationen/lieferant/**`, `/api/reklamationen/*` und
   `/api/reklamationen/*/bilder`, `/api/zeitverwaltung/feiertage/zwischen`,
   `/api/buchhaltung/mobile/**` und `/api/spracheingabe/**`.
   Angemeldet ist man hier per PC-Sitzung **oder** per Mitarbeiter-Token
   (Header `X-Auth-Token`, Parameter `token`, Token im Pfad, bei lesenden
   Aufrufen auch Cookie `ze_token`). Mit Token sind nur die Aufrufe erlaubt,
   die in `MobileApiPolicy` stehen (siehe unten), alles andere gibt `403`.
   Die App-Seiten unter `/zeiterfassung` sind ohne Anmeldung erreichbar.
3. **Öffentliche Dateien ohne Login:** `/`, `/index.html`, `/favicon.ico`,
   `/app-icon.png`, `/assets/**`, `/static/**`, `/manifest.json`, `/sw.js`,
   `/dokument-editor/**`, `/login/**`, `/onboarding/**`, `/error/**` und
   `/actuator/health`. Hier gibt es nur lesende Aufrufe.
4. **Alle übrigen `/api/**`** – PC-Sitzung (Formular-Login unter
   `/api/auth/login`) mit CSRF-Schutz: Schreibende Aufrufe schicken den
   Header `X-XSRF-TOKEN` mit dem Wert des Cookies `XSRF-TOKEN`. Ohne
   Anmeldung erreichbar sind nur `/api/auth/login`, `/api/auth/logout`,
   `/api/auth/register` und `/api/auth/bootstrap-status`.
5. **Alles andere** – Anmeldung nötig.

**Nur für Admins** (Rolle `ADMIN`, alles in Kette 4):

- `PUT /api/abteilungen/*/berechtigungen`
- `POST /api/abteilungen`, `PUT /api/abteilungen/*`, `DELETE /api/abteilungen/*` (Abteilungen anlegen, umbenennen, löschen)
- `/api/firma/**` – außer lesend `GET /api/firma`, `GET /api/firma/logo`,
  `GET /api/firma/steuerberater` und `GET /api/firma/steuerberater/*`, die dürfen alle Angemeldeten
- `/api/settings/**`, `/api/frontend-users/**`, `/api/postfaecher/**`
- `PUT /api/email/signatures/*/system-default`,
  `PUT` und `DELETE /api/email/signatures/*/firmen-vorlage`
- `POST`, `PUT`, `DELETE /api/formulare/**` – außer `POST /api/formulare/dokumentnummer`
- `POST /api/verrechnungslohn/uebernehmen`
- `POST /api/mahnwesen/lauf`
- `POST /api/emails/admin/**`
- Massenläufe ohne Knopf (`ADMIN_MASSENLAEUFE`, jede Methode): `/api/emails/import`,
  `/api/emails/scan-spam|scan-inquiries|scan-assignments|scan-steuerberater`,
  `/api/emails/backfill-parents`, `/api/emails/spam-model/rescore|bootstrap`,
  `/api/lieferant-dokumente/process-assigned-emails`,
  `/api/lieferant-dokumente/process-email/*`,
  `/api/lieferant-dokumente/lieferant/*/process-emails`,
  `/api/zeitverwaltung/saldo-cache/warmup`
- `DELETE /api/lieferant-dokumente/**`, `GET /api/lieferant-dokumente/duplicates`,
  `POST /api/lieferant-dokumente/relink-all`,
  `POST /api/lieferant-dokumente/lieferant/*/relink`,
  `POST /api/lieferant-dokumente/lieferant/*/reanalyze`
- `/api/admin/**`, `/api/beitraege/**`
- `/api/telefon/einstellungen/**`, `/api/telefon/admin/**`

**Nur für Admins in Kette 2** (Pfade, die auch die Handy-App liest):

- `POST`, `PUT`, `DELETE /api/arbeitsgaenge/**` (Arbeitsgänge und Stundensätze pflegen; lesen dürfen alle)
- `DELETE /api/kunden/*`, `POST /api/kunden/*/datenschutz-sperre`, `POST /api/kunden/*/anonymisieren`

`@PreAuthorize` wird nirgends verwendet. Feinere Rechte prüfen die Controller
bzw. Services selbst, meist über die Abteilungsrechte: Belege und Kasse,
sichtbare Lieferanten-Dokumenttypen, Eingangsrechnungen sehen/genehmigen,
Telefon (`darfTelefonSehen`), Aufschläge ändern (`darfAufschlaegeAendern`, Administratoren immer), Monatsabschluss, sichtbare Postfächer und
Anmeldecodes der Mitarbeiter.

**Mit Handy-Token erlaubt** (`MobileApiPolicy`, abschließende Liste):

- `GET /api/mitarbeiter/by-token/{token}`
- `GET /api/zeiterfassung/…`: `projekte`, `kategorien`, `lieferanten`,
  `feiertage`, `projekte/{id}`, `kategorien/{id}`, `projekte/{id}/bilder`,
  `arbeitsgaenge|aktiv|heute|saldo|buchungen|urlaubsverfall|buchungszeitfenster|langzeitkrankmeldung/{token}`
- `POST /api/zeiterfassung/start|stop|pause`
- `GET /api/urlaub/antraege|resturlaub|typen`, `POST /api/urlaub/antraege`
- `GET /api/kalender/mobile`, `GET /api/kalender/mobile/tag`
- `GET /api/abwesenheit/team`
- `GET /api/push/vapid-key`, `POST /api/push/subscribe`
- `GET /api/kunden`, `GET /api/arbeitsgaenge`, `GET /api/anfragen`, `GET /api/anfragen/{id}`
- `GET /api/lieferanten/{id}`, `GET /api/lieferanten/bilder/file/{datei}` (auch `/vorschau`),
  `GET /api/lieferanten/{id}/dokumente`, `GET /api/lieferanten/{id}/dokumente/{dokId}/download`,
  `POST /api/lieferanten/{id}/dokumente/analyze|import`
- Projekte und Anfragen (`/api/projekte/{id}` bzw. `/api/anfragen/{id}`):
  `GET`/`POST …/dokumente`, `GET`/`POST …/notizen`, `PATCH`/`DELETE …/notizen/{nid}`,
  `POST`/`DELETE …/notizen/{nid}/bilder…`
- `GET` und `POST /api/reklamationen/lieferant/{id}`, `GET /api/reklamationen/{id}`,
  `POST /api/reklamationen/{id}/bilder`
- `GET /api/zeitverwaltung/feiertage/zwischen`
- `GET /api/buchhaltung/mobile/me/permissions`, `GET /api/buchhaltung/mobile/belege` (auch `/{id}`),
  `POST /api/buchhaltung/mobile/belege`, `POST /api/buchhaltung/mobile/mwst-rechner`,
  `PUT /api/buchhaltung/mobile/belege/{id}/positionen`
- `POST /api/spracheingabe/transkribieren`, `GET /api/spracheingabe/verfuegbar`
- `GET /api/images/{datei}`, `GET /api/dokumente/{datei}` (auch `/thumbnail` und `/anzeige`)

**Antwortcodes der Absicherung:** `401` = nicht angemeldet bzw. Token
ungültig, `403` = angemeldet, aber nicht berechtigt (oder CSRF-Token fehlt).

---

## 1. Anmeldung, Benutzer und Rechte

### AuthController

**Basis-URL:** `/api/auth`

Anmelden und Abmelden selbst haben keine Controller-Methode: `POST /api/auth/login` (Formular-Login mit `username` und `password`) und `POST /api/auth/logout` stellt Spring Security bereit. `login`, `logout`, `register` und `bootstrap-status` gehen ohne Anmeldung.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/bootstrap-status` | Gibt es schon Login-Benutzer, ist die Ersteinrichtung nötig? (ohne Anmeldung) |
| `POST` | `/register` | Ersten Benutzer anlegen, nur in der Einrichtungsphase, sonst `403` (ohne Anmeldung) |
| `GET` | `/me` | Angemeldeter Benutzer mit Rollen, verknüpftem Mitarbeiter und Einrichtungs-Hinweisen |
| `PUT` | `/me/credentials` | Eigenen Benutzernamen und/oder eigenes Passwort ändern |

### FrontendUserController

**Basis-URL:** `/api/frontend-users`

Ganzer Pfad nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Login-Benutzer *(nur Admin)* |
| `POST` | `/` | Login-Benutzer anlegen oder ändern *(nur Admin)* |
| `DELETE` | `/{id}` | Login-Benutzer löschen *(nur Admin)* |

### AbteilungBerechtigungController

**Basis-URL:** `/api/abteilungen`

Rechte je Abteilung (welche Dokumenttypen sie sehen, ob sie Rechnungen genehmigen darf usw.).

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/berechtigungen` | Alle Abteilungen mit ihren Berechtigungen |
| `GET` | `/{id}/berechtigungen` | Berechtigungen einer Abteilung |
| `PUT` | `/{id}/berechtigungen` | Berechtigungen einer Abteilung ändern *(nur Admin)* |
| `GET` | `/dokumenttypen` | Alle Dokumenttypen, für die es Rechte gibt |

### DatensatzLockController

**Basis-URL:** `/api/datensatz-locks`

Bearbeitungssperre, damit nicht zwei Personen gleichzeitig dasselbe Dokument bearbeiten. `typ` ist `AUSGANG` oder `EINGANG`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/{typ}/{id}/acquire` | Sperre holen oder übernehmen; `409`, wenn jemand anderes sie hält |
| `POST` | `/{typ}/{id}/heartbeat` | Sperre am Leben halten |
| `DELETE` | `/{typ}/{id}` | Sperre freigeben |

### EntityLastAccessedController

**Basis-URL:** `/api/last-accessed`

„Zuletzt aufgerufen“-Reihenfolge je Liste, pro Benutzer und geräteübergreifend.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{entityType}` | Zuletzt aufgerufene Einträge einer Liste (z. B. Projekte, Anfragen) |
| `POST` | `/{entityType}/{entityId}` | Aufruf eines Eintrags merken |

---

## 2. Anfragen

### AnfrageController

**Basis-URL:** `/api/anfragen`

Den früheren `AngebotController` unter `/api/angebote` gibt es nicht mehr; Angebote hängen an Anfragen bzw. sind Ausgangsdokumente (Abschnitt 5).

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/freigabe-status` | Jüngste digitale Freigabe (Angebot/AB) je Anfrage (`ids`) |
| `POST` | `/zugferd/extract` | Rechnungsdaten aus einer hochgeladenen (ZUGFeRD-)PDF lesen |
| `POST` | `/zugferd/extract-ai` | PDF per KI auslesen (Anfrage oder Auftragsbestätigung) |
| `POST` | `/` | Anfrage anlegen (JSON) |
| `POST` | `/` | Anfrage mit Bild anlegen (multipart) |
| `POST` | `/{anfrageID}/dokumente` | Dokumente zur Anfrage hochladen |
| `POST` | `/{anfrageID}/zugferd` | ZUGFeRD-PDF aus PDF und Rechnungsdaten erzeugen |
| `GET` | `/{anfrageID}/dokumente` | Dokumente der Anfrage (optional `gruppe`) |
| `GET` | `/{anfrageID}/email-dokumente` | Versendbare Dokumente (Anfrage, AB) für den Mail-Dialog |
| `DELETE` | `/{anfrageID}/dokumente/{dokumentID}` | Dokument der Anfrage löschen |
| `GET` | `/` | Anfragen suchen, ohne Seiten (wenn kein `page` mitkommt) |
| `GET` | `/` | Anfragen seitenweise (sobald `page` gesetzt ist), mit Filtern für Freigabe, Status, Herkunft und Sortierung |
| `GET` | `/jahre` | Jahre mit Anfragen (für den Filter) |
| `DELETE` | `/{id}` | Anfrage löschen (`cascadeKunde=true` löscht den Kunden mit, wenn möglich) |
| `PUT` | `/{id}` | Anfrage ändern (JSON) |
| `PATCH` | `/{id}/kurzbeschreibung` | Kurzbeschreibung ändern (reiner Text) |
| `PUT` | `/{id}` | Anfrage mit Bild ändern (multipart) |
| `GET` | `/{id}` | Einzelne Anfrage |
| `GET` | `/{id}/projekt-vorlage` | Daten zum Anlegen eines Projekts aus der Anfrage |
| `GET` | `/{id}/produktkategorien-vorschlag` | Produktkategorien aus Angebot/AB als Vorschlag für das Projekt |
| `GET` | `/{anfrageId}/notizen` | Notizen der Anfrage |
| `POST` | `/{anfrageId}/emails` | E-Mail-Adresse zur Anfrage hinzufügen |
| `POST` | `/{anfrageId}/notizen` | Notiz anlegen |
| `PATCH` | `/{anfrageId}/notizen/{notizId}` | Notiz ändern |
| `DELETE` | `/{anfrageId}/notizen/{notizId}` | Notiz löschen |
| `POST` | `/{anfrageId}/notizen/{notizId}/bilder` | Bild zu einer Notiz hochladen |
| `DELETE` | `/{anfrageId}/notizen/{notizId}/bilder/{bildId}` | Bild einer Notiz löschen |

---

## 3. Projekte

### ProjektController

**Basis-URL:** `/api/projekte`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/freigabe-status` | Jüngste digitale Freigabe (Angebot/AB) je Projekt (`ids`) |
| `POST` | `/` | Projekt mit Bild und Dokumenten anlegen (multipart) |
| `POST` | `/` | Projekt anlegen (JSON) |
| `POST` | `/{projektId}/anfragen/{anfrageId}/zusammenfuehren` | Anfrage in das Projekt übernehmen (`409` bei Konflikt) |
| `GET` | `/{projektID}/stueckliste.pdf` | Stückliste als PDF |
| `PUT` | `/{projektID}` | Projekt mit Bild ändern (multipart) |
| `PUT` | `/{projektID}` | Projekt ändern (JSON) |
| `PATCH` | `/{projektID}/abgeschlossen` | Haken „Beendet“ setzen oder entfernen |
| `POST` | `/{projektID}/materialkosten` | Einen manuellen Materialkosten-Eintrag anhängen (vorhandene bleiben) |
| `PATCH` | `/{projektID}/kurzbeschreibung` | Kurzbeschreibung ändern (reiner Text) |
| `POST` | `/{projektID}/materialkosten/artikel` | Artikel als Materialkosten hinzufügen |
| `DELETE` | `/{projektID}/materialkosten/artikel/{artikelInProjektID}` | Artikel aus den Materialkosten entfernen |
| `PATCH` | `/{projektID}/materialkosten/artikel/{artikelInProjektID}` | Artikel in den Materialkosten ändern |
| `DELETE` | `/{projektID}/materialkosten/{materialID}` | Materialkosten-Eintrag entfernen |
| `POST` | `/{projektID}/produktkategorien` | Produktkategorien zum Projekt hinzufügen |
| `PATCH` | `/{projektID}/produktkategorien/{kategorieID}` | Produktkategorie im Projekt ändern |
| `DELETE` | `/{projektID}/produktkategorien/{kategorieID}` | Produktkategorie aus dem Projekt entfernen |
| `GET` | `/{projektID}/produktkategorien/{ppkId}/referenziert` | Hängen Zeitbuchungen an dieser Projekt-Kategorie? |
| `DELETE` | `/{projektID}` | Projekt löschen |
| `POST` | `/zugferd/extract` | Rechnungsdaten aus einer (ZUGFeRD-)PDF lesen |
| `POST` | `/zugferd/extract-ai` | PDF per KI auslesen, bei Fehler Standard-Auslesung |
| `POST` | `/{projektID}/zugferd` | ZUGFeRD-PDF aus PDF und Rechnungsdaten erzeugen |
| `POST` | `/{projektID}/dokumente` | Dokumente zum Projekt hochladen |
| `GET` | `/{projektID}/dokumente` | Dokumente des Projekts |
| `GET` | `/{projektID}/rechnungen` | Rechnungen des Projekts |
| `GET` | `/{projektID}/eingangsrechnungen` | Zugeordnete Lieferanten-Rechnungen (Nachkalkulation „Materialkosten“) |
| `GET` | `/{projektID}/email-dokumente` | Versendbare Dokumente für den Mail-Dialog |
| `GET` | `/offene-posten` | Offene Geschäftsdokumente aller Projekte |
| `GET` | `/umsatz` | Umsatzauswertung (Jahr, Monat, Rechnungs-/Auftragsnummer, Kunde, Kategorie) |
| `GET` | `/umsatz/statistiken` | Umsatzstatistik für Jahr/Monat |
| `GET` | `/umsatz/lieferantenkosten-jahresuebersicht` | Lieferantenkosten je Jahr (Erfolgsanalyse) |
| `GET` | `/umsatz/lieferanten-performance` | Lieferanten-Auswertung (optional Jahr/Monat) |
| `PATCH` | `/dokumente/{dokumentID}/bezahlt` | Geschäftsdokument als bezahlt/unbezahlt markieren |
| `DELETE` | `/{projektID}/dokumente/{dokumentID}` | Dokument des Projekts löschen |
| `GET` | `/` | Projekte suchen und filtern (`page`, `size`) |
| `GET` | `/jahre` | Jahre mit Projekten (Jahres-Filter) |
| `GET` | `/export-pdf` | PDF-Liste aller nicht beendeten Projekte (Vorschau im Browser) |
| `GET` | `/simple` | Schlanke Projektliste für Auswahlfelder |
| `GET` | `/suche` | Schnellsuche (höchstens 10 Treffer) für die Bestellungs-Zuordnung |
| `GET` | `/naechste-auftragsnummer` | Nächste freie Auftragsnummer (optional je Kunde) |
| `GET` | `/auftragsnummer-verfuegbar` | Ist eine Auftragsnummer noch frei? |
| `GET` | `/{id}` | Einzelnes Projekt |
| `GET` | `/{projektId}/notizen` | Notizen des Projekts (neueste zuerst) |
| `POST` | `/{projektId}/emails` | E-Mail-Adresse zum Projekt hinzufügen |
| `POST` | `/{projektId}/notizen` | Notiz anlegen |
| `PATCH` | `/{projektId}/notizen/{notizId}` | Notiz ändern |
| `DELETE` | `/{projektId}/notizen/{notizId}` | Notiz löschen |
| `POST` | `/{projektId}/notizen/{notizId}/bilder` | Bild zu einer Notiz hochladen |
| `DELETE` | `/{projektId}/notizen/{notizId}/bilder/{bildId}` | Bild einer Notiz löschen |

### ProjektWartungController

**Basis-URL:** `/api/admin/projekte`

Wartungsläufe ohne Knopf im Frontend, Aufruf siehe `ADMIN_ENDPUNKTE.md`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/preise-nachtragen` | Fehlende Auftragspreise aus Angebot/AB/Nachtrag (sonst Rechnungssumme) nachtragen *(nur Admin)* |
| `POST` | `/rabatt-betraege-korrigieren` | Netto/Brutto von Bestandsdokumenten mit Rabatt neu rechnen *(nur Admin)* |

---

## 4. Kunden

### KundeController

**Basis-URL:** `/api/kunden`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Kunden suchen (`q`, `name`, `nummer`, `ort`, `email`, `typ`, `page`, `size`) |
| `GET` | `/duplikat-check` | Mögliche Dubletten beim Anlegen (E-Mail, Telefon, Name + PLZ …) |
| `POST` | `/` | Kunde anlegen; bei Dubletten-Verdacht `409` mit Trefferliste, bestätigen per Header `X-Duplikat-Bestaetigt: true` |
| `PUT` | `/{id}` | Kunde ändern |
| `GET` | `/next-kundennummer` | Nächste freie Kundennummer |
| `GET` | `/{id}` | Kunde mit Details |
| `POST` | `/{id}/emails` | E-Mail-Adresse zum Kunden hinzufügen |
| `GET` | `/{id}/notizen` | Notizen des Kunden (optional Suche `q`) |
| `POST` | `/{id}/notizen` | Notiz anlegen |
| `DELETE` | `/{id}/notizen/{notizId}` | Notiz löschen |

---

## 5. Ausgangsdokumente: Angebote, Auftragsbestätigungen, Rechnungen

### AusgangsGeschaeftsDokumentController

**Basis-URL:** `/api/ausgangs-dokumente`

Angebote, Auftragsbestätigungen, Rechnungen, Mahnungen usw. als Ausgangsdokumente.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{id}/mahnung-vorschau` | Mahn-PDF einer Stufe als Vorschau (speichert und verschickt nichts) |
| `GET` | `/freigabe-status` | Jüngste digitale Freigabe je Dokument (`ids`) |
| `GET` | `/{id}/freigabe-audit` | Nachweis einer angenommenen Freigabe (E-Mail, IP, Zeit, Hash) |
| `GET` | `/{id}` | Einzelnes Dokument |
| `GET` | `/projekt/{projektId}` | Alle Dokumente eines Projekts |
| `GET` | `/anfrage/{anfrageId}` | Alle Dokumente einer Anfrage |
| `GET` | `/projekt/{projektId}/geerbte-rechnungsadresse` | Zuletzt bearbeitete Rechnungsanschrift im Projekt (zum Vorbelegen) |
| `GET` | `/anfrage/{anfrageId}/geerbte-rechnungsadresse` | Dasselbe für eine Anfrage |
| `POST` | `/` | Dokument anlegen; holt gleich die Bearbeitungssperre |
| `PUT` | `/{id}` | Dokument ändern (nur ungebucht und mit Bearbeitungssperre, sonst `409`) |
| `POST` | `/{id}/buchen` | Dokument buchen, danach ist es gesperrt |
| `POST` | `/{id}/email-versendet` | Nach dem Mail-Versand buchen und Versanddatum setzen |
| `POST` | `/{id}/pdf-speichern` | PDF zu einem gebuchten Dokument ablegen |
| `GET` | `/{id}/pdf` | Gespeicherte PDF (`404`, wenn keine da ist) |
| `POST` | `/{id}/storno` | Gebuchte Rechnung stornieren, legt ein Storno-Gegendokument an (nicht für Stornorechnungen und Entwürfe). Antwort: die Felder der Stornorechnung plus `hinweise` (Liste aus `{art, text}`, `art` = `MITSTORNIERT` oder `ENTWURF_PRUEFEN`) zu betroffenen Schlussrechnungen |
| `DELETE` | `/{id}` | Entwurf mit Begründung (`begruendung`) löschen, Eintrag im Prüfprotokoll (nicht wenn gebucht, versandt, digital angenommen oder storniert) |
| `GET` | `/{id}/historie` | Änderungsverlauf (Prüfprotokoll) eines Dokuments |
| `GET` | `/historie/nummer/{dokumentNummer}` | Verlauf über die Dokumentnummer, auch für gelöschte Dokumente |
| `GET` | `/{id}/abrechnungsverlauf` | Bisherige Rechnungen und Restbetrag zu einem Basisdokument |

### AusgangsGeschaeftsDokumentAuditController

**Basis-URL:** `/api/ausgangs-dokumente/audit`

Prüfprotokoll (GoBD) der Ausgangsdokumente.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/export` | Prüfprotokoll eines Zeitraums als CSV (mit Hash-Kette) |
| `GET` | `/anzahl` | Anzahl Einträge im Zeitraum |
| `GET` | `/verify` | Hash-Kette prüfen (intakt oder erste Bruchstelle) |
| `GET` | `/z3-paket` | GoBD-Paket (ZIP) für die Datenträgerüberlassung an den Prüfer |

### DokumentGeneratorController

**Basis-URL:** `/api/dokument-generator`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/pdf` | PDF aus Dokumentdaten erzeugen |
| `POST` | `/preview` | PDF-Vorschau (im Browser statt Download) |
| `GET` | `/zugferd-pruefung` | Reichen die Firmendaten für eine E-Rechnung? `204` = ja, sonst `422` mit Meldung |
| `POST` | `/zugferd-pdf` | ZUGFeRD-PDF (E-Rechnung) erzeugen |

### FormularTemplateController

**Basis-URL:** keine eigene (volle Pfade in der Tabelle)

Briefpapier und Dokumentvorlagen. Schreiben (POST/PUT/DELETE) nur Admins, außer `POST /api/formulare/dokumentnummer`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/formulare/template` | Gespeicherte Vorlage laden (`preset=default` liefert die Werksvorlage) |
| `PUT` | `/api/formulare/template` | Vorlage speichern *(nur Admin)* |
| `POST` | `/api/formulare/template/logo` | Logo für die Vorlage hochladen *(nur Admin)* |
| `GET` | `/api/formulare/template/assets/{filename:.+}` | Datei einer Vorlage (z. B. Logo) |
| `GET` | `/api/formulare/templates` | Alle benannten Vorlagen |
| `POST` | `/api/formulare/templates` | Benannte Vorlage speichern *(nur Admin)* |
| `POST` | `/api/formulare/templates/{name}/copy` | Vorlage kopieren *(nur Admin)* |
| `GET` | `/api/formulare/templates/{name}` | Benannte Vorlage laden |
| `PUT` | `/api/formulare/templates/{name}/rename` | Vorlage umbenennen *(nur Admin)* |
| `DELETE` | `/api/formulare/templates/{name}` | Vorlage löschen *(nur Admin)* |
| `GET` | `/api/formulare/templates/selection` | Welche Vorlage gilt für einen Dokumenttyp (und Benutzer)? |
| `POST` | `/api/formulare/templates/selection` | Vorlage zuweisen *(nur Admin)* |
| `GET` | `/api/formulare/placeholders` | Platzhalter auflösen (optional Projekt, neue Dokumentnummer) |
| `POST` | `/api/formulare/dokumentnummer` | Neue Dokumentnummer vergeben |
| `GET` | `/api/formulare/templates/{name}/textbaustein-defaults` | Standard-Textbausteine je Dokumenttyp einer Vorlage |
| `PUT` | `/api/formulare/templates/{name}/textbaustein-defaults` | Standard-Textbausteine ersetzen *(nur Admin)* |
| `GET` | `/api/formulare/templates/{name}/textbaustein-defaults/resolve` | Vor- und Nachtexte für einen Dokumenttyp auflösen |

### TextbausteinController

**Basis-URL:** `/api/textbausteine`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Textbausteine (optional `typ`) |
| `GET` | `/{id}` | Einzelner Textbaustein |
| `POST` | `/` | Textbaustein anlegen |
| `PUT` | `/{id}` | Textbaustein ändern |
| `DELETE` | `/{id}` | Textbaustein löschen |
| `GET` | `/placeholders` | Verfügbare Platzhalter |
| `GET` | `/typen` | Textbaustein-Typen |
| `GET` | `/dokumenttypen` | Dokumenttypen |

### MahnwesenController

**Basis-URL:** `/api/mahnwesen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/lauf` | Mahnlauf sofort starten (verschickt echte Mails); `409`, wenn schon einer läuft *(nur Admin)* |

---

## 6. Rechnungsübersicht und offene Posten

### RechnungsuebersichtController

**Basis-URL:** `/api/rechnungsuebersicht`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/ausgang` | Ausgangsrechnungen (`year`, `month`, `search`) |
| `GET` | `/eingang` | Eingangsrechnungen (`year`, `month`, `search`) |
| `POST` | `/merge-pdf` | Ausgewählte Rechnungen zu einer PDF zusammenfügen; nur echte PDFs, fehlende Eingangsrechnungen als IDs im Header `X-Nicht-Enthalten` |
| `POST` | `/analyze-upload` | Hochgeladenes Dokument ohne Lieferant per KI auslesen (derzeit ohne Oberfläche) |
| `POST` | `/import-upload` | Hochgeladenes Dokument speichern, optional einem Lieferanten zuordnen (derzeit ohne Oberfläche) |

### DokumentUebersichtController

**Basis-URL:** `/api/dokumentuebersicht`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/ausgang` | Ausgangsdokumente (Jahr, Monat, Suche, Nummer, Typ, Kunde, Betrag von/bis) |
| `GET` | `/eingang` | Eingangsdokumente (Jahr, Monat, Suche, Nummer, Typ, Lieferant, Betrag von/bis) |

### OffenePostenController

**Basis-URL:** `/api/offene-posten`

Was jemand sieht, regeln die Abteilungsrechte `darfRechnungenSehen` und `darfRechnungenGenehmigen`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/eingang` | Offene (unbezahlte) Eingangsrechnungen |
| `GET` | `/eingang/alle` | Alle Eingangsrechnungen, auch bezahlte |
| `PUT` | `/eingang/{id}/bezahlt` | Bezahlt-Status setzen (Skonto wird berücksichtigt) |
| `PATCH` | `/eingang/{id}/genehmigen` | Eingangsrechnung genehmigen (nur mit `darfRechnungenGenehmigen`) |
| `POST` | `/ausgang/analyze` | Ausgangsrechnung per KI auslesen (nur Vorschau; derzeit ohne Oberfläche) |
| `POST` | `/ausgang/import` | Ausgangsrechnung als offenen Posten zu einem Projekt anlegen (derzeit ohne Oberfläche) |

---

## 7. Lieferanten und Einkauf

### LieferantenController

**Basis-URL:** `/api/lieferanten`

Dokumente und Statistiken nur für die Dokumenttypen, die die Abteilung sehen darf. Ein Teil ist auch mit Handy-Token erreichbar (siehe Absicherung).

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Lieferanten suchen (`q`, `name`, `typ`, `ort`, `email`, `page`, `size`) |
| `POST` | `/` | Lieferant anlegen |
| `PUT` | `/{id}` | Lieferant ändern |
| `GET` | `/{id}` | Lieferant mit Details (`nurStammdaten=true` für die schnelle Variante) |
| `GET` | `/{id}/statistik` | Kennzahlen des Lieferanten |
| `GET` | `/{id}/email-verlauf` | Mail-Verlauf mit dem Lieferanten |
| `GET` | `/{id}/artikelpreise` | Artikelpreise des Lieferanten (seitenweise, Suche, Sortierung) |
| `GET` | `/{id}/emails` | Mails des Lieferanten (`limit`, `q`) |
| `POST` | `/{id}/emails` | Mail an den Lieferanten senden (multipart, mit Anhängen) |
| `PUT` | `/{id}/artikelpreise/{artikelId}` | Artikelpreis ändern |
| `POST` | `/{id}/artikelpreise` | Artikelpreis anlegen |
| `GET` | `/emails` | Alle E-Mail-Adressen aller Lieferanten |
| `POST` | `/{id}/emails` | E-Mail-Adresse zum Lieferanten hinzufügen (JSON) |
| `GET` | `/{id}/dokumente` | Dokumente des Lieferanten (optional `typ`) |
| `GET` | `/{id}/dokumente/positionssuche` | Dokumente über ihre Positionen suchen (Material, Charge, Abmessung …) |
| `POST` | `/{id}/dokumente` | Dokument hochladen |
| `POST` | `/{id}/dokumente/analyze` | Dokument per KI auslesen, ohne zu speichern (Import Schritt 1) |
| `POST` | `/{id}/dokumente/import` | Dokument mit geprüften Daten speichern (Import Schritt 2) |
| `POST` | `/{lieferantId}/dokumente/{dokumentId}/verknuepfen` | Dokument mit anderen Dokumenten verknüpfen (Belegkette) |
| `GET` | `/berechtigungen` | Rechte eines Mitarbeiters für Lieferanten-Dokumente (`token`) |
| `GET` | `/{lieferantId}/dokumente/{dokumentId}/download` | Lieferanten-Dokument herunterladen |
| `GET` | `/{id}/notizen` | Notizen (optional Suche `q`) |
| `POST` | `/{id}/notizen` | Notiz anlegen |
| `DELETE` | `/{id}/notizen/{notizId}` | Notiz löschen |
| `GET` | `/{id}/bilder` | Bilder des Lieferanten |
| `GET` | `/bilder/file/{dateiname:.+}` | Bilddatei |
| `GET` | `/bilder/file/{dateiname:.+}/vorschau` | Verkleinerte Vorschau (höchstens 300 px) |
| `POST` | `/{id}/bilder` | Bild hochladen |
| `DELETE` | `/{id}/bilder/{bildId}` | Bild löschen |

### LieferantDokumentController

**Basis-URL:** `/api/lieferant-dokumente`

Löschen (jedes DELETE) nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `PUT` | `/{dokumentId}` | Dokument und Geschäftsdaten ändern (nur mit Bearbeitungssperre, sonst `409`) |
| `POST` | `/lieferant/{lieferantId}/reanalyze` | Alle Dokumente eines Lieferanten neu per KI lesen *(nur Admin)* |
| `GET` | `/{dokumentId}` | Einzelnes Dokument mit Geschäftsdaten und Verknüpfungen |
| `POST` | `/{dokumentId}/reanalyze` | Ein Dokument neu per KI lesen |
| `POST` | `/relink-all` | Fehlende Belegketten-Verknüpfungen aller Lieferanten ergänzen *(nur Admin)* |
| `POST` | `/lieferant/{lieferantId}/relink` | Dasselbe für einen Lieferanten *(nur Admin)* |
| `POST` | `/process-assigned-emails` | Anhänge aller Lieferanten-Mails zu Dokumenten machen (KI) *(nur Admin)* |
| `POST` | `/process-email/{emailId}` | Anhänge einer Mail verarbeiten *(nur Admin)* |
| `POST` | `/lieferant/{lieferantId}/process-emails` | Anhänge aller Mails eines Lieferanten verarbeiten *(nur Admin)* |
| `GET` | `/duplicates` | Doppelte Dokumente anzeigen (gleiche Nummer und Lieferant) *(nur Admin)* |
| `DELETE` | `/duplicates` | Doppelte Dokumente löschen, das älteste bleibt *(nur Admin)* |
| `DELETE` | `/{id}` | Dokument löschen *(nur Admin)* |
| `GET` | `/{dokumentId}/download` | Dokument über seine ID herunterladen |

### LieferantDokumentPositionController

**Basis-URL:** `/api/bestellungen-uebersicht/positionen`

Lieferanten-Rechnungen nach Positionen auf Projekte/Kostenstellen aufteilen.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{geschaeftsdokumentId}` | Positionen mit ihrem aktuellen Ziel |
| `POST` | `/{geschaeftsdokumentId}/auslesen` | Positionen per KI (nach)lesen |
| `POST` | `/{geschaeftsdokumentId}/vorschau` | Aufteilung durchrechnen, ohne zu speichern |
| `POST` | `/{geschaeftsdokumentId}/zuordnen` | Aufteilung speichern |

### LieferantDokumentWartungController

**Basis-URL:** `/api/admin/lieferant-dokumente`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/werkstoffzeugnisse/nachlesen` | Werkstoffzeugnisse ohne Nummer/Positionen per KI nachlesen; `409`, wenn schon ein Lauf läuft *(nur Admin)* |

### LieferantWartungController

**Basis-URL:** `/api/admin/lieferanten`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/{id}/reprocess-attachments` | PDF-Anhänge aller Mails eines Lieferanten neu verarbeiten *(nur Admin)* |

### LieferantReklamationController

**Basis-URL:** `/api/reklamationen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/lieferant/{lieferantId}` | Reklamationen eines Lieferanten |
| `GET` | `/{id}` | Eine Reklamation mit Bildern (Detailseite der Handy-App) |
| `PATCH` | `/{id}/status` | Status einer Reklamation ändern |
| `DELETE` | `/{id}` | Reklamation löschen |
| `POST` | `/lieferant/{lieferantId}` | Reklamation anlegen |
| `POST` | `/{id}/bilder` | Foto zur Reklamation hochladen |
| `GET` | `/lieferscheine/search` | Lieferscheine eines Lieferanten suchen (`lieferantId`, `query`) |

### BestellungController

**Basis-URL:** `/api/bestellungen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/offen` | Offene, noch nicht bestellte Positionen |
| `PATCH` | `/{id}` | Als bestellt oder nicht bestellt markieren (`bestellt=true` oder `false`) |
| `GET` | `/projekt/{projektId}/pdf` | Bestell-PDF für ein Projekt |
| `GET` | `/lieferant/{lieferantId}/pdf` | Bestell-PDF für einen Lieferanten |

### BestellungsUebersichtController

**Basis-URL:** `/api/bestellungen-uebersicht`

Belegketten (Bestellung, AB, Lieferschein, Rechnung), Kostenstellen und Projektzuordnung im Einkauf.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Belegketten, nach Status gruppiert |
| `POST` | `/rechnung-verknuepfen` | Rechnung an eine Bestellung hängen (auch Teillieferungen und Teilrechnungen) |
| `GET` | `/ketten-vorschlaege` | Passende Dokumente für eine Kette vorschlagen, beste zuerst |
| `POST` | `/ketten-verknuepfen` | Beliebiges Dokument an die Kette hängen |
| `POST` | `/abhaengen` | „Gehört nicht dazu“: alle Verknüpfungen eines Dokuments lösen |
| `POST` | `/rechnung-hochladen` | Papierrechnung zu einer Bestellung hochladen (wird im Hintergrund gelesen) |
| `POST` | `/ausblenden` | Kette aus der Übersicht ausblenden |
| `POST` | `/einblenden` | Ausgeblendete Kette wieder einblenden |
| `GET` | `/geschaeftsdaten/{dokId}` | Geschäftsdaten eines Dokuments zum Bearbeiten |
| `PUT` | `/geschaeftsdaten/{dokId}` | Geschäftsdaten ändern |
| `GET` | `/kostenstellen` | Aktive Kostenstellen |
| `GET` | `/belege-offen` | Belege ohne Kostenstellen-Zuordnung (nicht aus dem Mail-Import) |
| `GET` | `/belegdaten/{belegId}` | Daten eines Belegs für die Zuordnung |
| `GET` | `/beleg-zuordnungen/{belegId}` | Kostenstellen-Zuordnungen eines Belegs |
| `POST` | `/beleg-zuordnen` | Beleg Kostenstellen zuordnen |
| `POST` | `/zuordnen` | Rechnung anteilig Projekten zuordnen |
| `POST` | `/lagerbestellung/{dokId}` | Rechnung als Lagerbestellung markieren (keine Projektzuordnung nötig) |
| `DELETE` | `/zuordnung/{dokId}` | Projektzuordnungen einer Rechnung aufheben |
| `GET` | `/zuordnungen/{dokId}` | Zuordnungen eines Dokuments |
| `GET` | `/zuordnungen/kostenstelle/{kostenstelleId}` | Alle Zuordnungen einer Kostenstelle |
| `GET` | `/kostenstellen/auswertung` | Kostenstellen-Auswertung eines Jahres (optional Monat) mit Vorjahresvergleich |

### VendorInvoiceController

**Basis-URL:** `/api/admin/vendor-invoices`

Rechnungsabruf bei Händlern (Microsoft, Amazon). Ganzer Pfad nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/status` | Ist der Abruf für Microsoft/Amazon/Apple eingerichtet und eingeschaltet? *(nur Admin)* |
| `POST` | `/fetch` | Rechnungen aller Händler jetzt abrufen *(nur Admin)* |
| `POST` | `/fetch/microsoft` | Nur Microsoft-Rechnungen abrufen *(nur Admin)* |
| `POST` | `/fetch/amazon` | Nur Amazon-Rechnungen abrufen *(nur Admin)* |

---

## 8. Artikel, Kategorien und Kalkulation

### ArtikelController

**Basis-URL:** `/api/artikel`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Artikel anlegen; ohne `artikelnummer` vergibt der Server die nächste freie (`A-` + sechs Ziffern) |
| `GET` | `/naechste-artikelnummer` | Vorschau der nächsten automatischen Artikelnummer (`{"artikelnummer": "A-004143"}`), reserviert nichts |
| `POST` | `/import/headers` | Spaltenköpfe einer Importdatei lesen |
| `POST` | `/import/analyze` | Import mit Spaltenzuordnung vorab prüfen |
| `POST` | `/import` | Artikel importieren (Lieferant, optional Kategorie und Preiskorrektur) |
| `GET` | `/lieferanten` | Namen aller aktiven Lieferanten (für den Filter) |
| `GET` | `/produktlinien` | Alle Produktlinien |
| `GET` | `/werkstoffe` | Alle Werkstoffe |
| `GET` | `/werkstoffe/details` | Werkstoffe mit Details |
| `GET` | `/match` | Passende Artikel zu Produktname und Produktlinie |
| `GET` | `/` | Artikel suchen und filtern (seitenweise, sortierbar); im Freitext `q` zählen Zahlen als ganze Zahl („20“ trifft nicht „120“), Komma = Punkt, Umlaut = Umschreibung |
| `POST` | `/{id}/externe-nummer` | Artikelnummer eines Lieferanten hinterlegen |
| `PATCH` | `/{id}/dokumenttexte` | Kurzbeschreibung, Kundentext und Verkaufsaufschlag ändern |
| `PUT` | `/{id}/stammdaten` | Stammdaten ersetzen (Name, Nummern, EAN, Einheit, Kategorie, Werkstoff, Normen, Maße); 400 bei ungültigen Werten, 409 bei doppelter Nummer/EAN oder veraltetem Stand (`version`) |
| `GET` | `/{id}/detail` | Artikel mit allen Lieferantenpreisen und Preisverlauf |
| `POST` | `/{id}/dokumente` | Vorschaubild oder Zusatzdokument (Zulassung, Zeichnung, Datenblatt …) hochladen |
| `GET` | `/{id}/dokumente` | Dokumente eines Artikels |
| `GET` | `/dokumente/{dokumentId}/datei` | Datei eines Artikel-Dokuments |
| `DELETE` | `/dokumente/{dokumentId}` | Artikel-Dokument löschen |
| `GET` | `/filteroptionen` | Auswahlwerte für die Filterleiste mit Klartext |

### AufschlagController

**Basis-URL:** `/api/aufschlaege`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/berechtigung` | Darf der angemeldete Benutzer die Aufschläge ändern? (`{“darfAendern”: true/false}`, für jeden angemeldeten Benutzer) |
| `GET` | `/` | Allgemeiner Aufschlag und je Artikelkategorie der eigene und der geltende Aufschlag *(Recht „Aufschläge ändern” oder Admin, sonst 403)* |
| `PUT` | `/allgemein` | Allgemeinen Aufschlag setzen, `prozent` leer = keiner (0 bis 999,99; 400 sonst) *(Recht oder Admin)* |
| `PUT` | `/kategorien/{id}` | Aufschlag einer Kategorie setzen, `prozent` leer = keiner; 404 bei unbekannter Kategorie *(Recht oder Admin)* |

### WerkstoffController

**Basis-URL:** `/api/werkstoffe`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Werkstoffe mit der Zahl der Artikel, die sie benutzen |
| `POST` | `/` | Werkstoff anlegen (Name Pflicht und eindeutig, Dichte 0,1 bis 25 kg/dm³); 400 bei ungültigen Werten, 409 bei doppeltem Namen |
| `PUT` | `/{id}` | Werkstoff ändern; 400 bei ungültigen Werten, 404 bei unbekannter ID, 409 bei doppeltem Namen |
| `DELETE` | `/{id}` | Werkstoff löschen; 409, solange Artikel ihn benutzen |

### ArtikelKategorieController

**Basis-URL:** `/api/artikel/kategorien`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/haupt` | Hauptkategorien |
| `GET` | `/alle` | Alle Kategorien |
| `GET` | `/{parentId}/unterkategorien` | Unterkategorien |
| `POST` | `/` | Kategorie anlegen |
| `PUT` | `/{id}/rollen` | Typische Liefer-Rollen einer Kategorie setzen |
| `GET` | `/{id}/effektive-rollen` | Wirksame Liefer-Rollen (eigene oder von der Oberkategorie geerbt) |

### ProduktkategorieController

**Basis-URL:** `/api/produktkategorien`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Produktkategorien (`light=true` für die schlanke Liste) |
| `GET` | `/haupt` | Hauptkategorien (`light`) |
| `GET` | `/{id}` | Einzelne Kategorie |
| `GET` | `/{kategorieId}/analyse` | Auswertung einer Kategorie (optional Jahr) |
| `GET` | `/auswertung` | Übersicht: Hauptordner oder Unterordner von `parentId` mit Projekten, Stunden, Menge, Stunden je Einheit (optional `jahr`, nur abgeschlossene Projekte) |
| `GET` | `/{parentId}/unterkategorien` | Unterkategorien (`light`) |
| `GET` | `/suche` | Unterste Kategorien per Suchbegriff (`q`) |
| `POST` | `/` | Kategorie anlegen (multipart, optional Bild) |
| `PATCH` | `/{kategorieId}/beschreibung` | Beschreibung ändern |
| `PUT` | `/{id}` | Kategorie ändern (multipart, optional Bild, `bildEntfernen`) |
| `PATCH` | `/{id}/verschieben` | In anderen Ordner verschieben (`parentId`, `null` = Hauptordner) |
| `GET` | `/{id}/loeschpruefung` | Was hängt am Ordner (Unterordner, Leistungen, Projekte, Zeiten)? |
| `POST` | `/{id}/loeschen` | Löschen mit Bezügen: `UMZIEHEN` (in `zielKategorieId`) oder `ALLES` |
| `DELETE` | `/{kategorieId}` | Leeren Ordner löschen (sonst 409) |

### AbteilungController

**Basis-URL:** `/api/abteilungen`

Abteilungen lesen (alle Angemeldeten) und pflegen (nur Admin). Fehler kommen als `{ "message": "…" }` in Klartext.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Abteilungen (`id`, `name`) |
| `POST` | `/` | Abteilung anlegen, Body `{ "name" }` *(nur Admin; 400 leerer/zu langer Name, 409 Name schon vergeben)* |
| `PUT` | `/{id}` | Abteilung umbenennen, Body `{ "name" }` *(nur Admin; 404, 409 wie oben)* |
| `DELETE` | `/{id}` | Abteilung samt Rechten löschen *(nur Admin; 409 solange Mitarbeiter oder Arbeitsgänge zugeordnet sind)* |

### ArbeitsgangController

**Basis-URL:** nur `/api` (volle Pfade in der Tabelle)

Lesen dürfen alle Angemeldeten (auch die Handy-App), anlegen, ändern und löschen nur Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/arbeitsgaenge` | Arbeitsgang anlegen *(nur Admin)* |
| `DELETE` | `/api/arbeitsgaenge/{arbeitsgangID}` | Arbeitsgang löschen *(nur Admin)* |
| `GET` | `/api/arbeitsgaenge` | Alle Arbeitsgänge |
| `POST` | `/api/arbeitsgaenge/stundensaetze` | Stundensätze mehrerer Arbeitsgänge speichern *(nur Admin)* |
| `PUT` | `/api/arbeitsgaenge/{id}/stundensatz` | Stundensatz eines Arbeitsgangs ändern *(nur Admin)* |

### ArbeitszeitartController

**Basis-URL:** `/api/arbeitszeitarten`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Aktive Arbeitszeitarten (für Dokumente) |
| `GET` | `/alle` | Alle Arbeitszeitarten, auch inaktive |
| `GET` | `/{id}` | Einzelne Arbeitszeitart |
| `POST` | `/` | Arbeitszeitart anlegen |
| `PUT` | `/{id}` | Arbeitszeitart ändern |
| `DELETE` | `/{id}` | Arbeitszeitart löschen |
| `POST` | `/{id}/deaktivieren` | Arbeitszeitart deaktivieren (statt löschen) |

### VerrechnungslohnController

**Basis-URL:** `/api/verrechnungslohn`

Rechner „Was muss meine Stunde kosten?“.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Verrechnungslohn eines Jahres berechnen (`jahr`, optional `internProzent`) |
| `POST` | `/uebernehmen` | Errechneten Satz auf alle Arbeitsgänge des Jahres schreiben *(nur Admin)* |

### LeistungController

**Basis-URL:** `/api/leistungen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Leistungen |
| `POST` | `/` | Leistung anlegen |
| `PUT` | `/{id}` | Leistung ändern |
| `GET` | `/{id}/verwendung` | In welchen Ausgangsdokumenten steckt die Leistung? |
| `DELETE` | `/{id}` | Leistung löschen |

### SchnittbilderController

**Basis-URL:** `/api/schnittbilder`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Passende Schnittbilder zu Artikel oder Kategorie |
| `GET` | `/{id}` | Einzelnes Schnittbild |

### UtilsController

**Basis-URL:** `/api/utils`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/verrechnungseinheiten` | Alle Verrechnungseinheiten |

### GaebImportController

**Basis-URL:** `/api/import`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/gaeb` | GAEB-Datei (Leistungsverzeichnis) einlesen, liefert die Blöcke zurück |

---

## 9. E-Mail

### UnifiedEmailController

**Basis-URL:** `/api/emails`

Gemeinsames Postfach. Jeder sieht nur Mails aus Postfächern, die für ihn freigegeben sind.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/absender-postfaecher` | Auswahl „Senden von“ (eigenes Postfach zuerst) |
| `GET` | `/{emailId}/attachments/{attachmentId}` | Anhang herunterladen |
| `POST` | `/import` | Postfach sofort abrufen *(nur Admin)* |
| `POST` | `/{emailId}/block-sender` | Absender sperren |
| `POST` | `/{emailId}/mark-spam` | Als Spam markieren |
| `POST` | `/{emailId}/mark-not-spam` | Kein Spam |
| `POST` | `/{emailId}/mark-not-newsletter` | Newsletter zurück in den Posteingang |
| `POST` | `/{emailId}/confirm-newsletter` | Newsletter bestätigen |
| `POST` | `/spam-model/bootstrap` | Spam-Modell aus einer CSV-Datei anlernen *(nur Admin)* |
| `GET` | `/spam-model/stats` | Stand des Spam-Lernmodells |
| `POST` | `/spam-model/rescore` | Alle Mails mit dem Spam-Modell neu bewerten (`400`, solange nicht angelernt) *(nur Admin)* |
| `GET` | `/projekt/{projektId}` | Mails eines Projekts |
| `GET` | `/anfrage/{anfrageId}` | Mails einer Anfrage |
| `GET` | `/lieferant/{lieferantId}` | Mails eines Lieferanten |
| `GET` | `/unassigned` | Nicht zugeordnete Mails |
| `GET` | `/inquiries` | Mails, die nach einer Anfrage aussehen |
| `GET` | `/new/projekt` | Neue Mails zu Projekten |
| `GET` | `/new/anfrage` | Neue Mails zu Anfragen |
| `GET` | `/new/lieferant` | Neue Mails von Lieferanten |
| `GET` | `/search` | Mails durchsuchen (`q`) |
| `GET` | `/inbox` | Ordner Posteingang |
| `GET` | `/projects` | Ordner Projekte |
| `GET` | `/offers` | Ordner Anfragen/Angebote |
| `GET` | `/suppliers` | Ordner Lieferanten |
| `GET` | `/tax-advisors` | Ordner Steuerberater |
| `GET` | `/sent` | Ordner Gesendet |
| `GET` | `/trash` | Ordner Papierkorb |
| `GET` | `/spam` | Ordner Spam |
| `GET` | `/newsletter` | Ordner Newsletter |
| `GET` | `/starred` | Markierte Mails |
| `GET` | `/{id:[0-9]+}` | Einzelne Mail |
| `GET` | `/{emailId}/thread` | Ganzer Verlauf zu einer Mail |
| `POST` | `/admin/backfill-attachment-filenames` | Kodierte Anhang-Dateinamen reparieren *(nur Admin)* |
| `POST` | `/admin/backfill-xml-to-pdf` | Lieferanten-Dokumente von XML auf die PDF aus derselben Mail umstellen *(nur Admin)* |
| `POST` | `/{id}/assign/projekt/{projektId}` | Mail einem Projekt zuordnen |
| `POST` | `/{id}/assign/anfrage/{anfrageId}` | Mail einer Anfrage zuordnen |
| `POST` | `/{id}/assign/lieferant/{lieferantId}` | Mail einem Lieferanten zuordnen |
| `POST` | `/{id}/unassign` | Zuordnung aufheben |
| `DELETE` | `/{id}` | Mail in den Papierkorb |
| `DELETE` | `/{id}/permanent` | Mail endgültig löschen |
| `POST` | `/bulk/move-to-folder` | Mehrere Mails verschieben (`inbox`, `trash`, `spam`, `newsletter`) |
| `POST` | `/mark-all-read` | Alle Mails eines Ordners als gelesen markieren (`folder`) |
| `POST` | `/{id}/mark-read` | Als gelesen markieren |
| `POST` | `/{id}/toggle-star` | Markierung an/aus |
| `GET` | `/{emailId}/attachments/download-all` | Alle Anhänge als ZIP |
| `GET` | `/stats` | Zähler je Ordner |
| `POST` | `/scan-spam` | Spam-Prüfung für noch nicht geprüfte Mails *(nur Admin)* |
| `POST` | `/scan-inquiries` | Anfrage-Erkennung für noch nicht geprüfte Mails *(nur Admin)* |
| `POST` | `/scan-assignments` | Spam neu prüfen und nicht zugeordnete Mails automatisch zuordnen *(nur Admin)* |
| `GET` | `/contacts` | Kontakte für die Empfänger-Auswahl (`q`) |
| `GET` | `/{id}/possible-assignments` | Mögliche Zuordnungen einer Mail |
| `POST` | `/backfill-parents` | Antworten nachträglich mit ihrer Ursprungsmail verknüpfen *(nur Admin)* |
| `POST` | `/admin/rebuild-threads` | Verläufe bereinigen (Standard Probelauf, `probelauf=false` speichert) *(nur Admin)* |
| `POST` | `/scan-steuerberater` | Steuerberater-Mails nachträglich verarbeiten (Lohnabrechnungen, BWA) *(nur Admin)* |
| `POST` | `/send` | Neue Mail senden (multipart, mit Anhängen) |
| `POST` | `/{emailId}/reply` | Auf eine Mail antworten (multipart) |

### EmailDraftController

**Basis-URL:** `/api/emails/drafts`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Entwürfe, die der Benutzer sehen darf |
| `GET` | `/count` | Anzahl Entwürfe |
| `GET` | `/{id}` | Einzelner Entwurf |
| `POST` | `/` | Entwurf anlegen (JSON) |
| `PUT` | `/{id}` | Entwurf ändern (JSON) |
| `POST` | `/` | Entwurf mit Anhängen anlegen (multipart) |
| `PUT` | `/{id}` | Entwurf mit Anhängen ändern (multipart) |
| `GET` | `/{id}/attachments/{attachmentId}` | Anhang eines Entwurfs herunterladen |
| `DELETE` | `/{id}` | Entwurf löschen |

### EmailController

**Basis-URL:** `/api/email`

Versand von Geschäftsdokumenten per Mail.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/from-addresses` | Aktive Absender-Adressen (die des Benutzers zuerst) |
| `GET` | `/dokument-absender` | Läuft der Dokumentversand über ein eigenes Postfach, und unter welcher Adresse? |
| `POST` | `/beautify` | Mail-Text per KI verbessern |
| `POST` | `/preview` | Vorschau einer Dokument-Mail (Projekt) |
| `POST` | `/preview/anfrage` | Vorschau einer Dokument-Mail (Anfrage) |
| `POST` | `/send` | Dokument-Mail senden (Projekt) |
| `POST` | `/send/anfrage` | Dokument-Mail senden (Anfrage) |

### EmailTemplateController

**Basis-URL:** `/api/email/template`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Betreff und Text für eine Dokument-Mail erzeugen (bei Angebot und Nachtragsangebot mit Freigabe-Link) |

### EmailTextTemplateController

**Basis-URL:** `/api/email-textvorlagen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Mail-Textvorlagen |
| `GET` | `/{id}` | Einzelne Vorlage |
| `POST` | `/` | Vorlage anlegen |
| `PUT` | `/{id}` | Vorlage ändern |
| `DELETE` | `/{id}` | Vorlage löschen |
| `GET` | `/dokumenttypen` | Dokumenttypen für Vorlagen |
| `GET` | `/placeholders` | Verfügbare Platzhalter |

### EmailSignatureController

**Basis-URL:** `/api/email/signatures`

Firmen-Vorlage und System-Signatur dürfen nur Admins ändern.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Signaturen, die der Benutzer sehen darf |
| `GET` | `/default` | Standard-Signatur zum Vorbelegen (Admins dürfen nach der eines anderen Benutzers fragen) |
| `GET` | `/firmen-vorlagen` | Firmen-Vorlagen |
| `PUT` | `/{id}/firmen-vorlage` | Signatur zur Firmen-Vorlage machen *(nur Admin)* |
| `DELETE` | `/{id}/firmen-vorlage` | Signatur ist keine Firmen-Vorlage mehr *(nur Admin)* |
| `GET` | `/system-default` | System-Signatur für automatische Mails |
| `PUT` | `/{id}/system-default` | Signatur als System-Signatur festlegen *(nur Admin)* |
| `PUT` | `/{id}/standard` | Eigene Standard-Signatur festlegen |
| `DELETE` | `/standard` | Eigene Standard-Signatur entfernen |
| `POST` | `/` | Signatur speichern |
| `GET` | `/{id}/preview` | Vorschau |
| `GET` | `/{id}` | Einzelne Signatur |
| `GET` | `/{id}/images/{imageId}` | Bild einer alten Signatur |
| `DELETE` | `/{id}` | Signatur löschen |
| `DELETE` | `/{id}/images/{imageId}` | Bild einer Signatur löschen |

### EmailKiClassificationController

**Basis-URL:** `/api/email-ki`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/status` | Ist die KI-Zuordnung (Gemini) eingeschaltet? |
| `POST` | `/classify/{emailId}` | Mail per KI einordnen (nur Vorschlag) |
| `POST` | `/classify-and-assign/{emailId}` | Mail einordnen und ab 60 % Sicherheit direkt zuordnen |

### OutOfOfficeController

**Basis-URL:** `/api/email/outofoffice`

Abwesenheitsnotizen für Postfächer.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Abwesenheitsnotizen |
| `GET` | `/postfaecher` | Postfächer, die der Benutzer auswählen darf |
| `POST` | `/` | Abwesenheitsnotiz speichern |
| `DELETE` | `/{id}` | Abwesenheitsnotiz löschen |

### PostfachController

**Basis-URL:** `/api/postfaecher`

Ganzer Pfad nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Postfächer *(nur Admin)* |
| `POST` | `/` | Postfach anlegen *(nur Admin)* |
| `PUT` | `/{id}` | Postfach ändern *(nur Admin)* |
| `PUT` | `/{id}/sichtbarkeit` | Festlegen, wer das Postfach sehen darf *(nur Admin)* |
| `DELETE` | `/{id}` | Postfach löschen *(nur Admin)* |
| `POST` | `/test` | Verbindung testen *(nur Admin)* |

---

## 10. Zeiterfassungs-App (Handy)

### ZeiterfassungApiController

**Basis-URL:** `/api/zeiterfassung`

Für die Handy-App; angemeldet wird mit dem Mitarbeiter-Token (siehe Absicherung).

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/projekte` | Offene Projekte (`limit`, `search`) |
| `GET` | `/kategorien` | Alle Kategorien mit Pfad |
| `GET` | `/kategorien/{projektId}` | Kategorien eines Projekts |
| `GET` | `/arbeitsgaenge/{token}` | Arbeitsgänge der Abteilung des Mitarbeiters |
| `GET` | `/lieferanten` | Aktive Lieferanten (`limit`, `search`) |
| `POST` | `/start` | Zeitbuchung starten |
| `POST` | `/stop` | Laufende Buchung beenden |
| `POST` | `/pause` | Pause starten |
| `GET` | `/aktiv/{token}` | Laufende Buchung |
| `GET` | `/heute/{token}` | Heute gearbeitete Stunden |
| `GET` | `/projekte/{projektId}` | Einzelnes Projekt (auch beendete) |
| `GET` | `/projekte/{projektId}/bilder` | Bilder eines Projekts |
| `GET` | `/buchungszeitfenster/{token}` | Erlaubtes Buchungszeitfenster |
| `GET` | `/saldo/{token}` | Saldo: Urlaub, Monatsstunden, Gesamtsaldo |
| `GET` | `/buchungen/{token}` | Buchungen eines Tages (`datum`, sonst heute) |
| `GET` | `/feiertage` | Feiertage eines Jahres |
| `GET` | `/urlaubsverfall/{token}` | Hinweis, wenn Resturlaub verfällt |

### LangzeitkrankmeldungMobileController

**Basis-URL:** `/api/zeiterfassung/langzeitkrankmeldung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{token}` | Laufende Langzeitkrankmeldung des Mitarbeiters (leer, wenn keine) |

### PushSubscriptionController

**Basis-URL:** `/api/push`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/vapid-key` | Öffentlicher Schlüssel für Push-Benachrichtigungen |
| `POST` | `/subscribe` | Gerät für Push anmelden |
| `POST` | `/unsubscribe` | Gerät abmelden |

### ZeiterfassungController

**Basis-URL:** keine eigene (volle Pfade in der Tabelle)

Liefert die Handy-App (PWA) aus, kein JSON. Ohne Anmeldung erreichbar.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/zeiterfassung` | Weiterleitung auf `/zeiterfassung/` |
| `GET` | `/zeiterfassung/`<br>`/zeiterfassung/{path:[^\.]*}`<br>`/zeiterfassung/{p1:[^\.]*}/{p2:[^\.]*}`<br>`/zeiterfassung/{p1:[^\.]*}/{p2:[^\.]*}/{p3:[^\.]*}`<br>`/zeiterfassung/{p1:[^\.]*}/{p2:[^\.]*}/{p3:[^\.]*}/{p4:[^\.]*}`<br>`/zeiterfassung/{p1:[^\.]*}/{p2:[^\.]*}/{p3:[^\.]*}/{p4:[^\.]*}/{p5:[^\.]*}` | App-Seite (`index.html`) für alle App-Pfade ohne Punkt, bis fünf Ebenen tief |

---

## 11. Zeitverwaltung (Büro)

### ZeitverwaltungController

**Basis-URL:** `/api/zeitverwaltung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/buchungen` | Zeitbuchungen eines Mitarbeiters im Monat |
| `PUT` | `/buchungen/{id}` | Buchung ändern (mit Bearbeiter und Änderungsgrund, Verlauf bleibt) |
| `DELETE` | `/buchungen/{id}` | Buchung stornieren (kein echtes Löschen) |
| `GET` | `/buchungen/{id}/historie` | Änderungsverlauf einer Buchung |
| `GET` | `/aenderungsgruende` | Auswahlliste der Änderungsgründe |
| `POST` | `/buchungen` | Buchung nachtragen |
| `GET` | `/kalender` | Monatskalender mit Buchungen, Feiertagen und Sollstunden |
| `GET` | `/feiertage` | Feiertage eines Jahres |
| `GET` | `/feiertage/zwischen` | Feiertage in einem Zeitraum (auch für die Handy-App) |
| `POST` | `/feiertage/regenerieren` | Feiertage für einen Jahresbereich neu erzeugen |
| `GET` | `/zeitkonten` | Zeitkonto-Stand aller Mitarbeiter |
| `GET` | `/zeitkonten/{mitarbeiterId}` | Zeitkonto-Stand eines Mitarbeiters |
| `POST` | `/zeitkonten/{mitarbeiterId}/vorschau` | Vorschau eines Zeitkonto-Wechsels |
| `PUT` | `/zeitkonten/{mitarbeiterId}` | Zeitkonto-Wechsel übernehmen |
| `POST` | `/zeitkonten/uebernehmen` | Zeitkonto-Wechsel für mehrere Mitarbeiter |
| `POST` | `/zeitkonten/{mitarbeiterId}/ausschalten` | Zeitkonto ausschalten |
| `GET` | `/auswertung/projekt/{projektId}/pdf` | Projektauswertung als PDF |
| `GET` | `/auswertung/projekt/{projektId}` | Projektauswertung nach Tätigkeiten/Arbeitsgängen |
| `POST` | `/saldo-cache/warmup` | Monatssalden aller aktiven Mitarbeiter neu in den Zwischenspeicher rechnen *(nur Admin)* |

### ZeitkontoKorrekturController

**Basis-URL:** `/api/zeitkonto/korrekturen`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Korrektur anlegen (Gutschrift oder Abzug) |
| `PUT` | `/{id}` | Korrektur ändern (mit Verlauf) |
| `DELETE` | `/{id}` | Korrektur stornieren (kein echtes Löschen) |
| `GET` | `/mitarbeiter/{mitarbeiterId}` | Korrekturen eines Mitarbeiters (`alleAnzeigen`) |
| `GET` | `/{id}/historie` | Verlauf einer Korrektur |
| `GET` | `/mitarbeiter/{mitarbeiterId}/summe` | Summe der Korrekturen im Jahr |

### ZeitkontenmodellController

**Basis-URL:** `/api/zeitverwaltung/zeitkontenmodelle`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Zeitkontenmodelle |
| `POST` | `/` | Modell anlegen |
| `PUT` | `/{id}` | Modell ändern |
| `DELETE` | `/{id}` | Modell löschen (`expectedVersion`) |

### MitarbeiterSaldoController

**Basis-URL:** keine eigene (volle Pfade in der Tabelle)

Ohne Klassen-Mapping.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/zeitverwaltung/mitarbeiter/{mitarbeiterId}/saldo` | Saldo eines Mitarbeiters (optional Jahr) |

### MonatsabschlussController

**Basis-URL:** `/api/zeitverwaltung/monatsabschluesse`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/berechtigung` | Darf der Benutzer Monate abschließen? |
| `GET` | `/{mitarbeiterId}/{jahr}/{monat}` | Status eines Monats |
| `POST` | `/{mitarbeiterId}/{jahr}/{monat}/abschliessen` | Monat abschließen |
| `POST` | `/{mitarbeiterId}/{jahr}/{monat}/oeffnen` | Monat wieder öffnen |

### MonatsabschlussUebersichtController

**Basis-URL:** `/api/zeitverwaltung/monatsabschluesse`

Teilt sich die Basis-URL mit dem `MonatsabschlussController`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/uebersicht` | Monatsübersicht aller Mitarbeiter (Filter, seitenweise) |
| `GET` | `/vergleich` | Monatsvergleich |
| `GET` | `/jahresvergleich` | Jahresvergleich |
| `POST` | `/sammelabschluss` | Mehrere ausgewählte Monate auf einmal abschließen |

### DatevExportController

**Basis-URL:** `/api/zeitverwaltung/monatsabschluesse/datev`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/vorpruefung` | Vor dem DATEV-Export prüfen |
| `POST` | `/export` | DATEV-Exportdatei erzeugen |

### DatevKonfigurationController

**Basis-URL:** `/api/zeitverwaltung/monatsabschluesse/datev/konfiguration`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | DATEV-Einstellungen lesen (ändern über `/api/settings/datev`) |

---

## 12. Mitarbeiter, Abwesenheiten und Lohn

### MitarbeiterController

**Basis-URL:** `/api/mitarbeiter`

Den App-Anmeldecode (Login-Token) eines Mitarbeiters sehen nur Admins und der Mitarbeiter selbst.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Mitarbeiter |
| `GET` | `/{id}` | Einzelner Mitarbeiter |
| `POST` | `/` | Mitarbeiter anlegen |
| `PUT` | `/{id}` | Mitarbeiter ändern |
| `DELETE` | `/{id}` | Mitarbeiter löschen |
| `POST` | `/{id}/dokumente` | Dokument hochladen |
| `GET` | `/{id}/dokumente` | Dokumente des Mitarbeiters |
| `GET` | `/{id}/qr-code` | QR-Code zum Anmelden in der Handy-App |
| `POST` | `/{id}/regenerate-token` | Neuen App-Anmeldecode erzeugen |
| `GET` | `/by-token/{token}` | Mitarbeiter zum App-Anmeldecode (Handy-App) |
| `GET` | `/{id}/notizen` | Notizen |
| `POST` | `/{id}/notizen` | Notiz anlegen |
| `DELETE` | `/notizen/{notizId}` | Notiz löschen |
| `GET` | `/{id}/stundenlohn-historie` | Stundenlohn-Verlauf |
| `POST` | `/{id}/stundenlohn-historie` | Stundenlohn-Eintrag anlegen |
| `PUT` | `/stundenlohn-historie/{eintragId}` | Stundenlohn-Eintrag ändern |
| `DELETE` | `/stundenlohn-historie/{eintragId}` | Stundenlohn-Eintrag löschen |

### AbwesenheitController

**Basis-URL:** `/api/abwesenheit`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Abwesenheit buchen (z. B. Urlaub, halber Tag) |
| `GET` | `/mitarbeiter/{mitarbeiterId}` | Abwesenheiten eines Mitarbeiters (optional Zeitraum) |
| `GET` | `/team` | Abwesenheiten aller Mitarbeiter im Zeitraum (Team-Kalender) |
| `DELETE` | `/{id}` | Abwesenheit löschen |

### UrlaubsantragController

**Basis-URL:** keine eigene (volle Pfade in der Tabelle)

Ohne Klassen-Mapping, daher volle Pfade.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/urlaub/antraege` | Urlaubsantrag stellen (auch Handy-App) |
| `GET` | `/api/urlaub/antraege` | Anträge (Filter `status`, `mitarbeiterId`, `jahr`) |
| `PUT` | `/api/urlaub/antraege/{id}/approve` | Antrag genehmigen |
| `PUT` | `/api/urlaub/antraege/{id}/reject` | Antrag ablehnen |
| `PUT` | `/api/urlaub/antraege/{id}/storno` | Antrag stornieren |
| `GET` | `/api/urlaub/resturlaub` | Resturlaub eines Mitarbeiters im Jahr |
| `GET` | `/api/langzeitkrankmeldungen/urlaubs-hinweise` | Warnung, wenn der Urlaub in eine laufende Krankmeldung fällt |
| `GET` | `/api/urlaub/typen` | Abwesenheitsarten |

### LangzeitkrankmeldungController

**Basis-URL:** `/api/langzeitkrankmeldungen`

Ändernde Aufrufe schicken die aktuelle `version` mit.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Langzeitkrankmeldungen (optional `status`) |
| `GET` | `/{id}` | Einzelne Meldung |
| `POST` | `/` | Meldung anlegen |
| `PUT` | `/{id}` | Meldung ändern |
| `PUT` | `/{id}/beenden` | Meldung beenden (`ende`) |
| `PUT` | `/{id}/oeffnen` | Beendete Meldung wieder öffnen |
| `PUT` | `/{id}/abbrechen` | Meldung abbrechen |
| `POST` | `/{id}/phasen` | Phase hinzufügen |
| `PUT` | `/{id}/phasen/{phasenId}` | Phase ändern |
| `DELETE` | `/{id}/phasen/{phasenId}` | Phase löschen |

### LohnStammdatenController

**Basis-URL:** `/api/lohn-stammdaten`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/krankenkassen` | Krankenkassen (optional nur aktive) |
| `POST` | `/krankenkassen` | Krankenkasse anlegen |
| `PUT` | `/krankenkassen/{id}` | Krankenkasse ändern |
| `DELETE` | `/krankenkassen/{id}` | Krankenkasse löschen |
| `GET` | `/sv-saetze` | Sozialversicherungs-Sätze |
| `POST` | `/sv-saetze` | SV-Satz anlegen |
| `PUT` | `/sv-saetze/{id}` | SV-Satz ändern |
| `DELETE` | `/sv-saetze/{id}` | SV-Satz löschen |
| `GET` | `/gewerke` | Gewerke (optional nur aktive) |
| `POST` | `/gewerke` | Gewerk anlegen |
| `PUT` | `/gewerke/{id}` | Gewerk ändern |
| `DELETE` | `/gewerke/{id}` | Gewerk löschen |

### LohnabrechnungController

**Basis-URL:** `/api/lohnabrechnungen`

Lohnabrechnungen, die vom Steuerberater kommen.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/mitarbeiter/{mitarbeiterId}` | Lohnabrechnungen eines Mitarbeiters |
| `GET` | `/jahr/{jahr}` | Lohnabrechnungen eines Jahres |
| `GET` | `/steuerberater/{steuerberaterId}/jahr/{jahr}` | Lohnabrechnungen eines Steuerberaters im Jahr |
| `GET` | `/jahre` | Verfügbare Jahre |
| `GET` | `/{id}` | Einzelne Lohnabrechnung |
| `GET` | `/{id}/download` | PDF herunterladen |
| `DELETE` | `/{id}` | Lohnabrechnung löschen |

---

## 13. Kalender und Benachrichtigungen

### KalenderController

**Basis-URL:** `/api/kalender`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Einträge eines Monats (optional nur für einen Mitarbeiter) |
| `GET` | `/mobile` | Einträge eines Monats für die Handy-App |
| `GET` | `/mobile/tag` | Einträge eines Tages für die Handy-App |
| `GET` | `/{id}` | Einzelner Eintrag |
| `POST` | `/` | Eintrag anlegen |
| `PUT` | `/{id}` | Eintrag ändern |
| `DELETE` | `/{id}` | Eintrag löschen |

### NotificationController

**Basis-URL:** `/api/notifications`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/summary` | Benachrichtigungen für die Glocke (mit `alle=true` für die ganze Seite) |
| `POST` | `/erledigt` | Einzelne Einträge erledigen (Mails gelten dann als gelesen) |
| `POST` | `/erledigt/kategorien` | Ganze Kategorien erledigen |

---

## 14. Buchhaltung, Belege und Kasse

### BelegController

**Basis-URL:** `/api/buchhaltung`

Belege und Kasse. Was jemand darf, regeln die Beleg-Rechte der Abteilung (sehen, erfassen). Die `/mobile/…`-Pfade sind die Varianten für die Handy-App.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/me/permissions` | Beleg-Rechte des Aufrufers |
| `GET` | `/mobile/me/permissions` | Dasselbe für die Handy-App |
| `POST` | `/mobile/belege` | Beleg hochladen (Handy-App) |
| `POST` | `/belege` | Beleg hochladen |
| `PUT` | `/belege/{id}/positionen` | Auswahl „für die Firma“ je Position speichern |
| `PUT` | `/mobile/belege/{id}/positionen` | Dasselbe für die Handy-App |
| `GET` | `/mobile/belege/{id}` | Einzelner Beleg (Handy-App) |
| `GET` | `/mobile/belege` | Zuletzt selbst hochgeladene Belege (Handy-App) |
| `POST` | `/mwst-rechner` | MwSt-Rechner: aus zwei von Netto, Brutto und Satz den Rest rechnen |
| `POST` | `/mobile/mwst-rechner` | Dasselbe für die Handy-App |
| `POST` | `/umbuchungen` | Buchung ohne Beleg (Privatentnahme, Privat an Firma, Kasse an Bank) |
| `GET` | `/belege` | Belege (Filter `status`, `kategorie`) |
| `GET` | `/belege/{id}` | Einzelner Beleg |
| `GET` | `/belege/{id}/datei` | Belegdatei herunterladen |
| `PUT` | `/belege/{id}` | Beleg ändern |
| `DELETE` | `/belege/{id}` | Beleg verwerfen, nur solange sein Monat offen ist |
| `GET` | `/steuerberater-export` | Geprüfte Belege eines Zeitraums als Liste für den Steuerberater |
| `GET` | `/kassenbuch` | Kassenbuch eines Zeitraums |

### KassenbuchController

**Basis-URL:** `/api/buchhaltung/kassenbuch`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/abschluss/vorschau` | Was ein Monatsabschluss bewirken würde und was ihn noch blockiert |
| `GET` | `/abschluesse` | Bisherige Monatsabschlüsse |
| `POST` | `/abschluss` | Monat abschließen: vergibt laufende Nummern, nicht umkehrbar |
| `POST` | `/belege/{id}/storno` | Festgeschriebenen Beleg per Gegenbuchung stornieren |
| `POST` | `/zaehlungen` | Kassenzählung erfassen |
| `GET` | `/zaehlungen` | Kassenzählungen (optional Zeitraum) |
| `GET` | `/zaehlungen/erwartet` | Was laut Kassenbuch in der Kasse liegen müsste (Stichtag) |
| `GET` | `/belege/{id}/verlauf` | Änderungsverlauf eines Belegs |
| `GET` | `/pruefung` | Kassenbuch-Protokoll auf nachträgliche Änderungen prüfen |
| `GET` | `/verfahrensdokumentation` | GoBD-Verfahrensdokumentation als Textdatei |

### KassenbuchungController

**Basis-URL:** `/api/buchhaltung/kassenbuch`

Teilt sich die Basis-URL mit dem `KassenbuchController`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/buchungen` | Kassenbuchung erfassen (multipart, optional mit Beleg) |
| `GET` | `/offene-ausgangsrechnungen` | Offene Ausgangsrechnungen (Auswahl beim Buchen) |

### KasseShortcutController

**Basis-URL:** `/api/buchhaltung/kasse`

Schnellbuchungen für die Kasse.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/bank-abhebung` | Bankabhebung in die Kasse |
| `POST` | `/privateinlage` | Privateinlage |
| `POST` | `/privatentnahme` | Privatentnahme |
| `POST` | `/lohn-zahlung` | Lohn bar auszahlen |
| `GET` | `/saldo` | Aktueller Kassenstand |
| `GET` | `/einstellung` | Kassen-Einstellungen |
| `PUT` | `/einstellung` | Kassen-Einstellungen ändern |

### SachkontoController

**Basis-URL:** `/api/buchhaltung`

Teilt sich die Basis-URL `/api/buchhaltung` mit dem `BelegController`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/sachkonten` | Sachkonten (optional nur aktive) |
| `POST` | `/sachkonten` | Sachkonto anlegen |
| `PUT` | `/sachkonten/{id}` | Sachkonto ändern |
| `GET` | `/auswertung` | Auswertung nach Sachkonten für einen Zeitraum |
| `GET` | `/auswertung/monat/pdf` | Monats-PDF für den Steuerberater (Belege und Sachkonto-Auswertung) |

### ZahlungsartController

**Basis-URL:** `/api/buchhaltung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/zahlungsarten` | Zahlungsarten (optional nur aktive) |

### SteuerberaterExportController

**Basis-URL:** `/api/buchhaltung/steuerberater`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/vorpruefung` | Offene Punkte vor dem Monatspaket für den Steuerberater |
| `GET` | `/paket` | Monatspaket als ZIP; bei offenen Punkten `409`, außer mit `trotzdem=true` |

### BwaController

**Basis-URL:** `/api/bwa`

BWA = Betriebswirtschaftliche Auswertung vom Steuerberater.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/jahre` | Jahre mit BWA |
| `GET` | `/jahr/{jahr}` | BWAs eines Jahres |
| `GET` | `/{id}` | Einzelne BWA |
| `GET` | `/{id}/pdf` | BWA als PDF |
| `DELETE` | `/{id}` | BWA löschen |

---

## 15. Mietverwaltung

### MietobjektController

**Basis-URL:** `/api/miete`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/mietobjekte` | Alle Mietobjekte |
| `POST` | `/mietobjekte` | Mietobjekt anlegen |
| `PUT` | `/mietobjekte/{id}` | Mietobjekt ändern |
| `DELETE` | `/mietobjekte/{id}` | Mietobjekt löschen |
| `GET` | `/mietobjekte/{id}/parteien` | Mietparteien |
| `POST` | `/mietobjekte/{id}/parteien` | Mietpartei anlegen |
| `PUT` | `/mietobjekte/{id}/parteien/{parteiId}` | Mietpartei ändern |
| `DELETE` | `/parteien/{parteiId}` | Mietpartei löschen |

### KostenVerteilungController

**Basis-URL:** `/api/miete`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/mietobjekte/{mietobjektId}/kostenstellen` | Kostenstellen eines Mietobjekts |
| `POST` | `/mietobjekte/{mietobjektId}/kostenstellen` | Kostenstelle anlegen |
| `PUT` | `/mietobjekte/{mietobjektId}/kostenstellen/{kostenstelleId}` | Kostenstelle ändern |
| `DELETE` | `/kostenstellen/{kostenstelleId}` | Kostenstelle löschen |
| `GET` | `/kostenstellen/{kostenstelleId}/kostenpositionen` | Kostenpositionen (optional Jahr) |
| `POST` | `/kostenstellen/{kostenstelleId}/kostenpositionen` | Kostenposition anlegen |
| `PUT` | `/kostenstellen/{kostenstelleId}/kostenpositionen/{kostenpositionId}` | Kostenposition ändern |
| `DELETE` | `/kostenpositionen/{kostenpositionId}` | Kostenposition löschen |
| `POST` | `/mietobjekte/{mietobjektId}/kostenpositionen/copy-vorjahr` | Kostenpositionen aus dem Vorjahr übernehmen (`zielJahr`) |
| `GET` | `/mietobjekte/{mietobjektId}/verteilungsschluessel` | Verteilungsschlüssel |
| `POST` | `/mietobjekte/{mietobjektId}/verteilungsschluessel` | Verteilungsschlüssel anlegen |
| `PUT` | `/mietobjekte/{mietobjektId}/verteilungsschluessel/{schluesselId}` | Verteilungsschlüssel ändern |
| `DELETE` | `/verteilungsschluessel/{schluesselId}` | Verteilungsschlüssel löschen |

### RaumVerbrauchController

**Basis-URL:** `/api/miete`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/mietobjekte/{mietobjektId}/raeume` | Räume eines Mietobjekts |
| `POST` | `/mietobjekte/{mietobjektId}/raeume` | Raum anlegen |
| `PUT` | `/mietobjekte/{mietobjektId}/raeume/{raumId}` | Raum ändern |
| `DELETE` | `/raeume/{raumId}` | Raum löschen |
| `GET` | `/raeume/{raumId}/verbrauchsgegenstaende` | Verbrauchsgegenstände (Zähler) eines Raums |
| `POST` | `/raeume/{raumId}/verbrauchsgegenstaende` | Verbrauchsgegenstand anlegen |
| `PUT` | `/raeume/{raumId}/verbrauchsgegenstaende/{gegenstandId}` | Verbrauchsgegenstand ändern |
| `DELETE` | `/verbrauchsgegenstaende/{gegenstandId}` | Verbrauchsgegenstand löschen |
| `GET` | `/mietobjekte/{mietobjektId}/zaehlerstaende/erfassung.pdf` | Erfassungsbogen für Zählerstände (PDF) |
| `GET` | `/verbrauchsgegenstaende/{gegenstandId}/zaehlerstaende` | Zählerstände |
| `POST` | `/verbrauchsgegenstaende/{gegenstandId}/zaehlerstaende` | Zählerstand erfassen |
| `PUT` | `/verbrauchsgegenstaende/{gegenstandId}/zaehlerstaende/{zaehlerstandId}` | Zählerstand ändern |
| `DELETE` | `/zaehlerstaende/{zaehlerstandId}` | Zählerstand löschen |

### MietabrechnungController

**Basis-URL:** `/api/miete/mietobjekte/{mietobjektId}/jahresabrechnung`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Jahresabrechnung (`jahr`) |
| `GET` | `/pdf` | Jahresabrechnung als PDF (`jahr`) |

---

## 16. Telefon

### TelefonController

**Basis-URL:** `/api/telefon`

Anrufe und Anrufbeantworter der FRITZ!Box. Die meisten Aufrufe verlangen das Abteilungsrecht `darfTelefonSehen`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/berechtigung` | Darf der Benutzer Telefondaten sehen? |
| `GET` | `/live` | Live-Strom für das Anruf-Fenster (Server-Sent Events) |
| `GET` | `/status` | Verbindungsstatus, letzte Abholung, Zahl neuer Nachrichten |
| `GET` | `/anrufe` | Anrufliste mit Filtern (seitenweise) |
| `POST` | `/anrufe/{id}/zuordnung` | Anruf von Hand zuordnen |
| `DELETE` | `/anrufe/{id}/zuordnung` | Zuordnung eines Anrufs aufheben |
| `GET` | `/sprachnachrichten` | Sprachnachrichten (Filter) |
| `GET` | `/sprachnachrichten/anzahl-neu` | Anzahl neuer Sprachnachrichten |
| `GET` | `/sprachnachrichten/{id}/audio` | Aufnahme als WAV |
| `PATCH` | `/sprachnachrichten/{id}` | Als abgehört markieren |
| `POST` | `/sprachnachrichten/{id}/zuordnung` | Sprachnachricht zuordnen |
| `DELETE` | `/sprachnachrichten/{id}/zuordnung` | Zuordnung aufheben |
| `POST` | `/abholen` | Anrufe und Nachrichten jetzt von der Anlage holen |
| `GET` | `/kontakt-rufnummern` | Weitere Rufnummern eines Kontakts (für alle Angemeldeten) |
| `GET` | `/kontakt-ueberblick` | Überblick zum Anrufer (Adresse, Projekte, Anfragen) |
| `GET` | `/steuerberater` | Kanzleien zur Auswahl beim Zuordnen |
| `DELETE` | `/kontakt-rufnummern/{id}` | Weitere Rufnummer löschen |
| `GET` | `/telefone` | Telefone, die beim Anrufen aus dem ERP klingeln können |
| `POST` | `/anrufen` | Zurückrufen: erst klingelt das eigene Telefon, dann wählt die Anlage |

### TelefonEinstellungenController

**Basis-URL:** `/api/telefon`

Teilt sich die Basis-URL mit dem `TelefonController`. Alles hier nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/einstellungen` | FRITZ!Box-Zugang lesen *(nur Admin)* |
| `PUT` | `/einstellungen` | FRITZ!Box-Zugang speichern *(nur Admin)* |
| `POST` | `/einstellungen/test` | Verbindung testen, auch mit ungespeicherten Eingaben *(nur Admin)* |
| `POST` | `/admin/nachholen` | Anrufliste der letzten `tage` Tage und alle Nachrichten neu abgleichen *(nur Admin)* |

---

## 17. Website

### BeitraegeController

**Basis-URL:** `/api/beitraege`

Bereich „Aktuelles“ der Firmen-Website; ganzer Pfad nur für Admins. Ganze Beiträge lassen sich bewusst nicht löschen.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Beiträge *(nur Admin)* |
| `GET` | `/{id}` | Einzelner Beitrag *(nur Admin)* |
| `POST` | `/` | Beitrag anlegen *(nur Admin)* |
| `PATCH` | `/{id}` | Beitrag ändern *(nur Admin)* |
| `POST` | `/{id}/status` | Status eines Beitrags ändern *(nur Admin)* |
| `POST` | `/{id}/bilder` | Bild hinzufügen *(nur Admin)* |
| `DELETE` | `/{id}/bilder/{imageId}` | Bild löschen *(nur Admin)* |
| `PATCH` | `/{id}/bilder/{imageId}` | Alternativtext eines Bilds ändern *(nur Admin)* |
| `POST` | `/{id}/titelbild` | Titelbild festlegen *(nur Admin)* |
| `GET` | `/bild/{dateiname:.+}` | Beitragsbild über das ERP anzeigen *(nur Admin)* |

### BeitragKiController

**Basis-URL:** `/api/beitraege/ki`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/entwurf` | Beitragsentwurf per KI aus Angaben und Bildern *(nur Admin)* |

### WebsiteAnalyticsSnapshotController

**Basis-URL:** `/api/website-analytics`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/latest` | Neuester Besucher-Stand der Website (`204`, wenn noch keiner da ist) |
| `GET` | `/verlauf` | Besucherverlauf (`tage`) |

---

## 18. Firma und Einstellungen

### FirmaController

**Basis-URL:** `/api/firma`

`/api/firma/**` ist Admin-Sache. Lesen dürfen alle Angemeldeten nur `GET /api/firma`, `GET /api/firma/logo`, `GET /api/firma/steuerberater` und `GET /api/firma/steuerberater/{id}`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Firmendaten |
| `PUT` | `/` | Firmendaten speichern *(nur Admin)* |
| `POST` | `/logo` | Firmenlogo hochladen (PNG, JPEG, WebP) *(nur Admin)* |
| `GET` | `/logo` | Firmenlogo (alle Angemeldeten) |
| `DELETE` | `/logo` | Firmenlogo löschen *(nur Admin)* |
| `GET` | `/kostenstellen` | Alle Kostenstellen *(nur Admin)* |
| `GET` | `/kostenstellen/typ/{typ}` | Kostenstellen eines Typs *(nur Admin)* |
| `GET` | `/kostenstellen/{id}` | Einzelne Kostenstelle *(nur Admin)* |
| `POST` | `/kostenstellen` | Kostenstelle anlegen *(nur Admin)* |
| `PUT` | `/kostenstellen/{id}` | Kostenstelle ändern *(nur Admin)* |
| `DELETE` | `/kostenstellen/{id}` | Kostenstelle löschen *(nur Admin)* |
| `POST` | `/kostenstellen/init` | Standard-Kostenstellen anlegen *(nur Admin)* |
| `GET` | `/steuerberater` | Steuerberater-Kontakte |
| `GET` | `/steuerberater/{id}` | Einzelner Steuerberater-Kontakt |
| `POST` | `/steuerberater` | Steuerberater anlegen *(nur Admin)* |
| `PUT` | `/steuerberater/{id}` | Steuerberater ändern *(nur Admin)* |
| `DELETE` | `/steuerberater/{id}` | Steuerberater löschen *(nur Admin)* |

### SystemSettingsController

**Basis-URL:** `/api/settings`

Ganzer Pfad `/api/settings/**` nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Alle Einstellungen als Liste *(nur Admin)* |
| `GET` | `/smtp` | Postausgang (SMTP) *(nur Admin)* |
| `PUT` | `/smtp` | Postausgang speichern *(nur Admin)* |
| `POST` | `/smtp/test` | Postausgang testen *(nur Admin)* |
| `GET` | `/imap` | Posteingang (IMAP) *(nur Admin)* |
| `PUT` | `/imap` | Posteingang speichern *(nur Admin)* |
| `POST` | `/imap/test` | Posteingang testen *(nur Admin)* |
| `PUT` | `/email-account` | E-Mail-Adresse und Passwort für SMTP und IMAP in einem Schritt *(nur Admin)* |
| `GET` | `/gemini` | KI-Zugang (Gemini) *(nur Admin)* |
| `PUT` | `/gemini` | KI-Zugang speichern *(nur Admin)* |
| `POST` | `/gemini/test` | KI-Zugang testen *(nur Admin)* |
| `GET` | `/anfrage-funnel-spamfilter` | Spamfilter für das Anfrageformular der Website *(nur Admin)* |
| `PUT` | `/anfrage-funnel-spamfilter` | Spamfilter speichern *(nur Admin)* |
| `GET` | `/datei-ordner` | Gemeinsamer Datei-Ordner (Zeichnungen, Excel …) *(nur Admin)* |
| `PUT` | `/datei-ordner` | Datei-Ordner speichern *(nur Admin)* |
| `POST` | `/datei-ordner/test` | Ordner prüfen (anlegen, Schreibrechte) *(nur Admin)* |
| `POST` | `/datei-ordner/freigeben` | Gespeicherten Ordner im Netzwerk freigeben *(nur Admin)* |

### ZeiterfassungEinstellungenController

**Basis-URL:** `/api/settings`

Liegt unter `/api/settings`, daher nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/zeiterfassung` | Einstellungen zur Zeiterfassung *(nur Admin)* |
| `PUT` | `/zeiterfassung` | Einstellungen zur Zeiterfassung speichern *(nur Admin)* |
| `GET` | `/datev` | DATEV-Einstellungen für das Steuerbüro *(nur Admin)* |
| `PUT` | `/datev` | DATEV-Einstellungen speichern *(nur Admin)* |

### ZeiterfassungZugangController

**Basis-URL:** `/api/settings/zeiterfassung-zugang`

Liegt unter `/api/settings`, daher nur für Admins.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/` | Zugang „Zeiterfassung von unterwegs“ (Tailscale-App oder Funnel) *(nur Admin)* |
| `PUT` | `/` | Zugang speichern *(nur Admin)* |

---

## 19. Dateien, KI und Sonstiges

### DateiController

**Basis-URL:** keine eigene (volle Pfade in der Tabelle)

Ohne Klassen-Mapping. Auch mit Handy-Token erreichbar.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/images/{dateiname:.+}` | Gespeichertes Bild |
| `GET` | `/api/dokumente/{dateiname:.+}` | Gespeichertes Dokument (`download=true` als Download) |
| `GET` | `/api/dokumente/{dateiname:.+}/thumbnail` | Vorschaubild (höchstens 300 × 300 px) |
| `GET` | `/api/dokumente/{dateiname:.+}/anzeige` | Bild in Anzeigegröße (höchstens 1600 px) für das Handy |

### DocumentScannerController

**Basis-URL:** `/api/documents`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/scan-manual` | Foto (`image`) in eine A4-PDF umwandeln, Dateiname per KI |

### KiHilfeController

**Basis-URL:** `/api/ki-hilfe`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/chat` | Frage an die KI-Hilfe stellen |

### SpracheingabeController

**Basis-URL:** `/api/spracheingabe`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/transkribieren` | Sprachaufnahme per KI in Text umwandeln |
| `GET` | `/verfuegbar` | Ist die Spracheingabe an (KI-Schlüssel hinterlegt)? |

### SystemUtilityController

**Basis-URL:** `/api/system`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/openfile-launcher-setup` | Setup für den „Datei öffnen“-Helfer als ZIP (`.bat` und `.ps1`) |

### FaviconController

**Basis-URL:** keine eigene (volle Pfade in der Tabelle)

Ohne Klassen-Mapping, ohne Anmeldung.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/favicon.ico` | Browser-Symbol |

### SpaErrorController

**Basis-URL:** `/error`

Fehlerseite von Spring Boot.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `alle` | `/` | Bei Seitenaufrufen: `404` wird auf `index.html` umgeleitet (React-Routing), sonst kurze HTML-Fehlerseite |
| `alle` | `/` | Bei allen anderen Anfragen: Fehler als JSON |

---

## 20. Schnittstelle für die Firmen-Website (`/api/internal`)

### AnfrageFunnelController

**Basis-URL:** `/api/internal/anfrage`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Anfrage aus dem Website-Formular (multipart: `anfrage` als JSON, optional `bilder`) |
| `POST` | `/` | Dasselbe als reines JSON ohne Bilder (derzeit nicht genutzt) |

### FreigabeInternalController

**Basis-URL:** `/api/internal/freigabe`

Digitale Freigabe von Angeboten durch Kunden über die Website.

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/{uuid}` | Daten für die Freigabe-Seite (Status `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED`) |
| `GET` | `/{uuid}/pdf` | PDF zur Freigabe (nur solange sie nicht abgelaufen ist) |
| `DELETE` | `/{uuid}/pdf` | PDF vom abgelaufenen oder zurückgezogenen Link lösen (`204`); die Datei bleibt im ERP. Offen oder angenommen: `409`, unbekannt: `404` |
| `POST` | `/{uuid}/code` | Sechsstelligen Bestätigungscode an den Kunden schicken |
| `POST` | `/{uuid}/akzeptieren` | Freigabe als angenommen speichern (mit IP und User-Agent) |

### AnalyticsSnapshotIngressController

**Basis-URL:** `/api/internal/analytics-snapshot`

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/` | Besucher-Stand der Website entgegennehmen |

---

## Controller-Gesamtübersicht

98 Controller, alphabetisch. „Endpunkte“ zählt die Handler-Methoden.

| # | Controller | Basis-URL | Endpunkte | Abschnitt |
|---|---|---|---|---|
| 1 | `AbteilungBerechtigungController` | `/api/abteilungen` | 4 | 1 |
| 2 | `AbteilungController` | `/api/abteilungen` | 4 | 8 |
| 3 | `AbwesenheitController` | `/api/abwesenheit` | 4 | 12 |
| 4 | `AnalyticsSnapshotIngressController` | `/api/internal/analytics-snapshot` | 1 | 20 |
| 5 | `AnfrageController` | `/api/anfragen` | 27 | 2 |
| 6 | `AnfrageFunnelController` | `/api/internal/anfrage` | 2 | 20 |
| 7 | `ArbeitsgangController` | `/api/arbeitsgaenge` (Klassen-Mapping nur `/api`) | 5 | 8 |
| 8 | `ArbeitszeitartController` | `/api/arbeitszeitarten` | 7 | 8 |
| 9 | `ArtikelController` | `/api/artikel` | 21 | 8 |
| 10 | `ArtikelKategorieController` | `/api/artikel/kategorien` | 6 | 8 |
| 11 | `AusgangsGeschaeftsDokumentAuditController` | `/api/ausgangs-dokumente/audit` | 4 | 5 |
| 12 | `AusgangsGeschaeftsDokumentController` | `/api/ausgangs-dokumente` | 19 | 5 |
| 13 | `AuthController` | `/api/auth` | 4 | 1 |
| 14 | `BeitraegeController` | `/api/beitraege` | 10 | 17 |
| 15 | `BeitragKiController` | `/api/beitraege/ki` | 1 | 17 |
| 16 | `BelegController` | `/api/buchhaltung` | 18 | 14 |
| 17 | `BestellungController` | `/api/bestellungen` | 4 | 7 |
| 18 | `BestellungsUebersichtController` | `/api/bestellungen-uebersicht` | 21 | 7 |
| 19 | `BwaController` | `/api/bwa` | 5 | 14 |
| 20 | `DateiController` | `/api/dokumente`, `/api/images` (ohne Klassen-Mapping) | 4 | 19 |
| 21 | `DatensatzLockController` | `/api/datensatz-locks` | 3 | 1 |
| 22 | `DatevExportController` | `/api/zeitverwaltung/monatsabschluesse/datev` | 2 | 11 |
| 23 | `DatevKonfigurationController` | `/api/zeitverwaltung/monatsabschluesse/datev/konfiguration` | 1 | 11 |
| 24 | `DocumentScannerController` | `/api/documents` | 1 | 19 |
| 25 | `DokumentGeneratorController` | `/api/dokument-generator` | 4 | 5 |
| 26 | `DokumentUebersichtController` | `/api/dokumentuebersicht` | 2 | 6 |
| 27 | `EmailController` | `/api/email` | 7 | 9 |
| 28 | `EmailDraftController` | `/api/emails/drafts` | 9 | 9 |
| 29 | `EmailKiClassificationController` | `/api/email-ki` | 3 | 9 |
| 30 | `EmailSignatureController` | `/api/email/signatures` | 15 | 9 |
| 31 | `EmailTemplateController` | `/api/email/template` | 1 | 9 |
| 32 | `EmailTextTemplateController` | `/api/email-textvorlagen` | 7 | 9 |
| 33 | `EntityLastAccessedController` | `/api/last-accessed` | 2 | 1 |
| 34 | `FaviconController` | `/favicon.ico` (ohne Klassen-Mapping) | 1 | 19 |
| 35 | `FirmaController` | `/api/firma` | 17 | 18 |
| 36 | `FormularTemplateController` | `/api/formulare` (ohne Klassen-Mapping) | 17 | 5 |
| 37 | `FreigabeInternalController` | `/api/internal/freigabe` | 5 | 20 |
| 38 | `FrontendUserController` | `/api/frontend-users` | 3 | 1 |
| 39 | `GaebImportController` | `/api/import` | 1 | 8 |
| 40 | `KalenderController` | `/api/kalender` | 7 | 13 |
| 41 | `KassenbuchController` | `/api/buchhaltung/kassenbuch` | 10 | 14 |
| 42 | `KassenbuchungController` | `/api/buchhaltung/kassenbuch` | 2 | 14 |
| 43 | `KasseShortcutController` | `/api/buchhaltung/kasse` | 7 | 14 |
| 44 | `KiHilfeController` | `/api/ki-hilfe` | 1 | 19 |
| 45 | `KostenVerteilungController` | `/api/miete` | 13 | 15 |
| 46 | `KundeController` | `/api/kunden` | 10 | 4 |
| 47 | `LangzeitkrankmeldungController` | `/api/langzeitkrankmeldungen` | 10 | 12 |
| 48 | `LangzeitkrankmeldungMobileController` | `/api/zeiterfassung/langzeitkrankmeldung` | 1 | 10 |
| 49 | `LeistungController` | `/api/leistungen` | 5 | 8 |
| 50 | `LieferantDokumentController` | `/api/lieferant-dokumente` | 13 | 7 |
| 51 | `LieferantDokumentPositionController` | `/api/bestellungen-uebersicht/positionen` | 4 | 7 |
| 52 | `LieferantDokumentWartungController` | `/api/admin/lieferant-dokumente` | 1 | 7 |
| 53 | `LieferantenController` | `/api/lieferanten` | 29 | 7 |
| 54 | `LieferantReklamationController` | `/api/reklamationen` | 7 | 7 |
| 55 | `LieferantWartungController` | `/api/admin/lieferanten` | 1 | 7 |
| 56 | `LohnabrechnungController` | `/api/lohnabrechnungen` | 7 | 12 |
| 57 | `LohnStammdatenController` | `/api/lohn-stammdaten` | 12 | 12 |
| 58 | `MahnwesenController` | `/api/mahnwesen` | 1 | 5 |
| 59 | `MietabrechnungController` | `/api/miete/mietobjekte/{mietobjektId}/jahresabrechnung` | 2 | 15 |
| 60 | `MietobjektController` | `/api/miete` | 8 | 15 |
| 61 | `MitarbeiterController` | `/api/mitarbeiter` | 17 | 12 |
| 62 | `MitarbeiterSaldoController` | `/api/zeitverwaltung/mitarbeiter/{mitarbeiterId}/saldo` (ohne Klassen-Mapping) | 1 | 11 |
| 63 | `MonatsabschlussController` | `/api/zeitverwaltung/monatsabschluesse` | 4 | 11 |
| 64 | `MonatsabschlussUebersichtController` | `/api/zeitverwaltung/monatsabschluesse` | 4 | 11 |
| 65 | `NotificationController` | `/api/notifications` | 3 | 13 |
| 66 | `OffenePostenController` | `/api/offene-posten` | 6 | 6 |
| 67 | `OutOfOfficeController` | `/api/email/outofoffice` | 4 | 9 |
| 68 | `PostfachController` | `/api/postfaecher` | 6 | 9 |
| 69 | `ProduktkategorieController` | `/api/produktkategorien` | 14 | 8 |
| 70 | `ProjektController` | `/api/projekte` | 50 | 3 |
| 71 | `ProjektWartungController` | `/api/admin/projekte` | 2 | 3 |
| 72 | `PushSubscriptionController` | `/api/push` | 3 | 10 |
| 73 | `RaumVerbrauchController` | `/api/miete` | 13 | 15 |
| 74 | `RechnungsuebersichtController` | `/api/rechnungsuebersicht` | 5 | 6 |
| 75 | `SachkontoController` | `/api/buchhaltung` | 5 | 14 |
| 76 | `SchnittbilderController` | `/api/schnittbilder` | 2 | 8 |
| 77 | `SpaErrorController` | `/error` | 2 | 19 |
| 78 | `SpracheingabeController` | `/api/spracheingabe` | 2 | 19 |
| 79 | `SteuerberaterExportController` | `/api/buchhaltung/steuerberater` | 2 | 14 |
| 80 | `SystemSettingsController` | `/api/settings` | 17 | 18 |
| 81 | `SystemUtilityController` | `/api/system` | 1 | 19 |
| 82 | `TelefonController` | `/api/telefon` | 19 | 16 |
| 83 | `TelefonEinstellungenController` | `/api/telefon` | 4 | 16 |
| 84 | `TextbausteinController` | `/api/textbausteine` | 8 | 5 |
| 85 | `UnifiedEmailController` | `/api/emails` | 56 | 9 |
| 86 | `UrlaubsantragController` | `/api/langzeitkrankmeldungen`, `/api/urlaub` (ohne Klassen-Mapping) | 8 | 12 |
| 87 | `UtilsController` | `/api/utils` | 1 | 8 |
| 88 | `VendorInvoiceController` | `/api/admin/vendor-invoices` | 4 | 7 |
| 89 | `VerrechnungslohnController` | `/api/verrechnungslohn` | 2 | 8 |
| 90 | `WebsiteAnalyticsSnapshotController` | `/api/website-analytics` | 2 | 17 |
| 91 | `ZahlungsartController` | `/api/buchhaltung` | 1 | 14 |
| 92 | `ZeiterfassungApiController` | `/api/zeiterfassung` | 17 | 10 |
| 93 | `ZeiterfassungController` | `/zeiterfassung` (ohne Klassen-Mapping) | 2 | 10 |
| 94 | `ZeiterfassungEinstellungenController` | `/api/settings` | 4 | 18 |
| 95 | `ZeiterfassungZugangController` | `/api/settings/zeiterfassung-zugang` | 2 | 18 |
| 96 | `ZeitkontenmodellController` | `/api/zeitverwaltung/zeitkontenmodelle` | 4 | 11 |
| 97 | `ZeitkontoKorrekturController` | `/api/zeitkonto/korrekturen` | 6 | 11 |
| 98 | `ZeitverwaltungController` | `/api/zeitverwaltung` | 19 | 11 |
