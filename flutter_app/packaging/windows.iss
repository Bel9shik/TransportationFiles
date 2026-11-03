#define AppVersion "2.0.0"
[Setup]
AppId={{C52ADDA3-8A9B-4FB8-AF49-11BD75EF13C6}
AppName=TransportationFiles
AppVersion={#AppVersion}
DefaultDirName={localappdata}\TransportationFiles-Native
DefaultGroupName=TransportationFiles
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\build\installer
OutputBaseFilename=TransportationFiles-Windows-X64-Setup
SetupIconFile=..\windows\runner\resources\app_icon.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\transportfiles.exe
[Files]
Source: "..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\TransportationFiles"; Filename: "{app}\transportfiles.exe"
Name: "{autodesktop}\TransportationFiles"; Filename: "{app}\transportfiles.exe"
[Run]
Filename: "{app}\transportfiles.exe"; Description: "Запустить TransportationFiles"; Flags: nowait postinstall skipifsilent
