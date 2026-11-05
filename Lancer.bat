@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\Demo_Java_mysql_Mirror_Server.jar" (
  echo Le fichier de l'application est absent. Extrayez entierement le ZIP.
  echo Vous pouvez aussi lancer Compiler.bat pour reconstruire l'application.
  pause
  exit /b 1
)
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
  "%JAVA_HOME%\bin\java.exe" -jar "dist\Demo_Java_mysql_Mirror_Server.jar"
  if errorlevel 1 pause
  exit /b
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java est introuvable. Java 8 ou plus recent est necessaire.
  pause
  exit /b 1
)
java -jar "dist\Demo_Java_mysql_Mirror_Server.jar"
if errorlevel 1 pause
