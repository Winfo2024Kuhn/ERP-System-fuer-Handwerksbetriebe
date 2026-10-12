# Dokumentationsplan – Kalkulationsprogramm

## Übersicht

Diese Datei hält fest, welche Dokumentation es im Repository tatsächlich gibt und wo noch etwas fehlt. Der Einstieg für Leser ist der Index [docs/README.md](README.md).

Stand: 12.10.2026. Neue Doku-Dateien hier und im Index eintragen.

---

## 1. Fachliche und technische Doku (`docs/`)

### Installation, Betrieb und Sicherheit

| Dokument | Inhalt |
|----------|--------|
| [INSTALLATION.md](INSTALLATION.md) | Installationswege (Docker beim Kunden, Docker zum Ausprobieren, Entwickeln), Zugang von unterwegs (Tailscale, Funnel, Reverse Proxy, Cloudflare Tunnel), Skript- und Umgebungsvariablen-Referenz |
| [MOBILE_TOKEN_SECURITY.md](MOBILE_TOKEN_SECURITY.md) | Mitarbeiter-Token der Handy-App, Wartezeiten nach Fehlversuchen, vertrauenswürdige Proxys, Netzgrenze, erlaubte Adressen, Login-CSRF, CORS, Website-Schnittstelle `/api/internal/**` |
| [ANFRAGE_FUNNEL_SETUP.md](ANFRAGE_FUNNEL_SETUP.md) | Anbindung des Anfrage-Formulars der Firmen-Website an das ERP |
| [ADMIN_ENDPUNKTE.md](ADMIN_ENDPUNKTE.md) | Admin- und Wartungs-Endpunkte ohne Knopf im Frontend |

### Architektur und Entwicklung

| Dokument | Inhalt |
|----------|--------|
| [ARCHITEKTUR_UEBERSICHT.md](ARCHITEKTUR_UEBERSICHT.md) | Tech-Stack, Paketstruktur, Design-Patterns, Integrationen, Flyway, Konfiguration, Deployment |
| [API_REFERENZ.md](API_REFERENZ.md) | REST-Endpoint-Übersicht |
| [MONATS_SALDO_CACHING.md](MONATS_SALDO_CACHING.md) | Cache der monatlichen Zeitkonto-Salden |
| [TESTPLAN.md](TESTPLAN.md) | Testarten, Befehle, CI-Jobs, Bestand der Tests und Lücken |
| [email-system-refactoring.md](email-system-refactoring.md) | E-Mail-System heute: Postfächer, Import, Zuordnung, Versand, Signaturen, Abwesenheiten, E-Mail Center (Dateiname historisch) |

### Geschäftsprozesse

| Dokument | Inhalt |
|----------|--------|
| [BUSINESS_CASES.md](BUSINESS_CASES.md) | Geschäftsnutzen der Module |
| [ANFRAGEWESEN.md](ANFRAGEWESEN.md) | Anfragen, Notizen und Bilder, Weg zum Projekt |
| [ANGEBOTSWESEN.md](ANGEBOTSWESEN.md) | Angebote als Geschäftsdokument (`ANGEBOT`, `NACHTRAGSANGEBOT`): Editor, Versand, digitale Annahme, automatische Auftragsbestätigung |
| [RECHNUNGSWESEN.md](RECHNUNGSWESEN.md) | Ausgangsdokumente von Angebot bis Schlussrechnung, Nummernvergabe, PDF |
| [DOKUMENTEN_LIFECYCLE.md](DOKUMENTEN_LIFECYCLE.md) | Lebenszyklus der Geschäftsdokumente (Eingang und Ausgang) |
| [GOBD_COMPLIANCE.md](GOBD_COMPLIANCE.md) | GoBD: Unveränderbarkeit, Löschverbot, Storno, Audit-Trail |
| [ZAHLUNGSVERKEHR.md](ZAHLUNGSVERKEHR.md) | Zahlungen, offene Posten, Mahnwesen |
| [LIEFERANTEN_DOKUMENTEN.md](LIEFERANTEN_DOKUMENTEN.md) | Eingangsrechnungen, KI-Erkennung, Freigabe |
| [ZUGFERD_E_INVOICING.md](ZUGFERD_E_INVOICING.md) | E-Rechnung (ZUGFeRD) erstellen und lesen |
| [BESTELLWESEN.md](BESTELLWESEN.md) | Einkauf, Bestellungen, Bestell-PDF |
| [ZEITERFASSUNG_WORKFLOWS.md](ZEITERFASSUNG_WORKFLOWS.md) | Zeiterfassung online und offline |
| [KASSE_BUCHHALTUNG.md](KASSE_BUCHHALTUNG.md) | Kassenbuch und Buchhaltung (technisch) |
| [PLAN_VERRECHNUNGSLOHN_RECHNER.md](PLAN_VERRECHNUNGSLOHN_RECHNER.md) | Verrechnungslohn-Rechner (umgesetzt, beschreibt den Ist-Stand) |

### Anleitungen für Anwender

| Dokument | Inhalt |
|----------|--------|
| [KASSE_ANLEITUNG.md](KASSE_ANLEITUNG.md) | So funktioniert die Kasse |
| [benutzer/monatsabschluss.md](benutzer/monatsabschluss.md) | Monatsstunden prüfen und abschließen |
| [benutzer/datev-lodas-export.md](benutzer/datev-lodas-export.md) | Monatsstunden für DATEV LODAS exportieren |

### Unterordner

| Ordner | Inhalt |
|--------|--------|
| `Dokumentation/` | 7 draw.io-Diagramme: Architektur, Dokumenten-Lifecycle, E-Mail-System, Zeiterfassung, Rechnungswesen, Datenmodell, GlobalEmailWatcher |
| `agent instructions/docs/` | Pflichtlektüre für KI-Agenten vor dem Editieren: `BACKEND_ARCH.md`, `FRONTEND_UI.md`, `TESTING_SECURITY.md`; dazu der Auftrag `SCRAPING_ARTIKEL_CSV.md` |
| `freigabe/` | `FREIGABE_FEATURE_PROMPT.md`: Schnittstelle zwischen Firmen-Website und ERP für die digitale Freigabe (Dateiname historisch) |
| `reviews/` | Prüfbericht E-Mail-Center vom 14.09.2026 |
| `superpowers/` | Arbeitsdokumente der Feature-Entwicklung: 17 Specs (dazu Screenshots unter `specs/bilder/`), 28 Pläne, 3 Reviews. Sie zeigen den Stand ihrer Entstehung und werden danach nicht weiter gepflegt |

---

## 2. Betrieb beim Kunden (`deployment/`)

| Dokument | Inhalt |
|----------|--------|
| [deployment/README.md](../deployment/README.md) | Wegweiser durch die Deployment-Wege |
| [deployment/docker-server/README.md](../deployment/docker-server/README.md) | Empfohlener Weg: Docker beim Kunden – Einrichtung, Sicherheit, tägliche Sicherung, Nachtupdate mit Rollback, Starttest |
| [deployment/docker-server/EINRICHTEN.md](../deployment/docker-server/EINRICHTEN.md) | Checkliste zum Abhaken für einen neuen Kunden |
| [deployment/mobile-public/README.md](../deployment/mobile-public/README.md) | Öffentlicher HTTPS-Zugang nur für die Handy-App (Windows und Docker) |
| [deployment/DEPLOYMENT_README.md](../deployment/DEPLOYMENT_README.md) | Altweg: Betrieb als JAR unter Windows mit geplanten Aufgaben |
| [deployment/scripts/README.md](../deployment/scripts/README.md) | Update-Skript des Altwegs |
| [deployment/openfile-launcher/README.md](../deployment/openfile-launcher/README.md), [SETUP-ERSTELLEN.md](../deployment/openfile-launcher/SETUP-ERSTELLEN.md) | `openfile://`-Launcher für Client-PCs (CAD- und Excel-Dateien aus der Netzwerkfreigabe öffnen) |

---

## 3. Weitere Doku außerhalb von `docs/`

| Datei | Inhalt |
|-------|--------|
| [README.md](../README.md) | Projektvorstellung, Demo mit Beispieldaten |
| [SECURITY.md](../SECURITY.md) | Sicherheitsrichtlinie, Lücken melden |
| [THIRD-PARTY-LICENSES.md](../THIRD-PARTY-LICENSES.md) | Lizenzen der verwendeten Bibliotheken |
| [RAG_INDEXING.md](../RAG_INDEXING.md) | Wissen der KI-Hilfe: RAG-Datei, Erzeugung beim Image-Bau, Nachtupdate, Entwicklung, von Hand |
| [src/main/resources/db/postgresql/migration/README.md](../src/main/resources/db/postgresql/migration/README.md) | Migrationen schreiben (PostgreSQL) |
| [react-pc-frontend/README.md](../react-pc-frontend/README.md), [react-zeiterfassung/README.md](../react-zeiterfassung/README.md) | Kurzbeschreibung der beiden Oberflächen |
| [scripts/README-artikel-backfill.md](../scripts/README-artikel-backfill.md) | Artikel-Dokumenttexte nachtragen |
| [scripts/claude-cloud/README.md](../scripts/claude-cloud/README.md) | Claude Code in der Cloud einrichten |
| `AGENTS.md`, `GEMINI.md`, `.claude/CLAUDE.md`, `.github/DEVELOPMENT.md` | Projektkontext und Regeln für KI-Agenten und Entwickler |

---

## 4. Lücken

Für diese Bereiche gibt es keine eigene, aktuelle Doku unter `docs/` (höchstens Einträge in der API-Referenz oder Arbeitsdokumente unter `superpowers/`):

| Bereich | Bisher |
|---------|--------|
| Telefonanlage (FRITZ!Box: Anrufliste, Anrufbeantworter, Live-Anrufe) | – |
| Benachrichtigungen (Glocke, Meldungen von Sicherung und Nachtupdate) | kurz in `deployment/docker-server/README.md` |
| Mietabrechnung | nur `API_REFERENZ.md` |
| Formularwesen (Briefpapier, Dokumentvorlagen) | – |
| Web Push und Kalender-Erinnerungen der Handy-App | – |
