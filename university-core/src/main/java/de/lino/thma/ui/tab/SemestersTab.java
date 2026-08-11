package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.Student;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.persistence.EntityType;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.ui.subtab.SemesterDetailTab;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.Comparator;
import java.util.List;

/**
 * The "Semesters" tab: one nested {@link Tab} per registered {@link Semester}, sorted
 * alphabetically by name (see {@link #sortTabs(TabPane)}) and switched between via the
 * {@link TabPane} filling this tab, with an "Add Semester" dialog above it for manually
 * creating new ones.
 *
 * <p>Each nested tab is a {@link SemesterDetailTab}, itself a further-nested tab pane
 * scoping that semester's linked modules, exams and statistics - modules themselves are
 * registered globally in {@link ModulesTab} and only linked here, but exams are only
 * ever shown or created nested inside the semester they belong to.
 */
public final class SemestersTab extends Tab {

    /**
     * Builds the "Semesters" tab: one nested {@link SemesterDetailTab} per registered
     * semester, sorted alphabetically, with add/remove actions.
     */
    public SemestersTab() {

        super("Semesters");

        final List<Semester> semesters = EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .sorted(Comparator.comparing(Semester::getName))
                .toList();

        final TabPane semesterTabs = new TabPane();
        semesterTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        semesters.forEach(semester -> semesterTabs.getTabs().add(new SemesterDetailTab(semester, semesterTabs)));

        final Button addButton = new Button("+ Add Semester");
        addButton.getStyleClass().add("button-primary");
        addButton.setOnAction(event -> addSemesterDialog(semesterTabs));

        final Button removeButton = new Button("− Remove Semester");
        removeButton.getStyleClass().add("button-danger");
        removeButton.disableProperty().bind(semesterTabs.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(event -> removeSelectedSemester(semesterTabs));

        final HBox toolbar = new HBox(8, addButton, removeButton);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        final BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(semesterTabs);

        this.setContent(pane);

    }

    /**
     * Opens a modal dialog for manually creating a new {@link Semester}, with a
     * multi-select list of the currently registered {@link Module}s taught during it.
     * On confirmation the semester is registered in {@link EntityFactory}'s cache, and
     * its own {@link SemesterDetailTab} is appended to {@code semesterTabs} and
     * selected. The semester's name must be unique, since it is a {@link Semester}'s
     * primary key.
     *
     * @param semesterTabs the tab pane to append the newly created semester's tab to
     */
    private static void addSemesterDialog(final TabPane semesterTabs) {

        final TextField nameField = new TextField();

        final List<Module> modules = EntityFactory.getInstance().getEntities(EntityType.MODULES);
        final ListView<Module> moduleList = new ListView<>(FXCollections.observableArrayList(modules));
        moduleList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(final Module module, final boolean empty) {
                super.updateItem(module, empty);
                setText(empty || module == null ? null : module.getName());
            }
        });
        moduleList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        moduleList.setPrefHeight(120);

        final GridPane grid = GuiSupport.formGrid(
                "Name", nameField,
                "Modules", moduleList
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Add Semester", grid, event -> {

            final String name = nameField.getText().trim();

            if (name.isBlank()) {
                GuiSupport.showValidationError("Name must not be empty.");
                return false;
            }

            if (EntityFactory.getInstance().findEntity(EntityType.SEMESTERS, name).isPresent()) {
                GuiSupport.showValidationError("A semester named \"" + name + "\" already exists.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final Integer[] moduleIds = moduleList.getSelectionModel().getSelectedItems().stream()
                    .map(Module::getId)
                    .toArray(Integer[]::new);

            final Semester semester = new Semester(nameField.getText().trim(), moduleIds);

            EntityFactory.getInstance().registerEntitiesInCache(EntityType.SEMESTERS, semester);
            EntityFactory.getInstance().syncToDatabase();

            final Tab detailTab = new SemesterDetailTab(semester, semesterTabs);
            semesterTabs.getTabs().add(detailTab);
            sortTabs(semesterTabs);
            semesterTabs.getSelectionModel().select(detailTab);

        });

    }

    /**
     * Sorts {@code semesterTabs}' own tabs alphabetically by title, e.g. after adding a
     * new one or renaming an existing one from within its own {@link SemesterDetailTab}.
     * Public since a rename is triggered from there, a different package, rather than
     * from here.
     *
     * @param semesterTabs the tab pane to sort
     */
    public static void sortTabs(final TabPane semesterTabs) {
        FXCollections.sort(semesterTabs.getTabs(), Comparator.comparing(Tab::getText));
    }

    /**
     * Removes the currently selected {@link SemesterDetailTab}'s {@link Semester} after
     * confirmation. Every {@link Student} enrolled in it is un-enrolled first (see
     * {@link Student#removeSemester(String)}), since a student's enrollment references
     * the semester by name rather than holding a live reference to it; the semester's
     * linked {@link Module}s are left untouched, since that link lives in the removed
     * {@link Semester} itself, not in the module.
     *
     * @param semesterTabs the tab pane to remove the selected semester's tab from
     */
    private static void removeSelectedSemester(final TabPane semesterTabs) {

        if (!(semesterTabs.getSelectionModel().getSelectedItem() instanceof SemesterDetailTab detailTab)) return;

        final Semester semester = detailTab.getSemester();

        if (!GuiSupport.confirmDeletion("Remove Semester", "Remove \"" + semester.getName()
                + "\"? Students enrolled in it will be un-enrolled.")) return;

        EntityFactory.getInstance().<Student>getEntities(EntityType.STUDENTS)
                .forEach(student -> student.removeSemester(semester.getName()));

        EntityFactory.getInstance().removeEntitiesFromCache(EntityType.SEMESTERS, semester);
        EntityFactory.getInstance().deleteFromDatabase(EntityType.SEMESTERS, semester);
        EntityFactory.getInstance().syncToDatabase();

        semesterTabs.getTabs().remove(detailTab);

    }

}
