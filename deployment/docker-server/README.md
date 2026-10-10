# Firmenserver mit Docker: automatische Updates um 3 Uhr nachts

Der Server beim Kunden holt sich neue Versionen **selbst** ab (Pull-Prinzip).
GitHub braucht keinen Zugang ins Firmennetz – keine offenen Ports, kein VPN in
der Cloud.

```
 git push main ──► GitHub Actions                     Firmenserver (hinter Router/VPN)
                   1. Tests (PR Quality Checks)        Cronjob 03:00 ─► nachtupdate.sh
                   2. Docker-Image bauen                 1. neues Image holen ◄──────┐
                   3. Starttest: Upgrade vom Kunden-     2. App stoppen, DB sichern  │
                      stand + neue Migrationen           3. neue Version starten     │
                   4. nur wenn grün: ghcr.io ──────────► 4. /actuator/health = 200?  │
                      :stable  + :sha-<commit>           5. nein → Rollback + Handy  │
                                                         ──────────────────────────┘
```

## Was nachts passiert

| Schritt | Was | Wenn's schiefgeht |
| --- | --- | --- |
| 1 | `docker pull` von `ERP_IMAGE` (`:stable`). Gleiches Image wie aktiv → fertig. | Nachricht „Download fehlgeschlagen“, nichts verändert |
| 2 | Läuft die aktuelle Version gesund? (Health-Check, max. 2 min) | Nachricht „Update ausgelassen“ – ohne gesunde Ausgangslage kein sicherer Rückweg |
| 3 | Platz prüfen, **App stoppen**, dann kompletter `mysqldump` nach `sicherungen/` | Alte Version startet wieder, Nachricht |
| 4 | Neue Version starten – Flyway spielt offene Migrationen selbst ein | |
| 5 | Jede Sekunde `/actuator/health` abfragen (Standard: max. 300 s) | Absturz wird sofort erkannt, nicht erst nach Ablauf der Zeit |
| 6 | **Rollback:** neue Version stoppen, Datenbank aus der Sicherung zurück, alte Version starten | Handy-Nachricht mit Fehlerauszug; scheitert auch das: NOTFALL-Nachricht |

Warum die App **vor** der Sicherung gestoppt wird: Sonst könnte zwischen
Sicherung und Update noch jemand buchen – und der Rollback würde das verschlucken.
Kostet nachts ein, zwei Minuten Ausfall, die niemand merkt.

Wird das Skript mittendrin abgebrochen (Server fährt herunter, `kill`), bringt
es die alte Version wieder hoch bzw. rollt zurück und meldet sich. Sonst bliebe
das ERP nach `docker compose stop` auch nach einem Neustart aus.

Eine Version, die einmal gescheitert ist, wird **nicht jede Nacht neu**
versucht (sonst jede Nacht Rollback + Nachricht), sondern erst wieder, wenn
eine neuere kommt – oder von Hand mit `sudo ./nachtupdate.sh --erzwingen`.

## Einrichtung (einmal pro Kunde)

Voraussetzungen: Linux-Server mit Docker + Compose-Plugin, `curl`, `flock`
(util-linux), `gzip`. Zeitzone des Servers auf `Europe/Berlin` stellen
(`timedatectl set-timezone Europe/Berlin`), sonst läuft „3 Uhr“ in UTC.

```bash
# 1. Diesen Ordner auf den Server kopieren, z.B. nach /opt/erp
sudo mkdir -p /opt/erp && sudo cp -r deployment/docker-server/. /opt/erp/ && cd /opt/erp

# 2. Anmeldung an der GitHub Container Registry (nur nötig, wenn das Image privat ist)
#    Pro Kunde ein eigenes Token, NUR mit dem Recht "read:packages".
echo <TOKEN> | sudo docker login ghcr.io -u <github-benutzer> --password-stdin

# 3. Einstellungen anlegen und ausfüllen (Passwörter, KUNDE_NAME, WEBHOOK_URL)
sudo ./einrichten.sh            # legt beim ersten Mal .env an und stoppt
sudo nano .env

# 4. Bestehende Datenbank übernehmen und alles starten + Cronjob eintragen
sudo ./einrichten.sh --import /pfad/zur/sicherung.sql.gz
```

> **Wichtig – leere Datenbank geht (noch) nicht:** Die Flyway-Migrationen
> beginnen erst bei V208, das Grundschema davor hat früher Hibernate angelegt.
> Eine leere MySQL kann das ERP deshalb nicht selbst aufbauen. Für den Umzug
> vom bisherigen Server dessen Sicherung mit `--import` einspielen. Die
> tägliche Sicherung des Windows-Servers (`backup-database.ps1` →
> `kalkulationsprogramm_db_<datum>.sql.gz`) passt direkt.

## Handy-Nachrichten

Empfohlen: [ntfy](https://ntfy.sh) – kostenlose App für Android und iOS.

```ini
WEBHOOK_URL=https://ntfy.sh/<langer-zufaelliger-name>
WEBHOOK_TOKEN=
WEBHOOK_BEI_ERFOLG=nein
WEBHOOK_MIT_FEHLERAUSZUG=nein
```

(Kommentare in der `.env` immer in eine eigene Zeile, nie hinter den Wert.)

- Der Themenname bei ntfy.sh ist wie ein Passwort: Wer ihn kennt, liest mit.
  Besser ist ein eigener ntfy-Server oder ein ntfy-Konto mit `WEBHOOK_TOKEN`.
- `WEBHOOK_BEI_ERFOLG=ja` schickt auch bei Erfolg eine leise Nachricht.
- Slack/Mattermost: `WEBHOOK_FORMAT=json` (sendet `{"text": "…"}`).

Die Nachricht enthält Kunde, Versionen und den Grund. Mit
`WEBHOOK_MIT_FEHLERAUSZUG=ja` kommen die wichtigsten Fehlerzeilen dazu.
Vorher wird geschwärzt: Werte in Anführungszeichen (z. B.
`Duplicate entry '…'`), Inhalte in `{…}`, E-Mail- und IP-Adressen. Ganz
ausschließen lässt sich Personenbezug in Fehlermeldungen trotzdem nicht. Bei
einem fremden Dienst wie ntfy.sh ist das **Auftragsverarbeitung (DSGVO)**.
Deshalb ist der Auszug standardmäßig aus. Das volle Log bleibt auf dem Server
unter `logs/`.

## Alltag

| Aufgabe | Befehl (in `/opt/erp`) |
| --- | --- |
| Update sofort testen | `sudo ./nachtupdate.sh` |
| Gescheiterte Version trotzdem einspielen | `sudo ./nachtupdate.sh --erzwingen` |
| Bestimmte Version festhalten | in `.env`: `ERP_IMAGE=ghcr.io/…:sha-abc1234` |
| Logs des letzten Laufs | `ls -t logs/ \| head` |
| App-Log live | `sudo docker compose logs -f app` |
| Datenbank-Konsole | `sudo docker compose exec mysql mysql -uroot -p` |

`sicherungen/` hält die letzten 14 Sicherungen von vor den Updates
(`SICHERUNGEN_BEHALTEN`). Sie sind **kein Ersatz** für die tägliche Sicherung
außer Haus – die Uploads (`app_uploads`-Volume) sichern sie zum Beispiel nicht.

## Der Starttest in GitHub

`.github/workflows/docker-image.yml` läuft nach jedem grünen Push auf `main`
und veröffentlicht nur, wenn `starttest.sh` grün ist: Er baut mit dem aktuellen
`:stable` (= was die Kunden gerade haben) eine Datenbank, startet darauf die
neue Version und erwartet `/actuator/health = 200`. Neue Migrationen laufen
dabei also einmal durch. Lokal genauso:

```bash
deployment/docker-server/starttest.sh <neues-image> <image-der-kunden>
```

Grenzen: Das Ausgangsschema kommt aus Hibernate, nicht aus einer echten
Kundendatenbank, und enthält keine Daten. Und weil Flyway mit
`out-of-order=true` läuft, verdeckt die Baseline neue Migrationen mit einer
Nummer **unter** der höchsten bisherigen. Der Starttest meldet sie dann als
Warnung. Migrationen, die nur an echten
Daten scheitern (z. B. doppelte Werte bei einem neuen UNIQUE), fängt erst das
Nachtupdate ab – mit Rollback.

## Dateien

| Datei | Zweck |
| --- | --- |
| `docker-compose.yml` | MySQL + App. App läuft immer als lokales Image `erp-app:aktiv`, das nur `nachtupdate.sh` umhängt |
| `.env.example` | Vorlage für `.env` (Passwörter, Webhook, Kunde) – `.env` nie committen |
| `einrichten.sh` | Ersteinrichtung inkl. Datenbank-Import und Cronjob `/etc/cron.d/erp-nachtupdate` |
| `nachtupdate.sh` | Das Nachtupdate mit Sicherung, Health-Check und Rollback |
| `starttest.sh` | Starttest für die GitHub Action |
