package de.lino.thma.ui.subtab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.EntityType;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.EntityTab;
import de.lino.thma.ui.helper.GuiSupport;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * The "Exams" sub-tab of one {@link SemesterDetailTab}: lists only the {@link Exam}s
 * belonging to that one {@link Semester} (see {@link Semester#getExams()}), sorted by
 * id, with an "Add Exam" dialog whose "Assign To Module" choices are restricted to
 * {@link Semester#getModules()} - an exam created here can never be linked to a module
 * outside this semester. Every exam belongs to exactly one module (see
 * {@link Exam#getModuleId()}), so the dialog requires one to be picked.
 *
 * <p>Name, examiner, date, credits, attempt and grade are editable in place; the id and
 * the derived module column are not.
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(javafx.scene.control.Tab, Runnable)}), so an exam
 * added or removed elsewhere is reflected here without restarting the app.
 */
public final class SemesterExamsTab extends EntityTab<Exam> {

    /**
     * Display and parse format used for the editable "Date" column and the "Add Exam" dialog's date picker.
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * The semester this tab is scoped to - see {@link #SemesterExamsTab(Semester)}.
     */
    private final Semester semester;

    /**
     * Builds this semester's "Exams" sub-tab: a table of {@code semester}'s own exams,
     * sorted by id, with add/remove actions scoped to it.
     *
     * @param semester the semester this tab is scoped to
     */
    SemesterExamsTab(final Semester semester) {

        super("Exams");

        this.semester = semester;

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from {@link #semester}'s current own exams:
     * a table, sorted by id, with add/remove actions scoped to it. Safe to call more
     * than once - see {@link GuiSupport#refreshOnSelect(javafx.scene.control.Tab, Runnable)}.
     */
    private void rebuild() {

        final List<Exam> exams = this.semester.getExams().stream()
                .sorted(Comparator.comparingInt(Exam::getId))
                .toList();
        final TableView<Exam> table = new TableView<>(FXCollections.observableArrayList(exams));

        final List<ColumnSpec<Exam>> columns = List.of(
                ColumnSpec.of("Id", e -> GuiSupport.idLabel(e.getId())),
                ColumnSpec.of("Module", e -> e.getModule().map(Module::getName).orElse("-")),
                ColumnSpec.editable("Name", Exam::getName, (e, v) -> e.setName(GuiSupport.requireText(v, "Name"))),
                ColumnSpec.editable("Examiner", Exam::getExaminer, (e, v) -> e.setExaminer(GuiSupport.requireText(v, "Examiner"))),
                ColumnSpec.editable("Date", e -> Instant.ofEpochMilli(e.getDate()).atZone(ZoneOffset.UTC).format(DATE_FORMAT),
                        (e, v) -> e.setDate(GuiSupport.parseDate(v, DATE_FORMAT, "Date"))),
                ColumnSpec.editable("Credits", e -> String.valueOf(e.getCredits()), (e, v) -> e.setCredits(GuiSupport.parseInt(v, "Credits"))),
                ColumnSpec.editable("Attempt", e -> String.valueOf(e.getAttempt()), (e, v) -> e.setAttempt(GuiSupport.parseInt(v, "Attempt"))),
                ColumnSpec.editable("Grade", e -> String.valueOf(e.getGrade()), (e, v) -> e.setGrade(GuiSupport.parseDouble(v, "Grade")))
        );

        this.buildContent(table, columns, this.semester.getName() + " Exams", "+ Add Exam", () -> addExamDialog(this.semester, table),
                "− Remove Exam", exam -> removeExam(semester, exam, table));

    }

    /**
     * Opens a modal dialog for manually creating a new {@link Exam}, assigned to one of
     * {@code semester}'s own {@link Module}s - required, since every exam belongs to
     * exactly one module (see {@link Exam#getModuleId()}). On confirmation the exam is
     * registered in {@link EntityFactory}'s cache and appended to {@code table}.
     *
     * @param semester the semester whose modules the exam may be assigned to
     * @param table the exams table to append the newly created exam to
     */
    private static void addExamDialog(final Semester semester, final TableView<Exam> table) {

        final List<Module> modules = semester.getModules();

        final ComboBox<Module> moduleBox = new ComboBox<>(FXCollections.observableArrayList(modules));
        moduleBox.setConverter(GuiSupport.nameConverter(Module::getName));

        final TextField nameField = new TextField();
        final TextField examinerField = new TextField();
        final DatePicker datePicker = new DatePicker(LocalDate.now());
        final Spinner<Integer> creditsSpinner = new Spinner<>(1, 30, 5);
        creditsSpinner.setEditable(true);
        final Spinner<Integer> attemptSpinner = new Spinner<>(1, 10, 1);
        final Spinner<Double> gradeSpinner = new Spinner<>(1.0, 5.0, 1.0, 0.1);
        gradeSpinner.setEditable(true);

        final GridPane grid = GuiSupport.formGrid(
                "Assign To Module", moduleBox,
                "Name", nameField,
                "Examiner", examinerField,
                "Date", datePicker,
                "Credits", creditsSpinner,
                "Attempt", attemptSpinner,
                "Grade", gradeSpinner
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Add Exam", grid, event -> {

            if (moduleBox.getValue() == null) {
                GuiSupport.showValidationError("A module must be selected.");
                return false;
            }

            if (nameField.getText().isBlank() || examinerField.getText().isBlank() || datePicker.getValue() == null) {
                GuiSupport.showValidationError("Name, examiner and date are required.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final Exam exam = new Exam(
                    GuiSupport.nextId(EntityType.EXAMS, Exam::getId),
                    moduleBox.getValue().getId(),
                    GuiSupport.epochMillisOf(datePicker.getValue()),
                    nameField.getText().trim(),
                    examinerField.getText().trim(),
                    creditsSpinner.getValue(),
                    attemptSpinner.getValue(),
                    gradeSpinner.getValue()
            );

            semester.addExam(exam.getId());
            EntityFactory.getInstance().registerEntitiesInCache(EntityType.EXAMS, exam);
            EntityFactory.getInstance().syncToDatabase();
            table.getItems().add(exam);

        });

    }

    /**
     * Removes {@code exam} after confirmation.
     *
     * @param exam the exam to remove
     * @param table the exams table to remove {@code exam} from
     */
    private static void removeExam(final Semester semester, final Exam exam, final TableView<Exam> table) {

        if (!GuiSupport.confirmDeletion("Remove Exam", "Remove \"" + exam.getName() + "\"?")) return;

        semester.removeExam(exam.getId());
        EntityFactory.getInstance().removeEntitiesFromCache(EntityType.EXAMS, exam);
        EntityFactory.getInstance().deleteFromDatabase(EntityType.EXAMS, exam);
        EntityFactory.getInstance().syncToDatabase();
        table.getItems().remove(exam);

    }

}
