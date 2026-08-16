package de.lino.thma.domain.entity.module;

import de.lino.database.database.entity.Serialized;
import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import lombok.*;

import java.io.Serial;
import java.util.List;

/**
 * A course module, made up of one or more {@link Exam}s (see {@link Exam#getModuleId()},
 * each exam's own link back to the module it belongs to), with a name, a short tag and
 * a credit value.
 */
@AllArgsConstructor
@Getter @Setter
@ToString @EqualsAndHashCode(callSuper = true)
public class Module extends Serialized {

    /**
     * Explicit serialization version, pinned so future field additions do not
     * invalidate previously serialized instances.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * This module's id, and primary key; see {@link #keysOf()}.
     */
    private final int id;

    /**
     * This module's name.
     */
    @NonNull
    private String name;

    /**
     * This module's short tag, e.g. a course code such as {@code "CS101"}.
     */
    @NonNull
    private String tag;

    /**
     * This module's credit value.
     */
    private int credits;

    /**
     * Resolves every {@link Exam} belonging to this module, i.e. every registered exam
     * whose own {@link Exam#getModuleId()} equals this module's id.
     *
     * @return the resolved exams, or an empty list if this module has none registered yet
     */
    public List<Exam> getExams() {
        return EntityFactory.getInstance().<Exam>getEntities(EntityType.EXAMS).stream()
                .filter(exam -> exam.getModuleId() == this.id)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * @return this module's id and name, in that order, with the id as the {@link #primaryKey()}
     */
    @Override
    public List<String> keysOf() {
        return List.of(String.valueOf(this.id), this.name);
    }

}
