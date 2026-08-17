' PostgreSQL DDL Studio - Silent Windows Launcher (No Command Prompt Window)
Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")
ScriptDir = FSO.GetParentFolderName(WScript.ScriptFullName)
JarPath = Chr(34) & ScriptDir & "\PostgreSQL-DDL-Studio.jar" & Chr(34)

WshShell.Run "javaw -jar " & JarPath, 0, False
Set WshShell = Nothing
Set FSO = Nothing
