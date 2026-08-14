package de.lino.thma.ui.helper;

import de.lino.database.utility.export.ExportCoordinator;
import de.lino.database.utils.export.transcript.TranscriptSection;
import de.lino.database.utils.export.transcript.format.PageFormat;
import de.lino.database.utils.export.transcript.format.PageLayout;
import de.lino.database.utils.export.transcript.format.PageOrientation;
import de.lino.thma.UniversityGui;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.profile.Information;
import de.lino.thma.domain.EntityType;
import de.lino.thma.utility.Constraints;
import de.lino.thma.utility.Serialized;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Static JavaFX helpers shared across the {@link EntityTab} subclasses in this package:
 * building table columns (editable or not) from {@link ColumnSpec}s, exporting a table
 * to PDF or Excel, showing form grids and the shared "add entity" dialog shape, and
 * resolving the next free id for a manually created entity.
 */
public final class GuiSupport {

    /**
     * Not instantiable; all functionality is exposed through static methods.
     */
    private GuiSupport() {
    }

    /**
     * Makes {@code tab} rebuild its own content via {@code rebuild} every time it
     * becomes the selected tab, not just once at construction - none of this app's tabs
     * bind to {@link EntityFactory}'s cache reactively, so a tab left sitting on screen
     * (or simply not the one you happen to switch back to) would otherwise keep showing
     * whatever was true when it was last built, however many changes another tab has
     * made since. {@code rebuild} is expected to tear down and reassemble this tab's own
     * content from scratch, e.g. by calling {@link #toColumns(List, TableView)} and
     * {@code setContent(...)} again exactly as the constructor already did once.
     *
     * <p>Only fires on becoming selected, not on becoming deselected, and never for the
     * very first time a tab is shown - callers still call {@code rebuild} (or run its
     * own constructor logic directly) once up front themselves, the same as before this
     * existed.
     *
     * @param tab the tab to rebuild on every future selection
     * @param rebuild rebuilds {@code tab}'s own content from the current state of {@link EntityFactory}'s cache
     */
    public static void refreshOnSelect(final Tab tab, final Runnable rebuild) {
        tab.setOnSelectionChanged(event -> {
            if (tab.isSelected()) rebuild.run();
        });
    }

    /**
     * Builds the {@link TableColumn}s for {@code table} from {@code specs}, in order.
     * Editable specs get a text-field cell editor whose commit is validated through
     * {@code spec}'s setter: an {@link IllegalArgumentException} it throws is shown via
     * {@link #showValidationError(String)} and the edit is discarded, leaving the
     * underlying entity untouched. A successful commit is immediately persisted via
     * {@link EntityFactory#syncToDatabase()}.
     *
     * @param specs the column definitions to build
     * @param table the table the columns are added to, refreshed after every edit
     * @param <T> the row type
     * @return the built columns, in the same order as {@code specs}
     */
    public static <T> List<TableColumn<T, String>> toColumns(final List<ColumnSpec<T>> specs, final TableView<T> table) {
        return specs.stream().map(spec -> toColumn(spec, table)).toList();
    }

    /**
     * Builds a single {@link TableColumn} from one {@link ColumnSpec}; see
     * {@link #toColumns(List, TableView)}.
     *
     * @param spec the column definition to build
     * @param table the table the column is added to, refreshed after every edit
     * @param <T> the row type
     * @return the built column
     */
    private static <T> TableColumn<T, String> toColumn(final ColumnSpec<T> spec, final TableView<T> table) {

        final TableColumn<T, String> column = stringColumn(spec.title(), spec.valueOf());

        if (spec.isEditable()) {

            column.setCellFactory(TextFieldTableCell.forTableColumn());

            column.setOnEditCommit(event -> {

                try {
                    spec.setter().accept(event.getRowValue(), event.getNewValue());
                    EntityFactory.getInstance().syncToDatabase();
                } catch (final IllegalArgumentException exception) {
                    showValidationError(exception.getMessage());
                }

                table.refresh();

            });

        }

        return column;

    }

    /**
     * Builds a single-value text column, reading its cell text from {@code valueOf}.
     *
     * @param title the column's header text
     * @param valueOf extracts the cell's text from a row
     * @param <T> the row type
     * @return the column
     */
    private static <T> TableColumn<T, String> stringColumn(final String title, final Function<T, String> valueOf) {
        final TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(valueOf.apply(data.getValue())));
        return column;
    }

    /**
     * Builds a menu button offering to export {@code table}'s current rows, laid out
     * exactly as {@code columns} describes them, to a PDF or Excel file in
     * {@link Constraints#EXPORT_PATH}, after letting the user pick a page format and
     * orientation via {@link #promptPageLayout()}.
     *
     * @param title the export's document title, also used as the exported file's base name
     * @param table the table to export the current rows of
     * @param columns the column definitions to export, in display order
     * @param <T> the row type
     * @return the built menu button
     */
    public static <T> MenuButton exportButton(final String title, final TableView<T> table, final List<ColumnSpec<T>> columns) {

        final List<String> headers = columns.stream().map(ColumnSpec::title).toList();
        final Function<T, List<String>> rowMapper = row -> columns.stream().map(spec -> spec.valueOf().apply(row)).toList();

        final MenuItem pdfItem = new MenuItem("Export as PDF");
        pdfItem.setOnAction(event -> promptPageLayout().ifPresent(layout ->
                runExport(table.getItems(), headers, rowMapper, title, title + ".pdf", layout)));

        final MenuItem excelItem = new MenuItem("Export as Excel");
        excelItem.setOnAction(event -> promptPageLayout().ifPresent(layout ->
                runExport(table.getItems(), headers, rowMapper, title, title + ".xlsx", layout)));

        final MenuItem csvItem = new MenuItem("Export as CSV");
        csvItem.setOnAction(event -> promptPageLayout().ifPresent(layout ->
                runExport(table.getItems(), headers, rowMapper, title, title + ".csv", layout)));

        final MenuItem jsonItem = new MenuItem("Export as Json");
        jsonItem.setOnAction(event -> promptPageLayout().ifPresent(layout ->
                runExport(table.getItems(), headers, rowMapper, title, title + ".json", layout)));

        return new MenuButton("Export", null, pdfItem, excelItem, csvItem, jsonItem);

    }

    /**
     * Prompts for the page format and orientation a transcript export should be
     * rendered at, via a small OK/Cancel dialog offering every {@link PageFormat} and
     * {@link PageOrientation}, defaulted to {@link PageLayout#DEFAULT}.
     *
     * @return the chosen layout, or empty if the dialog was cancelled
     */
    public static Optional<PageLayout> promptPageLayout() {

        final ComboBox<PageFormat> formatBox = new ComboBox<>(FXCollections.observableArrayList(PageFormat.values()));
        formatBox.setValue(PageLayout.DEFAULT.format());

        final ComboBox<PageOrientation> orientationBox = new ComboBox<>(FXCollections.observableArrayList(PageOrientation.values()));
        orientationBox.setValue(PageLayout.DEFAULT.orientation());

        final GridPane grid = formGrid("Page format", formatBox, "Orientation", orientationBox);

        final Dialog<ButtonType> dialog = confirmationDialog("Export options", grid, event -> true);

        return dialog.showAndWait()
                .filter(ButtonType.OK::equals)
                .map(button -> new PageLayout(formatBox.getValue(), orientationBox.getValue()));

    }

    /**
     * Runs one export, reporting success or failure via a blocking alert. {@code rows}
     * are wrapped as a single, untitled {@link TranscriptSection} (this table has no
     * grouping of its own) with no closing legend, and written through a fresh
     * {@link ExportCoordinator} whose {@link ExportCoordinator#exportTranscript} resolves
     * the PDF or Excel implementation to write with purely from {@code fileName}'s extension.
     *
     * @param rows the data set to export, in the order it should appear
     * @param headers the column headers, in display order
     * @param rowMapper turns a row into one cell value per header, in the same order as {@code headers}
     * @param title the document title
     * @param fileName the exported file's name, resolved against {@link Constraints#EXPORT_PATH}
     * @param pageLayout the page format and orientation to render the export at
     * @param <T> the type of the exported rows
     */
    private static <T> void runExport(
            final List<T> rows, final List<String> headers,
            final Function<T, List<String>> rowMapper, final String title, final String fileName, final PageLayout pageLayout
    ) {

        final TranscriptSection section = new TranscriptSection("", rows.stream().map(rowMapper).toList());

        final ExportCoordinator coordinator = new ExportCoordinator();

        runExport(() -> coordinator.exportTranscript(title, headers, List.of(section), "", List.of(), pageLayout, Constraints.EXPORT_PATH.resolve(fileName)), fileName);

    }

    /**
     * Functional shape for a self-contained export action that may fail with an
     * {@link IOException}, e.g. one of {@link ExportCoordinator}'s {@code exportXxx}
     * methods already bound to its own arguments via a lambda - {@link #runExport(List, List, Function, String, String, PageLayout)}
     * uses this to share result reporting with callers driving an {@link ExportCoordinator}
     * directly, such as {@link UniversityGui#exportDatabase()} ()} ()}'s archive export.
     */
    @FunctionalInterface
    public interface ExportAction {

        /**
         * Runs the export.
         *
         * @throws IOException if the export cannot be written
         */
        void run() throws IOException;

    }

    /**
     * Runs {@code action}, reporting success or failure via a blocking alert, the same
     * way {@link #runExport(List, List, Function, String, String, PageLayout)}
     * does for the table exporters - exposed publicly so any export action, not just a
     * transcript export wired up through this class, can share the same result
     * reporting.
     *
     * <p>Catches {@link Exception}, not just the {@link IOException} {@code action}
     * itself declares: a JavaFX button handler that lets an unchecked exception escape
     * fails silently (JavaFX logs it to stderr and drops it, with no dialog and no other
     * visible effect) - e.g. {@link EntityFactory#syncToDatabase()} wraps a failure on
     * one of its background virtual threads as an unchecked {@link java.util.concurrent.CompletionException},
     * which would otherwise vanish this way instead of ever reaching this method's own
     * {@code catch}.
     *
     * @param action the export action to run
     * @param fileName the exported file's name, resolved against {@link Constraints#EXPORT_PATH} for display only
     */
    public static void runExport(final ExportAction action, final String fileName) {

        try {
            action.run();
            showSuccess("Exported to " + Constraints.EXPORT_PATH.resolve(fileName).toAbsolutePath());
        } catch (final Exception exception) {
            showValidationError("Export failed: " + exception.getMessage());
        }

    }

    /**
     * Shows a blocking success alert with the given message, styled the same way
     * {@link #showValidationError(String)} styles its own - shared by
     * {@link #runExport(ExportAction, String)} and any other self-reporting action
     * that is not itself an export, such as {@link UniversityGui}'s own database import.
     *
     * @param message the message to display
     */
    public static void showSuccess(final String message) {
        final Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        Theme.style(alert.getDialogPane());
        alert.showAndWait();
    }

    /**
     * Shows a blocking informational alert with an explicit title, styled the same way
     * {@link #showSuccess(String)} styles its own untitled one - for a message that
     * needs a title of its own rather than none, e.g.
     * {@link de.lino.thma.ui.tab.ProfilesTab}'s admin-only credentials reveal.
     *
     * @param title the dialog's title
     * @param message the message to display
     */
    public static void showInfo(final String title, final String message) {
        final Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Theme.style(alert.getDialogPane());
        alert.showAndWait();
    }

    /**
     * Builds a two-column form layout, alternating field labels and their input
     * controls.
     *
     * @param labelsAndFields alternating {@link String} labels and {@link Node} controls, one control per preceding label
     * @return the assembled grid
     */
    public static GridPane formGrid(final Object... labelsAndFields) {

        final GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        for (int i = 0; i < labelsAndFields.length; i += 2) {
            grid.add(new Label((String) labelsAndFields[i]), 0, i / 2);
            grid.add((Node) labelsAndFields[i + 1], 1, i / 2);
        }

        return grid;

    }

    /**
     * Builds a modal OK/Cancel dialog with the given title and content, whose OK
     * button is blocked from closing the dialog while {@code isValid} returns
     * {@code false}. Callers are expected to report why via
     * {@link #showValidationError(String)} from within {@code isValid} before
     * returning {@code false}.
     *
     * @param title the dialog's title
     * @param content the dialog's content
     * @param isValid checked when OK is pressed; returning {@code false} keeps the dialog open
     * @return the built dialog, not yet shown
     */
    public static Dialog<ButtonType> confirmationDialog(
            final String title, final Node content, final Predicate<ActionEvent> isValid
    ) {

        final Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            if (!isValid.test(event)) event.consume();
        });

        Theme.style(dialog.getDialogPane());

        return dialog;

    }

    /**
     * Shows a blocking error alert with the given message.
     *
     * @param message the message to display
     */
    public static void showValidationError(final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        Theme.style(alert.getDialogPane());
        alert.showAndWait();
    }

    /**
     * Shows a blocking Yes/No confirmation alert, for destructive actions such as
     * removing an entity.
     *
     * @param title the dialog's title
     * @param message the message to display
     * @return {@code true} if the user confirmed with "Yes", {@code false} otherwise
     */
    public static boolean confirmDeletion(final String title, final String message) {

        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Theme.style(alert.getDialogPane());

        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();

    }

    /**
     * Builds a {@link StringConverter} for a {@link javafx.scene.control.ComboBox} that
     * displays each item via {@code nameOf} rather than its {@code toString()}.
     *
     * @param nameOf extracts the display text from an item
     * @param <T> the item type
     * @return the converter
     */
    public static <T> StringConverter<T> nameConverter(final Function<T, String> nameOf) {
        return new StringConverter<>() {

            @Override
            public String toString(final T item) {
                return item == null ? "" : nameOf.apply(item);
            }

            @Override
            public T fromString(final String string) {
                return null;
            }

        };
    }

    /**
     * Formats an entity's id for display, e.g. in an "Id" column or export row:
     * {@code "#42"}. Centralized here so the display pattern only needs to change in
     * one place.
     *
     * @param id the id to format
     * @return the formatted id
     */
    public static String idLabel(final int id) {
        return "#" + id;
    }

    /**
     * Computes the next free id for a manually created entity of the given type: one
     * more than the highest id currently registered under {@code type}, or {@code 1}
     * if none are registered yet.
     *
     * @param type the entity type to look up currently registered entities under
     * @param idOf extracts an entity's id
     * @param <T> the entity type
     * @return the next free id
     */
    public static <T extends Serialized> int nextId(final EntityType type, final Function<T, Integer> idOf) {

        final List<T> registered = EntityFactory.getInstance().getEntities(type);

        return registered.stream()
                .map(idOf)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;

    }

    /**
     * Requires {@code value} to not be blank once trimmed, for editable text columns
     * and "add entity" dialogs backing a {@code @NonNull} field.
     *
     * @param value the value to check
     * @param field the field's display name, used in the error message
     * @return {@code value}, trimmed
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static String requireText(final String value, final String field) {

        final String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException(field + " must not be empty.");

        return trimmed;

    }

    /**
     * Parses an edited cell's text as a whole number.
     *
     * @param value the text to parse
     * @param field the field's display name, used in the error message
     * @return the parsed number
     * @throws IllegalArgumentException if {@code value} is not a whole number
     */
    public static int parseInt(final String value, final String field) {

        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a whole number.");
        }

    }

    /**
     * Parses an edited cell's text as a decimal number, accepting a comma as the
     * decimal separator alongside a dot.
     *
     * @param value the text to parse
     * @param field the field's display name, used in the error message
     * @return the parsed number
     * @throws IllegalArgumentException if {@code value} is not a number
     */
    public static double parseDouble(final String value, final String field) {

        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a number.");
        }

    }

    /**
     * Parses an edited cell's text as a date in the given format, to epoch
     * milliseconds at midnight UTC.
     *
     * @param value the text to parse
     * @param format the expected date format
     * @param field the field's display name, used in the error message
     * @return the parsed date, as epoch milliseconds
     * @throws IllegalArgumentException if {@code value} does not match {@code format}
     */
    public static long parseDate(final String value, final DateTimeFormatter format, final String field) {

        try {
            return epochMillisOf(LocalDate.parse(value.trim(), format));
        } catch (final DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be a valid date (dd.MM.yyyy).");
        }

    }

    /**
     * Converts a date to epoch milliseconds at midnight UTC, for constructing
     * {@link Information#getBirthdate()} and
     * {@link Exam#getDate()} values from a
     * {@link javafx.scene.control.DatePicker}'s selected value.
     *
     * @param date the date
     * @return {@code date} at midnight UTC, as epoch milliseconds
     */
    public static long epochMillisOf(final LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

}
