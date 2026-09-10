package com.ediary.backend.user.service;

import com.ediary.backend.common.mail.EmailService;
import com.ediary.backend.user.event.AdminUserCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AdminUserNotificationListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendCredentials(AdminUserCreatedEvent event) {
        String parentCodeSection = event.parentLinkCode() == null
            ? ""
            : """

                Код прив’язки для реєстрації батьків: %s
                Передавайте цей код лише своїм батькам або законним представникам.
                """.formatted(event.parentLinkCode());
        emailService.sendText(
            event.email(),
            "Створено обліковий запис в електронному щоденнику",
            """
                Вітаємо, %s!

                Адміністратор створив для вас обліковий запис.
                Логін: %s
                Тимчасовий пароль: %s
                %s

                Після першого входу система вимагатиме змінити пароль.
                Не передавайте ці дані іншим особам.
                """.formatted(
                    event.recipientName(),
                    event.login(),
                    event.temporaryPassword(),
                    parentCodeSection
                )
        );
    }
}
