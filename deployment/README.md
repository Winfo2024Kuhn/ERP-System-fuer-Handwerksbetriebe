# Deployment - Handwerkerprogramm

Dieses Verzeichnis enthält alles, was das ERP beim Kunden zum Laufen bringt. Diese Seite ist
nur der Wegweiser – die Einzelheiten stehen in den verlinkten Anleitungen.

## Docker beim Kunden (Windows-PC oder Linux-Server) – empfohlen

Ein Doppelklick (Windows) bzw. ein Befehl (Linux) richtet alles ein: Datenbank
(PostgreSQL 16), ERP, Updater und Netzsperre. Das ERP startet nach jedem Neustart von selbst.
Jede Nacht um 2 Uhr sichert der Updater Datenbank und hochgeladene Dateien (auf den Rechner,
eine externe Festplatte und einen Cloud-Ordner). Um 3 Uhr holt er neue Versionen, sichert
vorher die Datenbank und rollt bei Problemen automatisch zurück. Was schiefgeht, sehen Admins
im ERP unter **Benachrichtigungen**, auf Wunsch zusätzlich per Handy-Nachricht.

- **[docker-server/README.md](./docker-server/README.md)** – Aufbau, alle Einstellungen,
  Sicherung, Nachtupdate, Rollback, Sicherheit
- **[docker-server/EINRICHTEN.md](./docker-server/EINRICHTEN.md)** – Schritt-für-Schritt-Anleitung
  zum Abhaken für einen neuen Kunden

Das Docker-Image baut GitHub nach jedem Merge auf `main` und gibt es erst nach einem
Starttest als `:stable` frei (Details: [docker-server/README.md, Abschnitt „Der Starttest in
GitHub“](./docker-server/README.md#der-starttest-in-github)).

## Handys von unterwegs

- **Mit `deployment/docker-server`:** über Tailscale – entweder die Tailscale-App auf jedem
  Handy oder Tailscale Funnel ohne App. Siehe
  [docker-server/README.md, Abschnitt „Hinter `tailscale serve`“](./docker-server/README.md#hinter-tailscale-serve-nur-über-tailscale-erreichbar).
- **[mobile-public/](./mobile-public/README.md):** öffentlicher HTTPS-Zugang nur für die
  Zeiterfassung (Testadresse über Cloudflare oder eigene Domain mit Caddy). Baut auf der
  `docker-compose.yml` im Repository-Stamm auf, nicht auf `deployment/docker-server`.

## Dateien direkt aus dem ERP öffnen (Windows-Arbeitsplätze)

**[openfile-launcher/](./openfile-launcher/README.md)** – registriert `openfile://` auf den
Büro-PCs, damit CAD-Zeichnungen und Excel-Dateien aus der Netzwerkfreigabe direkt im
passenden Programm aufgehen.

## Altweg: Start als JAR unter Windows

Bevor es `docker-server/` gab, lief das ERP als JAR auf einem Windows-Rechner, mit
Windows-Aufgaben für Start und Sicherung. Die Skripte liegen weiter in `scripts/` und wurden
zuletzt am 11.10.2026 auf PostgreSQL umgestellt. **Für Neuinstallationen ist
`docker-server/` der Weg.**

- **[DEPLOYMENT_README.md](./DEPLOYMENT_README.md)** – Installation, geplante Aufgaben,
  Sicherung, Fehlersuche
- **[scripts/README.md](./scripts/README.md)** – die einzelnen Skripte, vor allem das
  Update-Skript `update-production.ps1`

Auf einem Rechner mit `docker-server/` braucht es diese Skripte nicht: Sicherung und Update
erledigt dort der Updater. Eine noch eingerichtete Windows-Aufgabe
„Kalkulationsprogramm - Database Backup“ sichert dann dieselbe Datenbank ein zweites Mal.
