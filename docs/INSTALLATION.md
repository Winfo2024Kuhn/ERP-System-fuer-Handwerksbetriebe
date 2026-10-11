# Installation & Einrichtung

> **Ziel:** Vom Download bis zum laufenden ERP-System in wenigen Minuten – ohne Vorkenntnisse.
>
> Die Datenbank ist immer **PostgreSQL 16**. Sie läuft als eigener Docker-Container und wird beim ersten Start automatisch eingerichtet.

---

## Variante 1: Docker beim Kunden (empfohlen für Betriebe)

Für einen Windows-PC oder Linux-Server, auf dem das ERP dauerhaft läuft. Ein Doppelklick bzw. ein Befehl richtet alles ein: Datenbank, ERP und einen kleinen Updater, der nachts neue Versionen einspielt – mit Sicherung vorher und automatischem Rückweg bei Problemen.

1. Den Ordner `deployment/docker-server` auf den Rechner kopieren, z. B. nach `C:\ERP-Handwerk`.
2. Windows: **`Einrichten.cmd` doppelklicken**. Linux: `sudo ./einrichten.sh`.
3. Im Browser öffnet sich das ERP.

Bei einer neuen Datenbank ohne vorkonfigurierten Admin legst du im Browser den ersten Benutzer an; dieser wird Administrator. Schließe die Ersteinrichtung im geschützten Netz ab, bevor die Anwendung über eine öffentliche Domain erreichbar wird. Es gibt keine fest eingebauten Standard-Zugangsdaten für diese Einrichtung.

Schritt-für-Schritt-Anleitung für einen neuen Kunden, mit Sicherung auf Festplatte und OneDrive: **[deployment/docker-server/EINRICHTEN.md](../deployment/docker-server/EINRICHTEN.md)**

Alles Weitere (Umzug von einem anderen Server, Handy-Nachrichten, Sicherheit, was nachts passiert): **[deployment/docker-server/README.md](../deployment/docker-server/README.md)**

### Wo werden meine Daten gespeichert?

| Daten | Speicherort |
|-------|-------------|
| Datenbank | Docker-Volume `postgres_data` (PostgreSQL) |
| Uploads | Docker-Volume `app_uploads` oder ein eigener Ordner (`UPLOADS_ORDNER` in der `.env`) |
| Sicherungen vor jedem Update | Ordner `sicherungen\` im ERP-Ordner |

---

## Variante 2: Docker zum Ausprobieren (aus dem Projektordner)

Benötigt: [Docker Desktop](https://www.docker.com/products/docker-desktop/)

```bash
git clone https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe.git
cd ERP-System-fuer-Handwerksbetriebe
docker compose up -d --build
```

Oder unter Windows einfach `start-docker.bat` doppelklicken.

**Was passiert automatisch:**
- PostgreSQL 16 wird heruntergeladen und gestartet
- Die leere Datenbank bekommt beim ersten Start alle Tabellen und Stammdaten
- Qdrant (Vektor-DB für KI) wird gestartet
- Die Anwendung startet auf `http://localhost:8080`

Benutzer und Passwort der Datenbank stehen in der `.env` (Vorlage: `.env.example`, Werte `DB_USER` und `DB_PASSWORD`).

**Stoppen:** `stop-docker.bat` oder `docker compose down`

---

## Variante 3: Entwickeln (Backend mit Maven)

### Voraussetzungen

| Komponente | Version | Download |
|------------|---------|----------|
| Java (JDK) | 23+ | [Eclipse Adoptium](https://adoptium.net/) |
| Docker Desktop | aktuell | [docker.com](https://www.docker.com/products/docker-desktop/) (für die PostgreSQL-Datenbank) |
| Node.js | 18+ | [nodejs.org](https://nodejs.org/) (nur für Frontend-Entwicklung) |

### 1. Repository klonen

```bash
git clone https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe.git
cd ERP-System-fuer-Handwerksbetriebe
```

### 2. Datenbank starten (PostgreSQL im Container)

```bash
docker run -d --name erp-dev-pg -p 127.0.0.1:5432:5432 \
  -e POSTGRES_DB=kalkulationsprogramm_db \
  -e POSTGRES_USER=erp_user \
  -e POSTGRES_PASSWORD=DEIN_DB_PASSWORT \
  postgres:16
```

Die leere Datenbank bekommt beim ersten Start des Backends automatisch Basis-Schema und alle Migrationen. Später genügt `docker start erp-dev-pg`.

### 3. Konfiguration erstellen

Kopiere `src/main/resources/application-local.properties.example` nach `src/main/resources/application-local.properties` (wird nie committet) und trage die Zugangsdaten ein:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/kalkulationsprogramm_db
spring.datasource.username=erp_user
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

## Häufige Fragen (FAQ)

### Welche Datenbank brauche ich?

Immer PostgreSQL 16. Bei Variante 1 und 2 kommt sie als Docker-Container automatisch mit. MySQL, MariaDB und die frühere eingebaute H2-Datenbank der Windows-Installationsdatei werden nicht mehr unterstützt.

### Port 8080 ist belegt – was tun?

Starte die Anwendung mit einem anderen Port:
```bash
java -jar Kalkulationsprogramm-<Version>.jar --server.port=9090
```

### Wie sichere ich meine Daten?

- **Variante 1 macht das selbst:** Jede Nacht um 2 Uhr sichert der Updater die komplette Datenbank und alle hochgeladenen Dateien – auf den Rechner und zusätzlich auf eine externe Festplatte und/oder in einen Cloud-Ordner wie OneDrive. Die Ziele trägst du einmal in der `.env` ein (`SICHERUNG_PLATTE`, `SICHERUNG_CLOUD`), Anleitung: [deployment/docker-server/README.md, Tägliche Sicherung](../deployment/docker-server/README.md#tägliche-sicherung). Geht etwas schief, sehen Admins das im ERP unter **Benachrichtigungen**.
- **Von Hand** (andere Varianten): `pg_dump` im Datenbank-Container, z. B. `docker exec erp-postgres pg_dump -U postgres -d kalkulationsprogramm_db --no-owner --no-privileges > sicherung.sql` unter Linux. Unter Windows nicht mit `>` umleiten (Windows PowerShell 5.1 speichert dann UTF-16 und die Sicherung lässt sich nicht einspielen). Die hochgeladenen Dateien (Volume `app_uploads` bzw. `UPLOADS_ORDNER`) gehören mit in die Sicherung.

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
| PostgreSQL | 16 | Datenbank `kalkulationsprogramm_db`, am einfachsten als Docker-Container (siehe Variante 3) |
| Node.js | 18+ | Nur für Frontend-Build nötig |

> Am einfachsten ist **[Variante 1](#variante-1-docker-beim-kunden-empfohlen-für-betriebe)**: Dort kommen ERP und Datenbank fertig als Docker-Container, mit Updates und Sicherung. Die folgenden Schritte beschreiben den Betrieb als JAR.

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

#### 1. Datenbank einrichten (PostgreSQL)

Wie in [Variante 3, Schritt 2](#2-datenbank-starten-postgresql-im-container): PostgreSQL 16 als Container mit der Datenbank `kalkulationsprogramm_db`. Die Tabellen legt das ERP beim ersten Start selbst an.

#### 2. Konfiguration anpassen

Erstelle `src/main/resources/application-local.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/kalkulationsprogramm_db
spring.datasource.username=erp_user
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

Vorhandene Regeln (z. B. `ssh`) übernehmen und die Änderung vor dem Speichern mit *Preview rules* prüfen. Geräte, die bereits eine Markierung tragen (etwa ein Website-Server mit `tag:…`), fallen nicht unter `autogroup:member` und brauchen eine eigene Regel. Die Regel trennt die Handys von den übrigen Geräten; das ERP selbst sehen sie weiterhin wie ein Büro-PC. Desktop und Handy-App verlangen Anmeldung bzw. Code. Die Schnittstelle für die Internetseite (`/api/internal/…`) verlangt zusätzlich den gemeinsamen Website-Schlüssel (`WEBSITE_INTERNAL_API_KEY`); das Netz allein reicht nicht.

---

#### Variante 1b: Tailscale Funnel – Handys ohne Tailscale-App

Für Betriebe, in denen nicht jeder Mitarbeiter eine VPN-App auf dem Handy haben soll. Nur der ERP-Rechner hat Tailscale. Tailscale Funnel macht die Handy-App unter einer festen Adresse wie `https://erp-rechner.tail-xxxx.ts.net/zeiterfassung/` aus dem Internet erreichbar. Kein Router-Umbau, keine eigene Domain, kein zusätzlicher Server. Die Verbindung bleibt bis zum ERP-Rechner verschlüsselt; Tailscale reicht sie nur weiter.

**Was von außen erreichbar ist:** Nur die Handy-App und ihre Schnittstelle, mit dem persönlichen Code des Mitarbeiters. Falsche Codes lösen steigende Wartezeiten aus. Die Desktop-Anmeldung, die Verwaltung und interne Schnittstellen bleiben gesperrt, auch für Büro-Sitzungen. Geräte im eigenen Tailscale-Netz erreichen über dieselbe Adresse weiterhin das ganze ERP.

**Voraussetzung:** Tailscale läuft auf dem ERP-Rechner. Das ERP läuft dort direkt (JAR bzw. Windows-Dienst) oder im [Docker-Paket](../deployment/docker-server/README.md) mit `LOKALER_PROXY=ja`; sonst sieht es in Docker statt `localhost` das Docker-Netz als Absender.

**Freigabe im ERP:** Zusätzlich muss der Funnel im ERP freigegeben sein: *Einstellungen → Zeiterfassung → Zugang von unterwegs → „Über Tailscale Funnel freigeben“*. Ohne diese Freigabe weist das ERP jede Funnel-Anfrage ab, auch wenn am Rechner ein Funnel läuft. Die Karte zeigt auch, wann zuletzt eine Anfrage über Funnel kam.

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

**Abschalten:** `tailscale serve --bg 8080` – der Funnel ist aus, im eigenen Tailscale-Netz bleibt das ERP erreichbar. Danach im ERP wieder „Mit der Tailscale-App“ wählen. (`tailscale funnel --https=443 off` entfernt dagegen den ganzen Eintrag, also auch den Zugang im Tailscale-Netz.)

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
| `./mvnw clean package -DskipTests` | JAR ohne Test-Lauf bauen |
| `./mvnw test` | Nur Backend-Tests ausführen (JUnit 5 + Spring Boot Test, Datenbank: H2 im Speicher) |
| `./mvnw test "-Dtest=DatenbankKompatibilitaet*Test" -Dkompat.db.url=… -Dkompat.db.user=… -Dkompat.db.pass=…` | Datenbank-Tests gegen ein echtes PostgreSQL (Wegwerf-Container `postgres:16`) |

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

### Docker-Compose (PostgreSQL)

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `DB_USER` | Optional¹ | Benutzer der PostgreSQL-Datenbank im Compose-Stack, gilt auch für die App | `erp_user` |
| `DB_PASSWORD` | Optional¹ | Passwort dazu, gilt auch für die App | `CHANGE_ME_DB_PW` |

¹ Nur nötig, wenn die Default-Werte aus `docker-compose.yml` überschrieben werden sollen.

### Externe Datenbank (überschreibt Default-Verbindung)

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `APP_DB_URL` | Optional² | JDBC-URL für ein PostgreSQL außerhalb des Compose-Stacks (`DB_USER`/`DB_PASSWORD` gelten dafür) | `jdbc:postgresql://host.docker.internal:5432/kalkulationsprogramm_db` |

² Nur setzen, wenn die App auf eine DB außerhalb des Compose-Stacks zeigen soll.

### Admin-Zugangsdaten

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `APP_ADMIN_USER` | Ja | Login-Name des initialen Admin-Users | `admin` |
| `APP_ADMIN_PASS` | Ja | Initiales Admin-Passwort (nach erstem Login ändern!) | `change_me_strong_password` |

### Optionale Integration

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `ZEITERFASSUNG_URL` | Nein | Basis-URL der mobilen Zeiterfassung (für Deep-Links) | `http://localhost:8080` |
<!-- AUTO-GENERATED: env-table END -->

**Präzisierung zur generierten Admin-Tabelle:** `APP_ADMIN_USER` und `APP_ADMIN_PASS` sind im aktuellen Backend optionale Angaben für die erste Admin-Anlage. Ohne sie erfolgt die Erstregistrierung im Browser. Bestehende Login-Benutzer werden durch diese Variablen nicht geändert; siehe [Authentifizierung konfigurieren](#authentifizierung-konfigurieren).

### Anbindung der Firmen-Website

| Variable | Pflicht | Beschreibung | Beispielwert |
|----------|---------|--------------|--------------|
| `WEBSITE_INTERNAL_API_KEY` | Nur mit Website | Gemeinsamer Schlüssel für `/api/internal/**` (Anfrage-Formular, Freigabe-Seite, Analytics). Ohne Wert weist das ERP diese Aufrufe mit 401 ab. Derselbe Wert steht auf der Website als `ERP_INTERNAL_API_KEY`. | `openssl rand -base64 32` |
| `WEBSITE_BEITRAEGE_API_TOKEN` | Nur mit Beiträge-Modul | Bearer-Token für die Beiträge-Schnittstelle der Website | `<geheim>` |

Reihenfolge der Umstellung ohne Ausfall (erst Website, dann ERP): siehe [Mobile Token-Sicherheit](MOBILE_TOKEN_SECURITY.md#website-schnittstelle-apiinternal).
