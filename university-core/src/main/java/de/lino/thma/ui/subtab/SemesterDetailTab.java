package de.lino.thma.ui.subtab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.entity.semester.SemesterType;
import de.lino.thma.domain.EntityType;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.ui.tab.ModulesTab;
import de.lino.thma.ui.tab.SemestersTab;
import de.lino.thma.ui.tab.StatisticsTab;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.util.Arrays;

/**
 * One semester's own tab, nested inside {@link SemestersTab}'s tab pane: switching to
 * it shows a toolbar for renaming this semester (see {@link #renameSemesterDialog(Semester, Tab, TabPane)})
 * and retroactively assigning its own {@link SemesterType} (see {@link #studyTypeBox(Semester)}),
 * above a further-nested {@link TabPane} of {@link SemesterModulesTab},
 * {@link SemesterExamsTab}, {@link SemesterStatisticsTab} and {@link SchedulerTab}, all
 * scoped to this one {@link Semester}. Modules are registered independently of any
 * semester in {@link ModulesTab} and only linked here; exams and the scheduler, however,
 * are only ever shown or created nested inside the semester they belong to.
 */
public final class SemesterDetailTab extends Tab {

    /**
     * The semester this tab is scoped to; see {@link #getSemester()}.
     */
    private final Semester semester;

    /**
     * Builds this semester's own tab: a rename/study-type toolbar above a nested
     * {@link TabPane} of {@link SemesterModulesTab}, {@link SemesterExamsTab},
     * {@link SemesterStatisticsTab} and {@link SchedulerTab}, all scoped to {@code semester}.
     *
     * @param semester the semester this tab is scoped to
     * @param semesterTabs the parent tab pane this tab lives in, re-sorted alphabetically after a rename (see {@link #renameSemesterDialog(Semester, Tab, TabPane)})
     */
    public SemesterDetailTab(final Semester semester, final TabPane semesterTabs) {

        super(semester.getName());

        this.semester = semester;

        final TabPane tabs = new TabPane(
                new SemesterModulesTab(semester), new SemesterExamsTab(semester), new SemesterStatisticsTab(semester), new SchedulerTab(semester)
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        final BorderPane pane = new BorderPane();
        pane.setTop(toolbar(semester, this, semesterTabs));
        pane.setCenter(tabs);

        this.setContent(pane);

    }

    /**
     * Builds the bar carrying a "Rename" button (see {@link #renameSemesterDialog(Semester, Tab, TabPane)})
     * and this semester's own {@link SemesterType} selector (see {@link #studyTypeBox(Semester)}).
     *
     * @param semester the semester this bar is scoped to
     * @param detailTab this semester's own tab, renamed alongside the semester itself
     * @param semesterTabs the parent tab pane {@code detailTab} lives in, re-sorted alphabetically after a rename
     * @return the built bar
     */
    private static HBox toolbar(final Semester semester, final Tab detailTab, final TabPane semesterTabs) {

        final Button renameButton = new Button("✎ Rename");
        renameButton.setOnAction(event -> renameSemesterDialog(semester, detailTab, semesterTabs));

        final Label typeLabel = new Label("Study Type:");

        final HBox bar = new HBox(8, renameButton, typeLabel, studyTypeBox(semester));
        bar.getStyleClass().add("toolbar");
        bar.setPadding(new Insets(8));
        bar.setAlignment(Pos.CENTER_LEFT);

        return bar;

    }

    /**
     * Builds the combo box letting this semester's own {@link SemesterType} be assigned
     * or changed after the fact - e.g. for a semester registered before this field
     * existed, or one nobody has classified yet - so the per-type breakdown in
     * {@link StatisticsTab} has something to group by. {@code null} ("Unassigned")
     * is itself a selectable choice, since a semester need not be classified at all.
     *
     * @param semester the semester to assign a type to
     * @return the built combo box
     */
    private static ComboBox<SemesterType> studyTypeBox(final Semester semester) {

        final ComboBox<SemesterType> typeBox = new ComboBox<>(FXCollections.observableArrayList(
                Arrays.asList(null, SemesterType.UNDER_GRADUATE_STUDY, SemesterType.GRADUATE_STUDY)
        ));

        typeBox.setConverter(new StringConverter<>() {

            @Override
            public String toString(final SemesterType type) {
                return type == null ? "Unassigned" : type.getDisplayName();
            }

            @Override
            public SemesterType fromString(final String string) {
                return null;
            }

        });

        typeBox.getSelectionModel().select(semester.getType());
        typeBox.setOnAction(event -> {
            semester.setType(typeBox.getValue());
            EntityFactory.getInstance().syncToDatabase();
        });

        return typeBox;

    }

    /**
     * Opens a modal dialog for renaming {@code semester}, pre-filled with its current
     * name. On confirmation with an actually-changed, unique name: the semester's stale
     * database row under its old composite key ({@code "<owner email>;<old name>"}) is
     * deleted, so once renamed in memory it is not left behind as an orphaned duplicate
     * rather than updated; the owning {@link Profile} (looked up by
     * {@link Semester#getOwnerEmail()}, if still registered) has its own
     * {@link Profile#getSemesters()} updated to the new key in place of the old one;
     * the semester itself is renamed via {@link Semester#setId(String)}; and
     * {@code detailTab}'s own title is updated to match, with {@code semesterTabs}
     * re-sorted alphabetically to reflect the semester's possibly-changed position.
     *
     * @param semester the semester to rename
     * @param detailTab this semester's own tab, renamed alongside the semester itself
     * @param semesterTabs the parent tab pane {@code detailTab} lives in
     */
    private static void renameSemesterDialog(final Semester semester, final Tab detailTab, final TabPane semesterTabs) {

        final TextField nameField = new TextField(semester.getName());

        final GridPane grid = GuiSupport.formGrid("Name", nameField);

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Rename Semester", grid, event -> {

            final String newName = nameField.getText().trim();

            if (newName.isBlank()) {
                GuiSupport.showValidationError("Name must not be empty.");
                return false;
            }

            if (!newName.equalsIgnoreCase(semester.getName())
                    && EntityFactory.getInstance().findEntity(EntityType.SEMESTERS, semester.getOwnerEmail() + ";" + newName).isPresent()) {
                GuiSupport.showValidationError("A semester named \"" + newName + "\" already exists.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final String oldName = semester.getName();
            final String newName = nameField.getText().trim();

            if (newName.equals(oldName)) return;

            final String oldId = semester.getId();
            final String newId = semester.getOwnerEmail() + ";" + newName;

            EntityFactory.getInstance().deleteFromDatabase(EntityType.SEMESTERS, oldId);

            EntityFactory.getInstance().<Profile>findEntity(EntityType.PROFILE, semester.getOwnerEmail()).ifPresent(owner -> {
                owner.removeSemester(oldId);
                owner.addSemester(newId);
            });

            semester.setId(newId);
            EntityFactory.getInstance().syncToDatabase();

            detailTab.setText(newName);
            SemestersTab.sortTabs(semesterTabs);

        });

    }

    /**
     * The {@link Semester} this tab is scoped to, e.g. for {@link SemestersTab} to
     * remove when this tab's own "Remove Semester" action is triggered.
     *
     * @return the semester
     */
    public Semester getSemester() {
        return this.semester;
    }

}
