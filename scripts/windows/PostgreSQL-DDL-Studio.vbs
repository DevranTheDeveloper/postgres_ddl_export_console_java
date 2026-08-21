' ========================================================================
'  🐘 PostgreSQL DDL Studio - Windows Launcher
' ========================================================================
Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")

ScriptDir = FSO.GetParentFolderName(WScript.ScriptFullName)
BatPath = Chr(34) & ScriptDir & "\PostgreSQL-DDL-Studio.bat" & Chr(34)

' Launch the smart batch script silently with 0 (completely hidden CMD window)
WshShell.Run "cmd /c " & BatPath, 0, False

Set WshShell = Nothing
Set FSO = Nothing
