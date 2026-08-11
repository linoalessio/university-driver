package de.lino.thma.domain.entity.semester;

import de.lino.thma.domain.entity.Student;
import lombok.Getter;

/**
 * The kind of study a {@link Student} is enrolled in for a
 * given {@link Semester}. A student may be enrolled under both types at once, e.g.
 * while starting a graduate program before finishing an undergraduate one.
 */
@Getter
public enum SemesterType {

    /**
     * Undergraduate before-(bachelor's) study.
     */
    UNDER_GRADUATE_STUDY("Undergraduate"),

    /**
     * Graduate main-(bachelor's) study.
     */
    GRADUATE_STUDY("Graduate");

    /**
     * A short, human-readable label for this type, e.g. for a combo box or table
     * column, as opposed to {@link #name()}'s raw constant spelling.
     */
    private final String displayName;

    /**
     * Constructs a semester type constant.
     *
     * @param displayName the short, human-readable label for this type
     */
    SemesterType(final String displayName) {
        this.displayName = displayName;
    }

}
