package de.lino.thma.domain.entity.profile;

import com.google.common.collect.Lists;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.profile.login.Login;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.utility.Serialized;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.poi.sl.draw.geom.GuideIf;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A student, identified by an id and a {@link Information}, and tracking which semesters
 * they are currently enrolled in.
 */
@Getter
@ToString @EqualsAndHashCode(callSuper = true)
public class Profile extends Serialized {

    /**
     * Explicit serialization version, pinned so future field additions do not
     * invalidate previously serialized instances.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * This student's id, and primary key; see {@link #keysOf()}.
     */
    private final int id;

    /**
     * This student's information.
     */
    private final Information information;

    private final List<String> semesters;

    /**
     * Constructs a student.
     *
     * @param id the student's id
     * @param information the student's information
     * @throws NullPointerException if {@code information} or {@code password} is {@code null}
     */
    public Profile(final int id, final Information information) {

        this.id = id;
        this.information = Objects.requireNonNull(information, "@Profile.init: information must not be null");
        this.semesters = new CopyOnWriteArrayList<>();

    }

    public Profile addSemester(final String semester) {
        this.semesters.add(Objects.requireNonNull(semester, "semester must not be null"));
        return this;
    }

    public Profile removeSemester(final String semester) {
        this.semesters.remove(Objects.requireNonNull(semester, "semester must not be null"));
        return this;
    }

    public Optional<Semester> getSemester(final String id) {
        return EntityFactory.getInstance().findEntity(EntityType.SEMESTERS, id);
    }

    public Optional<Login> getLoginCredentials() {
        return EntityFactory.getInstance().findEntity(EntityType.LOGIN, this.information.getEmailAddress());
    }

    /**
     * {@inheritDoc}
     *
     * @return this student's id, email address, and full name, in that order, with the id as the {@link #primaryKey()}
     */
    @Override
    public List<String> keysOf() {
        return List.of(String.valueOf(this.id), this.information.getEmailAddress(), this.information.getFullName());
    }

}
