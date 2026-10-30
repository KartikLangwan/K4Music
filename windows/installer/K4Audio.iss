; K4 Audio for Windows — installer, updater and uninstaller.
; Built by `gradlew -p windows packageInstaller`, which passes AppVersion, AppImage, OutDir and RepoRoot.

#ifndef AppVersion
  #define AppVersion "2.14.26"
#endif
#ifndef RepoRoot
  #define RepoRoot "..\.."
#endif
#ifndef AppImage
  #define AppImage RepoRoot + "\windows\build\compose\binaries\main\app\K4 Audio"
#endif
#ifndef OutDir
  #define OutDir RepoRoot + "\windows\build\installer"
#endif

#define AppName "K4 Audio"
#define AppExe "K4 Audio.exe"
; The running app holds this mutex (SingleInstance.kt); the id matches the app's AppUserModelID.
#define AppMutexName "K4AudioAppMutex"
#define AppUserModelID "KartikLangwan.K4Audio"
#define UninstallKey "Software\Microsoft\Windows\CurrentVersion\Uninstall\{6F1D2B7A-4E3C-4A8B-9C21-5D7E3A9B1C04}_is1"

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
VersionInfoDescription={#AppName} Setup
VersionInfoCompany=Kartik Langwan
; Per-user by default (no admin prompt, and yt-dlp can update itself); "all users" stays available.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
DisableWelcomePage=no
LicenseFile={#RepoRoot}\LICENSE
OutputDir={#OutDir}
OutputBaseFilename=K4Audio-Setup-{#AppVersion}
SetupIconFile={#RepoRoot}\windows\icons\k4audio.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
WizardStyle=modern
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
; A running K4 Audio is closed by the [Code] below (politely first); Restart Manager is the backstop.
CloseApplications=yes
RestartApplications=no

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
Name: "startup"; Description: "Start K4 Audio (minimized) when I sign in to Windows"; GroupDescription: "Startup:"; Flags: unchecked

[Registry]
; The same value the app's own "Start with Windows" switch writes (StartupManager.kt); unchecked unless chosen here.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "K4Audio"; ValueData: """{app}\{#AppExe}"" --startup"; Flags: uninsdeletevalue; Tasks: startup
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "K4Music"; Flags: deletevalue

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"; AppUserModelID: "{#AppUserModelID}"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; AppUserModelID: "{#AppUserModelID}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; yt-dlp updates itself in place (app\resources\yt-dlp): remove what Setup did not install there, never the whole folder.
Type: filesandordirs; Name: "{app}\app\resources\yt-dlp"
Type: dirifempty; Name: "{app}\app\resources"
Type: dirifempty; Name: "{app}\app"
Type: dirifempty; Name: "{app}\runtime"
Type: dirifempty; Name: "{app}"

[Code]
var
  PreviousVersion: String;

{ The version already installed (empty on a first install). }
function InstalledVersion(): String;
begin
  Result := '';
  if not RegQueryStringValue(HKCU, '{#UninstallKey}', 'DisplayVersion', Result) then
    RegQueryStringValue(HKLM, '{#UninstallKey}', 'DisplayVersion', Result);
end;

function IsUpdate(): Boolean;
begin
  Result := PreviousVersion <> '';
end;

function SameVersion(): Boolean;
begin
  Result := PreviousVersion = '{#AppVersion}';
end;

function AppRunning(): Boolean;
begin
  Result := CheckForMutexes('{#AppMutexName}');
end;

{ Asks a running K4 Audio to quit (it saves the queue and settings). True once it is gone.
  Never force-kill it (taskkill): Windows Smart App Control then blocks this whole installer.
  A version that does not know --quit is left to Restart Manager (CloseApplications). }
function CloseRunningApp(Exe: String): Boolean;
var
  Code, I: Integer;
begin
  Result := True;
  if not AppRunning() then exit;
  if FileExists(Exe) then
    Exec(Exe, '--quit', '', SW_HIDE, ewNoWait, Code);
  for I := 1 to 60 do
  begin
    if not AppRunning() then exit;
    Sleep(250);
  end;
  Result := not AppRunning();
end;

function InitializeSetup(): Boolean;
begin
  PreviousVersion := InstalledVersion();
  Result := True;
end;

procedure InitializeWizard();
begin
  if not IsUpdate() then exit;
  WizardForm.Caption := 'Update {#AppName}';
  if SameVersion() then
  begin
    WizardForm.WelcomeLabel1.Caption := 'Repair {#AppName}';
    WizardForm.WelcomeLabel2.Caption :=
      '{#AppName} {#AppVersion} is already installed. Setup will install it again to repair it.' + #13#10#13#10 +
      'Your library, playlists, settings and downloads stay as they are.';
  end
  else
  begin
    WizardForm.WelcomeLabel1.Caption := 'Update {#AppName}';
    WizardForm.WelcomeLabel2.Caption :=
      '{#AppName} ' + PreviousVersion + ' is installed on this PC. Setup will update it to version {#AppVersion}.' + #13#10#13#10 +
      'Your library, playlists, settings and downloads stay as they are. If {#AppName} is open, Setup closes it for you and can open it again at the end.';
  end;
end;

{ An update keeps the folder, licence answer and shortcut choices of the first install. }
function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := IsUpdate() and ((PageID = wpLicense) or (PageID = wpSelectDir) or (PageID = wpSelectTasks));
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if not IsUpdate() then exit;
  if CurPageID = wpReady then
  begin
    WizardForm.PageNameLabel.Caption := 'Ready to update';
    WizardForm.PageDescriptionLabel.Caption := '{#AppName} ' + PreviousVersion + '  >  {#AppVersion}';
    WizardForm.ReadyLabel.Caption := 'Click Update to update {#AppName}. It takes a few seconds.';
    WizardForm.NextButton.Caption := 'Update';
  end
  else if CurPageID = wpInstalling then
  begin
    WizardForm.PageNameLabel.Caption := 'Updating {#AppName}';
    WizardForm.PageDescriptionLabel.Caption := 'Please wait while {#AppName} is updated to version {#AppVersion}.';
  end
  else if CurPageID = wpFinished then
  begin
    WizardForm.FinishedHeadingLabel.Caption := '{#AppName} is up to date';
    WizardForm.FinishedLabel.Caption := '{#AppName} was updated to version {#AppVersion}.';
  end;
end;

{ Runs when Install / Update is clicked: a running copy is closed first. }
function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if not AppRunning() then exit;
  { Started from the app's own "Update now" (or silently): no need to ask. }
  if (ExpandConstant('{param:fromapp|0}') <> '1') and (not WizardSilent()) then
    if MsgBox('{#AppName} is open. Setup will close it to continue (the music stops for a moment).',
              mbInformation, MB_OKCANCEL) <> IDOK then
    begin
      Result := 'Close {#AppName} and run Setup again.';
      exit;
    end;
  { Still open (an old version): Setup then offers to close it (CloseApplications). }
  CloseRunningApp(ExpandConstant('{app}\{#AppExe}'));
end;

function InitializeUninstall(): Boolean;
begin
  Result := True;
  if not AppRunning() then exit;
  if not UninstallSilent() then
    if MsgBox('{#AppName} is open. Close it and uninstall?', mbConfirmation, MB_YESNO) <> IDYES then
    begin
      Result := False;
      exit;
    end;
  Result := CloseRunningApp(ExpandConstant('{app}\{#AppExe}'));
  if not Result then
    MsgBox('{#AppName} could not be closed. Close it from the tray icon and try again.', mbError, MB_OK);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  // The app's own "Start with Windows" switch writes this too, not only the installer task: never leave it pointing at nothing.
  if CurUninstallStep = usUninstall then
    RegDeleteValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'K4Audio');
  // The library is the user's data: keep it unless they say otherwise (never asked in silent mode).
  if (CurUninstallStep = usPostUninstall) and (not UninstallSilent) then
    if MsgBox('Also delete your K4 Audio library, playlists, settings and downloads?' + #13#10 +
              'Choose No to keep them for a future install.',
              mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
    begin
      DelTree(ExpandConstant('{userappdata}\K4 Audio'), True, True, True);
      DelTree(ExpandConstant('{localappdata}\K4 Audio'), True, True, True);
    end;
end;
