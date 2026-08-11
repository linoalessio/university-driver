package de.lino.thma.domain.entity;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.semester.Semester;
import de.lino.thma.domain.entity.semester.SemesterType;
import de.lino.thma.persistence.EntityType;
import de.lino.thma.utility.Serialized;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A student, identified by an id and a {@link Profile}, and tracking which semesters
 * they are currently enrolled in.
 */
@Getter
@ToString @EqualsAndHashCode(callSuper = true)
public class Student extends Serialized {

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
     * This student's profile.
     */
    private final Profile profile;

    /**
     * The tags (primary key or name, see {@link Semester#keysOf()}) of every
     * {@code Semester} the student has been enrolled in, per {@link SemesterType}, in
     * enrollment order. Backed by an {@link EnumMap}, since {@link SemesterType} has a
     * small, fixed set of constants and an enum map resolves lookups against an array
     * by ordinal rather than hashing, and is wrapped for safe concurrent access; each
     * per-type list is a {@link CopyOnWriteArrayList} for the same reason
     * {@link EntityFactory}'s per-type entity lists are.
     */
    private final Map<SemesterType, List<String>> semesters;

    /**
     * Constructs a student.
     *
     * @param id the student's id
     * @param profile the student's profile
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public Student(final int id, final Profile profile) {

        this.id = id;
        this.profile = Objects.requireNonNull(profile, "@Student.init: profile must not be null");
        this.semesters = Collections.synchronizedMap(new EnumMap<>(SemesterType.class));

    }

    /**
     * Enrolls the student in another semester under the given {@link SemesterType},
     * on top of any it is already enrolled in for that type.
     *
     * @param semesterType the type of study {@code semesterTag} belongs to
     * @param semesterTag the tag of the semester to enroll in
     * @return this student, for chaining
     * @throws NullPointerException if {@code semesterType} or {@code semesterTag} is {@code null}
     */
    public Student addSemester(final SemesterType semesterType, final String semesterTag) {

        Objects.requireNonNull(semesterType, "@Student.addSemester: semesterType must not be null");
        Objects.requireNonNull(semesterTag, "@Student.addSemester: semesterTag must not be null");

        this.semesters.computeIfAbsent(semesterType, type -> new CopyOnWriteArrayList<>()).add(semesterTag);
        return this;

    }

    /**
     * Un-enrolls the student from the given semester, regardless of which
     * {@link SemesterType} it is registered under.
     *
     * @param semesterTag the tag of the semester to un-enroll from
     * @return this student, for chaining
     * @throws NullPointerException if {@code semesterTag} is {@code null}
     */
    public Student removeSemester(final String semesterTag) {

        Objects.requireNonNull(semesterTag, "@Student.removeSemester: semesterTag must not be null");

        this.semesters.values().forEach(semesterTags -> semesterTags.removeIf(tag -> tag.equalsIgnoreCase(semesterTag)));
        return this;

    }

    /**
     * Un-enrolls the student from one specific semester under the given
     * {@link SemesterType}, leaving any other semesters registered for that type
     * untouched.
     *
     * @param semesterType the type of study {@code semesterTag} belongs to
     * @param semesterTag the tag of the semester to un-enroll from
     * @return this student, for chaining
     * @throws NullPointerException if {@code semesterType} or {@code semesterTag} is {@code null}
     */
    public Student removeSemester(final SemesterType semesterType, final String semesterTag) {

        Objects.requireNonNull(semesterType, "@Student.removeSemester: semesterType must not be null");
        Objects.requireNonNull(semesterTag, "@Student.removeSemester: semesterTag must not be null");

        this.semesters.getOrDefault(semesterType, List.of()).removeIf(tag -> tag.equalsIgnoreCase(semesterTag));
        return this;

    }

    /**
     * Updates every occurrence of {@code oldName} across every {@link SemesterType} to
     * {@code newName}, e.g. when the {@link Semester} itself is renamed from its own
     * {@link de.lino.thma.ui.subtab.SemesterDetailTab} - since this student's enrollment
     * references it by name rather than holding a live reference to it, the rename would
     * otherwise silently orphan it here.
     *
     * @param oldName the semester tag to replace
     * @param newName the semester tag to replace it with
     * @return this student, for chaining
     * @throws NullPointerException if {@code oldName} or {@code newName} is {@code null}
     */
    public Student renameSemester(final String oldName, final String newName) {

        Objects.requireNonNull(oldName, "@Student.renameSemester: oldName must not be null");
        Objects.requireNonNull(newName, "@Student.renameSemester: newName must not be null");

        this.semesters.values().forEach(tags -> tags.replaceAll(tag -> tag.equalsIgnoreCase(oldName) ? newName : tag));
        return this;

    }

    /**
     * Computes the student's average score across every {@code Semester} they are
     * enrolled in under the given {@link SemesterType}, as the mean of each matching
     * semester's own {@link Semester#getAverageScore()}.
     *
     * @param semesterType the type of study to average over
     * @return the average score, or {@code NaN} if the student is enrolled in no semester of that type
     */
    public double getAverageScoreBySemesterType(final SemesterType semesterType) {
        return this.getSemestersByType(semesterType).stream().mapToDouble(Semester::getAverageScore).sum() / this.getSemestersByType(semesterType).size();
    }

    /**
     * Looks up the {@code Semester} the student is currently enrolled in, i.e. the
     * most recently {@link #addSemester(SemesterType, String) added} one across every
     * {@link SemesterType}.
     *
     * @return the matching semester, or an empty {@link Optional} if the student is not enrolled in any semester
     */
    public Optional<Semester> getCurrentSemester() {

        final List<String> semesterTags = this.semesters.values().stream()
                .flatMap(List::stream)
                .toList();

        return semesterTags.isEmpty()
                ? Optional.empty()
                : EntityFactory.getInstance().findEntity(EntityType.SEMESTERS, semesterTags.getLast());

    }

    /**
     * Resolves every {@code Semester} the student is currently enrolled in under the
     * given {@link SemesterType} to its live entity, looked up in
     * {@link EntityFactory#findEntity(EntityType, Object)} by tag. Tags that no longer
     * resolve to a registered {@code Semester} are silently skipped.
     *
     * @param semesterType the type of study to look up
     * @return the resolved semesters, in enrollment order, or an empty list if the student is enrolled in none of that type
     */
    public List<Semester> getSemestersByType(final SemesterType semesterType) {

        final List<Semester> semesterList = Collections.synchronizedList(new ArrayList<>());

        for (String string : this.semesters.getOrDefault(semesterType, List.of())) {
            final Optional<Semester> semester = EntityFactory.getInstance().findEntity(EntityType.SEMESTERS, string);
            if (semester.isEmpty()) continue;
            semesterList.add(semester.get());
        }

        return semesterList;
    }

    /**
     * Looks up the {@code Semester}, among every one the student is currently
     * enrolled in across all {@link SemesterType}s, whose stored tag matches
     * {@code semesterName}.
     *
     * @param semesterName the tag to look for
     * @return the matching semester, or an empty {@link Optional} if none of the student's semesters matches
     * @throws NullPointerException if {@code semesterName} is {@code null}
     */
    public Optional<Semester> getSemesterByName(final String semesterName) {

        Objects.requireNonNull(semesterName, "@Student.getSemesterByName: semesterName must not be null");

        return this.semesters.values().stream()
                .flatMap(List::stream)
                .filter(semesterName::equals)
                .findFirst()
                .flatMap(semesterTag -> EntityFactory.getInstance().findEntity(EntityType.SEMESTERS, semesterTag));

    }

    /**
     * {@inheritDoc}
     *
     * @return this student's id, email address, and full name, in that order, with the id as the {@link #primaryKey()}
     */
    @Override
    public List<String> keysOf() {
        return List.of(String.valueOf(this.id), this.profile.getEmailAddress(), this.profile.getFullName());
    }

}
