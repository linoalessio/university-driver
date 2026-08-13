package de.lino.thma.utility;

import java.nio.file.Path;

/**
 * Shared, process-wide constants used across the application.
 */
public final class Constraints {

    /**
     * Home path of the application's configuration and local database.
     *
     * <p>Anchored to the current user's home directory (macOS's own
     * {@code ~/Library/Application Support} convention) rather than the process's
     * working directory: a double-clicked {@code .app} bundle is not launched from the
     * project directory, so a relative path here would silently point the packaged app
     * at a fresh, empty location instead of the same database {@code mvn javafx:run}
     * and {@code java -jar} use.
     */
    public static final Path CONFIGURATION_PATH = Path.of(System.getProperty("user.home"), "Library", "Application Support", "University Driver");

    /**
     * Export path of the application's exports: the current user's Downloads folder.
     */
    public static final Path EXPORT_PATH = Path.of(System.getProperty("user.home"), "Downloads");

    /**
     * Not instantiable; all functionality is exposed through static members.
     */
    private Constraints() {
    }

}
