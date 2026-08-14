package de.lino.thma.domain.entity.scheduler;

import de.lino.thma.domain.entity.scheduler.time.SchedulerTime;
import lombok.Getter;

import java.time.LocalTime;
import java.util.List;

/**
 * A named, ready-made set of {@link SchedulerTime} periods that can be applied to a
 * {@link Scheduler} in one go (see {@link #applyTo(Scheduler)}), e.g. from the "Period
 * Layout" button on {@link de.lino.thma.ui.subtab.SchedulerTab}'s "Periods" sub-tab, so
 * a student does not have to add every period of a typical semester one at a time
 * through "+ Add Period".
 */
@Getter
public enum PeriodLayout {

    /**
     * The 6 lecture periods and 5 breaks between them from {@code "table.pdf"}'s own
     * "Zeit" column - the same sample data {@link de.lino.thma.Launcher}'s dummy
     * scheduler is built from.
     */
    WINTER_SEMESTER("Winter-Semester", List.of(
            new Slot(SchedulerTime.ScheduleType.LECTURE, 8, 0, 9, 30),
            new Slot(SchedulerTime.ScheduleType.BREAK, 9, 30, 9, 45),

            new Slot(SchedulerTime.ScheduleType.LECTURE, 9, 45, 11, 15),
            new Slot(SchedulerTime.ScheduleType.BREAK, 11, 15, 12, 0),

            new Slot(SchedulerTime.ScheduleType.LECTURE, 12, 0, 13, 30),
            new Slot(SchedulerTime.ScheduleType.BREAK, 13, 30, 13, 40),

            new Slot(SchedulerTime.ScheduleType.LECTURE, 13, 40, 15, 10),
            new Slot(SchedulerTime.ScheduleType.BREAK, 15, 10, 15, 20),

            new Slot(SchedulerTime.ScheduleType.LECTURE, 15, 20, 16, 50),
            new Slot(SchedulerTime.ScheduleType.BREAK, 16, 50, 17, 0),

            new Slot(SchedulerTime.ScheduleType.LECTURE, 17, 0, 18, 30)
    ));

    /**
     * This layout's display name, e.g. {@code "Winter-Semester"}.
     */
    private final String displayName;

    /**
     * This layout's periods, in the order they are applied in.
     */
    private final List<Slot> slots;

    PeriodLayout(final String displayName, final List<Slot> slots) {
        this.displayName = displayName;
        this.slots = slots;
    }

    /**
     * Appends every one of this layout's periods to {@code scheduler} that is not
     * already covered by one of its existing periods (see {@link Scheduler#hasPeriod(LocalTime, LocalTime)}),
     * each with the next free lesson id (see {@link Scheduler#nextLessonId()}) - already
     * covered slots are left untouched rather than duplicated or overwritten.
     *
     * @param scheduler the scheduler to apply this layout to
     * @return how many periods were actually appended, out of this layout's total
     */
    public int applyTo(final Scheduler scheduler) {

        int applied = 0;

        for (final Slot slot : this.slots) {

            final LocalTime start = LocalTime.of(slot.startHour(), slot.startMinute());
            final LocalTime end = LocalTime.of(slot.endHour(), slot.endMinute());

            if (scheduler.hasPeriod(start, end)) continue;

            scheduler.appendSchedulerTime(scheduler.nextLessonId(), slot.type(), slot.startHour(), slot.startMinute(), slot.endHour(), slot.endMinute());
            applied++;

        }

        return applied;

    }

    /**
     * One period within a {@link PeriodLayout}: a {@link SchedulerTime.ScheduleType}
     * and a start and end time, as the raw hour/minute components
     * {@link Scheduler#appendSchedulerTime(int, SchedulerTime.ScheduleType, int, int, int, int)}
     * itself takes.
     *
     * @param type whether this period is a lecture or a break
     * @param startHour the hour of day this period starts, {@code 0}-{@code 23}
     * @param startMinute the minute of the hour this period starts, {@code 0}-{@code 59}
     * @param endHour the hour of day this period ends, {@code 0}-{@code 23}
     * @param endMinute the minute of the hour this period ends, {@code 0}-{@code 59}
     */
    public record Slot(SchedulerTime.ScheduleType type, int startHour, int startMinute, int endHour, int endMinute) {
    }

}
