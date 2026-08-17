' PostgreSQL DDL Studio - Silent Windows Launcher
On Error Resume Next

Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")
ScriptDir = FSO.GetParentFolderName(WScript.ScriptFullName)
JarPath = Chr(34) & ScriptDir & "\PostgreSQL-DDL-Studio.jar" & Chr(34)
BatPath = Chr(34) & ScriptDir & "\PostgreSQL-DDL-Studio.bat" & Chr(34)

' Try launching with javaw
Result = WshShell.Run("javaw -jar " & JarPath, 0, False)

If Err.Number <> 0 Then
    ' If javaw is not found, launch the .bat file which has full diagnostics and Java 17 download prompts
    WshShell.Run "cmd /c " & BatPath, 1, False
End If

Set WshShell = Nothing
Set FSO = Nothing
