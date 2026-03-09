<p align="center">
  <img src="assets/c__Users_User_Documents_GitHub_Kalkulationsprogramm_src_main_resources_static_firmenlogo.png" alt="Logo" width="120" />
</p>

<h1 align="center">Handwerkerprogramm</h1>

<p align="center">
  <strong>Open-Source-ERP für Handwerksbetriebe</strong><br/>
  Von der Angebotserstellung über die Zeiterfassung bis zur Schlussrechnung – alles in einer Anwendung.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-23-orange?logo=openjdk" alt="Java 23" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?logo=spring-boot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react" alt="React" />
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript" alt="TypeScript" />
  <img src="https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/Lizenz-MIT-green" alt="Lizenz" />
</p>

---

## 🙋 Über dieses Projekt

Ich habe dieses Programm ursprünglich entwickelt, um meinem Vater in seinem Handwerksbetrieb zu helfen. Was als kleines Tool zur Projektkalkulation begann, ist über die Zeit zu einem vollständigen ERP-System gewachsen – mit Rechnungswesen, Zeiterfassung, E-Mail-Integration und vielem mehr.

Ich studiere **Wirtschaftsinformatik im 4. Semester** und habe das Projekt durch **Pair-Programming mit Claude (AI)** entwickelt. Jetzt möchte ich es als Open Source verfügbar machen, damit auch andere Handwerksbetriebe davon profitieren können.

---

## ✨ Features

### 📊 Projektkalkulation & Controlling
- Hierarchische Produktkategorien (z. B. „Dach > Flachdach") mit Verrechnungseinheiten (m², kg, Stück, lfd. Meter)
- **Erfolgsanalyse-Dashboard** mit Echtzeit-KPIs: Gewinn, Material-/Arbeitskosten, Top-10-Kunden
- Monatlicher Umsatzverlauf mit Vorjahresvergleich
- Regionale Projekt-Heatmap nach PLZ

### 📄 Rechnungswesen (GoBD-konform)
- Komplette Dokumentenkette: **Angebot → Auftragsbestätigung → Abschlagsrechnungen → Schlussrechnung**
- GoBD-konforme Unveränderbarkeit, Löschverbot, Storno-Verfahren und lückenlose Nummerierung
- Automatische MwSt-Berechnung, Rechnungsadress-Override, PDF-Generierung
- **ZUGFeRD/XRechnung**-Integration (E-Rechnungen erstellen & lesen)
- Offene-Posten-Verwaltung mit Mahnwesen (3 Stufen)

### 📧 E-Mail-Zentrale
- IMAP-Import alle 60 Sekunden mit Spam-Filter und Thread-Erkennung
- **Automatische Zuordnung** zu Kunden, Lieferanten und Projekten
- Anhänge werden als Lieferantendokumente analysiert (ZUGFeRD / KI)
- Signatur- und Abwesenheitsnotiz-Verwaltung
- KI-gestützte E-Mail-Formulierung (Ollama / Gemini)

### ⏱️ Mobile Zeiterfassung (PWA)
- Start/Stop-Stempelung auf Projekt + Kategorie + Arbeitsgang
- **Offline-fähig** mit automatischem Sync bei Reconnect
- Saldenauswertung (Soll/Ist, Überstunden, Zeitausgleich)
- Urlaubsanträge mit Genehmigungsworkflow
- Feiertage (Bayern) automatisch berücksichtigt
- GoBD-konformer **Audit-Trail** für jede Buchungsänderung

### 🤖 Intelligente Dokumentenverarbeitung
- Automatische Erkennung: ZUGFeRD → XML → **Gemini AI** (OCR-Fallback)
- Lieferantenrechnungen mit Confidence-Score und Verifizierungs-Flag
- Prozentuale Projektzuordnung für Eingangsrechnungen
- Gutschrift-Rechnungs-Verknüpfung

### 🛒 Bestellwesen
- Offene Artikel pro Projekt sammeln und gruppiert nach Lieferant bestellen
- Kilogramm-Berechnung für Werkstoffe, Schnittformen und Winkel für Profile
- Lieferantenpreise hinterlegen, Bestellungs-PDF generieren

### 🏠 Mietverwaltung
- Jahres-Nebenkostenabrechnung für Mietobjekte
- Kostenstellen-Verteilung nach Verbrauch oder Fläche
- Zählerstanderfassung (Wasser, Strom, Gas) mit Vorjahresvergleich
- PDF-Generierung für Mieter

### 📝 Dokumentengenerator (WYSIWYG)
- Template-basierte Dokumente mit Platzhaltern (`{{KUNDENNAME}}`, `{{LEISTUNGEN_TABELLE}}`, ...)
- Rich-Text-Editor mit Formatierung, Bildern und Tabellen
- Professionelle PDF-Ausgabe mit Firmenbriefkopf

---

## 🏗️ Architektur

```mermaid
graph TB
    subgraph Frontend
        PC["🖥️ Desktop-Frontend<br/>(React + Vite + Tailwind)"]
        Mobile["📱 Mobile PWA<br/>(React Zeiterfassung)"]
        TB["📝 Textbausteine-Editor<br/>(React)"]
    end

    subgraph Backend["Spring Boot Backend"]
        API["REST API<br/>(56 Controller)"]
        Services["Business Logic<br/>(84 Services)"]
        Domain["Domain Model<br/>(109 Entities)"]
    end

    subgraph Extern["Externe Dienste"]
        AI["🤖 Google Gemini AI"]
        IMAP["📧 IMAP E-Mail-Server"]
        Qdrant["🔍 Qdrant (Vector DB)"]
    end

    subgraph Daten["Datenspeicher"]
        MySQL["🐬 MySQL"]
        FS["📁 Dateisystem (uploads/)"]
    end

    PC --> API
    Mobile --> API
    TB --> API
    API --> Services
    Services --> Domain
    Domain --> MySQL
    Services --> FS
    Services --> AI
    Services --> IMAP
    Services --> Qdrant
```

---

## 🛠️ Tech-Stack

| Schicht | Technologie |
|---------|-------------|
| **Backend** | Java 23, Spring Boot 3.2.5, JPA/Hibernate, Flyway |
| **Datenbank** | MySQL 8 |
| **Desktop-Frontend** | React 18 + TypeScript + Vite + Tailwind CSS |
| **Mobile App** | React PWA (Offline-fähig via IndexedDB) |
| **PDF-Generierung** | OpenPDF, Apache PDFBox, Mustang (ZUGFeRD) |
| **KI-Integration** | Google Gemini API, Ollama (optional) |
| **E-Mail** | Jakarta Mail (IMAP/SMTP) |
| **Suche** | Qdrant Vector Database |
| **Build** | Maven (Backend), Vite (Frontend) |
| **Deployment** | jpackage (Windows EXE), Docker (optional) |

---

## 🚀 Schnellstart

### Voraussetzungen

- **Java 23** (JDK) – [Eclipse Adoptium](https://adoptium.net/)
- **MySQL 8** – Datenbank `kalkulationsprogramm_db` anlegen
- **Node.js 18+** – für die React-Frontends

### 1. Repository klonen

```bash
git clone https://github.com/Winfo2024Kuhn/Handwerkerprogramm.git
cd Handwerkerprogramm
```

### 2. Datenbank konfigurieren

Erstelle `src/main/resources/application-local.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/kalkulationsprogramm_db
spring.datasource.username=DEIN_USER
spring.datasource.password=DEIN_PASSWORT
```

### 3. Backend starten

```bash
./mvnw spring-boot:run
```

### 4. Desktop-Frontend starten

```bash
cd react-pc-frontend
npm install
npm run dev
```

### 5. Mobile Zeiterfassung starten (optional)

```bash
cd react-zeiterfassung
npm install
npm run dev
```

---

## 📁 Projektstruktur

```
Handwerkerprogramm/
├── src/main/java/.../kalkulationsprogramm/
│   ├── controller/          # 56 REST-Controller
│   ├── service/             # 84 Business-Services
│   ├── repository/          # Spring Data Repositories
│   ├── domain/              # 109 JPA-Entities
│   ├── dto/                 # API-Datenmodelle
│   ├── config/              # Spring-Konfiguration
│   └── mapper/              # DTO ↔ Entity Mapper
│
├── react-pc-frontend/       # 🖥️ Desktop-UI (31 Seiten)
│   └── src/pages/           # Editoren, Dashboards, Tools
│
├── react-zeiterfassung/     # 📱 Mobile PWA (18 Seiten)
│   └── src/pages/           # Stempeluhr, Urlaub, Salden
│
├── react-textbausteine/     # 📝 Textbaustein-Editor
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
└── docker-compose.yml       # Qdrant Vector DB
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
| Domain-Entities | 109 |
| Desktop-Seiten (PC) | 31 |
| Mobile-Seiten (PWA) | 18 |
| Dokumentationen | 7 |
| Architektur-Diagramme | 7 |

---

## 🔧 Build & Deployment

```bash
# Tests ausführen
./mvnw test

# JAR bauen
./mvnw clean package

# Windows-Installer erzeugen
./mvnw package
./mvnw jpackage:jpackage
# → Installer liegt in target/installer/

# Frontend für Produktion bauen
cd react-pc-frontend && npm run build
```

Detaillierte Deployment-Anleitung: [`deployment/README.md`](deployment/README.md)

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

Dieses Projekt ist Open Source. Details folgen.

---

<p align="center">
  Gebaut mit ❤️, ☕ und KI-Unterstützung
</p>
