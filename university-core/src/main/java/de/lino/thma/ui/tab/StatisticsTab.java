package de.lino.thma.ui.tab;

import de.lino.database.utility.export.ExportCoordinator;
import de.lino.database.utils.export.transcript.TranscriptLegendEntry;
import de.lino.database.utils.export.transcript.TranscriptSection;
import de.lino.database.utils.export.transcript.format.PageLayout;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.entity.semester.SemesterType;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.utility.Constraints;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * The "Statistics" tab, built entirely differently depending on whether the logged-in
 * account is an admin (see {@link de.lino.thma.domain.entity.profile.login.Login#isAdmin()}):
 *
 * <p>For an admin, this tab shows only two admin-only sections, and nothing else - no
 * per-{@link SemesterType} panels, no exam export: {@link #adminOverviewPanel()}, a
 * system-wide count of every registered {@link Profile}, {@link Semester}, {@link Exam}
 * and {@link Module}; and {@link #semesterGroupsPanel(Runnable)}, every registered
 * semester grouped by its own {@link Semester#getName()} - since each student's semester
 * is its own entity, keyed {@code "<owner email>;<name>"}, several profiles sharing the
 * same semester name (e.g. "WS23/24") each own a distinct one - showing, per name, which
 * profiles own one, and which modules and exams are reachable across all of them
 * combined, with a "Remove Semester" button removing every one of them at once (see
 * {@link #removeSemesterGroup(SemesterGroup, Runnable)}).
 *
 * <p>For a non-admin, this tab shows two side-by-side panels, one per
 * {@link SemesterType} (see {@link #typePanel(SemesterType, List)}) - undergraduate
 * study on the left, graduate study on the right - each with its own stat cards, its own
 * table of that type's {@link Semester}s, and its own export button, plus a shared
 * "Export All Exams" menu button offering a grouped, transcript-style PDF or Excel
 * workbook (auto-resolved by {@link ExportCoordinator#exportTranscript}) of every exam
 * reachable from those same semesters, independent of the per-semester {@link Exam}
 * export already available from within each {@link de.lino.thma.ui.subtab.SemesterExamsTab}.
 * Both offer a page format and orientation prompt via {@link GuiSupport#promptPageLayout()}
 * before writing the file. Both panels and the "Export All Exams" button are scoped to
 * the logged-in {@link Profile}'s own {@link Semester}s (matched via
 * {@link Profile#getSemesters()}, the same {@code "<email>;<semester id>"} keys
 * {@link SemestersTab} filters by) - a student never sees, and can never export, another
 * profile's semesters or exams. A semester's {@link SemesterType} is assigned
 * retroactively, from its own {@link de.lino.thma.ui.subtab.SemesterDetailTab}; one with
 * none assigned yet appears in neither panel.
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
    private static AtomicReference<Profile> CURRENT_PROFILE = new AtomicReference<>();

    /**
     * Whether the logged-in account is an admin, bypassing the per-profile scoping
     * entirely - see {@link #StatisticsTab(Profile, boolean)}.
     */
    private final boolean isAdmin;

    /**
     * Builds the "Statistics" tab: for an admin, only {@link #adminOverviewPanel()} and
     * {@link #semesterGroupsPanel()}; for a non-admin, the side-by-side undergraduate and
     * graduate panels plus the "Export All Exams" toolbar, scoped to
     * {@code currentProfile}'s own semesters.
     *
     * @param currentProfile the logged-in account's own profile, or {@code null} if it has none
     * @param isAdmin whether the logged-in account is an admin
     */
    public StatisticsTab(final Profile currentProfile, final boolean isAdmin) {

        super("Statistics");

        CURRENT_PROFILE.set(currentProfile);
        this.isAdmin = isAdmin;

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from the current state of
     * {@link EntityFactory}'s cache: for {@link #isAdmin}, only {@link #adminOverviewPanel()}
     * and {@link #semesterGroupsPanel()}, stacked in a scrollable column; for a non-admin,
     * the side-by-side undergraduate and graduate panels plus the "Export All Exams"
     * toolbar, scoped to {@link #CURRENT_PROFILE}'s own semesters. Safe to call more than
     * once - see {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        if (this.isAdmin) {

            final VBox content = new VBox(16, adminOverviewPanel(), new Separator(), semesterGroupsPanel(this::rebuild));

            final ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);

            this.setContent(scrollPane);

            return;

        }

        final List<Semester> semesters = EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .filter(semester -> CURRENT_PROFILE.get() != null && CURRENT_PROFILE.get().getSemesters().contains(semester.getId()))
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
     * Builds the admin-only "Admin Overview" panel: a row of stat cards counting every
     * registered {@link Profile}, {@link Semester}, {@link Exam} and {@link Module} in
     * the system, straight from {@link EntityFactory}'s cache - unlike
     * {@link #typePanel(SemesterType, List)}'s own cards (only ever built for a
     * non-admin), these totals are not filtered by {@link SemesterType}, ownership, or
     * reachability through any semester at all.
     *
     * @return the built panel
     */
    private static VBox adminOverviewPanel() {

        final int profileCount = EntityFactory.getInstance().getEntities(EntityType.PROFILE).size();
        final int semesterCount = EntityFactory.getInstance().getEntities(EntityType.SEMESTERS).size();
        final int examCount = EntityFactory.getInstance().getEntities(EntityType.EXAMS).size();
        final int moduleCount = EntityFactory.getInstance().getEntities(EntityType.MODULES).size();

        final Label heading = new Label("Admin Overview");
        heading.getStyleClass().add("app-title");

        final HBox cards = new HBox(16,
                ExamStatistics.statCard("Profiles", String.valueOf(profileCount)),
                ExamStatistics.statCard("Semesters", String.valueOf(semesterCount)),
                ExamStatistics.statCard("Exams", String.valueOf(examCount)),
                ExamStatistics.statCard("Modules", String.valueOf(moduleCount))
        );
        cards.setAlignment(Pos.CENTER);

        final VBox panel = new VBox(12, heading, cards);
        panel.setPadding(new Insets(16, 16, 0, 16));

        return panel;

    }

    /**
     * Builds the admin-only "Semesters by Name" panel: every registered {@link Semester}
     * grouped by {@link Semester#getName()} (see {@link SemesterGroup#of(String, List)})
     * into a table of one row per distinct name, a "Remove Semester" button acting on the
     * table's current selection (see {@link #removeSemesterGroup(SemesterGroup, Runnable)}),
     * above a detail area listing the selected group's own profiles, modules and exams in
     * full (see {@link #groupDetail(SemesterGroup)}), updated via a selection listener
     * since a {@link TableView} cannot show all of that per row at once. The first group,
     * if any, is selected up front so the detail area is never empty the first time this
     * panel is shown.
     *
     * @param onRemoved invoked once a semester group has actually been removed, to refresh this whole tab - see {@link #removeSemesterGroup(SemesterGroup, Runnable)}
     * @return the built panel
     */
    private static VBox semesterGroupsPanel(final Runnable onRemoved) {

        final Map<String, List<Semester>> byName = EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .collect(Collectors.groupingBy(Semester::getName, TreeMap::new, Collectors.toList()));

        final List<SemesterGroup> groups = byName.entrySet().stream()
                .map(entry -> SemesterGroup.of(entry.getKey(), entry.getValue()))
                .toList();

        final Label heading = new Label("Semesters by Name");
        heading.getStyleClass().add("app-title");

        final TableView<SemesterGroup> table = new TableView<>(FXCollections.observableArrayList(groups));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(28 * Math.max(groups.size(), 1) + 128);

        final List<ColumnSpec<SemesterGroup>> columns = List.of(
                ColumnSpec.of("Semester Name", SemesterGroup::name),
                ColumnSpec.of("Profiles", group -> String.valueOf(group.profiles().size())),
                ColumnSpec.of("Modules", group -> String.valueOf(group.modules().size())),
                ColumnSpec.of("Exams", group -> String.valueOf(group.exams().size()))
        );
        table.getColumns().addAll(GuiSupport.toColumns(columns, table));

        final Button removeButton = new Button("− Remove Semester");
        removeButton.getStyleClass().add("button-danger");
        removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(event -> removeSemesterGroup(table.getSelectionModel().getSelectedItem(), onRemoved));

        final HBox toolbar = new HBox(removeButton);
        toolbar.setAlignment(Pos.CENTER_RIGHT);
        toolbar.setPadding(new Insets(8, 0, 0, 0));

        final HBox detail = new HBox(16);
        detail.setPadding(new Insets(12, 0, 0, 0));

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldGroup, newGroup) ->
                detail.getChildren().setAll(newGroup == null ? List.of() : groupDetail(newGroup)));

        if (!groups.isEmpty()) table.getSelectionModel().selectFirst();

        final VBox panel = new VBox(12, heading, table, toolbar, detail);
        panel.setPadding(new Insets(16));

        return panel;

    }

    /**
     * Removes {@code group} after confirmation - every underlying {@link Semester} sharing
     * its {@link SemesterGroup#name()}, one per owning {@link Profile} - the same way
     * {@link SemestersTab}'s own "Remove Semester" removes a single one: unlinked from its
     * owner's {@link Profile#getSemesters()} (if that profile is still registered),
     * removed from {@link EntityFactory}'s cache, and deleted from the database. Every
     * {@link Exam} belonging to one of those semesters (see {@link Semester#getExams()})
     * is removed right along with it, the same way {@link de.lino.thma.ui.subtab.SemesterExamsTab}'s
     * own "Remove Exam" removes one - an exam is only ever added scoped to the one
     * semester it belongs to, so none is left dangling, reachable through no semester at
     * all, once this method returns. Its {@link Module}s are left untouched, since
     * {@code semester}'s link to those lives in the semester being removed, not in the
     * module itself, and a module can be taught across several other semesters too.
     * Since this deletes every profile's own copy of the same-named semester in one
     * action, the confirmation prompt names how many profiles are affected. {@code onRemoved}
     * is only invoked - to refresh the enclosing {@link StatisticsTab} so its admin
     * overview counts and this panel's own table both reflect the removal - once a
     * removal was actually confirmed; {@code group} being {@code null} (nothing selected)
     * is a no-op.
     *
     * @param group the semester group to remove, or {@code null} if nothing is selected
     * @param onRemoved invoked once the removal is complete
     */
    private static void removeSemesterGroup(final SemesterGroup group, final Runnable onRemoved) {

        if (group == null) return;

        final List<Semester> semesters = EntityFactory.getInstance().<Semester>getEntities(EntityType.SEMESTERS).stream()
                .filter(semester -> semester.getName().equals(group.name()))
                .toList();

        final String profileCount = semesters.size() == 1 ? "1 profile" : semesters.size() + " profiles";

        if (!GuiSupport.confirmDeletion("Remove Semester",
                "Remove \"" + group.name() + "\"? This will remove it, and every exam sat during it, from " + profileCount + ".")) return;

        for (final Semester semester : semesters) {

            final Exam[] exams = semester.getExams().toArray(Exam[]::new);
            EntityFactory.getInstance().removeEntitiesFromCache(EntityType.EXAMS, exams);
            EntityFactory.getInstance().deleteFromDatabase(EntityType.EXAMS, exams);

            EntityFactory.getInstance().<Profile>findEntity(EntityType.PROFILE, semester.getOwnerEmail())
                    .ifPresent(owner -> owner.removeSemester(semester.getId()));

            EntityFactory.getInstance().removeEntitiesFromCache(EntityType.SEMESTERS, semester);
            EntityFactory.getInstance().deleteFromDatabase(EntityType.SEMESTERS, semester);

        }

        EntityFactory.getInstance().syncToDatabase();

        onRemoved.run();

    }

    /**
     * Builds one {@link SemesterGroup}'s detail area: three side-by-side, independently
     * scrollable lists of every profile, module and exam reachable across all of the
     * group's own same-named semesters, each formatted for readability rather than
     * relying on the entity's own {@code toString()}.
     *
     * @param group the group to build the detail area for
     * @return the built list of nodes, one per category
     */
    private static List<Node> groupDetail(final SemesterGroup group) {

        final ListView<String> profiles = new ListView<>(FXCollections.observableArrayList(
                group.profiles().stream()
                        .map(profile -> profile.getInformation().getFullName() + " (" + profile.getInformation().getEmailAddress() + ")")
                        .sorted()
                        .toList()
        ));

        final ListView<String> modules = new ListView<>(FXCollections.observableArrayList(
                group.modules().stream()
                        .map(module -> module.getName() + " (" + module.getTag() + ")")
                        .sorted()
                        .toList()
        ));

        final ListView<String> exams = new ListView<>(FXCollections.observableArrayList(
                group.exams().stream()
                        .map(exam -> GuiSupport.idLabel(exam.getId()) + " " + exam.getName())
                        .sorted()
                        .toList()
        ));

        return List.of(
                detailColumn("Profiles (" + group.profiles().size() + ")", profiles),
                detailColumn("Modules (" + group.modules().size() + ")", modules),
                detailColumn("Exams (" + group.exams().size() + ")", exams)
        );

    }

    /**
     * Builds one labeled, horizontally-growing column of {@link #groupDetail(SemesterGroup)}'s
     * three side-by-side lists.
     *
     * @param title the column's label
     * @param list the list to place below it
     * @return the built column
     */
    private static VBox detailColumn(final String title, final ListView<String> list) {

        final Label label = new Label(title);
        label.getStyleClass().add("stat-title");

        list.setPrefHeight(160);

        final VBox column = new VBox(6, label, list);
        HBox.setHgrow(column, Priority.ALWAYS);

        return column;

    }

    /**
     * One distinct {@link Semester#getName()} shared across one or more profiles' own
     * semesters, resolved from every such semester's combined {@link Semester#getModules()}
     * and {@link Semester#getExams()}, and the {@link Profile} that owns each - built by
     * {@link #semesterGroupsPanel()} for its table, and read back by
     * {@link #groupDetail(SemesterGroup)} for its detail area.
     *
     * @param name the shared semester name every semester in {@code semesters} was grouped under
     * @param profiles every profile owning one of the same-named semesters, deduplicated by owner email
     * @param modules every module taught in any of the same-named semesters, deduplicated
     * @param exams every exam reachable through any of the same-named semesters, deduplicated
     */
    private record SemesterGroup(String name, List<Profile> profiles, List<Module> modules, List<Exam> exams) {

        /**
         * Builds one {@link SemesterGroup} from every semester sharing {@code name}.
         *
         * @param name the shared name {@code semesters} were grouped under
         * @param semesters every registered semester with that name
         * @return the built group
         */
        static SemesterGroup of(final String name, final List<Semester> semesters) {

            final List<Profile> profiles = semesters.stream()
                    .map(Semester::getOwnerEmail)
                    .distinct()
                    .map(email -> EntityFactory.getInstance().<Profile>findEntity(EntityType.PROFILE, email))
                    .flatMap(Optional::stream)
                    .toList();

            final List<Module> modules = semesters.stream()
                    .flatMap(semester -> semester.getModules().stream())
                    .distinct()
                    .toList();

            final List<Exam> exams = semesters.stream()
                    .flatMap(semester -> semester.getExams().stream())
                    .distinct()
                    .toList();

            return new SemesterGroup(name, profiles, modules, exams);

        }

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
     * <p>Package-private rather than {@code private}: also reused, unmodified, by
     * {@link ProfilesTab}'s own "double-click a student" statistics dialog, to export
     * just that one student's own exams the exact same way.
     *
     * @param semesters the semesters this export is scoped to
     * @return the built menu button
     */
    static MenuButton exportAllExamsButton(final List<Semester> semesters) {

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

        final String fileName = UUID.randomUUID() + "_" + CURRENT_PROFILE.get().getInformation().getEmailAddress() + "_exams" + fileExtension;

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
