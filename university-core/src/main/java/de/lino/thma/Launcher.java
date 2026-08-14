package de.lino.thma;

import de.lino.thma.domain.entity.scheduler.PeriodLayout;
import de.lino.thma.domain.entity.scheduler.Scheduler;
import de.lino.thma.domain.entity.scheduler.lesson.Lecture;
import de.lino.thma.domain.entity.scheduler.time.SchedulerTime;
import javafx.application.Application;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

/**
 * Plain entry point for the packaged jar/app bundle, delegating straight to
 * {@link UniversityGui#main(String[])}.
 *
 * <p>The jar's own {@code Main-Class} (see {@code maven-shade-plugin} in {@code pom.xml})
 * must not extend {@link Application} directly: {@code java -jar} specifically detects
 * that case ahead of even loading the class, and refuses to start with "Error: JavaFX
 * runtime components are missing" - even though the JavaFX classes and native libraries
 * are actually present in the shaded jar. Routing through this separate, non-{@link
 * Application} class works around that check.
 */
public final class Launcher {

    private static final List<DayOfWeek> WEEK = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /**
     * Not instantiable; all functionality is exposed through {@link #main(String[])}.
     */
    private Launcher() {
    }

    /**
     * Delegates straight to {@link UniversityGui#main(String[])}.
     *
     * @param args the arguments passed from the command line
     */
    public static void main(final String[] args) {

        final Scheduler scheduler = new Scheduler("WiSe2025/2026");

        // the 6 periods, taken from "table.pdf"'s "Zeit" column, now the "Winter-Semester" PeriodLayout
        PeriodLayout.WINTER_SEMESTER.applyTo(scheduler);

        // Monday: period 1 (HPS), 2 (DMC), 3 (SS)
        scheduler
                .appendLesson(DayOfWeek.MONDAY, new Lecture(1, "Höhere Programmiersprachen", "R010", "BTH"))
                .appendLesson(DayOfWeek.MONDAY, new Lecture(3, "Digital und Microcomputertechnik", "L206", "ACK"))
                .appendLesson(DayOfWeek.MONDAY, new Lecture(5, "Signale und Systeme", "S322", "MAR"))
        ;

        // Tuesday: period 1 (HPS), 3 (MA3), 4 (CNW), 5 (HPS) - period 2 is free
        scheduler
                .appendLesson(DayOfWeek.TUESDAY, new Lecture(1, "Höhere Programmiersprachen", "R010", "BTH"))
                .appendLesson(DayOfWeek.TUESDAY, new Lecture(5, "Mathematik 3", "G013", "NEF"))
                .appendLesson(DayOfWeek.TUESDAY, new Lecture(7, "Computer Netzwerke", "S213", "KOE"))
                .appendLesson(DayOfWeek.TUESDAY, new Lecture(9, "Höhere Programmiersprachen", "S213", "BTH"))
        ;

        // Wednesday: period 1 (SS), 2 (MA3), 3 (MA3), 4 (CNW)
        scheduler
                .appendLesson(DayOfWeek.WEDNESDAY, new Lecture(1, "Signale und Systeme", "S213", "MAR"))
                .appendLesson(DayOfWeek.WEDNESDAY, new Lecture(3, "Mathematik 3", "A309", "NEF"))
                .appendLesson(DayOfWeek.WEDNESDAY, new Lecture(5, "Mathematik 3", "A309", "NEF"))
                .appendLesson(DayOfWeek.WEDNESDAY, new Lecture(7, "Computer Netzwerke", "S117", "KOE"))
        ;

        // Thursday: empty in "table.pdf" - no lessons at all

        // Friday: period 1 (DB), 2 (DMC), 3 (DB)
        scheduler
                .appendLesson(DayOfWeek.FRIDAY, new Lecture(1, "Datenbanken", "S113", "HTM"))
                .appendLesson(DayOfWeek.FRIDAY, new Lecture(3, "Digital und Microcomputertechnik", "S220", "ACK"))
                .appendLesson(DayOfWeek.FRIDAY, new Lecture(5, "Datenbanken", "S212", "HTM"))
        ;

        printSchedule(scheduler);

        // UniversityGui.main(args);
    }

    private static void printSchedule(final Scheduler scheduler) {

        System.out.println(scheduler.getSemesterName());
        System.out.println("=".repeat(scheduler.getSemesterName().length()));

        for (final DayOfWeek day : WEEK) {

            final List<Lecture> dayLessons = scheduler.getLessons().getOrDefault(day, List.of());
            if (dayLessons.isEmpty()) {
                continue;
            }

            System.out.println();
            System.out.println(day);

            dayLessons.stream()
                    .sorted(Comparator.comparing(lecture -> scheduler.get(lecture.getLessonId()).getStartDate()))
                    .forEach(lecture -> System.out.println("  " + formatLesson(scheduler, lecture)));
        }
    }

    private static String formatLesson(final Scheduler scheduler, final Lecture lecture) {

        final SchedulerTime time = scheduler.get(lecture.getLessonId());
        final String timeRange = "%s - %s".formatted(time.getStartDate(), time.getEndDate());

        return "%-13s  %-8s  %-35s  %-6s  %s".formatted(
                timeRange, time.getScheduleType().getName(), lecture.getModuleTag(), lecture.getRoom(), String.join(", ", lecture.getProfessor()));
    }

}
