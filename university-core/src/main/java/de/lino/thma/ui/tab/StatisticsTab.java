package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.database.export.ExportCoordinator;
import de.lino.database.export.transcript.format.PageLayout;
import de.lino.database.export.transcript.TranscriptLegendEntry;
import de.lino.database.export.transcript.TranscriptSection;
import de.lino.thma.domain.entity.semester.SemesterType;
import de.lino.thma.domain.EntityType;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.utility.Constraints;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The "Statistics" tab: two side-by-side panels, one per {@link SemesterType} (see
 * {@link #typePanel(SemesterType, List)}) - undergraduate study on the left, graduate
 * study on the right - each with its own stat cards, its own table of that type's
 * {@link Semester}s, and its own export button, plus a shared "Export All Exams" menu
 * button offering a grouped, transcript-style PDF or Excel workbook (auto-resolved by
 * {@link ExportCoordinator#exportTranscript}) of every exam reachable from those same
 * semesters, independent of the per-semester {@link Exam} export already available from
 * within each {@link de.lino.thma.ui.subtab.SemesterExamsTab}. Both offer a page format
 * and orientation prompt via {@link GuiSupport#promptPageLayout()} before writing the
 * file.
 *
 * <p>Both panels and the "Export All Exams" button are scoped to the logged-in
 * {@link Profile}'s own {@link Semester}s (matched via {@link Profile#getSemesters()},
 * the same {@code "<email>;<semester id>"} keys {@link SemestersTab} filters by) - a
 * student never sees, and can never export, another profile's semesters or exams. An
 * admin account (see {@link de.lino.thma.domain.entity.profile.login.Login#isAdmin()})
 * bypasses that scoping entirely: every registered semester and exam is shown and
 * exportable instead, regardless of which profile - including its own - owns it.
 *
 * <p>A semester's {@link SemesterType} is assigned retroactively, from its own
 * {@link de.lino.thma.ui.subtab.SemesterDetailTab}; one with none assigned yet
 * appears in neither panel.
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}) - unlike every other top-level tab,
 * this one has no add/remove actions of its own to keep its own display in sync with,
 * so without this it would never reflect a single change made anywhere else in the app
 * once first built.
 */
public final class StatisticsTab extends Tab {

    /**
     * The German grading-scale key printed as the closing legend page of
     * {@link #exportAllExams(String, PageLayout, List)}'s transcript exports.
     */
    private static final List<TranscriptLegendEntry> GRADING_SCALE = List.of(
            new TranscriptLegendEntry("1.0 / 1.3 / 1.5", "sehr gut (excellent)"),
            new TranscriptLegendEntry("1.7 / 2.0 / 2.3 / 2.5", "gut (good)"),
            new TranscriptLegendEntry("2.7 / 3.0 / 3.3 / 3.5", "befriedigend (satisfactory)"),
            new TranscriptLegendEntry("3.7 / 4.0", "ausreichend (sufficient)"),
            new TranscriptLegendEntry("5.0", "nicht ausreichend (fail)")
    );

    /**
     * The logged-in account's own profile, or {@code null} if it has none - see
     * {@link #StatisticsTab(Profile, boolean)}.
     */
    private final Profile currentProfile;

    /**
     * Whether the logged-in account is an admin, bypassing the per-profile scoping
     * entirely - see {@link #StatisticsTab(Profile, boolean)}.
     */
    private final boolean isAdmin;

    /**
     * Builds the "Statistics" tab: side-by-side undergraduate and graduate panels plus
     * the shared "Export All Exams" toolbar, both scoped to {@code currentProfile}'s own
     * semesters unless {@code isAdmin}.
     *
     * @param currentProfile the logged-in account's own profile, or {@code null} if it has none
     * @param isAdmin whether the logged-in account is an admin, bypassing the per-profile scoping entirely
     */
    public StatisticsTab(final Profile currentProfile, final boolean isAdmin) {

        super("Statistics");

        this.currentProfile = currentProfile;
        this.isAdmin = isAdmin;

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from the current state of
     * {@link EntityFactory}'s cache: the side-by-side undergraduate and graduate panels
     * plus the shared "Export All Exams" toolbar, both scoped to {@link #currentProfile}'s
     * own semesters unless {@link #isAdmin}. Safe to call more than once - see
     * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        final List<Semester> semesters = EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .filter(semester -> this.isAdmin || (this.currentProfile != null && this.currentProfile.getSemesters().contains(semester.getId())))
                .toList();

        final HBox panels = new HBox(
                typePanel(SemesterType.UNDER_GRADUATE_STUDY, semesters),
                new Separator(Orientation.VERTICAL),
                typePanel(SemesterType.GRADUATE_STUDY, semesters)
        );

        final HBox toolbar = new HBox(exportAllExamsButton(semesters));
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        final BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(panels);

        this.setContent(pane);

    }

    /**
     * Builds one {@link SemesterType}'s own statistics panel: a heading, stat cards
     * summarizing every exam from a {@code type}-classified semester (deduplicated in
     * case the same exam is reachable through more than one such semester via a shared
     * {@link Module}), and a table breaking those same metrics down per semester, with
     * its own export button. Takes up half the available width, next to the other type's
     * panel.
     *
     * @param type the study type this panel is scoped to
     * @param allSemesters every registered semester, filtered down to {@code type}'s own
     * @return the built panel
     */
    private static VBox typePanel(final SemesterType type, final List<Semester> allSemesters) {

        final List<Semester> semesters = allSemesters.stream()
                .filter(semester -> semester.getType() == type)
                .sorted(Comparator.comparing(Semester::getName))
                .toList();
        final List<Exam> exams = semesters.stream().flatMap(semester -> semester.getExams().stream()).distinct().toList();

        final Label heading = new Label(type.getDisplayName() + " Study");
        heading.getStyleClass().add("app-title");

        final HBox cards = new HBox(16,
                ExamStatistics.statCard("Exams", String.valueOf(exams.size())),
                ExamStatistics.statCard("Average Grade", ExamStatistics.formatGrade(ExamStatistics.averageGrade(exams))),
                ExamStatistics.statCard("Pass Rate", ExamStatistics.formatPercent(ExamStatistics.passRate(exams))),
                ExamStatistics.statCard("Credits Earned", String.valueOf(ExamStatistics.creditsEarned(exams)))
        );
        cards.setAlignment(Pos.CENTER);

        final TableView<Semester> table = new TableView<>(FXCollections.observableArrayList(semesters));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(table, Priority.ALWAYS);

        final List<ColumnSpec<Semester>> columns = List.of(
                ColumnSpec.of("Semester", Semester::getName),
                ColumnSpec.of("Exams", s -> String.valueOf(s.getExams().size())),
                ColumnSpec.of("Average Grade", s -> ExamStatistics.formatGrade(s.getAverageScore())),
                ColumnSpec.of("Pass Rate", s -> ExamStatistics.formatPercent(ExamStatistics.passRate(s.getExams()))),
                ColumnSpec.of("Credits Earned", s -> String.valueOf(ExamStatistics.creditsEarned(s.getExams())))
        );

        table.getColumns().addAll(GuiSupport.toColumns(columns, table));

        final HBox exportBar = new HBox(GuiSupport.exportButton(type.getDisplayName() + " Statistics", table, columns));
        exportBar.setAlignment(Pos.CENTER_RIGHT);

        final VBox panel = new VBox(12, heading, cards, exportBar, table);
        panel.setPadding(new Insets(16));
        HBox.setHgrow(panel, Priority.ALWAYS);

        return panel;

    }

    /**
     * Builds the "Export All Exams" menu button, offering every exam reachable from
     * {@code semesters} - already scoped to the logged-in profile's own, or every
     * registered semester for an admin account, see {@link #StatisticsTab(Profile, boolean)}
     * - as either a grouped, transcript-style PDF or the same shape as an Excel workbook
     * (auto-resolved by {@link ExportCoordinator#exportTranscript} from the exported
     * file's extension): one section per distinct semester (or set of semesters, joined
     * by name, if a shared {@link Module} links to more than one), each section's exams
     * sorted by {@link Exam#getId()}. An exam whose module is not linked to any of
     * {@code semesters} is left out entirely, rather than falling into an "Unassigned"
     * section - it belongs to none of the semesters this export is scoped to.
     *
     * @param semesters the semesters this export is scoped to
     * @return the built menu button
     */
    private static MenuButton exportAllExamsButton(final List<Semester> semesters) {

        final MenuItem pdfItem = new MenuItem("Export as PDF");
        pdfItem.setOnAction(event -> GuiSupport.promptPageLayout().ifPresent(layout ->
                exportAllExams(".pdf", layout, semesters)));

        final MenuItem excelItem = new MenuItem("Export as Excel");
        excelItem.setOnAction(event -> GuiSupport.promptPageLayout().ifPresent(layout ->
                exportAllExams(".xlsx", layout, semesters)));

        final MenuItem csvItem = new MenuItem("Export as CSV");
        csvItem.setOnAction(event -> GuiSupport.promptPageLayout().ifPresent(layout ->
                exportAllExams(".csv", layout, semesters)));

        final MenuItem jsonItem = new MenuItem("Export as Json");
        jsonItem.setOnAction(event -> GuiSupport.promptPageLayout().ifPresent(layout ->
                exportAllExams(".json", layout, semesters)));

        final MenuButton button = new MenuButton("Export All Exams", null, pdfItem, excelItem, csvItem, jsonItem);
        button.getStyleClass().add("button-primary");

        return button;

    }

    /**
     * Groups every exam reachable from {@code semesters} by semester and writes them
     * through a fresh {@link ExportCoordinator}, whose
     * {@link ExportCoordinator#exportTranscript} resolves the PDF or Excel
     * implementation to write with purely from {@code fileExtension}.
     *
     * @param fileExtension the exported file's extension, including the leading dot
     * @param pageLayout the page format and orientation to render the export at
     * @param semesters the semesters this export is scoped to
     */
    private static void exportAllExams(final String fileExtension, final PageLayout pageLayout, final List<Semester> semesters) {

        final List<Exam> exams = semesters.stream()
                .flatMap(semester -> semester.getExams().stream())
                .distinct()
                .toList();

        final var grouped = new TreeMap<String, List<Exam>>();

        for (final Exam exam : exams) {

            final String groupLabel = exam.getModule()
                    .map(m -> semesters.stream().filter(s -> s.hasModule(m.getId())).map(Semester::getName).sorted().collect(Collectors.joining(", ")))
                    .filter(label -> !label.isBlank())
                    .orElse("Unassigned");

            grouped.computeIfAbsent(groupLabel, key -> new ArrayList<>()).add(exam);

        }

        final List<TranscriptSection> sections = grouped.entrySet().stream()
                .map(entry -> new TranscriptSection(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(Exam::getId))
                                .map(StatisticsTab::examRow)
                                .toList()
                ))
                .toList();

        final String fileName = UUID.randomUUID() + "_exams" + fileExtension;

        final ExportCoordinator coordinator = new ExportCoordinator();

        GuiSupport.runExport(() -> coordinator.exportTranscript(
                "Technical University of Applied Science Mannheim",
                List.of("Id", "Module", "Grade", "Status", "Credits", "Examiner", "Attempt"),
                sections,
                "Grading Scale",
                GRADING_SCALE,
                pageLayout,
                Constraints.EXPORT_PATH.resolve(fileName)
        ), fileName);

    }

    /**
     * Builds one exam's row for {@link #exportAllExams(String, PageLayout, List)},
     * resolving its linked {@link Module}'s name where one still resolves, falling back
     * to the exam's own name otherwise.
     *
     * @param exam the exam to build a row for
     * @return the row's cell values, matching the export's column headers
     */
    private static List<String> examRow(final Exam exam) {

        return List.of(
                GuiSupport.idLabel(exam.getId()),
                exam.getModule().map(Module::getName).orElse(exam.getName()),
                ExamStatistics.formatGrade(exam.getGrade()),
                ExamStatistics.isPassed(exam) ? "bestanden" : "durchgefallen",
                String.valueOf(exam.getCredits()),
                exam.getExaminer(),
                String.valueOf(exam.getAttempt())
        );

    }

}
