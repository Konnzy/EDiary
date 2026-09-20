package com.ediary.backend.notification.listener;

import com.ediary.backend.notification.service.EmailService;
import com.ediary.backend.user.admin.event.AdminUserCreatedEvent;
import com.ediary.backend.user.admin.event.AdminUserPasswordResetEvent;
import com.ediary.backend.user.admin.event.AdminUserRoleChangedEvent;
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendTemporaryPassword(AdminUserPasswordResetEvent event) {
        emailService.sendText(
            event.email(),
            "Новий тимчасовий пароль електронного щоденника",
            """
                Адміністратор скинув пароль вашого облікового запису.

                Логін: %s
                Тимчасовий пароль: %s

                Після входу система вимагатиме встановити новий пароль.
                Не передавайте ці дані іншим особам.
                """.formatted(event.login(), event.temporaryPassword())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendRoleChanged(AdminUserRoleChangedEvent event) {
        String linkCodeSection = event.parentLinkCode() == null
            ? ""
            : "\nКод прив’язки для батьків: " + event.parentLinkCode() + "\n";
        emailService.sendText(
            event.email(),
            "Змінено роль в електронному щоденнику",
            """
                Адміністратор змінив роль вашого облікового запису.

                Логін: %s
                Нова роль: %s
                %s
                Якщо ви не очікували цієї зміни, зверніться до адміністратора закладу.
                """.formatted(event.login(), roleLabel(event.role()), linkCodeSection)
        );
    }

    private String roleLabel(com.ediary.backend.user.entity.UserRole role) {
        return switch (role) {
            case STUDENT -> "Учень";
            case TEACHER -> "Вчитель";
            case PARENT -> "Батьки";
            case ADMIN -> "Адміністратор";
        };
    }
}
