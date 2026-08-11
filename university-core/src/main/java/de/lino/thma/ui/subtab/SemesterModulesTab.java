package de.lino.thma.ui.subtab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.persistence.EntityType;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.EntityTab;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.ui.tab.ModulesTab;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;

import java.util.Comparator;
import java.util.List;

/**
 * The "Modules" sub-tab of one {@link SemesterDetailTab}: lists the {@link Module}s
 * currently linked to that one {@link Semester} (see {@link Semester#getModules()}),
 * sorted by id, with a "+ Link Module" dialog restricted to modules registered in the
 * global {@link ModulesTab} that are not linked to this semester yet - modules are
 * created only in {@link ModulesTab}, this tab only links or shows already-registered
 * ones, so the same module can be linked to several semesters.
 *
 * <p>Every column is read-only here; editing a module's own name, tag or credits happens
 * in {@link ModulesTab}.
 */
public final class SemesterModulesTab extends EntityTab<Module> {

    /**
     * Builds this semester's "Modules" sub-tab: a read-only table of {@code semester}'s
     * linked modules, sorted by id, with a link action scoped to it.
     *
     * @param semester the semester this tab is scoped to
     */
    SemesterModulesTab(final Semester semester) {

        super("Modules");

        final List<Module> modules = semester.getModules().stream()
                .sorted(Comparator.comparingInt(Module::getId))
                .toList();
        final TableView<Module> table = new TableView<>(FXCollections.observableArrayList(modules));

        final List<ColumnSpec<Module>> columns = List.of(
                ColumnSpec.of("Id", m -> GuiSupport.idLabel(m.getId())),
                ColumnSpec.of("Name", Module::getName),
                ColumnSpec.of("Tag", Module::getTag),
                ColumnSpec.of("Credits", m -> String.valueOf(m.getCredits()))
        );

        this.buildContent(table, columns, semester.getName() + " Modules", "+ Link Module", () -> linkModuleDialog(semester, table));

    }

    /**
     * Opens a modal dialog for linking one of the globally registered {@link Module}s
     * (see {@link ModulesTab}) to {@code semester} via {@link Semester#addModule(int)},
     * offering only modules not already linked to it.
     *
     * @param semester the semester to link the selected module to
     * @param table the modules table to append the newly linked module to
     */
    private static void linkModuleDialog(final Semester semester, final TableView<Module> table) {

        final List<Module> linkable = EntityFactory.getInstance().<Module>getEntities(EntityType.MODULES).stream()
                .filter(module -> !semester.hasModule(module.getId()))
                .toList();

        final ComboBox<Module> moduleBox = new ComboBox<>(FXCollections.observableArrayList(linkable));
        moduleBox.setConverter(GuiSupport.nameConverter(Module::getName));
        moduleBox.setPromptText(linkable.isEmpty() ? "No modules left to link" : "Select a module");

        final GridPane grid = GuiSupport.formGrid("Module", moduleBox);

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Link Module", grid, event -> {

            if (moduleBox.getValue() == null) {
                GuiSupport.showValidationError("Select a module to link.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final Module module = moduleBox.getValue();
            semester.addModule(module.getId());
            EntityFactory.getInstance().syncToDatabase();
            table.getItems().add(module);
            table.getItems().sort(Comparator.comparingInt(Module::getId));

        });

    }

}
