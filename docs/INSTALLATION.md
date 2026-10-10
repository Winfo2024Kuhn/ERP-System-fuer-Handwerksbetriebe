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

Bei einer neuen Datenbank ohne vorkonfigurierten Admin legst du im Browser den ersten Benutzer an; dieser wird Administrator. Schließe die Ersteinrichtung im geschützten Netz ab, bevor die Anwendung über eine öffentliche Domain erreichbar wird. Es gibt keine fest eingebauten Standard-Zugangsdaten für diese Einrichtung.

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
Unter Windows findest du die IP mit `ipconfig`. Gib Port 8080 in der Firewall nur für die vorgesehenen Geräte im Firmennetz frei, nicht für das öffentliche Internet.

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

Die Desktop-API verwendet **Sitzungen nach Anmeldung mit Benutzername und Passwort sowie CSRF-Schutz**. HTTP Basic ist deaktiviert. Die mobile API prüft Mitarbeiter-Tokens und erlaubt nur ausdrücklich freigegebene Methoden und Routen. Details zu Wartezeiten, Proxy-Vertrauen und Grenzen: [Mobile Token-Sicherheit](MOBILE_TOKEN_SECURITY.md).

Für eine neue Datenbank kann der erste Admin über Umgebungsvariablen angelegt werden:

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

Die Variablen werden nur zur ersten Anlage verwendet, solange noch kein Login-Benutzer existiert; sie ändern kein bestehendes Passwort. Ohne konfigurierte Werte erfolgt die erste Registrierung im Browser. Schließe sie vor einer öffentlichen Freigabe ab und verwende eigene, starke Zugangsdaten.

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

> 💡 **Tipp:** Unter Windows die IP mit `ipconfig` herausfinden. Gib Port 8080 in der Firewall nur für die vorgesehenen Geräte im Firmennetz frei, nicht für das öffentliche Internet.

---

### Option B: Cloud-Server (VPS / Cloudrechner)

Für den Zugriff **von unterwegs oder von Baustellen** – z. B. auf einem VPS bei Hetzner, Netcup oder DigitalOcean.

VPN, Reverse Proxy und Tunnel sind unterschiedliche Zugangswege. Für einen öffentlichen Zugriff ohne VPN sind HTTPS, eingeschränkte Backend-Erreichbarkeit, passende Proxy-Vertrauensregeln sowie geprüfte Endpunkt- und Objektberechtigungen gemeinsam erforderlich. Die folgenden Varianten ersetzen diese Prüfung nicht; siehe [Domain-Betrieb ohne VPN](MOBILE_TOKEN_SECURITY.md#vor-einer-öffentlichen-domain-ohne-vpn).

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

**Netzwerkgrenze prüfen:** Der Zugriff soll ausschließlich über das eingerichtete private Netzwerk möglich sein. Ein VPN verhindert für sich allein keine zusätzlich offenen öffentlichen Ports; Firewall und Serverbindung entsprechend prüfen.

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

Einfacher geht es mit `tailscale serve --bg 8080`: Tailscale holt das Zertifikat selbst und reicht `https://mein-server.tail-xxxx.ts.net` an Port 8080 weiter. Das ERP erkennt dabei den echten Absender, ohne dass etwas konfiguriert werden muss.

**Empfohlen – Handys nur zum ERP lassen:** Ohne eigene Regeln erreicht in Tailscale jedes Gerät jedes andere, also auch jedes Mitarbeiter-Handy die Büro-PCs. In der Tailscale-Verwaltung unter *Access controls* lassen sich die Handys davon trennen. Dazu bekommen die Handys in der Geräteliste über *Edit ACL tags* die Markierung `tag:handy`; alle anderen Geräte bleiben, wie sie sind:

```jsonc
{
  "tagOwners": {"tag:handy": ["autogroup:admin"]},
  // Tailscale-IP des ERP-Rechners (steht in der Geräteliste)
  "hosts": {"erp-rechner": "100.x.y.z"},
  "acls": [
    // Handys: nur Port 443 des ERP-Rechners (tailscale serve) – darüber aber das ganze ERP
    {"action": "accept", "src": ["tag:handy"], "dst": ["erp-rechner:443"]},
    // Büro-PCs und Server der Kontoinhaber: wie bisher alles
    {"action": "accept", "src": ["autogroup:member"], "dst": ["*:*"]}
  ]
}
```

Vorhandene Regeln (z. B. `ssh`) übernehmen und die Änderung vor dem Speichern mit *Preview rules* prüfen. Geräte, die bereits eine Markierung tragen (etwa ein Website-Server mit `tag:…`), fallen nicht unter `autogroup:member` und brauchen eine eigene Regel. Die Regel trennt die Handys von den übrigen Geräten; das ERP selbst sehen sie weiterhin wie ein Büro-PC. Desktop und Handy-App verlangen Anmeldung bzw. Code. Die Schnittstelle für die Internetseite (`/api/internal/…`) hat dagegen noch keinen eigenen Schlüssel; sie ist nur durch das Netz geschützt.

---

#### Variante 1b: Tailscale Funnel – Handys ohne Tailscale-App

Für Betriebe, in denen nicht jeder Mitarbeiter eine VPN-App auf dem Handy haben soll. Nur der ERP-Rechner hat Tailscale. Tailscale Funnel macht die Handy-App unter einer festen Adresse wie `https://erp-rechner.tail-xxxx.ts.net/zeiterfassung/` aus dem Internet erreichbar. Kein Router-Umbau, keine eigene Domain, kein zusätzlicher Server. Die Verbindung bleibt bis zum ERP-Rechner verschlüsselt; Tailscale reicht sie nur weiter.

**Was von außen erreichbar ist:** Nur die Handy-App und ihre Schnittstelle, mit dem persönlichen Code des Mitarbeiters. Falsche Codes lösen steigende Wartezeiten aus. Die Desktop-Anmeldung, die Verwaltung und interne Schnittstellen bleiben gesperrt, auch für Büro-Sitzungen. Geräte im eigenen Tailscale-Netz erreichen über dieselbe Adresse weiterhin das ganze ERP.

**Voraussetzung:** Das ERP läuft direkt auf dem Rechner mit Tailscale (JAR bzw. Windows-Dienst). Läuft es in Docker, sieht es statt `localhost` das Docker-Netz als Absender; dafür das [Docker-Gateway](../deployment/mobile-public/README.md) verwenden.

**Einrichten (einmalig, auf dem ERP-Rechner):**

1. Tailscale installieren und mit dem Firmenkonto anmelden.
2. In der Tailscale-Verwaltung unter *DNS* MagicDNS und *HTTPS Certificates* einschalten.
3. Funnel starten (Windows: Eingabeaufforderung als Administrator):
   ```bash
   tailscale funnel --bg 8080
   ```
   Beim ersten Mal zeigt der Befehl einen Link, über den Funnel für diesen Rechner freigegeben wird.
4. Die Adresse anzeigen lassen:
   ```bash
   tailscale funnel status
   ```
5. Diese Adresse für die QR-Codes eintragen (`application-local.properties`, ohne `/zeiterfassung` und ohne Schrägstrich am Ende) und das ERP neu starten:
   ```properties
   zeiterfassung.base-url=https://erp-rechner.tail-xxxx.ts.net
   ```
6. Mitarbeiter scannen ihren QR-Code neu. Eine bereits installierte App mit anderer Adresse vorher abgleichen lassen: Offline-Buchungen gehören zur alten Adresse.

**Prüfen – vom Handy mit ausgeschaltetem WLAN:**

- `https://erp-rechner.tail-xxxx.ts.net/` öffnet die Handy-App.
- `https://erp-rechner.tail-xxxx.ts.net/login` zeigt „Zugriff verweigert“.
- Anmelden per QR-Code, Stempeln und Projektfotos funktionieren – auf iPhone und Android, jeweils über Mobilfunk.

**Abschalten:** `tailscale funnel --https=443 off`

**Nicht verwenden:**

- `tailscale funnel --tcp …`, `--tls-terminated-tcp …` oder andere reine Weiterleitungen ohne HTTP (`netsh interface portproxy`, `ssh -R`): Dann kommt jeder Zugriff aus dem Internet als `localhost` ohne Absenderangabe an und gilt als lokal – Desktop-Anmeldung und interne Schnittstellen wären öffentlich.
- `zeiterfassung.security.enabled=false`: Das schaltet die Netzgrenze ab; mit Funnel wäre das ganze ERP öffentlich.

**Abwägung:** Die Tailscale-App auf jedem Handy (Variante 1) ist sicherer, weil das ERP dann aus dem Internet gar nicht sichtbar ist. Mit Funnel ist die Anmeldeseite der Handy-App öffentlich; automatische Suchprogramme finden die Adresse und probieren sie aus. Der Schutz liegt dann im persönlichen Code und in den Regeln des ERP. Codes daher nicht weitergeben, bei verlorenem Handy oder Austritt sofort neu erzeugen. Die Adresse endet immer auf `.ts.net`; für eine eigene Domain siehe Variante 2. Uploads von außen begrenzt das ERP selbst auf 25 MB. Eine Begrenzung der Anfragerate wie im [Docker-Gateway](../deployment/mobile-public/README.md) gibt es bei Funnel nicht, nur die Wartezeiten nach falschen Codes. Diese merkt sich das ERP für höchstens 10.000 Absender; wer die Liste mit sehr vielen Adressen füllt, lässt neue Geräte bis zu fünf Minuten warten.

---

#### Variante 2: HTTPS mit Reverse Proxy (für öffentlichen Zugriff)

Für eine Domain ohne VPN kann ein Reverse Proxy die HTTPS-Verbindung annehmen und geprüfte Anfragen an das Backend weiterleiten. DNS und Zertifikate müssen zur Domain passen; das Backend darf nur über die vorgesehenen Proxys erreichbar sein.

Vor der Freigabe werden die benötigten mobilen Seiten, Ressourcen und API-Methoden ausdrücklich festgelegt. Eine pauschale Weiterleitung des gesamten ERP auf `localhost:8080` ist keine vollständige Absicherung. Die Token-Prüfung ersetzt weder diese Proxy-Regeln noch die Prüfung, welche konkreten Projekte, Dokumente und Mitarbeiterdaten ein angemeldeter Nutzer abrufen darf.

Die [Betriebsanleitung zur mobilen Token-Sicherheit](MOBILE_TOKEN_SECURITY.md) beschreibt die konkreten Einstellungen für `zeiterfassung.security.trusted-proxies`, das Verhalten von `X-Forwarded-For`, was von außen erreichbar ist und die notwendigen Funktionsprüfungen. Für Windows mit Docker steht das [vorbereitete Startpaket](../deployment/mobile-public/README.md) bereit: temporäre HTTPS-Testadresse oder feste Domain, begrenzte mobile Routen, private Backend-Ports und Vorprüfungen vor dem Öffnen.

---

#### Variante 3: Cloudflare Tunnel (kein VPN auf dem Endgerät nötig)

Ein [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/) kann einen Zugangsweg über eine Domain bereitstellen. Auch dafür sind die öffentliche Routenfreigabe, HTTPS, Objektberechtigungen und die Abschottung des Backends zu prüfen. Die Einrichtung eines Tunnels allein schützt keine zusätzlich erreichbaren Backend-Ports und gewährt keine fachlichen Berechtigungen.

`cloudflared` verbindet sich auf `localhost:8080`; Proxys auf dem ERP-Rechner selbst gelten als freigegeben. Die Cloudflare-Edge hängt den echten Absender an `X-Forwarded-For` an, das ERP wertet diese Kette aus und gleicht sie mit `CF-Connecting-IP` ab. Anfragen aus dem Internet gelten damit als extern: Die Netzgrenze lässt nur die Handy-App durch, Desktop-Oberfläche und Anmeldung bleiben gesperrt. Jeder Nutzer hat eine eigene Code-Sperre über seine echte IP. Passen die Angaben nicht zusammen, gilt die Anfrage ebenfalls als extern. **Nach dem Update:** Wer bisher den ganzen ERP-Rechner per Tunnel freigegeben hat, erreicht darüber nur noch die mobile Zeiterfassung. Details: [Vertrauenswürdige Proxys konfigurieren](MOBILE_TOKEN_SECURITY.md#vertrauenswürdige-proxys-konfigurieren).

---

### Übersicht: Welche Variante passt zu mir?

| Kriterium | LAN (lokal) | Tailscale VPN | Tailscale Funnel | HTTPS + Reverse Proxy | Cloudflare Tunnel |
|-----------|:-----------:|:-------------:|:----------------:|:---------------------:|:-----------------:|
| Einrichtung | ⭐ Einfach | ⭐ Einfach | ⭐ Einfach | ⭐⭐ Mittel | ⭐⭐ Mittel |
| Zugriff von unterwegs | ❌ | ✅ | ✅ (nur Handy-App) | ✅ | ✅ |
| App auf jedem Handy | – | Tailscale-App | Keine | Keine | Keine |
| Öffentlich erreichbar | Nein, bei passender Firewall | Nein, bei passender Firewall | Nur Handy-App (Netzgrenze) | Nur freigegebene Routen (Proxy-Regeln) | Nur Handy-App (Netzgrenze) |
| HTTPS | Für den Betriebsweg prüfen | Für den Betriebsweg prüfen | Automatisch | Einrichten und prüfen | Gesamten Verbindungsweg prüfen |
| Port öffnen | Nur LAN | Kein Port | Kein Port | Port 80 + 443 | Kein Port |

---

## Skript-Referenz

<!-- AUTO-GENERATED: scripts-table START -->
> Erzeugt aus `pom.xml`, `react-pc-frontend/package.json`, `react-zeiterfassung/package.json`. **Nicht manuell editieren** – per `/ecc:update-docs` regenerieren.

### Backend (Maven Wrapper)

Aufruf aus dem Projekt-Root. Unter Windows `./mvnw.cmd …`, unter Linux/macOS `./mvnw …`.

| Befehl | Beschreibung |
|--------|--------------|
| `./mvnw spring-boot:run` | Backend starten (Port 8080, Profile `local` über `application-local.properties`) |
| `./mvnw clean package` | JAR bauen inkl. Tests (`target/Kalkulationsprogramm-<Version>.jar`) |
| `./mvnw clean package -DskipTests` | JAR ohne Test-Lauf bauen (für Installer-Build) |
| `./mvnw test` | Nur Backend-Tests ausführen (JUnit 5 + Spring Boot Test) |
| `./mvnw jpackage:jpackage` | Windows-Installer erzeugen (`target/installer/`) |

### Desktop-Frontend (`react-pc-frontend/`)

Aufruf nach `cd react-pc-frontend`. Voraussetzung: `npm install` einmalig.

| Befehl | Beschreibung |
|--------|--------------|
| `npm run dev` | Vite-Dev-Server mit HMR |
| `npm run build` | Produktiv-Build mit `tsc -b` + `vite build` |
| `npm run preview` | Produktiv-Build lokal probefahren |
| `npm run lint` | ESLint über das gesamte Frontend |
| `npm test` | Vitest-Suite einmal ausführen |
| `npm run test:watch` | Vitest im Watch-Modus |
| `npm run test:coverage` | Vitest mit V8-Coverage-Report |

### Mobile Zeiterfassung (`react-zeiterfassung/`)

Aufruf nach `cd react-zeiterfassung`. PWA mit `vite-plugin-pwa`.

| Befehl | Beschreibung |
|--------|--------------|
| `npm run dev` | Vite-Dev-Server (HTTPS via `@vitejs/plugin-basic-ssl` für Kamera-/Geolocation-Tests) |
| `npm run build` | Produktiv-PWA-Build inkl. Service-Worker |
| `npm run preview` | Produktiv-Build lokal probefahren |
| `npm run lint` | ESLint |
| `npm test` | Vitest-Suite einmal ausführen |
| `npm run test:watch` | Vitest im Watch-Modus |
| `npm run test:coverage` | Vitest mit V8-Coverage-Report |
<!-- AUTO-GENERATED: scripts-table END -->

---

## Umgebungsvariablen-Referenz

<!-- AUTO-GENERATED: env-table START -->
> Erzeugt aus `.env.example`. **Nicht manuell editieren** – per `/ecc:update-docs` regenerieren.
>
> Hinweis: SMTP/IMAP-Zugangsdaten und der Gemini-API-Key werden **nicht** über ENV-Variablen, sondern nach dem ersten Start in der UI unter **System-Einstellungen → E-Mail-Konto / KI Gemini** in der Datenbank gepflegt.

### Docker-Compose (MariaDB)

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `MARIADB_ROOT_PASSWORD` | Optional¹ | Root-Passwort der MariaDB im Compose-Stack | `CHANGE_ME_ROOT_PW` |
| `MARIADB_DATABASE` | Optional¹ | Name der App-Datenbank | `kalkulationsprogramm_db` |
| `MARIADB_USER` | Optional¹ | App-DB-Benutzer | `erp_user` |
| `MARIADB_PASSWORD` | Optional¹ | App-DB-Passwort | `CHANGE_ME_DB_PW` |

¹ Nur nötig, wenn die Default-Werte aus `docker-compose.yml` überschrieben werden sollen.

### Externe Datenbank (überschreibt Default-Verbindung)

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `APP_DB_URL` | Optional² | JDBC-URL für externes MariaDB/MySQL | `jdbc:mariadb://host.docker.internal:3306/kalkulationsprogramm_db?useUnicode=true&characterEncoding=UTF-8` |
| `APP_DB_USER` | Optional² | DB-Benutzer für externe DB | `mariadb_user` |
| `APP_DB_PASS` | Optional² | DB-Passwort für externe DB | `mariadb_password` |

² Nur setzen, wenn die App auf eine DB außerhalb des Compose-Stacks zeigen soll.

### Admin-Zugangsdaten

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `APP_ADMIN_USER` | Ja | Login-Name des initialen Admin-Users | `Marvin` |
| `APP_ADMIN_PASS` | Ja | Initiales Admin-Passwort (nach erstem Login ändern!) | `change_me_strong_password` |

### Optionale Integration

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `ZEITERFASSUNG_URL` | Nein | Basis-URL der mobilen Zeiterfassung (für Deep-Links) | `http://localhost:8080` |
<!-- AUTO-GENERATED: env-table END -->

**Präzisierung zur generierten Admin-Tabelle:** `APP_ADMIN_USER` und `APP_ADMIN_PASS` sind im aktuellen Backend optionale Angaben für die erste Admin-Anlage. Ohne sie erfolgt die Erstregistrierung im Browser. Bestehende Login-Benutzer werden durch diese Variablen nicht geändert; siehe [Authentifizierung konfigurieren](#authentifizierung-konfigurieren).
