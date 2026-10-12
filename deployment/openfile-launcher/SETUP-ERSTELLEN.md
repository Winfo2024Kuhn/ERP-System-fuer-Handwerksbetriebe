# Setup-Programm erstellen - Anleitung

Es gibt **drei Möglichkeiten**, aus `launcher.ps1` ein Setup zu machen. Sie unterscheiden sich
auch darin, wohin der Launcher installiert wird:

| Weg | Ergebnis | Installiert nach |
| --- | --- | --- |
| Option 1: IExpress | `OpenFileLauncher-Setup.exe` | `C:\Program Files\OpenFileLauncher` (über `install-openfile-launcher.ps1`) |
| Option 2: Inno Setup | `Output\OpenFileLauncher-Setup.exe` | `C:\Program Files (x86)\OpenFileLauncher` |
| Option 3: PowerShell-Setup | `OpenFileLauncher-Setup.ps1` | `C:\Program Files (x86)\OpenFileLauncher` |

Keines der Setups bringt `config.json` mit (die erlaubten Pfade, siehe [README](README.md)).

---

## ⭐ Option 1: IExpress (bereits in Windows)

**Einfachste Methode - keine zusätzliche Software nötig!**

1. Doppelklick auf `BUILD-SETUP.bat`
2. Warten Sie 30-60 Sekunden
3. Fertig! `OpenFileLauncher-Setup.exe` wurde erstellt

**ODER manuell:**
```batch
iexpress /N setup-config.sed
```

Die `.exe` enthält `launcher.ps1`, `install-openfile-launcher.ps1` und `setup-wrapper.bat`. Der
Wrapper startet das Installations-Skript mit Administrator-Rechten.

---

## 🏆 Option 2: Inno Setup (professioneller)

**Erstellt ein richtiges Windows-Installer-Programm mit Deinstallationsfunktion!**

### Einmalige Vorbereitung:

1. **Download Inno Setup:** https://jrsoftware.org/isdl.php
2. **Installieren Sie** Inno Setup (kostenlos)

### Setup erstellen:

1. **Rechtsklick** auf `OpenFileLauncher-Setup.iss`
2. Wählen Sie **"Compile"**
3. Fertig! Das Setup befindet sich in: `Output\OpenFileLauncher-Setup.exe`

**Vorteile von Inno Setup:**
- ✅ Professioneller Windows-Installer
- ✅ Automatische Deinstallations-Funktion (entfernt auch das Schema `openfile://`)
- ✅ Modernes Setup-Wizard-Interface
- ✅ Digitale Signatur möglich
- ✅ Update-Unterstützung

Vor dem Kompilieren in `OpenFileLauncher-Setup.iss` den Herausgeber (`MyAppPublisher`,
`MyAppURL`) anpassen – dort stehen noch Platzhalter.

---

## Option 3: PowerShell-Setup (das, was das ERP zum Herunterladen anbietet)

1. Doppelklick auf `BUILD-SIMPLE-SETUP.bat`
2. Es bettet `launcher.ps1` in die Vorlage `OpenFileLauncher-Installer.ps1` ein und schreibt
   `OpenFileLauncher-Setup.ps1`

Zusammen mit `OpenFileLauncher-Install.bat` ist das das Paket, das das ERP als
`OpenFileLauncher-Setup.zip` zum Herunterladen anbietet (siehe [README](README.md), Abschnitt
„Setup-Download aus dem ERP“). Die eingecheckte `OpenFileLauncher-Setup.ps1` enthält noch eine
ältere `launcher.ps1` – nach Änderungen also neu erzeugen.

---

## 📦 Das fertige Setup verteilen

Nach der Erstellung haben Sie eine einzelne Setup-Datei (bei Option 3 zusammen mit
`OpenFileLauncher-Install.bat`).

**Diese Datei können Sie verteilen:**
1. Per E-Mail
2. Auf Netzwerkfreigabe
3. Per USB-Stick
4. Per Software-Deployment-System (SCCM/Intune)

**Installation auf Client:**
- Einfach **Doppelklick** auf die .exe (bzw. `OpenFileLauncher-Install.bat` als Administrator)
- Fertig! ✅

---

## 🔄 Setup aktualisieren

Wenn Sie `launcher.ps1` ändern:

1. Ersetzen Sie die Datei in diesem Ordner
2. Führen Sie erneut `BUILD-SETUP.bat` bzw. `BUILD-SIMPLE-SETUP.bat` aus ODER kompilieren Sie die .iss-Datei
3. Verteilen Sie das neue Setup

---

## ℹ️ Empfehlung

- **Für schnelles Testing:** IExpress (Option 1)
- **Für Produktion:** Inno Setup (Option 2)
