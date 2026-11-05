@echo off
setlocal
cd /d "%~dp0"
set "MIRROR_JDK=%JAVA_HOME%"
if defined MIRROR_JDK if exist "%MIRROR_JDK%\bin\javac.exe" goto compile
set "MIRROR_JDK=C:\Program Files\Java\jdk1.8.0_92"
if exist "%MIRROR_JDK%\bin\javac.exe" goto compile
echo JDK introuvable. Definissez JAVA_HOME vers le dossier de votre JDK.
pause
exit /b 1
:compile
if not exist "build\classes" mkdir "build\classes"
if not exist "dist\lib" mkdir "dist\lib"
"%MIRROR_JDK%\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -d "build\classes" src\mirror\*.java
if errorlevel 1 goto failure
copy /y "lib\mysql-connector-j-9.7.0.jar" "dist\lib\mysql-connector-j-9.7.0.jar" >nul
if errorlevel 1 goto failure
"%MIRROR_JDK%\bin\jar.exe" cfm "dist\Demo_Java_mysql_Mirror_Server.jar" MANIFEST.MF -C "build\classes" mirror
if errorlevel 1 goto failure
echo Compilation reussie. Ouvrez Lancer.bat.
exit /b 0
:failure
echo Echec de compilation. Consultez le message ci-dessus.
pause
exit /b 1
