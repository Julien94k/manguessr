package com.manguessr.controller;

import com.manguessr.model.dto.AuthResponse;
import com.manguessr.model.dto.LoginRequest;
import com.manguessr.model.dto.RegisterRequest;
import com.manguessr.model.dto.UserProfile;
import com.manguessr.security.CustomUserDetails;
import com.manguessr.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfile> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(authService.toProfile(principal.getUser()));
    }
}
