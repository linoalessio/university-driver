package de.lino.thma.ui.helper;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One column of an {@link EntityTab}'s table: how to read a cell's text from a row via
 * {@link #valueOf()}, and, if the attribute may be corrected after entry, how to write
 * an edited cell's text back onto the row via {@link #setter()}.
 *
 * <p>The same list of specs a tab builds its {@link javafx.scene.control.TableColumn}s
 * from also drives {@link GuiSupport#exportButton(String, javafx.scene.control.TableView, java.util.List)},
 * so a tab's displayed, edited and exported data can never drift apart.
 *
 * @param title the column's header text
 * @param valueOf extracts the cell's text from a row
 * @param setter writes an edited cell's text back onto a row, throwing {@link IllegalArgumentException} if the new text is invalid; {@code null} if this column is read-only
 * @param <T> the row type
 */
public record ColumnSpec<T>(String title, Function<T, String> valueOf, BiConsumer<T, String> setter) {

    /**
     * A read-only column, not editable in the table.
     *
     * @param title the column's header text
     * @param valueOf extracts the cell's text from a row
     * @param <T> the row type
     * @return the column
     */
    public static <T> ColumnSpec<T> of(final String title, final Function<T, String> valueOf) {
        return new ColumnSpec<>(title, valueOf, null);
    }

    /**
     * An editable column: double-clicking a cell lets the user retype it, writing the
     * corrected value back onto the row via {@code setter} once confirmed.
     *
     * @param title the column's header text
     * @param valueOf extracts the cell's text from a row
     * @param setter writes an edited cell's text back onto a row, throwing {@link IllegalArgumentException} if the new text is invalid
     * @param <T> the row type
     * @return the column
     */
    public static <T> ColumnSpec<T> editable(final String title, final Function<T, String> valueOf, final BiConsumer<T, String> setter) {
        return new ColumnSpec<>(title, valueOf, setter);
    }

    /**
     * Whether this column can be edited in the table.
     *
     * @return {@code true} if a {@link #setter()} was given
     */
    boolean isEditable() {
        return this.setter != null;
    }

}
