package de.lino.thma.ui.helper;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

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
     * The theme currently active across the whole application.
     */
    private static Theme current = DARK;

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
     * Switches the current theme to the other one and re-applies it to {@code scene}.
     *
     * @param scene the main window's scene, restyled with the new theme
     */
    public static void toggle(final Scene scene) {
        current = current == LIGHT ? DARK : LIGHT;
        applyTo(scene);
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

}
