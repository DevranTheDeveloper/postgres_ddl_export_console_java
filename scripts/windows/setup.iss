; ========================================================================
;  🐘 PostgreSQL DDL Studio - Professional Windows Setup Wizard (Inno Setup)
; ========================================================================

#define MyAppName "PostgreSQL DDL Studio"
#define MyAppVersion "5.5.6"
#define MyAppPublisher "Devran Sever"
#define MyAppURL "https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java"
#define MyAppExeName "PostgreSQL-DDL-Studio.bat"
#define MyAppIcoName "AppIcon.ico"

[Setup]
; App Metadata
AppId={{E853D432-8419-4C57-9411-9A7246416DFA}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}

; Installation Paths (Supports both Admin Program Files and Non-Admin Per-User)
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
LicenseFile=
OutputDir=..\..\dist
OutputBaseFilename=PostgreSQL-DDL-Studio-Setup-{#MyAppVersion}
SetupIconFile=..\..\src\main\resources\{#MyAppIcoName}
UninstallDisplayIcon={app}\{#MyAppIcoName}

; Compression & Modern Aesthetics
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=auto
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

; Windows PE Version & Security Metadata
VersionInfoVersion=5.5.6.0
VersionInfoTextVersion=5.5.6
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Setup Installer
VersionInfoCopyright=Copyright (C) 2026 {#MyAppPublisher}. All rights reserved.
VersionInfoProductName={#MyAppName}
VersionInfoProductVersion=5.5.6.0
VersionInfoOriginalFileName=PostgreSQL-DDL-Studio-Setup-{#MyAppVersion}.exe
MinVersion=6.1sp1

[Languages]
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "quicklaunchicon"; Description: "{cm:CreateQuickLaunchIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked; OnlyBelowVersion: 6.1; Check: not IsAdminInstallMode

[Files]
; Fat JAR and Launchers
Source: "..\..\target\postgres_ddl_export_console_java-1.0.0.jar"; DestDir: "{app}"; DestName: "PostgreSQL-DDL-Studio.jar"; Flags: ignoreversion
Source: "PostgreSQL-DDL-Studio.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "PostgreSQL-DDL-Studio.vbs"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\src\main\resources\AppIcon.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "README-Windows.txt"; DestDir: "{app}"; Flags: ignoreversion isreadme

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\PostgreSQL-DDL-Studio.bat"; IconFilename: "{app}\AppIcon.ico"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\PostgreSQL-DDL-Studio.bat"; IconFilename: "{app}\AppIcon.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\PostgreSQL-DDL-Studio.bat"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: shellexec postinstall nowait skipifsilent
