# Installation & Einrichtung

> **Ziel:** In 2 Minuten von Download bis zum laufenden ERP-System – ohne Vorkenntnisse.

---

## Variante 1: Windows-Installer (empfohlen für Endanwender)

Die einfachste Methode. **Keine Voraussetzungen** – kein Java, kein Docker, keine Datenbank.

### Schritt 1 – Herunterladen

Lade den neuesten Installer von der [**Releases-Seite**](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/releases) herunter:

```
ERP-Handwerk-1.0.0.exe
```

### Schritt 2 – Installieren

1. Doppelklick auf `ERP-Handwerk-1.0.0.exe`
2. Installationsordner wählen (Standard: `C:\Program Files\ERP-Handwerk`)
3. **„Installieren"** klicken

Der Installer erstellt automatisch:
- ✅ Startmenü-Eintrag unter **„ERP Handwerk"**
- ✅ Desktop-Verknüpfung
- ✅ Eigene Java-Laufzeitumgebung (JRE)
- ✅ Eingebettete H2-Datenbank

### Schritt 3 – Starten

Startmenü → **„ERP Handwerk"** → Anwendung startet und der Browser öffnet sich automatisch.

**Standard-Zugangsdaten:**

| Feld | Wert |
|------|------|
| Benutzer | `Marvin` |
| Passwort | `123456` |

> ⚠️ **Ändere die Zugangsdaten nach dem ersten Login!**

### Wo werden meine Daten gespeichert?

| Daten | Speicherort |
|-------|-------------|
| Datenbank | `%USERPROFILE%\ERP-Handwerk\datenbank.mv.db` |
| Uploads | Im Installationsverzeichnis unter `uploads\` |

### Deinstallation

Windows → Einstellungen → Apps → **„ERP-Handwerk"** → Deinstallieren

> 💡 Die Datenbank unter `%USERPROFILE%\ERP-Handwerk\` bleibt nach der Deinstallation erhalten. Lösche den Ordner manuell, wenn du alle Daten entfernen möchtest.

---

## Variante 2: Docker (empfohlen für Entwickler & Server)

Benötigt: [Docker Desktop](https://www.docker.com/products/docker-desktop/)

```bash
git clone https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe.git
cd ERP-System-fuer-Handwerksbetriebe
docker compose up -d --build
```

Oder unter Windows einfach `start-docker.bat` doppelklicken.

**Was passiert automatisch:**
- MySQL 8 wird heruntergeladen und konfiguriert
- Datenbank und Tabellen werden erstellt
- Qdrant (Vektor-DB für KI) wird gestartet
- Die Anwendung startet auf `http://localhost:8080`

**Stoppen:** `stop-docker.bat` oder `docker compose down`

---

## Variante 3: Manuelle Installation (für Entwickler)

### Voraussetzungen

| Komponente | Version | Download |
|------------|---------|----------|
| Java (JDK) | 23+ | [Eclipse Adoptium](https://adoptium.net/) |
| MySQL | 8+ | [MySQL Downloads](https://dev.mysql.com/downloads/) |
| Node.js | 18+ | [nodejs.org](https://nodejs.org/) (nur für Frontend-Entwicklung) |

### 1. Repository klonen

```bash
git clone https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe.git
cd ERP-System-fuer-Handwerksbetriebe
```

### 2. MySQL-Datenbank einrichten

```sql
CREATE DATABASE kalkulationsprogramm_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_german2_ci;
CREATE USER 'erp'@'localhost' IDENTIFIED BY 'DEIN_DB_PASSWORT';
GRANT ALL PRIVILEGES ON kalkulationsprogramm_db.* TO 'erp'@'localhost';
```

### 3. Konfiguration erstellen

Erstelle `src/main/resources/application-local.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/kalkulationsprogramm_db?useUnicode=true&characterEncoding=UTF-8
spring.datasource.username=erp
spring.datasource.password=DEIN_DB_PASSWORT
```

### 4. Backend starten

```bash
./mvnw spring-boot:run
```

### 5. Desktop-Frontend starten (optional, für Entwicklung)

```bash
cd react-pc-frontend
npm install
npm run dev
```

### 6. Mobile Zeiterfassung starten (optional)

```bash
cd react-zeiterfassung
npm install
npm run dev
```

---

## Installer selbst bauen (für Entwickler)

Um den Windows-Installer selbst zu erstellen:

### Voraussetzungen auf der Build-Maschine

| Tool | Version | Download |
|------|---------|----------|
| JDK | 23+ | [Eclipse Adoptium](https://adoptium.net/) |
| WiX Toolset | 3.x | [github.com/wixtoolset/wix3/releases](https://github.com/wixtoolset/wix3/releases) |

### Build-Schritte

```bash
# 1. JAR bauen
./mvnw clean package -DskipTests

# 2. Staging-Verzeichnis erstellen
mkdir target/jpackage-input
cp target/Kalkulationsprogramm-1.0.0.jar target/jpackage-input/

# 3. Installer erstellen
./mvnw jpackage:jpackage
```

Oder einfach: `build-installer.bat` doppelklicken.

Der Installer liegt dann unter: `target/installer/ERP-Handwerk-1.0.0.exe`

### Was der Installer enthält

| Komponente | Beschreibung |
|------------|--------------|
| JRE 23 | Eigene Java-Laufzeitumgebung (~170 MB) |
| Spring Boot App | Backend + eingebettetes Frontend |
| H2-Datenbank | Eingebettete Datenbank (kein MySQL nötig) |
| JVM-Parameter | `-Dspring.profiles.active=h2 -Xmx512m` |

---

## Häufige Fragen (FAQ)

### Kann ich von H2 auf MySQL wechseln?

Ja. Die H2-Datenbank ist für den Einstieg gedacht. Für den produktiven Einsatz mit mehreren Nutzern empfehlen wir MySQL:

1. MySQL installieren und Datenbank anlegen (siehe Variante 3)
2. `application-local.properties` erstellen
3. Die Anwendung ohne `h2`-Profil starten:
   ```bash
   java -jar Kalkulationsprogramm-1.0.0.jar --spring.profiles.active=local
   ```

### Port 8080 ist belegt – was tun?

Starte die Anwendung mit einem anderen Port:
```bash
java -jar Kalkulationsprogramm-1.0.0.jar --server.port=9090
```

### Wie sichere ich meine Daten?

- **H2-Modus:** Kopiere den Ordner `%USERPROFILE%\ERP-Handwerk\` regelmäßig auf ein Backup-Medium
- **MySQL-Modus:** Verwende `mysqldump` für regelmäßige Backups
- **Docker-Modus:** Die Daten liegen in Docker Volumes – sichere diese über `docker cp` oder Volume-Backups

### Kann ich das Programm im Netzwerk nutzen?

Ja! Andere Geräte im gleichen Netzwerk erreichen die Anwendung über:
```
http://[SERVER-IP]:8080
```
Unter Windows findest du die IP mit `ipconfig`. Stelle sicher, dass Port 8080 in der Windows-Firewall freigegeben ist.
