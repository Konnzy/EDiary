package com.ediary.backend.registration.service;

import com.ediary.backend.user.entity.AppUser;

record AccountProvisioningResult(AppUser user, String temporaryPassword, String parentLinkCode) {
}
