package com.smartrent.repo;

import com.smartrent.domain.AppUser;
import com.smartrent.domain.Role;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<AppUser> findByRole(Role role, Pageable pageable);
}
