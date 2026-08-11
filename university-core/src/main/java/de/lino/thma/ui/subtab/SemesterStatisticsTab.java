package de.lino.thma.ui.subtab;

import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.semester.Semester;
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
 */
public final class SemesterStatisticsTab extends Tab {

    /**
     * Builds this semester's "Statistics" sub-tab: a row of stat cards summarizing
     * {@code semester}'s own exams.
     *
     * @param semester the semester this tab is scoped to
     */
    SemesterStatisticsTab(final Semester semester) {

        super("Statistics");

        final List<Exam> exams = semester.getExams();

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
