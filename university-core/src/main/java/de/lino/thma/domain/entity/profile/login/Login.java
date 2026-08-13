package de.lino.thma.domain.entity.profile.login;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.utility.Serialized;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.Serial;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * One account's login credentials: an email address, a {@link Role}, and a password -
 * stored not in plaintext but as a random per-account salt and its PBKDF2-HMAC-SHA256
 * hash (see {@link #hashPassword(String)}), so this instance (and anything it is
 * serialized to, e.g. the local database) never holds a recoverable copy of the
 * original password.
 */
@Getter
@ToString(exclude = "passwordHash")
@EqualsAndHashCode(callSuper = true)
public class Login extends Serialized {

    /**
     * Explicit serialization version, pinned so future field additions do not
     * invalidate previously serialized instances.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The key-derivation algorithm {@link #hashPassword(String)} and {@link #matches(String, String)}
     * hash passwords with.
     */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * The number of PBKDF2 rounds a password is stretched through, per the
     * <a href="https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html">OWASP
     * Password Storage Cheat Sheet</a>'s current minimum for PBKDF2-HMAC-SHA256.
     */
    private static final int ITERATIONS = 600_000;

    /**
     * The derived key length, in bits.
     */
    private static final int KEY_LENGTH = 256;

    /**
     * The random salt's length, in bytes.
     */
    private static final int SALT_LENGTH = 16;

    /**
     * Separates the Base64-encoded salt from the Base64-encoded hash within
     * {@link #passwordHash}.
     */
    private static final String FIELD_SEPARATOR = ":";

    /**
     * This account's email address, and primary key; see {@link #keysOf()}.
     */
    private final String email;

    /**
     * This account's password, at rest as {@code "<salt>:<hash>"}, both Base64-encoded.
     * Set via {@link #Login(String, String, Role)} or {@link #changePassword(String)},
     * and checked via {@link #attemptLogin(String, String)}.
     */
    private String passwordHash;

    /**
     * This account's password, in plaintext, kept only so an admin can look it back up
     * (see {@link #getPassword()}) - never read by {@link #attemptLogin(String, String)}
     * itself, which checks {@link #passwordHash} instead.
     */
    private String password;

    /**
     * This account's role, determining {@link #isAdmin()} / {@link #isStudent()}.
     */
    private final Role role;

    /**
     * Constructs a login credentials instance.
     *
     * @param email the account's email address
     * @param password the account's password, in plaintext
     * @param role the account's role
     * @throws NullPointerException if any argument is {@code null}
     */
    public Login(@NonNull final String email, @NonNull final String password, @NonNull final Role role) {

        this.email = email;
        this.passwordHash = hashPassword(password);
        this.password = password;
        this.role = role;

    }

    /**
     * Replaces this account's password with {@code newPassword}, hashed the same way
     * as by the constructor.
     *
     * @param newPassword the new password, in plaintext
     * @throws NullPointerException if {@code newPassword} is {@code null}
     */
    public void changePassword(@NonNull final String newPassword) {
        this.passwordHash = hashPassword(newPassword);
        this.password = newPassword;
    }

    /**
     * Attempts a login: {@code email} must match this account's own (ignoring case),
     * and {@code password}'s hash must match {@link #passwordHash}, compared in
     * constant time via {@link MessageDigest#isEqual(byte[], byte[])} to avoid leaking
     * timing information about how much of the hash was matched.
     *
     * @param email the email address to check
     * @param password the password to check, in plaintext
     * @return {@code true} if both {@code email} and {@code password} match this account
     * @throws NullPointerException if either argument is {@code null}
     */
    public boolean attemptLogin(@NonNull final String email, @NonNull final String password) {
        return this.email.equalsIgnoreCase(email) && matches(password, this.passwordHash);
    }

    /**
     * Checks whether this account's {@link #role} is {@link Role#ADMIN}.
     *
     * @return {@code true} if this account is an admin
     */
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    /**
     * Checks whether this account's {@link #role} is {@link Role#STUDENT}.
     *
     * @return {@code true} if this account is a student
     */
    public boolean isStudent() {
        return this.role == Role.STUDENT;
    }

    /**
     * Hashes {@code password} against a freshly generated random salt.
     *
     * @param password the plaintext password to hash
     * @return {@code "<salt>:<hash>"}, both Base64-encoded
     */
    private static String hashPassword(final String password) {

        final byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);

        final byte[] hash = deriveKey(password, salt);

        return Base64.getEncoder().encodeToString(salt) + FIELD_SEPARATOR + Base64.getEncoder().encodeToString(hash);

    }

    /**
     * Checks whether {@code password}, hashed against the salt embedded in
     * {@code stored}, reproduces {@code stored}'s own hash.
     *
     * @param password the plaintext password to check
     * @param stored a hash previously produced by {@link #hashPassword(String)}
     * @return {@code true} if {@code password} hashes to {@code stored}'s embedded hash
     */
    private static boolean matches(final String password, final String stored) {

        final String[] parts = stored.split(FIELD_SEPARATOR, 2);
        final byte[] salt = Base64.getDecoder().decode(parts[0]);
        final byte[] expected = Base64.getDecoder().decode(parts[1]);

        return MessageDigest.isEqual(expected, deriveKey(password, salt));

    }

    /**
     * Derives a {@link #KEY_LENGTH}-bit key from {@code password} and {@code salt} via
     * {@value #ALGORITHM}, stretched over {@link #ITERATIONS} rounds.
     *
     * @param password the plaintext password to derive a key from
     * @param salt the salt to derive the key against
     * @return the derived key
     */
    private static byte[] deriveKey(final String password, final byte[] salt) {

        final PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);

        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException("@Login.deriveKey: " + ALGORITHM + " is not available", exception);
        } finally {
            spec.clearPassword();
        }

    }

    /**
     * Resolves the {@link Profile} matching this account's own email address, the
     * inverse of {@link Profile#getLoginCredentials()}.
     *
     * @return the matching profile, or an empty {@link Optional} if this account has none (e.g. an admin account created before one existed)
     */
    public Optional<Profile> getCurrentProfile() {
        return EntityFactory.getInstance().findEntity(EntityType.PROFILE, this.email);
    }

    /**
     * {@inheritDoc}
     *
     * @return this account's email address, as its only identifying value
     */
    @Override
    public List<String> keysOf() {
        return List.of(this.email);
    }

}
