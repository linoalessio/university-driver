package de.lino.thma.domain;

import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.profile.login.Login;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.semester.Semester;
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
     * {@link Profile} entities, stored under the {@code "profiles"} database section.
     */
    PROFILE("profiles", Profile.class),

    /**
     * {@link Login} entities, stored under the {@code "logins"} database
     * section - looked up by email rather than embedded in {@link Profile} itself, so a
     * {@link Profile#getLoginCredentials()} lookup always reflects the current record.
     */
    LOGIN("logins", Login.class),

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
