package de.lino.thma.domain.entity.scheduler.time;

import de.lino.database.database.entity.Serialized;
import lombok.*;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

@Getter @Setter
@AllArgsConstructor
@ToString @EqualsAndHashCode(callSuper = false)
public class SchedulerTime extends Serialized {

    private LocalTime startDate;
    private LocalTime endDate;
    private ScheduleType scheduleType;

    public long getTotalDurationInMinutes() {
        return Duration.between(this.startDate, this.endDate).toMinutes();
    }

    @AllArgsConstructor
    @Getter @ToString
    public enum ScheduleType {

        LECTURE("Lecture"),
        BREAK("Break");

        private final String name;

    }

    @Override
    public List<String> keysOf() {
        return List.of();
    }

}
