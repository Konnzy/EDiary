package com.ediary.backend.user.admin.event;

import com.ediary.backend.user.entity.UserRole;

public record AdminUserRoleChangedEvent(
    String email,
    String login,
    UserRole role,
    String parentLinkCode
) {
}
