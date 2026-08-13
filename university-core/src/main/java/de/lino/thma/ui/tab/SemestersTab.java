package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.EntityType;
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
 * The "Semesters" tab: one nested {@link Tab} per {@link Semester} owned by the
 * currently logged-in {@link Profile}, sorted alphabetically by name (see
 * {@link #sortTabs(TabPane)}) and switched between via the {@link TabPane} filling this
 * tab, with an "Add Semester" dialog above it for creating new ones under that profile.
 *
 * <p>Each {@link Semester}'s own primary key is {@code "<owner email>;<name>"} (see
 * {@link Semester#Semester(Profile, String, Integer...)}), and that same key is recorded
 * in the owning {@link Profile#getSemesters()} - this tab filters every registered
 * semester down to the ones {@code currentProfile} owns by checking membership in that
 * list.
 *
 * <p>An admin account (see {@link de.lino.thma.domain.entity.profile.login.Login#isAdmin()})
 * bypasses that filtering entirely and sees every registered semester instead, since an
 * admin also owns a {@link Profile} of its own (the first ever registered one, see
 * {@link de.lino.thma.ui.LoginGui}) but is not scoped down to just that one's own
 * semesters the way a student is.
 *
 * <p>Each nested tab is a {@link SemesterDetailTab}, itself a further-nested tab pane
 * scoping that semester's linked modules, exams and statistics - modules themselves are
 * registered globally in {@link ModulesTab} and only linked here, but exams are only
 * ever shown or created nested inside the semester they belong to.
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}), so a semester (or anything nested
 * under one) added, edited or removed elsewhere is reflected here without restarting
 * the app.
 */
public final class SemestersTab extends Tab {

    /**
     * The logged-in account's own profile, or {@code null} if it has none - see
     * {@link #SemestersTab(Profile, boolean)}.
     */
    private final Profile currentProfile;

    /**
     * Whether the logged-in account is an admin, bypassing the per-profile filtering
     * entirely - see {@link #SemestersTab(Profile, boolean)}.
     */
    private final boolean isAdmin;

    /**
     * Builds the "Semesters" tab: one nested {@link SemesterDetailTab} per semester
     * {@code currentProfile} owns (or every registered semester, if {@code isAdmin}),
     * sorted alphabetically, with add/remove actions.
     *
     * @param currentProfile the logged-in account's own profile, or {@code null} if it has none
     * @param isAdmin whether the logged-in account is an admin, bypassing the per-profile filtering entirely
     */
    public SemestersTab(final Profile currentProfile, final boolean isAdmin) {

        super("Semesters");

        this.currentProfile = currentProfile;
        this.isAdmin = isAdmin;

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from the current state of
     * {@link EntityFactory}'s cache: one nested {@link SemesterDetailTab} per semester
     * {@link #currentProfile} owns (or every registered semester, if {@link #isAdmin}),
     * sorted alphabetically, with add/remove actions. Safe to call more than once - see
     * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        final List<Semester> semesters = EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .filter(semester -> this.isAdmin || (this.currentProfile != null && this.currentProfile.getSemesters().contains(semester.getId())))
                .sorted(Comparator.comparing(Semester::getName))
                .toList();

        final TabPane semesterTabs = new TabPane();
        semesterTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        semesters.forEach(semester -> semesterTabs.getTabs().add(new SemesterDetailTab(semester, semesterTabs)));

        final HBox toolbar = new HBox(8);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        if (this.currentProfile != null) {

            final Button addButton = new Button("+ Add Semester");
            addButton.getStyleClass().add("button-primary");
            addButton.setOnAction(event -> addSemesterDialog(this.currentProfile, semesterTabs));
            toolbar.getChildren().add(addButton);

        }

        final Button removeButton = new Button("− Remove Semester");
        removeButton.getStyleClass().add("button-danger");
        removeButton.disableProperty().bind(semesterTabs.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(event -> removeSelectedSemester(semesterTabs));
        toolbar.getChildren().add(removeButton);

        final BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(semesterTabs);

        this.setContent(pane);

    }

    /**
     * Opens a modal dialog for creating a new {@link Semester} owned by {@code owner},
     * with a multi-select list of the currently registered {@link Module}s taught during
     * it. On confirmation the semester is registered in {@link EntityFactory}'s cache,
     * its key recorded in {@code owner}'s own {@link Profile#getSemesters()}, and its
     * {@link SemesterDetailTab} appended to {@code semesterTabs} and selected. The
     * semester's name must be unique among {@code owner}'s own semesters, since
     * {@code owner}'s email combined with the name is a {@link Semester}'s primary key.
     *
     * @param owner the profile the new semester is registered under
     * @param semesterTabs the tab pane to append the newly created semester's tab to
     */
    private static void addSemesterDialog(final Profile owner, final TabPane semesterTabs) {

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

            if (EntityFactory.getInstance().findEntity(EntityType.SEMESTERS, owner.getInformation().getEmailAddress() + ";" + name).isPresent()) {
                GuiSupport.showValidationError("A semester named \"" + name + "\" already exists.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final Integer[] moduleIds = moduleList.getSelectionModel().getSelectedItems().stream()
                    .map(Module::getId)
                    .toArray(Integer[]::new);

            final Semester semester = new Semester(owner, nameField.getText().trim(), moduleIds);

            EntityFactory.getInstance().registerEntitiesInCache(EntityType.SEMESTERS, semester);
            owner.addSemester(semester.getId());
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
     * confirmation, along with its key in whichever {@link Profile} owns it (looked up by
     * {@link Semester#getOwnerEmail()}, if that profile is still registered). Its linked
     * {@link Module}s are left untouched, since that link lives in the removed
     * {@link Semester} itself, not in the module.
     *
     * @param semesterTabs the tab pane to remove the selected semester's tab from
     */
    private static void removeSelectedSemester(final TabPane semesterTabs) {

        if (!(semesterTabs.getSelectionModel().getSelectedItem() instanceof SemesterDetailTab detailTab)) return;

        final Semester semester = detailTab.getSemester();

        if (!GuiSupport.confirmDeletion("Remove Semester", "Remove \"" + semester.getName() + "\"?")) return;

        EntityFactory.getInstance().<Profile>findEntity(EntityType.PROFILE, semester.getOwnerEmail())
                .ifPresent(owner -> owner.removeSemester(semester.getId()));

        EntityFactory.getInstance().removeEntitiesFromCache(EntityType.SEMESTERS, semester);
        EntityFactory.getInstance().deleteFromDatabase(EntityType.SEMESTERS, semester);
        EntityFactory.getInstance().syncToDatabase();

        semesterTabs.getTabs().remove(detailTab);

    }

}
