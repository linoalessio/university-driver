package de.lino.thma.domain.entity.profile.login;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * The role a {@link Login} account holds, determining
 * {@link Login#isAdmin()} / {@link Login#isStudent()}.
 */
@Getter
@AllArgsConstructor
@ToString
public enum Role {

    /**
     * A regular student account.
     */
    STUDENT("Student"),

    /**
     * An administrator account.
     */
    ADMIN("Admin");

    private final String name;

}
