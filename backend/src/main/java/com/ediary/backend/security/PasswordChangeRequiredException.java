package com.ediary.backend.security;

import org.springframework.security.access.AccessDeniedException;

final class PasswordChangeRequiredException extends AccessDeniedException {
    PasswordChangeRequiredException() {
        super("Змініть тимчасовий пароль, щоб продовжити");
    }
}
