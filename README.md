# University Driver

![Java](https://img.shields.io/badge/Java-21-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.12-blue)
![Build](https://img.shields.io/badge/Build-Maven-C71A36)
![Platform](https://img.shields.io/badge/Platform-macOS%20(Apple%20Silicon)%20%7C%20Windows-lightgrey)

University Driver is a desktop app for tracking a university career: semesters, the
modules taught in each, their exams and grades, each semester's own weekly class
schedule, and the statistics that fall out of them — undergraduate and graduate study
tracked side by side. Built with JavaFX on JDK 21, backed by a local JSON file database
via [`database-driver`](https://github.com/linoalessio/database-driver-v2), with PDF,
Excel, CSV/JSON and full-database export built in, and separate Student and Admin
roles governing which tabs and actions are available.

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
| **Modules**     | Views the global module catalogue (id, name, tag, credit value), entirely read-only — cannot edit a field in place, add a new module, or remove an existing one.                                            |
| **Semesters**   | One tab per *own* semester (never another student's), each with its own nested view of linked modules, that semester's exams, its own weekly **Scheduler**, and semester-scoped statistics — plus a rename action and a retroactive undergraduate/graduate assignment used to group semesters in the Statistics tab. |
| **Exams**       | Created and graded from within the owning semester's own nested tab — name, examiner, date, credits, attempt number and grade, all editable in place. No system-wide exam list; a student only ever sees exams reachable through their own semesters. |
| **Scheduler**   | A semester starts with no scheduler; **"+ Create Scheduler"** builds an empty one. Once created, its **"Periods"** sub-tab defines the day's time slots — each tagged Lecture or Break, added one at a time or all at once via **"Period Layout"** (a named preset — "Winter-Semester" or "Summer-Semester", both the same six lecture periods and five breaks from `table.pdf`'s own timetable, just with slightly different break placement around midday) — and its **"Time Table"** sub-tab lays out the week the same way `table.pdf` does: one row per period, one column per weekday, each occupied cell showing a lecture's module tag, room and professor. Lectures are added/removed/edited (double-click a cell to reconfigure it in place) from a dialog restricted to that semester's own linked modules; a period can only be removed or turned into a break once nothing is still scheduled into it. A dedicated **"Export"** button renders the exact on-screen grid as PDF, Excel, CSV, JSON, XML or Docx. |
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
| **Exports**     | Available from every table — PDF (Apache PDFBox), Excel `.xlsx` (Apache POI), CSV and JSON — styled consistently where formatting applies (shaded header row, bordered cells, banded rows), plus a grouped transcript variant (PDF/Excel/CSV/JSON/XML/Docx) with a closing grading-scale legend. The Scheduler's own Time Table export goes through that same six-format transcript exporter rather than the four-format per-table one above, since it isn't row/column table data in the same sense as the rest. Every export lands in the current user's **Downloads** folder. |
| **Top bar**     | A **"Profile"** dropdown groups the light/dark theme toggle and logout, applied consistently across the main window and every dialog — the chosen theme is persisted to disk and restored on the next launch. An admin account additionally gets a **"Data"** dropdown grouping the one-click zipped database export/import; a student account has no business exporting or overwriting the whole local database, so it doesn't see the dropdown at all. |

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
- **macOS (Apple Silicon) or Windows.** The JavaFX dependency's native classifier is
  picked automatically by a Maven profile in `pom.xml` based on the building machine's
  OS — `mac-aarch64` by default, `win` on Windows (see the `<profiles>` section). To
  build on another platform (Intel Mac, Linux), add a matching profile or override
  `-Djavafx.platform=<classifier>` on the command line (e.g. `mac`, `linux`).
- Network access to Maven Central and the project's GitHub Packages repository (for
  the `database-driver-plugin` dependency this app is built on).

## Installation

There's no pre-built release to download — the app is packaged locally, from source,
on the machine that's going to run it. Two steps: get the source, then build+install a
native app bundle for your OS.

### 1. Get the source

```bash
git clone <this repository's URL>
cd university-driver
```

### 2. Install the app

This runs a full `mvn clean package`, then uses `jpackage` (bundled with JDK 21 — see
[Requirements](#requirements)) to produce a native app bundle and place it where your
OS expects installed apps to live, plus a double-clickable shortcut on the Desktop.
Re-run the same script after pulling changes or editing code to reinstall the updated
build — it always overwrites the previous one in place.

**macOS:**

```bash
./packaging/build-app.sh
```

Produces **"University Driver.app"**, installs it to `/Applications`, and refreshes a
symlink to it on the Desktop (a plain copy there would break under iCloud Desktop
sync — see the script's comments).

Because the app is only ad-hoc signed (no Apple Developer ID), the very first launch
triggers Gatekeeper's "cannot verify developer" warning even though the app is fine —
right-click (or Control-click) it in `/Applications` and choose **Open**, then confirm
in the dialog. This one-time step isn't needed again for that build.

**Windows (PowerShell):**

```powershell
.\packaging\build-app.ps1
```

Produces **"University Driver.exe"** under
`%LOCALAPPDATA%\Programs\University Driver` (no Administrator rights needed), plus a
`.lnk` shortcut on the Desktop. The script auto-detects a JDK 21 install via
`JAVA_HOME` or common per-vendor locations (Temurin, Corretto, Microsoft Build of
OpenJDK); if none is found it stops with instructions to set `JAVA_HOME` yourself.

The build is unsigned, so the first launch shows Windows SmartScreen's "Windows
protected your PC" prompt — click **More info**, then **Run anyway**. Again, only
needed once per build.

--- ---

## Building & Running

Day-to-day development workflows, distinct from the one-time app install above. Run
from the `university-core` Maven module, not the repository root:

```bash
cd university-core
```

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

--- ---

## Configuration & Data

The app stores its configuration and local database under the current user's
per-user application data directory — **not** the project directory — so a
double-clicked packaged app and `mvn javafx:run` always see the same data. The exact
location follows each OS's own convention (see `Constraints.CONFIGURATION_PATH`):

- macOS: `~/Library/Application Support/University Driver/`
  ([Application Support](https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html))
- Windows: `%APPDATA%\University Driver\` (typically
  `C:\Users\<you>\AppData\Roaming\University Driver\`)

```
University Driver/
├── credentials.json      # local JSON database connection settings
└── database/
    ├── profiles/
    ├── logins/
    ├── semesters/     # a semester's own Scheduler, if it has one, is embedded here — not a section of its own
    ├── modules/
    └── exams/
```

**Nothing here needs to be created by hand.** The very first time the app starts
(`mvn javafx:run`, `java -jar`, or the packaged app), `EntityFactory`'s constructor
creates the whole tree above on demand if it's missing — the `University Driver`
directory itself, a default `credentials.json`, the `database` directory, and every
entity type's own subdirectory (`profiles`, `logins`, `semesters`, `modules`,
`exams`), all up front rather than one at a time as each type is first used. The
generated `credentials.json` looks like this:

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
credentials to configure, and this file only needs to exist, not be edited.

`university-core/config/` (holding real personal grade data on the original
development machine) is deliberately excluded from version control (see
`.gitignore`) — it is not shipped with the repo. If you already have an existing
per-user data directory from a previous run of this project (on the same OS), copy it
into place instead of letting a fresh one be generated — `cp -R` on macOS,
`Copy-Item -Recurse` on Windows.

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
│       ├── semester             Semester (holds an optional Scheduler), SemesterType
│       ├── module               Module, Exam
│       └── scheduler            Scheduler, PeriodLayout (named period presets, e.g. "Winter-Semester")
│           ├── time              SchedulerTime (start/end time + LECTURE/BREAK type)
│           └── lesson             Lecture, Break
├── ui
│   ├── LoginGui                the login/register screen shown before the main window
│   ├── helper                  EntityTab, ColumnSpec, GuiSupport, Theme (persisted light/dark),
│   │                           ExamStatistics (shared stat-card/grade helpers)
│   ├── tab                     top-level tabs: Profiles (admin-only), Modules, Exams (admin-only),
│   │                           Semesters (student-only), Statistics (role-dependent)
│   └── subtab                  nested per-semester tabs: SemesterDetailTab (rename + study-type toolbar),
│                               SemesterModulesTab, SemesterExamsTab, SemesterStatisticsTab,
│                               SchedulerTab (Periods + Time Table)
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
