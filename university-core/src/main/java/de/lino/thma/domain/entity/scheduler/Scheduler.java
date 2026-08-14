package de.lino.thma.domain.entity.scheduler;

import com.google.common.collect.Maps;
import de.lino.thma.domain.entity.scheduler.lesson.Lecture;
import de.lino.thma.domain.entity.scheduler.time.SchedulerTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A semester's weekly lesson plan: the {@link Lecture}s taught on each
 * {@link DayOfWeek} (see {@link #appendLesson(DayOfWeek, Lecture)}), and the
 * {@link SchedulerTime} - start and end time - each lesson id is scheduled for (see
 * {@link #appendSchedulerTime(int, int, int, int, int)}).
 *
 * <p>{@link #lessons} only ever holds {@link Lecture}s, never a
 * {@link de.lino.thma.domain.entity.scheduler.lesson.Break} - a break is purely a
 * {@link SchedulerTime.ScheduleType#BREAK}-tagged period, with no per-day content of
 * its own to store. Keeping this field's element type concrete rather than the
 * abstract {@link de.lino.thma.utility.Serialized} also sidesteps a real problem: Gson
 * (used by {@link de.lino.thma.domain.EntityFactory} to persist entities) cannot
 * deserialize a polymorphic {@code List<Serialized>} back into its original
 * {@code Lecture}/{@code Break} mix without a registered type adapter, and fails with
 * {@code "Abstract classes can't be instantiated"} the first time it tries.
 */
@Getter
@ToString @EqualsAndHashCode(callSuper = false)
public class Scheduler {

    /**
     * This scheduler's semester name, e.g. {@code "WiSe2026"}.
     */
    private final String semesterName;

    /**
     * The lectures taught on each day of the week, in the order they occur. Backed by
     * a {@link CopyOnWriteArrayList} per day, since reads far outnumber writes via
     * {@link #appendLesson(DayOfWeek, Lecture)}.
     */
    private final Map<DayOfWeek, List<Lecture>> lessons;

    /**
     * The start and end time each lesson id is scheduled for, keyed by lesson id;
     * see {@link #appendSchedulerTime(int, SchedulerTime.ScheduleType, int, int, int, int)} 
     */
    private final Map<Integer, SchedulerTime> schedulerTimes;

    /**
     * Constructs an empty scheduler for the given semester.
     *
     * @param semesterName the semester's name, e.g. {@code "WiSe2026"}
     * @throws NullPointerException if {@code semesterName} is {@code null}
     */
    public Scheduler(final String semesterName) {

        this.semesterName = Objects.requireNonNull(semesterName, "@Scheduler.init: semesterName must not be null");

        this.lessons = Maps.newConcurrentMap();
        this.schedulerTimes = Maps.newConcurrentMap();

    }

    /**
     * Registers the start and end time a lesson is scheduled for, if {@code lessonId}
     * does not have one registered yet.
     *
     * @param lessonId the id of the lesson to register a time for
     * @param startHour the hour of day the lesson starts, {@code 0}-{@code 23}
     * @param startMinute the minute of the hour the lesson starts, {@code 0}-{@code 59}
     * @param endHour the hour of day the lesson ends, {@code 0}-{@code 23}
     * @param endMinute the minute of the hour the lesson ends, {@code 0}-{@code 59}
     * @return this scheduler, for chaining
     * @throws java.time.DateTimeException if any hour or minute given is out of range
     */
    public Scheduler appendSchedulerTime(final int lessonId, final SchedulerTime.ScheduleType scheduleType, final int startHour, final int startMinute, final int endHour, final int endMinute) {
        final LocalTime startTime = LocalTime.of(startHour, startMinute);
        final LocalTime endTime = LocalTime.of(endHour, endMinute);
        this.schedulerTimes.computeIfAbsent(lessonId, key -> new SchedulerTime(startTime, endTime, scheduleType));
        return this;
    }

    /**
     * Removes a lesson's registered time, and the lesson itself from every day of
     * {@link #lessons} it is listed under.
     *
     * @param lessonId the id of the lesson to remove
     * @return this scheduler, for chaining
     */
    public Scheduler removeSchedulerTime(final int lessonId) {
        final String key = String.valueOf(lessonId);
        this.lessons.values().forEach(dayLessons -> dayLessons.removeIf(lesson -> lesson.hasKey(key)));
        this.schedulerTimes.remove(lessonId);
        return this;
    }

    /**
     * Appends a lecture to the given day of the week, after any lecture already
     * registered for that day.
     *
     * @param dayOfWeek the day to append the lecture to
     * @param lecture the lecture to append
     * @return this scheduler, for chaining
     * @throws NullPointerException if {@code dayOfWeek} is {@code null}
     */
    public Scheduler appendLesson(@NonNull final DayOfWeek dayOfWeek, final Lecture lecture) {
        this.lessons.computeIfAbsent(dayOfWeek, key -> new CopyOnWriteArrayList<>()).add(lecture);
        return this;
    }

    /**
     * Removes the lecture scheduled into {@code lessonId} on {@code dayOfWeek}, if any.
     *
     * @param dayOfWeek the day to remove the lecture from
     * @param lessonId the id of the lecture's period
     * @return this scheduler, for chaining
     * @throws NullPointerException if {@code dayOfWeek} is {@code null}
     */
    public Scheduler removeLesson(@NonNull final DayOfWeek dayOfWeek, final int lessonId) {
        this.lessons.getOrDefault(dayOfWeek, List.of()).removeIf(lesson -> lesson.hasKey(String.valueOf(lessonId)));
        return this;
    }

    /**
     * Looks up the start and end time a lesson is scheduled for.
     *
     * @param lessonId the id of the lesson to look up
     * @return the lesson's registered time, or {@code null} if {@code lessonId} has none registered
     */
    public SchedulerTime get(final int lessonId) {
        return this.schedulerTimes.get(lessonId);
    }

    /**
     * The next free lesson id for a new {@link SchedulerTime} period appended via
     * {@link #appendSchedulerTime(int, SchedulerTime.ScheduleType, int, int, int, int)}:
     * one more than the highest id currently registered, or {@code 1} if none are yet.
     *
     * @return the next free lesson id
     */
    public int nextLessonId() {
        return this.schedulerTimes.keySet().stream()
                .max(Integer::compareTo)
                .map(id -> id + 1)
                .orElse(1);
    }

    /**
     * Checks whether a period covering exactly {@code start} to {@code end} is already
     * registered, regardless of its {@link SchedulerTime.ScheduleType} - two periods
     * covering the same slot would make a timetable grid ambiguous about which row a
     * lecture belongs in.
     *
     * @param start the start time to look for
     * @param end the end time to look for
     * @return {@code true} if a period from {@code start} to {@code end} is already registered
     */
    public boolean hasPeriod(final LocalTime start, final LocalTime end) {
        return this.schedulerTimes.values().stream()
                .anyMatch(time -> time.getStartDate().equals(start) && time.getEndDate().equals(end));
    }

}
