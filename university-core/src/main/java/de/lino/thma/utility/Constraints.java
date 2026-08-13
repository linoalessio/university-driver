package de.lino.thma.utility;

import java.nio.file.Path;

/**
 * Shared, process-wide constants used across the application.
 */
public final class Constraints {

    /**
     * Home path of the application's configuration and local database.
     *
     * <p>Anchored to the current user's home directory rather than the process's
     * working directory: a double-clicked packaged app is not launched from the
     * project directory, so a relative path here would silently point the packaged app
     * at a fresh, empty location instead of the same database {@code mvn javafx:run}
     * and {@code java -jar} use.
     *
     * <p>Follows each OS's own per-user application data convention: macOS's
     * {@code ~/Library/Application Support}, Windows's {@code %APPDATA%} (Roaming), and
     * the XDG {@code ~/.config} convention elsewhere (e.g. Linux).
     */
    public static final Path CONFIGURATION_PATH = resolveConfigurationPath();

    /**
     * Export path of the application's exports: the current user's Downloads folder,
     * which shares the same name directly under the user's home directory on both
     * macOS and Windows.
     */
    public static final Path EXPORT_PATH = Path.of(System.getProperty("user.home"), "Downloads");

    private static Path resolveConfigurationPath() {
        String userHome = System.getProperty("user.home");
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null ? Path.of(appData) : Path.of(userHome, "AppData", "Roaming");
            return base.resolve("University Driver");
        }
        if (osName.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "University Driver");
        }
        return Path.of(userHome, ".config", "University Driver");
    }

    /**
     * Not instantiable; all functionality is exposed through static members.
     */
    private Constraints() {
    }

}
