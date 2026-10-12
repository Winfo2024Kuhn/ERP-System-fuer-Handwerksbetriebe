# Sicherheitsrichtlinie

## Unterstützte Versionen

Dieses Projekt befindet sich in aktiver Entwicklung. Sicherheitsupdates werden für die aktuelle Version bereitgestellt. Installationen mit Docker ([`deployment/docker-server`](deployment/docker-server)) holen sich die aktuelle Version (`:stable`) automatisch über das Nachtupdate.

## Sicherheitslücken melden

Bitte melde Sicherheitslücken **nicht** als öffentliches GitHub Issue.

Sende stattdessen eine Beschreibung der Schwachstelle direkt über GitHub an den Repository-Inhaber ([@Winfo2024Kuhn](https://github.com/Winfo2024Kuhn)).

Bitte beschreibe:
- Art der Schwachstelle
- Betroffene Komponente (Backend / Frontend / Deployment)
- Schritte zur Reproduktion
- Mögliche Auswirkungen

## Sicherheitsmaßnahmen im Projekt

Dieses Projekt setzt folgende Sicherheitsstandards um:

- **OWASP Top 10** – siehe [DEVELOPMENT.md](.github/DEVELOPMENT.md)
- **GoBD-Konformität** – unveränderbare Buchungshistorie, Audit-Trail
- **Keine Secrets im Code** – lokale Konfiguration via `application-local.properties`
- **Anmeldung** – Spring Security mit Login, Passwörter als BCrypt-Hash, CSRF-Schutz aktiv
- **Handy-App mit eigenem Mitarbeiter-Token** – nur freigegebene Routen, zusätzliche Objektprüfungen, Begrenzung von Fehlversuchen, siehe [MOBILE_TOKEN_SECURITY.md](docs/MOBILE_TOKEN_SECURITY.md)
- **Website-Schnittstelle** – `/api/internal` nur mit gemeinsamem Schlüssel (Header `X-ERP-Website-Key`)
- **Parametrisierte Datenbankabfragen** – kein SQL-Injection-Risiko
- **HTML-Sanitisierung** – XSS-Schutz für E-Mail-Inhalte
- **UUID-basierte Dateinamen** – Path-Traversal-Schutz
- **Abgeschotteter Betrieb mit Docker** – Datenbank nur im internen Docker-Netz, App ohne Superuser-Rechte in der Datenbank, App-Container schreibgeschützt und mit minimalen Linux-Rechten, Netzsperre: das ERP erreicht das übrige Firmennetz nicht (Details: [deployment/docker-server/README.md](deployment/docker-server/README.md))
- **Geprüfte Updates** – Kundenserver bekommen nur Images, die im Pull Request vollständig grün getestet und per Starttest geprüft wurden; vor jedem Update wird gesichert, bei Fehlern zurückgerollt
