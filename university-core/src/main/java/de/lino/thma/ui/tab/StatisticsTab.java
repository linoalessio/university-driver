package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.entity.semester.SemesterType;
import de.lino.thma.persistence.EntityType;
import de.lino.thma.persistence.export.ExportCoordinator;
import de.lino.thma.persistence.export.transcript.TranscriptExporter;
import de.lino.thma.persistence.export.transcript.TranscriptLegendEntry;
import de.lino.thma.persistence.export.transcript.TranscriptSection;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.GuiSupport;
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

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The "Statistics" tab: two side-by-side panels, one per {@link SemesterType} (see
 * {@link #typePanel(SemesterType, List)}) - undergraduate study on the left, graduate
 * study on the right - each with its own stat cards, its own table of that type's
 * {@link Semester}s, and its own export button, plus a shared "Export All Exams" menu
 * button offering a grouped, transcript-style PDF ({@link ExportCoordinator.TranscriptPDFExporter}) or
 * Excel workbook ({@link ExportCoordinator.TranscriptExcelExporter}) of every registered exam
 * regardless of type, independent of the per-semester {@link Exam} export already
 * available from within each {@link de.lino.thma.ui.subtab.SemesterExamsTab}.
 *
 * <p>A semester's {@link SemesterType} is assigned retroactively, from its own
 * {@link de.lino.thma.ui.subtab.SemesterDetailTab}; one with none assigned yet
 * appears in neither panel.
 */
public final class StatisticsTab extends Tab {

    /**
     * The German grading-scale key printed as the closing legend page of
     * {@link #exportAllExams(String, TranscriptExporter)}'s transcript exports.
     */
    private static final List<TranscriptLegendEntry> GRADING_SCALE = List.of(
            new TranscriptLegendEntry("1.0 / 1.3 / 1.5", "sehr gut (excellent)"),
            new TranscriptLegendEntry("1.7 / 2.0 / 2.3 / 2.5", "gut (good)"),
            new TranscriptLegendEntry("2.7 / 3.0 / 3.3 / 3.5", "befriedigend (satisfactory)"),
            new TranscriptLegendEntry("3.7 / 4.0", "ausreichend (sufficient)"),
            new TranscriptLegendEntry("5.0", "nicht ausreichend (fail)")
    );

    /**
     * Builds the "Statistics" tab: side-by-side undergraduate and graduate panels plus
     * the shared "Export All Exams" toolbar.
     */
    public StatisticsTab() {

        super("Statistics");

        final List<Semester> semesters = EntityFactory.getInstance().getEntities(EntityType.SEMESTERS);

        final HBox panels = new HBox(
                typePanel(SemesterType.UNDER_GRADUATE_STUDY, semesters),
                new Separator(Orientation.VERTICAL),
                typePanel(SemesterType.GRADUATE_STUDY, semesters)
        );

        final HBox toolbar = new HBox(exportAllExamsButton());
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
     * Builds the "Export All Exams" menu button, offering every registered {@link Exam}
     * as either a grouped, transcript-style PDF ({@link ExportCoordinator.TranscriptPDFExporter})
     * or the same shape as an Excel workbook ({@link ExportCoordinator.TranscriptExcelExporter}):
     * one section per distinct semester (or set of semesters, joined by name, if a shared
     * {@link Module} links to more than one; an exam whose module is not linked to any
     * semester falls into its own "Unassigned" section), each section's exams sorted by
     * {@link Exam#getId()}.
     *
     * @return the built menu button
     */
    private static MenuButton exportAllExamsButton() {

        final MenuItem pdfItem = new MenuItem("Export as PDF");
        pdfItem.setOnAction(event -> exportAllExams(".pdf", new ExportCoordinator.TranscriptPDFExporter()));

        final MenuItem excelItem = new MenuItem("Export as Excel");
        excelItem.setOnAction(event -> exportAllExams(".xlsx", new ExportCoordinator.TranscriptExcelExporter()));

        final MenuButton button = new MenuButton("Export All Exams", null, pdfItem, excelItem);
        button.getStyleClass().add("button-primary");

        return button;

    }

    /**
     * Groups every registered {@link Exam} by semester and writes them via {@code format},
     * injected into a fresh {@link ExportCoordinator} rather than called directly - the
     * same {@link TranscriptExporter} shape both
     * {@link ExportCoordinator.TranscriptPDFExporter} and
     * {@link ExportCoordinator.TranscriptExcelExporter} implement, letting this
     * method write either format through the same call site.
     *
     * @param fileExtension the exported file's extension, including the leading dot
     * @param format the exporter to write the grouped exams through
     */
    private static void exportAllExams(final String fileExtension, final TranscriptExporter format) {

        final List<Exam> exams = EntityFactory.getInstance().getEntities(EntityType.EXAMS);
        final List<Module> modules = EntityFactory.getInstance().getEntities(EntityType.MODULES);
        final List<Semester> semesters = EntityFactory.getInstance().getEntities(EntityType.SEMESTERS);

        final var grouped = new TreeMap<String, List<Exam>>();

        for (final Exam exam : exams) {

            final Optional<Module> module = modules.stream().filter(m -> m.getExamId() == exam.getId()).findFirst();

            final String groupLabel = module
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
                                .map(exam -> examRow(exam, modules))
                                .toList()
                ))
                .toList();

        final String fileName = UUID.randomUUID() + "_exams" + fileExtension;

        final ExportCoordinator coordinator = new ExportCoordinator();
        coordinator.injectTranscriptExporter(format);

        GuiSupport.runExport(() -> coordinator.exportTranscript(
                "Technical University of Applied Science Mannheim",
                List.of("Id", "Module", "Grade", "Status", "Credits", "Examiner", "Attempt"),
                sections,
                "Grading Scale",
                GRADING_SCALE,
                Path.of(fileName)
        ), fileName);

    }

    /**
     * Builds one exam's row for {@link #exportAllExams(String, TranscriptExporter)},
     * resolving its linked {@link Module}'s name where one exists, falling back to the
     * exam's own name otherwise.
     *
     * @param exam the exam to build a row for
     * @param modules every registered module, searched for the one linking to {@code exam}
     * @return the row's cell values, matching the export's column headers
     */
    private static List<String> examRow(final Exam exam, final List<Module> modules) {

        final Optional<Module> module = modules.stream().filter(m -> m.getExamId() == exam.getId()).findFirst();

        return List.of(
                GuiSupport.idLabel(exam.getId()),
                module.map(Module::getName).orElse(exam.getName()),
                ExamStatistics.formatGrade(exam.getGrade()),
                ExamStatistics.isPassed(exam) ? "bestanden" : "durchgefallen",
                String.valueOf(exam.getCredits()),
                exam.getExaminer(),
                String.valueOf(exam.getAttempt())
        );

    }

}
