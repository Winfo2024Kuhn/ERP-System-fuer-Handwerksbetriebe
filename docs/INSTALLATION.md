# Installation & Einrichtung

> **Ziel:** In 2 Minuten von Download bis zum laufenden ERP-System – ohne Vorkenntnisse.

---

## Variante 1: Windows-Installer (empfohlen für Endanwender)

Die einfachste Methode. **Keine Voraussetzungen** – kein Java, kein Docker, keine Datenbank.

### Schritt 1 – Herunterladen

Lade den neuesten Installer von der [**Releases-Seite**](https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe/releases) herunter:

```
ERP-Handwerk-<Version>.exe
```

### Schritt 2 – Installieren

1. Doppelklick auf `ERP-Handwerk-<Version>.exe`
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
- MariaDB 11 wird heruntergeladen und konfiguriert
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
| MariaDB | 11+ | [MariaDB Downloads](https://mariadb.org/download/) |
| Node.js | 18+ | [nodejs.org](https://nodejs.org/) (nur für Frontend-Entwicklung) |

### 1. Repository klonen

```bash
git clone https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe.git
cd ERP-System-fuer-Handwerksbetriebe
```

### 2. Datenbank einrichten (MariaDB oder MySQL)

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
# MariaDB (Standard)
spring.datasource.url=jdbc:mariadb://localhost:3306/kalkulationsprogramm_db?useUnicode=true&characterEncoding=UTF-8

# oder MySQL 8+
# spring.datasource.url=jdbc:mysql://localhost:3306/kalkulationsprogramm_db?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC

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
cp target/Kalkulationsprogramm-<Version>.jar target/jpackage-input/

# 3. Installer erstellen
./mvnw jpackage:jpackage
```

Oder einfach: `build-installer.bat` doppelklicken.

Der Installer liegt dann unter: `target/installer/ERP-Handwerk-<Version>.exe`

### Was der Installer enthält

| Komponente | Beschreibung |
|------------|--------------|
| JRE 23 | Eigene Java-Laufzeitumgebung (~170 MB) |
| Spring Boot App | Backend + eingebettetes Frontend |
| H2-Datenbank | Eingebettete Datenbank (kein MariaDB nötig) |
| JVM-Parameter | `-Dspring.profiles.active=h2 -Xmx512m` |

---

## Häufige Fragen (FAQ)

### Kann ich von H2 auf MariaDB wechseln?

Ja. Die H2-Datenbank ist für den Einstieg gedacht. Für den produktiven Einsatz mit mehreren Nutzern empfehlen wir MariaDB:

1. MariaDB installieren und Datenbank anlegen (siehe Variante 3)
2. `application-local.properties` erstellen
3. Die Anwendung ohne `h2`-Profil starten:
   ```bash
java -jar Kalkulationsprogramm-<Version>.jar --spring.profiles.active=local
   ```

### Port 8080 ist belegt – was tun?

Starte die Anwendung mit einem anderen Port:
```bash
java -jar Kalkulationsprogramm-<Version>.jar --server.port=9090
```

### Wie sichere ich meine Daten?

- **H2-Modus:** Kopiere den Ordner `%USERPROFILE%\ERP-Handwerk\` regelmäßig auf ein Backup-Medium
- **MariaDB-Modus:** Verwende `mariadb-dump` oder `mysqldump` für regelmäßige Backups
- **Docker-Modus:** Die Daten liegen in Docker Volumes – sichere diese über `docker cp` oder Volume-Backups

### Kann ich das Programm im Netzwerk nutzen?

Ja! Andere Geräte im gleichen Netzwerk erreichen die Anwendung über:
```
http://[SERVER-IP]:8080
```
Unter Windows findest du die IP mit `ipconfig`. Stelle sicher, dass Port 8080 in der Windows-Firewall freigegeben ist.

---

## Deployment & Betrieb

Das Handwerkerprogramm kann auf zwei Arten betrieben werden: **lokal im Firmennetzwerk** oder auf einem **Cloud-Server**. Beide Varianten werden hier erklärt.

### Gemeinsame Voraussetzungen

| Komponente | Version | Hinweis |
|------------|---------|---------|
| Java (JDK) | 23+ | [Eclipse Adoptium](https://adoptium.net/) |
| MariaDB | 11+ | Datenbank `kalkulationsprogramm_db` anlegen |
| Node.js | 18+ | Nur für Frontend-Build nötig |

### Backend für Produktion vorbereiten

```bash
# 1. JAR bauen (inkl. Tests)
./mvnw clean package

# 2. Frontend für Produktion bauen
cd react-pc-frontend && npm install && npm run build
cd ../react-zeiterfassung && npm install && npm run build
```

Das fertige JAR liegt unter `target/kalkulationsprogramm-*.jar`.

### Authentifizierung konfigurieren

Die API ist **standardmäßig per HTTP Basic Auth geschützt**. Admin-Zugangsdaten werden über Umgebungsvariablen gesetzt:

```powershell
# Windows (PowerShell)
$env:APP_ADMIN_USER = "meinBenutzername"
$env:APP_ADMIN_PASS = "sicheresPasswort123!"
```

```bash
# Linux / macOS
export APP_ADMIN_USER="meinBenutzername"
export APP_ADMIN_PASS="sicheresPasswort123!"
```

> ⚠️ **Wichtig:** Ändere unbedingt die Standard-Zugangsdaten (`admin` / `changeme`) vor dem produktiven Einsatz!

---

### Option A: Lokaler Betrieb (Firmenserver / eigener Rechner)

Ideal, wenn alle Nutzer im **gleichen Netzwerk (LAN/WLAN)** arbeiten – z. B. im Büro oder in der Werkstatt.

#### 1. Datenbank einrichten (MariaDB oder MySQL)

```sql
CREATE DATABASE kalkulationsprogramm_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_german2_ci;
CREATE USER 'erp'@'localhost' IDENTIFIED BY 'DEIN_DB_PASSWORT';
GRANT ALL PRIVILEGES ON kalkulationsprogramm_db.* TO 'erp'@'localhost';
```

#### 2. Konfiguration anpassen

Erstelle `src/main/resources/application-local.properties`:

```properties
# MariaDB (Standard)
spring.datasource.url=jdbc:mariadb://localhost:3306/kalkulationsprogramm_db?useUnicode=true&characterEncoding=UTF-8

# oder MySQL 8+
# spring.datasource.url=jdbc:mysql://localhost:3306/kalkulationsprogramm_db?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC

spring.datasource.username=erp
spring.datasource.password=DEIN_DB_PASSWORT
```

#### 3. Server starten

```powershell
# Umgebungsvariablen setzen
$env:APP_ADMIN_USER = "admin"
$env:APP_ADMIN_PASS = "deinSicheresPasswort"

# JAR starten
java -jar target/kalkulationsprogramm-*.jar --spring.profiles.active=local
```

#### 4. Zugriff im Netzwerk

| Nutzer | URL |
|--------|-----|
| Gleicher Rechner | `http://localhost:8080` |
| Anderer PC im LAN | `http://192.168.x.x:8080` (IP des Servers) |
| Zeiterfassung (Handy) | `http://192.168.x.x:8080/zeiterfassung` |

> 💡 **Tipp:** Unter Windows die IP mit `ipconfig` herausfinden. Stelle sicher, dass Port 8080 in der Windows-Firewall freigegeben ist.

---

### Option B: Cloud-Server (VPS / Cloudrechner)

Für den Zugriff **von unterwegs oder von Baustellen** – z. B. auf einem VPS bei Hetzner, Netcup oder DigitalOcean.

> ⚠️ **Sicherheitshinweis:** Einen Server ohne Absicherung ins Internet zu stellen ist gefährlich! Wähle mindestens **eine** der folgenden Absicherungen:

#### Variante 1: VPN mit Tailscale (empfohlen für Einsteiger)

Mit [Tailscale](https://tailscale.com/) wird ein privates Netzwerk aufgebaut – der Server ist **nicht öffentlich** erreichbar, nur über das VPN.

**Auf dem Cloud-Server:**
```bash
# Tailscale installieren (Ubuntu/Debian)
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up

# Handwerkerprogramm starten
java -jar kalkulationsprogramm-*.jar
```

**Auf jedem Client (PC, Handy):**
1. Tailscale App installieren ([tailscale.com/download](https://tailscale.com/download))
2. Mit dem gleichen Konto anmelden
3. Zugriff über die Tailscale-IP: `http://100.x.x.x:8080`

**Vorteile:** Einfachste Einrichtung, kein Port öffnen, automatische Verschlüsselung, kostenlos für kleine Teams (bis 100 Geräte).

**Optional – HTTPS innerhalb von Tailscale aktivieren:**
```bash
# Auf dem Server: Tailscale HTTPS-Zertifikat anfordern
sudo tailscale cert mein-server.tail-xxxx.ts.net

# Spring Boot mit HTTPS starten
java -jar kalkulationsprogramm-*.jar \
  --server.ssl.certificate=mein-server.tail-xxxx.ts.net.crt \
  --server.ssl.certificate-private-key=mein-server.tail-xxxx.ts.net.key \
  --server.port=8443
```

Dann erreichbar über: `https://mein-server.tail-xxxx.ts.net:8443`

---

#### Variante 2: HTTPS mit Reverse Proxy (für öffentlichen Zugriff)

Wenn der Server **öffentlich erreichbar** sein soll (z. B. für Kunden-Zeiterfassung), nutze einen Reverse Proxy mit automatischem SSL-Zertifikat.

**Mit Caddy (empfohlen – automatisches HTTPS):**

```bash
# Caddy installieren (Ubuntu/Debian)
sudo apt install -y caddy
```

Erstelle `/etc/caddy/Caddyfile`:
```
erp.meinefirma.de {
    reverse_proxy localhost:8080
}
```

```bash
sudo systemctl restart caddy
```

Caddy holt sich automatisch ein Let's-Encrypt-Zertifikat. Die Anwendung ist dann unter `https://erp.meinefirma.de` erreichbar.

> 📋 **Voraussetzung:** Eine Domain (z. B. `erp.meinefirma.de`) muss per DNS-A-Record auf die Server-IP zeigen.

**Zusätzliche Absicherung für öffentliche Server:**
- Starkes Admin-Passwort setzen (mind. 16 Zeichen)
- SSH nur mit Key-Login (Passwort-Login deaktivieren)
- Firewall: nur Port 80, 443 und SSH offen (`ufw allow 80,443,22/tcp`)
- Fail2Ban installieren gegen Brute-Force-Angriffe
- Regelmäßige Datenbank-Backups (siehe `deployment/scripts/backup-database.ps1`)

---

#### Variante 3: Cloudflare Tunnel (kein Port öffnen, kein VPN nötig)

[Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) macht den lokalen Server über eine Cloudflare-Domain erreichbar, **ohne Ports zu öffnen**.

```bash
# cloudflared installieren
curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg | sudo tee /usr/share/keyrings/cloudflare.gpg
sudo apt install cloudflared

# Tunnel erstellen und konfigurieren
cloudflared tunnel login
cloudflared tunnel create handwerkerprogramm
cloudflared tunnel route dns handwerkerprogramm erp.meinefirma.de

# Tunnel starten
cloudflared tunnel --url http://localhost:8080 run handwerkerprogramm
```

**Vorteile:** Kein Port öffnen, automatisches HTTPS, DDoS-Schutz inklusive, kostenloser Tarif verfügbar.

---

### Übersicht: Welche Variante passt zu mir?

| Kriterium | LAN (lokal) | Tailscale VPN | HTTPS + Reverse Proxy | Cloudflare Tunnel |
|-----------|:-----------:|:-------------:|:---------------------:|:-----------------:|
| Einrichtung | ⭐ Einfach | ⭐ Einfach | ⭐⭐ Mittel | ⭐⭐ Mittel |
| Kosten | Keine | Kostenlos | Domain nötig (~1 €/M.) | Domain nötig |
| Zugriff von unterwegs | ❌ | ✅ | ✅ | ✅ |
| Öffentlich erreichbar | ❌ | ❌ | ✅ | ✅ |
| HTTPS | Nicht nötig | Optional | ✅ Automatisch | ✅ Automatisch |
| Port öffnen | Nur LAN | Kein Port | Port 80 + 443 | Kein Port |
