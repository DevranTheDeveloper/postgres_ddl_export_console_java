' ========================================================================
'  🐘 PostgreSQL DDL Studio - Windows Launcher
' ========================================================================
Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")

ScriptDir = FSO.GetParentFolderName(WScript.ScriptFullName)
BatPath = Chr(34) & ScriptDir & "\PostgreSQL-DDL-Studio.bat" & Chr(34)

' Launch the smart batch script with full Java 17 detection and auto-update prompts
WshShell.Run "cmd /c " & BatPath, 1, False

Set WshShell = Nothing
Set FSO = Nothing
