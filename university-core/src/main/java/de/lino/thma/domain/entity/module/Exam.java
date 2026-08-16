package de.lino.thma.domain.entity.module;

import de.lino.database.database.entity.Serialized;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import lombok.*;

import java.io.Serial;
import java.util.List;
import java.util.Optional;

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
     * The id of the {@link Module} this exam belongs to; see {@link #getModule()}.
     */
    private final int moduleId;

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
     * Resolves the {@link Module} this exam belongs to, by {@link #moduleId}.
     *
     * @return the resolved module, or an empty {@link Optional} if {@link #moduleId} no longer resolves to one
     */
    public Optional<Module> getModule() {
        return EntityFactory.getInstance().findEntity(EntityType.MODULES, this.moduleId);
    }

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
