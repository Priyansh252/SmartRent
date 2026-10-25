package com.smartrent.security;

import com.smartrent.domain.AppUser;
import com.smartrent.domain.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated principal stored in the HTTP session. Carries id and role so services can enforce ownership. */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final Role role;
    private final boolean active;

    public AppUserDetails(AppUser u) {
        this.id = u.getId();
        this.email = u.getEmail();
        this.fullName = u.getFullName();
        this.passwordHash = u.getPasswordHash();
        this.role = u.getRole();
        this.active = u.isActive();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public CurrentUser asCurrentUser() {
        return new CurrentUser(id, email, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
