package de.lino.thma.ui.subtab;

import de.lino.database.utility.export.ExportCoordinator;
import de.lino.database.utils.export.ExportType;
import de.lino.database.utils.export.transcript.TranscriptSection;
import de.lino.database.utils.export.transcript.format.PageLayout;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.scheduler.PeriodLayout;
import de.lino.thma.domain.entity.scheduler.Scheduler;
import de.lino.thma.domain.entity.scheduler.lesson.Lecture;
import de.lino.thma.domain.entity.scheduler.time.SchedulerTime;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.GuiSupport;
import de.lino.thma.utility.Constraints;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

/**
 * The "Scheduler" sub-tab of one {@link SemesterDetailTab}: {@code semester}'s own
 * weekly lesson plan (see {@link Semester#getScheduler()}), only ever reachable for a
 * {@link de.lino.thma.domain.entity.profile.login.Role#STUDENT} account, the same way
 * every tab nested under {@link de.lino.thma.ui.tab.SemestersTab} is.
 *
 * <p>Before {@code semester} has a {@link Scheduler} of its own (the default - see
 * {@link Semester#createScheduler()}), this tab shows nothing but a prompt to create
 * one. Once created, it shows a further-nested {@link TabPane} of:
 * <ul>
 *     <li>"Periods" - the {@link SchedulerTime} slots lessons can be scheduled into,
 *     added in order with an auto-incremented lesson id (see {@link Scheduler#nextLessonId()}),
 *     each tagged {@link SchedulerTime.ScheduleType#LECTURE} or {@link SchedulerTime.ScheduleType#BREAK};
 *     <li>"Week" - one section per weekday, listing the {@link Lecture}s appended to it
 *     (see {@link Scheduler#appendLesson(DayOfWeek, Lecture)}), each tied to one of
 *     the {@link SchedulerTime.ScheduleType#LECTURE} periods above - a lecture is never
 *     scheduled into a break. A lecture's module is restricted to
 *     {@link Semester#getModules()} - the modules already linked to this semester -
 *     the same way {@link SemesterExamsTab}'s own "Assign To Module" choice is.
 * </ul>
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}), so a period or lesson added here
 * - or a scheduler created here - is never stale.
 */
public final class SchedulerTab extends Tab {

    /**
     * The weekdays a {@link Scheduler} can hold lessons on, in display order - this app
     * has no lessons on weekends.
     */
    private static final List<DayOfWeek> WEEK = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /**
     * The semester this tab is scoped to - see {@link #SchedulerTab(Semester)}.
     */
    private final Semester semester;

    /**
     * Builds this semester's "Scheduler" sub-tab: a creation prompt if {@code semester}
     * has no {@link Scheduler} yet, otherwise its periods and weekly lesson plan.
     *
     * @param semester the semester this tab is scoped to
     */
    SchedulerTab(final Semester semester) {

        super("Scheduler");

        this.semester = semester;

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from {@link #semester}'s current
     * {@link Semester#getScheduler()}: a creation prompt if it is still {@code null},
     * otherwise a nested {@link TabPane} of "Periods" and "Week", with whichever of the
     * two was previously selected kept selected. Safe to call more than once - see
     * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        final Scheduler scheduler = this.semester.getScheduler();

        if (scheduler == null) {
            this.setContent(createSchedulerPrompt());
            return;
        }

        final int previouslySelected = this.getContent() instanceof TabPane previous
                ? previous.getSelectionModel().getSelectedIndex() : 0;

        final TabPane tabs = new TabPane(periodsTab(scheduler), weekTab(this.semester, scheduler, this::rebuild));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().select(Math.clamp(previouslySelected, 0, tabs.getTabs().size() - 1));

        this.setContent(tabs);

    }

    /**
     * Builds the prompt shown in place of {@link #semester}'s scheduler before it has
     * one: a short message above a "+ Create Scheduler" button that calls
     * {@link Semester#createScheduler()} and immediately rebuilds this tab into its
     * periods/week view.
     *
     * @return the built prompt
     */
    private Node createSchedulerPrompt() {

        final Label message = new Label("\"" + this.semester.getName() + "\" has no scheduler yet.");

        final Button createButton = new Button("+ Create Scheduler");
        createButton.getStyleClass().add("button-primary");
        createButton.setOnAction(event -> {
            this.semester.createScheduler();
            EntityFactory.getInstance().syncToDatabase();
            this.rebuild();
        });

        final VBox box = new VBox(12, message, createButton);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));

        return box;

    }

    /**
     * Builds the "Periods" sub-tab: a table of {@code scheduler}'s own
     * {@link SchedulerTime} entries, sorted by lesson id, with an "+ Add Period"
     * dialog (see {@link #periodDialog(Scheduler, Integer, Runnable)}) that assigns the
     * next free id itself - a period is never created with a hand-picked one - a
     * "Period Layout" dialog (see {@link #periodLayoutDialog(Scheduler, Runnable)}) for
     * applying a whole {@link PeriodLayout} at once, a "− Remove Period" button acting
     * on the table's current selection (see {@link #removePeriod(Scheduler, Map.Entry, Runnable)}),
     * and the same edit dialog again on a double-click of any row, pre-filled with that
     * row's own values.
     *
     * @param scheduler the scheduler to list the periods of
     * @return the built sub-tab
     */
    private Tab periodsTab(final Scheduler scheduler) {

        final Tab tab = new Tab("Periods");
        tab.setClosable(false);

        final List<Map.Entry<Integer, SchedulerTime>> periods = scheduler.getSchedulerTimes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        final TableView<Map.Entry<Integer, SchedulerTime>> table = new TableView<>(FXCollections.observableArrayList(periods));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        final List<ColumnSpec<Map.Entry<Integer, SchedulerTime>>> columns = List.of(
                ColumnSpec.of("Id", entry -> GuiSupport.idLabel(entry.getKey())),
                ColumnSpec.of("Type", entry -> entry.getValue().getScheduleType().getName()),
                ColumnSpec.of("Start", entry -> entry.getValue().getStartDate().toString()),
                ColumnSpec.of("End", entry -> entry.getValue().getEndDate().toString()),
                ColumnSpec.of("Duration", entry -> entry.getValue().getTotalDurationInMinutes() + " min")
        );
        table.getColumns().addAll(GuiSupport.toColumns(columns, table));

        table.setRowFactory(view -> {
            final TableRow<Map.Entry<Integer, SchedulerTime>> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    periodDialog(scheduler, row.getItem().getKey(), this::rebuild);
                }
            });
            return row;
        });

        final Button layoutButton = new Button("Period Layout");
        layoutButton.setOnAction(event -> periodLayoutDialog(scheduler, this::rebuild));

        final Button addButton = new Button("+ Add Period");
        addButton.getStyleClass().add("button-primary");
        addButton.setOnAction(event -> periodDialog(scheduler, null, this::rebuild));

        final Button removeButton = new Button("− Remove Period");
        removeButton.getStyleClass().add("button-danger");
        removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(event -> removePeriod(scheduler, table.getSelectionModel().getSelectedItem(), this::rebuild));

        final HBox toolbar = new HBox(8, layoutButton, addButton, removeButton);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        final BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(table);
        tab.setContent(pane);

        return tab;

    }

    /**
     * Opens a modal dialog for creating or editing a {@link SchedulerTime} period on
     * {@code scheduler}: a {@link SchedulerTime.ScheduleType}, and a start and end time
     * built from hour/minute spinners rather than free text.
     *
     * <p>{@code existingId} tells the two modes apart: {@code null} appends a brand new
     * period, whose lesson id is never entered by hand - it is always
     * {@link Scheduler#nextLessonId()} - while an actual id edits that period in place
     * (see {@link SchedulerTime#setScheduleType}/{@link SchedulerTime#setStartDate}/
     * {@link SchedulerTime#setEndDate}), pre-filling the dialog with its current values,
     * keeping the same id, and rejecting a change to {@link SchedulerTime.ScheduleType#BREAK}
     * while any lecture is still scheduled into it (see {@link Scheduler#getLessons()}) -
     * those would have to be removed or moved first. Either way, a start/end time that
     * exactly matches a different period's is always rejected - two periods covering the
     * same slot would make {@link #timetableGrid(Semester, Scheduler, Runnable)}
     * ambiguous about which row a lecture belongs in. On confirmation {@code onChange}
     * is run, so the caller can rebuild whatever view is showing {@code scheduler}'s
     * periods.
     *
     * @param scheduler the scheduler to append or edit the period on
     * @param existingId the id of the period to edit, or {@code null} to append a new one
     * @param onChange run after the period has been appended or edited, and persisted
     */
    private static void periodDialog(final Scheduler scheduler, final Integer existingId, final Runnable onChange) {

        final boolean editing = existingId != null;
        final SchedulerTime existing = editing ? scheduler.get(existingId) : null;

        final ComboBox<SchedulerTime.ScheduleType> typeBox = new ComboBox<>(FXCollections.observableArrayList(SchedulerTime.ScheduleType.values()));
        typeBox.setConverter(GuiSupport.nameConverter(SchedulerTime.ScheduleType::getName));

        final Spinner<Integer> startHour = new Spinner<>(0, 23, editing ? existing.getStartDate().getHour() : 8);
        final Spinner<Integer> startMinute = new Spinner<>(0, 59, editing ? existing.getStartDate().getMinute() : 0, 5);
        final Spinner<Integer> endHour = new Spinner<>(0, 23, editing ? existing.getEndDate().getHour() : 9);
        final Spinner<Integer> endMinute = new Spinner<>(0, 59, editing ? existing.getEndDate().getMinute() : 30, 5);
        startHour.setEditable(true);
        startMinute.setEditable(true);
        endHour.setEditable(true);
        endMinute.setEditable(true);

        typeBox.getSelectionModel().select(editing ? existing.getScheduleType() : SchedulerTime.ScheduleType.LECTURE);

        final GridPane grid = GuiSupport.formGrid(
                "Type", typeBox,
                "Start", new HBox(4, startHour, new Label(":"), startMinute),
                "End", new HBox(4, endHour, new Label(":"), endMinute)
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog(editing ? "Edit Period" : "Add Period", grid, event -> {

            final LocalTime start = LocalTime.of(startHour.getValue(), startMinute.getValue());
            final LocalTime end = LocalTime.of(endHour.getValue(), endMinute.getValue());

            if (!end.isAfter(start)) {
                GuiSupport.showValidationError("End must be after start.");
                return false;
            }

            final boolean alreadyExists = scheduler.getSchedulerTimes().entrySet().stream()
                    .filter(entry -> !editing || !entry.getKey().equals(existingId))
                    .anyMatch(entry -> entry.getValue().getStartDate().equals(start) && entry.getValue().getEndDate().equals(end));

            if (alreadyExists) {
                GuiSupport.showValidationError("A period from " + start + " to " + end + " already exists.");
                return false;
            }

            if (editing && typeBox.getValue() == SchedulerTime.ScheduleType.BREAK) {

                final boolean hasLectures = scheduler.getLessons().values().stream()
                        .flatMap(List::stream)
                        .anyMatch(lecture -> lecture.getLessonId() == existingId);

                if (hasLectures) {
                    GuiSupport.showValidationError("Remove every lecture scheduled into this period before turning it into a break.");
                    return false;
                }

            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            if (editing) {
                existing.setScheduleType(typeBox.getValue());
                existing.setStartDate(LocalTime.of(startHour.getValue(), startMinute.getValue()));
                existing.setEndDate(LocalTime.of(endHour.getValue(), endMinute.getValue()));
            } else {
                scheduler.appendSchedulerTime(
                        scheduler.nextLessonId(), typeBox.getValue(),
                        startHour.getValue(), startMinute.getValue(), endHour.getValue(), endMinute.getValue()
                );
            }

            EntityFactory.getInstance().syncToDatabase();

            onChange.run();

        });

    }

    /**
     * Removes {@code period} after confirmation, warning first if any lecture is
     * currently scheduled into it (see {@link Scheduler#removeSchedulerTime(int)},
     * which removes those along with the period itself, the same way
     * {@link de.lino.thma.ui.tab.ModulesTab#removeModule(Module, TableView)} warns before unlinking a
     * module from every semester that teaches it). On confirmation {@code onChange} is
     * run, so the caller can rebuild whatever view is showing {@code scheduler}'s
     * periods or weekly lesson plan.
     *
     * @param scheduler the scheduler to remove {@code period} from
     * @param period the period to remove, e.g. the "Periods" table's current selection
     * @param onChange run after the period has been removed and persisted
     */
    private static void removePeriod(final Scheduler scheduler, final Map.Entry<Integer, SchedulerTime> period, final Runnable onChange) {

        final boolean hasLectures = scheduler.getLessons().values().stream()
                .flatMap(List::stream)
                .anyMatch(lesson -> lesson.hasKey(String.valueOf(period.getKey())));

        final String message = "Remove period " + GuiSupport.idLabel(period.getKey()) + " (" + timeRangeOf(scheduler, period.getKey()) + ")?"
                + (hasLectures ? " Every lecture scheduled into it will be removed too." : "");

        if (!GuiSupport.confirmDeletion("Remove Period", message)) return;

        scheduler.removeSchedulerTime(period.getKey());
        EntityFactory.getInstance().syncToDatabase();

        onChange.run();

    }

    /**
     * Opens a modal dialog for applying a whole {@link PeriodLayout} to
     * {@code scheduler} at once: a single choice of layout, defaulted to
     * {@link PeriodLayout#WINTER_SEMESTER}. On confirmation, every one of the chosen
     * layout's periods not already covered by an existing one is appended (see
     * {@link PeriodLayout#applyTo(Scheduler)}) and {@code onChange} is run, so the
     * caller can rebuild whatever view is showing {@code scheduler}'s periods; a short
     * summary of how many periods were actually added is shown afterward.
     *
     * @param scheduler the scheduler to apply the chosen layout to
     * @param onChange run after the layout has been applied and persisted
     */
    private static void periodLayoutDialog(final Scheduler scheduler, final Runnable onChange) {

        final ComboBox<PeriodLayout> layoutBox = new ComboBox<>(FXCollections.observableArrayList(PeriodLayout.values()));
        layoutBox.setConverter(GuiSupport.nameConverter(PeriodLayout::getDisplayName));
        layoutBox.getSelectionModel().selectFirst();

        final GridPane grid = GuiSupport.formGrid("Layout", layoutBox);

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Period Layout", grid, event -> {

            if (layoutBox.getValue() == null) {
                GuiSupport.showValidationError("Select a layout.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final PeriodLayout layout = layoutBox.getValue();
            final int applied = layout.applyTo(scheduler);
            EntityFactory.getInstance().syncToDatabase();

            onChange.run();

            GuiSupport.showSuccess(applied == 0
                    ? "Every period in \"" + layout.getDisplayName() + "\" was already present."
                    : "Added " + applied + " period(s) from \"" + layout.getDisplayName() + "\".");

        });

    }

    /**
     * Builds the "Week" sub-tab: an "Export" menu button (see
     * {@link #timeTableExportButton(Scheduler)}, disabled while {@code scheduler} has no
     * periods yet), an "+ Add Lesson" one (disabled until {@code scheduler} has at least
     * one {@link SchedulerTime.ScheduleType#LECTURE} period to schedule a lesson into)
     * and a "− Remove Lesson" one (disabled until it has at least one lecture actually
     * scheduled), above a timetable grid laid out the same way {@code "table.pdf"} is -
     * one row per {@link SchedulerTime} period, sorted by id, one column per
     * {@link #WEEK} day - rather than one table per day.
     *
     * @param semester the semester {@code scheduler} belongs to, whose linked modules a lecture may be assigned to
     * @param scheduler the scheduler to show the weekly lesson plan of
     * @param onChange run after a lesson has been appended, removed and persisted, to rebuild whatever view is showing it
     * @return the built sub-tab
     */
    private static Tab weekTab(final Semester semester, final Scheduler scheduler, final Runnable onChange) {

        final Tab tab = new Tab("Time Table");
        tab.setClosable(false);

        final boolean hasLecturePeriod = scheduler.getSchedulerTimes().values().stream()
                .anyMatch(time -> time.getScheduleType() == SchedulerTime.ScheduleType.LECTURE);
        final boolean hasScheduledLecture = WEEK.stream().anyMatch(day -> !scheduler.getLessons().getOrDefault(day, List.of()).isEmpty());

        final Button addButton = new Button("+ Add Lesson");
        addButton.getStyleClass().add("button-primary");
        addButton.setDisable(!hasLecturePeriod);
        addButton.setOnAction(event -> lessonDialog(semester, scheduler, null, null, onChange));

        final Button removeButton = new Button("− Remove Lesson");
        removeButton.getStyleClass().add("button-danger");
        removeButton.setDisable(!hasScheduledLecture);
        removeButton.setOnAction(event -> removeLessonDialog(scheduler, onChange));

        final HBox toolbar = new HBox(8, timeTableExportButton(scheduler), addButton, removeButton);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(8));
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        final BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(timetableGrid(semester, scheduler, onChange));
        tab.setContent(pane);

        return tab;

    }

    /**
     * Builds the "Week" sub-tab's "Export" menu button, offering exactly "Export as
     * PDF" and "Export as CSV" - unlike {@link GuiSupport#exportButton(String, TableView, List)},
     * which always offers all four formats {@link GuiSupport} otherwise supports, this
     * timetable is exported through {@link ExportCoordinator} directly so it can leave
     * Excel and JSON out. Both formats export the same "Period"-then-weekday columns,
     * the same period rows sorted by id, and the same per-cell text
     * {@link #timetableGrid(Semester, Scheduler, Runnable)} itself shows - a lecture's
     * module tag, room and professor, or blank (including for a break, which is never
     * labeled as one); only the "Period" cell itself differs between the two formats,
     * since the PDF exporter cannot render a stacked one (see
     * {@link #exportPeriodText(Scheduler, Map, int, boolean)}). Disabled while
     * {@code scheduler} has no periods yet, since there would be nothing to export.
     *
     * @param scheduler the scheduler to export the periods and weekly lesson plan of
     * @return the built menu button
     */
    private static MenuButton timeTableExportButton(final Scheduler scheduler) {

        final List<Integer> periodIds = scheduler.getSchedulerTimes().keySet().stream().sorted().toList();
        final Map<Integer, Integer> lectureNumbers = lectureNumbersOf(scheduler);

        final List<String> headers = new ArrayList<>(List.of("Period"));
        WEEK.forEach(day -> headers.add(displayName(day)));

        final String fileName = scheduler.getSemesterName().replace(".", "_") + "_Time_Table";
        final String documentTitle = "Time Table (" +  scheduler.getSemesterName() + ")";

        final MenuItem pdfItem = exportAs(headers, scheduler, periodIds, lectureNumbers, fileName, documentTitle, "Export as Excel", ExportType.EXCEL);

        final MenuItem excelItem = exportAs(headers, scheduler, periodIds, lectureNumbers, fileName, documentTitle, "Export as PDF", ExportType.PDF);

        final MenuItem csvItem = exportAs(headers, scheduler, periodIds, lectureNumbers, fileName, documentTitle, "Export as CSV", ExportType.CSV);

        final MenuItem jsonItem = exportAs(headers, scheduler, periodIds, lectureNumbers, fileName, documentTitle, "Export as Json", ExportType.JSON);

        final MenuItem xmlItem = exportAs(headers, scheduler, periodIds, lectureNumbers, fileName, documentTitle, "Export as XML", ExportType.XML);

        final MenuItem docxItem = exportAs(headers, scheduler, periodIds, lectureNumbers, fileName, documentTitle, "Export as Docx", ExportType.DOCX);

        final MenuButton button = new MenuButton("Export", null, pdfItem, excelItem, csvItem, jsonItem, xmlItem, docxItem);
        button.setDisable(periodIds.isEmpty());

        return button;

    }

    private static MenuItem exportAs(
            final List<String> headers, final Scheduler scheduler, final List<Integer> periodIds
            , final Map<Integer, Integer> lectureNumbers, final String fileName, final String documentTitle
            , final String text, final ExportType exportType
    ) {
        final MenuItem item = new MenuItem(text);
        item.setOnAction(event -> GuiSupport.promptPageLayout().ifPresent(layout ->
                exportTimeTable(headers,
                        exportRows(scheduler, periodIds, lectureNumbers, exportType == ExportType.CSV), exportType.adjustSuffixToFile(Path.of(fileName)).toString(), documentTitle, layout)
                )
        );
        return item;
    }

    /**
     * Builds the timetable's exported rows, one per {@code periodIds} entry, each
     * starting with {@link #exportPeriodText(Scheduler, Map, int, boolean)} followed by
     * {@link #exportCellText(Scheduler, DayOfWeek, int)} for every {@link #WEEK} day.
     *
     * @param scheduler the scheduler to read the periods and weekly lessons of
     * @param periodIds the period ids to build one row for each of, in display order
     * @param lectureNumbers each lecture period's display number, as built by {@link #lectureNumbersOf(Scheduler)}
     * @param stackPeriodId whether the "Period" cell puts a lecture's display number on its own line above the time range
     * @return the built rows
     */
    private static List<List<String>> exportRows(
            final Scheduler scheduler, final List<Integer> periodIds, final Map<Integer, Integer> lectureNumbers, final boolean stackPeriodId
    ) {
        return periodIds.stream()
                .map(periodId -> {
                    final List<String> row = new ArrayList<>(List.of(exportPeriodText(scheduler, lectureNumbers, periodId, stackPeriodId)));
                    WEEK.forEach(day -> row.add(exportCellText(scheduler, day, periodId)));
                    return row;
                })
                .toList();
    }

    /**
     * Writes the timetable's {@code headers} and {@code rows} to {@link Constraints#EXPORT_PATH},
     * as a single untitled {@link TranscriptSection}, reporting success or failure via
     * {@link GuiSupport#runExport(GuiSupport.ExportAction, String)} the same way every
     * other export in this app does.
     *
     * @param headers the column headers, in display order
     * @param rows the data rows, in display order
     * @param fileName the exported file's name, resolved against {@link Constraints#EXPORT_PATH}
     * @param pageLayout the page format and orientation to render a PDF export at, ignored for CSV
     */
    private static void exportTimeTable(final List<String> headers, final List<List<String>> rows, final String fileName, final String documentTitle, final PageLayout pageLayout) {

        final TranscriptSection section = new TranscriptSection("", rows);
        final ExportCoordinator coordinator = new ExportCoordinator();

        GuiSupport.runExport(() -> coordinator.exportTranscript(
                documentTitle, headers, List.of(section), "", List.of(), pageLayout, Constraints.EXPORT_PATH.resolve(fileName)
        ), fileName);

    }

    /**
     * The exported text of the timetable grid's leftmost "Period" cell for one period -
     * the same two lines {@link #periodColumn(Scheduler)} itself shows: a lecture
     * period's display number (see {@link #lectureNumbersOf(Scheduler)}) above its time
     * range, or just the time range on its own for a break, which is never numbered.
     *
     * <p>{@code stackPeriodId} controls how literally "above" is taken: CSV (see
     * {@link #timeTableExportButton(Scheduler)}) genuinely stacks the two on separate
     * lines within one quoted cell, exactly like the on-screen grid, since a CSV cell
     * happily holds an embedded newline. The PDF exporter cannot: passing a
     * {@code "\n"} through {@link de.lino.database.utility.export.ExportCoordinator}
     * crashes with {@code IllegalArgumentException: U+000A ('controlLF') is not
     * available in the font Helvetica} the moment it tries to measure the string's
     * width, so a PDF export instead falls back to the number and time range on one
     * line, space-separated.
     *
     * @param scheduler the scheduler to read {@code periodId}'s time range from
     * @param lectureNumbers {@code periodId}'s display number, as built by {@link #lectureNumbersOf(Scheduler)}
     * @param periodId the id of the period to format
     * @param stackPeriodId {@code true} to join the number and time range with a newline (CSV), {@code false} to join them with a space (PDF)
     * @return the formatted cell text
     */
    private static String exportPeriodText(
            final Scheduler scheduler, final Map<Integer, Integer> lectureNumbers, final int periodId, final boolean stackPeriodId
    ) {
        final Integer number = lectureNumbers.get(periodId);
        final String time = timeRangeOf(scheduler, periodId);
        return number == null ? time : GuiSupport.idLabel(number) + (stackPeriodId ? "\n" : " ") + time;
    }

    /**
     * The exported text of one timetable grid cell - the same information
     * {@link #dayColumn(Semester, Scheduler, DayOfWeek, Runnable)} itself shows for that
     * period/day combination, just on one line instead of two: blank for a
     * {@link SchedulerTime.ScheduleType#BREAK} period (the word "Break" is never printed
     * - the row's own blank cells already read as a break); a lecture's module tag, room
     * and professor for a {@link SchedulerTime.ScheduleType#LECTURE} one with a lecture
     * actually scheduled into {@code day}; blank otherwise.
     *
     * @param scheduler the scheduler to read {@code day}'s lessons from
     * @param day the weekday this cell is in the column of
     * @param periodId the id of the period this cell is in the row of
     * @return the exported cell text
     */
    private static String exportCellText(final Scheduler scheduler, final DayOfWeek day, final int periodId) {

        if (scheduler.get(periodId).getScheduleType() == SchedulerTime.ScheduleType.BREAK) {
            return "";
        }

        return scheduler.getLessons().getOrDefault(day, List.of()).stream()
                .filter(lecture -> lecture.getLessonId() == periodId)
                .findFirst()
                .map(lecture -> lecture.getModuleTag() + " (" + lecture.getRoom() + ") - " + String.join(", ", lecture.getProfessor()))
                .orElse("");

    }

    /**
     * Builds the timetable grid itself: one row per {@link SchedulerTime} period
     * registered in {@code scheduler}, sorted by id, under a leftmost "Period" column
     * (see {@link #periodColumn(Scheduler)}) followed by one column per {@link #WEEK}
     * day (see {@link #dayColumn(Semester, Scheduler, DayOfWeek, Runnable)}) - the same
     * row-per-period, column-per-weekday shape {@code "table.pdf"} itself uses. A
     * double-click on an occupied lecture cell reopens {@link #lessonDialog(Semester, Scheduler, DayOfWeek, Lecture, Runnable)}
     * pre-filled with that cell's own lecture, to reconfigure it in place.
     *
     * @param semester the semester {@code scheduler} belongs to, whose linked modules a lecture may be assigned to
     * @param scheduler the scheduler to read the periods and weekly lessons of
     * @param onChange run after a lecture has been edited and persisted, to rebuild whatever view is showing it
     * @return the built grid
     */
    private static TableView<Integer> timetableGrid(final Semester semester, final Scheduler scheduler, final Runnable onChange) {

        final List<Integer> periodIds = scheduler.getSchedulerTimes().keySet().stream().sorted().toList();
        final TableView<Integer> table = new TableView<>(FXCollections.observableArrayList(periodIds));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(76);
        table.prefHeightProperty().bind(table.fixedCellSizeProperty().multiply(Bindings.size(table.getItems()).add(1.5)));

        table.getColumns().add(periodColumn(scheduler));
        WEEK.forEach(day -> table.getColumns().add(dayColumn(semester, scheduler, day, onChange)));

        return table;

    }

    /**
     * Builds the grid's leftmost "Period" column: each cell shows the period's own
     * lesson id above its time range - two stacked lines, rather than side by side -
     * built from actual {@link Label} nodes rather than a single wrapped string, so
     * each line renders with its own weight and size regardless of column width.
     *
     * @param scheduler the scheduler to read each period's time range from
     * @return the built column
     */
    private static TableColumn<Integer, Integer> periodColumn(final Scheduler scheduler) {

        final Map<Integer, Integer> lectureNumbers = lectureNumbersOf(scheduler);

        final TableColumn<Integer, Integer> column = new TableColumn<>("Period");
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        column.setMinWidth(110);

        column.setCellFactory(col -> new TableCell<>() {

            private final Label idLabel = new Label();
            private final Label timeLabel = new Label();
            private final VBox box = new VBox(2, idLabel, timeLabel);

            {
                idLabel.setStyle("-fx-font-weight: bold;");
                timeLabel.getStyleClass().add("stat-title");
                box.setAlignment(Pos.CENTER);
                idLabel.setMaxWidth(Double.MAX_VALUE);
                idLabel.setAlignment(Pos.CENTER);
                timeLabel.setMaxWidth(Double.MAX_VALUE);
                timeLabel.setAlignment(Pos.CENTER);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(final Integer periodId, final boolean empty) {

                super.updateItem(periodId, empty);

                if (empty || periodId == null) {
                    setGraphic(null);
                    return;
                }

                final Integer lectureNumber = lectureNumbers.get(periodId);

                idLabel.setText(lectureNumber == null ? null : GuiSupport.idLabel(lectureNumber));
                timeLabel.setText(timeRangeOf(scheduler, periodId));
                setGraphic(box);

            }

        });

        return column;

    }

    /**
     * Numbers {@code scheduler}'s own {@link SchedulerTime.ScheduleType#LECTURE}
     * periods 1, 2, 3... in id order, skipping every
     * {@link SchedulerTime.ScheduleType#BREAK} one - the display id
     * {@link #periodColumn(Scheduler)} shows above a lecture period's time range, kept
     * separate from that period's real, internal lesson id (used everywhere else, e.g.
     * {@link Scheduler#get(int)} or {@link Lecture#getLessonId()}) so a break never
     * consumes a number of its own. E.g. 11 periods alternating lecture/break/lecture/...
     * number their 6 lectures 1 through 6, not 1, 3, 5, 7, 9, 11.
     *
     * @param scheduler the scheduler to number the lecture periods of
     * @return each lecture period's real lesson id mapped to its 1-based display number
     */
    private static Map<Integer, Integer> lectureNumbersOf(final Scheduler scheduler) {

        final Map<Integer, Integer> numbers = new LinkedHashMap<>();

        scheduler.getSchedulerTimes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getValue().getScheduleType() == SchedulerTime.ScheduleType.LECTURE)
                .forEach(entry -> numbers.put(entry.getKey(), numbers.size() + 1));

        return numbers;

    }

    /**
     * Builds one weekday column of the timetable grid: each cell is left blank for a
     * {@link SchedulerTime.ScheduleType#BREAK} row - the word "Break" is never printed,
     * the row's own blank cells (and {@link #periodColumn(Scheduler)}'s own blank id for
     * it) already read as one - blank as well for an unscheduled
     * {@link SchedulerTime.ScheduleType#LECTURE} row, or - once a {@link Lecture} has
     * been appended to {@code day} under that row's period id (see
     * {@link Scheduler#appendLesson(DayOfWeek, Lecture)}) - two centered lines built
     * from actual {@link Label} nodes rather than a single wrapped string: the module
     * tag and room on top, the professor below. A double-click on an occupied lecture
     * cell reopens {@link #lessonDialog(Semester, Scheduler, DayOfWeek, Lecture, Runnable)}
     * pre-filled with that cell's own lecture; a break or empty cell ignores the click.
     *
     * @param semester the semester whose linked modules a reconfigured lecture may be assigned to
     * @param scheduler the scheduler to read {@code day}'s lessons from
     * @param day the weekday this column is scoped to
     * @param onChange run after a lecture has been reconfigured and persisted, to rebuild whatever view is showing it
     * @return the built column
     */
    private static TableColumn<Integer, Integer> dayColumn(final Semester semester, final Scheduler scheduler, final DayOfWeek day, final Runnable onChange) {

        final TableColumn<Integer, Integer> column = new TableColumn<>(displayName(day));
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        column.setMinWidth(150);

        column.setCellFactory(col -> new TableCell<>() {

            private final Label tagRoomLabel = new Label();
            private final Label professorLabel = new Label();
            private final VBox box = new VBox(2, tagRoomLabel, professorLabel);

            {
                tagRoomLabel.setStyle("-fx-font-weight: bold;");
                professorLabel.getStyleClass().add("stat-title");
                box.setAlignment(Pos.CENTER);
                tagRoomLabel.setMaxWidth(Double.MAX_VALUE);
                tagRoomLabel.setAlignment(Pos.CENTER);
                professorLabel.setMaxWidth(Double.MAX_VALUE);
                professorLabel.setAlignment(Pos.CENTER);
                setAlignment(Pos.CENTER);

                setOnMouseClicked(event -> {

                    if (event.getClickCount() != 2 || isEmpty() || getItem() == null) return;

                    final int periodId = getItem();
                    if (scheduler.get(periodId).getScheduleType() == SchedulerTime.ScheduleType.BREAK) return;

                    scheduler.getLessons().getOrDefault(day, List.of()).stream()
                            .filter(scheduled -> scheduled.getLessonId() == periodId)
                            .findFirst()
                            .ifPresent(lecture -> lessonDialog(semester, scheduler, day, lecture, onChange));

                });

            }

            @Override
            protected void updateItem(final Integer periodId, final boolean empty) {

                super.updateItem(periodId, empty);

                if (empty || periodId == null) {
                    setGraphic(null);
                    return;
                }

                if (scheduler.get(periodId).getScheduleType() == SchedulerTime.ScheduleType.BREAK) {
                    setGraphic(null);
                    return;
                }

                final Optional<Lecture> lecture = scheduler.getLessons().getOrDefault(day, List.of()).stream()
                        .filter(scheduled -> scheduled.getLessonId() == periodId)
                        .findFirst();

                if (lecture.isEmpty()) {
                    setGraphic(null);
                    return;
                }

                tagRoomLabel.setText(lecture.get().getModuleTag() + " (" + lecture.get().getRoom() + ")");
                professorLabel.setText(String.join(", ", lecture.get().getProfessor()));
                setGraphic(box);

            }

        });

        return column;

    }

    /**
     * Opens a modal dialog for appending a new {@link Lecture} to one of
     * {@code semester}'s scheduler's days, or editing an existing one in place, asking
     * for exactly five things: the day, the {@link SchedulerTime} period it is taught in
     * (restricted to {@link SchedulerTime.ScheduleType#LECTURE} ones - a lecture is
     * never scheduled into a break), the module (restricted to {@link Semester#getModules()},
     * displayed by its own {@link Module#getTag()}), the professor and the room - all
     * five required.
     *
     * <p>{@code existingLecture} tells the two modes apart: {@code null} (alongside a
     * {@code null} {@code existingDay}) appends a brand new lecture, while an actual
     * lecture - e.g. from a double-click on {@link #dayColumn(Semester, Scheduler, DayOfWeek, Runnable)}'s
     * own grid cell - pre-fills the dialog with its current day, period, module,
     * professor and room, and on confirmation removes it (see
     * {@link Scheduler#removeLesson(DayOfWeek, int)}) before appending the edited
     * version - which may land on a different day or period than the original, if
     * either was changed. On confirmation {@code onChange} is run, so the caller can
     * rebuild whatever view is showing the affected day(s).
     *
     * @param semester the semester whose linked modules the lecture may be assigned to
     * @param scheduler the scheduler to append or edit the lecture on
     * @param existingDay the day the lecture being edited is currently scheduled on, or {@code null} to append a new one
     * @param existingLecture the lecture to edit, or {@code null} to append a new one
     * @param onChange run after the lecture has been appended or edited, and persisted
     */
    private static void lessonDialog(
            final Semester semester, final Scheduler scheduler,
            final DayOfWeek existingDay, final Lecture existingLecture, final Runnable onChange
    ) {

        final boolean editing = existingLecture != null;

        final ComboBox<DayOfWeek> dayBox = new ComboBox<>(FXCollections.observableArrayList(WEEK));
        dayBox.setConverter(GuiSupport.nameConverter(SchedulerTab::displayName));

        final List<Integer> periodIds = scheduler.getSchedulerTimes().entrySet().stream()
                .filter(entry -> entry.getValue().getScheduleType() == SchedulerTime.ScheduleType.LECTURE)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        final ComboBox<Integer> periodBox = new ComboBox<>(FXCollections.observableArrayList(periodIds));
        periodBox.setConverter(GuiSupport.nameConverter(id -> timeRangeOf(scheduler, id)));
        periodBox.setPromptText(periodIds.isEmpty() ? "No lecture periods yet" : "Select a period");

        final List<Module> modules = semester.getModules();
        final ComboBox<Module> moduleBox = new ComboBox<>(FXCollections.observableArrayList(modules));
        moduleBox.setConverter(GuiSupport.nameConverter(Module::getTag));
        moduleBox.setPromptText(modules.isEmpty() ? "No modules linked to this semester" : "Select a module tag");

        final TextField professorField = new TextField();
        final TextField roomField = new TextField();

        if (editing) {
            dayBox.getSelectionModel().select(existingDay);
            periodBox.getSelectionModel().select(existingLecture.getLessonId());
            modules.stream()
                    .filter(module -> module.getTag().equalsIgnoreCase(existingLecture.getModuleTag()))
                    .findFirst()
                    .ifPresent(moduleBox.getSelectionModel()::select);
            professorField.setText(String.join(", ", existingLecture.getProfessor()));
            roomField.setText(existingLecture.getRoom());
        } else {
            dayBox.getSelectionModel().selectFirst();
        }

        final GridPane grid = GuiSupport.formGrid(
                "Day", dayBox,
                "Lesson", periodBox,
                "Module Tag", moduleBox,
                "Professor", professorField,
                "Room", roomField
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog(editing ? "Edit Lesson" : "Add Lesson", grid, event -> {

            if (dayBox.getValue() == null || periodBox.getValue() == null || moduleBox.getValue() == null
                    || professorField.getText().isBlank() || roomField.getText().isBlank()) {
                GuiSupport.showValidationError("Day, lesson, module tag, professor and room are all required.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            if (editing) {
                scheduler.removeLesson(existingDay, existingLecture.getLessonId());
            }

            final Lecture lecture = new Lecture(
                    periodBox.getValue(), moduleBox.getValue().getTag(), roomField.getText().trim(), professorsOf(professorField.getText())
            );

            scheduler.appendLesson(dayBox.getValue(), lecture);
            EntityFactory.getInstance().syncToDatabase();

            onChange.run();

        });

    }

    /**
     * Opens a modal dialog for removing a {@link Lecture} from one of
     * {@code scheduler}'s days: a day, restricted to ones with at least one lecture
     * scheduled, and one of that day's own lectures, picked by its period's time range
     * (see {@link Scheduler#removeLesson(DayOfWeek, int)}). On confirmation
     * {@code onChange} is run, so the caller can rebuild whatever view is showing the
     * affected day.
     *
     * @param scheduler the scheduler to remove the lecture from
     * @param onChange run after the lecture has been removed and persisted
     */
    private static void removeLessonDialog(final Scheduler scheduler, final Runnable onChange) {

        final List<DayOfWeek> daysWithLectures = WEEK.stream()
                .filter(day -> !scheduler.getLessons().getOrDefault(day, List.of()).isEmpty())
                .toList();

        final ComboBox<DayOfWeek> dayBox = new ComboBox<>(FXCollections.observableArrayList(daysWithLectures));
        dayBox.setConverter(GuiSupport.nameConverter(SchedulerTab::displayName));

        final ComboBox<Integer> periodBox = new ComboBox<>();
        periodBox.setConverter(GuiSupport.nameConverter(id -> timeRangeOf(scheduler, id)));

        dayBox.valueProperty().addListener((observable, oldDay, day) -> {
            final List<Integer> ids = day == null ? List.of() : scheduler.getLessons().getOrDefault(day, List.of()).stream()
                    .map(Lecture::getLessonId)
                    .sorted()
                    .toList();
            periodBox.setItems(FXCollections.observableArrayList(ids));
            periodBox.getSelectionModel().clearSelection();
        });
        dayBox.getSelectionModel().selectFirst();

        final GridPane grid = GuiSupport.formGrid(
                "Day", dayBox,
                "Lesson", periodBox
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Remove Lesson", grid, event -> {

            if (dayBox.getValue() == null || periodBox.getValue() == null) {
                GuiSupport.showValidationError("Select a day and a lesson to remove.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            scheduler.removeLesson(dayBox.getValue(), periodBox.getValue());
            EntityFactory.getInstance().syncToDatabase();

            onChange.run();

        });

    }

    /**
     * Splits a comma-separated professor list from the "Add Lesson" dialog's free-text
     * field into individual, trimmed names.
     *
     * @param text the comma-separated text to split, possibly blank
     * @return the individual professor names, or an empty array if {@code text} is blank
     */
    private static String[] professorsOf(final String text) {
        return text.isBlank() ? new String[0] : text.trim().split("\\s*,\\s*");
    }

    /**
     * The start and end time of the period {@code lessonId} is scheduled into, formatted
     * as {@code "<start> - <end>"}.
     *
     * @param scheduler the scheduler to look {@code lessonId}'s period up in
     * @param lessonId the id of the period to format
     * @return the formatted time range
     */
    private static String timeRangeOf(final Scheduler scheduler, final int lessonId) {
        final SchedulerTime time = scheduler.get(lessonId);
        return time.getStartDate() + " - " + time.getEndDate();
    }

    /**
     * Formats a {@link DayOfWeek} for display, e.g. {@link DayOfWeek#MONDAY} as
     * {@code "Monday"} rather than its all-caps {@link DayOfWeek#name()}.
     *
     * @param day the day to format
     * @return the formatted day name
     */
    private static String displayName(final DayOfWeek day) {
        final String name = day.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

}
