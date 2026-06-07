@echo off
setlocal

set "ROOT=%~dp0"
set "DIST_DIR=%ROOT%dist"
set "MAVEN_REPO=%ROOT%.m2-repo"

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
call "%MVN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" -DskipTests package
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

echo.
echo [INFO] Build completed successfully.
echo [INFO] Output directory: %DIST_DIR%
exit /b 0
