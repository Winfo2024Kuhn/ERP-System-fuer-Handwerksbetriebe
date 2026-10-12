; ====================================================================
; OpenFile Launcher - Inno Setup Skript
; ====================================================================
; Erstellt ein professionelles Windows-Setup-Programm
;
; VERWENDUNG:
; 1. Installieren Sie Inno Setup ab Version 6.3: https://jrsoftware.org/isdl.php
;    Vorher auf dem ERP-Rechner Setup-Freigabe.ps1 ausfuehren (legt config.json hierher)
; 2. Rechtsklick auf diese .iss-Datei -> "Compile"
; 3. Das Setup wird als "Output\OpenFileLauncher-Setup.exe" erstellt
; ====================================================================

#define MyAppName "OpenFile Launcher"
#define MyAppVersion "1.0"
#define MyAppPublisher "Example Company"
#define MyAppURL "https://example-company.de"

[Setup]
AppId={{A8F3D9C1-5E2B-4A7D-9F1C-3B8E6D4A2F7E}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
; 64-Bit-Modus: {autopf} ist dann "C:\Program Files" - derselbe Ort wie
; install-openfile-launcher.ps1 und Setup-Freigabe.ps1 (nicht "Program Files (x86)")
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DefaultDirName={autopf}\OpenFileLauncher
; Nicht den Ordner einer frueheren x86-Installation weiterverwenden
UsePreviousAppDir=no
DisableDirPage=yes
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=Output
OutputBaseFilename=OpenFileLauncher-Setup
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
WizardStyle=modern
UninstallDisplayIcon={app}\launcher.ps1

[Languages]
Name: "german"; MessagesFile: "compiler:Languages\German.isl"

[Files]
Source: "launcher.ps1"; DestDir: "{app}"; Flags: ignoreversion
; Erlaubte Netzwerkpfade, erzeugt von Setup-Freigabe.ps1. Ohne sie oeffnet der Launcher nichts.
Source: "config.json"; DestDir: "{app}"; Flags: ignoreversion
Source: "README.md"; DestDir: "{app}"; Flags: ignoreversion
Source: "SCHNELLSTART.txt"; DestDir: "{app}"; Flags: ignoreversion

[Registry]
; Registriere openfile:// URL-Schema
Root: HKCR; Subkey: "openfile"; ValueType: string; ValueName: ""; ValueData: "URL:OpenFile Protocol"; Flags: uninsdeletekey
Root: HKCR; Subkey: "openfile"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKCR; Subkey: "openfile\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "shell32.dll,0"
Root: HKCR; Subkey: "openfile\shell\open\command"; ValueType: string; ValueName: ""; ValueData: "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\launcher.ps1"" ""%1"""

[Messages]
german.WelcomeLabel2=Dieses Programm wird [name/ver] auf Ihrem Computer installieren.%n%nDer OpenFile Launcher ermöglicht das Öffnen von Dateien über openfile://-Links, insbesondere für CAD-Zeichnungen und Excel-Dateien von Netzwerkfreigaben.
german.FinishedLabel=Die Installation von [name] wurde erfolgreich abgeschlossen.%n%nSie können jetzt openfile://-Links verwenden!

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    MsgBox('OpenFile Launcher wurde erfolgreich installiert!' + #13#10 + #13#10 + 
           'Das openfile:// URL-Schema ist jetzt registriert.' + #13#10 + 
           'Sie können jetzt Dateien über openfile://-Links öffnen.', 
           mbInformation, MB_OK);
  end;
end;
