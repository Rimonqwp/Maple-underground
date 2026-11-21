@echo off
setlocal

set "ROOT=%~dp0"
set "DIST_DIR=%ROOT%dist"
set "MAVEN_REPO=%ROOT%.m2-repo"

REM ---- Centralized DB secrets: real values live in build-secrets.cmd (git-ignored) ----
REM If missing, build with pom defaults (blank passwords; runtime falls back to env vars).
REM See build-secrets.cmd.example for the template.
if exist "%ROOT%build-secrets.cmd" (
    call "%ROOT%build-secrets.cmd"
    echo [INFO] Loaded database secrets from build-secrets.cmd
) else (
    echo [WARN] build-secrets.cmd not found - building with pom defaults ^(blank passwords^).
    echo [WARN] Copy build-secrets.cmd.example to build-secrets.cmd and fill in real credentials.
)

REM Assemble -D properties passed to the underground_state build.
REM Single shared database + account for the whole series (separated by table name).
set "DB_PROPS="
if defined DB_HOST set "DB_PROPS=%DB_PROPS% "-Ddb.host=%DB_HOST%""
if defined DB_PORT set "DB_PROPS=%DB_PROPS% "-Ddb.port=%DB_PORT%""
if defined DB_DATABASE set "DB_PROPS=%DB_PROPS% "-Ddb.database=%DB_DATABASE%""
if defined DB_USER set "DB_PROPS=%DB_PROPS% "-Ddb.user=%DB_USER%""
if defined DB_PASSWORD set "DB_PROPS=%DB_PROPS% "-Ddb.password=%DB_PASSWORD%""

set "JAVA_HOME_FALLBACK=C:\Program Files\Zulu\zulu-25"

set "JAVA_BIN="
set "MVN_CMD="

if defined JAVA_HOME (
    call :UseJavaHome "%JAVA_HOME%"
)
if not defined JAVA_BIN (
    call :UseJavaHome "%JAVA_HOME_FALLBACK%"
)

if not defined JAVA_BIN (
    echo [ERROR] Could not find a JDK 25 or newer.
    echo         Paper 26.3 requires Java 25. Set JAVA_HOME to a JDK 25 or newer directory.
    exit /b 1
)

if defined MAVEN_HOME (
    if exist "%MAVEN_HOME%\bin\mvn.cmd" (
        set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
    )
)

if not defined MVN_CMD (
    if exist "%MAVEN_REPO%\tools\apache-maven-3.9.11\bin\mvn.cmd" (
        set "MVN_CMD=%MAVEN_REPO%\tools\apache-maven-3.9.11\bin\mvn.cmd"
    )
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%ProgramFiles%\JetBrains"
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%ProgramFiles(x86)%\JetBrains"
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%LOCALAPPDATA%\JetBrains"
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%LOCALAPPDATA%\Programs\JetBrains"
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%TEMP%"
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%LOCALAPPDATA%\Temp"
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%ProgramFiles%\Apache"
)

if not defined MVN_CMD (
    call :FindMavenInRoot "%ProgramFiles(x86)%\Apache"
)

if not defined MVN_CMD (
    for /f "delims=" %%I in ('where mvn.cmd 2^>nul') do (
        if not defined MVN_CMD set "MVN_CMD=%%I"
    )
)

if not defined MVN_CMD (
    echo [ERROR] Could not find Maven.
    echo         Please install Maven, set MAVEN_HOME, or use IntelliJ bundled Maven.
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo [INFO] Using JAVA_HOME=%JAVA_HOME%
echo [INFO] Using Maven=%MVN_CMD%
echo.

if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
if not exist "%MAVEN_REPO%" mkdir "%MAVEN_REPO%"

echo [INFO] Building underground_state...
pushd "%ROOT%underground_state"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests%DB_PROPS% clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_state
    exit /b 1
)
popd
if not exist "%ROOT%underground_state\target\underground_state-26.3-shaded.jar" (
    if exist "%ROOT%underground_state\target\underground_state-26.3.jar" (
        copy /Y "%ROOT%underground_state\target\underground_state-26.3.jar" "%ROOT%underground_state\target\underground_state-26.3-shaded.jar" >nul
    )
)
echo [INFO] Finished underground_state.
echo.

echo [INFO] Building underground_item...
pushd "%ROOT%underground_item"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_item
    exit /b 1
)
popd
if not exist "%ROOT%underground_item\target\underground_item-26.3-shaded.jar" (
    if exist "%ROOT%underground_item\target\underground_item-26.3.jar" (
        copy /Y "%ROOT%underground_item\target\underground_item-26.3.jar" "%ROOT%underground_item\target\underground_item-26.3-shaded.jar" >nul
    )
)
echo [INFO] Finished underground_item.
echo.

echo [INFO] Building underground_collect...
pushd "%ROOT%underground_collect"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_collect
    exit /b 1
)
popd
if not exist "%ROOT%underground_collect\target\underground_collect-26.3-shaded.jar" (
    if exist "%ROOT%underground_collect\target\underground_collect-26.3.jar" (
        copy /Y "%ROOT%underground_collect\target\underground_collect-26.3.jar" "%ROOT%underground_collect\target\underground_collect-26.3-shaded.jar" >nul
    )
)
echo [INFO] Finished underground_collect.
echo.

echo [INFO] Building underground_island...
pushd "%ROOT%underground_island"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_island
    exit /b 1
)
popd
if not exist "%ROOT%underground_island\target\underground_island-26.3-shaded.jar" (
    if exist "%ROOT%underground_island\target\underground_island-26.3.jar" (
        copy /Y "%ROOT%underground_island\target\underground_island-26.3.jar" "%ROOT%underground_island\target\underground_island-26.3-shaded.jar" >nul
    )
)
echo [INFO] Finished underground_island.
echo.

echo [INFO] Building underground_caves...
pushd "%ROOT%underground_caves"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_caves
    exit /b 1
)
popd
if not exist "%ROOT%underground_caves\target\underground_caves-26.3-shaded.jar" (
    if exist "%ROOT%underground_caves\target\underground_caves-26.3.jar" (
        copy /Y "%ROOT%underground_caves\target\underground_caves-26.3.jar" "%ROOT%underground_caves\target\underground_caves-26.3-shaded.jar" >nul
    )
)
echo [INFO] Finished underground_caves.
echo.

echo [INFO] Building underground_mayor...
pushd "%ROOT%underground_mayor"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_mayor
    exit /b 1
)
popd
if not exist "%ROOT%underground_mayor\target\underground_mayor-26.3-shaded.jar" (
    if exist "%ROOT%underground_mayor\target\underground_mayor-26.3.jar" (
        copy /Y "%ROOT%underground_mayor\target\underground_mayor-26.3.jar" "%ROOT%underground_mayor\target\underground_mayor-26.3-shaded.jar" >nul
    )
)
echo [INFO] Finished underground_mayor.
echo.


echo [INFO] Building underground_quest...
pushd "%ROOT%underground_quest"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_quest
    exit /b 1
)
popd
echo [INFO] Finished underground_quest.

echo [INFO] Building underground_velocity Velocity bridge...
pushd "%ROOT%underground_velocity"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests clean package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_velocity
    exit /b 1
)
popd
echo [INFO] Finished underground_velocity Velocity bridge.
echo.

if exist "%ROOT%underground_state\target\underground_state-26.3-shaded.jar" (
    copy /Y "%ROOT%underground_state\target\underground_state-26.3-shaded.jar" "%DIST_DIR%\underground_state-26.3-shaded.jar" >nul
    echo [INFO] Copied underground_state-26.3-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_state\target\underground_state-26.3-shaded.jar
)

if exist "%ROOT%underground_item\target\underground_item-26.3-shaded.jar" (
    copy /Y "%ROOT%underground_item\target\underground_item-26.3-shaded.jar" "%DIST_DIR%\underground_item-26.3-shaded.jar" >nul
    echo [INFO] Copied underground_item-26.3-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_item\target\underground_item-26.3-shaded.jar
)

if exist "%ROOT%underground_collect\target\underground_collect-26.3-shaded.jar" (
    copy /Y "%ROOT%underground_collect\target\underground_collect-26.3-shaded.jar" "%DIST_DIR%\underground_collect-26.3-shaded.jar" >nul
    echo [INFO] Copied underground_collect-26.3-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_collect\target\underground_collect-26.3-shaded.jar
)

if exist "%ROOT%underground_island\target\underground_island-26.3-shaded.jar" (
    copy /Y "%ROOT%underground_island\target\underground_island-26.3-shaded.jar" "%DIST_DIR%\underground_island-26.3-shaded.jar" >nul
    echo [INFO] Copied underground_island-26.3-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_island\target\underground_island-26.3-shaded.jar
)

if exist "%ROOT%underground_caves\target\underground_caves-26.3-shaded.jar" (
    copy /Y "%ROOT%underground_caves\target\underground_caves-26.3-shaded.jar" "%DIST_DIR%\underground_caves-26.3-shaded.jar" >nul
    echo [INFO] Copied underground_caves-26.3-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_caves\target\underground_caves-26.3-shaded.jar
)

if exist "%ROOT%underground_mayor\target\underground_mayor-26.3-shaded.jar" (
    copy /Y "%ROOT%underground_mayor\target\underground_mayor-26.3-shaded.jar" "%DIST_DIR%\underground_mayor-26.3-shaded.jar" >nul
    echo [INFO] Copied underground_mayor-26.3-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_mayor\target\underground_mayor-26.3-shaded.jar
)


if exist "%ROOT%underground_quest\target\underground_quest-26.3.jar" (
    copy /Y "%ROOT%underground_quest\target\underground_quest-26.3.jar" "%DIST_DIR%\underground_quest-26.3-shaded.jar" >nul
    echo [INFO] Copied underground_quest-26.3-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_quest\target\underground_quest-26.3.jar
)
if exist "%ROOT%underground_velocity\target\underground_velocity-26.3.jar" (
    copy /Y "%ROOT%underground_velocity\target\underground_velocity-26.3.jar" "%DIST_DIR%\underground_velocity-26.3.jar" >nul
    echo [INFO] Copied underground_velocity-26.3.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_velocity\target\underground_velocity-26.3.jar
)

echo.
echo [INFO] Build completed successfully.
echo [INFO] Output directory: %DIST_DIR%
exit /b 0

:FindMavenInRoot
for /f "delims=" %%I in ('where /r "%~1" mvn.cmd 2^>nul') do (
    if not defined MVN_CMD set "MVN_CMD=%%I"
)
exit /b 0

:UseJavaHome
if not exist "%~1\bin\javac.exe" exit /b 0
set "JAVA_CANDIDATE_VERSION="
for /f "tokens=2 delims==" %%V in ('findstr /B "JAVA_VERSION=" "%~1\release" 2^>nul') do set "JAVA_CANDIDATE_VERSION=%%~V"
for /f "tokens=1 delims=.-+" %%V in ("%JAVA_CANDIDATE_VERSION%") do (
    if %%V GEQ 25 (
        set "JAVA_HOME=%~1"
        set "JAVA_BIN=%~1\bin\java.exe"
    )
)
exit /b 0
