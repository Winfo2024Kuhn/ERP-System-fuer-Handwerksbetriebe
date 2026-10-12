# 📚 Dokumentation – Handwerkerprogramm

Willkommen in der Dokumentation des Handwerkerprogramms. Hier findest du die technischen und fachlichen Dokumente zum System.

> **Die KI-Hilfe liest mit:** Alles, was direkt in `docs/` und in `docs/benutzer/` steht, kommt in ihr Wissen (siehe [RAG_INDEXING.md](../RAG_INDEXING.md)). Was hier über Menüs, Knöpfe und Abläufe steht, erklärt sie den Nutzern weiter – deshalb muss es stimmen. Arbeitsdokumente unter `superpowers/` und `reviews/` gehören nicht dazu.

Stand: Oktober 2026. Den vollständigen Bestand samt Lücken führt [DOKUMENTATIONSPLAN.md](DOKUMENTATIONSPLAN.md).

---

## Für Anwender

| Dokument | Inhalt |
|----------|--------|
| [KASSE_ANLEITUNG.md](KASSE_ANLEITUNG.md) | So funktioniert die Kasse |
| [benutzer/monatsabschluss.md](benutzer/monatsabschluss.md) | Monatsstunden prüfen und abschließen |
| [benutzer/datev-lodas-export.md](benutzer/datev-lodas-export.md) | Monatsstunden für DATEV LODAS exportieren |

## Geschäftsprozesse

| Dokument | Inhalt |
|----------|--------|
| [BUSINESS_CASES.md](BUSINESS_CASES.md) | Geschäftsnutzen aller Module |
| [ANFRAGEWESEN.md](ANFRAGEWESEN.md) | Anfragen, Notizen und Bilder, Weg zum Projekt |
| [ANGEBOTSWESEN.md](ANGEBOTSWESEN.md) | Angebote schreiben, versenden, digital annehmen lassen |
| [RECHNUNGSWESEN.md](RECHNUNGSWESEN.md) | Geschäftsdokumente von Angebot bis Schlussrechnung, Nummernvergabe, PDF |
| [DOKUMENTEN_LIFECYCLE.md](DOKUMENTEN_LIFECYCLE.md) | Lebenszyklus der Geschäftsdokumente (Eingang und Ausgang) |
| [GOBD_COMPLIANCE.md](GOBD_COMPLIANCE.md) | GoBD: Unveränderbarkeit, Löschverbot, Storno, Audit-Trail |
| [ZAHLUNGSVERKEHR.md](ZAHLUNGSVERKEHR.md) | Bezahlt-Status, offene Posten, Mahnwesen |
| [ZUGFERD_E_INVOICING.md](ZUGFERD_E_INVOICING.md) | E-Rechnung (ZUGFeRD) erstellen und lesen |
| [LIEFERANTEN_DOKUMENTEN.md](LIEFERANTEN_DOKUMENTEN.md) | Eingangsrechnungen, KI-Erkennung, Freigabe |
| [BESTELLWESEN.md](BESTELLWESEN.md) | Einkauf: Bedarf, Bestellungen, Bestell-PDF |
| [email-system-refactoring.md](email-system-refactoring.md) | E-Mail-System: Postfächer, Import, Zuordnung, Versand, Signaturen |
| [ZEITERFASSUNG_WORKFLOWS.md](ZEITERFASSUNG_WORKFLOWS.md) | Zeiterfassung in der Handy-App, online und offline |
| [KASSE_BUCHHALTUNG.md](KASSE_BUCHHALTUNG.md) | Kassenbuch und Belege (technisch) |
| [PLAN_VERRECHNUNGSLOHN_RECHNER.md](PLAN_VERRECHNUNGSLOHN_RECHNER.md) | Verrechnungslohn-Rechner |

## Installation, Betrieb und Sicherheit

| Dokument | Inhalt |
|----------|--------|
| [INSTALLATION.md](INSTALLATION.md) | Installationswege, Zugang von unterwegs, Umgebungsvariablen |
| [deployment/docker-server/README.md](../deployment/docker-server/README.md) | Docker beim Kunden: Sicherung, Nachtupdate mit Rollback, Starttest |
| [MOBILE_TOKEN_SECURITY.md](MOBILE_TOKEN_SECURITY.md) | Sicherheit der Handy-App und der Website-Schnittstelle |
| [ANFRAGE_FUNNEL_SETUP.md](ANFRAGE_FUNNEL_SETUP.md) | Anfrage-Formular der Firmen-Website anbinden |
| [ADMIN_ENDPUNKTE.md](ADMIN_ENDPUNKTE.md) | Admin- und Wartungs-Endpunkte ohne Knopf im Frontend |
| [RAG_INDEXING.md](../RAG_INDEXING.md) | Wissen der KI-Hilfe: wie die RAG-Datei entsteht und mit dem Nachtupdate kommt |

## Architektur und Entwicklung

| Dokument | Inhalt |
|----------|--------|
| [ARCHITEKTUR_UEBERSICHT.md](ARCHITEKTUR_UEBERSICHT.md) | Tech-Stack, Paketstruktur, Integrationen, Konfiguration |
| [API_REFERENZ.md](API_REFERENZ.md) | Alle REST-Endpunkte (97 Controller) und ihre Absicherung |
| [MONATS_SALDO_CACHING.md](MONATS_SALDO_CACHING.md) | Monatssalden der Zeitkonten und Monatsabschluss |
| [TESTPLAN.md](TESTPLAN.md) | Testarten, Befehle, CI und Lücken |
| [agent instructions/docs/](agent%20instructions/docs/) | Pflichtlektüre für KI-Agenten: `BACKEND_ARCH.md`, `FRONTEND_UI.md`, `TESTING_SECURITY.md` |

---

## Architektur-Diagramme

Im Verzeichnis [`Dokumentation/`](Dokumentation/) befinden sich draw.io-Diagramme. Sie sind älter als die Texte oben – im Zweifel gilt der Text.

| Diagramm | Beschreibung |
|----------|--------------|
| 01_Architektur_Uebersicht.drawio | System-Gesamtarchitektur |
| 02_Dokumenten_Lifecycle.drawio | Dokumenten-Lebenszyklus |
| 03_Email_System.drawio | E-Mail-System-Architektur |
| 04_Zeiterfassung_Workflow.drawio | Zeiterfassungs-Ablauf |
| 05_Rechnungswesen.drawio | Rechnungsprozess |
| 06_Datenmodell_Kernentitaeten.drawio | Datenmodell der Kern-Entities |
| GlobalEmailWatcher.drawio | Globaler E-Mail-Watcher |

> 💡 draw.io-Dateien können im Browser unter [app.diagrams.net](https://app.diagrams.net) geöffnet werden.

---

## Empfohlene Lesereihenfolge

1. **[BUSINESS_CASES.md](BUSINESS_CASES.md)** – Überblick über alle Module
2. **[ARCHITEKTUR_UEBERSICHT.md](ARCHITEKTUR_UEBERSICHT.md)** – Technische Architektur
3. **[GOBD_COMPLIANCE.md](GOBD_COMPLIANCE.md)** – Rechtliche Grundlagen
4. **[RECHNUNGSWESEN.md](RECHNUNGSWESEN.md)** – Kernprozess
5. **[DOKUMENTEN_LIFECYCLE.md](DOKUMENTEN_LIFECYCLE.md)** – Dokumentenflüsse
6. **[ZAHLUNGSVERKEHR.md](ZAHLUNGSVERKEHR.md)** – Zahlungen & Offene Posten
7. **[LIEFERANTEN_DOKUMENTEN.md](LIEFERANTEN_DOKUMENTEN.md)** – Eingangsrechnungen
8. **[ZUGFERD_E_INVOICING.md](ZUGFERD_E_INVOICING.md)** – E-Rechnungen
9. **[BESTELLWESEN.md](BESTELLWESEN.md)** – Einkauf & Bestellungen
10. **[ANGEBOTSWESEN.md](ANGEBOTSWESEN.md)** – Angebote
11. **[ZEITERFASSUNG_WORKFLOWS.md](ZEITERFASSUNG_WORKFLOWS.md)** – Handy-App
12. **[KASSE_BUCHHALTUNG.md](KASSE_BUCHHALTUNG.md)** – Kassenbuch
13. **[API_REFERENZ.md](API_REFERENZ.md)** – REST-Endpunkte

---

📖 *Zurück zum [Hauptprojekt](../README.md)*
