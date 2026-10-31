#define MyAppName "ChurchCare"
#define MyAppVersion "0.1.0"
#ifndef PublishDir
  #define PublishDir "..\publish\ChurchCare"
#endif
#ifndef OutputDir
  #define OutputDir "..\dist"
#endif

[Setup]
AppId={{A3F4F7E1-5A39-4D44-9D26-7F9B2D4C8A11}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=ChurchCare
DefaultDirName={localappdata}\Programs\ChurchCare
DefaultGroupName=ChurchCare
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir={#OutputDir}
OutputBaseFilename=ChurchCare-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\ChurchCare.exe
ArchitecturesInstallIn64BitMode=x64

[Files]
Source: "{#PublishDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\ChurchCare"; Filename: "{app}\ChurchCare.exe"
Name: "{autodesktop}\ChurchCare"; Filename: "{app}\ChurchCare.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\ChurchCare.exe"; Description: "Launch ChurchCare"; Flags: nowait postinstall skipifsilent
