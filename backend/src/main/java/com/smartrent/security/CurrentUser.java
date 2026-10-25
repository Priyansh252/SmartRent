package com.smartrent.security;

import com.smartrent.domain.Role;

/** Immutable view of the caller that services use for ownership / role decisions. */
public record CurrentUser(Long id, String email, Role role) {

    public boolean isStaff() {
        return role == Role.EMPLOYEE || role == Role.ADMIN;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
