package de.lino.thma.ui.helper;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Base for a {@link Tab} listing every registered entity of one type in a
 * {@link TableView}, built from {@link ColumnSpec}s (some of which may be editable, to
 * fix a typo after the fact), with a toolbar above it carrying an export button, an
 * "add entity" button and, optionally, a "remove entity" button acting on the table's
 * current selection.
 *
 * <p>Subclasses call one of the {@code buildContent} overloads from their constructor,
 * after their table has been populated with data, since a table built from entity data
 * cannot be assembled before the mandatory {@code super(title)} call.
 *
 * @param <T> the entity type listed in this tab's table
 */
public abstract class EntityTab<T> extends Tab {

    /**
     * Names the tab; subclasses must still call one of the {@code buildContent}
     * overloads to give it a body.
     *
     * @param title the tab's title
     */
    protected EntityTab(final String title) {
        super(title);
    }

    /**
     * Wraps {@code table} into this tab's content: a {@link BorderPane} with a top
     * toolbar carrying an export button and a single add button. Equivalent to
     * {@link #buildContent(TableView, List, String, String, Runnable, String, Consumer)}
     * with no remove button.
     *
     * @param table the table to display in the tab, already populated with data
     * @param columns the table's columns, built via {@link GuiSupport#toColumns(List, TableView)}, and reused to drive {@code exportTitle}'s export
     * @param exportTitle the exported file's document title and base file name
     * @param addLabel the label of the toolbar's add button
     * @param onAdd invoked when the add button is pressed, to open the entity-specific creation dialog
     */
    protected final void buildContent(
            final TableView<T> table,
            final List<ColumnSpec<T>> columns,
            final String exportTitle,
            final String addLabel,
            final Runnable onAdd
    ) {
        this.buildContent(table, columns, exportTitle, addLabel, onAdd, null, null);
    }

    /**
     * Wraps {@code table} into this tab's content: a {@link BorderPane} with a top
     * toolbar carrying an export button, an add button and a remove button. The remove
     * button is disabled whenever {@code table} has no selection, and invokes
     * {@code onRemove} with the selected row when pressed - callers are expected to
     * confirm the removal (see {@link GuiSupport#confirmDeletion(String, String)})
     * before actually deleting anything from within {@code onRemove}.
     *
     * @param table the table to display in the tab, already populated with data
     * @param columns the table's columns, built via {@link GuiSupport#toColumns(List, TableView)}, and reused to drive {@code exportTitle}'s export
     * @param exportTitle the exported file's document title and base file name
     * @param addLabel the label of the toolbar's add button
     * @param onAdd invoked when the add button is pressed, to open the entity-specific creation dialog
     * @param removeLabel the label of the toolbar's remove button
     * @param onRemove invoked with the selected row when the remove button is pressed, to remove it after confirmation
     */
    protected final void buildContent(
            final TableView<T> table,
            final List<ColumnSpec<T>> columns,
            final String exportTitle,
            final String addLabel,
            final Runnable onAdd,
            final String removeLabel,
            final Consumer<T> onRemove
    ) {

        table.getColumns().addAll(GuiSupport.toColumns(columns, table));
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        final Button addButton = new Button(addLabel);
        addButton.getStyleClass().add("button-primary");
        addButton.setOnAction(event -> onAdd.run());

        final HBox toolbar = new HBox(8, GuiSupport.exportButton(exportTitle, table, columns), addButton);
        toolbar.getStyleClass().add("toolbar");

        if (onRemove != null) {

            final Button removeButton = new Button(removeLabel);
            removeButton.getStyleClass().add("button-danger");
            removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
            removeButton.setOnAction(event -> onRemove.accept(table.getSelectionModel().getSelectedItem()));

            toolbar.getChildren().add(removeButton);

        }

        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        final BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(table);

        this.setContent(pane);

    }

}
