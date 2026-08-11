package de.lino.thma.domain.entity.module;

import de.lino.thma.utility.Serialized;
import lombok.*;

import java.io.Serial;
import java.util.List;

/**
 * An exam belonging to a {@link Module}, sat by a student on a given date, with a
 * name, examiner, credit value and attempt number.
 */
@AllArgsConstructor
@Getter @Setter
@ToString @EqualsAndHashCode(callSuper = true)
public class Exam extends Serialized {

    /**
     * Explicit serialization version, pinned so future field additions do not
     * invalidate previously serialized instances.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * This exam's id, and primary key; see {@link #keysOf()}.
     */
    private final int id;

    /**
     * The date this exam takes place, as epoch milliseconds.
     */
    private long date;

    /**
     * This exam's name.
     */
    @NonNull
    private String name;

    /**
     * The name of this exam's examiner.
     */
    @NonNull
    private String examiner;

    /**
     * This exam's credit value.
     */
    private int credits;

    /**
     * Which attempt this is, starting at {@code 1} for a student's first sitting.
     */
    private int attempt;

    /**
     * The grade achieved on this attempt.
     */
    private double grade;

    /**
     * {@inheritDoc}
     *
     * @return this exam's id and name, in that order, with the id as the {@link #primaryKey()}
     */
    @Override
    public List<String> keysOf() {
        return List.of(String.valueOf(this.id), this.name);
    }

}
