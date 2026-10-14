; K4 Music for Windows — installer and uninstaller.
; Built by `gradlew -p windows packageInstaller`, which passes AppVersion, AppImage, OutDir and RepoRoot.

#ifndef AppVersion
  #define AppVersion "2.12.14"
#endif
#ifndef RepoRoot
  #define RepoRoot "..\.."
#endif
#ifndef AppImage
  #define AppImage RepoRoot + "\windows\build\compose\binaries\main\app\K4 Music"
#endif
#ifndef OutDir
  #define OutDir RepoRoot + "\windows\build\installer"
#endif

#define AppName "K4 Music"
#define AppExe "K4 Music.exe"

[Setup]
; Never change AppId: it is how upgrades and the uninstaller find an existing install.
AppId={{6F1D2B7A-4E3C-4A8B-9C21-5D7E3A9B1C04}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=Kartik Langwan
AppPublisherURL=https://k4music.vercel.app
AppSupportURL=https://k4music.vercel.app
AppUpdatesURL=https://k4music.vercel.app
AppCopyright=© 2026 Kartik Langwan
VersionInfoVersion={#AppVersion}.0
VersionInfoProductName={#AppName}
; Per-user by default (no admin prompt, and yt-dlp can update itself); "all users" stays available.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
LicenseFile={#RepoRoot}\LICENSE
OutputDir={#OutDir}
OutputBaseFilename=K4Music-Setup-{#AppVersion}
SetupIconFile={#RepoRoot}\windows\icons\k4music.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
WizardStyle=modern
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
; The app holds this mutex while running, so setup and uninstall can ask to close it first.
AppMutex=K4MusicAppMutex
CloseApplications=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "german"; MessagesFile: "compiler:Languages\German.isl"
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "french"; MessagesFile: "compiler:Languages\French.isl"
Name: "italian"; MessagesFile: "compiler:Languages\Italian.isl"
Name: "japanese"; MessagesFile: "compiler:Languages\Japanese.isl"
Name: "dutch"; MessagesFile: "compiler:Languages\Dutch.isl"
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"
Name: "catalan"; MessagesFile: "compiler:Languages\Catalan.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; yt-dlp updates itself in place; remove whatever it left behind.
Type: filesandordirs; Name: "{app}"

[Code]
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  // The library is the user's data: keep it unless they say otherwise (never asked in silent mode).
  if (CurUninstallStep = usPostUninstall) and (not UninstallSilent) then
    if MsgBox('Also delete your K4 Music library, playlists, settings and downloads?' + #13#10 +
              'Choose No to keep them for a future install.',
              mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
    begin
      DelTree(ExpandConstant('{userappdata}\K4 Music'), True, True, True);
      DelTree(ExpandConstant('{localappdata}\K4 Music'), True, True, True);
    end;
end;
