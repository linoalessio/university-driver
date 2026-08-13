package de.lino.thma.ui.helper;

import de.lino.database.json.JsonDocument;
import de.lino.thma.utility.Constraints;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The two visual themes the GUI can be switched between, each backed by its own CSS
 * stylesheet under {@code /theme} on the classpath.
 *
 * <p>The app only ever has one window, so the currently active theme is tracked as
 * simple process-wide state rather than being threaded through every constructor. That
 * lets any {@link Dialog} or {@link javafx.scene.control.Alert} built anywhere in the
 * {@code ui} package pick up the current theme via {@link #style(DialogPane)}, since
 * those render in their own {@link Scene} and would otherwise stay unstyled.
 *
 * <p>The current selection is persisted to {@code configuration.json} under
 * {@link Constraints#CONFIGURATION_PATH}, so it survives across app restarts; see
 * {@link #load()} and {@link #save()}.
 */
public enum Theme {

    /**
     * The light theme, styled by {@code /theme/light.css}.
     */
    LIGHT("/theme/light.css"),

    /**
     * The dark theme, styled by {@code /theme/dark.css}; the default at startup.
     */
    DARK("/theme/dark.css");

    /**
     * This theme's stylesheet, as a classpath resource path.
     */
    private final String resourcePath;

    /**
     * Constructs a theme constant.
     *
     * @param resourcePath this theme's stylesheet, as a classpath resource path
     */
    Theme(final String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /**
     * The file the current theme selection is persisted to and loaded from, alongside
     * the app's other per-user state.
     */
    private static final Path CONFIGURATION_FILE = Constraints.CONFIGURATION_PATH.resolve("configuration.json");

    /**
     * The key {@link #CONFIGURATION_FILE} stores the selected theme's {@link #name()} under.
     */
    private static final String THEME_KEY = "theme";

    /**
     * The theme currently active across the whole application, loaded from
     * {@link #CONFIGURATION_FILE} at startup (see {@link #load()}), defaulting to
     * {@link #DARK} if it is missing, unreadable, or holds an unrecognized value.
     */
    private static Theme current = load();

    /**
     * The currently active theme.
     *
     * @return the current theme
     */
    public static Theme current() {
        return current;
    }

    /**
     * Replaces {@code scene}'s stylesheets with the current theme's.
     *
     * @param scene the scene to style
     */
    public static void applyTo(final Scene scene) {
        scene.getStylesheets().setAll(current.location());
    }

    /**
     * Switches the current theme to the other one, re-applies it to {@code scene}, and
     * persists the new selection to {@link #CONFIGURATION_FILE}.
     *
     * @param scene the main window's scene, restyled with the new theme
     */
    public static void toggle(final Scene scene) {
        current = current == LIGHT ? DARK : LIGHT;
        applyTo(scene);
        save();
    }

    /**
     * Applies the current theme's stylesheet to a dialog or alert's own pane, since
     * {@link Dialog} and {@link javafx.scene.control.Alert} render in their own
     * {@link Scene} rather than inheriting the main window's.
     *
     * @param pane the dialog pane to style
     */
    public static void style(final DialogPane pane) {
        pane.getStylesheets().setAll(current.location());
    }

    /**
     * Resolves this theme's stylesheet resource path to a URL string usable by
     * {@link javafx.scene.Scene#getStylesheets()}.
     *
     * @return this theme's stylesheet location
     * @throws NullPointerException if the stylesheet resource is missing from the classpath
     */
    private String location() {
        return Objects.requireNonNull(
                Theme.class.getResource(this.resourcePath),
                "Missing stylesheet resource: " + this.resourcePath
        ).toExternalForm();
    }

    /**
     * Reads the persisted theme selection from {@link #CONFIGURATION_FILE}.
     *
     * <p>Falls back to {@link #DARK} if the file does not exist yet (e.g. first
     * launch), has no {@link #THEME_KEY} entry, or holds a value that is no longer a
     * recognized {@link Theme} constant - a missing or corrupt configuration file
     * should never prevent the app from starting.
     *
     * @return the persisted theme, or {@link #DARK} if none is available
     */
    private static Theme load() {

        final JsonDocument document = JsonDocument.load(CONFIGURATION_FILE);
        if (!document.contains(THEME_KEY)) return DARK;

        try {
            return Theme.valueOf(document.getString(THEME_KEY));
        } catch (final IllegalArgumentException exception) {
            return DARK;
        }

    }

    /**
     * Persists the currently active theme to {@link #CONFIGURATION_FILE}, creating its
     * parent directory if this is the first time any per-user state has been written.
     */
    private static void save() {
        new JsonDocument().append(THEME_KEY, current.name()).write(CONFIGURATION_FILE);
    }

}
