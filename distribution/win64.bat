@echo off
setlocal enabledelayedexpansion

set "JAR_FILE="
for %%f in (..\app-swing\build\libs\app-swing-*.jar) do set "JAR_FILE=%%~nf"

if not defined JAR_FILE (
  echo No app-swing jar found in ..\app-swing\build\libs - run "gradlew app-swing:bootJar" first.
  exit /b 1
)

set "APP_VERSION=!JAR_FILE:app-swing-=!"

jpackage -t msi --name "Storage Explorer" --vendor "Szabolcs Bazil Papp" --app-version !APP_VERSION! --input "..\app-swing\build\libs" --main-jar "!JAR_FILE!.jar" --icon "..\app-swing\src\main\resources\icons\favicon.ico" --java-options -Xms256m --java-options -Xmx4g --win-shortcut --win-menu --verbose
