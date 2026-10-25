@echo off
setlocal enabledelayedexpansion

set "JAR_FILE="
for %%f in (..\app-swing\build\libs\storage-explorer-swing-*.jar) do set "JAR_FILE=%%~nf"

if not defined JAR_FILE (
  echo No storage-explorer-swing jar found in ..\app-swing\build\libs - run "gradlew app-swing:bootJar" first.
  exit /b 1
)

set "APP_VERSION=!JAR_FILE:storage-explorer-swing-=!"

echo !APP_VERSION!
echo !JAR_FILE!

jpackage -t msi --name "Storage Explorer" --vendor "Szabolcs Bazil Papp" --app-version !APP_VERSION! --input "..\app-swing\build\libs" --main-jar "!JAR_FILE!.jar" --icon "..\app-swing\src\main\resources\icons\favicon.ico" --java-options -XX:InitialRAMPercentage=10 --java-options -XX:MaxRAMPercentage=60 --java-options -XX:+UseZGC --java-options -XX:+ZGenerational --java-options -XX:MinHeapFreeRatio=10 --java-options -XX:MaxHeapFreeRatio=30 --java-options -XX:+UseStringDeduplication --win-shortcut --win-menu --verbose
