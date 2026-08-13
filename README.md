# University Driver

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.12-blue)
![Build](https://img.shields.io/badge/Build-Maven-C71A36)
![Platform](https://img.shields.io/badge/Platform-macOS%20(Apple%20Silicon)-lightgrey)

University Driver is a desktop app for tracking a university career: semesters, the
modules taught in each, their exams and grades, and the statistics that fall out of
them — undergraduate and graduate study tracked side by side. Built with JavaFX on
JDK 21, backed by a local JSON file database via [`database-driver`](https://github.com/linoalessio/database-driver-v2),
with PDF, Excel, CSV/JSON and full-database export built in, and separate Student and
Admin roles governing which tabs and actions are available.

## Features

Every session starts at a login screen; **"Register"** lets anyone without an account
create one on the spot. The very first account ever registered becomes an **Admin**;
every account after that is a **Student**, and self-registration can never grant Admin
to anyone else — the only way to promote someone is for an existing Admin to create
their account as one from the Profiles tab. Which tabs an account sees, and what each
one lets it do, is driven entirely by that one **Role**:

### Student

| **Area**       | **What it does**                                                                                                                                                                                             |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Modules**     | Views the global module catalogue (name, tag, credit value) and may edit those three fields in place — cannot add a new module or remove an existing one.                                                  |
| **Semesters**   | One tab per *own* semester (never another student's), each with its own nested view of linked modules, that semester's exams, and semester-scoped statistics — plus a rename action and a retroactive undergraduate/graduate assignment used to group semesters in the Statistics tab. |
| **Exams**       | Created and graded from within the owning semester's own nested tab — name, examiner, date, credits, attempt number and grade, all editable in place. No system-wide exam list; a student only ever sees exams reachable through their own semesters. |
| **Statistics**  | Undergraduate and graduate study shown side by side, scoped to the student's own semesters — each with summary stat cards and a per-semester breakdown table, plus a combined **"Export All Exams"** action producing a single grouped, official-transcript-style document of just those semesters. |

### Admin

| **Area**       | **What it does**                                                                                                                                                                                             |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Profiles**    | Admin-only tab listing every student, with their personal details editable in place. Double-clicking a **Student** row opens that student's own statistics dialog (their semester/exam/module totals, undergraduate/graduate average grade, and the same scoped "Export All Exams" action) followed by their login credentials in plaintext; double-clicking another **Admin** row shows just its credentials. The tab's own export includes a "Password" column too — both admin-only, since the tab itself is. |
| **Modules**     | Same global catalogue a student sees, plus **"Add Module"** and **"Remove Module"** actions — removing one unlinks it from every semester that teaches it first.                                           |
| **Exams**       | Admin-only tab listing every exam registered system-wide, independent of who it belongs to, with the profile(s) and semester(s) it's reachable through resolved per row, and a remove action — no add action, since an exam is only ever created scoped to one semester's own modules. |
| **Semesters**   | No dedicated "Semesters" tab for an Admin (there's no single semester of their own to show); instead every registered semester across every student is reachable, grouped by name, from the Statistics tab below. |
| **Statistics**  | Replaced entirely by two admin-only sections: an **Admin Overview** of system-wide profile/semester/exam/module counts, and a **"Semesters by Name"** panel grouping every same-named semester across all students (e.g. every student's own "WS23/24") into one row, with its combined profiles/modules/exams listed in a detail view and a single **"Remove Semester"** action deleting it — and every exam sat during it — for every student who owns one at once. |

### Shared by every account

| **Area**       | **What it does**                                                                                                                                                                                             |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Exports**     | Available from every table — PDF (Apache PDFBox), Excel `.xlsx` (Apache POI), CSV and JSON — styled consistently where formatting applies (shaded header row, bordered cells, banded rows), plus a grouped transcript variant (PDF/Excel/CSV/JSON) with a closing grading-scale legend. Every export lands in the current user's **Downloads** folder. |
| **Top bar**     | A **"Data"** dropdown groups the one-click zipped database export/import (available to every account, not just Admin); a **"Profile"** dropdown groups the light/dark theme toggle and logout, applied consistently across the main window and every dialog. The chosen theme is persisted to disk and restored on the next launch. |

Every tab reads and writes through the same in-memory entity cache, and rebuilds
itself from scratch whenever it becomes the selected tab, so a change made in one tab
(e.g. renaming a semester, deleting a module) is immediately reflected everywhere else
it's referenced without restarting the app, and is persisted to disk on every edit.

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
    ├── profiles/
    ├── logins/
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
mkdir -p ~/"Library/Application Support/University Driver"/database/{profiles,logins,semesters,modules,exams}
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

Every export (PDF, Excel, CSV, JSON, database backup) is written to the current
user's **Downloads** folder.

--- ---

## Performance

- **Virtual-thread concurrency** — all database I/O (persisting an entity, deleting
  one, reloading a whole entity type) runs on [virtual threads](https://openjdk.org/jeps/444)
  (`Executors.newVirtualThreadPerTaskExecutor()`), one per task rather than a pooled
  platform thread. Virtual threads are cheap to create and unmount automatically
  while blocked on I/O, so writing or reloading many entities at once (e.g. "Export
  Database", or reloading every entity type on startup) dispatches them all
  concurrently instead of one after another.
- **Copy-on-write caching** — every entity type's in-memory list, and each profile's
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
├── UniversityGui              the JavaFX Application; login-gated top bar + tab pane
├── domain                     EntityFactory (in-memory cache + persistence), EntityType and entities
│   └── entity
│       ├── profile             Profile, Information
│       │   └── login            Login (email/password/role, hash + admin-only plaintext), Role
│       ├── semester             Semester, SemesterType
│       └── module               Module, Exam
├── ui
│   ├── LoginGui                the login/register screen shown before the main window
│   ├── helper                  EntityTab, ColumnSpec, GuiSupport, Theme (persisted light/dark)
│   ├── tab                     top-level tabs: Profiles (admin-only), Modules, Exams (admin-only),
│   │                           Semesters (student-only), Statistics (role-dependent), ExamStatistics (shared stat-card/grade helpers)
│   └── subtab                  nested per-semester tabs: SemesterDetailTab (rename + study-type toolbar),
│                               SemesterModulesTab, SemesterExamsTab, SemesterStatisticsTab
└── utility                     Constraints, Serialized, MultiTaskingFactory, Application lifecycle
```

`Launcher` exists only because `java -jar` refuses to start a jar whose `Main-Class`
extends `javafx.application.Application` directly (misreporting "JavaFX runtime
components are missing" even though they're present in the shaded jar) — it
delegates straight to `UniversityGui.main(String[])`.

Every export (PDF/Excel table, grouped transcript, database backup) goes through
`ExportCoordinator`, which is not part of this project's own tree — it's a generic,
entity-agnostic mechanism shared with any application built on
[`database-driver`](https://github.com/linoalessio/database-driver-v2): the
`DataExporter`/`TranscriptExporter`/`ArchiveExporter` contracts live in
`database-driver-api` (`de.lino.database.export`, `.data`, `.transcript`, `.archiv`),
and `ExportCoordinator` itself, plus its default PDF/Excel/zip implementations, lives in
`database-driver-plugin` (also `de.lino.database.export`). This project only binds
those generic exporters to its own state — see
[`GuiSupport.exportButton`](university-core/src/main/java/de/lino/thma/ui/helper/GuiSupport.java)
(per-table exports), [`StatisticsTab.exportAllExams`](university-core/src/main/java/de/lino/thma/ui/tab/StatisticsTab.java)
(the grouped "Export All Exams" transcript) and
[`UniversityGui.exportDatabase`](university-core/src/main/java/de/lino/thma/UniversityGui.java)
(the database backup button, binding `DirectoryZipExporter` to `Constraints.CONFIGURATION_PATH`
and `EntityFactory::syncToDatabase`).

--- ---

## Generating Javadoc

Every class, method and field is documented. To generate browsable HTML docs:

```bash
mvn javadoc:javadoc
```

Output is written to `target/site/apidocs/index.html`.
</content>
