# Setup-Programm erstellen - Anleitung

Es gibt **zwei Möglichkeiten**, ein Setup-Programm zu erstellen:

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
- ✅ Automatische Deinstallations-Funktion
- ✅ Modernes Setup-Wizard-Interface
- ✅ Digitale Signatur möglich
- ✅ Update-Unterstützung

---

## 📦 Das fertige Setup verteilen

Nach der Erstellung haben Sie eine einzelne `.exe`-Datei:

- **IExpress:** `OpenFileLauncher-Setup.exe` (ca. 20-30 KB)
- **Inno Setup:** `Output\OpenFileLauncher-Setup.exe` (ca. 200-300 KB)

**Diese .exe können Sie verteilen:**
1. Per E-Mail
2. Auf Netzwerkfreigabe
3. Per USB-Stick
4. Per Software-Deployment-System (SCCM/Intune)

**Installation auf Client:**
- Einfach **Doppelklick** auf die .exe
- Fertig! ✅

---

## 🔄 Setup aktualisieren

Wenn Sie `launcher.ps1` ändern:

1. Ersetzen Sie die Datei in diesem Ordner
2. Führen Sie erneut `BUILD-SETUP.bat` ODER kompilieren Sie die .iss-Datei
3. Verteilen Sie die neue Setup.exe

---

## ℹ️ Empfehlung

- **Für schnelles Testing:** IExpress (Option 1)
- **Für Produktion:** Inno Setup (Option 2)
