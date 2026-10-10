# ERP Handwerk beim Kunden: einrichten mit Docker, automatische Updates nachts

Ein Ordner, ein Befehl: Datenbank, ERP und ein kleiner **Updater** laufen als
Docker-Container. Sie starten nach jedem Neustart des Rechners von selbst. Neue
Versionen holt sich der Rechner nachts um 3 Uhr selbst ab (Pull-Prinzip).
GitHub braucht also keinen Zugang ins Firmennetz: keine offenen Ports, kein VPN.

```
 git push main ─► GitHub Actions                       Rechner beim Kunden (Windows-PC oder Linux-Server)
                  1. Tests (PR Quality Checks)          ┌─ postgres ─ Datenbank (eigener Server: mysql)
                  2. Docker-Image (Backend + beide      ├─ app ───── ERP (Backend + PC-Oberflaeche + Zeiterfassung)
                     Frontends in EINEM Image)          └─ updater ─ 03:00: neues Image holen ◄─────┐
                  3. Starttest: Kundenstand + neue          sichern, wechseln, Health-Check,     │
                     Migrationen, PostgreSQL + MySQL        bei Fehler Rollback + Handy-Nachricht│
                  4. nur wenn gruen: ghcr.io :stable ───────────────────────────────────────────┘
```

**Backend und Frontend kommen immer zusammen.** Das Image enthält das Spring-Boot-Backend
und beide Oberflächen (PC und Zeiterfassungs-App). Ein Update tauscht alles auf einmal.
Damit können Oberfläche und Backend nie auseinanderlaufen. Die Zeiterfassungs-App auf den
Handys lädt die neue Version beim nächsten Öffnen.

## Einrichten

### Welche Datenbank?

| | Datenbank | Einrichten |
| --- | --- | --- |
| **Alle Installationen** | PostgreSQL (Standard) | `Einrichten.cmd` bzw. `./einrichten.sh` |
| Altinstallationen | MySQL (Übergang, wird entfernt) | `Einrichten.cmd -Datenbank mysql` bzw. `./einrichten.sh --mysql` |

Der eigene Server läuft seit dem 10.10.2026 ebenfalls auf PostgreSQL. MySQL bleibt nur
noch als Rückweg im Code, bis PostgreSQL sich im Betrieb bewährt hat.

Beide bekommen **dasselbe Image und dieselben Nachtupdates**. Die App erkennt die
Datenbank am Profil (`COMPOSE_PROFILES` in der `.env`) und nimmt die passende
Migrationslinie. Die Wahl gilt für immer: Ein späteres Umstellen in der `.env` zieht die
Daten nicht mit um.

Auf PostgreSQL verbindet sich das ERP mit einer eigenen Rolle **ohne Superuser-Rechte**
(`DB_USER`, angelegt von `postgres-init/01-app-rolle.sh` beim ersten Start). Der
Superuser `postgres` (`DB_ROOT_PASSWORD`) dient nur für Wartung, Sicherung und Rollback.

> **`.env` aus einer früheren Einrichtung** (mit `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`
> …): Die Variablen heißen jetzt `DB_ROOT_PASSWORD`, `DB_PASSWORD`, `DB_USER`, dazu
> kommt `COMPOSE_PROFILES=mysql`. Werte übernehmen, Namen anpassen – sonst startet
> nichts, und das Nachtupdate meldet sich per Nachricht.

### Windows-PC (Docker Desktop)

1. Diesen Ordner (`deployment/docker-server`) auf den PC kopieren, z. B. nach `C:\ERP-Handwerk`.
2. **`Einrichten.cmd` doppelklicken.** Das Skript erledigt Folgendes:
   - Es prüft Docker Desktop und bietet die Installation an, falls es fehlt.
   - Docker Desktop startet ab jetzt beim Anmelden automatisch.
   - Der PC geht am Netzstrom nicht mehr in Standby oder Ruhezustand.
   - Der Ordner wird nur für Administratoren und den aktuellen Benutzer freigegeben. Darin
     liegen Passwörter (`.env`) und Sicherungen mit allen Kundendaten.
   - Es legt die `.env` mit zufälligen Passwörtern an und fragt nach dem Namen des Betriebs.
   - Es startet alles, wartet, bis das ERP läuft, und legt die Verknüpfung „ERP Handwerk“ auf den Desktop.
3. Im Browser öffnet sich das ERP. **Wer sich als Erster registriert, wird Admin.** Das
   also direkt nach der Einrichtung selbst erledigen. Alternativ in der `.env`
   `APP_ADMIN_USER` und `APP_ADMIN_PASS` setzen, dann wird der Admin vorab angelegt.

Scheitert schon der allererste Start (z. B. Strom weg, während die Datenbank eingerichtet
wird), ist die Datenbank halb angelegt. Dann einmal neu beginnen:
`docker compose down -v` (löscht die noch leere Datenbank) und erneut einrichten.

> **Wichtig für PCs, die immer laufen sollen:** Docker Desktop startet erst, wenn sich
> jemand an Windows **anmeldet**. Nach einem Stromausfall oder einem Windows-Update-Neustart
> läuft das ERP deshalb erst wieder, wenn sich jemand angemeldet hat. Abhilfe ist die
> automatische Anmeldung, am sichersten mit
> [Autologon von Microsoft Sysinternals](https://learn.microsoft.com/sysinternals/downloads/autologon),
> weil es das Passwort verschlüsselt ablegt. Dann aber eine Bildschirmsperre einrichten,
> sonst steht der PC nach jedem Neustart angemeldet und offen da.
>
> Docker Desktop ist für kleine Betriebe kostenlos (unter 250 Mitarbeitende **und** unter
> 10 Mio. USD Umsatz). Größere Betriebe brauchen eine Lizenz oder einen Linux-Server.

### Linux-Server

```bash
sudo mkdir -p /opt/erp && sudo cp -r deployment/docker-server/. /opt/erp/ && cd /opt/erp
sudo KUNDE_NAME="Schreinerei Muster" ./einrichten.sh
```

Das Skript lässt Docker beim Systemstart mitstarten, legt die `.env` mit zufälligen
Passwörtern an und startet alles. Danach genügt jederzeit `docker compose up -d`.

### Umzug von einem bestehenden Server

Ein leerer Rechner richtet die Datenbank selbst ein, mit Basis-Schema und Stammdaten.
Bei einem **Umzug** spielst du stattdessen die Sicherung des alten Servers ein. Eine
Sicherung passt nur zur **selben Datenbankart**: Die tägliche Sicherung des eigenen
Windows-Servers (`kalkulationsprogramm_db_<datum>.sql.gz`, MySQL) also in eine
MySQL-Einrichtung. Eine unpassende Datei lehnt das Skript ab, ohne etwas zu verändern.

```bash
# Linux:   sudo ./einrichten.sh --mysql --import /pfad/zur/sicherung.sql.gz
# Windows: Einrichten.cmd -Datenbank mysql, Sicherung in den ERP-Ordner legen, dann dort:
docker compose exec updater bash /erp/nachtupdate.sh --importieren sicherung.sql.gz
```

Was vorher in der Datenbank stand, wird vor dem Import gesichert (`sicherungen/vor-import-…`).
Eine PostgreSQL-Sicherung von woanders muss eine Klartext-Sicherung ohne Besitzer sein:
`pg_dump --no-owner --no-privileges <datenbank> | gzip > sicherung.sql.gz` (kein `-Fc`).

### Image privat?

Ist das Paket auf GitHub privat, braucht jeder Rechner einmal eine Anmeldung. Pro Kunde
ein eigenes Token, nur mit dem Recht `read:packages`:

```bash
echo <TOKEN> | docker login ghcr.io -u <github-benutzer> --password-stdin
```

Für ein Open-Source-ERP ist es einfacher, das Paket öffentlich zu stellen
(GitHub → Packages → Package settings → Change visibility).

### Weitere Einstellungen: `app.env`

API-Schlüssel, der Schlüssel für die Mail-Zugänge, Funktionsschalter und alles andere, was
früher in der `application-local.properties` stand, kommen in die Datei `app.env` neben der
`.env`. Sie ist optional und wird nie committet. Der Name ergibt sich aus dem Schlüssel:
Punkte werden zu `_`, Bindestriche entfallen, alles groß. Werte in einfachen Anführungszeichen:

```ini
AI_GEMINI_APIKEY='...'
MAIL_CREDENTIALS_ENCRYPTIONKEY='...'
EMAIL_FEATURES_ENABLED='true'
```

Nach einer Änderung: `docker compose up -d app`.

### Uploads in einem vorhandenen Ordner

Standardmäßig liegen Uploads im Docker-Volume `app_uploads`. Soll ein vorhandener Ordner
auf dem Rechner bleiben, etwa weil er im Firmennetz freigegeben ist (CAD-Zeichnungen),
steht er in der `.env` unter `UPLOADS_ORDNER`. Unter Windows schreibt man ihn so, wie ihn die
Docker-Desktop-VM sieht. Dann versteht ihn auch der Updater:

```ini
UPLOADS_ORDNER=/run/desktop/mnt/host/c/Kalkulationsprogramm/uploads
```

Solche Ordner sichert das Nachtupdate nicht mit. Das bleibt Aufgabe der täglichen Sicherung.

### Hinter `tailscale serve` (nur über Tailscale erreichbar)

```ini
APP_ADRESSE=127.0.0.1
LOKALER_PROXY=ja
```

Der Port lauscht dann nur auf dem Rechner selbst, im LAN ist nichts offen. `tailscale serve`
leitet an `http://127.0.0.1:<APP_PORT>` weiter. Mit `LOKALER_PROXY=ja` glaubt das ERP dem
Proxy die echte Absender-Adresse (`X-Forwarded-For`). Das ist nötig für die Regel „von außen
nur die Zeiterfassung" und für die Code-Sperre pro Gerät. **Nie mit `APP_ADRESSE=0.0.0.0`
kombinieren:** Unter Docker Desktop kommen dann alle Anfragen aus dem LAN über dieselbe
Gateway-Adresse, und jeder könnte sich als beliebiger Absender ausgeben.

**Handys ohne Tailscale-App (Funnel):** Standardmäßig brauchen die Handys der Mitarbeiter die
Tailscale-App. Soll die Zeiterfassung ohne App aus dem Internet erreichbar sein, am Rechner
`tailscale funnel --bg <APP_PORT>` ausführen und im ERP unter *Einstellungen → Zeiterfassung →
Zugang von unterwegs* „Über Tailscale Funnel freigeben“ wählen. Ohne diese Freigabe weist das
ERP jede Funnel-Anfrage ab. Von außen ist dann nur die Zeiterfassung erreichbar. Zurück:
`tailscale serve --bg <APP_PORT>` (Funnel aus, Tailscale-Netz bleibt) und im ERP umstellen.
Details: [INSTALLATION.md, Variante 1b](../../docs/INSTALLATION.md#variante-1b-tailscale-funnel--handys-ohne-tailscale-app).

## Sicherheit: so ist das ERP abgeschottet

Findet jemand eine Lücke im ERP, soll er weder an den Rechner noch an das Firmennetz kommen.

| Maßnahme | Wo | Wirkung |
| --- | --- | --- |
| Eigener Benutzer `erp` (uid 10001) | `Dockerfile`, `deployment/docker-image/app-start.sh` | Java läuft nie als root. Das Startskript gibt nur noch alte Upload-/Log-Ordner an `erp` und wechselt dann den Benutzer. Dabei verliert der Prozess alle Berechtigungen. |
| Schreibgeschütztes Dateisystem | `read_only: true` | Das ERP kann weder sich selbst noch das System im Container verändern. Beschreibbar: Uploads, Logs, `/tmp`. |
| Keine Linux-Berechtigungen | `cap_drop: [ALL]`, `no-new-privileges` | Kein Zugriff auf Kernel-Funktionen, kein Weg zurück zu root. |
| Kein Docker-Socket für die App | `docker-compose.yml` | Den Socket hat nur der Updater. Er hat keinen Port und führt nur `nachtupdate.sh` aus. |
| Datenbank ohne Netz nach außen | Netz `intern` (`internal: true`) | PostgreSQL/MySQL erreicht nur die App, keinen Port und kein Internet. |
| Netzsperre | Dienst `netzsperre` | Die App darf ins Internet (Mail, KI, Website), aber nicht ins Firmennetz: keine PCs, keine Buchhaltung, keine Dateifreigaben, auch nicht der Rechner selbst. Ausnahmen in `NETZSPERRE_AUSNAHMEN`, Standard `fritz.box:49000` (Anrufliste und Anrufbeantworter). |
| Nur ein Port | `APP_ADRESSE`, `APP_PORT` | Der Browser-Zugang, sonst nichts. Hinter `tailscale serve` nur auf `127.0.0.1`. |

Die Netzsperre setzt Regeln in Dockers eigene Firewall-Kette `DOCKER-USER`, nur für die
Bridge `erp-extern`. Das geht auf Linux-Servern und in der Docker-Desktop-VM unter Windows
gleich. Docker löscht die Regeln bei jedem Neustart, deshalb läuft der Dienst dauerhaft, setzt
sie beim Start und prüft sie jede Minute. Beim Stoppen entfernt er sie. Prüfen:
`docker compose logs netzsperre` (dort steht „Sperre aktiv“ mit den erlaubten Zielen).
Ausschalten (nicht empfohlen): `NETZSPERRE=nein` in der `.env`, dann
`docker compose up -d netzsperre`.

> **Bestehende Installationen:** Nach dem Kopieren dieser `docker-compose.yml` einmal
> `docker compose up -d` ausführen. Damit entstehen die Netze `intern`/`extern` und alle
> Dienste werden neu angelegt. Das Nachtupdate tauscht nur die App und würde sie sonst in
> ein Netz ohne Datenbank stellen.

Am sichersten ist ein eigener kleiner Rechner nur für das ERP, in einem eigenen Netz (VLAN
oder Gastnetz). Dann bleibt auch beim schlimmsten Vorfall der Rest der Firma unberührt.

## Was nachts passiert

| Schritt | Was | Wenn's schiefgeht |
| --- | --- | --- |
| 1 | `docker pull` von `ERP_IMAGE` (`:stable`). Gleiches Image wie aktiv → fertig. | Nachricht „Download fehlgeschlagen“, nichts verändert |
| 2 | Läuft die aktuelle Version gesund? (Health-Check, max. 2 min) | Nachricht „Update ausgelassen“, denn ohne gesunde Ausgangslage gibt es keinen sicheren Rückweg |
| 3 | Platz prüfen, **App stoppen**, dann komplette Sicherung (`pg_dump` bzw. `mysqldump`) nach `sicherungen/` | Alte Version startet wieder, Nachricht |
| 4 | Neue Version starten, Flyway spielt offene Migrationen selbst ein | |
| 5 | Jede Sekunde `/actuator/health` abfragen (Standard: max. 300 s) | Ein Absturz wird sofort erkannt |
| 6 | **Rollback:** neue Version stoppen, Datenbank aus der Sicherung zurück, alte Version starten | Handy-Nachricht. Scheitert auch das: NOTFALL-Nachricht |

PostgreSQL leert und befüllt die Datenbank beim Rollback in **einer** Transaktion:
Scheitert das Einspielen, bleibt sie, wie sie war. MySQL kann das nicht (DDL läuft dort
außerhalb von Transaktionen).

- **App vor der Sicherung stoppen:** Sonst könnte zwischen Sicherung und Update noch
  jemand buchen, und der Rollback würde diese Buchung verschlucken.
- **Gescheiterte Versionen** werden nicht jede Nacht neu versucht, sondern erst, wenn
  eine neuere kommt.
- **Stromausfall oder Neustart mitten im Update:** Der Updater bringt einen
  unterbrochenen Rollback beim nächsten Start selbst zu Ende und meldet sich.
- **NOTFALL:** Scheitert auch der Rollback, oder läuft das ERP nach einer Unterbrechung
  schon wieder, weil jemand es gestartet hat, dann legt der Updater `.status/notfall` an
  und schickt eine dringende Nachricht. Ab da **verändert er nichts mehr**, also keine
  Updates, keine Rollbacks und keine Importe. Er erinnert nur jede Nacht. Grund: Inzwischen
  kann wieder gearbeitet worden sein, und eine alte Sicherung würde diese Arbeit löschen.
  Wenn du den Stand geprüft hast (die Sicherung liegt in `sicherungen/`), die Datei
  `.status/notfall` löschen. Danach läuft alles wieder automatisch.
- **Welche Version läuft,** steuert allein der Updater. Bitte **kein** `docker compose pull`
  von Hand. Das nächste `docker compose up` würde sonst ohne Sicherung wechseln (ein
  einfacher Neustart dagegen behält die laufende Version).

## Handy-Nachrichten

Empfohlen ist [ntfy](https://ntfy.sh), eine kostenlose App für Android und iOS. Eintrag in der `.env`:

```ini
WEBHOOK_URL=https://ntfy.sh/<langer-zufaelliger-name>
WEBHOOK_TOKEN=
WEBHOOK_BEI_ERFOLG=nein
WEBHOOK_MIT_FEHLERAUSZUG=nein
```

(Kommentare in der `.env` immer in eine eigene Zeile schreiben, nie hinter den Wert.)

- Der Themenname bei ntfy.sh ist wie ein Passwort: Wer ihn kennt, liest mit. Besser ist
  ein eigener ntfy-Server oder ein ntfy-Konto mit `WEBHOOK_TOKEN`.
- Mit `WEBHOOK_BEI_ERFOLG=ja` kommt auch bei Erfolg eine leise Nachricht.
- Für Slack oder Mattermost: `WEBHOOK_FORMAT=json`.
- Mit `WEBHOOK_MIT_FEHLERAUSZUG=ja` kommen die wichtigsten Fehlerzeilen mit. Vorher werden
  geschwärzt: Werte in Anführungszeichen, Inhalte in `{…}`, E-Mail- und IP-Adressen.
  Personenbezug lässt sich in Fehlermeldungen trotzdem nie ganz ausschließen. Bei einem
  fremden Dienst ist das **Auftragsverarbeitung (DSGVO)**, deshalb ist der Auszug
  standardmäßig aus.

## Alltag

| Aufgabe | Befehl (im ERP-Ordner) |
| --- | --- |
| Alles starten | `docker compose up -d` |
| Update sofort testen | `docker compose exec updater bash /erp/nachtupdate.sh` |
| Gescheiterte Version trotzdem einspielen | `docker compose exec updater bash /erp/nachtupdate.sh --erzwingen` |
| Bestimmte Version festhalten | in `.env`: `ERP_IMAGE=ghcr.io/…:sha-abc1234`, dann **`docker compose exec updater bash /erp/nachtupdate.sh --erzwingen`** (nicht `compose up`, das würde ohne Sicherung wechseln) |
| Andere Update-Zeit | in `.env`: `UPDATE_ZEITPLAN=30 2 * * *`, dann `docker compose up -d updater` |
| Logs des Nachtupdates | Ordner `logs/` |
| App-Log live | `docker compose logs -f app` |
| Updater selbst erneuern (selten) | `docker compose build --pull updater && docker compose up -d updater` |

`sicherungen/` hält die letzten 14 Sicherungen von vor den Updates
(`SICHERUNGEN_BEHALTEN`). Sie sind **kein Ersatz** für eine tägliche Sicherung außer
Haus. Die hochgeladenen Dateien (Volume `app_uploads`) sichern sie zum Beispiel nicht.

**Die Dateien in diesem Ordner** (Skripte, `docker-compose.yml`) aktualisiert das
Nachtupdate nicht, sondern nur das ERP selbst. Änderungen daran müssen einmal von Hand
auf die Rechner der Kunden kopiert werden. `.env`, `sicherungen/` und `logs/` dabei
nicht überschreiben.

## Offene Punkte

- **PostgreSQL 16:** Ein späterer Wechsel der Hauptversion (z. B. auf 17) geht nicht
  durch einfaches Austauschen des Images, sondern braucht Sicherung und Wiedereinspielen
  (oder `pg_upgrade`). Das muss einmal geplant werden, wenn es so weit ist.
- **Zeitzone:** Der PostgreSQL-Container läuft auf deutscher Zeit (`TZ=Europe/Berlin`),
  MySQL-Server meist auf UTC. Die App setzt Zeitstempel selbst, das ist also gleich. Nur
  wo die Datenbank selbst „jetzt“ einträgt (Spalten-Standardwert `CURRENT_TIMESTAMP`),
  steht bei PostgreSQL Ortszeit.
- **MySQL 8.0** bekommt seit April 2026 keine Updates mehr. Der eigene Produktivserver
  läuft auch noch darauf. Der Umstieg auf **8.4 LTS** sollte für beide gemeinsam geplant
  und getestet werden.
- **PostgreSQL verhält sich in Kleinigkeiten anders als MySQL.** Abgefangen sind
  Spaltentypen, Upserts, Datumsrechnung und Parameter-Typen (`DatenbankKompatibilitaetTest`
  prüft jede Abfrage gegen beide Datenbanken). Bekannte Unterschiede, die noch auffallen
  können: `=` und `LIKE` unterscheiden in PostgreSQL Groß-/Kleinschreibung (die
  Suchfelder nutzen `LOWER`, Einzelvergleiche nicht immer), leere Werte sortieren bei
  `ORDER BY … DESC` oben statt unten, und bei Verstößen gegen Eindeutigkeit/Fremdschlüssel
  zeigt die Oberfläche eine allgemeine statt der genauen Fehlermeldung.

## Neuinstallation: das Basis-Schema

Die Flyway-Migrationen beginnen erst bei V208, das Grundschema davor hat früher
Hibernate angelegt. Deshalb spielt die App auf einer **leeren** Datenbank zuerst
`src/main/resources/db/basis/V1__basis_schema.sql` ein: Schema, Stammdaten aus den
Migrationen und die Flyway-Historie (`FlywayStartSetupConfig`). Danach laufen die
normalen Migrationen. Bestehende Datenbanken sind davon nicht betroffen.

Erzeugt wird die Datei mit `scripts/basis-schema/erzeugen.sh`. Neu erzeugen muss man
sie nur selten, denn neue Migrationen laufen auf der Basis ganz normal.

**PostgreSQL** hat eine eigene Basis (`db/postgresql/basis`, erzeugt mit
`scripts/basis-schema/postgres_erzeugen.sh`) und eine eigene Migrationslinie
(`db/postgresql/migration`). Jede neue Migration gibt es deshalb **zweimal mit derselben
Nummer**, einmal pro Datenbank. Fehlt ein Zwilling, scheitert der Build
(`PostgresMigrationslinieTest`). Übersetzungshilfe: `db/postgresql/migration/README.md`.

## Der Starttest in GitHub

`.github/workflows/docker-image.yml` läuft nach jedem grünen Push auf `main`. Es
veröffentlicht nur, wenn `starttest.sh` grün ist. Dabei richtet das aktuelle `:stable`
(also der Kundenstand) eine leere Datenbank ein, darauf startet die neue Version, und
`/actuator/health` muss 200 liefern. Neue Migrationen laufen dabei einmal echt durch,
auch solche mit kleinerer Nummer (out-of-order). Das passiert zweimal: auf PostgreSQL
und auf MySQL. Lokal geht es genauso:

```bash
STARTTEST_DB=postgres deployment/docker-server/starttest.sh <neues-image> [<image-der-kunden>]
STARTTEST_DB=mysql    deployment/docker-server/starttest.sh <neues-image> [<image-der-kunden>]
```

Grenze: Die Testdatenbank enthält nur Stammdaten. Migrationen, die nur an echten Daten
scheitern (z. B. Dubletten bei einem neuen UNIQUE), fängt erst das Nachtupdate ab,
dann mit Rollback.

## Dateien

| Datei | Zweck |
| --- | --- |
| `docker-compose.yml` | postgres oder mysql (Profil), app, updater, netzsperre, alle mit `restart: always` |
| `postgres-init/` | Legt beim ersten Start die App-Rolle in PostgreSQL an (ohne Superuser-Rechte) |
| `.env.example` | Vorlage für `.env`. Die `.env` wird beim Einrichten erzeugt und nie committet |
| `app.env` | Optional, weitere ERP-Einstellungen (API-Schlüssel …). Nie committet |
| `netzsperre/` | Image der Netzsperre (Firewall-Regeln: ERP nicht ins Firmennetz) |
| `Einrichten.cmd`, `einrichten-windows.ps1` | Einrichtung auf einem Windows-PC |
| `einrichten.sh` | Einrichtung auf einem Linux-Server, optional mit `--mysql` und `--import` |
| `nachtupdate.sh` | Nachtupdate, Rollback, Wiederaufnahme nach Neustart, Import |
| `updater/` | Image des Updater-Containers (Zeitplan per crond) |
| `starttest.sh` | Starttest für die GitHub Action |
