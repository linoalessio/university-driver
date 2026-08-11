package de.lino.thma.utility.application;

import de.lino.thma.utility.application.detection.ProjectType;
import de.lino.thma.utility.application.info.ApplicationProperties;
import lombok.NonNull;

import java.util.Objects;

/**
 * Base class for the single running application instance, modeling its lifecycle as
 * loading, running and ending phases, plus out-of-band exception reporting.
 *
 * <p>Subclasses are expected to register themselves as {@link #getInstance() the
 * singleton instance} by calling {@link #setInstance(Application)} with {@code this},
 * typically from their own constructor, before {@link #getInstance()} is called
 * anywhere else.
 */
public abstract class Application {

    /**
     * The registered application instance, see {@link #getInstance()}.
     */
    private static volatile Application instance;

    /**
     * Registers {@code application} as {@link #getInstance() the singleton instance}.
     *
     * @param application the instance to register, typically {@code this}
     * @throws NullPointerException if {@code application} is {@code null}
     */
    protected void setInstance(final Application application) {
        instance = Objects.requireNonNull(application, "@Application.setInstance: application cannot be null");
    }

    /**
     * Called once, before {@link #onRunning(String[])}, to prepare and load whatever
     * the application needs before it can run, such as a database connection.
     */
    public abstract void onLoading();

    /**
     * Called once the application is shutting down, to release whatever
     * {@link #onLoading()} acquired.
     */
    public abstract void onEnding();

    /**
     * Called once loading has finished, with the arguments passed to the JVM's
     * {@code main(String[])} entry point.
     *
     * @param args the arguments passed from the command line
     */
    public abstract void onRunning(final String[] args);

    /**
     * Called when a {@link RuntimeException} occurs while loading or running the
     * application, so it can be reported or recovered from.
     *
     * @param reason the exception that occurred
     */
    public abstract void onException(final RuntimeException reason);

    /**
     * Returns this application's properties.
     *
     * @return this application's {@link ApplicationProperties}
     */
    public abstract ApplicationProperties getApplicationProperties();

    /**
     * Returns the build tool managing the current project.
     *
     * @return {@code ProjectType.JAVA_PLUGIN}, {@code ProjectType.MAVEN_PLUGIN}, or {@code ProjectType.GRADLE_PLUGIN}
     */
    public abstract ProjectType getProjectBuildType();

    /**
     * Returns the current CLI prefix.
     *
     * @return the prefix
     */
    public abstract String getPrefix();

    /**
     * Sets the CLI prefix.
     *
     * @param prefix the new prefix
     */
    public abstract void setPrefix(@NonNull final String prefix);

    /**
     * Persists the application's current data.
     */
    public abstract void save();

    /**
     * Returns the registered application instance.
     *
     * @return the {@link Application} instance registered via {@link #setInstance(Application)}
     * @throws NullPointerException if no instance has been registered yet
     */
    public static Application getInstance() {
        return Objects.requireNonNull(instance, "@Application.getInstance: no Application instance has been registered yet");
    }

}
