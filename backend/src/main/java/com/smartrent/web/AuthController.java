package com.smartrent.web;

import com.smartrent.dto.Dtos.MeDto;
import com.smartrent.dto.Dtos.RegisterRequest;
import com.smartrent.dto.Dtos.UserDto;
import com.smartrent.security.AppUserDetails;
import com.smartrent.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.register(request));
    }

    @GetMapping("/me")
    public MeDto me(@AuthenticationPrincipal AppUserDetails me) {
        return new MeDto(me.getId(), me.getFullName(), me.getUsername(), me.getRole());
    }
}
