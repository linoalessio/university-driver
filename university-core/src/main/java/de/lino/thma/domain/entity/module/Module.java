package de.lino.thma.domain.entity.module;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.utility.Serialized;
import lombok.*;

import java.io.Serial;
import java.util.List;
import java.util.Optional;

/**
 * A course module, made up of one or more {@link Exam}s (see {@link Exam#getId()} ()}),
 * with a name, a short tag and a credit value.
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
     * The id of this module's {@link Exam}, resolved on demand via {@link #getExam()}.
     */
    private int examId;

    /**
     * Resolves this module's {@link Exam}, looked up in
     * {@link EntityFactory#findEntity(EntityType, Object)} by {@link #getExamId()}.
     *
     * @return the resolved exam, or an empty {@link Optional} if {@link #getExamId()} no longer matches a registered exam
     */
    public Optional<Exam> getExam() {
        return EntityFactory.getInstance().findEntity(EntityType.EXAMS, this.examId);
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
