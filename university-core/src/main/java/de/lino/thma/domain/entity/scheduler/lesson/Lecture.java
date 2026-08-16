package de.lino.thma.domain.entity.scheduler.lesson;

import de.lino.database.database.entity.Serialized;
import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.List;

@Getter @ToString
public class Lecture extends Serialized {

    private final int lessonId;

    private final String moduleTag;

    private final String room;

    private final List<String> professor;

    public Lecture(int lessonId, String moduleTag, String room, String... professors) {

        this.lessonId = lessonId;

        this.moduleTag = moduleTag;
        this.room = room;
        this.professor = Arrays.asList(professors);

    }

    @Override
    public List<String> keysOf() {
        return List.of(String.valueOf(this.lessonId));
    }

}
