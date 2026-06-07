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

set "JAVA_HOME_FALLBACK=C:\Program Files\Zulu\zulu-21"
set "MAVEN_CMD_FALLBACK=C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
set "JETBRAINS_DIR=C:\Program Files\JetBrains"

set "JAVA_BIN="
set "MVN_CMD="

if exist "%JAVA_HOME_FALLBACK%\bin\java.exe" (
    set "JAVA_HOME=%JAVA_HOME_FALLBACK%"
    set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_BIN (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" (
            set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
        )
    )
)

if not defined JAVA_BIN (
    echo [ERROR] Could not find a Java 21 runtime.
    echo         Please install JDK 21 or set JAVA_HOME to a JDK 21 directory.
    exit /b 1
)

if defined MAVEN_HOME (
    if exist "%MAVEN_HOME%\bin\mvn.cmd" (
        set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
    )
)

if not defined MVN_CMD (
    if exist "%MAVEN_CMD_FALLBACK%" (
        set "MVN_CMD=%MAVEN_CMD_FALLBACK%"
    )
)

if not defined MVN_CMD (
    for /f "delims=" %%I in ('dir /b /ad /o-n "%JETBRAINS_DIR%\IntelliJ IDEA *" 2^>nul') do (
        if not defined MVN_CMD (
            if exist "%JETBRAINS_DIR%\%%I\plugins\maven\lib\maven3\bin\mvn.cmd" (
                set "MVN_CMD=%JETBRAINS_DIR%\%%I\plugins\maven\lib\maven3\bin\mvn.cmd"
            )
        )
    )
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
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests%DB_PROPS% package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_state
    exit /b 1
)
popd
echo [INFO] Finished underground_state.
echo.

echo [INFO] Building underground_item...
pushd "%ROOT%underground_item"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_item
    exit /b 1
)
popd
echo [INFO] Finished underground_item.
echo.

echo [INFO] Building underground_collect...
pushd "%ROOT%underground_collect"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_collect
    exit /b 1
)
popd
echo [INFO] Finished underground_collect.
echo.

echo [INFO] Building underground_caves...
pushd "%ROOT%underground_caves"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_caves
    exit /b 1
)
popd
echo [INFO] Finished underground_caves.
echo.

echo [INFO] Building underground_mayor...
pushd "%ROOT%underground_mayor"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_mayor
    exit /b 1
)
popd
echo [INFO] Finished underground_mayor.
echo.

echo [INFO] Building underground_velocity Velocity bridge...
pushd "%ROOT%underground_velocity"
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests package
if errorlevel 1 (
    popd
    echo [ERROR] Build failed: underground_velocity
    exit /b 1
)
popd
echo [INFO] Finished underground_velocity Velocity bridge.
echo.

if exist "%ROOT%underground_state\target\underground_state-1.0-shaded.jar" (
    copy /Y "%ROOT%underground_state\target\underground_state-1.0-shaded.jar" "%DIST_DIR%\underground_state-1.0-shaded.jar" >nul
    echo [INFO] Copied underground_state-1.0-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_state\target\underground_state-1.0-shaded.jar
)

if exist "%ROOT%underground_item\target\underground_item-1.0-shaded.jar" (
    copy /Y "%ROOT%underground_item\target\underground_item-1.0-shaded.jar" "%DIST_DIR%\underground_item-1.0-shaded.jar" >nul
    echo [INFO] Copied underground_item-1.0-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_item\target\underground_item-1.0-shaded.jar
)

if exist "%ROOT%underground_collect\target\underground_collect-1.0-shaded.jar" (
    copy /Y "%ROOT%underground_collect\target\underground_collect-1.0-shaded.jar" "%DIST_DIR%\underground_collect-1.0-shaded.jar" >nul
    echo [INFO] Copied underground_collect-1.0-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_collect\target\underground_collect-1.0-shaded.jar
)

if exist "%ROOT%underground_caves\target\underground_caves-1.0-shaded.jar" (
    copy /Y "%ROOT%underground_caves\target\underground_caves-1.0-shaded.jar" "%DIST_DIR%\underground_caves-1.0-shaded.jar" >nul
    echo [INFO] Copied underground_caves-1.0-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_caves\target\underground_caves-1.0-shaded.jar
)

if exist "%ROOT%underground_mayor\target\underground_mayor-1.0-shaded.jar" (
    copy /Y "%ROOT%underground_mayor\target\underground_mayor-1.0-shaded.jar" "%DIST_DIR%\underground_mayor-1.0-shaded.jar" >nul
    echo [INFO] Copied underground_mayor-1.0-shaded.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_mayor\target\underground_mayor-1.0-shaded.jar
)

if exist "%ROOT%underground_velocity\target\underground_velocity-1.0-SNAPSHOT.jar" (
    copy /Y "%ROOT%underground_velocity\target\underground_velocity-1.0-SNAPSHOT.jar" "%DIST_DIR%\underground_velocity-1.0-SNAPSHOT.jar" >nul
    echo [INFO] Copied underground_velocity-1.0-SNAPSHOT.jar
) else (
    echo [WARN] Missing artifact: %ROOT%underground_velocity\target\underground_velocity-1.0-SNAPSHOT.jar
)

echo.
echo [INFO] Build completed successfully.
echo [INFO] Output directory: %DIST_DIR%
exit /b 0
