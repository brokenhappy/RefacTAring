@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Gradle needs a supported JVM before it can apply its Java toolchain settings.
@rem Provision one inside the checkout so a user's JAVA_HOME is irrelevant.
set "BOOTSTRAP_ROOT=%APP_HOME%\.jdks"
set "BOOTSTRAP_JDK=%BOOTSTRAP_ROOT%\temurin-21"
set "JAVA_EXE=%BOOTSTRAP_JDK%\bin\java.exe"
if exist "%JAVA_EXE%" goto execute
set "JAVA_EXE=%BOOTSTRAP_JDK%\Contents\Home\bin\java.exe"
if exist "%JAVA_EXE%" goto execute

if /I "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    set "BOOTSTRAP_ARCH=aarch64"
) else (
    set "BOOTSTRAP_ARCH=x64"
)

echo Downloading Eclipse Temurin 21 for this checkout...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'Stop'; $root = $env:BOOTSTRAP_ROOT; $jdk = Join-Path $root 'temurin-21'; $temporary = Join-Path $root ('.temurin-21-' + [guid]::NewGuid()); New-Item -ItemType Directory -Force -Path $temporary | Out-Null; try { $archive = Join-Path $temporary 'temurin-21.zip'; Invoke-WebRequest -UseBasicParsing -Uri ('https://api.adoptium.net/v3/binary/latest/21/ga/windows/' + $env:BOOTSTRAP_ARCH + '/jdk/hotspot/normal/eclipse') -OutFile $archive; Expand-Archive -LiteralPath $archive -DestinationPath $temporary -Force; $extracted = Get-ChildItem -LiteralPath $temporary -Directory | Select-Object -First 1; if ($null -eq $extracted) { throw 'The downloaded Java archive had an unexpected layout.' }; Move-Item -LiteralPath $extracted.FullName -Destination $jdk -ErrorAction Stop } finally { Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue }"
if not "%ERRORLEVEL%" == "0" (
    echo.
    echo ERROR: Could not download or install Java 21. Check your network connection and run gradlew.bat again.
    goto fail
)
set "JAVA_EXE=%BOOTSTRAP_JDK%\bin\java.exe"
if exist "%JAVA_EXE%" goto execute
set "JAVA_EXE=%BOOTSTRAP_JDK%\Contents\Home\bin\java.exe"
if exist "%JAVA_EXE%" goto execute
echo.
echo ERROR: Java 21 was installed but its java executable was not found.
goto fail

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
