package de.lino.thma.ui.subtab;

import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.ui.tab.ExamStatistics;
import de.lino.thma.ui.tab.StatisticsTab;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The "Statistics" sub-tab of one {@link SemesterDetailTab}: a summary row of the
 * {@link Exam} metrics for that one {@link Semester}, using the same computations as
 * the global {@link StatisticsTab}, just scoped to {@link Semester#getExams()}.
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}), so an exam added or removed
 * elsewhere - e.g. this same semester's own {@link SemesterExamsTab} - is reflected here
 * without restarting the app.
 */
public final class SemesterStatisticsTab extends Tab {

    /**
     * The semester this tab is scoped to - see {@link #SemesterStatisticsTab(Semester)}.
     */
    private final Semester semester;

    /**
     * Builds this semester's "Statistics" sub-tab: a row of stat cards summarizing
     * {@code semester}'s own exams.
     *
     * @param semester the semester this tab is scoped to
     */
    SemesterStatisticsTab(final Semester semester) {

        super("Statistics");

        this.semester = semester;

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from {@link #semester}'s current own exams: a
     * row of stat cards summarizing them. Safe to call more than once - see
     * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        final List<Exam> exams = this.semester.getExams();

        final HBox summary = new HBox(16,
                ExamStatistics.statCard("Exams", String.valueOf(exams.size())),
                ExamStatistics.statCard("Average Grade", ExamStatistics.formatGrade(ExamStatistics.averageGrade(exams))),
                ExamStatistics.statCard("Pass Rate", ExamStatistics.formatPercent(ExamStatistics.passRate(exams))),
                ExamStatistics.statCard("Credits Earned", String.valueOf(ExamStatistics.creditsEarned(exams)))
        );
        summary.setPadding(new Insets(16));
        summary.setAlignment(Pos.CENTER);

        this.setContent(summary);

    }

}
