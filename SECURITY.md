# Sicherheitsrichtlinie

## Unterstützte Versionen

Dieses Projekt befindet sich in aktiver Entwicklung. Sicherheitsupdates werden für die aktuelle Version bereitgestellt.

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
- **Parametrisierte Datenbankabfragen** – kein SQL-Injection-Risiko
- **HTML-Sanitisierung** – XSS-Schutz für E-Mail-Inhalte
- **UUID-basierte Dateinamen** – Path-Traversal-Schutz
