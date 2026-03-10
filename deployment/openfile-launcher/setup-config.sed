[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles

[Strings]
InstallPrompt=Möchten Sie den OpenFile Launcher installieren? Dies registriert das openfile:// URL-Schema auf diesem Computer.
DisplayLicense=
FinishMessage=OpenFile Launcher wurde erfolgreich installiert! Sie können jetzt openfile://-Links verwenden.
TargetName=.\OpenFileLauncher-Setup.exe
FriendlyName=OpenFile Launcher Setup
AppLaunched=setup-wrapper.bat
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0="launcher.ps1"
FILE1="install-openfile-launcher.ps1"
FILE2="setup-wrapper.bat"

[SourceFiles]
SourceFiles0=.
[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=
