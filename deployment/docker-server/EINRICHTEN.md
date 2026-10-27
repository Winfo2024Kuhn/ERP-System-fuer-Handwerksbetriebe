# Neuen Kunden einrichten – Schritt für Schritt

Diese Anleitung führt durch die Einrichtung des ERP auf dem Rechner eines Kunden: vom
leeren Windows-PC bis zur ersten geprüften Sicherung. Zum Abhaken beim Kunden gedacht.
Hintergründe und alle Einstellungen stehen im [README](README.md).

**Zeitbedarf:** etwa 1,5 Stunden (davon viel Warten auf Downloads).

## So läuft es danach von selbst

| Wann | Was passiert | Was der Kunde merkt |
| --- | --- | --- |
| Immer | Datenbank, ERP und Updater laufen als Docker-Container und starten nach jedem Neustart von selbst | ERP über die Verknüpfung „ERP Handwerk“ |
| Jede Nacht 2 Uhr | **Sicherung:** die Datenbank auf den PC, die externe Festplatte und in OneDrive, dazu alle hochgeladenen Dateien auf die Festplatte und in OneDrive | nichts |
| Jede Nacht 3 Uhr | **Update:** neue Version holen, vorher sichern, bei Problemen automatisch zurück zur alten | nichts |
| Auf Klick | **Jetzt aktualisieren:** in Docker Desktop beim Container `erp-jetzt-aktualisieren` auf **Start** klicken – derselbe Ablauf wie nachts, sofort ([README](README.md#update-per-klick-docker-desktop)) | ERP ist während eines Updates ein paar Minuten weg |
| Alle 5 Minuten | **Docker-Wächter:** Hängt Docker Desktop (z. B. nach einem Windows-Update), startet er es neu. Hat Docker die Verbindung zur Sicherungsplatte verloren, startet er es in der Stunde vor der Sicherung neu | ERP ist höchstens ein paar Minuten weg |
| Wenn etwas schiefgeht | Meldung im ERP für Admins (Glocke → Benachrichtigungen → „Sicherung & Updates“), auf Wunsch zusätzlich aufs Handy | Admin sieht die Meldung |

Der PC muss dafür **nachts eingeschaltet** bleiben.

---

## 0. Vorher klären

- [ ] **Rechner:** ein Windows-10/11-PC (64 Bit), der dauerhaft läuft – am besten nicht der
      Arbeitsplatz des Chefs. Empfehlung: 16 GB Arbeitsspeicher, SSD, 100 GB frei.
      **Windows Pro** nehmen: Nur damit lässt sich die Sicherungsplatte mit BitLocker
      verschlüsseln (Windows Home kann das nicht).
- [ ] **Docker Desktop:** für Betriebe unter 250 Mitarbeitenden **und** unter 10 Mio. USD
      Umsatz kostenlos. Größere Betriebe brauchen eine Lizenz oder einen Linux-Server.
- [ ] **Externe Festplatte** (USB), die dauerhaft am PC bleibt.
- [ ] **OneDrive mit Geschäftskonto** (Microsoft 365 Business). Kein privates Konto: Die
      Sicherung enthält alle Kunden-, Mitarbeiter- und Lohndaten. Mit dem Geschäftskonto gibt
      es den Vertrag zur Auftragsverarbeitung (DSGVO Art. 28).
- [ ] **Wer wird Admin** im ERP? (In der Regel der Chef, du richtest es mit ihm ein.)
- [ ] **Zugang für die Handys der Mitarbeiter:** über die Tailscale-App auf jedem Handy
      (sicherer) oder über Tailscale Funnel ohne App (siehe Schritt 8).
- [ ] **Ein Passwortmanager** (z. B. Bitwarden), in dem du die Zugangsdaten des Kunden ablegst.

## 1. Rechner vorbereiten

- [ ] Alle Windows-Updates einspielen und neu starten.
- [ ] **BitLocker** für Laufwerk C: einschalten (Einstellungen → Datenschutz und Sicherheit →
      Geräteverschlüsselung bzw. BitLocker). Den Wiederherstellungsschlüssel in den
      Passwortmanager legen.
- [ ] **Automatische Anmeldung** einrichten, damit das ERP nach einem Stromausfall oder
      Windows-Update ohne Zutun wieder läuft: [Autologon von Microsoft Sysinternals](https://learn.microsoft.com/sysinternals/downloads/autologon).
      Danach eine **Bildschirmsperre** einstellen (z. B. nach 5 Minuten), sonst steht der PC
      angemeldet und offen da.
- [ ] **OneDrive** mit dem Geschäftskonto des Betriebs anmelden (unter genau dem
      Windows-Benutzer, der sich automatisch anmeldet).

## 2. ERP-Ordner auf den PC kopieren

- [ ] Auf GitHub das Repository als ZIP laden (*Code → Download ZIP*) und daraus den Ordner
      `deployment/docker-server` nach **`C:\ERP-Handwerk`** kopieren.

> Nicht den Ordner eines anderen laufenden Servers kopieren: Darin liegen dessen Passwörter
> (`.env`, `app.env`) und Sicherungen.

## 3. Einrichten starten

- [ ] In `C:\ERP-Handwerk` **`Einrichten.cmd` doppelklicken.**
  - Fehlt Docker Desktop, bietet das Skript die Installation an. Danach: PC neu starten,
    Docker Desktop einmal öffnen, die Lizenz bestätigen und `Einrichten.cmd` noch einmal
    doppelklicken.
  - Das Skript fragt nach dem **Namen des Betriebs**, z. B. „Schreinerei Mustermann“. Er
    steht später in den Handy-Nachrichten.
  - Es legt die `.env` mit zufälligen Passwörtern an, schaltet Standby ab, setzt die
    Nutzungszeit von Windows Update auf 6 bis 22 Uhr, startet alles, richtet den
    Docker-Wächter ein und legt die Verknüpfung „ERP Handwerk“ auf den Desktop. Beim ersten
    Mal lädt es etwa 1 GB.
  - Meldet es bei der Nutzungszeit „Administratorrechte?“: von Hand einstellen unter
    *Einstellungen → Windows Update → Erweiterte Optionen → Nutzungszeit* → „Manuell“,
    6 bis 22 Uhr.

Kommt beim ersten Start die Meldung *denied* oder *unauthorized*, ist das Image privat:
siehe README, Abschnitt „Image privat?“.

## 4. Admin anlegen – sofort

- [ ] Im Browser öffnet sich das ERP. **Wer sich als Erster registriert, wird Admin.** Das
      also gleich jetzt mit dem Chef erledigen und das Passwort in den Passwortmanager legen.

## 5. Weitere Einstellungen: `app.env`

Im Ordner `C:\ERP-Handwerk` eine Datei **`app.env`** anlegen (mit dem Editor). Werte stehen in
einfachen Anführungszeichen.

- [ ] **Schlüssel für die E-Mail-Postfächer** (Pflicht, sobald E-Mail genutzt wird). Er muss
      genau 32 Zeichen lang sein. Erzeugen in PowerShell:

  ```powershell
  $b = New-Object byte[] 24; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
  ```

  ```ini
  MAIL_CREDENTIALS_ENCRYPTIONKEY='<die 32 Zeichen von oben>'
  EMAIL_FEATURES_ENABLED='true'
  ```

- [ ] Optional weitere Schlüssel, z. B. für die KI (`AI_GEMINI_APIKEY='…'`).
- [ ] Danach im Ordner `C:\ERP-Handwerk` (Rechtsklick → *Im Terminal öffnen*):

  ```powershell
  docker compose up -d app
  ```

> **Den Schlüssel nie verlieren.** Mit ihm sind die Postfach-Passwörter in der Datenbank
> verschlüsselt. Fehlt er nach einem Plattenausfall, muss der Kunde alle Postfächer neu
> einrichten. `.env` und `app.env` gehören deshalb in den Passwortmanager (siehe Schritt 9) –
> sie sind bewusst **nicht** Teil der täglichen Sicherung.

## 6. Tägliche Sicherung einrichten – Pflicht

### 6.1 Externe Festplatte

- [ ] Festplatte anschließen und **mit NTFS formatieren** (Rechtsklick im Explorer →
      Formatieren).
- [ ] **Festen Laufwerksbuchstaben** geben, damit er sich nie ändert: Rechtsklick auf Start →
      *Datenträgerverwaltung* → Rechtsklick auf die Platte → *Laufwerkbuchstaben und -pfade
      ändern* → z. B. **S:**.
- [ ] **BitLocker To Go** einschalten (Rechtsklick auf das Laufwerk → *BitLocker aktivieren*,
      Option „Automatisch entsperren“ anhaken). Wiederherstellungsschlüssel in den
      Passwortmanager. So wird ein Diebstahl der Platte keine Datenpanne.
- [ ] Auf der Platte den Ordner **`S:\ERP-Sicherung`** anlegen.
- [ ] **Windows soll die Platte nie abschalten.** Sonst trennt Windows sie beim Energiesparen
      kurz, und Docker verliert die Verbindung (der Docker-Wächter repariert das zwar in der
      Nacht vor der Sicherung, aber besser, es passiert gar nicht). PowerShell **als
      Administrator** öffnen:

  ```powershell
  # Festplatten nie ausschalten (am Netzstrom)
  powercfg /change disk-timeout-ac 0
  # USB-Energiesparen aus (am Netzstrom)
  powercfg /setacvalueindex SCHEME_CURRENT 2a737441-1930-4402-8d77-b2bebba308a3 48e6b7a6-50f5-4782-a5d4-53bb8f07e226 0
  powercfg /setactive SCHEME_CURRENT
  ```

  Das gilt für den gerade aktiven Energiesparplan. Wird später ein anderer Plan gewählt,
  die Befehle wiederholen.
  Dazu im *Geräte-Manager* unter „USB-Controller“ bei jedem **USB-Root-Hub** →
  *Eigenschaften* → *Energieverwaltung* den Haken „Computer kann das Gerät ausschalten, um
  Energie zu sparen“ entfernen.

### 6.2 OneDrive

- [ ] Im OneDrive-Ordner einen Ordner **`ERP-Sicherung`** anlegen. Den genauen Pfad zeigt
      PowerShell:

  ```powershell
  echo $env:OneDriveCommercial
  ```

  Beispiel: `C:\Users\Max\OneDrive - Schreinerei Mustermann` → Ordner
  `C:\Users\Max\OneDrive - Schreinerei Mustermann\ERP-Sicherung`.

### 6.3 In der `.env` eintragen

- [ ] `C:\ERP-Handwerk\.env` mit dem Editor öffnen und die beiden Ziele eintragen – so
      geschrieben, wie Docker sie sieht: `C:\` wird `/run/desktop/mnt/host/c/`, `S:\` wird
      `/run/desktop/mnt/host/s/`, Schrägstriche statt Backslashes, **ohne
      Anführungszeichen** (Leerzeichen im Pfad sind in Ordnung):

  ```ini
  SICHERUNG_PLATTE=/run/desktop/mnt/host/s/ERP-Sicherung
  SICHERUNG_CLOUD=/run/desktop/mnt/host/c/Users/Max/OneDrive - Schreinerei Mustermann/ERP-Sicherung
  ```

### 6.4 Einbinden, einrichten, einmal sichern

Im Terminal in `C:\ERP-Handwerk`:

- [ ] Updater mit den neuen Zielen neu erstellen:

  ```powershell
  docker compose up -d updater
  ```

- [ ] Ziele markieren (die Festplatte muss angeschlossen sein):

  ```powershell
  docker compose exec updater bash /erp/sicherung.sh --ziele-einrichten
  ```

  In beiden Ordnern liegt jetzt die Datei `ERP-SICHERUNGSZIEL.txt`. Nicht löschen: Ohne sie
  sichert das ERP dort nicht mehr.

- [ ] Die erste Sicherung von Hand starten:

  ```powershell
  docker compose exec updater bash /erp/sicherung.sh
  ```

- [ ] Prüfen: In `S:\ERP-Sicherung` und im OneDrive-Ordner gibt es jetzt `db\` mit einer
      Datei `kalkulationsprogramm_db_….sql.gz` und `uploads\`. Das Ergebnis zeigt auch:

  ```powershell
  docker compose exec updater cat /betriebsstatus/sicherung.json
  ```

  Dort muss `"ergebnis": "OK"` stehen.

## 7. Handy-Nachrichten für dich (empfohlen)

So erfährst du als Betreuer von Problemen, auch wenn beim Kunden niemand ins ERP schaut.

- [ ] Die App **ntfy** auf dein Handy laden und ein Thema mit langem Zufallsnamen
      abonnieren (der Name ist wie ein Passwort).
- [ ] In der `.env` eintragen (wirkt ab dem nächsten Lauf, kein Neustart nötig):

  ```ini
  WEBHOOK_URL=https://ntfy.sh/<langer-zufaelliger-name>
  ```

## 8. Zugang von unterwegs (Handys der Mitarbeiter)

- [ ] [Tailscale](https://tailscale.com) auf dem PC installieren und mit dem Konto des
      Betriebs anmelden. In der Tailscale-Verwaltung unter *DNS* „MagicDNS“ und „HTTPS
      Certificates“ einschalten.
- [ ] In der `.env`:

  ```ini
  APP_ADRESSE=127.0.0.1
  LOKALER_PROXY=ja
  ZEITERFASSUNG_URL=https://<rechnername>.<tailnet>.ts.net
  ```

- [ ] Im Terminal (als Administrator): `tailscale serve --bg 8080`, danach in
      `C:\ERP-Handwerk`: `docker compose up -d app`.
- [ ] Wichtig: Ab jetzt ist das ERP im Büronetz nicht mehr direkt erreichbar. Andere Büro-PCs
      brauchen ebenfalls Tailscale und öffnen `https://<rechnername>.<tailnet>.ts.net`. Am
      ERP-PC selbst funktioniert die Verknüpfung „ERP Handwerk“ weiter.
- [ ] **Variante A (sicherer):** Die Mitarbeiter installieren die Tailscale-App und melden
      sich im Firmenkonto an.
      **Variante B (ohne App):** `tailscale funnel --bg 8080` und im ERP unter *Einstellungen →
      Zeiterfassung → Zugang von unterwegs* „Über Tailscale Funnel freigeben“. Details und
      Abwägung: [INSTALLATION.md, Variante 1b](../../docs/INSTALLATION.md#variante-1b-tailscale-funnel--handys-ohne-tailscale-app).
- [ ] QR-Codes für die Mitarbeiter im ERP erzeugen und einmal mit einem Handy im Mobilfunk
      (WLAN aus) testen.

## 8b. Dateien direkt öffnen – Excel, HiCAD, Tenado (empfohlen)

Damit alle dieselbe Datei aus dem gemeinsamen Ordner öffnen – im Büro und unterwegs, ohne
Kopien und ohne Windows-Freigabe. Erklärung: [README, Ordner-Freigabe ohne Windows](README.md#ordner-freigabe-ohne-windows).

- [ ] In den **Tailscale-Regeln** `tag:erp-dateien` unter `tagOwners` eintragen und den
      Zugriff auf Port 445 erlauben (Regeln stehen im README-Abschnitt).
- [ ] In `C:\ERP-Handwerk` (Rechtsklick → *Im Terminal öffnen*):

  ```powershell
  docker compose exec updater bash /erp/dateifreigabe.sh
  ```

  Den angezeigten **Anmelde-Link** im Browser öffnen und mit dem Tailscale-Admin-Konto
  bestätigen. Am Ende nennt das Skript die Adresse des Ordners.
- [ ] `docker compose up -d`
- [ ] Im ERP: *Einstellungen → Dateien* → **„Diese Adresse eintragen“** → speichern.
- [ ] Auf jedem PC, der Dateien öffnen soll: Tailscale installieren und anmelden, dann unter
      *Einstellungen → Dateien → „Dateien direkt am PC öffnen“* das Hilfsprogramm einrichten.
      Benutzer und Passwort für den Ordner bringt die Einrichtung mit.
- [ ] Eine Excel-Datei aus einem Projekt öffnen, etwas ändern, speichern – und noch einmal
      öffnen: Die Änderung ist da.

## 9. Abnahme – bevor du gehst

- [ ] `docker compose ps` zeigt vier laufende Dienste: `erp-postgres`, `erp-app`,
      `erp-updater`, `erp-netzsperre` – mit Ordner-Freigabe (8b) zusätzlich `erp-dateifreigabe`.
- [ ] **PC neu starten.** Nach der automatischen Anmeldung läuft das ERP wieder von selbst
      (Verknüpfung „ERP Handwerk“ öffnen).
- [ ] In der **Aufgabenplanung** steht „ERP Handwerk Docker-Waechter“ mit Status „Bereit“.
- [ ] Im ERP als Admin auf die **Glocke** klicken: Unter „Sicherung & Updates“ steht nichts.
- [ ] Die Sicherung aus Schritt 6 liegt auf der Festplatte und in OneDrive (OneDrive zeigt
      den grünen Haken).
- [ ] **Ab in den Passwortmanager** (ein Eintrag pro Kunde):
  - `C:\ERP-Handwerk\.env` und `C:\ERP-Handwerk\app.env` (als Anhang oder Inhalt)
  - Admin-Zugang des ERP
  - BitLocker-Wiederherstellungsschlüssel (C: und Festplatte)
  - Windows-Anmeldung, Tailscale- und Microsoft-365-Konto
- [ ] Dem Kunden sagen:
  - PC nachts **anlassen**, die Festplatte **nicht abziehen**.
  - Erscheint im ERP unter „Sicherung & Updates“ eine Meldung: dich anrufen.

## 10. Am nächsten Tag kurz prüfen

- [ ] Im ERP als Admin (geht auch aus der Ferne über Tailscale) auf die Glocke: Unter
      „Sicherung & Updates“ steht nichts. Auf der Festplatte und in OneDrive liegt eine
      Datenbank-Sicherung von heute Nacht.

---

## Wenn etwas schiefgeht

| Meldung im ERP | Was tun |
| --- | --- |
| „Externe Festplatte nicht gefunden“ | Platte anschließen. Der Docker-Wächter bindet sie in der Stunde vor der nächsten Sicherung selbst ein. Eilt es: `docker desktop restart`. Ist die Platte neu, fehlt ihr noch die Markierung: Schritt 6.4 (`--ziele-einrichten`). |
| „Externe Festplatte: Die Verbindung ist abgerissen“ | Nichts, wenn die Platte steckt: Der Docker-Wächter startet Docker Desktop vor der nächsten Sicherung neu. Eilt es: `docker desktop restart`. Kommt das öfter vor, die Energiespar-Einstellungen aus Schritt 6.1 prüfen. |
| „… ist in der .env eingetragen, aber noch nicht eingebunden“ | Im ERP-Ordner `docker compose up -d updater`. |
| „Cloud-Ordner nicht gefunden“ | Ist OneDrive angemeldet und der Ordner `ERP-Sicherung` noch da? Danach PC neu starten. |
| „Seit über einem Tag keine Sicherung“ | War der PC nachts aus? Läuft Docker Desktop? `docker compose ps` prüfen. |
| „Sicherung nur auf diesem Rechner“ | Schritt 6 fehlt noch. |
| „Zu wenig Speicherplatz auf diesem Rechner“ | Auf Laufwerk C: Platz schaffen, mindestens 1 GB muss frei sein. |
| „Der Updater ist veraltet“ | Im ERP-Ordner `docker compose build updater`, danach `docker compose up -d updater`. |
| „Update fehlgeschlagen – alte Version läuft wieder“ | Der Betrieb merkt nichts. Log steht in `C:\ERP-Handwerk\logs\`. Die nächste neuere Version wird automatisch wieder versucht. |
| „ERP-Update: Download fehlgeschlagen“ | Es wurde nichts verändert. Hat der PC Internet? Ist das Image privat, siehe README, Abschnitt „Image privat?“. Nächste Nacht kommt ein neuer Versuch. |
| „ERP-Update ausgelassen“ | Es wurde nichts verändert. Der Text der Meldung sagt, warum: zu wenig Speicherplatz, Sicherung fehlgeschlagen oder das ERP lief schon vorher nicht sauber. |
| „NOTFALL“ | Sofort hinschauen: README, Abschnitt „Was nachts passiert“. |

Nachrichten vom Docker-Wächter und wenn das ERP im Browser nicht aufgeht:

| Handy-Nachricht oder Beobachtung | Was tun |
| --- | --- |
| „ERP war kurz nicht erreichbar“ | Nichts. Der Docker-Wächter hat Docker Desktop neu gestartet, das ERP läuft wieder. |
| „ERP nicht erreichbar“ (dringend) | Der automatische Neustart hat nicht geholfen. Der Wächter versucht es weiter, nach dem zweiten Fehlschlag nur noch alle 4 Stunden, und schickt keine weitere Nachricht. Am PC nachsehen: Zeigt Docker Desktop eine Frage (Lizenz, Anmeldung)? `docker compose ps`, Log `C:\ERP-Handwerk\logs\docker-waechter.log`. Hilft das nicht, den PC neu starten. |
| „ERP-Sicherung: … wieder verbunden“ | Nichts. Die Verbindung zur Platte (oder zum Cloud-Ordner) war abgerissen, der Wächter hat Docker Desktop vor der Sicherung neu gestartet. |
| „ERP-Sicherung: … nicht erreichbar“ (dringend) | Windows sieht die Platte, Docker auch nach einem Neustart nicht. Den PC neu starten. |
| ERP lässt sich im Browser nicht öffnen | Bis zu 10 Minuten warten, der Docker-Wächter startet Docker Desktop selbst neu. Eilt es: im Terminal `docker desktop restart`. |

### Eine gelöschte Datei zurückholen

Im Ordner `uploads\` auf der Festplatte oder in OneDrive wird nie etwas gelöscht. Die Datei
dort suchen und dem Kunden geben bzw. im ERP neu hochladen.

### Der PC ist kaputt – alles auf einem neuen PC zurückholen

1. Schritte 1 bis 4 auf dem neuen PC (neue `.env` mit neuen Passwörtern ist in Ordnung).
2. Die `app.env` aus dem Passwortmanager nach `C:\ERP-Handwerk` legen (wegen des
   E-Mail-Schlüssels), dann `docker compose up -d app`.
3. Die neueste Datenbank-Sicherung (`kalkulationsprogramm_db_….sql.gz`, von der Festplatte
   oder aus OneDrive) nach `C:\ERP-Handwerk` kopieren und einspielen:

   ```powershell
   docker compose exec updater bash /erp/nachtupdate.sh --importieren kalkulationsprogramm_db_20261011_020001.sql.gz
   ```

4. Die hochgeladenen Dateien zurückkopieren (Festplatte als `S:` angeschlossen):

   ```powershell
   docker run --rm -v erp-handwerk_app_uploads:/ziel -v "S:\ERP-Sicherung\uploads:/quelle:ro" alpine:3.22 cp -a /quelle/. /ziel/
   docker compose restart app
   ```

   Steht in der `.env` ein `UPLOADS_ORDNER`, die Dateien stattdessen im Explorer in diesen
   Ordner kopieren.

5. Schritt 6 wiederholen (die Ziele sind schon markiert, `--ziele-einrichten` entfällt) und
   die Abnahme aus Schritt 9 machen.

**Tipp:** Das Zurückholen einmal im Jahr auf einem Testrechner üben. Eine Sicherung, die nie
zurückgespielt wurde, ist nur eine Hoffnung.
