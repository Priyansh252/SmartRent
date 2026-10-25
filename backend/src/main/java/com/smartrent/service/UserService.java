package com.smartrent.service;

import com.smartrent.domain.AppUser;
import com.smartrent.domain.Role;
import com.smartrent.dto.Dtos.CreateUserRequest;
import com.smartrent.dto.Dtos.PageResponse;
import com.smartrent.dto.Dtos.RegisterRequest;
import com.smartrent.dto.Dtos.UserDto;
import com.smartrent.repo.UserRepository;
import com.smartrent.security.CurrentUser;
import com.smartrent.web.error.ApiException;
import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
    }

    /** Public self-registration: always creates a CUSTOMER; the role can never be chosen by the caller. */
    @Transactional
    public UserDto register(RegisterRequest r) {
        AppUser u = create(r.fullName(), r.email(), r.password(), r.phone(), Role.CUSTOMER);
        return Mappers.toDto(u);
    }

    @Transactional
    public UserDto adminCreate(CreateUserRequest r, CurrentUser actor) {
        AppUser u = create(r.fullName(), r.email(), r.password(), r.phone(), r.role());
        audit.log(actor.email(), "USER_CREATED", "User", u.getId(), u.getEmail() + " as " + u.getRole());
        return Mappers.toDto(u);
    }

    @Transactional
    public UserDto setActive(Long id, boolean active, CurrentUser actor) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.userNotFound(id));
        if (u.getId().equals(actor.id()) && !active) {
            throw ApiException.conflict("CANNOT_DISABLE_SELF", "You cannot disable your own account.");
        }
        u.setActive(active);
        audit.log(actor.email(), active ? "USER_ENABLED" : "USER_DISABLED", "User", id, u.getEmail());
        return Mappers.toDto(u);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(Role role, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id"));
        return PageResponse.of((role == null ? users.findAll(pr) : users.findByRole(role, pr)).map(Mappers::toDto));
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return Mappers.toDto(users.findById(id).orElseThrow(() -> ApiException.userNotFound(id)));
    }

    private AppUser create(String fullName, String email, String rawPassword, String phone, Role role) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this e-mail address already exists.");
        }
        AppUser u = new AppUser(fullName.trim(), normalized, phone, encoder.encode(rawPassword), role);
        u.setCreatedAt(clock.instant());
        return users.save(u);
    }
}
