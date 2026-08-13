package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.EntityType;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.EntityTab;
import de.lino.thma.ui.helper.GuiSupport;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.Comparator;
import java.util.List;

/**
 * The "Modules" tab, listing every registered {@link Module} independently of any
 * {@link Semester}, sorted by id, with an "Add Module" dialog for manually creating new
 * ones. A module created here belongs to no semester until it is linked to one from
 * within that semester's own "Modules" sub-tab (see {@link SemesterModulesTab}), so the
 * same module can be taught across several semesters.
 *
 * <p>Unlike {@link de.lino.thma.ui.tab.SemestersTab}, this tab is never scoped to the
 * logged-in {@link de.lino.thma.domain.entity.profile.Profile}: a {@link Module} belongs
 * to no profile, only to whichever semesters link it, so every profile sees and can
 * manage the same global set of modules.
 *
 * <p>Name, tag and credits are editable in place; the id is not.
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}), so a module added, edited or
 * unlinked from elsewhere - e.g. a semester's own "Modules" sub-tab
 * ({@link de.lino.thma.ui.subtab.SemesterModulesTab}) linking one - is reflected here
 * without restarting the app.
 */
public final class ModulesTab extends EntityTab<Module> {

    /**
     * Builds the "Modules" tab: a table of every registered module, sorted by id, with
     * add/remove actions.
     */
    public ModulesTab() {

        super("Modules");

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from the current state of
     * {@link EntityFactory}'s cache: the table of every registered module, sorted by id,
     * with add/remove actions. Safe to call more than once - see
     * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        final List<Module> modules = EntityFactory.getInstance().<Module>getEntities(EntityType.MODULES).stream()
                .sorted(Comparator.comparingInt(Module::getId))
                .toList();
        final TableView<Module> table = new TableView<>(FXCollections.observableArrayList(modules));

        final List<ColumnSpec<Module>> columns = List.of(
                ColumnSpec.of("Id", m -> GuiSupport.idLabel(m.getId())),
                ColumnSpec.editable("Name", Module::getName, (m, v) -> m.setName(GuiSupport.requireText(v, "Name"))),
                ColumnSpec.editable("Tag", Module::getTag, (m, v) -> m.setTag(GuiSupport.requireText(v, "Tag"))),
                ColumnSpec.editable("Credits", m -> String.valueOf(m.getCredits()), (m, v) -> m.setCredits(GuiSupport.parseInt(v, "Credits")))
        );

        this.buildContent(table, columns, "Modules", "+ Add Module", () -> addModuleDialog(table),
                "− Remove Module", module -> removeModule(module, table));

    }

    /**
     * Opens a modal dialog for manually creating a new {@link Module}, belonging to no
     * semester yet. On confirmation the module is registered in {@link EntityFactory}'s
     * cache and appended to {@code table}.
     *
     * @param table the modules table to append the newly created module to
     */
    private static void addModuleDialog(final TableView<Module> table) {

        final TextField nameField = new TextField();
        final TextField tagField = new TextField();
        final Spinner<Integer> creditsSpinner = new Spinner<>(1, 30, 5);
        creditsSpinner.setEditable(true);

        final GridPane grid = GuiSupport.formGrid(
                "Name", nameField,
                "Tag", tagField,
                "Credits", creditsSpinner
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Add Module", grid, event -> {

            if (nameField.getText().isBlank()) {
                GuiSupport.showValidationError("Name must not be empty.");
                return false;
            }

            if (tagField.getText().isBlank()) {
                GuiSupport.showValidationError("Tag must not be empty.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final Module module = new Module(
                    GuiSupport.nextId(EntityType.MODULES, Module::getId),
                    nameField.getText().trim(),
                    tagField.getText().trim(),
                    creditsSpinner.getValue()
            );

            EntityFactory.getInstance().registerEntitiesInCache(EntityType.MODULES, module);
            EntityFactory.getInstance().syncToDatabase();
            table.getItems().add(module);

        });

    }

    /**
     * Removes {@code module} after confirmation, first unlinking it from every
     * {@link Semester} that currently links to it (see {@link Semester#removeModule(int)}),
     * since that link lives in the semester, not the module.
     *
     * @param module the module to remove
     * @param table the modules table to remove {@code module} from
     */
    private static void removeModule(final Module module, final TableView<Module> table) {

        if (!GuiSupport.confirmDeletion("Remove Module", "Remove \"" + module.getName()
                + "\"? It will be unlinked from every semester that teaches it.")) return;

        EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .filter(semester -> semester.hasModule(module.getId()))
                .forEach(semester -> semester.removeModule(module.getId()));

        EntityFactory.getInstance().removeEntitiesFromCache(EntityType.MODULES, module);
        EntityFactory.getInstance().deleteFromDatabase(EntityType.MODULES, module);
        EntityFactory.getInstance().syncToDatabase();
        table.getItems().remove(module);

    }

}
