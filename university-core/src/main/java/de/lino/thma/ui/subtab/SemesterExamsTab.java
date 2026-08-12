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
import java.util.Optional;

/**
 * The "Exams" sub-tab of one {@link SemesterDetailTab}: lists only the {@link Exam}s
 * belonging to that one {@link Semester} (see {@link Semester#getExams()}), sorted by
 * id, with an "Add Exam" dialog whose "Assign To Module" choices are restricted to
 * {@link Semester#getModules()} - an exam created here can never be linked to a module
 * outside this semester.
 *
 * <p>Name, examiner, date, credits, attempt and grade are editable in place; the id and
 * the derived module column are not.
 */
public final class SemesterExamsTab extends EntityTab<Exam> {

    /**
     * Display and parse format used for the editable "Date" column and the "Add Exam" dialog's date picker.
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Builds this semester's "Exams" sub-tab: a table of {@code semester}'s own exams,
     * sorted by id, with add/remove actions scoped to it.
     *
     * @param semester the semester this tab is scoped to
     */
    SemesterExamsTab(final Semester semester) {

        super("Exams");

        final List<Exam> exams = semester.getExams().stream()
                .sorted(Comparator.comparingInt(Exam::getId))
                .toList();
        final TableView<Exam> table = new TableView<>(FXCollections.observableArrayList(exams));

        final List<ColumnSpec<Exam>> columns = List.of(
                ColumnSpec.of("Id", e -> GuiSupport.idLabel(e.getId())),
                ColumnSpec.of("Module", e -> moduleOf(semester, e).map(Module::getName).orElse("-")),
                ColumnSpec.editable("Name", Exam::getName, (e, v) -> e.setName(GuiSupport.requireText(v, "Name"))),
                ColumnSpec.editable("Examiner", Exam::getExaminer, (e, v) -> e.setExaminer(GuiSupport.requireText(v, "Examiner"))),
                ColumnSpec.editable("Date", e -> Instant.ofEpochMilli(e.getDate()).atZone(ZoneOffset.UTC).format(DATE_FORMAT),
                        (e, v) -> e.setDate(GuiSupport.parseDate(v, DATE_FORMAT, "Date"))),
                ColumnSpec.editable("Credits", e -> String.valueOf(e.getCredits()), (e, v) -> e.setCredits(GuiSupport.parseInt(v, "Credits"))),
                ColumnSpec.editable("Attempt", e -> String.valueOf(e.getAttempt()), (e, v) -> e.setAttempt(GuiSupport.parseInt(v, "Attempt"))),
                ColumnSpec.editable("Grade", e -> String.valueOf(e.getGrade()), (e, v) -> e.setGrade(GuiSupport.parseDouble(v, "Grade")))
        );

        this.buildContent(table, columns, semester.getName() + " Exams", "+ Add Exam", () -> addExamDialog(semester, table),
                "− Remove Exam", exam -> removeExam(exam, table));

    }

    /**
     * Looks up the {@link Module}, if any, currently linked to the given exam via
     * {@link Module#getExamId()}, among {@code semester}'s own modules.
     *
     * @param semester the semester to search the modules of
     * @param exam the exam to find the linked module of
     * @return the linked module, or an empty {@link Optional} if none links to it
     */
    private static Optional<Module> moduleOf(final Semester semester, final Exam exam) {
        return semester.getModules().stream()
                .filter(module -> module.getExamId() == exam.getId())
                .findFirst();
    }

    /**
     * Opens a modal dialog for manually creating a new {@link Exam}, optionally
     * assigning it to one of {@code semester}'s own {@link Module}s (a module links to
     * at most one exam via {@link Module#getExamId()}; picking a module that already
     * has one reassigns it to the new exam). On confirmation the exam is registered in
     * {@link EntityFactory}'s cache and appended to {@code table}.
     *
     * @param semester the semester whose modules the exam may be assigned to
     * @param table the exams table to append the newly created exam to
     */
    private static void addExamDialog(final Semester semester, final TableView<Exam> table) {

        final List<Module> modules = semester.getModules();

        final ComboBox<Module> moduleBox = new ComboBox<>(FXCollections.observableArrayList(modules));
        moduleBox.setConverter(GuiSupport.nameConverter(Module::getName));
        moduleBox.setPromptText("None");

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

            if (nameField.getText().isBlank() || examinerField.getText().isBlank() || datePicker.getValue() == null) {
                GuiSupport.showValidationError("Name, examiner and date are required.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final Exam exam = new Exam(
                    GuiSupport.nextId(EntityType.EXAMS, Exam::getId),
                    GuiSupport.epochMillisOf(datePicker.getValue()),
                    nameField.getText().trim(),
                    examinerField.getText().trim(),
                    creditsSpinner.getValue(),
                    attemptSpinner.getValue(),
                    gradeSpinner.getValue()
            );

            EntityFactory.getInstance().registerEntitiesInCache(EntityType.EXAMS, exam);

            if (moduleBox.getValue() != null) moduleBox.getValue().setExamId(exam.getId());

            EntityFactory.getInstance().syncToDatabase();
            table.getItems().add(exam);

        });

    }

    /**
     * Removes {@code exam} after confirmation, first clearing {@link Module#getExamId()}
     * on every {@link Module} that links to it, since that link lives in the module,
     * not the exam.
     *
     * @param exam the exam to remove
     * @param table the exams table to remove {@code exam} from
     */
    private static void removeExam(final Exam exam, final TableView<Exam> table) {

        if (!GuiSupport.confirmDeletion("Remove Exam", "Remove \"" + exam.getName() + "\"?")) return;

        EntityFactory.getInstance().<Module>getEntities(EntityType.MODULES).stream()
                .filter(module -> module.getExamId() == exam.getId())
                .forEach(module -> module.setExamId(0));

        EntityFactory.getInstance().removeEntitiesFromCache(EntityType.EXAMS, exam);
        EntityFactory.getInstance().deleteFromDatabase(EntityType.EXAMS, exam);
        EntityFactory.getInstance().syncToDatabase();
        table.getItems().remove(exam);

    }

}
