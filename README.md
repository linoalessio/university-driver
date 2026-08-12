# University Driver

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.12-blue)
![Build](https://img.shields.io/badge/Build-Maven-C71A36)
![Platform](https://img.shields.io/badge/Platform-macOS%20(Apple%20Silicon)-lightgrey)

University Driver is a desktop app for tracking a university career: semesters, the
modules taught in each, their exams and grades, and the statistics that fall out of
them — undergraduate and graduate study tracked side by side. Built with JavaFX on
JDK 21, backed by a local JSON file database via [`database-driver`](https://github.com/linoalessio/database-driver-v2),
with PDF, Excel and full-database export built in.

## Features

| **Area**       | **What it does**                                                                                                                                                                                             |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Modules**     | A global catalogue of course modules (name, tag, credit value), created once and linked to whichever semesters teach them.                                                                                 |
| **Semesters**   | One tab per semester, each with its own nested view of linked modules, that semester's exams, and semester-scoped statistics — plus a rename action and a retroactive undergraduate/graduate assignment used to group semesters in the Statistics tab. |
| **Exams**       | Belong to a semester's modules — name, examiner, date, credits, attempt number and grade, all editable in place.                                                                                            |
| **Statistics**  | Undergraduate and graduate study shown side by side, each with summary stat cards and a per-semester breakdown table, plus a combined **"Export All Exams"** action producing a single grouped, official-transcript-style document across every semester. |
| **Exports**     | Available from every table — PDF (Apache PDFBox) and Excel `.xlsx` (Apache POI), styled consistently (shaded header row, bordered cells, banded rows), plus a grouped transcript variant with a closing grading-scale legend and a one-click zipped database backup. Every export lands in the current user's **Downloads** folder. |
| **Theme**       | Light / dark, toggled from the top bar and applied consistently across the main window and every dialog.                                                                                                    |

Every tab reads and writes through the same in-memory entity cache, so a change made
in one tab (e.g. renaming a semester, deleting a module) is immediately reflected
everywhere else it's referenced, and is persisted to disk on every edit.

## Requirements

- **JDK 21** — the Maven build is pinned to it via a
  [toolchain](https://maven.apache.org/guides/mini/guide-using-toolchains.html)
  (`~/.m2/toolchains.xml`), regardless of your system's default `JAVA_HOME`, since
  Lombok's annotation processor silently no-ops under newer JDKs.

  ```xml
  <toolchains>
      <toolchain>
          <type>jdk</type>
          <provides><version>21</version></provides>
          <configuration>
              <jdkHome>/path/to/your/jdk-21</jdkHome>
          </configuration>
      </toolchain>
  </toolchains>
  ```

- **Maven 3.6+**.
- **macOS on Apple Silicon** — the JavaFX dependency is pinned to the `mac-aarch64`
  classifier in `pom.xml`. To run on another platform (Intel Mac, Linux, Windows),
  change the `<classifier>` of the `javafx-controls` dependency to match (e.g. `mac`,
  `linux`, `win`), or remove it and let Maven's OS/architecture detection pick
  automatically.
- Network access to Maven Central and the project's GitHub Packages repository (for
  the `database-driver-plugin` dependency this app is built on).

## Installation

Get the source via git:

```bash
git clone <this repository's URL>
cd university-driver/university-core
```

--- ---

## Building & Running

**Build a self-contained, executable "fat" jar** — all dependencies (JavaFX, PDFBox,
POI, the database driver) shaded in:

```bash
mvn clean package
```

Produces `target/university-core-1.0-SNAPSHOT.jar`.

**Run from source, during development:**

```bash
mvn javafx:run
```

Launches `de.lino.thma.UniversityGui` directly, with the module path assembled
automatically by the `javafx-maven-plugin` — no manual `--module-path` juggling
needed.

**Run from the packaged jar:**

```bash
java -jar target/university-core-1.0-SNAPSHOT.jar
```

**Build a native macOS app bundle:**

```bash
./packaging/build-app.sh
```

Builds the jar, then uses `jpackage` (bundled with JDK 21) to produce
**"University Driver.app"**, installs it to `/Applications`, and refreshes a
double-clickable symlink to it on the Desktop. Re-run this script after any code
change to rebuild the app bundle. Requires the same JDK 21 install as the Maven
toolchain (`jpackage` ships with the JDK itself).

--- ---

## Configuration & Data

The app stores its configuration and local database under the current user's
[Application Support](https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html)
directory — **not** the project directory — so a double-clicked `.app` bundle and
`mvn javafx:run` always see the same data:

```
~/Library/Application Support/University Driver/
├── credentials.json      # local JSON database connection settings
└── database/
    ├── students/
    ├── semesters/
    ├── modules/
    └── exams/
```

This directory is not created automatically, and `university-core/config/` (holding
real personal grade data on the original development machine) is deliberately
excluded from version control (see `.gitignore`) — it is not shipped with the repo.
On a fresh checkout, seed the directory yourself before the first run:

```bash
mkdir -p ~/"Library/Application Support/University Driver"
mkdir -p ~/"Library/Application Support/University Driver"/database/{students,semesters,modules,exams}
```

and create `~/Library/Application Support/University Driver/credentials.json` with:

```json
{
  "address": "Unknown",
  "userName": "Unknown",
  "password": "Unknown",
  "port": -1,
  "database": "Unknown",
  "fileRepository": "database"
}
```

The app talks to a local JSON file store, not a networked SQL database, so every
field above except `fileRepository` is an unused placeholder — there are no real
credentials to configure. If you already have an existing `config/` folder from a
previous run of this project, `cp -R` it into place instead of creating these files
by hand.

Every export (PDF, Excel, database backup) is written to the current user's
**Downloads** folder.

--- ---

## Performance

- **Virtual-thread concurrency** — all database I/O (persisting an entity, deleting
  one, reloading a whole entity type) runs on [virtual threads](https://openjdk.org/jeps/444)
  (`Executors.newVirtualThreadPerTaskExecutor()`), one per task rather than a pooled
  platform thread. Virtual threads are cheap to create and unmount automatically
  while blocked on I/O, so writing or reloading many entities at once (e.g. "Export
  Database", or reloading every entity type on startup) dispatches them all
  concurrently instead of one after another.
- **Copy-on-write caching** — every entity type's in-memory list, and each student's
  per-semester-type enrollment list, is a `CopyOnWriteArrayList`. Reads (populating a
  table, looking up an entity by key) vastly outnumber writes (adding, removing,
  renaming), so lookups proceed without any locking or contention, at the cost of
  copying the backing array on the comparatively rare writes.
- **Bulk registration and persistence** — entities are registered and synced to the
  database in batches rather than one at a time, since each write to a
  `CopyOnWriteArrayList` copies its backing array — batching keeps that cost to once
  per operation instead of once per entity.
- **Client-side export rendering** — PDF and Excel documents are built directly
  against Apache PDFBox / POI's low-level APIs (manual page/row layout, explicit
  column-width computation) rather than a templating layer, keeping large exports
  (e.g. the combined "Export All Exams" transcript across every semester) fast and
  dependency-light.

## Project Structure

```
de.lino.thma
├── Launcher                  non-JavaFX entry point for the packaged jar (see below)
├── UniversityGui              the JavaFX Application; top bar + tab pane
├── domain                     EntityFactory (in-memory cache + persistence) and entities
│   └── entity                 Student, Profile, Semester, SemesterType, Module, Exam
├── persistence                 EntityType registry and the export/ subpackage
│   └── export                  DataExporter, DatabaseZipExporter, PDF/Excel exporters
├── ui
│   ├── helper                  EntityTab, ColumnSpec, GuiSupport, Theme
│   ├── tab                     top-level tabs: Modules, Semesters, Statistics
│   └── subtab                  nested per-semester tabs: Modules, Exams, Statistics
└── utility                     Constraints, Serialized, MultiTaskingFactory, Application lifecycle
```

`Launcher` exists only because `java -jar` refuses to start a jar whose `Main-Class`
extends `javafx.application.Application` directly (misreporting "JavaFX runtime
components are missing" even though they're present in the shaded jar) — it
delegates straight to `UniversityGui.main(String[])`.

--- ---

## Generating Javadoc

Every class, method and field is documented. To generate browsable HTML docs:

```bash
mvn javadoc:javadoc
```

Output is written to `target/site/apidocs/index.html`.
</content>
