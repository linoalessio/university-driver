package de.lino.thma.domain.entity.semester;

import de.lino.thma.domain.EntityFactory;
import de.lino.thma.domain.EntityType;
import de.lino.thma.domain.entity.module.Exam;
import de.lino.thma.domain.entity.module.Module;
import de.lino.thma.domain.entity.profile.Profile;
import de.lino.thma.domain.entity.scheduler.Scheduler;
import de.lino.thma.utility.Serialized;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A semester, identified by its name, and the {@link Module}
 * ids taught during it.
 */
@Getter @Setter
@ToString @EqualsAndHashCode(callSuper = true)
public class Semester extends Serialized {

    /**
     * Explicit serialization version, pinned so future field additions do not
     * invalidate previously serialized instances.
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * This semester's name, and primary key; see {@link #keysOf()}. Mutable, since a
     * semester can be renamed after the fact from its own
     * {@link de.lino.thma.ui.subtab.SemesterDetailTab} - unlike most other primary
     * keys in this app, which are immutable ids assigned once at creation.
     */
    @Setter
    private String id;

    /**
     * The ids of the modules taught during this semester. Backed by a
     * {@link CopyOnWriteArrayList}, since reads via {@link #hasModule(int)} far
     * outnumber writes via {@link #addModule(int)}.
     */
    private final List<Integer> modules;

    private final List<Integer> exams;

    /**
     * This semester's own weekly lesson plan, {@code null} until created via
     * {@link #createScheduler()}.
     */
    private Scheduler scheduler;

    /**
     * The kind of study this semester itself is classified under, for statistics
     * grouped by {@link SemesterType} (see {@link de.lino.thma.ui.tab.StatisticsTab}).
     * {@code null} until assigned, e.g. for a semester registered before this field
     * existed, or one nobody has classified yet; assigned after the fact via
     * {@link de.lino.thma.ui.subtab.SemesterDetailTab}.
     */
    @Setter
    private SemesterType type;

    /**
     * Constructs a semester, owned by {@code profile}: its primary key becomes
     * {@code "<profile's email>;<name>"} (see {@link #getOwnerEmail()}), the same key
     * recorded in the owning {@link Profile#getSemesters()}.
     *
     * @param profile the profile this semester is owned by
     * @param id the semester's id
     * @param modules the ids of the modules taught during this semester
     * @throws NullPointerException if {@code profile}, {@code name} or {@code modules} is {@code null}
     */
    public Semester(final Profile profile, final String id, final Integer... modules) {

        this.scheduler = null;
        this.id = profile.getInformation().getEmailAddress() + ";" + id;

        this.modules = new CopyOnWriteArrayList<>(Arrays.asList(Objects.requireNonNull(modules, "@Semester.init: modules must not be null")));
        this.exams = new CopyOnWriteArrayList<>();

    }

    /**
     * Adds another module to this semester, e.g. one just created from within this
     * semester's own tab.
     *
     * @param moduleId the id of the module to add
     * @return this semester, for chaining
     */
    public Semester addModule(final int moduleId) {
        this.modules.add(moduleId);
        return this;
    }

    public Semester addExam(final int examId) {
        this.exams.add(examId);
        return this;
    }

    /**
     * Creates this semester's own {@link Scheduler}, named after it, if it does not
     * have one yet - see {@link #scheduler}. Only ever reachable for a
     * {@link de.lino.thma.domain.entity.profile.login.Role#STUDENT} account:
     * {@link de.lino.thma.ui.tab.SemestersTab} (and everything nested under it,
     * including {@link de.lino.thma.ui.subtab.SchedulerTab}) is never built for an
     * admin one.
     *
     * @return this semester, for chaining
     */
    public Semester createScheduler() {

        if (this.scheduler == null) {
            this.scheduler = new Scheduler(this.getName());
        }

        return this;

    }

    /**
     * Removes a module from this semester, e.g. when the module itself is deleted (see
     * {@link de.lino.thma.ui.tab.ModulesTab}) and must be unlinked from every semester
     * that taught it.
     *
     * @param moduleId the id of the module to remove
     * @return this semester, for chaining
     */
    public Semester removeModule(final int moduleId) {
        this.modules.remove(Integer.valueOf(moduleId));
        return this;
    }

    public Semester removeExam(final int examId) {
        this.exams.remove(Integer.valueOf(examId));
        return this;
    }

    /**
     * Resolves every {@link Module} taught during this semester, filtering the
     * globally registered modules down to those whose id is one of {@link #modules}.
     *
     * @return the resolved modules, or an empty list if this semester has none registered yet
     */
    public List<Module> getModules() {
        return EntityFactory.getInstance().<Module>getEntities(EntityType.MODULES).stream()
                .filter(module -> this.hasModule(module.getId()))
                .toList();
    }

    /**
     * Resolves every {@link Exam} belonging to this semester's {@link #getModules()}
     * (see {@link Module#getExams()}), flattened across every module.
     *
     * @return the resolved exams, or an empty list if none of this semester's modules has any yet
     */
    public List<Exam> getExams() {
        return EntityFactory.getInstance().<Exam>getEntities(EntityType.EXAMS).stream()
                .filter(exams -> this.hasExam(exams.getId()))
                .toList();
    }

    /**
     * The average grade across this semester's exams.
     *
     * @return the average grade, or {@code 0} if this semester has no exams yet
     */
    public double getAverageScore() {
        final List<Exam> exams = this.getExams();
        return exams.isEmpty() ? 0 : exams.stream().mapToDouble(Exam::getGrade).average().orElse(0);
    }

    /**
     * Checks whether the given module id is part of this semester.
     *
     * @param moduleId the module id to look for
     * @return {@code true} if {@code moduleId} is contained in {@link #getModules()}
     */
    public boolean hasModule(final int moduleId) {
        return this.modules.contains(moduleId);
    }

    public boolean hasExam(final int examId) {
        return this.exams.contains(examId);
    }

    public String getName() {
        return this.id.split(";")[1];
    }

    /**
     * The email address of the {@link Profile} that owns this semester, derived from
     * this semester's own {@link #id} rather than tracked as a separate field - see
     * {@link #Semester(Profile, String, Integer...)}.
     *
     * @return the owning profile's email address
     */
    public String getOwnerEmail() {
        return this.id.split(";", 2)[0];
    }

    /**
     * {@inheritDoc}
     *
     * @return this semester's name, as its sole entry and {@link #primaryKey()}
     */
    @Override
    public List<String> keysOf() {
        return List.of(this.id);
    }

}
