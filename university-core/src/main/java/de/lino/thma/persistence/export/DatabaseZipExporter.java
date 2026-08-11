package de.lino.thma.persistence.export;

import de.lino.database.json.file.FileProvider;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.persistence.EntityType;
import de.lino.thma.utility.Constraints;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zips the application's entire local database directory (see
 * {@link Constraints#CONFIGURATION_PATH}) into a single archive - a full, format-agnostic
 * backup of every registered entity across every {@link EntityType}, as opposed to the
 * other exporters in this package, which each write one entity type's own data as a
 * table or transcript.
 */
public final class DatabaseZipExporter {

    /**
     * Not instantiable; all functionality is exposed through {@link #export(Path)}.
     */
    private DatabaseZipExporter() {
    }

    /**
     * Zips every file under {@link Constraints#CONFIGURATION_PATH}, preserving its
     * directory structure, into an archive at {@code output}. Calls
     * {@link EntityFactory#syncToDatabase()} first, so the archive reflects the very
     * latest state even if some in-memory change has not otherwise been flushed yet.
     *
     * @param output the file path the archive is written to, resolved against {@link Constraints#EXPORT_PATH}; overwritten if it already exists
     * @throws IOException if the local database directory does not exist, or the archive cannot be written
     * @throws NullPointerException if {@code output} is {@code null}
     */
    public static void export(final Path output) throws IOException {

        Objects.requireNonNull(output, "@DatabaseZipExporter.export: output must not be null");

        EntityFactory.getInstance().syncToDatabase();

        final Path sourceDir = Constraints.CONFIGURATION_PATH;

        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("No local database found at " + sourceDir);
        }

        final Path outputPath = Constraints.EXPORT_PATH.resolve(output.getFileName());

        if (Files.exists(outputPath)) Files.delete(outputPath);
        if (!Files.exists(Constraints.EXPORT_PATH)) FileProvider.getInstance().createDirectory(Constraints.EXPORT_PATH);

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(outputPath))) {

            try (Stream<Path> files = Files.walk(sourceDir).filter(Files::isRegularFile)) {

                for (final Path file : (Iterable<Path>) files::iterator) {

                    final String entryName = sourceDir.relativize(file).toString().replace('\\', '/');

                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zip);
                    zip.closeEntry();

                }

            }

        }

    }

}
