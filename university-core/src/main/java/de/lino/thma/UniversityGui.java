package de.lino.thma;

import de.lino.database.export.ExportCoordinator;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.ui.helper.Theme;
import de.lino.thma.ui.tab.ModulesTab;
import de.lino.thma.ui.tab.SemestersTab;
import de.lino.thma.ui.tab.StatisticsTab;
import de.lino.thma.utility.Constraints;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A simple JavaFX window switching between the entity tabs in {@code java.thma.ui}
 * ({@link ModulesTab}, {@link SemestersTab}, {@link StatisticsTab}) via the
 * {@link TabPane} filling the window, below a top bar carrying an "Export Database"
 * button (see {@link #exportDatabase()}) and a light/dark {@link Theme} toggle. Each
 * entity tab reads and writes through {@link EntityFactory}'s shared cache, so changes
 * made in one tab are immediately visible in another.
 *
 * <p>Modules are registered independently in {@link ModulesTab} and only linked to the
 * semesters that teach them; exams are not listed in their own top-level tab, and only
 * ever appear nested inside the {@link SemestersTab} tab of the semester they belong to.
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
     * Builds and shows the main window: the top bar over a {@link TabPane} of every
     * entity tab, styled with the current {@link Theme}.
     *
     * @param stage the primary stage provided by the JavaFX runtime
     */
    @Override
    public void start(final Stage stage) {

        final TabPane tabs = new TabPane(new ModulesTab(), new SemestersTab(), new StatisticsTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        final BorderPane root = new BorderPane();
        root.setCenter(tabs);

        final Scene scene = new Scene(root, 1100, 650);
        root.setTop(topBar(scene, stage));
        Theme.applyTo(scene);

        stage.setTitle("University Driver");
        stage.setScene(scene);
        stage.show();

    }

    /**
     * Builds the top bar carrying the app's title, a button exporting the entire local
     * database to a zip in the user's Downloads folder (see {@link #exportDatabase()}),
     * a toggle button switching {@code scene} between {@link Theme#LIGHT} and
     * {@link Theme#DARK}, and a quit button closing {@code stage} - closing it this way
     * goes through the same shutdown path as clicking the window's own close button, so
     * {@link #main(String[])}'s post-{@code launch} {@link EntityFactory#syncToDatabase()}
     * still runs.
     *
     * @param scene the main window's scene, restyled whenever the theme toggle is pressed
     * @param stage the main window, closed when the quit button is pressed
     * @return the built top bar
     */
    private static HBox topBar(final Scene scene, final Stage stage) {

        final Label title = new Label("University Driver");
        title.getStyleClass().add("app-title");

        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        final Button exportDatabaseButton = new Button("⬇ Export Database");
        exportDatabaseButton.setOnAction(event -> exportDatabase());

        final ToggleButton themeToggle = new ToggleButton(Theme.current() == Theme.DARK ? "☀ Light Mode" : "🌙 Dark Mode");
        themeToggle.getStyleClass().add("theme-toggle");
        themeToggle.setSelected(Theme.current() == Theme.DARK);
        themeToggle.setOnAction(event -> {
            Theme.toggle(scene);
            themeToggle.setText(Theme.current() == Theme.DARK ? "☀ Light Mode" : "🌙 Dark Mode");
        });

        final Button quitButton = new Button("⏻ Quit");
        quitButton.getStyleClass().add("button-danger");
        quitButton.setOnAction(event -> stage.close());

        final HBox bar = new HBox(12, title, spacer, exportDatabaseButton, themeToggle, quitButton);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));

        return bar;

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
                Constraints.CONFIGURATION_PATH, () -> EntityFactory.getInstance().syncToDatabase()
        ));

        GuiSupport.runExport(() -> coordinator.exportArchive(Constraints.EXPORT_PATH.resolve(fileName)), fileName);

    }

    /**
     * Launches the GUI, loading every entity from the local database beforehand and
     * persisting the in-memory cache back to it once the window is closed.
     *
     * @param args the arguments passed from the command line
     */
    public static void main(final String[] args) {
        EntityFactory.getInstance().syncFromDatabase();
        launch(args);
        EntityFactory.getInstance().syncToDatabase();
    }

}
