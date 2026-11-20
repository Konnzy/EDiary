package com.ediary.backend.registration.dto.request;

import com.ediary.backend.common.validation.UkrainianNamePatterns;
import com.ediary.backend.user.entity.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateRegistrationRequest(
    @NotBlank(message = "Введіть ім'я")
    @Size(max = 50, message = "Ім'я не може перевищувати 50 символів")
    @Pattern(
        regexp = UkrainianNamePatterns.REQUIRED,
        message = "Ім'я має містити лише українські літери"
    )
    String firstName,

    @NotBlank(message = "Введіть прізвище")
    @Size(max = 50, message = "Прізвище не може перевищувати 50 символів")
    @Pattern(
        regexp = UkrainianNamePatterns.REQUIRED,
        message = "Прізвище має містити лише українські літери"
    )
    String lastName,

    @Size(max = 50, message = "По батькові не може перевищувати 50 символів")
    @Pattern(
        regexp = UkrainianNamePatterns.OPTIONAL,
        message = "По батькові має містити лише українські літери"
    )
    String patronymic,

    @NotBlank(message = "Введіть електронну пошту")
    @Email(message = "Некоректний формат електронної пошти")
    @Size(max = 100, message = "Email не може перевищувати 100 символів")
    String email,

    @NotBlank(message = "Введіть номер телефону")
    @Pattern(
        regexp = "^(?:\\+?380)?[0-9 ()-]{9,18}$",
        message = "Некоректний формат номера телефону"
    )
    String phone,

    @NotNull(message = "Вкажіть дату народження")
    @Past(message = "Дата народження має бути в минулому")
    LocalDate birthDate,

    @NotNull(message = "Оберіть роль")
    UserRole role,

    Long requestedClassId,
    Long requestedSubjectId,

    @Size(max = 9, message = "Код прив’язки не може перевищувати 9 символів")
    @Pattern(
        regexp = "^$|^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$",
        message = "Код прив’язки має формат XXXX-XXXX"
    )
    String parentLinkCode,

    @AssertTrue(message = "Потрібно погодитися з умовами користування")
    boolean termsAccepted
) {
}
