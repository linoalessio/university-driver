package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.EntityTab;
import de.lino.thma.ui.helper.ExamStatistics;
import de.lino.thma.ui.helper.GuiSupport;
import javafx.collections.FXCollections;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The "Exams" tab, listing every registered {@link Exam} system-wide, independent of
 * which {@link Semester} or {@link Profile} it belongs to, sorted by id, with a
 * "Remove Exam" action - no add action, since an exam is only ever created scoped to
 * one semester's own modules, from within that semester's own
 * {@link de.lino.thma.ui.subtab.SemesterExamsTab}.
 *
 * <p>This tab is admin-only: {@link de.lino.thma.UniversityGui} only ever constructs it
 * for an account whose {@link de.lino.thma.domain.entity.profile.login.Login#isAdmin()}
 * is {@code true}, the same way {@link ProfilesTab} is - a student never sees, or removes,
 * another profile's own exams from here.
 *
 * <p>Since an {@link Exam} itself carries no direct link to a {@link Profile} or
 * {@link Semester} - only to its {@link Module}, via {@link Exam#getModuleId()} - the
 * owning semester(s) are instead resolved by searching every registered semester for one
 * whose own {@link Semester#hasExam(int)} matches (see {@link #owningSemesters(Exam)}):
 * in the ordinary case exactly one, since an exam is only ever added scoped to the one
 * semester it belongs to (see {@link de.lino.thma.ui.subtab.SemesterExamsTab#addExamDialog}),
 * but this tab still degrades gracefully - showing {@code "-"} - for an exam reachable
 * through none, or joins more than one together, should that assumption ever not hold.
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}), so an exam added or removed
 * elsewhere - e.g. a semester's own {@link de.lino.thma.ui.subtab.SemesterExamsTab} - is
 * reflected here without restarting the app.
 */
public final class ExamsTab extends EntityTab<Exam> {

    /**
     * Builds the "Exams" tab: a table of every registered exam, sorted by id, with a
     * remove action.
     */
    public ExamsTab() {

        super("Exams");

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from the current state of
     * {@link EntityFactory}'s cache: the table of every registered exam, sorted by id,
     * with a remove action. Safe to call more than once - see
     * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        final List<Exam> exams = EntityFactory.getInstance().<Exam>getEntities(EntityType.EXAMS).stream()
                .sorted(Comparator.comparingInt(Exam::getId))
                .toList();
        final TableView<Exam> table = new TableView<>(FXCollections.observableArrayList(exams));

        final List<ColumnSpec<Exam>> columns = List.of(
                ColumnSpec.of("Id", e -> GuiSupport.idLabel(e.getId())),
                ColumnSpec.of("Profile", ExamsTab::profileLabel),
                ColumnSpec.of("Semester", ExamsTab::semesterLabel),
                ColumnSpec.of("Module", e -> e.getModule().map(m -> m.getName() + " (" + m.getTag() + ")").orElse("-")),
                ColumnSpec.of("Grade", e -> ExamStatistics.formatGrade(e.getGrade()))
        );

        this.buildContent(table, columns, "Exams", null, null, "− Remove Exam", exam -> removeExam(exam, table));

    }

    /**
     * Resolves every registered {@link Semester} whose own {@link Semester#hasExam(int)}
     * matches {@code exam} - ordinarily exactly one, see this class's own Javadoc.
     *
     * @param exam the exam to look the owning semester(s) up for
     * @return the resolved semesters, or an empty list if none link to {@code exam}
     */
    private static List<Semester> owningSemesters(final Exam exam) {
        return EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .filter(semester -> semester.hasExam(exam.getId()))
                .toList();
    }

    /**
     * Builds the "Profile" column's cell text for {@code exam}: the email address (and,
     * in parentheses, the id) of every {@link Profile} owning one of {@link #owningSemesters(Exam)},
     * deduplicated by email and joined with a comma - {@code "-"} if none resolve.
     *
     * @param exam the exam to build the cell text for
     * @return the built cell text
     */
    private static String profileLabel(final Exam exam) {

        final String label = owningSemesters(exam).stream()
                .map(Semester::getOwnerEmail)
                .distinct()
                .sorted()
                .map(email -> EntityFactory.getInstance().<Profile>findEntity(EntityType.PROFILE, email)
                        .map(profile -> email + " (#" + profile.getId() + ")")
                        .orElse(email))
                .collect(Collectors.joining(", "));

        return label.isBlank() ? "-" : label;

    }

    /**
     * Builds the "Semester" column's cell text for {@code exam}: the name of every one of
     * {@link #owningSemesters(Exam)}, deduplicated and joined with a comma - {@code "-"}
     * if none resolve.
     *
     * @param exam the exam to build the cell text for
     * @return the built cell text
     */
    private static String semesterLabel(final Exam exam) {

        final String label = owningSemesters(exam).stream()
                .map(Semester::getName)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));

        return label.isBlank() ? "-" : label;

    }

    /**
     * Removes {@code exam} after confirmation, first unlinking it from every
     * {@link Semester} that currently links to it (see {@link #owningSemesters(Exam)} and
     * {@link Semester#removeExam(int)}), the same way {@link de.lino.thma.ui.subtab.SemesterExamsTab}'s
     * own "Remove Exam" removes one scoped to a single semester - unlike that one, this
     * tab is not scoped to any one semester, so every linking semester is unlinked here.
     *
     * @param exam the exam to remove
     * @param table the exams table to remove {@code exam} from
     */
    private static void removeExam(final Exam exam, final TableView<Exam> table) {

        if (!GuiSupport.confirmDeletion("Remove Exam", "Remove \"" + exam.getName() + "\"?")) return;

        owningSemesters(exam).forEach(semester -> semester.removeExam(exam.getId()));

        EntityFactory.getInstance().removeEntitiesFromCache(EntityType.EXAMS, exam);
        EntityFactory.getInstance().deleteFromDatabase(EntityType.EXAMS, exam);
        EntityFactory.getInstance().syncToDatabase();
        table.getItems().remove(exam);

    }

}
