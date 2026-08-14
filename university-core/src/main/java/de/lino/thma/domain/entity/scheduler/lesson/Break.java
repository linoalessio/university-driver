package de.lino.thma.domain.entity.scheduler.lesson;

import de.lino.thma.utility.Serialized;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter @ToString
@AllArgsConstructor
public class Break extends Serialized {

    private final int lessonId;

    @Override
    public List<String> keysOf() {
        return List.of(String.valueOf(this.lessonId));
    }

}
