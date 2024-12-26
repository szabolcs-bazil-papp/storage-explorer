# Storage Explorer

## Introduction

The application allows exploring smartbit4all file system storages, displaying any persisted
entity and its version history. It is also capable to visualise the persisted objects and
their relationships in a directed graph.

---

## Prerequisites

1. Ensure the [smartbit4all platform](https://github.com/smartbit4all/platform) is present in the
   project's **parent folder**.
2. Make sure JDK 11+ is installed.

---

## Installation and Execution

### Swing App

1. Build the project:
   ```shell
   .\gradlew app-swing:bootJar
   ```
2. Create installer:
   ```shell
   cd .\distribution
   .\win64.bat
   ```
### SpringBoot Starter

1. Build the library:
   ```shell
   .\gradlew spring-boot-starter:build
   .\gradlew spring-boot-starter:publishToMavenLocal
   ```
2. Verify changes by experimenting with app-demo.
---

## Configuration

Configuring runtime behaviour (such as controlling how expansively the graphs are rendered) is
enabled by interfaces on the GUI.

An example [application.properties](src/main/resources/application.properties) file is provided to
set logging levels.

---

## Usage

### Opening storage entries for inspection

Storage entries (objects, lists and maps) can be inspected by:

- clicking the corresponding element in the tree
- clicking any rendered node in the graph
- on the List and Map inspection view, clicking any entry in the `URI` column
- on the Object inspection view, selecting any text containing a valid `URI` and pressing
  the `Ctrl` `Shift` `I` key combination.
- pressing `Ctrl` `Shift` `T` brings up a search dialog; clicking any of the results will dispose of
  the dialog and open the inspection view for it.

### Rendering the graph

While inspecting any storage entry, interact with the `Render` button.

Right-clicking any node on the graph will bring up a context menu, with the option to load and
render more nodes. This option - understandably - won't have any effect if your configuration
already renders all reachable nodes.
