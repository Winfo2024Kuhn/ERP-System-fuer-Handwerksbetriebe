# Spec: Telefon – Anrufliste und Anrufbeantworter aus der FRITZ!Box

Datum: 29.09.2026

Status: Grundlage ist ein mit dem Nutzer abgeschlossenes Brainstorming.
Diese Spec überführt die dort getroffenen Entscheidungen in eine
Umsetzungsgrundlage für den Plan. Entscheidungen, die im Brainstorming nicht
ausdrücklich getroffen wurden, sind als **(Vorschlag)** markiert und gelten,
solange der Nutzer beim Review der Spec nicht widerspricht.

## Ziel

Im Menü **Kommunikation** kommt neben „E-Mail“ eine Gruppe **Telefon** hinzu:

- **Anrufliste** – alle geschäftlichen Anrufe (angenommen, verpasst,
  ausgehend, abgewiesen), automatisch dem passenden Kunden oder Lieferanten
  zugeordnet.
- **Anrufbeantworter** – Sprachnachrichten direkt im ERP abhören; sie bleiben
  im ERP erhalten, auch wenn sie auf der FRITZ!Box gelöscht werden.
- **Anrufe in der Kundenakte** – im Kunden- und Lieferanteneditor ein Reiter
  „Anrufe“ mit allen Anrufen und Sprachnachrichten dieses Kontakts.
- **Live-Anzeige** – klingelt das Geschäftstelefon, zeigt das ERP sofort an,
  wer anruft („Kunde Mustermann GmbH ruft an“), mit Sprung in die Kundenakte.

Datenquelle ist die FRITZ!Box des Betriebs (beim Nutzer: FRITZ!Box 7590,
FRITZ!OS 8.21). Der ERP-Server steht im selben Büro-Netzwerk und erreicht die
Box direkt.

## Nicht-Ziele

- Andere Telefonanlagen (Speedport, sipgate, Placetel …). Die Architektur
  kapselt die FRITZ!Box hinter einer Schnittstelle, gebaut wird aber nur die
  FRITZ!Box-Umsetzung.
- Extern gehostete ERP-Server (kein VPN-/MyFRITZ!-/E-Mail-Umweg).
- Telefonieren aus dem ERP heraus (Click-to-Dial).
- Spracherkennung/Abschrift von Sprachnachrichten.
- Zuordnung zu Mitarbeitern (bewusst weggelassen).
- Rückschreiben auf die FRITZ!Box: Das ERP löscht dort nichts und markiert
  nichts als gehört. „Abgehört“ im ERP ist unabhängig vom Status auf der Box
  (die Info-LED blinkt weiter, bis am Telefon abgehört wird).
- Mobile App (`react-zeiterfassung`).

## Etappen

1. **Etappe 1:** Einstellungen, Abholung per TR-064, Datenmodell, Zuordnung,
   Anrufliste, Anrufbeantworter mit Abhören, Reiter „Anrufe“ in Kunden- und
   Lieferanteneditor, automatisches Löschen nach Frist.
2. **Etappe 2:** Live-Anzeige über den FRITZ!Box-Anrufmonitor und
   Server-Sent Events.

Etappe 2 baut auf Etappe 1 auf (Einstellungen, Geschäftsnummern,
Zuordnungslogik), Etappe 1 ist ohne Etappe 2 vollständig nutzbar.

## Grundregel: nur Geschäftsnummern

Die Box meldet mehrere eigene Rufnummern (beim Nutzer: 2323 = Geschäft,
980850 und 980860 = privat). In den Einstellungen werden die
**Geschäftsnummern** angehakt. **Nur** Anrufe, bei denen eine Geschäftsnummer
beteiligt ist (eingehend: angerufene Nummer; ausgehend: verwendete eigene
Nummer), und nur Nachrichten des Anrufbeantworters, der als geschäftlich
gewählt ist, gelangen ins ERP. Alles andere wird beim Abholen verworfen und
nie gespeichert – auch nicht in Logs. Ist keine Geschäftsnummer gewählt,
übernimmt das ERP nichts.

Das gilt identisch für Anrufliste, Anrufbeantworter und Live-Anzeige.

## Architektur

```
TelefonAnlage (Interface)                 ← Rest des ERP kennt nur dieses
  └─ FritzBoxTelefonAnlage                ← TR-064 (SOAP über HTTP, Digest)
        ├─ Tr064Client                    ← SOAP-Aufrufe + Digest-Anmeldung
        ├─ FritzAnruflisteParser          ← XML → AnlagenAnruf
        └─ FritzSprachnachrichtenParser   ← XML → AnlagenSprachnachricht

TelefonAbholService   (@Scheduled, alle 2 Min.)
  → TelefonAnlage → Filter Geschäftsnummern → speichern (idempotent)
  → RufnummernZuordnungService → Audiodateien über DateiSpeicher-Muster ablegen

RufnummerNormalisierer   (rein, ohne Abhängigkeiten, voll unit-getestet)
RufnummernZuordnungService (Nummer → Kunde | Lieferant | mehrdeutig | unbekannt)
TelefonAufbewahrungService (@Scheduled, nachts)
TelefonEinstellungenService (system_setting, Passwort verschlüsselt)

Etappe 2:
FritzAnrufmonitorClient (TCP 1012, Reconnect) → AnrufmonitorEreignis
  → Filter Geschäftsnummern → Zuordnung → TelefonLiveService (SseEmitter)
```

Die Klassen liegen unter `service/telefon/`, Controller unter
`controller/TelefonController` bzw. `TelefonEinstellungenController`,
Entities unter `domain/`. Constructor Injection, keine Feld-Injection.

### Passwort-Verschlüsselung

`MailSecretService` (AES-GCM, Schlüssel `mail.credentials.encryption-key`)
existiert bisher nur auf `origin/codex/beschaffung-konzept`. Die Klasse wird
samt Test **byte-identisch** unter demselben Pfad
(`service/mail/MailSecretService.java`) übernommen, damit der spätere Merge
des Einkaufs-Branches konfliktfrei ist. Ist der Schlüssel nicht eingerichtet,
lässt sich kein FRITZ!Box-Passwort speichern; die Einstellungsseite zeigt dann
einen klaren Hinweis. Das Passwort wird nie ans Frontend ausgeliefert (nur
`passwortGesetzt: true/false`).

### HTTP-Client

TR-064 verlangt HTTP-Digest-Authentifizierung. Dafür wird
`org.apache.httpcomponents.client5:httpclient5` ergänzt (Version über Spring
Boot verwaltet). Verbindung per **HTTP auf Port 49000** im LAN – Digest
überträgt das Passwort nicht im Klartext; HTTPS (49443) würde wegen des
selbstsignierten Zertifikats ein „allen Zertifikaten vertrauen“ erfordern, was
nicht eingebaut wird. Timeouts: Verbindung 5 s, Antwort 15 s.

XML-Parsing mit `DocumentBuilderFactory` **XXE-sicher**: DOCTYPE verboten
(`disallow-doctype-decl`), externe Entities/DTDs aus.

## TR-064-Aufrufe

| Zweck | Service / Aktion |
|---|---|
| Verbindung testen, eigene Nummern | `X_VoIP:1` / `X_AVM-DE_GetNumbers` |
| Ortsvorwahl, Landesvorwahl | `X_VoIP:1` / `X_AVM-DE_GetVoIPCommonAreaCode`, `X_AVM-DE_GetVoIPCommonCountryCode` |
| Anrufliste | `X_AVM-DE_OnTel:1` / `GetCallList` → URL, abgerufen mit `&days=N` |
| Anrufbeantworter-Liste | `X_AVM-DE_TAM:1` / `GetList` |
| Nachrichten eines AB | `X_AVM-DE_TAM:1` / `GetMessageList(NewIndex)` → URL |
| Audiodatei | `Path` der Nachricht + Session-ID aus der Listen-URL |

`N` für `days` = Tage seit letzter erfolgreicher Abholung + 1 (mindestens 1,
beim ersten Lauf 30).

Anruftypen der Liste: 1 = angenommen, 2 = verpasst, 3 = ausgehend,
10 = abgewiesen. Typ 9/11 (laufendes Gespräch) wird übersprungen und beim
nächsten Lauf abgeschlossen übernommen. Datum kommt als `dd.MM.yy HH:mm`
(Ortszeit `Europe/Berlin`), Dauer als `h:mm` (Minutengenauigkeit).

### Risiko: Audioformat (erste Aufgabe der Umsetzung)

Welches Format die Aufnahmen der 7590 unter FRITZ!OS 8.21 genau haben, wird
als **erster Schritt** gegen die echte Box geprüft (Datei holen, Header
ansehen). Ziel: Das ERP speichert und liefert eine im Browser abspielbare
Datei. Ist die Aufnahme bereits WAV (PCM oder G.711) → unverändert speichern.
Ist sie roh/nicht abspielbar → beim Abholen serverseitig mit `javax.sound`
in WAV (PCM, 16 bit) umwandeln, ohne externe Programme. Die tatsächliche Dauer
in Sekunden wird aus der Datei bestimmt. Die Rohdaten aus diesem Test sind
echte Personendaten und werden nicht committet; Test-Fixtures werden
synthetisch erzeugt.

## Datenmodell (Flyway `V400__telefon_anbindung.sql`)

Version 400, weil `origin/codex/beschaffung-konzept` bis V397 belegt und
`spring.flyway.out-of-order=true` gesetzt ist. Vor dem Anlegen prüfen, dass
V400 auf keinem offenen Branch vergeben ist.

**`telefon_anruf`**

| Spalte | Typ | Bemerkung |
|---|---|---|
| `id` | BIGINT PK | |
| `zeitpunkt` | DATETIME | Minutengenau |
| `art` | VARCHAR(20) | `ANGENOMMEN`, `VERPASST`, `AUSGEHEND`, `ABGEWIESEN` |
| `nummer_roh` | VARCHAR(40) NULL | wie von der Box geliefert; NULL = unterdrückt |
| `nummer_normalisiert` | VARCHAR(40) NULL | E.164 (`+49931…`), Index |
| `eigene_nummer` | VARCHAR(40) | beteiligte Geschäftsnummer |
| `dauer_minuten` | INT | |
| `name_fritzbox` | VARCHAR(200) NULL | Name aus dem Box-Telefonbuch |
| `kunde_id` | BIGINT NULL FK | |
| `lieferant_id` | BIGINT NULL FK | |
| `zuordnung` | VARCHAR(20) | `AUTOMATISCH`, `MANUELL`, `KEINE` |
| `angelegt_am` | DATETIME | |

Unique `(zeitpunkt, art, eigene_nummer, nummer_roh)` für Idempotenz. Bekannte
Grenze: Zwei gleichartige Anrufe derselben Nummer in derselben Minute werden
als einer gespeichert – akzeptiert. CHECK: höchstens einer von
`kunde_id`/`lieferant_id` gesetzt.

**`sprachnachricht`**

| Spalte | Typ | Bemerkung |
|---|---|---|
| `id` | BIGINT PK | |
| `anrufbeantworter` | INT | Index des AB auf der Box |
| `zeitpunkt` | DATETIME | |
| `nummer_roh` / `nummer_normalisiert` | VARCHAR(40) NULL | |
| `dauer_sekunden` | INT | aus der Audiodatei |
| `datei_name` | VARCHAR(100) | vom ERP erzeugt (UUID + `.wav`) |
| `abgehoert_am` | DATETIME NULL | NULL = neu |
| `abgehoert_von` | BIGINT NULL | FrontendUserProfile |
| `anruf_id` | BIGINT NULL FK | zugehöriger Anruf, falls zuordenbar |
| `kunde_id` / `lieferant_id` / `zuordnung` | | wie oben |
| `angelegt_am` | DATETIME | |

Unique `(anrufbeantworter, zeitpunkt, nummer_roh)`. Der Nachrichten-Index der
Box wird **nicht** als Schlüssel genutzt, weil ihn die Box nach dem Löschen
neu vergibt. Verknüpfung mit dem Anruf: gleicher `nummer_roh` und Zeitpunkt
±1 Minute, eingehend auf eine Geschäftsnummer.

**`kontakt_rufnummer`**

| Spalte | Typ | Bemerkung |
|---|---|---|
| `id` | BIGINT PK | |
| `kunde_id` / `lieferant_id` | BIGINT NULL FK | genau einer gesetzt (CHECK) |
| `nummer_roh` | VARCHAR(40) | wie eingegeben/übernommen |
| `nummer_normalisiert` | VARCHAR(40) | Index |
| `angelegt_am` | DATETIME | |

FKs auf Kunde/Lieferant mit `ON DELETE CASCADE` für `kontakt_rufnummer`,
`ON DELETE SET NULL` (+ `zuordnung = 'KEINE'` per Service) für Anruf und
Sprachnachricht.

**Einstellungen** in `system_setting` (Schlüssel):
`telefon.aktiv`, `telefon.fritzbox.host` (Standard `fritz.box`),
`telefon.fritzbox.benutzer`, `telefon.fritzbox.passwort` (verschlüsselt,
`v1:`-Präfix), `telefon.geschaeftsnummern` (kommagetrennt),
`telefon.anrufbeantworter` (kommagetrennte AB-Indizes),
`telefon.aufbewahrung.anrufe.monate` (Standard 12),
`telefon.aufbewahrung.sprachnachrichten.monate` (Standard 12),
`telefon.ortsvorwahl`, `telefon.landesvorwahl` (beim Verbindungstest von der
Box übernommen), Status: `telefon.abholung.letzter-erfolg`,
`telefon.abholung.letzter-fehler`.

## Rufnummern-Normalisierung

`RufnummerNormalisierer.normalisiere(String roh, String landesvorwahl,
String ortsvorwahl)` → E.164 oder `null`:

- Alles außer Ziffern und führendem `+` entfernen (Leerzeichen, `/`, `-`,
  `()`, Punkte). `(0)` in `+49 (0) 931 …` wird entfernt.
- `+49…` → unverändert; `0049…` → `+49…`; `0…` → `+49` + Rest ohne `0`;
  Nummer ohne führende `0` (Ortsnetz) → `+49` + Ortsvorwahl ohne `0` + Nummer.
- Leer, `anonym`, `unbekannt` oder weniger als 3 Ziffern → `null`.
- Kein Regex mit verschachtelten Wiederholungen (ReDoS); einfache
  Zeichenschleife.

Dieselbe Funktion normalisiert die Nummern der Box **und** die Nummern an
Kunden/Lieferanten, damit `0931 / 123 45-6`, `+49 931 123456` und `123456`
(bei Ortsvorwahl 0931) übereinstimmen.

## Zuordnung

`RufnummernZuordnungService.finde(String normalisiert)` →
`Treffer(kunde | lieferant)`, `Mehrdeutig(List<Kontakt>)` oder `Unbekannt`.

- Gesucht wird in `Kunde.telefon`, `Kunde.mobiltelefon`,
  `Lieferanten.telefon`, `Lieferanten.mobiltelefon` und `kontakt_rufnummer`.
- Da `Kunde`/`Lieferanten` keine normalisierte Spalte haben, baut der Service
  pro Abhollauf ein Verzeichnis `normalisiert → Kontakte` im Speicher auf
  (einige tausend Kontakte, vernachlässigbar). Keine neue Spalte an Kunde/
  Lieferant.
- **Genau ein Kontakt** → Zuordnung `AUTOMATISCH`.
- **Mehrere Kontakte** → keine Zuordnung; die API liefert die Kandidaten mit,
  die Oberfläche zeigt „Mögliche Kontakte: A, B“ zum Anklicken.
- **Keiner** → `KEINE`, Anzeige „Unbekannt“.
- Manuelle Zuordnungen (`MANUELL`) werden von der Automatik nie
  überschrieben.

**Nachträglich zuordnen ist optional.** Unbekannte Anrufe erzeugen keine
Aufgabe, keinen Zähler und keine Erinnerung. Wer möchte, klickt „Zuordnen“:
Dialog mit Kontaktsuche (Kunden und Lieferanten), Häkchen **„Nummer beim
Kontakt merken“** (Standard: an).

- Mit Häkchen: Eintrag in `kontakt_rufnummer`, danach werden **alle**
  Anrufe und Sprachnachrichten mit dieser normalisierten Nummer und
  `zuordnung = KEINE` automatisch nachgezogen.
- Ohne Häkchen: nur dieser eine Anruf/diese Nachricht wird `MANUELL`
  zugeordnet.
- „Zuordnung aufheben“ setzt auf `KEINE` zurück (ein gemerkter
  `kontakt_rufnummer`-Eintrag bleibt; er ist im Kontakt unter „Weitere
  Rufnummern“ sichtbar und dort löschbar).
- Anrufe mit unterdrückter Nummer können einzeln `MANUELL` zugeordnet
  werden, das Häkchen ist dann ausgeblendet.

Wird ein Kontakt neu angelegt oder seine Telefonnummer geändert, gleicht der
nächste Abhollauf bestehende `KEINE`-Einträge erneut ab (der Lauf prüft
dazu alle `KEINE`-Einträge der letzten Aufbewahrungsfrist).

## Abholung

`TelefonAbholService`, `@Scheduled(fixedDelay = 120_000, initialDelay = 60_000)`,
läuft nur bei `telefon.aktiv = true` und gesetzten Zugangsdaten. Ein Lauf
gleichzeitig (Lock), zusätzlich manuell auslösbar („Jetzt abholen“).

1. Anrufliste holen → Typ 9/11 überspringen → nur Geschäftsnummern →
   nicht vorhandene speichern → zuordnen.
2. Für jeden gewählten Anrufbeantworter die Nachrichtenliste holen → nicht
   vorhandene: Audiodatei herunterladen, ggf. umwandeln, unter
   `${file.upload-dir}/sprachnachrichten/<uuid>.wav` speichern (Pfad per
   `normalize()` + `startsWith(baseDir)` geprüft, nie Namen aus der Box
   verwenden), Datensatz anlegen, mit Anruf verknüpfen, zuordnen.
   Schlägt der Download fehl, wird kein Datensatz angelegt (nächster Lauf
   versucht es erneut).
3. `KEINE`-Einträge neu abgleichen.
4. Status `letzter-erfolg` setzen, `letzter-fehler` leeren.

**Fehler:** Box nicht erreichbar, 401, Zeitüberschreitung → Status
`letzter-fehler` mit verständlichem Grund („FRITZ!Box nicht erreichbar“,
„Benutzername oder Passwort falsch“). Geloggt wird nur beim **Wechsel** von
Erfolg zu Fehler bzw. bei geändertem Fehlergrund (kein Log-Spam alle 2 Min.),
ohne Rufnummern oder Namen. Verloren geht nichts: Der nächste erfolgreiche
Lauf holt über `days` nach.

## Aufbewahrung

`TelefonAufbewahrungService`, täglich 3:30 Uhr: Anrufe älter als
`aufbewahrung.anrufe.monate` löschen, Sprachnachrichten älter als
`aufbewahrung.sprachnachrichten.monate` samt Audiodatei löschen. Werte in den
Einstellungen änderbar (1–120 Monate). `kontakt_rufnummer` bleibt, sie hängt
am Kontakt.

## Berechtigungen **(Vorschlag)**

- Anrufliste, Anrufbeantworter, Reiter „Anrufe“, Zuordnen, Abhören,
  Live-Anzeige: alle angemeldeten Nutzer des PC-Frontends (wie E-Mail-Center).
  „Abgehört“ ist ein gemeinsamer Status für alle (wie ein gemeinsames
  Postfach), mit Name und Zeitpunkt, wer abgehört hat.
- Telefon-Einstellungen (Zugangsdaten, Geschäftsnummern, Fristen): nur
  `ADMIN`.

## API

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/api/telefon/anrufe?art=&nur=unbekannt&suche=&kundeId=&lieferantId=&seite=&groesse=` | Anrufliste, neueste zuerst, seitenweise |
| POST | `/api/telefon/anrufe/{id}/zuordnung` | `{kundeId \| lieferantId, nummerMerken}` |
| DELETE | `/api/telefon/anrufe/{id}/zuordnung` | Zuordnung aufheben |
| GET | `/api/telefon/sprachnachrichten?nurNeue=&kundeId=&lieferantId=` | Liste |
| GET | `/api/telefon/sprachnachrichten/anzahl-neu` | Zähler fürs Menü |
| GET | `/api/telefon/sprachnachrichten/{id}/audio` | WAV, `Range` unterstützt (Spulen) |
| PATCH | `/api/telefon/sprachnachrichten/{id}` | `{abgehoert: true \| false}` |
| POST | `/api/telefon/sprachnachrichten/{id}/zuordnung` | wie bei Anrufen |
| DELETE | `/api/telefon/sprachnachrichten/{id}/zuordnung` | |
| POST | `/api/telefon/abholen` | „Jetzt abholen“ |
| GET / PUT | `/api/telefon/einstellungen` | ADMIN; Passwort nur schreibend |
| POST | `/api/telefon/einstellungen/test` | ADMIN; liefert eigene Nummern + AB-Liste |
| DELETE | `/api/telefon/kontakt-rufnummern/{id}` | gemerkte Nummer entfernen |
| GET | `/api/telefon/live` | Etappe 2: SSE-Strom |

Die Antwort eines Anrufs/einer Nachricht enthält `kontakt` (`{typ: KUNDE |
LIEFERANT, id, name}`) oder `kandidaten` (bei mehrdeutig). DTOs, keine
Entities nach außen. CSRF bleibt aktiv; es wird nichts ausgenommen.

## Oberfläche (`react-pc-frontend`)

Design nach dem Skill `handwerkerprogramm-design` (rose/slate, Lucide-Icons,
vorhandene Pflicht-Komponenten aus `FRONTEND_UI.md`). Wording ohne
Fachbegriffe: „Anrufe“, „Anrufbeantworter“, „Verpasst“, „Unbekannt“,
„Zuordnen“, „Abgehört“.

**Menü** `RibbonNav` → Kategorie „Kommunikation“, neue Untergruppe
**Telefon**: „Anrufe“ (`/telefon/anrufe`, Icon `Phone`) und
„Anrufbeantworter“ (`/telefon/anrufbeantworter`, Icon `Voicemail`, Zähler
neuer Nachrichten). Beide Einträge öffnen dieselbe Seite `TelefonPage` mit
zwei Reitern. Ist die Telefon-Anbindung nicht eingerichtet, zeigt die Seite
einen leeren Zustand mit Verweis auf die Einstellungen (für ADMIN) bzw.
„Noch nicht eingerichtet – bitte an den Administrator wenden“.

**Reiter „Anrufe“**: Tabelle mit Art-Symbol (`PhoneIncoming`, `PhoneMissed`
rot, `PhoneOutgoing`, `PhoneOff`), **Wer** (Kontaktname als Link zur Akte,
sonst Nummer + Name aus dem Box-Telefonbuch + unaufdringlicher Knopf
„Zuordnen“, bei mehrdeutig „Mögliche Kontakte: …“), Nummer, Wann („Heute
11:55“, „Gestern 09:52“, sonst Datum), Dauer, Symbol falls eine
Sprachnachricht dazu existiert (Klick springt zum Abspielen). Filter-Chips:
Alle · Verpasst · Unbekannt; Suchfeld (Name oder Nummer); Knopf „Jetzt
abholen“; Hinweiszeile „Zuletzt abgeholt: vor 1 Minute“ bzw. Fehlergrund.

**Reiter „Anrufbeantworter“**: Liste, neue Nachrichten oben und fett mit
Punkt. Je Eintrag: Wer, Wann, Dauer, **Abspielen**-Knopf mit
Fortschrittsbalken und Spulen (HTML5-Audio, eigene Bedienelemente im
Design-System), Download. Beim Start der Wiedergabe wird die Nachricht als
abgehört markiert (**Vorschlag**); „Wieder als neu markieren“ im
Drei-Punkte-Menü. Anzeige „Abgehört von Max Mustermann, heute 12:04“.

**Zuordnen-Dialog** (`TelefonZuordnenDialog`): Umschalter „Kunde |
Lieferant“, der die vorhandenen `KundeSearchModal` bzw.
`LieferantSearchModal` für die Suche nutzt (keine neue Suchkomponente);
darunter der gewählte Kontakt, Häkchen „Nummer beim Kontakt merken“, Knöpfe
„Zuordnen“ / „Abbrechen“.

**Kundeneditor / LieferantenEditor**: neuer Reiter **„Anrufe“** (Icon
`Phone`, Zähler) neben „E-Mails“: chronologische Liste von Anrufen und
Sprachnachrichten dieses Kontakts, Sprachnachrichten direkt abspielbar.
Im Stammdaten-Bereich unter den Telefonfeldern: „Weitere Rufnummern“
(gemerkte Nummern, löschbar).

**Einstellungen** (`EinstellungenEditor`, nur ADMIN): Abschnitt „Telefon
(FRITZ!Box)“ mit Schalter aktiv, Adresse, Benutzer, Passwort, Knopf
„Verbindung testen“; nach erfolgreichem Test Auswahl der Geschäftsnummern
(Häkchen) und des Anrufbeantworters; Aufbewahrungsfristen; Status der
letzten Abholung. Daneben eine kurze Anleitung: in der FRITZ!Box unter
*System → FRITZ!Box-Benutzer* einen Benutzer (z. B. `erp`) mit den Rechten
„Sprachnachrichten, Faxnachrichten, FRITZ!App Fon und Anrufliste“ anlegen;
unter *Heimnetz → Netzwerk → Netzwerkeinstellungen* „Zugriff für
Anwendungen zulassen“ (TR-064) aktivieren; für Etappe 2 an einem Telefon
`#96*5*` wählen (Anrufmonitor).

## Etappe 2: Live-Anzeige

**Backend:** `FritzAnrufmonitorClient` hält bei `telefon.aktiv` eine
TCP-Verbindung zu `host:1012`. Zeilenformat
`dd.MM.yy HH:mm:ss;RING;<verbindungsId>;<anrufer>;<angerufen>;<leitung>;`
bzw. `CALL`, `CONNECT`, `DISCONNECT`. Verarbeitet werden nur `RING` (auf
Geschäftsnummer) sowie `CONNECT`/`DISCONNECT` zu bekannten Verbindungs-IDs.
Reconnect mit Backoff (5 s → 60 s); Verbindungsstatus erscheint in den
Einstellungen („Anrufmonitor verbunden“ / „nicht erreichbar – #96*5*
gewählt?“). Bei `DISCONNECT` wird zusätzlich sofort eine Abholung angestoßen,
damit der Anruf ohne Wartezeit in der Liste steht.

`TelefonLiveService` verwaltet `SseEmitter` (Timeout 30 Min., Heartbeat-
Kommentar alle 25 s, abgestorbene Emitter werden entfernt). Ereignisse:
`anruf-klingelt` (`{verbindungsId, nummer, kontakt | kandidaten | null,
nameFritzbox}`) und `anruf-beendet` (`{verbindungsId, angenommen}`).
Endpoint erfordert Anmeldung wie alle `/api`-Pfade.

**Frontend:** Hook `useTelefonLive` im Layout öffnet einen `EventSource`
auf `/api/telefon/live` (mit Wiederverbindung). Bei `anruf-klingelt`
erscheint unten rechts eine Karte: „**Mustermann GmbH** ruft an“ (Kunde/
Lieferant als Kennzeichen), Nummer, Knopf „Akte öffnen“; bei unbekannt
„Unbekannte Nummer 0931 …“ ohne weiteren Knopf. Die Karte verschwindet bei
`anruf-beendet` oder nach 60 s und lässt sich wegklicken. Kein Ton.

## Sicherheit und Datenschutz

- Nur Geschäftsnummern gelangen ins ERP (siehe Grundregel).
- Keine Rufnummern, Namen oder Audiodaten in Logs.
- Passwort verschlüsselt (`MailSecretService`), nie im Frontend.
- Audiodateien nur über den authentifizierten Endpoint, nie als statische
  Ressource; Dateinamen vom ERP erzeugt, Pfadprüfung gegen das Basisverzeichnis.
- XXE-sicheres XML-Parsing, kein ReDoS-anfälliger Regex.
- Die FRITZ!Box-Adresse ist nur durch ADMIN änderbar (begrenzt SSRF auf
  Administratoren); es werden nur die festen TR-064-Pfade und von der Box
  gelieferte Download-Pfade auf **demselben Host** aufgerufen.
- Automatisches Löschen nach Frist.
- Tests und Fixtures nur mit Dummy-Daten (`Max Mustermann`,
  `Mustermann GmbH`, Nummern wie `0931 1234567`).

## Tests

- **Unit:** `RufnummerNormalisierer` (alle Schreibweisen, Ortsnetz,
  `(0)`, `0049`, anonym, Müll), `RufnummernZuordnungService` (eindeutig,
  mehrdeutig, unbekannt, manuell nicht überschreiben, Nachziehen),
  `FritzAnruflisteParser` / `FritzSprachnachrichtenParser` mit synthetischen
  XML-Fixtures (inkl. Typ 9/11, private Nummern werden gefiltert, XXE-Payload
  wird abgelehnt), Anrufmonitor-Zeilenparser, Digest-/SOAP-Client gegen
  lokalen Testserver (MockWebServer o. Ä.).
- **Service/Repository:** Abholung idempotent (zweiter Lauf legt nichts
  doppelt an), fehlgeschlagener Download legt nichts an, Aufbewahrung löscht
  Datensatz und Datei, Fehlerstatus/Log nur bei Wechsel.
- **Controller-Slice:** Berechtigungen (Einstellungen nur ADMIN), Passwort
  nie in der Antwort, Audio mit `Range`, Zuordnung mit/ohne Merken.
- **Frontend:** Vitest für TelefonPage (Filter, Zuordnen-Dialog, Abhören
  markiert als abgehört), Reiter in Kunden-/LieferantenEditor, Live-Karte;
  Playwright-Designprüfung laut Skill `playwright-design-pruefung`.
- **Manuell** gegen die echte FRITZ!Box: Verbindungstest, Abholung,
  Abspielen, Live-Anzeige (Etappe 2).

## Abnahmekriterien

1. Nach Eintragen der Zugangsdaten und „Verbindung testen“ zeigt das ERP die
   eigenen Nummern; nach Auswahl von 2323 erscheinen binnen 2 Minuten die
   Anrufe auf 2323 – und keine Anrufe auf 980850/980860.
2. Ein Anruf von einer bei einem Kunden hinterlegten Nummer (egal in welcher
   Schreibweise) ist automatisch diesem Kunden zugeordnet und erscheint in
   dessen Reiter „Anrufe“.
3. Eine unbekannte Nummer kann – muss aber nicht – zugeordnet werden; mit
   „Nummer merken“ werden auch frühere und künftige Anrufe dieser Nummer
   zugeordnet.
4. Eine Nachricht auf dem Anrufbeantworter ist im ERP abspielbar, wird beim
   Abspielen als abgehört markiert und bleibt erhalten, wenn sie auf der Box
   gelöscht wird. Auf der Box bleibt sie unverändert.
5. Nach Ablauf der Frist werden Anrufe und Aufnahmen (inkl. Datei) gelöscht.
6. Ist die Box nicht erreichbar, zeigt das ERP den Grund an und holt nach
   Wiederherstellung alles nach.
7. (Etappe 2) Klingelt 2323, erscheint innerhalb von ca. 1 Sekunde die
   Live-Karte mit dem zugeordneten Kontakt; Anrufe auf privaten Nummern
   lösen keine Karte aus.
