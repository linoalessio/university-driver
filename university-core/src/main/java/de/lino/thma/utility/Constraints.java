package de.lino.thma.utility;

import de.lino.thma.domain.entity.profile.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Shared, process-wide constants used across the application.
 */
public final class Constraints {

    /**
     * The application's shared {@link Logger}.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger("[University]");

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

    /**
     * Converts a calendar date to epoch milliseconds at midnight UTC, for constructing
     * {@link Profile#getBirthdate()} values from readable year/month/day literals.
     *
     * @param year the year
     * @param month the month, 1-12
     * @param day the day of month
     * @return {@code year-month-day} at midnight UTC, as epoch milliseconds
     */
    public static long birthdateOf(final int year, final int month, final int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

}
