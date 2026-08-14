package de.lino.thma;

import javafx.application.Application;

/**
 * Plain entry point for the packaged jar/app bundle, delegating straight to
 * {@link UniversityGui#main(String[])}.
 *
 * <p>The jar's own {@code Main-Class} (see {@code maven-shade-plugin} in {@code pom.xml})
 * must not extend {@link Application} directly: {@code java -jar} specifically detects
 * that case ahead of even loading the class, and refuses to start with "Error: JavaFX
 * runtime components are missing" - even though the JavaFX classes and native libraries
 * are actually present in the shaded jar. Routing through this separate, non-{@link
 * Application} class works around that check.
 */
public final class Launcher {

    /**
     * Not instantiable; all functionality is exposed through {@link #main(String[])}.
     */
    private Launcher() {
    }

    /**
     * Delegates straight to {@link UniversityGui#main(String[])}.
     *
     * @param args the arguments passed from the command line
     */
    public static void main(final String[] args) {
        UniversityGui.main(args);
    }

}
