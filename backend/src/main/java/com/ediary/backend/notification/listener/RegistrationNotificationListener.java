package com.ediary.backend.notification.listener;

import com.ediary.backend.notification.service.EmailService;
import com.ediary.backend.registration.event.RegistrationApprovedEvent;
import com.ediary.backend.registration.event.RegistrationRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RegistrationNotificationListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendApprovalEmail(RegistrationApprovedEvent event) {
        String parentCodeSection = event.parentLinkCode() == null
            ? ""
            : """

                Код прив’язки для реєстрації батьків: %s
                Передавайте цей код лише своїм батькам або законним представникам.
                """.formatted(event.parentLinkCode());
        boolean sent = emailService.sendText(
            event.email(),
            "Доступ до електронного щоденника схвалено",
            """
                Вітаємо, %s!

                Вашу заявку схвалено.
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
        event.deliveryResult().accept(sent);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendRejectionEmail(RegistrationRejectedEvent event) {
        emailService.sendText(
            event.email(),
            "Результат розгляду заявки",
            """
                Вітаємо, %s.

                Вашу заявку на доступ відхилено.
                Причина: %s

                Для уточнення зверніться до адміністрації навчального закладу.
                """.formatted(event.recipientName(), event.reason())
        );
    }

}
