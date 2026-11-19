@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"
set "TARGET_DIR=C:\Users\Mauricio\AppData\Roaming\ModrinthApp\profiles\Minecraft 1.21.6\mods"

echo [1/3] Compilando AeternumCarpet...
call gradlew.bat build
if errorlevel 1 (
    echo [ERROR] Fallo la compilacion.
    exit /b 1
)

set "BUILT_JAR="
for %%F in ("build\libs\aeternum-carpet-*.jar") do (
    echo "%%~nxF" | findstr /i /c:"-sources.jar" >nul
    if errorlevel 1 (
        set "BUILT_JAR=%%~fF"
        set "BUILT_NAME=%%~nxF"
    )
)

if not defined BUILT_JAR (
    echo [ERROR] No se encontro el .jar compilado en build\libs.
    exit /b 1
)

if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"

echo [2/3] Limpiando versiones anteriores en Modrinth...
del /q "%TARGET_DIR%\aeternum-carpet-*.jar" 2>nul

echo [3/3] Copiando %BUILT_NAME% a "%TARGET_DIR%"...
copy /y "%BUILT_JAR%" "%TARGET_DIR%\%BUILT_NAME%" >nul
if errorlevel 1 (
    echo [ERROR] No se pudo copiar el archivo.
    exit /b 1
)

echo Listo: %BUILT_NAME% desplegado en "%TARGET_DIR%".
endlocal
