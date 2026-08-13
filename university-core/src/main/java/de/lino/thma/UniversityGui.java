package de.lino.thma;

import de.lino.database.export.ExportCoordinator;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.profile.login.Login;
import de.lino.thma.ui.LoginGui;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.ui.helper.Theme;
import de.lino.thma.ui.tab.ExamsTab;
import de.lino.thma.ui.tab.ModulesTab;
import de.lino.thma.ui.tab.ProfilesTab;
import de.lino.thma.ui.tab.SemestersTab;
import de.lino.thma.ui.tab.StatisticsTab;
import de.lino.thma.utility.Constraints;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A simple JavaFX window switching between the entity tabs in {@code java.thma.ui}
 * ({@link ProfilesTab}, {@link ExamsTab}, {@link ModulesTab}, {@link SemestersTab},
 * {@link StatisticsTab}) via the {@link TabPane} filling the window, below a top bar
 * carrying a "Data" dropdown
 * (export - see {@link #exportDatabase()} - and import - see
 * {@link #importDatabase(Stage, TabPane, Profile, boolean)} - of the local database) and
 * a "Profile" dropdown (the light/dark {@link Theme} toggle and logout). Each entity tab
 * reads and writes through {@link EntityFactory}'s shared cache, so changes made in one
 * tab are immediately visible in another.
 *
 * <p>The main window is only reachable through a successful {@link LoginGui} login: see
 * {@link #start(Stage)} and {@link #showMainWindow(Stage, Login)}. Every
 * entity type other than {@link EntityType#LOGIN} itself - most notably each student's
 * own {@link Profile} - is only synced from the database
 * once that login succeeds.
 *
 * <p>Modules are registered independently in {@link ModulesTab} and only linked to the
 * semesters that teach them; a student sees exams only nested inside the
 * {@link SemestersTab} tab of the semester they belong to - an admin additionally sees
 * every registered exam, system-wide, in the admin-only {@link ExamsTab}.
 *
 * <p>Standalone entry point, run via {@link #main(String[])} independently of the
 * CLI-driven {@code Main}/{@code UniversityDriverApplication} lifecycle.
 */
public final class UniversityGui extends Application {

    /**
     * Timestamp format embedded in {@link #exportDatabase()}'s backup file name.
     */
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /**
     * Shows the {@link LoginGui} login screen. The main window itself is only built,
     * via {@link #showMainWindow(Stage, Login)}, once a login attempt
     * actually succeeds.
     *
     * @param stage the primary stage provided by the JavaFX runtime
     */
    @Override
    public void start(final Stage stage) {
        showLoginScreen(stage);
        stage.show();
    }

    /**
     * Replaces {@code stage}'s scene with the {@link LoginGui} login screen, wired to
     * open the main window (see {@link #showMainWindow(Stage, Login)}) on a successful
     * login. Shared by {@link #start(Stage)} and the main window's own logout button
     * (see {@link #topBar(Scene, Stage, TabPane, Profile, boolean)}).
     *
     * @param stage the primary stage, whose scene is replaced
     */
    private static void showLoginScreen(final Stage stage) {

        final Scene loginScene = new Scene(new LoginGui(credentials -> showMainWindow(stage, credentials)), 1100, 650);
        Theme.applyTo(loginScene);
        stage.setTitle("University Driver — Login");
        stage.setScene(loginScene);

    }

    /**
     * Builds and shows the main window: the top bar over a {@link TabPane} of every
     * entity tab, styled with the current {@link Theme}. Only called once
     * {@code credentials} has been checked successfully by {@link LoginGui}; every
     * entity type is only synced from the database here, so no entity data - most
     * notably a student's own {@link Profile} - is loaded before that point.
     *
     * <p>The {@link Profile} matching {@code credentials}' own email (see
     * {@link Login#getCurrentProfile()}) is resolved once here and passed, alongside
     * {@link Login#isAdmin()}, to {@link ModulesTab} and {@link StatisticsTab} (which
     * scope what they show, or let be managed, down to that one profile's own unless the
     * account is an admin). {@link SemestersTab} is left out of {@code tabs} entirely for
     * an admin account - the same way the {@link ProfilesTab} - listing every student,
     * and (double-click, or exported) their login credentials in plaintext - and the
     * {@link ExamsTab} - listing, and letting be removed, every registered exam
     * system-wide - are both admin-only, and left out of {@code tabs} entirely otherwise.
     *
     * @param stage the primary stage provided by the JavaFX runtime
     * @param credentials the account that just logged in successfully
     */
    private static void showMainWindow(final Stage stage, final Login credentials) {

        EntityFactory.getInstance().syncFromDatabase();
        final Profile currentProfile = credentials.getCurrentProfile().orElse(null);
        final boolean isAdmin = credentials.isAdmin();

        final TabPane tabs = new TabPane(new ModulesTab(isAdmin), new StatisticsTab(currentProfile, isAdmin));
        if (!isAdmin) tabs.getTabs().add(1, new SemestersTab(currentProfile));
        if (isAdmin) tabs.getTabs().addAll(0, List.of(new ProfilesTab(), new ExamsTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        final BorderPane root = new BorderPane();
        root.setCenter(tabs);

        final Scene scene = new Scene(root, 1100, 650);
        root.setTop(topBar(scene, stage, tabs, currentProfile, isAdmin));
        Theme.applyTo(scene);
        stage.setTitle("University Driver — Welcome " + credentials.getCurrentProfile().orElseThrow().getInformation().getFullName());
        stage.setScene(scene);

    }

    /**
     * Builds the top bar carrying the app's title, a "Data" dropdown grouping the
     * database export (see {@link #exportDatabase()}) and import (see
     * {@link #importDatabase(Stage, TabPane, Profile, boolean)}) actions, a "Profile"
     * dropdown grouping the light/dark {@link Theme} toggle and the logout action, and a
     * quit button closing {@code stage} - closing it this way goes through the same
     * shutdown path as clicking the window's own close button, so
     * {@link #main(String[])}'s post-{@code launch} {@link EntityFactory#syncToDatabase()}
     * still runs.
     *
     * @param scene the main window's scene, restyled whenever the theme toggle is pressed
     * @param stage the main window, closed when the quit button is pressed, and the owner of the import file chooser
     * @param tabs the tab pane rebuilt from scratch once a database import succeeds
     * @param currentProfile the logged-in account's own profile, or {@code null} if it has none; threaded through to {@link #importDatabase(Stage, TabPane, Profile, boolean)}
     * @param isAdmin whether the logged-in account is an admin; threaded through to {@link #importDatabase(Stage, TabPane, Profile, boolean)}
     * @return the built top bar
     */
    private static HBox topBar(final Scene scene, final Stage stage, final TabPane tabs, final Profile currentProfile, final boolean isAdmin) {

        final Label title = new Label("University Driver");
        title.getStyleClass().add("app-title");

        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        final MenuItem exportDatabaseItem = new MenuItem("⬇ Export Database");
        exportDatabaseItem.setOnAction(event -> exportDatabase());

        final MenuItem importDatabaseItem = new MenuItem("⬆ Import Database");
        importDatabaseItem.setOnAction(event -> importDatabase(stage, tabs, currentProfile, isAdmin));

        final MenuButton dataMenu = new MenuButton("Data", null, exportDatabaseItem, importDatabaseItem);

        final MenuButton profileMenu = getProfileMenu(scene, stage);

        final Button quitButton = new Button("⏻ Quit");
        quitButton.getStyleClass().add("button-danger");
        quitButton.setOnAction(event -> stage.close());

        final HBox bar = new HBox(12, title, spacer, dataMenu, profileMenu, quitButton);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));

        return bar;

    }

    private static @NotNull MenuButton getProfileMenu(Scene scene, Stage stage) {
        final MenuItem themeToggleItem = new MenuItem(Theme.current() == Theme.DARK ? "☀ Light Mode" : "🌙 Dark Mode");

        themeToggleItem.setOnAction(event -> {
            Theme.toggle(scene);
            themeToggleItem.setText(Theme.current() == Theme.DARK ? "☀ Light Mode" : "🌙 Dark Mode");
        });

        final MenuItem logoutItem = new MenuItem("↩ Logout");
        logoutItem.setOnAction(event -> showLoginScreen(stage));

        final MenuButton profileMenu = new MenuButton("Profile", null, themeToggleItem, logoutItem);
        return profileMenu;
    }

    /**
     * Exports the entire local database to a timestamped zip archive in the user's
     * Downloads folder, reporting success or failure via the same blocking alert every
     * other export in the app uses. A {@link ExportCoordinator.DirectoryZipExporter},
     * bound to this application's own {@link Constraints#CONFIGURATION_PATH} and
     * flushed via {@link EntityFactory#syncToDatabase()} beforehand, is injected into
     * a fresh {@link ExportCoordinator} rather than called directly.
     */
    private static void exportDatabase() {

        final String fileName = "University Driver Backup " + LocalDateTime.now().format(BACKUP_TIMESTAMP) + ".zip";

        final ExportCoordinator coordinator = new ExportCoordinator();
        coordinator.injectArchiveExporter(new ExportCoordinator.DirectoryZipExporter(
                Constraints.CONFIGURATION_PATH.resolve("database"), () -> EntityFactory.getInstance().syncToDatabase()
        ));

        GuiSupport.runExport(() -> coordinator.exportArchive(Constraints.EXPORT_PATH.resolve(fileName)), fileName);

    }

    /**
     * Restores the local database from a previously {@link #exportDatabase() exported}
     * zip archive picked via a file chooser, after an explicit confirmation since this
     * overwrites every file currently under {@link Constraints#CONFIGURATION_PATH}. On
     * success: {@link EntityFactory#getDatabaseProvider()}'s own
     * {@link de.lino.database.provider.DatabaseProvider#reload() reload()} is called
     * first, since the local JSON store caches everything it has ever read in memory
     * and would otherwise never notice the files this method just overwrote on disk;
     * only then does {@link EntityFactory#syncFromDatabase()} actually see the newly
     * imported data, and {@code tabs} is rebuilt from scratch so every already-open tab
     * reflects it too. A cancelled file chooser or confirmation leaves the app untouched.
     *
     * <p>Catches {@link Exception}, not just {@link IOException}: a JavaFX button
     * handler that lets an unchecked exception escape fails silently (JavaFX logs it to
     * stderr and drops it, with no dialog and no other visible effect), e.g. if the
     * imported data turns out to contain a record {@link EntityFactory#syncFromDatabase()}
     * cannot reconstruct.
     *
     * @param stage the main window, used as the file chooser's owner
     * @param tabs the tab pane rebuilt from scratch once the import succeeds
     * @param currentProfile the logged-in account's own profile, or {@code null} if it has none; re-passed to the rebuilt {@link SemestersTab} and {@link StatisticsTab}
     * @param isAdmin whether the logged-in account is an admin; re-passed to the rebuilt {@link ModulesTab} and {@link StatisticsTab}
     */
    private static void importDatabase(final Stage stage, final TabPane tabs, final Profile currentProfile, final boolean isAdmin) {

        final FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Database");
        chooser.setInitialDirectory(Constraints.EXPORT_PATH.toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Database Backup (*.zip)", "*.zip"));

        final File archive = chooser.showOpenDialog(stage);
        if (archive == null) return;

        if (!GuiSupport.confirmDeletion("Import Database",
                "Importing \"" + archive.getName() + "\" will overwrite the current database. Continue?")) return;

        try {

            extractZip(archive.toPath(), Constraints.CONFIGURATION_PATH);

            EntityFactory.getInstance().getDatabaseProvider().reload();
            EntityFactory.getInstance().syncFromDatabase();

            final List<Tab> rebuiltTabs = new ArrayList<>(List.of(new ModulesTab(isAdmin), new StatisticsTab(currentProfile, isAdmin)));
            if (!isAdmin) rebuiltTabs.add(1, new SemestersTab(currentProfile));
            if (isAdmin) rebuiltTabs.addAll(0, List.of(new ProfilesTab(), new ExamsTab()));
            tabs.getTabs().setAll(rebuiltTabs);

            GuiSupport.showSuccess("Imported from " + archive.getAbsolutePath());

        } catch (final Exception exception) {
            GuiSupport.showValidationError("Import failed: " + exception.getMessage());
        }

    }

    /**
     * Extracts every entry of {@code archive} into {@code targetDirectory}, overwriting
     * any file already there, the inverse of
     * {@link ExportCoordinator.DirectoryZipExporter}. Each entry's resolved path is
     * checked to still fall under {@code targetDirectory} before being written, guarding
     * against a maliciously crafted archive escaping it via a {@code ../} entry name
     * ("zip slip").
     *
     * @param archive the zip file to extract
     * @param targetDirectory the directory to extract {@code archive} into; created if it does not exist
     * @throws IOException if {@code archive} cannot be read, {@code targetDirectory} cannot be written to,
     *                      or an entry's resolved path falls outside {@code targetDirectory}
     */
    private static void extractZip(final Path archive, final Path targetDirectory) throws IOException {

        Files.createDirectories(targetDirectory);
        final Path normalizedTarget = targetDirectory.normalize();

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {

            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {

                final Path resolved = normalizedTarget.resolve(entry.getName()).normalize();

                if (!resolved.startsWith(normalizedTarget)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING);
                }

                zip.closeEntry();

            }

        }

    }

    /**
     * Launches the GUI and persists the in-memory cache back to the database once the
     * window is closed. Only {@link EntityType#LOGIN} is loaded beforehand - the type
     * {@link LoginGui} needs to check an attempted login against - so no other entity
     * data is loaded until that login actually succeeds; see
     * {@link #showMainWindow(Stage, Login)}.
     *
     * @param args the arguments passed from the command line
     */
    public static void main(final String[] args) {
        EntityFactory.getInstance().syncFromDatabase(EntityType.LOGIN);
        launch(args);
        EntityFactory.getInstance().syncToDatabase();
    }

}
