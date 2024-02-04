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

While standing in the project's root folder, execute the following command:

```shell
./gradlew build
```

The created JAR can be launched with:

```shell
java -jar ./build/libs/storage-explorer-0.1.0.jar
```

---

## Configuration

The following values can be provided to specify run behaviour:

graph.traversal.inbound=0

- `fs.base.directory`: Declare storage location here.
- `graph.traversal.outbound`: Define how many outbound edges to traverse in a single rendering
  routine. 0 means no outgoing
  edges shall be detected. Negative values mean there is no limit applied (referenced nodes are
  discovered during rendering until exhausted).
- `graph.traversal.inbound`: Define how many inbound edges to discover in a single rendering
  routine. 0 means no inbound
  references shall be searched. Negative values mean there is no limit applied (inbound references
  are discovered until all such references are exhausted).

The properties can be provided by editing
the [application.properties](src/main/resources/application.properties) file and rebuilding the
application, or providing a custom version next to the JAR. Run configuration cannot be changed
without restarting the application.

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
