<p align="center">
  <img src="assets/app_logo.png" alt="Handwerkerprogramm Logo" width="120" />
</p>

<h1 align="center">Handwerkerprogramm</h1>

<p align="center">
  <strong>Das Open-Source-ERP, das für Handwerksbetriebe gebaut wurde – nicht an sie angepasst.</strong><br/>
  Angebote kalkulieren. Zeiten erfassen. Rechnungen stellen. Projekte nachkalkulieren. Alles in einer Anwendung.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-23-orange?logo=openjdk" alt="Java 23" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?logo=spring-boot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react" alt="React" />
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript" alt="TypeScript" />
  <img src="https://img.shields.io/badge/MariaDB-11-003545?logo=mariadb&logoColor=white" alt="MariaDB" />
  <img src="https://img.shields.io/badge/KI--Assistent-Gemini-4285F4?logo=google&logoColor=white" alt="KI" />
  <img src="https://img.shields.io/badge/Lizenz-MIT-green" alt="Lizenz" />
</p>

<div align="center">

| Metric | Value |
|--------|-------|
| ⭐ Stars | ![Stars](https://img.shields.io/github/stars/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe?style=flat&color=f59e0b) |
| 🍴 Forks | ![Forks](https://img.shields.io/github/forks/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe?style=flat&color=6366f1) |
| 👁️ Total Views | ![Views](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FWinfo2024Kuhn%2FERP-System-fuer-Handwerksbetriebe%2Fmain%2F.github%2Ftraffic%2Fdata.json&query=%24.total_views&label=&color=dc2626) |
| 👤 Unique Visitors (total) | ![Unique Views](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FWinfo2024Kuhn%2FERP-System-fuer-Handwerksbetriebe%2Fmain%2F.github%2Ftraffic%2Fdata.json&query=%24.total_unique_visitors&label=&color=16a34a) |
| 📥 Total Clones | ![Clones](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FWinfo2024Kuhn%2FERP-System-fuer-Handwerksbetriebe%2Fmain%2F.github%2Ftraffic%2Fdata.json&query=%24.total_clones&label=&color=7c3aed) |
| 🧑‍💻 Unique Cloners (total) | ![Unique Clones](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FWinfo2024Kuhn%2FERP-System-fuer-Handwerksbetriebe%2Fmain%2F.github%2Ftraffic%2Fdata.json&query=%24.total_unique_cloners&label=&color=0891b2) |

</div>

<p align="center">
  ⭐ <strong>Gefällt dir das Projekt? Ein Stern hilft dabei, dass mehr Handwerksbetriebe dieses Tool finden!</strong>
</p>

<br/>

<p align="center">
  <strong>🖥️ Desktop-Frontend</strong>
</p>
<p align="center">
  <img src="assets/pc_frontend.png" alt="Desktop Frontend – Projektkalkulation & Controlling" width="90%" />
</p>

<br/>

<p align="center">
  <strong> Automatische Zeitkalkulation per Linearer Regression</strong>
</p>
<p align="center">
  <img src="assets/Produktkategorie_Nachkalkulation.png" alt="Produktkategorie Nachkalkulation – Lineare Regression" width="90%" />
</p>

<br/>

<p align="center">
  <strong>📱 Mobile App (PWA)</strong>
</p>
<p align="center">
  <img src="assets/mobild_frontend.png" alt="Mobile Zeiterfassung – PWA für die Baustelle" width="35%" />
</p>

<br/>

<p align="center">
  <strong>📝 Dokumenteditor – Block-basierte Angebots- & Rechnungserstellung</strong>
</p>
<p align="center">
  <img src="assets/Dokumenteditor.png" alt="Dokumenteditor – Block-basierter Editor mit Live-PDF-Vorschau" width="90%" />
</p>

---

## 🙋 Über dieses Projekt

Ich habe dieses Programm entwickelt, um meinem Vater in seinem Handwerksbetrieb zu helfen. Was als kleines Tool zur Projektkalkulation begann, ist über die Zeit zu einem vollständigen ERP-System gewachsen – mit eingebautem KI-Assistenten, automatischer Zeitkalkulation, Echtzeit-Nachkalkulation, E-Mail-Integration und vielem mehr.

Ich studiere **Wirtschaftsinformatik im 4. Semester** und habe das Projekt durch **Pair-Programming mit Claude (AI)** entwickelt. Jetzt möchte ich es als Open Source verfügbar machen, damit auch andere Handwerksbetriebe davon profitieren können.

---

## 🚀 Was dieses ERP besonders macht

> Die meisten ERP-Systeme sind für Konzerne gebaut und kosten tausende Euro. Dieses hier ist speziell für Handwerksbetriebe entwickelt – mit Features, die echte Probleme aus dem Arbeitsalltag lösen.

### 🧠 KI-Assistent, der das Programm kennt (RAG-basiert)

Kein dummer Chatbot – der eingebaute KI-Assistent **versteht die gesamte Anwendung**:

- **Kontextbewusst:** Die KI sieht, auf welcher Seite du gerade bist, welche Felder sichtbar sind, welche Fehler angezeigt werden und welche Buttons deaktiviert sind – und hilft dir gezielt weiter
- **Retrieval-Augmented Generation (RAG):** Beim Start wird der gesamte Quellcode (Frontend, Backend, Docs) in semantische Chunks aufgeteilt, als Vektoren embedded (Gemini text-embedding-004) und in einem lokalen Vektorindex gespeichert. Bei jeder Nutzerfrage werden die relevantesten Code-Stellen per Cosine-Similarity gefunden und als Kontext an die KI übergeben
- **Google Search Grounding:** Neben Programm-Hilfe beantwortet die KI auch fachliche Fragen – DIN-Normen, Materialeigenschaften, Vorschriften, aktuelle Wetterdaten für Baustellen
- **Überall verfügbar:** Schwebendes Chat-Widget auf jeder Seite, Gesprächsverlauf bleibt erhalten

> Beispiel: „Wie erstelle ich eine Schlussrechnung?" → Die KI findet den relevanten Code, versteht den Workflow und erklärt dir Schritt für Schritt, was zu tun ist.

### ⏱️ Automatische Zeitkalkulation per Lineare Regression

Das System **lernt aus deinen abgeschlossenen Projekten**, wie lange Arbeiten dauern:

- **Datengetriebene Kalkulation:** Für jede Produktkategorie (z. B. „Fenster einbauen", „Flachdach abdichten") werden alle vergangenen Projekte mit Zeitbuchungen analysiert
- **Lineare Regression:** Berechnet automatisch `Fixzeit + Variable Zeit × Menge` – also z. B. „0,5 h Rüstzeit + 1,2 h pro Fenster"
- **Aufschlüsselung nach Arbeitsgang:** Zeigt für jeden Arbeitsgang (Montage, Nacharbeit, Transport) die durchschnittlichen Stunden pro Einheit
- **Interaktives Diagramm:** Scatter-Plot mit Regressionslinie – sofort sichtbar, welche Projekte Ausreißer waren
- **Jahresfilter:** Analysiere nur aktuelle Jahre oder vergleiche die Entwicklung

> Kein manuelles Schätzen mehr – das Programm sagt dir auf Basis echter Daten, wie lange ein Auftrag dauern wird.

### 📊 Echtzeit-Nachkalkulation im Projekt

Jedes Projekt zeigt dir **live**, ob du Geld verdienst oder verlierst:

- **Arbeitskosten:** Die Zeiterfassung der Mitarbeiter (Stunden × Stundensatz) wird automatisch pro Projekt summiert – aufgeschlüsselt nach Kategorie → Arbeitsgang → Mitarbeiter
- **Materialkosten aus 3 Quellen:** Manuelle Einträge + Artikel aus dem Lager + zugeordnete Eingangsrechnungen – alles fließt automatisch zusammen
- **Gewinn-Berechnung:** `Netto-Preis − Arbeitskosten − Materialkosten = Gewinn` – wird rot, wenn negativ
- **8-Tab-Übersicht:** Zeiten, Materialkosten, E-Mails, Geschäftsdokumente, Dateien, Beschreibung, Bautagebuch – alles an einem Ort

> Du siehst sofort: „Bei diesem Projekt sind wir 800 € im Minus, weil der Monteur 12 Stunden länger gebraucht hat als kalkuliert."

### 🛒 Eingangsrechnungen → Projekte & Kostenstellen zuordnen

Lieferantenrechnungen verschwinden nicht in einem Ordner – sie werden **aktiv den richtigen Projekten zugeordnet**:

- **4-Stufen-Bestellkette:** Anfrage → Bestellung → Rechnung eingegangen → Zugeordnet
- **Flexible Aufteilung:** Eine Rechnung kann prozentual oder als absoluter Betrag auf mehrere Projekte und Kostenstellen (Lager, Büro, Versicherung) verteilt werden
- **Automatischer Rest:** Das letzte Projekt bekommt automatisch den Restbetrag – keine Rundungsfehler
- **Sofortige Nachkalkulation:** Die zugeordneten Beträge erscheinen sofort im ProjektEditor unter Materialkosten und aktualisieren die Gewinnberechnung
- **PDF-Verknüpfung:** Die Originalrechnung wird automatisch im Projekt als Eingangsrechnung hinterlegt

> Beispiel: 10.000 € Lieferantenrechnung → 60 % auf „Hausbau Meyer" (6.000 €) + 40 % auf Kostenstelle „Lager" (4.000 €). Sofort sichtbar im Projekt.

### 📬 Eingangsrechnungen automatisch aus dem E-Mail-Postfach

Keine manuelle Ablage mehr – das System **holt sich Lieferantenrechnungen selbstständig**:

- **IMAP-Polling alle 60 Sekunden:** Das System überwacht automatisch das E-Mail-Postfach und erkennt neue Nachrichten
- **Automatische Lieferanten-Zuordnung:** Anhand der Absender-Domain wird die E-Mail dem richtigen Lieferanten zugeordnet
- **KI-Analyse der Anhänge:** PDF-Anhänge werden automatisch analysiert – erst per ZUGFeRD-XML, dann per Gemini AI (OCR-Fallback). Die KI erkennt Dokumenttyp (Rechnung, Lieferschein, Gutschrift), Rechnungsnummer, Beträge und Zahlungsbedingungen
- **Automatische Bestellungs-Verknüpfung:** Erkannte Bestellnummern werden mit offenen Bestellungen abgeglichen – die Rechnung dockt automatisch an die richtige Bestellkette an
- **Direkt verfügbar:** Nach der Analyse erscheint die Rechnung in der Bestellungsübersicht und kann sofort Projekten zugeordnet werden

> Du bestellst Material, der Lieferant schickt die Rechnung per Mail – und das Programm hat sie schon analysiert, dem Lieferanten zugeordnet und an die Bestellung gehängt, bevor du die Mail überhaupt gelesen hast.

### 📱 Mobile App – das Werkzeug für die Baustelle

Die mobile PWA läuft auf **jedem Smartphone im Browser** – kein App Store nötig:

- **Zeiterfassung:** 3-Schritt-Wizard (Projekt → Kategorie → Arbeitsgang), Start/Stop, Projekt-Wechsel mit automatischer Umbuchung
- **Bautagebuch mit Fotos:** Direkt auf der Baustelle Fotos schießen, mit Zeitstempel und Mitarbeitername ins Projekt-Tagebuch laden
- **Kunden- & Lieferanten-Adressen:** Komplettes Adressbuch mit **Click-to-Call**, **SMS**, **E-Mail** und **Google-Maps-Navigation** direkt zur Baustelle oder zum Lieferanten
- **Lieferscheine scannen:** Kamera öffnen, Lieferschein fotografieren, **Perspektivkorrektur** mit Eckpunkten justieren, KI analysiert automatisch Nummer, Datum und Bestellreferenz
- **Reklamation erstellen:** Problem beschreiben, Beweisfotos schießen, mit Lieferschein verknüpfen – alles vom Handy aus
- **Angebote einsehen:** Angebots-Details, Fotos und Bautagebuch auch für Angebote verfügbar
- **Teamkalender:** Wer ist im Urlaub? Wer ist krank? Farbcodierte Übersicht für Woche/Monat
- **Urlaub & Abwesenheiten:** Antrag stellen (Urlaub, Krankheit, Fortbildung, Zeitausgleich), Feiertag-Check, Genehmigungsworkflow
- **Salden-Übersicht:** Soll/Ist-Stunden, Überstunden, Resturlaub, Krankheitstage – alles auf einen Blick
- **Offline-First:** Alle Daten werden in IndexedDB gecacht. Zeitbuchungen, Fotos und Reklamationen werden lokal gespeichert und bei Verbindung automatisch synchronisiert

> Auf der Baustelle kein Internet? Kein Problem. Die App arbeitet offline weiter und synchronisiert alles, sobald wieder Empfang da ist.

### 📝 Dokumenteditor – vom Angebot zur Nachkalkulation in einer Kette

Der Dokumenteditor ist das Herzstück des Rechnungswesens. Hier entsteht alles – vom ersten Angebot bis zur Schlussrechnung:

**Der Workflow:** Du legst einmalig deine **Leistungen** an (z. B. „Fenster einbauen", „Putz auftragen") und ordnest jede Leistung einer **Produktkategorie** zu (z. B. „Fensterbau", „Putzarbeiten"). Im Dokumenteditor ziehst du diese Leistungen als Blöcke in dein Angebot – mit Menge, Einheit und Preis. Sobald das Angebot zum Projekt wird, weiß das System automatisch, welche Produktkategorien betroffen sind. Deine Mitarbeiter buchen ihre Zeiten auf genau diese Kategorien. Das Ergebnis: **Die Nachkalkulation läuft vollautomatisch** – geplante Stunden aus dem Angebot vs. tatsächlich gebuchte Stunden, aufgeschlüsselt nach Kategorie und Arbeitsgang.

- **Block-basierter Aufbau mit Drag & Drop:** Leistungspositionen, Textbausteine, Abschnittsüberschriften, Trennlinien – frei kombinierbar und per Drag & Drop sortierbar
- **Leistungskatalog → Produktkategorien → Zeiterfassung:** Jede Leistung ist an eine Produktkategorie gebunden. Wenn der Mitarbeiter auf der Baustelle Zeit bucht, wählt er dieselbe Kategorie – das System verbindet beides automatisch
- **Live-PDF-Vorschau:** Rechts neben dem Editor siehst du in Echtzeit das fertige PDF mit Firmenbriefkopf, Positionsnummern und Summenblock
- **7 Dokumenttypen:** Angebot, Rechnung, Teilrechnung, Abschlagsrechnung, Schlussrechnung, Gutschrift, Storno – alle aus einem Editor
- **Abschnitte mit Zwischensummen:** Positionen in Bauabschnitte gruppieren (z. B. „Erdgeschoss", „Dacharbeiten") – Zwischensumme wird automatisch berechnet
- **GAEB-Import:** Leistungsverzeichnisse im GAEB-XML-Format importieren – Positionen werden direkt als Blöcke angelegt
- **E-Mail-Versand mit ZUGFeRD:** Dokument direkt per E-Mail versenden – wahlweise als Standard-PDF oder ZUGFeRD/XRechnung (EN 16931)
- **GoBD-konforme Sperrung:** Gebuchte Rechnungen werden automatisch gesperrt – nur noch Storno möglich

> Du legst einmal deine Leistungen und Kategorien an – danach stellt sich das Programm von selbst ein: Angebote kalkulieren, Zeiten erfassen, Nachkalkulation auswerten. Alles hängt zusammen.

### 📂 CAD- & Excel-Dateien direkt aus dem Browser öffnen – wie ein gemeinsamer Cloudspeicher

Alle CAD-Zeichnungen und Excel-Dateien werden zentral auf dem Server gespeichert und sind **für alle Mitarbeiter sofort aufrufbar** – egal ob HiCAD, Tenado oder Excel:

- **Zentraler Netzwerkspeicher:** Alle Zeichnungen und Tabellen liegen auf einem gemeinsamen Netzlaufwerk (z. B. `\\Server\CADdrawings`). Jeder PC im Büro kann darauf zugreifen – kein manuelles Kopieren, keine veralteten Versionen
- **1-Klick-Öffnen aus dem ERP:** Ein Klick auf „Öffnen" im Projekt öffnet die Datei direkt im richtigen Programm – HiCAD, Tenado oder Excel starten automatisch mit der richtigen Datei. Kein Suchen im Datei-Explorer
- **openfile://-Protokoll:** Das ERP nutzt ein eigenes Protokoll, das der mitgelieferte **OpenFileLauncher** auf jedem PC registriert. Er mappt das Netzlaufwerk automatisch auf den richtigen Laufwerksbuchstaben (z. B. Z:) und übergibt den Pfad dem passenden Programm
- **Einmal installieren, überall nutzen:** Der Launcher wird einmalig per `INSTALL.bat` auf jedem Arbeits-PC eingerichtet (< 1 Minute). Danach funktioniert das Öffnen auf Knopfdruck – für alle Mitarbeiter gleichzeitig
- **Automatisches Laufwerk-Mapping:** Ist das Netzlaufwerk bereits verbunden, erkennt der Launcher es automatisch. Ist es noch nicht gemappt, verbindet er es selbstständig im Hintergrund

> Beispiel: Der Projektleiter im Büro klickt in der Projektansicht auf eine Zeichnung – HiCAD öffnet sie direkt. Der Kollege am Nebenschreibtisch sieht dieselbe Datei, weil beide auf denselben zentralen Speicher zugreifen. Wie Google Drive – nur lokal, schnell und ohne Cloud-Abhängigkeit.

### 🎨 Formular-Designer – eigene Vorlagen wie in Canva

Jede Firma hat ihren eigenen Stil. Im Formular-Designer gestaltest du deine **Dokumentvorlagen pixelgenau** auf einer DIN-A4-Arbeitsfläche:

- **Canva-ähnlicher Editor:** Elemente frei auf der Seite platzieren – per Drag & Drop verschieben, an Kanten und Hilfslinien einrasten lassen, Größe per Anfasser ändern
- **12 Elementtypen:** Logo, Überschriften, Freitext, Adressfeld, Kundennummer, Dokumentnummer, Datum, Projektnummer, Leistungstabelle, Seitenzahl und mehr
- **Platzhalter mit Live-Vorschau:** `{{KUNDENNAME}}`, `{{BAUVORHABEN}}`, `{{DATUM}}` etc. – werden in der Vorschau mit echten Daten aus der Datenbank gefüllt
- **Mehrseitige Vorlagen:** Seite 1 mit Briefkopf und Logo, Folgeseiten nur mit Tabelle und Seitenzahl – unabhängig gestaltbar
- **Vorlagen-Bibliothek:** Templates speichern, duplizieren und verschiedenen Dokumenttypen zuweisen (ein Template für Angebote, ein anderes für Rechnungen)
- **Magnetische Hilfslinien:** Automatisches Einrasten an Kanten, Mitten und Nachbar-Elementen für saubere Ausrichtung
- **Undo/Redo:** Bis zu 50 Schritte rückgängig machen (Strg+Z)

> Einmal das Firmen-Layout gestalten – danach werden alle Angebote, Rechnungen und Lieferscheine automatisch in deinem Corporate Design erzeugt.

---

## 📋 Weitere Module

| Modul | Highlights |
|-------|-----------|
| **🛒 Bestellwesen** | 4-Stufen-Kette (Anfrage → Rechnung), Kilogramm-Berechnung für Profile, Rechnungssplitting auf Projekte & Kostenstellen |
| **🏠 Mietverwaltung** | Jahres-Nebenkostenabrechnung, Zählerstanderfassung (Wasser, Strom, Gas), PDF für Mieter |
| **📊 Controlling-Dashboard** | Echtzeit-KPIs, monatlicher Umsatzverlauf mit Vorjahresvergleich, Projekt-Heatmap nach PLZ |

---

## 🔒 Datenschutz & KI-Hinweis

> **Wichtig:** Dieses ERP verarbeitet sensible Geschäftsdaten (Rechnungen, Kundendaten, Mitarbeiterzeiten). Bitte beachte:

- **Lokaler Betrieb empfohlen:** Für maximalen Datenschutz das System im eigenen Netzwerk betreiben – alle Daten bleiben auf deinem Server
- **Google Gemini API:** Die KI-Features (Chat-Assistent, Dokumentenanalyse, E-Mail-Polishing) senden Daten an die Google Gemini API. Stelle sicher, dass du die **EU-Region** verwendest und das **KI-Training auf deinen Daten deaktivierst** (Opt-out in der Google Cloud Console). Gemini-Anfragen über die kostenpflichtige API werden laut Google standardmäßig **nicht** für das Modelltraining verwendet – prüfe dennoch deine Vertragsbedingungen
- **Ohne KI nutzbar:** Alle Kernfunktionen (Rechnungswesen, Zeiterfassung, Bestellwesen) funktionieren **vollständig ohne KI**. Der Gemini-API-Key ist optional – ohne ihn werden die KI-Features einfach deaktiviert
- **DSGVO:** Bei personenbezogenen Daten (Mitarbeiterzeiten, Kundenadressen) gelten die üblichen DSGVO-Pflichten. Da das System Self-Hosted ist, behältst du die volle Kontrolle über deine Daten

---

## 🏗️ Architektur

```mermaid
graph TB
    subgraph Frontend
        PC["🖥️ Desktop-Frontend<br/>(React + Vite + Tailwind)"]
        Mobile["📱 Mobile PWA<br/>(React Zeiterfassung)"]
        KI["🧠 KI-Hilfe Chat<br/>(schwebendes Widget)"]
    end

    subgraph Backend["Spring Boot Backend"]
        API["REST API<br/>(56 Controller)"]
        Services["Business Logic<br/>(84 Services)"]
        Domain["Domain Model<br/>(90 Entities)"]
        RAG["🔍 Local RAG Service<br/>(Vektor-Index im RAM)"]
    end

    subgraph Extern["Externe Dienste"]
        AI["🤖 Google Gemini AI<br/>(Chat, OCR, Embedding)"]
        Search["🌐 Google Search<br/>(Grounding)"]
        IMAP["📧 IMAP E-Mail-Server"]
    end

    subgraph Daten["Datenspeicher"]
        MariaDB["🐬 MariaDB"]
        FS["📁 Dateisystem (uploads/)"]
        Cache["📦 RAG-Cache (.json)"]
    end

    PC --> API
    Mobile --> API
    KI --> API
    API --> Services
    Services --> Domain
    Domain --> MariaDB
    Services --> FS
    Services --> AI
    Services --> IMAP
    RAG --> AI
    RAG --> Cache
    Services --> RAG
    AI --> Search
```

---

## 🛠️ Tech-Stack

| Schicht | Technologie |
|---------|-------------|
| **Backend** | Java 23, Spring Boot 3.2.5, JPA/Hibernate, Flyway |
| **Datenbank** | MariaDB 11 |
| **Desktop-Frontend** | React 18 + TypeScript + Vite + Tailwind CSS |
| **Mobile App** | React PWA (Offline-fähig via IndexedDB) |
| **KI-Assistent** | Google Gemini API (Chat, Vision, Embedding), Google Search Grounding |
| **RAG / Vektorsuche** | Lokaler In-Memory-Vektorindex mit Cosine-Similarity (kein externer Service nötig) |
| **PDF-Generierung** | OpenPDF, Apache PDFBox, Mustang (ZUGFeRD) |
| **E-Mail** | Jakarta Mail (IMAP/SMTP) |
| **Build** | Maven (Backend), Vite (Frontend) |
| **Deployment** | jpackage (Windows EXE), Docker (optional) |

---

## 🚀 Schnellstart

### 💿 Für Endanwender: 1-Klick-Installation (Windows)

**Keine Vorkenntnisse nötig. Kein Java, kein Docker, keine Datenbank.**

1. **[⬇️ ERP-Handwerk-<Version>.exe herunterladen](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/releases)**
2. Doppelklick → Installieren
3. Startmenü → **„ERP Handwerk"** → Fertig ✅

> Der Installer enthält alles: eigene Java-Laufzeitumgebung, eingebettete Datenbank und die komplette Anwendung.

### 🐳 Für Entwickler: Docker

```bash
git clone https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe.git
cd ERP-System-fuer-Handwerksbetriebe
docker compose up -d --build
```

### 🔧 Weitere Optionen

Detaillierte Anleitungen für alle Installationsvarianten (Docker, manuelle Installation, Installer selbst bauen):

📖 **[Vollständige Installationsanleitung →](docs/INSTALLATION.md)**

---

## 🏗️ Deployment & Betrieb

Das Handwerkerprogramm kann **lokal im Firmennetzwerk** oder auf einem **Cloud-Server** betrieben werden.

📖 **[Vollständige Deployment-Anleitung → (LAN, Tailscale VPN, Caddy HTTPS, Cloudflare Tunnel)](docs/INSTALLATION.md#deployment--betrieb)**

---

## �📁 Projektstruktur

```
Handwerkerprogramm/
├── src/main/java/.../kalkulationsprogramm/
│   ├── controller/          # 56 REST-Controller
│   ├── service/             # 84 Business-Services
│   │   ├── KiHilfeService         # KI-Chat mit RAG + Google Search
│   │   ├── LocalRagService        # Vektor-Embedding & Similarity Search
│   │   ├── GeminiDokumentAnalyseService  # KI-Rechnungserkennung
│   │   ├── ProduktkategorieService       # Zeitkalkulation (Regression)
│   │   └── EmailAiService         # KI-E-Mail-Polishing
│   ├── repository/          # Spring Data Repositories
│   ├── domain/              # 90 JPA-Entities
│   ├── dto/                 # API-Datenmodelle
│   ├── config/              # Spring-Konfiguration
│   └── mapper/              # DTO ↔ Entity Mapper
│
├── react-pc-frontend/       # 🖥️ Desktop-UI (31 Seiten)
│   └── src/
│       ├── pages/           # Editoren, Dashboards, Tools
│       └── components/
│           └── KiHilfeChat  # 🧠 Schwebendes KI-Widget
│
├── react-zeiterfassung/     # 📱 Mobile PWA (18 Seiten)
│   └── src/pages/           # Stempeluhr, Urlaub, Salden
│
├── docs/                    # 📚 Dokumentation
│   ├── GOBD_COMPLIANCE.md   # GoBD-Konformität
│   ├── RECHNUNGSWESEN.md    # Rechnungsprozesse
│   ├── DOKUMENTEN_LIFECYCLE.md
│   └── ...
│
├── deployment/              # 🚀 Deployment-Scripts
│   └── scripts/             # Backup, Autostart, Restart
│
└── docker-compose.yml       # Qdrant Vector DB (optional)
```

---

## 📚 Dokumentation

Die vollständige Dokumentation befindet sich im [`docs/`](docs/) Verzeichnis:

| Dokument | Beschreibung |
|----------|--------------|
| [BUSINESS_CASES.md](docs/BUSINESS_CASES.md) | Geschäftsnutzen aller Module |
| [GOBD_COMPLIANCE.md](docs/GOBD_COMPLIANCE.md) | GoBD-Konformität & Audit-Trail |
| [RECHNUNGSWESEN.md](docs/RECHNUNGSWESEN.md) | Kompletter Rechnungsprozess |
| [DOKUMENTEN_LIFECYCLE.md](docs/DOKUMENTEN_LIFECYCLE.md) | Lebenszyklus aller Dokumente |
| [ZEITERFASSUNG_WORKFLOWS.md](docs/ZEITERFASSUNG_WORKFLOWS.md) | Zeiterfassung Online & Offline |
| [DOKUMENTATIONSPLAN.md](docs/DOKUMENTATIONSPLAN.md) | Übersicht & Roadmap der Docs |

Architektur-Diagramme (draw.io) liegen in [`docs/Dokumentation/`](docs/Dokumentation/).

---

## 📊 Projekt in Zahlen

| Metrik | Wert |
|--------|------|
| REST-Controller | 56 |
| Business-Services | 84 |
| Domain-Entities | 90 |
| Desktop-Seiten (PC) | 31 |
| Mobile-Seiten (PWA) | 18 |
| Dokumentationen | 7 |
| Architektur-Diagramme | 7 |

---

## 🤝 Beitragen

Beiträge sind willkommen! Dieses Projekt lebt davon, dass Handwerksbetriebe und Entwickler zusammenarbeiten.

1. Fork erstellen
2. Feature-Branch anlegen (`git checkout -b feature/mein-feature`)
3. Änderungen committen (`git commit -m 'Neues Feature hinzugefügt'`)
4. Branch pushen (`git push origin feature/mein-feature`)
5. Pull Request erstellen

### Entwicklungsrichtlinien

- **Backend:** Java-Konventionen, Constructor Injection, Tests sind Pflicht
- **Frontend:** React + TypeScript, Rose/Rot-Farbschema, deutsche UI-Texte
- **Tests:** JUnit 5 + Mockito (Backend), Vitest + Testing Library (Frontend)
- **Sicherheit:** OWASP Top 10, parametrisierte Queries, Input-Validierung

---

## 📜 Lizenz

Dieses Projekt steht unter der [MIT-Lizenz](LICENSE).

Eine vollständige Auflistung aller verwendeten Drittanbieter-Bibliotheken und deren Lizenzen
findet sich in [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).

---

<p align="center">
  Gebaut mit ❤️, ☕ und KI-Unterstützung<br/>
  <sub>Von einem Wirtschaftsinformatik-Studenten für den Handwerksbetrieb seines Vaters – jetzt Open Source für alle.</sub>
</p>
