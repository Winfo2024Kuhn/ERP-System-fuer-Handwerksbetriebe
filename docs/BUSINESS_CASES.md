# Business Cases – Handwerkerprogramm

## 🎯 Kernproblem

Handwerksbetriebe kämpfen mit dezentralen Papierprozessen, fehlender Projektkostentransparenz und manuellen Buchhaltungsaufgaben – dieses ERP-System digitalisiert den kompletten kaufmännischen Workflow von Angebot bis Rechnung.

---

## 1. Projektkalkulation mit Produktkategorien-Analyse

**Was:** Hierarchische Produktkategorien (z.B. "Dach > Flachdach") mit Verrechnungseinheiten (m², kg, Stück, lfd. Meter)

**Funktion:** `analysiereKategorie()` berechnet **Stunden pro Einheit** über alle Projekte einer Kategorie, ermöglicht lineare Regression für Zeitprognosen

**Business Value:** Präzise Nachkalkulation und realistische Zeitschätzungen für neue Angebote

---

## 2. Erfolgsanalyse & Controlling-Dashboard

**Was:** Echtzeit-KPIs mit Gewinn, Brutto/Netto, Material- und Arbeitskosten

**Features:**
- Top-10-Kunden-Ranking nach Umsatz und Gewinn
- Monatlicher Umsatzverlauf mit Vorjahresvergleich
- Lieferantenkosten-Jahresübersicht
- Angebots-Konversionsrate (wie viele Angebote werden zu Projekten)
- Regionale Heatmap (Projekte nach PLZ/Ort)

**Business Value:** Geschäftsführung sieht auf einen Blick Rentabilität pro Kunde/Kategorie

---

## 3. Intelligente Dokumentenverarbeitung (KI-gestützt)

**Was:** Automatische Erkennung von Rechnungen, Lieferscheinen, Gutschriften

**Technologie:**
1. ZUGFeRD-PDF-Extraktion (strukturierte Rechnungsdaten)
2. XML-Parsing (XRechnung)
3. Fallback: Gemini AI für OCR/Textanalyse

**Features:** Automatische Projekt-/Lieferantenzuordnung, Gutschrift-Rechnungs-Verknüpfung

**Business Value:** Kein manuelles Abtippen von Lieferantenrechnungen mehr

---

## 4. E-Mail-Zentrale mit Auto-Zuordnung

**Was:** IMAP-Import alle 60 Sekunden, Spam-Filter, Thread-Erkennung

**Features:**
- Automatische Zuordnung zu Kunden/Lieferanten/Projekten basierend auf E-Mail-Adressen
- Anhänge werden als Lieferantendokumente analysiert
- Signaturen und Abwesenheitsnotizen verwaltbar

**Business Value:** Keine verlorenen E-Mails, vollständige Kommunikationshistorie pro Projekt

---

## 5. Mobile Zeiterfassung (PWA)

**Was:** 12-seitige mobile App für Mitarbeiter

**Features:**
- Start/Stop-Zeiterfassung auf Projekt + Kategorie + Arbeitsgang
- Saldenauswertung (Soll/Ist, Überstunden)
- Urlaubsanträge mit Genehmigungsworkflow
- Offline-Fähigkeit mit `OfflineService`
- Feiertage (Bayern) automatisch berücksichtigt

**Business Value:** Exakte Lohnkosten pro Projekt ohne Zettelwirtschaft

---

## 6. Mietverwaltung (Nebenkostenabrechnung)

**Was:** Jahresabrechnung für Mietobjekte mit Mietparteien

**Features:**
- Kostenstellen-Verteilung nach Verbrauch oder Fläche
- Zählerstanderfassung (Wasser, Strom, Gas)
- Verbrauchsvergleich Vorjahr
- PDF-Generierung für Mieter

**Business Value:** Rechtskonforme Nebenkostenabrechnung ohne Excel-Chaos

---

## 7. Dokumentengenerator (WYSIWYG)

**Was:** Template-basierte Dokumente mit Platzhaltern

**Platzhalter:**
- `{{DOKUMENTNUMMER}}`
- `{{KUNDENNAME}}`
- `{{EMPFAENGER_STRASSE}}`
- `{{LEISTUNGEN_TABELLE}}`

**Dokumenttypen:** Angebot, Rechnung, Auftragsbestätigung, Mahnung

**Business Value:** Professionelle, einheitliche Geschäftsdokumente ohne Word-Templates

---

## 8. Bestellwesen mit Lieferantenpreisen

**Was:** Artikel aus Projekten sammeln, Bestelllisten generieren

**Features:**
- Kilogramm-Berechnung für Werkstoffe
- Lieferanten-Artikelpreise hinterlegen
- Schnittformen und Winkel für Profile

**Business Value:** Keine vergessenen Bestellungen, automatische Preiskalkulation

---

## Technische Architektur

| Layer | Technologie |
|-------|-------------|
| Backend | Spring Boot 3.2.5, Java 23, JPA/Hibernate, Flyway |
| Datenbank | MySQL 8 |
| PC-Frontend | React 18 + TypeScript + Vite + Tailwind CSS |
| Mobile | React PWA (Zeiterfassung, Offline-fähig) |
| KI | Google Gemini API, Ollama (optional) |
| PDF | OpenPDF, ZUGFeRD (Mustang), PDFBox |

---

## Kennzahlen

- **84 Services**
- **56 Controller**
- **90 Domain-Entities**
- **31 Desktop-Seiten** (PC-Frontend)
- **18 Mobile-Seiten** (PWA)

> Ein vollständiges ERP für Handwerksbetriebe.
