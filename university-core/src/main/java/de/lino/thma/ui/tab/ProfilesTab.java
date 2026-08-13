package de.lino.thma.ui.tab;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.profile.login.Login;
import de.lino.thma.domain.entity.profile.login.Role;
import de.lino.thma.domain.entity.profile.Information;
import de.lino.thma.ui.helper.ColumnSpec;
import de.lino.thma.ui.helper.EntityTab;
import de.lino.thma.ui.helper.GuiSupport;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

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
 * A student's password is never shown as a plain table column, but double-clicking a
 * row reveals it (see {@link #showCredentialsDialog(Profile)}), and it is always
 * included, in plaintext, in this tab's own export (see {@link Login#getPassword()}) -
 * both admin-only, since only an admin ever reaches this tab in the first place.
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
     * Builds a {@link TableRow} that, on a double-click, reveals the row's own
     * {@link Profile}'s login credentials via {@link #showCredentialsDialog(Profile)} -
     * skipped for a double-click landing on an empty row (past the last actual entry).
     *
     * @return the built row
     */
    private static TableRow<Profile> credentialsRevealingRow() {

        final TableRow<Profile> row = new TableRow<>();

        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) showCredentialsDialog(row.getItem());
        });

        return row;

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
