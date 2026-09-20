package com.ediary.backend.schedule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.ediary.backend.schedule.entity.Weekday;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SaveScheduleLessonRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsSupportedRoomFormats() {
        for (String room : new String[] {"121", "121-А", "12Б", "Спортзал", "Актова зала"}) {
            assertThat(validator.validate(request(room))).as(room).isEmpty();
        }
    }

    @Test
    void rejectsSpecialCharactersAndInvalidSeparators() {
        for (String room : new String[] {"121/А", "<121>", "121-", "-121"}) {
            assertThat(validator.validate(request(room)))
                .as(room)
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("room"));
        }
    }

    @Test
    void rejectsRoomNameLongerThanDatabaseLimit() {
        assertThat(validator.validate(request("Дуже довга назва кабінету 121")))
            .anyMatch(violation -> violation.getPropertyPath().toString().equals("room"));
    }

    private SaveScheduleLessonRequest request(String room) {
        return new SaveScheduleLessonRequest(Weekday.MONDAY, 1, 1L, 1L, room);
    }
}
