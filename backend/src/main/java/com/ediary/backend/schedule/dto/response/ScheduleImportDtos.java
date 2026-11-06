package com.ediary.backend.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public final class ScheduleImportDtos {
    private ScheduleImportDtos() {}

    public enum Status { ADD, UNCHANGED, CONFLICT, INVALID }
    public enum Column { WEEK, DAY, LESSON, SUBJECT, TEACHER, ROOM }

    public record Row(int number, List<String> values, Status status, ScheduleLessonResponse current,
                      ScheduleLessonResponse proposed, Map<Column, List<String>> cellErrors) {
        @JsonProperty("errors")
        public List<String> errors() {
            return cellErrors.values().stream().flatMap(List::stream).toList();
        }
    }

    public record Preview(String fingerprint, List<String> columns, List<Row> rows) {}

    public record Result(int added, int replaced, int kept, int unchanged) {}
}
