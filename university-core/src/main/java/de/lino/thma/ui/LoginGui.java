package de.lino.thma.ui;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.profile.Information;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.profile.login.Login;
import de.lino.thma.domain.entity.profile.login.Role;
import de.lino.thma.ui.helper.GuiSupport;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.NonNull;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The login screen shown before {@link de.lino.thma.UniversityGui}'s main window: a
 * centered card prompting for an email address and password, checked against the
 * {@link Login} already registered under {@link EntityType#LOGIN} - the only
 * entity type loaded into {@link EntityFactory}'s cache before a successful login (see
 * {@link de.lino.thma.UniversityGui#main(String[])}) - behind a "Login" button, next to
 * a "Register" button for anyone without an account yet (see
 * {@link #showRegisterDialog(Consumer)}).
 *
 * <p>{@code onLoginSuccess} is only invoked once a matching account is found (or, for
 * registration, just created), so no other entity data - most notably a student's own
 * {@link Profile} - is ever loaded, registered, or displayed before that happens.
 */
public final class LoginGui extends BorderPane {

    /**
     * The entered email address.
     */
    private final TextField emailField = new TextField();

    /**
     * The entered password.
     */
    private final PasswordField passwordField = new PasswordField();

    /**
     * Builds the login screen.
     *
     * @param onLoginSuccess invoked with the matched (or newly registered) account once a login or registration attempt succeeds
     * @throws NullPointerException if {@code onLoginSuccess} is {@code null}
     */
    public LoginGui(@NonNull final Consumer<Login> onLoginSuccess) {

        final Label title = new Label("University Driver");
        title.getStyleClass().add("app-title");

        final Label subtitle = new Label("Sign in to continue");
        subtitle.getStyleClass().add("stat-title");

        this.emailField.setPromptText("Email");
        this.passwordField.setPromptText("Password");
        this.passwordField.setOnAction(event -> attemptLogin(onLoginSuccess));

        final Button loginButton = new Button("Login");
        loginButton.getStyleClass().add("button-primary");
        loginButton.setDefaultButton(true);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> attemptLogin(onLoginSuccess));

        final Button registerButton = new Button("Register");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setOnAction(event -> showRegisterDialog(onLoginSuccess));

        final HBox buttons = new HBox(8, loginButton, registerButton);
        HBox.setHgrow(loginButton, Priority.ALWAYS);
        HBox.setHgrow(registerButton, Priority.ALWAYS);

        final VBox card = new VBox(12, title, subtitle, this.emailField, this.passwordField, buttons);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30));
        card.setMaxWidth(320);
        card.setMaxHeight(VBox.USE_PREF_SIZE);

        this.setCenter(card);
        BorderPane.setAlignment(card, Pos.CENTER);

    }

    /**
     * Validates that the entered email and password are non-blank, looks up the
     * matching {@link Login} under {@link EntityType#LOGIN} by email, and
     * checks the password against it. On success, invokes {@code onLoginSuccess} with
     * the matched account; on failure, reports why via
     * {@link GuiSupport#showValidationError(String)} and clears the password field.
     *
     * @param onLoginSuccess invoked with the matched account once a login attempt succeeds
     */
    private void attemptLogin(final Consumer<Login> onLoginSuccess) {

        final String email = this.emailField.getText() == null ? "" : this.emailField.getText().trim();
        final String password = this.passwordField.getText() == null ? "" : this.passwordField.getText();

        if (email.isBlank() || password.isBlank()) {
            GuiSupport.showValidationError("Email and password must not be empty.");
            return;
        }

        final Optional<Login> credentials = EntityFactory.getInstance().findEntity(EntityType.LOGIN, email);

        if (credentials.isEmpty() || !credentials.get().attemptLogin(email, password)) {
            GuiSupport.showValidationError("Invalid email or password.");
            this.passwordField.clear();
            return;
        }

        onLoginSuccess.accept(credentials.get());

    }

    /**
     * Opens a modal dialog for anyone without an account yet to create one: a new
     * {@link Profile} and its matching {@link Login}. Every registration after the very
     * first is registered as a {@link Role#STUDENT} - self-registration never lets anyone
     * choose {@link Role#ADMIN} for themselves - except the first {@link Profile} the
     * whole application ever registers, which becomes {@link Role#ADMIN} instead, since
     * an admin account can otherwise only ever be created from {@link de.lino.thma.ui.tab.ProfilesTab},
     * itself admin-only - with no admin yet, nobody could ever reach it. The email field
     * is pre-filled with whatever was already typed into this screen's own
     * {@link #emailField}.
     *
     * <p>{@link EntityType#PROFILE} is synced from the database first, since no profile
     * data is loaded before a login - unlike an ordinary login, registration needs to see
     * every existing profile (whether any are registered at all, to decide the new
     * account's {@link Role}, and each one's id to assign the new one a free one, see
     * {@link GuiSupport#nextId(EntityType, java.util.function.Function)}) and every
     * existing login's email to reject a duplicate account.
     *
     * <p>On confirmation, both entities are registered in {@link EntityFactory}'s cache
     * and persisted, and {@code onLoginSuccess} is invoked with the new account directly
     * - the same as a successful login - so a fresh registration signs straight in rather
     * than requiring a second, separate login attempt.
     *
     * @param onLoginSuccess invoked with the newly registered account once registration succeeds
     */
    private void showRegisterDialog(final Consumer<Login> onLoginSuccess) {

        EntityFactory.getInstance().syncFromDatabase(EntityType.PROFILE);

        final TextField firstNameField = new TextField();
        final TextField lastNameField = new TextField();
        final DatePicker birthdatePicker = new DatePicker(LocalDate.now().minusYears(18));
        final TextField birthPlaceField = new TextField();
        final TextField addressField = new TextField();
        final TextField emailField = new TextField(this.emailField.getText() == null ? "" : this.emailField.getText().trim());
        final PasswordField passwordField = new PasswordField();

        final GridPane grid = GuiSupport.formGrid(
                "First Name", firstNameField,
                "Last Name", lastNameField,
                "Birthdate", birthdatePicker,
                "Birthplace", birthPlaceField,
                "Address", addressField,
                "Email", emailField,
                "Password", passwordField
        );

        final Dialog<ButtonType> dialog = GuiSupport.confirmationDialog("Create Account", grid, event -> {

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
                GuiSupport.showValidationError("An account with email \"" + emailField.getText().trim() + "\" already exists.");
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

            final boolean isFirstProfile = EntityFactory.getInstance().getEntities(EntityType.PROFILE).isEmpty();

            final Profile profile = new Profile(GuiSupport.nextId(EntityType.PROFILE, Profile::getId), information);
            final Login login = new Login(information.getEmailAddress(), passwordField.getText(), isFirstProfile ? Role.ADMIN : Role.STUDENT);

            EntityFactory.getInstance().registerEntitiesInCache(EntityType.PROFILE, profile);
            EntityFactory.getInstance().registerEntitiesInCache(EntityType.LOGIN, login);
            EntityFactory.getInstance().syncToDatabase();

            onLoginSuccess.accept(login);

        });

    }

}
