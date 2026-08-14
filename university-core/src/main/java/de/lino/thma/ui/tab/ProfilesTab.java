package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.profile.login.Login;
import de.lino.thma.domain.entity.profile.login.Role;
import de.lino.thma.domain.entity.profile.Information;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.entity.semester.SemesterType;
import de.lino.thma.ui.helper.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The "Students" tab, listing every registered {@link Profile} - each row its personal
 * {@link Information} alongside its account metadata, its matching
 * {@link Login} looked up by email via {@link Profile#getLoginCredentials()}
 * (registered separately under {@link EntityType#LOGIN}, not embedded in the profile
 * itself) - sorted by id, with an "Add Student" dialog for manually creating new ones.
 *
 * <p>This tab is admin-only: {@link de.lino.thma.UniversityGui} only ever constructs it
 * for an account whose {@link Login#isAdmin()} is {@code true} - a student never sees
 * every other student's own profile.
 *
 * <p>First name, last name, birthplace and address are editable in place; id, birthdate,
 * email and role are not - birthdate has no setter on {@link Information} to begin with,
 * and email/role live on the separately-registered {@link Login} rather than
 * {@link Information} itself, so editing them here would silently desynchronize a
 * student's displayed profile from the email/role their account actually logs in with.
 * A student's password is never shown as a plain table column, and it is always
 * included, in plaintext, in this tab's own export (see {@link Login#getPassword()}) -
 * admin-only, since only an admin ever reaches this tab in the first place.
 *
 * <p>Double-clicking a row reveals different information depending on that row's own
 * {@link Role}: for a {@link Role#STUDENT}, a statistics dialog (see
 * {@link #showStatisticsDialog(Profile)}) - that student's total semesters, exams and
 * modules, their average grade in {@link SemesterType#UNDER_GRADUATE_STUDY} and
 * {@link SemesterType#GRADUATE_STUDY} separately, the same "Export All Exams" menu
 * button {@link StatisticsTab} itself offers (scoped down to just that one student's own
 * exams), and their own login credentials in plaintext; for any other role (i.e. another
 * {@link Role#ADMIN}), just that account's own login credentials (see
 * {@link #showCredentialsDialog(Profile)}), the same as every row revealed before this
 * distinction existed.
 *
 * <p>Rebuilds itself from scratch every time it becomes the selected tab (see
 * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}), so a student added or removed
 * elsewhere is reflected here without restarting the app.
 */
public final class ProfilesTab extends EntityTab<Profile> {

    /**
     * Display and parse format used for the read-only "Birthdate" column and the "Add
     * Student" dialog's date picker.
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Builds the "Students" tab: a table of every registered student, sorted by id, with
     * add/remove actions, a double-click credentials reveal, and a password-including
     * export.
     */
    public ProfilesTab() {

        super("Profiles");

        this.rebuild();
        GuiSupport.refreshOnSelect(this, this::rebuild);

    }

    /**
     * (Re)builds this tab's entire content from the current state of
     * {@link EntityFactory}'s cache: the table of every registered student, sorted by
     * id, with add/remove actions, a double-click credentials reveal, and a
     * password-including export. Safe to call more than once - see
     * {@link GuiSupport#refreshOnSelect(Tab, Runnable)}.
     */
    private void rebuild() {

        final List<Profile> profiles = EntityFactory.getInstance().<Profile>getEntities(EntityType.PROFILE).stream()
                .sorted(Comparator.comparingInt(Profile::getId))
                .toList();
        final TableView<Profile> table = new TableView<>(FXCollections.observableArrayList(profiles));
        table.setRowFactory(view -> credentialsRevealingRow());

        final List<ColumnSpec<Profile>> columns = List.of(
                ColumnSpec.of("Id", p -> GuiSupport.idLabel(p.getId())),
                ColumnSpec.editable("First Name", p -> p.getInformation().getFirstName(),
                        (p, v) -> p.getInformation().setFirstName(GuiSupport.requireText(v, "First name"))),
                ColumnSpec.editable("Last Name", p -> p.getInformation().getLastName(),
                        (p, v) -> p.getInformation().setLastName(GuiSupport.requireText(v, "Last name"))),
                ColumnSpec.of("Birthdate", p -> Instant.ofEpochMilli(p.getInformation().getBirthdate()).atZone(ZoneOffset.UTC).format(DATE_FORMAT)),
                ColumnSpec.editable("Birthplace", p -> p.getInformation().getBirthPlace(),
                        (p, v) -> p.getInformation().setBirthPlace(GuiSupport.requireText(v, "Birthplace"))),
                ColumnSpec.editable("Address", p -> p.getInformation().getAddress(),
                        (p, v) -> p.getInformation().setAddress(GuiSupport.requireText(v, "Address"))),
                ColumnSpec.of("Email", p -> p.getLoginCredentials().map(Login::getEmail).orElse("-")),
                ColumnSpec.of("Role", p -> p.getLoginCredentials().map(login -> login.getRole().name()).orElse("-"))
        );

        final List<ColumnSpec<Profile>> exportColumns = new ArrayList<>(columns);
        exportColumns.add(ColumnSpec.of("Password", p -> p.getLoginCredentials().map(Login::getPassword).orElse("-")));

        this.buildContent(table, columns, exportColumns, "Students", "+ Add Student", () -> addStudentDialog(table),
                "− Remove Student", profile -> removeProfile(profile, table));

    }

    /**
     * Builds a {@link TableRow} that, on a double-click, reveals different information
     * about the row's own {@link Profile} depending on its {@link Role}: a
     * {@link Role#STUDENT}'s statistics (see {@link #showStatisticsDialog(Profile)}), or
     * any other role's own login credentials (see {@link #showCredentialsDialog(Profile)})
     * - skipped for a double-click landing on an empty row (past the last actual entry),
     * or one whose {@link Profile} has no matching {@link Login} to check the role of.
     *
     * @return the built row
     */
    private static TableRow<Profile> credentialsRevealingRow() {

        final TableRow<Profile> row = new TableRow<>();

        row.setOnMouseClicked(event -> {

            if (event.getClickCount() != 2 || row.isEmpty()) return;

            final Profile profile = row.getItem();

            if (profile.getLoginCredentials().map(Login::isStudent).orElse(false)) {
                showStatisticsDialog(profile);
            } else {
                showCredentialsDialog(profile);
            }

        });

        return row;

    }

    /**
     * Shows a {@link Role#STUDENT}'s own statistics in a blocking dialog: their total
     * {@link Profile#getSemesters()}, and the total {@link Exam}s and {@link Module}s
     * reachable across all of them (deduplicated the same way {@link StatisticsTab}'s own
     * per-type panels are, in case a shared module links more than one of the student's
     * semesters), their average grade in {@link SemesterType#UNDER_GRADUATE_STUDY} and
     * {@link SemesterType#GRADUATE_STUDY} computed separately (a semester with neither
     * type assigned yet counts toward neither), {@link StatisticsTab}'s own
     * "Export All Exams" menu button, reused unmodified but scoped down to just this one
     * student's own semesters, and, the same as {@link #showCredentialsDialog(Profile)}
     * shows for any other role, this student's own login credentials in plaintext (see
     * {@link Login#getPassword()}) - admin-only, since this whole tab is (see this
     * class's own Javadoc).
     *
     * @param profile the student whose statistics to show
     */
    private static void showStatisticsDialog(final Profile profile) {

        final List<Semester> semesters = profile.getSemesters().stream()
                .map(profile::getSemester)
                .flatMap(Optional::stream)
                .toList();

        final List<Exam> exams = semesters.stream().flatMap(semester -> semester.getExams().stream()).distinct().toList();
        final List<Module> modules = semesters.stream().flatMap(semester -> semester.getModules().stream()).distinct().toList();

        final Label credentialsLabel = new Label(profile.getLoginCredentials()
                .map(login -> "Email: " + login.getEmail() + "\nPassword: " + login.getPassword())
                .orElse("No login credentials found."));

        final HBox cards = new HBox(16,
                ExamStatistics.statCard("Semesters", String.valueOf(semesters.size())),
                ExamStatistics.statCard("Exams", String.valueOf(exams.size())),
                ExamStatistics.statCard("Modules", String.valueOf(modules.size())),
                ExamStatistics.statCard("Avg. Grade (Undergraduate)", ExamStatistics.formatGrade(ExamStatistics.averageGrade(averageGradeExams(semesters, SemesterType.UNDER_GRADUATE_STUDY)))),
                ExamStatistics.statCard("Avg. Grade (Graduate)", ExamStatistics.formatGrade(ExamStatistics.averageGrade(averageGradeExams(semesters, SemesterType.GRADUATE_STUDY))))
        );
        cards.setAlignment(Pos.CENTER);

        final HBox exportBar = new HBox(StatisticsTab.exportAllExamsButton(semesters));
        exportBar.setAlignment(Pos.CENTER_RIGHT);

        final VBox content = new VBox(16, credentialsLabel, cards, exportBar);
        content.setPadding(new Insets(16));

        final Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(profile.getInformation().getFullName() + " — Statistics");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Theme.style(dialog.getDialogPane());

        dialog.showAndWait();

    }

    /**
     * Resolves the exams to average across for {@link #showStatisticsDialog(Profile)}'s
     * own {@code type}-specific stat card: every exam reachable from {@code semesters}
     * filtered down to those classified as {@code type}, deduplicated in case a shared
     * {@link Module} links more than one of them.
     *
     * @param semesters the student's own semesters to filter and flatten
     * @param type the study type to filter down to
     * @return the resolved exams
     */
    private static List<Exam> averageGradeExams(final List<Semester> semesters, final SemesterType type) {
        return semesters.stream()
                .filter(semester -> semester.getType() == type)
                .flatMap(semester -> semester.getExams().stream())
                .distinct()
                .toList();
    }

    /**
     * Shows {@code profile}'s own login credentials - email and, in plaintext,
     * password (see {@link Login#getPassword()}) - in a blocking info dialog, admin-only
     * since this whole tab is (see this class's own Javadoc).
     *
     * @param profile the student whose credentials to reveal
     */
    private static void showCredentialsDialog(final Profile profile) {

        final Optional<Login> login = profile.getLoginCredentials();

        if (login.isEmpty()) {
            GuiSupport.showValidationError("No login credentials found for \"" + profile.getInformation().getFullName() + "\".");
            return;
        }

        GuiSupport.showInfo("Login Credentials", "Email: " + login.get().getEmail() + "\nPassword: " + login.get().getPassword());

    }

    /**
     * Opens a modal dialog for manually creating a new {@link Profile} and its matching
     * {@link Login} - registered separately under {@link EntityType#PROFILE}
     * and {@link EntityType#LOGIN}, since a {@link Profile} no longer embeds its own
     * login account. On confirmation both are registered in {@link EntityFactory}'s
     * cache and the profile is appended to {@code table}; the entered password is
     * hashed by {@link Login}'s own constructor and never retained in
     * plaintext beyond this method.
     *
     * @param table the students table to append the newly created student to
     */
    private static void addStudentDialog(final TableView<Profile> table) {

        final TextField firstNameField = new TextField();
        final TextField lastNameField = new TextField();
        final DatePicker birthdatePicker = new DatePicker(LocalDate.now().minusYears(18));
        final TextField birthPlaceField = new TextField();
        final TextField addressField = new TextField();
        final TextField emailField = new TextField();
        final PasswordField passwordField = new PasswordField();

        final ComboBox<Role> roleBox = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        roleBox.setConverter(GuiSupport.nameConverter(Role::name));
        roleBox.setValue(Role.STUDENT);

        final GridPane grid = GuiSupport.formGrid(
                "First Name", firstNameField,
                "Last Name", lastNameField,
                "Birthdate", birthdatePicker,
                "Birthplace", birthPlaceField,
                "Address", addressField,
                "Email", emailField,
                "Password", passwordField,
                "Role", roleBox
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Add Student", grid, event -> {

            if (firstNameField.getText().isBlank() || lastNameField.getText().isBlank() || birthPlaceField.getText().isBlank()
                    || addressField.getText().isBlank() || emailField.getText().isBlank() || birthdatePicker.getValue() == null) {
                GuiSupport.showValidationError("First name, last name, birthdate, birthplace, address and email are required.");
                return false;
            }

            if (passwordField.getText().isBlank()) {
                GuiSupport.showValidationError("Password must not be empty.");
                return false;
            }

            if (EntityFactory.getInstance().findEntity(EntityType.LOGIN, emailField.getText().trim()).isPresent()) {
                GuiSupport.showValidationError("A student with email \"" + emailField.getText().trim() + "\" already exists.");
                return false;
            }

            return true;

        });

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(button -> {

            final Information information = new Information(
                    GuiSupport.epochMillisOf(birthdatePicker.getValue()),
                    firstNameField.getText().trim(),
                    lastNameField.getText().trim(),
                    birthPlaceField.getText().trim(),
                    addressField.getText().trim(),
                    emailField.getText().trim()
            );

            final Profile profile = new Profile(GuiSupport.nextId(EntityType.PROFILE, Profile::getId), information);
            final Login login = new Login(
                    information.getEmailAddress(), passwordField.getText(), roleBox.getValue()
            );

            EntityFactory.getInstance().registerEntitiesInCache(EntityType.PROFILE, profile);
            EntityFactory.getInstance().registerEntitiesInCache(EntityType.LOGIN, login);
            EntityFactory.getInstance().syncToDatabase();
            table.getItems().add(profile);

        });

    }

    /**
     * Removes {@code profile} after confirmation, along with its matching
     * {@link Login} (looked up by email under {@link EntityType#LOGIN}, if
     * still present) - otherwise the account would keep existing, and stay able to log
     * in, with no {@link Profile} left to show for it.
     *
     * @param profile the student to remove
     * @param table the students table to remove {@code profile} from
     */
    private static void removeProfile(final Profile profile, final TableView<Profile> table) {

        if (!GuiSupport.confirmDeletion("Remove Student", "Remove \""
                + profile.getInformation().getFullName() + "\"?")) return;

        profile.getLoginCredentials().ifPresent(loginCredentials -> {
            EntityFactory.getInstance().removeEntitiesFromCache(EntityType.LOGIN, loginCredentials);
            EntityFactory.getInstance().deleteFromDatabase(EntityType.LOGIN, loginCredentials);
        });

        EntityFactory.getInstance().removeEntitiesFromCache(EntityType.PROFILE, profile);
        EntityFactory.getInstance().deleteFromDatabase(EntityType.PROFILE, profile);
        EntityFactory.getInstance().syncToDatabase();
        table.getItems().remove(profile);

    }

}
