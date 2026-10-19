# ERP Handwerk beim Kunden: einrichten mit Docker, automatische Updates nachts

Ein Ordner, ein Befehl: Datenbank, ERP, ein kleiner **Updater** und die **Netzsperre** laufen
als Docker-Container. Sie starten nach jedem Neustart des Rechners von selbst. Jede Nacht um
2 Uhr sichert der Updater Datenbank und hochgeladene Dateien, um 3 Uhr holt er neue Versionen
selbst ab (Pull-Prinzip). GitHub braucht also keinen Zugang ins Firmennetz: keine offenen
Ports, kein VPN.

```
 Pull Request ──► GitHub Actions                        Rechner beim Kunden (Windows-PC oder Linux-Server)
  ─► Merge auf    1. Tests im PR (PR Quality Checks),   ┌─ postgres ─── Datenbank (PostgreSQL 16)
     main            beim Bauen nachgeprueft            ├─ app ──────── ERP (Backend + PC-Oberflaeche + Zeiterfassung)
                  2. Docker-Image (Backend + beide      ├─ netzsperre ─ haelt das ERP aus dem Firmennetz heraus
                     Frontends in EINEM Image)          └─ updater ──── 02:00: Sicherung (Rechner, Platte, Cloud)
                  3. Starttest: Kundenstand + neue                      03:00: neues Image holen ◄─────────────┐
                     Migrationen auf PostgreSQL                         sichern, wechseln, Health-Check,       │
                  4. nur wenn gruen: ghcr.io :stable ──┐                bei Fehler Rollback + Nachricht        │
                                                       └───────────────────────────────────────────────────────┘
```

**Backend und Frontend kommen immer zusammen.** Das Image enthält das Spring-Boot-Backend
und beide Oberflächen (PC und Zeiterfassungs-App). Ein Update tauscht alles auf einmal.
Damit können Oberfläche und Backend nie auseinanderlaufen. Die Zeiterfassungs-App auf den
Handys lädt die neue Version beim nächsten Öffnen.

## Einrichten

> **Neuer Kunde?** Die Schritt-für-Schritt-Anleitung zum Abhaken – vom leeren PC bis zur
> ersten geprüften Sicherung – steht in **[EINRICHTEN.md](EINRICHTEN.md)**.

### Die Datenbank

Alle Installationen laufen auf **PostgreSQL 16**, auch der eigene Server (seit dem
10.10.2026). Die Datenbank ist ein eigener Container (`postgres`) und kommt beim
Einrichten automatisch mit. Eine leere Datenbank richtet die App beim ersten Start selbst
ein.

Das ERP verbindet sich mit einer eigenen Rolle **ohne Superuser-Rechte** (`DB_USER`,
angelegt von `postgres-init/01-app-rolle.sh` beim ersten Start). Der Superuser `postgres`
(`DB_ROOT_PASSWORD`) dient nur für Wartung, Sicherung und Rollback.

### Ältere Einrichtungen

- **`COMPOSE_PROFILES=postgres` in der `.env`:** Diese Zeile brauchte es früher. Sie
  schadet nicht und darf stehen bleiben.
- **`COMPOSE_PROFILES=mysql` in der `.env`:** Diese Installation läuft noch auf MySQL. Das
  ERP kennt nur noch PostgreSQL. Die Einrichtung bricht deshalb ab, das Nachtupdate
  verändert nichts und meldet sich per Nachricht. Die Daten müssen erst nach PostgreSQL umziehen, wie beim
  eigenen Server am 10.10.2026. Danach die Zeile löschen. Dasselbe gilt für eine `.env`
  mit `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` … aus einer sehr frühen Einrichtung. Dort
  fehlen `DB_ROOT_PASSWORD` und `DB_PASSWORD`, und Docker Compose bricht mit „… fehlt in .env“
  ab.
- **Alter Container `erp-mysql`:** Mit dieser `docker-compose.yml` startet er nicht mehr.
  Docker nennt ihn dann „orphan“ (verwaist). Seine Daten im Volume
  `erp-handwerk_mysql_data` bleiben liegen, bis man sie bewusst löscht
  (`docker volume rm erp-handwerk_mysql_data`). Vorher prüfen, dass alles auf PostgreSQL
  angekommen ist.

### Windows-PC (Docker Desktop)

1. Diesen Ordner (`deployment/docker-server`) auf den PC kopieren, z. B. nach `C:\ERP-Handwerk`.
2. **`Einrichten.cmd` doppelklicken.** Das Skript erledigt Folgendes:
   - Es prüft Docker Desktop und bietet die Installation an, falls es fehlt.
   - Docker Desktop startet ab jetzt beim Anmelden automatisch.
   - Der PC geht am Netzstrom nicht mehr in Standby oder Ruhezustand.
   - Die Nutzungszeit von Windows Update wird auf 6 bis 22 Uhr gesetzt. In dieser Zeit
     startet Windows nicht von selbst neu (braucht Administratorrechte, sonst kommt ein
     Hinweis zum Einstellen von Hand).
   - Es richtet den [Docker-Wächter](#docker-wächter-windows) ein.
   - Der Ordner wird nur für Administratoren, das Systemkonto und den aktuellen Benutzer
     freigegeben. Darin liegen Passwörter (`.env`) und Sicherungen mit allen Kundendaten.
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

Voraussetzung sind Docker mit dem Compose-Plugin (`docker compose`) und `curl`. Das Skript
lässt Docker beim Systemstart mitstarten, legt die `.env` mit zufälligen Passwörtern an und
startet alles. Einen Cronjob `/etc/cron.d/erp-nachtupdate` aus einer früheren Einrichtung
entfernt es, denn das Nachtupdate läuft jetzt im Updater. Danach genügt jederzeit
`docker compose up -d`.

### Umzug von einem bestehenden Server

Ein leerer Rechner richtet die Datenbank selbst ein, mit Basis-Schema und Stammdaten.
Bei einem **Umzug** spielst du stattdessen die Sicherung des alten Servers ein, zum
Beispiel die neueste Datei aus dessen `sicherungen/`. Es passen nur
**PostgreSQL-Sicherungen**. Eine andere Datei (etwa eine alte MySQL-Sicherung) lehnt das
Skript ab, ohne etwas zu verändern.

```bash
# Linux:   sudo ./einrichten.sh --import /pfad/zur/sicherung.sql.gz
# Windows: Einrichten.cmd, Sicherung in den ERP-Ordner legen, dann dort:
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

### Die `.env` im Überblick

Jede Einstellung ist in `.env.example` erklärt. Wann eine Änderung wirkt, hängt davon ab, wer
sie liest:

| Einstellungen | Wirkt nach |
| --- | --- |
| `APP_PORT`, `APP_ADRESSE`, `LOKALER_PROXY`, `ZEITERFASSUNG_URL` (Adresse in den QR-Codes), `FREIGABE_PUBLIC_BASE_URL` (Link zur digitalen Annahme von Angeboten), `APP_ADMIN_USER`/`APP_ADMIN_PASS` | `docker compose up -d app` |
| `UPLOADS_ORDNER` | `docker compose up -d` (App und Updater) |
| `NETZSPERRE`, `NETZSPERRE_AUSNAHMEN` | `docker compose up -d netzsperre` |
| `UPDATE_ZEITPLAN`, `SICHERUNG_ZEITPLAN`, `SICHERUNG_PLATTE`, `SICHERUNG_CLOUD` | `docker compose up -d updater` |
| `KUNDE_NAME`, `SICHERUNG_TAGE`, `HEALTH_WARTEZEIT_SEKUNDEN`, `SICHERUNGEN_BEHALTEN`, `WEBHOOK_…` | dem nächsten Lauf von Sicherung bzw. Nachtupdate, ohne Neustart |
| `ERP_IMAGE` | siehe [Alltag](#alltag), Zeile „Bestimmte Version festhalten“ |
| `DB_USER`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | nur beim allerersten Start (Anlegen der Datenbank). **Später nicht ändern:** Die Datenbank behält Benutzer und Passwörter, ein geändertes `DB_USER` oder `DB_PASSWORD` sperrt das ERP aus. |

Werte ohne Anführungszeichen und ohne Leerzeichen um das `=` schreiben, Kommentare immer in
eine eigene Zeile.

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

Wird das ERP im Büronetz über eine eigene Domain aufgerufen (nicht IP, Rechnername, `*.fritz.box`
oder `*.ts.net`), gehört sie hier hinein: `APP_SECURITY_ALLOWEDHOSTS='erp.firma.de'`. Details:
[Erlaubte Adressen](../../docs/MOBILE_TOKEN_SECURITY.md#erlaubte-adressen-schutz-vor-dns-rebinding).

### Uploads in einem vorhandenen Ordner

Standardmäßig liegen Uploads im Docker-Volume `app_uploads`. Soll ein vorhandener Ordner
auf dem Rechner bleiben, etwa weil er im Firmennetz freigegeben ist (CAD-Zeichnungen),
steht er in der `.env` unter `UPLOADS_ORDNER`. Unter Windows schreibt man ihn so, wie ihn die
Docker-Desktop-VM sieht. Dann versteht ihn auch der Updater:

```ini
UPLOADS_ORDNER=/run/desktop/mnt/host/c/Kalkulationsprogramm/uploads
```

Das Nachtupdate sichert Uploads nie mit. Die [tägliche Sicherung](#tägliche-sicherung)
spiegelt sie – egal ob Volume oder eigener Ordner.

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
| Keine Linux-Berechtigungen | `cap_drop: [ALL]`, `no-new-privileges` | Kein Zugriff auf Kernel-Funktionen, kein Weg zurück zu root. Nur das Startskript bekommt kurz `CHOWN`, `SETUID`, `SETGID`, die mit dem Benutzerwechsel wegfallen. |
| Kein Docker-Socket für die App | `docker-compose.yml` | Den Socket haben nur der Updater und der Klick-Container `erp-jetzt-aktualisieren` (kein Netz, kein Port, nur `docker exec` auf den Updater). Der Updater hat keinen Port und führt nur `nachtupdate.sh` und `sicherung.sh` aus (der Klick-Container lässt ihn `nachtupdate.sh` über `update-jetzt.sh` aufrufen). Die App liest deren Ergebnis nur (Volume `betriebsstatus`, schreibgeschützt eingebunden). |
| Datenbank ohne Netz nach außen | Netz `intern` (`internal: true`) | PostgreSQL erreicht nur die App, keinen Port und kein Internet. |
| Netzsperre | Dienst `netzsperre` | Die App darf ins Internet (Mail, KI, Website), aber nicht ins Firmennetz: keine PCs, keine Buchhaltung, keine Dateifreigaben, auch nicht der Rechner selbst. Ausnahmen in `NETZSPERRE_AUSNAHMEN`, Standard `fritz.box:49000,fritz.box:1012` (Anrufliste und Anrufbeantworter, Anrufmonitor für das Anruf-Fenster). |
| Nur ein Port | `APP_ADRESSE`, `APP_PORT` | Der Browser-Zugang, sonst nichts. Hinter `tailscale serve` nur auf `127.0.0.1`. |

Die Netzsperre setzt Regeln in Dockers eigene Firewall-Kette `DOCKER-USER`, nur für die
Bridge `erp-extern`. Das geht auf Linux-Servern und in der Docker-Desktop-VM unter Windows
gleich. Docker löscht die Regeln bei jedem Neustart, deshalb läuft der Dienst dauerhaft, setzt
sie beim Start und prüft sie jede Minute (Namen in den Ausnahmen werden dabei neu aufgelöst,
Ausnahmen gelten nur für TCP). Gesperrt sind private Netze, Link-Local, der Adressbereich von
Tailscale und Multicast. Beim Stoppen entfernt er die Regeln. Prüfen:
`docker compose logs netzsperre` (dort steht „Sperre aktiv“ mit den erlaubten Zielen).
Ausschalten (nicht empfohlen): `NETZSPERRE=nein` in der `.env`, dann
`docker compose up -d netzsperre`.

> **Bestehende Installationen:** Nach dem Kopieren dieser `docker-compose.yml` einmal
> `docker compose up -d` ausführen. Damit entstehen die Netze `intern`/`extern` und alle
> Dienste werden neu angelegt. Das Nachtupdate tauscht nur die App und würde sie sonst in
> ein Netz ohne Datenbank stellen.

> **Anruf-Fenster bleibt aus?** Ältere `.env`-Dateien erlauben nur `fritz.box:49000`. Der
> Anrufmonitor der FRITZ!Box braucht zusätzlich Port 1012. In der `.env`
> `NETZSPERRE_AUSNAHMEN=fritz.box:49000,fritz.box:1012` eintragen (bzw. die eigene
> FRITZ!Box-Adresse aus den Telefon-Einstellungen), dann
> `docker compose up -d netzsperre`. In `docker compose logs netzsperre` muss danach
> `…:1012` bei den erlaubten Zielen stehen.

Am sichersten ist ein eigener kleiner Rechner nur für das ERP, in einem eigenen Netz (VLAN
oder Gastnetz). Dann bleibt auch beim schlimmsten Vorfall der Rest der Firma unberührt.

## Tägliche Sicherung

Jede Nacht um 2 Uhr (`SICHERUNG_ZEITPLAN`) sichert der Updater mit `sicherung.sh`:

| Was | Wohin |
| --- | --- |
| Komplette Datenbank (`pg_dump`, das ERP läuft dabei weiter) | `sicherungen/taeglich-<zeit>.sql.gz` auf diesem Rechner |
| dieselbe Datenbank-Sicherung | `<ziel>/db/kalkulationsprogramm_db_<zeit>.sql.gz` auf der externen Festplatte und im Cloud-Ordner |
| Hochgeladene Dateien | `<ziel>/uploads/` – nur Neues und Geändertes wird kopiert, **gelöscht wird dort nie etwas** (auch nicht, was im ERP gelöscht wurde) |

Läuft gerade ein Nachtupdate, wartet die Sicherung (höchstens zwei Stunden), denn es stoppt die
App und spielt bei einem Rollback die Datenbank zurück. Sind auf diesem Rechner weniger als
1 GB frei, bricht sie mit einer Meldung ab.

Sicherungen, die älter als `SICHERUNG_TAGE` (Standard 30) sind, werden gelöscht – hier und in
den Zielen. Im Hauptordner eines Ziels betrifft das auch alte `kalkulationsprogramm_db_*.sql.gz`
der früheren Windows-Sicherung. Eine Kopie auf demselben Rechner schützt nicht vor einem
Plattenausfall. Deshalb **mindestens ein Ziel eintragen**. Sonst meldet das ERP Admins jeden
Tag „Sicherung nur auf diesem Rechner“.

> **Datenschutz (DSGVO):** Die Sicherung enthält alle Kunden-, Mitarbeiter- und Lohndaten –
> unverschlüsselt. Ein Cloud-Ordner ist damit **Auftragsverarbeitung** (Art. 28): nur ein
> geschäftliches Konto mit Vertrag zur Auftragsverarbeitung nehmen (z. B. OneDrive for
> Business / Microsoft 365), kein privates Konto. Die externe Festplatte verschlüsseln
> (BitLocker To Go unter Windows, LUKS unter Linux), damit ein Diebstahl keine Datenpanne wird.
> Unter Linux sind die Dateien im Ziel nur für root lesbar – zum Zurückspielen an einem
> anderen Rechner also root bzw. `sudo` benutzen.

**Ziele eintragen** (`.env`, Windows-Laufwerke so geschrieben, wie Docker Desktop sie sieht):

```ini
SICHERUNG_PLATTE=/run/desktop/mnt/host/f/Kalkulationsprogramm-Backup
SICHERUNG_CLOUD=/run/desktop/mnt/host/c/Users/<name>/OneDrive/backup_handwerkerprogramm
```

Unter Linux einfach den Pfad, z. B. `/media/sicherung/erp` oder ein eingebundenes Netzlaufwerk.

Danach – **mit angeschlossener Festplatte**:

```bash
docker compose up -d updater
docker compose exec updater bash /erp/sicherung.sh --ziele-einrichten
docker compose exec updater bash /erp/sicherung.sh
```

`--ziele-einrichten` legt in jedes Ziel die Datei `ERP-SICHERUNGSZIEL.txt`. Die Sicherung
schreibt nur in Ordner, in denen diese Datei liegt. Grund: Fehlt die Festplatte beim Start
von Docker, bindet Docker an ihrer Stelle einen leeren Ordner ein, und die Sicherung landete
unbemerkt im Nirgendwo. Stattdessen kommt die Meldung „Externe Festplatte nicht gefunden“.
Nach dem Anschließen den Rechner neu starten (oder `docker compose up -d --force-recreate updater`).
Unter Windows erledigt das der [Docker-Wächter](#docker-wächter-windows) von selbst in der
Stunde vor der nächsten Sicherung.

**Abgerissene Verbindung (Windows):** Trennt Windows die Platte kurz, etwa beim
Energiesparen, ist sie unter Windows gleich wieder da. Docker Desktop hält aber an der alten
Verbindung fest: Im Updater endet jeder Zugriff mit „No such device“, und die Sicherung meldet
„Die Verbindung ist abgerissen“. Ein Neustart nur des Updaters hilft dann **nicht**, er
scheitert („mkdir /run/desktop/mnt/host/f: file exists“) und lässt den Updater gestoppt
zurück. Erst `docker desktop restart` bindet die Platte neu ein. Das macht der Docker-Wächter
selbst. Vorbeugen: Energiesparen für die Platte abschalten (EINRICHTEN.md, Schritt 6.1).

Eingebunden werden die Ziele beim **Erstellen** des Updaters. Wer `SICHERUNG_PLATTE` oder
`SICHERUNG_CLOUD` ändert und `docker compose up -d updater` vergisst, bekommt deshalb die
Meldung „… ist eingetragen, aber noch nicht eingebunden“ – gesichert wird dann nicht dorthin.

**Zurückspielen:** Die gewünschte `.sql.gz` in den ERP-Ordner legen und
`docker compose exec updater bash /erp/nachtupdate.sh --importieren <datei>` (siehe
[Umzug](#umzug-von-einem-bestehenden-server)). Eine Sicherung aus `sicherungen/` geht direkt:
`--importieren sicherungen/taeglich-<zeit>.sql.gz`. Hochgeladene Dateien einfach aus
`<ziel>/uploads/` zurückkopieren.

## Meldungen im ERP

Sicherung und Nachtupdate schreiben nach jedem Lauf ihr Ergebnis nach `sicherung.json` bzw.
`nachtupdate.json` im Docker-Volume `betriebsstatus` (ohne Kundendaten; Fehlerzeilen aus dem
App-Log gehen nur aufs Handy, nie hierher). Ansehen: `docker compose exec updater cat
/betriebsstatus/sicherung.json`. Das ERP liest das Volume nur und zeigt Admins unter
**Benachrichtigungen** (Glocke oben rechts), Thema „Sicherung & Updates“:

- Sicherung fehlgeschlagen oder nur teilweise (z. B. Festplatte fehlt, Cloud-Ordner voll),
- **seit über 26 Stunden keine Sicherung** – fängt auch einen Rechner ab, der nachts aus
  war, oder einen gestoppten Updater,
- Update fehlgeschlagen (alte Version läuft wieder) und NOTFALL.

Klappt der nächste Lauf, verschwindet die Meldung von selbst. Weggeklickt kommt sie nach einer
Woche wieder, wenn das Problem noch besteht. Handy-Nachrichten (unten) gibt es zusätzlich.

## Was nachts passiert

| Schritt | Was | Wenn's schiefgeht |
| --- | --- | --- |
| 1 | `docker pull` von `ERP_IMAGE` (`:stable`). Gleiches Image wie aktiv → fertig. | Nachricht „Download fehlgeschlagen“, nichts verändert |
| 2 | Läuft die aktuelle Version gesund? (Health-Check, max. 2 min) | Nachricht „Update ausgelassen“, denn ohne gesunde Ausgangslage gibt es keinen sicheren Rückweg |
| 3 | Platz prüfen, **App stoppen**, dann komplette Sicherung (`pg_dump`) nach `sicherungen/` | Alte Version startet wieder, Nachricht |
| 4 | Neue Version starten, Flyway spielt offene Migrationen selbst ein | |
| 5 | Jede Sekunde `/actuator/health` abfragen (Standard: max. 300 s) | Ein Absturz wird sofort erkannt |
| 6 | **Rollback:** neue Version stoppen, Datenbank aus der Sicherung zurück, alte Version starten | Handy-Nachricht. Scheitert auch das: NOTFALL-Nachricht |

PostgreSQL leert und befüllt die Datenbank beim Rollback in **einer** Transaktion:
Scheitert das Einspielen, bleibt sie, wie sie war.

- **App vor der Sicherung stoppen:** Sonst könnte zwischen Sicherung und Update noch
  jemand buchen, und der Rollback würde diese Buchung verschlucken.
- **Gescheiterte Versionen** werden nicht jede Nacht neu versucht, sondern erst, wenn
  eine neuere kommt.
- **KI-Hilfe:** Ihr Wissen über das Programm (die RAG-Datei aus Doku und Quellcode) steckt
  im Image unter `/app/ki-hilfe/`. Mit jeder neuen Version kommt das passende Wissen mit,
  beim Rollback das alte. Log und Erfolgsmeldung nennen, wie viele Abschnitte die KI-Hilfe
  jetzt kennt. Beim Kunden ist dafür nichts einzurichten; die Fragen stellt das ERP mit dem
  Gemini-Schlüssel aus *System-Einstellungen → KI-Funktionen*. Abschalten:
  `AI_RAG_ENABLED='false'` in der `app.env`. Details: [RAG_INDEXING.md](../../RAG_INDEXING.md).
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

## Update per Klick (Docker Desktop)

Wer nicht bis 3 Uhr warten will (oder der PC nachts aus ist), aktualisiert das ERP
**ohne Befehl**: In Docker Desktop unter *Containers* in der Gruppe `erp-handwerk` den
Container **`erp-jetzt-aktualisieren`** suchen und auf **Start** (▶) klicken. Im Reiter
*Logs* läuft das Update mit: neue Version holen, Datenbank sichern, wechseln, prüfen. Am
Ende steht dort, ob das ERP aktualisiert wurde, schon aktuell war oder die alte Version
weiterläuft. Danach beendet sich der Container (grau, „Exited“). Beim nächsten Mal wieder
auf Start klicken.

- **Derselbe Ablauf wie nachts**, mit Sicherung und automatischem Rollback. Der Container
  ruft nur `updater/update-jetzt.sh` im Updater auf, der wiederum `nachtupdate.sh`. Es gibt
  keine zweite Update-Logik.
- **Ist eine neue Version da,** ist das ERP während des Updates einige Minuten nicht
  erreichbar. Am besten also nicht mitten im Arbeitstag klicken.
- **Nur diesen einen Container starten,** nicht die ganze Gruppe `erp-handwerk` (Start oder
  Neustart der Gruppe in Docker Desktop kann auch ein Update auslösen, mitten am Tag).
- **Fenster schließen oder Stop klicken ist harmlos:** Das Update läuft im Updater
  abgekoppelt zu Ende. Nach einem Stop zeigt der Container allerdings nicht das Ergebnis
  (sein Code stammt vom abgebrochenen Mitlesen): Das steht im ERP unter Benachrichtigungen
  und in `logs/nachtupdate-*.log`. Ein zweiter Klick währenddessen wartet auf den ersten
  Lauf und meldet danach „schon aktuell“ – oder, war der erste gescheitert, dass diese
  Version schon einmal gescheitert ist. Eine gescheiterte Version spielt der Klick nicht
  erneut ein; das geht nur per Befehl mit `--erzwingen` (siehe [Alltag](#alltag)).
- **Rot („Exited (1)“):** nicht eingespielt, die alte Version läuft. **Exited (2)** ist ein
  NOTFALL (siehe [Was nachts passiert](#was-nachts-passiert)), **Exited (3)**: nichts
  verändert, eine Voraussetzung fehlt. Jeder andere Code: Ausgang unklar, im ERP und in
  den Logs nachsehen. Das Ergebnis steht auch im ERP unter Benachrichtigungen und in
  `logs/nachtupdate-*.log`. Fehlt der Updater ganz (z. B. nach `docker compose down`),
  startet der Klick ihn; geht auch das nicht, erst `docker compose up -d`.
- **Er taucht von selbst auf:** Den Container legt der Updater bei jedem Start an
  (`updater/start.sh`), gestartet wird er nie von allein (Profil `manuell`). Fehlt er auf
  einer bestehenden Installation nach dem Kopieren dieser Dateien, einmal
  `docker compose restart updater` ausführen.
- Auf der Kommandozeile geht dasselbe mit `docker start -a erp-jetzt-aktualisieren`.

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

## Docker-Wächter (Windows)

**Das Problem:** Windows Update tauscht ab und zu WSL aus, den Linux-Unterbau von Docker
Desktop, und zwar im laufenden Betrieb und auch tagsüber (die Nutzungszeit hilft dagegen
nicht, sie verschiebt nur Neustarts des ganzen PCs). Danach starten die Container zwar von
selbst wieder, aber Docker Desktop leitet den Port nicht mehr weiter. Das ERP läuft dann im
Container, im Browser kommt aber nur „Seite kann nicht geöffnet werden“. Manchmal ist
Docker Desktop auch ganz weg. So geschehen am 13.10.2026 mit dem Update auf WSL 3.0.3.

**Die Abhilfe:** `docker-waechter.ps1` läuft als geplante Aufgabe „ERP Handwerk
Docker-Waechter“ alle 5 Minuten im angemeldeten Benutzer, unsichtbar im Hintergrund:

1. Antwortet `/actuator/health` von Windows aus? Dann ist nichts zu tun. Das gilt auch für
   eine Fehlerseite (z. B. 503): Dann funktioniert die Weiterleitung, und das Problem liegt
   in der App selbst. Ein Neustart von Docker würde da nichts helfen.
2. Kommt keine Verbindung zustande, prüft er noch dreimal im Abstand von 30 Sekunden. Kurze
   Aussetzer lösen also nichts aus.
3. Er lässt die Finger davon, wenn der PC oder Docker Desktop erst vor weniger als
   10 Minuten gestartet ist. Nach dem Anmelden braucht Docker Desktop ein paar Minuten.
4. Er greift **nur** ein, wenn Docker gar nicht antwortet oder der Container `erp-app`
   sich selbst als gesund meldet und trotzdem nicht erreichbar ist. Startet das ERP gerade
   (Nachtupdate, Neustart) oder wurde es bewusst gestoppt, lässt er es in Ruhe.
5. Dann startet er Docker Desktop neu und wartet bis zu 8 Minuten, bis das ERP wieder
   antwortet. Das ERP ist so meist innerhalb von 10 Minuten wieder da.
6. Er startet höchstens einmal in 30 Minuten neu. Hilft das zweimal hintereinander nicht
   (z. B. weil Docker Desktop eine Lizenzfrage zeigt oder WSL kaputt ist), versucht er es
   nur noch alle 4 Stunden. Sonst würde er Docker Desktop den ganzen Tag lang immer wieder
   neu starten.
7. Mit `WEBHOOK_URL` kommt eine Handy-Nachricht: leise, wenn der Neustart geholfen hat,
   dringend beim **ersten** erfolglosen Neustart (danach keine weiteren, bis es wieder
   geht).
8. **Sicherungsziele:** Antwortet das ERP gesund, prüft er auch die Ziele aus
   `SICHERUNG_PLATTE` und `SICHERUNG_CLOUD` (nur Windows-Pfade `/run/desktop/mnt/host/…`).
   Liegt die Datei `ERP-SICHERUNGSZIEL.txt` unter Windows im Ziel, der Updater sieht sie aber
   nicht (abgerissene Verbindung, siehe [Tägliche Sicherung](#tägliche-sicherung), oder
   Platte erst nach dem Start von Docker angesteckt), startet er Docker Desktop neu. Und zwar
   nur 15 bis 60 Minuten vor der Sicherung (`SICHERUNG_ZEITPLAN` der Form `Minute Stunde * * *`),
   nicht während eines Nachtupdates und höchstens einmal in 12 Stunden. Danach kommt eine
   leise Handy-Nachricht, hilft der Neustart nicht, eine dringende. Fehlt die Platte auch
   unter Windows, oder ist ein Ziel neu eingetragen und noch nicht eingebunden, greift er
   nicht ein, das meldet die Sicherung selbst.

Bei jedem Lauf frischt er `logs/docker-waechter-lebt.txt` auf. Daran erkennt
`sicherung.sh`, dass der Wächter läuft, und schreibt in die Meldung „wird von selbst wieder
eingebunden“ statt „Rechner neu starten“.

Was er tut, steht in `logs/docker-waechter.log`. Zum Ausprobieren ohne Neustart und ohne
Handy-Nachricht: `powershell -ExecutionPolicy Bypass -File docker-waechter.ps1 -Probelauf`. Einen Neustart von Hand löst
`docker desktop restart` aus. Sind `APP_PORT` oder `APP_ADRESSE` in der `.env` ungültig,
pausiert der Wächter und schreibt das ins Log. Im Zweifel greift er also nicht ein.

**Einrichten** macht `Einrichten.cmd` mit. Auf einer bestehenden Installation die Datei
`docker-waechter.ps1` in den ERP-Ordner kopieren und einmal ausführen (kein Administrator
nötig, unter dem Benutzer, unter dem Docker Desktop läuft):

```powershell
powershell -ExecutionPolicy Bypass -File C:\ERP-Handwerk\docker-waechter.ps1 -Einrichten
```

**Abschalten**, z. B. auf einem PC, auf dem Docker Desktop auch mal bewusst beendet wird:
*Aufgabenplanung → „ERP Handwerk Docker-Waechter“ → Deaktivieren*. Sonst startet der
Wächter Docker Desktop nach etwa 10 Minuten wieder.

## Alltag

| Aufgabe | Befehl (im ERP-Ordner) |
| --- | --- |
| Alles starten | `docker compose up -d` |
| Update sofort einspielen | **ohne Befehl:** in Docker Desktop beim Container `erp-jetzt-aktualisieren` auf Start klicken ([Update per Klick](#update-per-klick-docker-desktop)) |
| Update sofort testen | `docker compose exec updater bash /erp/nachtupdate.sh` |
| Gescheiterte Version trotzdem einspielen | `docker compose exec updater bash /erp/nachtupdate.sh --erzwingen` |
| Bestimmte Version festhalten | in `.env`: `ERP_IMAGE=ghcr.io/…:sha-abc1234`, dann **`docker compose exec updater bash /erp/nachtupdate.sh --erzwingen`** (nicht `compose up`, das würde ohne Sicherung wechseln) |
| Andere Update-Zeit | in `.env`: `UPDATE_ZEITPLAN=30 3 * * *`, dann `docker compose up -d updater` |
| Sicherung sofort ausführen | `docker compose exec updater bash /erp/sicherung.sh` |
| Andere Sicherungszeit | in `.env`: `SICHERUNG_ZEITPLAN=30 1 * * *`, dann `docker compose up -d updater` |
| Logs von Nachtupdate und Sicherung | Ordner `logs/` (`nachtupdate-*.log`, `sicherung-*.log`, nach einem Rollback `gescheitert-*-app.log`) |
| App-Log live | `docker compose logs -f app` |
| ERP im Browser weg, Container laufen aber (Windows) | `docker desktop restart` – macht der [Docker-Wächter](#docker-wächter-windows) meist innerhalb von 10 Minuten selbst; sein Log: `logs/docker-waechter.log` |
| Updater selbst erneuern (selten) | `docker compose build --pull updater && docker compose up -d updater` |

`sicherungen/` hält außer den täglichen Sicherungen die letzten 14 Sicherungen von vor den
Updates (`SICHERUNGEN_BEHALTEN`). Die sind nur für den Rollback da und kein Ersatz für die
[tägliche Sicherung](#tägliche-sicherung) außer Haus. Sicherungen von vor einem Import
(`vor-import-…`) bleiben liegen, bis man sie selbst löscht. Logs, die älter als 90 Tage sind,
räumt das Nachtupdate weg.

**Die Dateien in diesem Ordner** (Skripte, `docker-compose.yml`) aktualisiert das
Nachtupdate nicht, sondern nur das ERP selbst. Änderungen daran müssen einmal von Hand
auf die Rechner der Kunden kopiert werden. `.env`, `app.env`, `sicherungen/`, `logs/` und
`.status/` dabei nicht überschreiben. Danach
`docker compose build updater netzsperre && docker compose up -d` – das baut den Updater neu,
wenn sich `updater/Dockerfile` geändert hat, und die Netzsperre, deren Skript `sperre.sh` im
Image steckt. Außerdem bindet es geänderte Ordner ein. Danach den Updater einmal neu starten
(`docker compose restart updater`), damit er den Container
[`erp-jetzt-aktualisieren`](#update-per-klick-docker-desktop) anlegt bzw. erneuert.

## Offene Punkte

- **PostgreSQL 16:** Ein späterer Wechsel der Hauptversion (z. B. auf 17) geht nicht
  durch einfaches Austauschen des Images, sondern braucht Sicherung und Wiedereinspielen
  (oder `pg_upgrade`). Das muss einmal geplant werden, wenn es so weit ist.
- **Zeitzone:** Der PostgreSQL-Container läuft auf deutscher Zeit (`TZ=Europe/Berlin`).
  Die App setzt Zeitstempel selbst. Nur wo die Datenbank selbst „jetzt“ einträgt
  (Spalten-Standardwert `CURRENT_TIMESTAMP`), steht die Ortszeit des Containers.
- **Suchen und Sortieren auf PostgreSQL:** Groß-/Kleinschreibung spielt in Suchen keine
  Rolle, leere Werte stehen beim Sortieren aufsteigend oben und absteigend unten. Die
  Tests `DatenbankKompatibilitaet*Test` prüfen Suchen, Sortierung und das Speichern jeder
  Entität gegen ein echtes PostgreSQL (CI-Job „Datenbank-Kompatibilität“). Bekannte
  Eigenheiten: Umlaute werden nicht akzentfrei verglichen (die Suche nach „wurth“ findet
  „Würth“ nicht), Leerzeichen am Ende zählen bei exakten Vergleichen mit, und bei zu
  langen Texten nennt die Fehlermeldung die Höchstlänge, aber nicht das Feld.

## Neuinstallation: das Basis-Schema

Die PostgreSQL-Migrationen beginnen erst nach V406 – alles davor steckt im Basis-Schema.
Deshalb spielt die App auf einer **leeren** Datenbank zuerst
`src/main/resources/db/postgresql/basis/V1__basis_schema.sql` ein: Schema und Stammdaten
(`FlywayStartSetupConfig`). Danach laufen die Migrationen aus `db/postgresql/migration`
(Flyway setzt dafür eine Baseline). Eine mit `postgres_erzeugen.sh` neu erzeugte Basis
bringt zusätzlich die Flyway-Historie der enthaltenen Migrationen mit. Bestehende
Datenbanken sind davon nicht betroffen.

Erzeugt wird die Basis mit `scripts/basis-schema/postgres_erzeugen.sh`. Neu erzeugen muss
man sie nur selten, denn neue Migrationen laufen auf der Basis ganz normal. Jede neue
Migration ist **eine** Datei in `db/postgresql/migration/` (Anleitung:
`db/postgresql/migration/README.md`). Die Ordnernamen bleiben so, damit die Flyway-Historie
der laufenden Installationen gültig bleibt.

## Der Starttest in GitHub

`.github/workflows/docker-image.yml` läuft nach jedem Push auf `main` (also nach jedem
Merge eines PR – getestet wurde vorher im PR, siehe `pr-ci.yml`). Zuerst prüft es, dass
genau dieser Stand aus einem gemergten PR mit grünem „PR Quality Gate“ stammt; sonst gibt es
kein Image. Es veröffentlicht nur, wenn `starttest.sh` grün ist. Dabei richtet das aktuelle `:stable`
(also der Kundenstand) eine leere Datenbank ein, darauf startet die neue Version, und
`/actuator/health` muss 200 liefern. Neue Migrationen laufen dabei einmal echt durch,
auch solche mit kleinerer Nummer (out-of-order). Die Testdatenbank ist ein frisches
PostgreSQL 16, eingerichtet wie beim Kunden (App-Rolle ohne Superuser-Rechte), und die App
läuft so abgeschottet wie dort (schreibgeschützt, ohne Linux-Berechtigungen).

Vor dem Starttest bekommt das Image die RAG-Datei der KI-Hilfe: Vorlage ist die Datei aus
dem aktuellen `:stable`, eingebettet wird nur, was sich seitdem an Doku und Quellcode geändert
hat (Secret `GEMINI_API_KEY`, Zeitlimit 12 Minuten). Scheitert das, bekommt das Image den
bisherigen Stand – ein Release hängt nie an der KI-Hilfe. Details: [RAG_INDEXING.md](../../RAG_INDEXING.md).

Veröffentlicht wird immer `:sha-<commit>` (7 Zeichen) und `:stable` nur, wenn der Commit noch
der Stand von `main` ist. So rollt ein erneut gestarteter alter Lauf keine ältere Version aus.
Lokal geht es genauso:

```bash
deployment/docker-server/starttest.sh <neues-image> [<image-der-kunden>]
```

Ohne zweites Image prüft der Test nur eine Neuinstallation mit dem neuen Image.

Grenze: Die Testdatenbank enthält nur Stammdaten. Migrationen, die nur an echten Daten
scheitern (z. B. Dubletten bei einem neuen UNIQUE), fängt erst das Nachtupdate ab,
dann mit Rollback.

## Dateien

| Datei | Zweck |
| --- | --- |
| `EINRICHTEN.md` | Schritt-für-Schritt-Anleitung für einen neuen Kunden (zum Abhaken) |
| `docker-compose.yml` | postgres, app, updater, netzsperre, alle mit `restart: always`; dazu der Klick-Container `jetzt-aktualisieren` (Profil `manuell`, startet nur auf Klick) |
| `postgres-init/` | Legt beim ersten Start die App-Rolle in PostgreSQL an (ohne Superuser-Rechte) |
| `.env.example` | Vorlage für `.env`. Die `.env` wird beim Einrichten erzeugt und nie committet |
| `app.env` | Optional, weitere ERP-Einstellungen (API-Schlüssel …). Nie committet |
| `netzsperre/` | Image der Netzsperre (Firewall-Regeln: ERP nicht ins Firmennetz) |
| `Einrichten.cmd`, `einrichten-windows.ps1` | Einrichtung auf einem Windows-PC |
| `docker-waechter.ps1` | Windows: startet Docker Desktop neu, wenn das ERP hängt (geplante Aufgabe alle 5 Minuten) |
| `einrichten.sh` | Einrichtung auf einem Linux-Server, optional mit `--import` |
| `nachtupdate.sh` | Nachtupdate, Rollback, Wiederaufnahme nach Neustart, Import |
| `sicherung.sh` | Tägliche Sicherung auf diesen Rechner, externe Festplatte und Cloud-Ordner |
| `gemeinsam.sh` | Bausteine beider Skripte: `.env` lesen, Datenbank sichern, Handy-Nachricht, Ergebnis fürs ERP |
| `updater/` | Image des Updater-Containers und sein Startskript (Zeitplan per crond); `update-jetzt.sh` ist der Ablauf hinter dem Knopf „Jetzt aktualisieren“ |
| Volume `betriebsstatus` | Ergebnis der letzten Sicherung bzw. des letzten Updates – liest das ERP für die Benachrichtigungen |
| `starttest.sh` | Starttest für die GitHub Action |
