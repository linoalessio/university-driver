package de.lino.thma.ui.helper;

import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.ui.subtab.SemesterStatisticsTab;
import de.lino.thma.ui.tab.StatisticsTab;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/**
 * Shared grade-statistics helpers, used by both the global {@link StatisticsTab} and
 * each {@link SemesterStatisticsTab} to summarize a list of {@link Exam}s identically,
 * whether that list is every exam in the system or just one semester's.
 */
public final class ExamStatistics {

    /**
     * The lowest {@link Exam#getGrade()} still counted as passing, on the German
     * 1.0 (best) to 5.0 (fail) grading scale used by the "Add Exam" dialog.
     */
    private static final double PASSING_GRADE = 4.0;

    /**
     * Not instantiable; all functionality is exposed through static methods.
     */
    private ExamStatistics() {
    }

    /**
     * Checks whether an exam was passed, i.e. graded at or better than {@link #PASSING_GRADE}.
     *
     * @param exam the exam to check
     * @return {@code true} if {@code exam} was passed
     */
    public static boolean isPassed(final Exam exam) {
        return exam.getGrade() <= PASSING_GRADE;
    }

    /**
     * The average grade across the given exams.
     *
     * @param exams the exams to average
     * @return the average grade, or {@code 0} if {@code exams} is empty
     */
    public static double averageGrade(final List<Exam> exams) {
        return exams.isEmpty() ? 0 : exams.stream().mapToDouble(Exam::getGrade).average().orElse(0);
    }

    /**
     * The share of the given exams that were {@link #isPassed(Exam) passed}.
     *
     * @param exams the exams to check
     * @return the pass rate, from {@code 0} to {@code 1}, or {@code 0} if {@code exams} is empty
     */
    public static double passRate(final List<Exam> exams) {
        return exams.isEmpty() ? 0 : exams.stream().filter(ExamStatistics::isPassed).count() / (double) exams.size();
    }

    /**
     * The total credits earned from the given exams, counting only those {@link #isPassed(Exam) passed}.
     *
     * @param exams the exams to sum credits over
     * @return the summed credits of every passed exam
     */
    public static int creditsEarned(final List<Exam> exams) {
        return exams.stream().filter(ExamStatistics::isPassed).mapToInt(Exam::getCredits).sum();
    }

    /**
     * Formats a grade to two decimal places, independent of the platform's default locale.
     *
     * @param grade the grade to format
     * @return the formatted grade
     */
    public static String formatGrade(final double grade) {
        return String.format(Locale.US, "%.2f", grade);
    }

    /**
     * Formats a {@code 0}-{@code 1} ratio as a whole-number percentage.
     *
     * @param ratio the ratio to format
     * @return the formatted percentage
     */
    public static String formatPercent(final double ratio) {
        return String.format(Locale.US, "%.0f%%", ratio * 100);
    }

    /**
     * Builds a small labeled metric box, e.g. showing an average grade, for a
     * statistics summary row.
     *
     * @param title the metric's label
     * @param value the metric's already-formatted value
     * @return the assembled box
     */
    public static VBox statCard(final String title, final String value) {

        final Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");

        final Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");

        final VBox card = new VBox(4, valueLabel, titleLabel);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER);

        return card;

    }

}
