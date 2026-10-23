# Business Cases – Handwerkerprogramm

> **Stand:** Oktober 2026

## 🎯 Kernproblem

Handwerksbetriebe kämpfen mit dezentralen Papierprozessen, fehlender Projektkostentransparenz und manuellen Buchhaltungsaufgaben – dieses ERP-System digitalisiert den kompletten kaufmännischen Workflow von der Anfrage über Angebot und Bestellung bis zu Rechnung und Kasse.

---

## 1. Projektkalkulation mit Produktkategorien-Analyse

**Was:** Hierarchische Produktkategorien (z.B. "Dach > Flachdach") mit Verrechnungseinheiten (m², kg, Stück, lfd. Meter)

**Funktion:** `analysiereKategorie()` berechnet **Stunden pro Einheit** über alle Projekte einer Kategorie und legt eine lineare Regression (Fixzeit + Zeit je Einheit) für Zeitprognosen darüber

**Business Value:** Präzise Nachkalkulation und realistische Zeitschätzungen für neue Angebote

---

## 2. Erfolgsanalyse & Controlling-Dashboard

**Was:** Kennzahlen je Geschäftsjahr mit Gewinn, Brutto/Netto, Material- und Arbeitskosten (Finanzen & Controlling › Auswertung › Erfolgsanalyse)

**Features:**
- Top-10-Kunden nach Umsatz und Gewinn (sortierbar)
- Entwicklung von Umsatz und Gewinn je Monat
- Kategorie-Performance
- Lieferantenkosten mit Historie und Vorjahreswert
- Konversionsrate: wie viele Anfragen zu Projekten werden
- Regionale Verteilung der Projekte nach Ort
- Kostenstellen im Vorjahresvergleich
- Website-Daten (Besucher, Klicks auf Anrufen/E-Mail), sobald die Webseite Daten liefert

**Business Value:** Geschäftsführung sieht auf einen Blick Rentabilität pro Kunde/Kategorie

---

## 3. Intelligente Dokumentenverarbeitung (KI-gestützt)

**Was:** Automatische Erkennung von Angeboten, Auftragsbestätigungen, Lieferscheinen, Werkstoffzeugnissen, Rechnungen und Gutschriften der Lieferanten

**Technologie:**
1. ZUGFeRD-PDF-Extraktion (strukturierte Rechnungsdaten)
2. XML-Auswertung (XRechnung)
3. Sonst: Gemini AI zum Auslesen – optional; ohne KI werden E-Rechnungen trotzdem gelesen, alles andere trägt man von Hand ein

**Features:** Automatische Zuordnung zum Lieferanten über die E-Mail, Einordnung in die Dokumentenkette (Angebot → AB → Lieferschein → Rechnung, Gutschrift → Rechnung), Rechnungsvorschläge, Verteilung der Kosten auf Projekte und Kostenstellen, Genehmigung von Eingangsrechnungen

**Business Value:** Kein manuelles Abtippen von Lieferantenrechnungen mehr

---

## 4. E-Mail-Zentrale mit Auto-Zuordnung

**Was:** IMAP-Import je Postfach alle 60 Sekunden, Spam-Filter, Thread-Erkennung, Erkennung unzustellbarer Mails

**Features:**
- Mehrere Postfächer: Hauptpostfach, eigene Postfächer pro Mitarbeiter, Postfach für Rechnungen & Mahnungen; Sichtbarkeit je Abteilung und Benutzer
- Automatische Zuordnung zu Lieferanten, Projekten, Anfragen und Steuerberatern anhand der E-Mail-Adressen
- Anhänge von Lieferanten-Mails werden als Lieferantendokumente analysiert
- Signaturen (mit Firmenvorlagen) und Abwesenheitsnotizen verwaltbar (Kommunikation › E-Mail-Einstellungen); Abwesenheitsnotizen gelten pro Postfach – Mitarbeiter für das eigene und allgemeine Postfächer, Admins für alle – und antworten aus dem Postfach, in dem die Mail ankam
- E-Mail-Vorlagen mit Platzhaltern für Angebote, Rechnungen, Mahnungen und Webseiten-Anfragen

**Business Value:** Keine verlorenen E-Mails, vollständige Kommunikationshistorie pro Projekt

---

## 5. Mobile Zeiterfassung (PWA)

**Was:** Mobile App für Mitarbeiter (rund 23 Seiten)

**Features:**
- Start/Stop-Zeiterfassung auf Projekt + Kategorie + Tätigkeit
- Saldenauswertung (Soll/Ist, Überstunden)
- Urlaubsanträge mit Genehmigungsworkflow, Abwesenheiten (Urlaub, Zeitausgleich, Krankheit)
- Offline-Fähigkeit (Buchungen werden nachgereicht)
- Feiertage (Bayern) automatisch berücksichtigt
- Belege scannen, Lieferscheine hochladen, Projekt- und Anfrage-Notizen, Kalender, Kunden und Lieferanten

**Business Value:** Exakte Lohnkosten pro Projekt ohne Zettelwirtschaft

---

## 6. Mietverwaltung (Nebenkostenabrechnung)

**Was:** Jahresabrechnung für Mietobjekte mit Mietparteien

**Features:**
- Kostenverteilung nach Verbrauch, Fläche oder Prozent
- Zählerstanderfassung (Wasser, Strom, Heizung, Gas), auch als Erfassungs-PDF
- Vergleich mit dem Vorjahr, Kostenpositionen aus dem Vorjahr übernehmen
- PDF-Abrechnung für Mieter

**Business Value:** Rechtskonforme Nebenkostenabrechnung ohne Excel-Chaos

---

## 7. Dokumentengenerator

**Was:** Geschäftsdokumente aus Vorlagen (Formularwesen) und Textbausteinen mit Platzhaltern

**Platzhalter (Auswahl):**
- `{{DOKUMENTNUMMER}}`
- `{{KUNDENNAME}}`
- `{{KUNDENADRESSE}}`
- `{{BAUVORHABEN}}`
- `{{ZAHLUNGSZIEL}}`

**Dokumenttypen:** Angebot, Nachtragsangebot, Auftragsbestätigung, Rechnung, Teil-, Abschlags- und Schlussrechnung, Gutschrift, Storno, Zahlungserinnerung, 1. und 2. Mahnung

**Business Value:** Professionelle, einheitliche Geschäftsdokumente ohne Word-Templates

---

## 8. Digitale Angebotsfreigabe

**Was:** Kunden nehmen Angebote und Auftragsbestätigungen über einen Link auf der Firmen-Webseite digital an

**Features:**
- Link mit einstellbarer Gültigkeit (Standard 14 Tage) direkt in der E-Mail
- Bestätigung per 6-stelligem Code an die E-Mail-Adresse des Kunden
- Wahl von Alternativpositionen durch den Kunden
- Nach der Annahme eines Angebots entsteht automatisch die Auftragsbestätigung und geht an den Kunden
- Status im Programm: „Wartet auf Kunde“, „Digital angenommen“, abgelaufen

**Business Value:** Schnellere Aufträge ohne Ausdrucken, Unterschreiben und Einscannen

---

## 9. Bestellwesen mit Lieferantenpreisen

**Was:** Material aus Projekten sammeln, Bestellungen per PDF und E-Mail an Lieferanten schicken, Lieferanten-Unterlagen bis zur Rechnung verfolgen

**Features:**
- Kilogramm-Berechnung für Werkstoffe
- Lieferanten-Artikelpreise mit Preishistorie hinterlegen
- Schnittformen und Winkel für Profile
- Bestellübersicht: Angebote, Bestellt, Rechnung zuordnen, Erledigt

**Business Value:** Keine vergessenen Bestellungen, automatische Preiskalkulation

---

## 10. Belege & Kasse

**Was:** Belege per Handy scannen und am PC prüfen, Bar-Kassenbuch mit Monatsabschluss

**Features:**
- Sechs einfache Buchungsarten (Geld eingenommen, Geld ausgegeben, Bank, Privat)
- Kasse zählen, Monat abschließen, Fehler per Storno korrigieren (GoBD)
- Monatspaket für den Steuerberater: Kassenbuch-PDF, DATEV-Buchungsstapel, Eingangsrechnungen, Belegdateien

**Business Value:** Kassenbuch ohne Papierkram, steuerberater-fertig

---

## 11. Webseiten-Anfragen und Telefon

**Was:** Anfragen von der Firmen-Webseite landen direkt im Programm; Anrufe der FRITZ!Box werden mitgeschrieben

**Features:**
- Anfrage-Formular der Webseite legt Kunde, Anfrage und Notiz mit Bildern an, schickt eine Bestätigungsmail und eine Push-Nachricht aufs Handy
- Anrufliste und Anrufbeantworter (Kommunikation › Telefon), Anrufer werden Kunden, Lieferanten und Steuerberatern zugeordnet

**Business Value:** Keine Anfrage und kein Rückruf geht verloren

---

## Technische Architektur

| Layer | Technologie |
|-------|-------------|
| Backend | Spring Boot 4.1, Java 25, JPA/Hibernate, Flyway |
| Datenbank | PostgreSQL 16 |
| PC-Frontend | React 19 + TypeScript + Vite + Tailwind CSS |
| Mobile | React-PWA (Zeiterfassung, offline-fähig) |
| KI | Google Gemini API (optional), Ollama (optional, lokal) |
| PDF | OpenPDF, ZUGFeRD (Mustang), PDFBox |

---

## Kennzahlen

Stand Oktober 2026, gezählt im Quellcode:

- **rund 190 Services**
- **rund 100 Controller**
- **rund 125 Domain-Entities**
- **44 Desktop-Seiten** (PC-Frontend)
- **23 Mobile-Seiten** (PWA)

> Ein vollständiges ERP für Handwerksbetriebe.
