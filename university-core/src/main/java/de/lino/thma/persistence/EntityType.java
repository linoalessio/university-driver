package de.lino.thma.persistence;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.Student;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.utility.Serialized;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.io.Serializable;

/**
 * The kinds of {@link Serialized} entity that {@link EntityFactory} can register,
 * persist, and reload, each paired with the SQLite section it is stored under and its
 * concrete Java type.
 */
@AllArgsConstructor
@Getter
public enum EntityType {

    /**
     * {@link Student} entities, stored under the {@code "students"} database section.
     */
    STUDENTS("students", Student.class),

    /**
     * {@link Semester} entities, stored under the {@code "semesters"} database section.
     */
    SEMESTERS("semesters", Semester.class),

    /**
     * {@link Module} entities, stored under the {@code "modules"} database section.
     */
    MODULES("modules", Module.class),

    /**
     * {@link Exam} entities, stored under the {@code "exams"} database section.
     */
    EXAMS("exams", Exam.class);

    /**
     * The name of the database section entities of this type are stored under.
     */
    @NonNull
    private final String databaseSectionTag;

    /**
     * The concrete {@link Serialized} subclass entities of this type are reconstructed
     * as by {@link EntityFactory#syncFromDatabase(EntityType)}.
     */
    @NonNull
    private final Class<? extends Serializable> entityClass;

}
