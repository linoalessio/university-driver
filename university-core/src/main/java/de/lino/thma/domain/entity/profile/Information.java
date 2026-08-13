package de.lino.thma.domain.entity.profile;

import de.lino.thma.utility.Serialized;
import lombok.*;

import java.io.Serial;
import java.util.List;

/**
 * A person's personal details: birthdate, name, birthplace, address and email
 * address.
 */
@AllArgsConstructor
@Getter @Setter
@ToString @EqualsAndHashCode(callSuper = true)
public class Information extends Serialized {

    /**
     * Explicit serialization version, pinned so future field additions do not
     * invalidate previously serialized instances.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * This person's birthdate, as epoch milliseconds.
     */
    private final long birthdate;

    /**
     * This person's first name.
     */
    @NonNull
    private String firstName;

    /**
     * This person's last name.
     */
    @NonNull
    private String lastName;

    /**
     * This person's birthplace.
     */
    @NonNull
    private String birthPlace;

    /**
     * This person's postal address.
     */
    @NonNull
    private String address;

    /**
     * This person's email address, and primary key; see {@link #keysOf()}.
     */
    @NonNull
    private String emailAddress;

    /**
     * Returns this person's first and last name joined with a single space.
     *
     * @return the full name
     */
    public String getFullName() {
        return this.firstName + " " + this.lastName;
    }

    /**
     * {@inheritDoc}
     *
     * @return this person's email address and full name, in that order, with the email address as the {@link #primaryKey()}
     */
    @Override
    public List<String> keysOf() {
        return List.of(this.emailAddress, this.getFullName());
    }

}
