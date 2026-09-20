package com.ediary.backend.user.admin.dto;

import com.ediary.backend.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
    @NotBlank(message = "Введіть логін користувача")
    @Size(min = 3, max = 50, message = "Логін має містити від 3 до 50 символів")
    @Pattern(
        regexp = "^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,48}[A-Za-z0-9])$",
        message = "Логін має містити лише латинські літери, цифри, крапку, дефіс або підкреслення та починатися і закінчуватися літерою або цифрою"
    )
    String login,

    @NotBlank(message = "Введіть ім'я")
    @Size(max = 50, message = "Ім'я не може перевищувати 50 символів")
    @Pattern(
        regexp = "^[А-Яа-яІіЇїЄєҐґ]+(?:[ '’-][А-Яа-яІіЇїЄєҐґ]+)*$",
        message = "Ім'я має містити лише українські літери"
    )
    String firstName,

    @NotBlank(message = "Введіть прізвище")
    @Size(max = 50, message = "Прізвище не може перевищувати 50 символів")
    @Pattern(
        regexp = "^[А-Яа-яІіЇїЄєҐґ]+(?:[ '’-][А-Яа-яІіЇїЄєҐґ]+)*$",
        message = "Прізвище має містити лише українські літери"
    )
    String lastName,

    @Size(max = 50, message = "По батькові не може перевищувати 50 символів")
    @Pattern(
        regexp = "^$|^[А-Яа-яІіЇїЄєҐґ]+(?:[ '’-][А-Яа-яІіЇїЄєҐґ]+)*$",
        message = "По батькові має містити лише українські літери"
    )
    String patronymic,

    @NotBlank(message = "Введіть електронну пошту")
    @Email(message = "Некоректний формат електронної пошти")
    @Size(max = 100, message = "Email не може перевищувати 100 символів")
    String email,

    @Pattern(
        regexp = "^$|^(?:\\+?380)?[0-9 ()-]{9,18}$",
        message = "Некоректний формат номера телефону"
    )
    String phone,

    @NotNull(message = "Оберіть роль")
    UserRole role
) {
}
